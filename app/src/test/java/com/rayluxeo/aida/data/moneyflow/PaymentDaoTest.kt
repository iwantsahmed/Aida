package com.rayluxeo.aida.data.moneyflow

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.PaymentApplication
import com.rayluxeo.aida.data.db.dao.PaymentDao
import com.rayluxeo.aida.data.db.entity.PaymentEntity
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.model.PaymentMethod
import java.time.LocalDate
import java.util.Currency
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Applying a payment is the one place in Mira where two tables have to move together, so most of
 * what is asserted here is what the database looks like after a refusal: nothing written, and
 * the invoice exactly as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PaymentDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: PaymentDao
    private var clientId: Long = 0L

    @Before
    fun openDatabase() {
        database = openMoneyFlowDatabase()
        dao = database.paymentDao()
        clientId = database.insertClient()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an amount round trips through the database as whole minor units`() = runTest {
        val id = dao.insert(payment(invoiceId = null, amount = Money.of("1234.56")))

        val stored = dao.findById(id)!!

        assertThat(stored.amountMinor).isEqualTo(123_456L)
        assertThat(stored.currencyCode).isEqualTo("BDT")
        assertThat(MoneyAmount.fromMinorUnits(stored.amountMinor, stored.currencyCode))
            .isEqualTo(Money.of("1234.56"))
    }

    @Test
    fun `a trailing zero survives the round trip`() = runTest {
        val id = dao.insert(payment(invoiceId = null, amount = Money.of("1200.00")))

        val stored = dao.findById(id)!!

        assertThat(stored.amountMinor).isEqualTo(120_000L)
        assertThat(MoneyAmount.fromMinorUnits(stored.amountMinor, stored.currencyCode).amount.toPlainString())
            .isEqualTo("1200.00")
    }

    @Test
    fun `a partial payment moves the invoice to partially paid`() = runTest {
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L)

        val outcome = dao.recordAgainstInvoice(payment(invoiceId, Money.of("400.00")))

        assertThat(outcome).isInstanceOf(PaymentApplication.Applied::class.java)
        assertThat((outcome as PaymentApplication.Applied).invoiceStatus)
            .isEqualTo(InvoiceStatus.PARTIALLY_PAID)
        assertThat(database.invoiceStatus(invoiceId)).isEqualTo("PARTIALLY_PAID")
        assertThat(database.invoiceAmountPaidMinor(invoiceId)).isEqualTo(40_000L)
    }

    @Test
    fun `a payment meeting the total moves the invoice to paid`() = runTest {
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L)

        val outcome = dao.recordAgainstInvoice(payment(invoiceId, Money.of("1000.00")))

        assertThat((outcome as PaymentApplication.Applied).invoiceStatus).isEqualTo(InvoiceStatus.PAID)
        assertThat(database.invoiceStatus(invoiceId)).isEqualTo("PAID")
        assertThat(database.invoiceAmountPaidMinor(invoiceId)).isEqualTo(100_000L)
    }

    @Test
    fun `partial payments settle the invoice and their sum does not drift by a paisa`() = runTest {
        val invoiceId = database.insertInvoice(clientId, totalMinor = 370_368L)

        dao.recordAgainstInvoice(payment(invoiceId, Money.of("1234.56")))
        dao.recordAgainstInvoice(payment(invoiceId, Money.of("1234.56")))
        val last = dao.recordAgainstInvoice(payment(invoiceId, Money.of("1234.56")))

        assertThat((last as PaymentApplication.Applied).invoiceStatus).isEqualTo(InvoiceStatus.PAID)
        assertThat(database.invoiceAmountPaidMinor(invoiceId)).isEqualTo(370_368L)
        assertThat(dao.listForInvoice(invoiceId).sumOf { it.amountMinor }).isEqualTo(370_368L)
    }

    @Test
    fun `a payment in another currency is refused and nothing is written`() = runTest {
        val invoiceId = database.insertInvoice(clientId, currencyCode = "BDT", totalMinor = 100_000L)
        val dollars = Money.of("500.00", Currency.getInstance("USD"))

        val outcome = dao.recordAgainstInvoice(payment(invoiceId, dollars))

        assertThat(outcome).isEqualTo(PaymentApplication.CurrencyMismatch("BDT"))
        assertThat(database.paymentCount()).isEqualTo(0L)
        assertThat(database.invoiceAmountPaidMinor(invoiceId)).isEqualTo(0L)
        assertThat(database.invoiceStatus(invoiceId)).isEqualTo("SENT")
    }

    @Test
    fun `an overpayment is refused and nothing is written`() = runTest {
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L, amountPaidMinor = 60_000L)

        val outcome = dao.recordAgainstInvoice(payment(invoiceId, Money.of("500.00")))

        assertThat(outcome).isEqualTo(PaymentApplication.Overpayment(40_000L))
        assertThat(database.paymentCount()).isEqualTo(0L)
        assertThat(database.invoiceAmountPaidMinor(invoiceId)).isEqualTo(60_000L)
    }

    @Test
    fun `a payment against a void invoice is refused`() = runTest {
        val invoiceId = database.insertInvoice(clientId, status = InvoiceStatus.VOID)

        val outcome = dao.recordAgainstInvoice(payment(invoiceId, Money.of("100.00")))

        assertThat(outcome).isEqualTo(PaymentApplication.InvoiceNotPayable)
        assertThat(database.paymentCount()).isEqualTo(0L)
    }

    @Test
    fun `a payment against an invoice that is not there is refused`() = runTest {
        val outcome = dao.recordAgainstInvoice(payment(invoiceId = 404L, amount = Money.of("100.00")))

        assertThat(outcome).isEqualTo(PaymentApplication.InvoiceMissing)
        assertThat(database.paymentCount()).isEqualTo(0L)
    }

    @Test
    fun `deleting an invoice orphans its payments rather than deleting them`() = runTest {
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L)
        dao.recordAgainstInvoice(payment(invoiceId, Money.of("400.00")))
        val paymentId = dao.listForInvoice(invoiceId).single().id

        database.deleteInvoice(invoiceId)

        val orphan = dao.findById(paymentId)
        assertThat(orphan).isNotNull()
        assertThat(orphan!!.invoiceId).isNull()
        assertThat(orphan.amountMinor).isEqualTo(40_000L)
        assertThat(database.paymentCount()).isEqualTo(1L)
    }

    @Test
    fun `a client an invoice still points at cannot be deleted`() {
        database.insertInvoice(clientId)

        assertThrows(SQLiteConstraintException::class.java) { database.deleteClient(clientId) }
    }

    @Test
    fun `deleting a payment takes it back off the invoice`() = runTest {
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L)
        dao.recordAgainstInvoice(payment(invoiceId, Money.of("1000.00")))
        val paymentId = dao.listForInvoice(invoiceId).single().id
        assertThat(database.invoiceStatus(invoiceId)).isEqualTo("PAID")

        val removed = dao.deleteAndUnapply(paymentId, atEpochMillis = RECEIVED_AT)

        assertThat(removed).isEqualTo(1)
        assertThat(database.paymentCount()).isEqualTo(0L)
        assertThat(database.invoiceAmountPaidMinor(invoiceId)).isEqualTo(0L)
        assertThat(database.invoiceStatus(invoiceId)).isEqualTo("SENT")
    }

    @Test
    fun `deleting one of two payments leaves the invoice partially paid`() = runTest {
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L)
        dao.recordAgainstInvoice(payment(invoiceId, Money.of("600.00")))
        dao.recordAgainstInvoice(payment(invoiceId, Money.of("400.00")))
        val first = dao.listForInvoice(invoiceId).first().id

        dao.deleteAndUnapply(first, atEpochMillis = RECEIVED_AT)

        assertThat(database.invoiceAmountPaidMinor(invoiceId)).isEqualTo(40_000L)
        assertThat(database.invoiceStatus(invoiceId)).isEqualTo("PARTIALLY_PAID")
    }

    @Test
    fun `deleting a payment that is not there reports nothing removed`() = runTest {
        assertThat(dao.deleteAndUnapply(404L, atEpochMillis = RECEIVED_AT)).isEqualTo(0)
    }

    @Test
    fun `the received total sums every payment in the window without drift`() = runTest {
        dao.insert(payment(invoiceId = null, amount = Money.of("1234.56")))
        dao.insert(payment(invoiceId = null, amount = Money.of("0.01")))
        dao.insert(payment(invoiceId = null, amount = Money.of("99.99")))
        dao.insert(payment(invoiceId = null, amount = Money.of("1000.00"), receivedAt = OUTSIDE_WINDOW))

        val total = dao.sumReceivedMinor(
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
            currencyCode = "BDT",
        )

        assertThat(total).isEqualTo(133_456L)
        assertThat(MoneyAmount.fromMinorUnits(total, "BDT")).isEqualTo(Money.of("1334.56"))
    }

    @Test
    fun `a total in one currency never picks up another`() = runTest {
        dao.insert(payment(invoiceId = null, amount = Money.of("100.00")))
        dao.insert(payment(invoiceId = null, amount = Money.of("100.00", Currency.getInstance("USD"))))

        assertThat(dao.sumReceivedMinor(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(10_000L)
        assertThat(dao.sumReceivedMinor(WINDOW_START, WINDOW_END, "USD")).isEqualTo(10_000L)
    }

    private fun payment(
        invoiceId: Long?,
        amount: Money,
        receivedAt: Long = RECEIVED_AT,
    ): PaymentEntity = PaymentEntity(
        invoiceId = invoiceId,
        clientId = clientId,
        amountMinor = MoneyAmount.toMinorUnits(amount),
        currencyCode = amount.currency.currencyCode,
        method = PaymentMethod.BANK_TRANSFER,
        receivedAtEpochMillis = receivedAt,
        reference = null,
        note = null,
        createdAtEpochMillis = receivedAt,
        updatedAtEpochMillis = receivedAt,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 5)
        val RECEIVED_AT: Long = TODAY.atMillis(hour = 11)
        val WINDOW_START: Long = TODAY.atStartOfDayMillis()
        val WINDOW_END: Long = TODAY.plusDays(1).atStartOfDayMillis()
        val OUTSIDE_WINDOW: Long = TODAY.plusDays(2).atMillis(hour = 11)
    }
}
