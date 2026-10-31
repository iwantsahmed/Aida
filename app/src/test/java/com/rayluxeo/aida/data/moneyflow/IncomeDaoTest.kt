package com.rayluxeo.aida.data.moneyflow

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.IncomeDao
import com.rayluxeo.aida.data.db.entity.IncomeEntryEntity
import com.rayluxeo.aida.data.db.entity.PaymentEntity
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.model.PaymentMethod
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Income is the half of the ledger most at risk of double counting: an entry created from a
 * payment must not be added to the same figure as the payment itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class IncomeDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: IncomeDao

    @Before
    fun openDatabase() {
        database = openMoneyFlowDatabase()
        dao = database.incomeDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an entry round trips through the database as whole minor units`() = runTest {
        val id = dao.insert(entry(source = "Retainer", amount = Money.of("1234.56")))

        val stored = dao.findById(id)!!

        assertThat(stored.amountMinor).isEqualTo(123_456L)
        assertThat(MoneyAmount.fromMinorUnits(stored.amountMinor, stored.currencyCode))
            .isEqualTo(Money.of("1234.56"))
    }

    @Test
    fun `a binned entry disappears from every read and from the total`() = runTest {
        val kept = dao.insert(entry(source = "Retainer", amount = Money.of("500.00")))
        val binned = dao.insert(entry(source = "Duplicate", amount = Money.of("900.00")))

        assertThat(dao.softDelete(binned, atEpochMillis = NOW)).isEqualTo(1)

        assertThat(dao.observeRecent(50).first().map { it.id }).containsExactly(kept)
        assertThat(dao.observeBetween(WINDOW_START, WINDOW_END).first().map { it.id })
            .containsExactly(kept)
        assertThat(dao.sumReceivedMinor(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(50_000L)
    }

    @Test
    fun `restoring brings back the same row`() = runTest {
        val id = dao.insert(entry(source = "Retainer", amount = Money.of("500.00")))
        dao.softDelete(id, atEpochMillis = NOW)

        assertThat(dao.restore(id, atEpochMillis = NOW)).isEqualTo(1)
        assertThat(dao.findById(id)!!.deletedAtEpochMillis).isNull()
    }

    @Test
    fun `a client sees only their own entries`() = runTest {
        val clientId = database.insertClient(name = "Rayluxeo")
        val otherId = database.insertClient(name = "Someone else", uuid = "other")
        val mine = dao.insert(entry(source = "Retainer", amount = Money.of("500.00"), clientId = clientId))
        dao.insert(entry(source = "Other job", amount = Money.of("300.00"), clientId = otherId))

        assertThat(dao.observeForClient(clientId).first().map { it.id }).containsExactly(mine)
    }

    @Test
    fun `the total sums many entries without drifting by a paisa`() = runTest {
        dao.insert(entry(source = "One", amount = Money.of("1234.56")))
        dao.insert(entry(source = "Two", amount = Money.of("0.01")))
        dao.insert(entry(source = "Three", amount = Money.of("99.99")))

        val total = dao.sumReceivedMinor(WINDOW_START, WINDOW_END, "BDT")

        assertThat(total).isEqualTo(133_456L)
        assertThat(MoneyAmount.fromMinorUnits(total, "BDT")).isEqualTo(Money.of("1334.56"))
    }

    @Test
    fun `the uninvoiced total leaves out whatever a payment already accounts for`() = runTest {
        val paymentId = database.paymentDao().insert(payment(Money.of("700.00")))
        dao.insert(entry(source = "Invoice INV-001", amount = Money.of("700.00"), paymentId = paymentId))
        dao.insert(entry(source = "Cash job", amount = Money.of("300.00")))

        assertThat(dao.sumReceivedMinor(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(100_000L)
        assertThat(dao.sumReceivedMinorUninvoiced(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(30_000L)
        assertThat(dao.findByPaymentId(paymentId)!!.source).isEqualTo("Invoice INV-001")
    }

    @Test
    fun `deleting the payment leaves the income entry behind with no payment`() = runTest {
        val paymentId = database.paymentDao().insert(payment(Money.of("700.00")))
        val id = dao.insert(entry(source = "Invoice INV-001", amount = Money.of("700.00"), paymentId = paymentId))

        database.openHelper.writableDatabase
            .execSQL("DELETE FROM payments WHERE id = ?", arrayOf<Any?>(paymentId))

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored!!.paymentId).isNull()
        assertThat(stored.amountMinor).isEqualTo(70_000L)
    }

    private fun entry(
        source: String,
        amount: Money,
        paymentId: Long? = null,
        clientId: Long? = null,
        receivedAt: Long = NOW,
    ): IncomeEntryEntity = IncomeEntryEntity(
        source = source,
        description = null,
        amountMinor = MoneyAmount.toMinorUnits(amount),
        currencyCode = amount.currency.currencyCode,
        receivedAtEpochMillis = receivedAt,
        clientId = clientId,
        projectId = null,
        paymentId = paymentId,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = receivedAt,
        updatedAtEpochMillis = receivedAt,
    )

    private fun payment(amount: Money): PaymentEntity = PaymentEntity(
        invoiceId = null,
        clientId = null,
        amountMinor = MoneyAmount.toMinorUnits(amount),
        currencyCode = amount.currency.currencyCode,
        method = PaymentMethod.CASH,
        receivedAtEpochMillis = NOW,
        reference = null,
        note = null,
        createdAtEpochMillis = NOW,
        updatedAtEpochMillis = NOW,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 5)
        val NOW: Long = TODAY.atMillis(hour = 11)
        val WINDOW_START: Long = TODAY.atStartOfDayMillis()
        val WINDOW_END: Long = TODAY.plusDays(1).atStartOfDayMillis()
    }
}
