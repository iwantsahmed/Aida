package com.rayluxeo.aida.data.money

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.money.InvoiceFixtures.insertClient
import com.rayluxeo.aida.data.repository.InvoiceRepositoryImpl
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceLine
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.usecase.BuildInvoiceTotalsUseCase
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The repository together with the arithmetic that feeds it, because the guarantee worth testing
 * is the pair: figures that `BuildInvoiceTotalsUseCase` produced survive the trip to disk and
 * back without moving by a paisa, and figures it did not produce are refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InvoiceRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private lateinit var repository: InvoiceRepositoryImpl
    private var clientId = 0L

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 20, 9, 0))
    private val buildTotals = BuildInvoiceTotalsUseCase()

    @Before
    fun openDatabase() {
        database = InvoiceFixtures.openDatabase()
        clientId = insertClient(database)
        repository = InvoiceRepositoryImpl(
            dao = database.invoiceDao(),
            clock = clock,
            dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a priced invoice round-trips through the database exactly`() = runTest {
        val document = priced(listOf(line(quantity = "2.5", unitPrice = "1234.56", taxPercent = "15.00")))

        val created = repository.create(document).value()
        val reread = repository.byId(created.id).value()

        assertThat(reread.subtotal).isEqualTo(Money.of("3086.40"))
        assertThat(reread.tax).isEqualTo(Money.of("462.96"))
        assertThat(reread.total).isEqualTo(Money.of("3549.36"))
        val line = reread.lines.single()
        assertThat(line.unitPrice).isEqualTo(Money.of("1234.56"))
        assertThat(line.lineTotal).isEqualTo(Money.of("3086.40"))
        assertThat(line.taxRatePercent).isEqualTo(BigDecimal("15.00"))
        assertThat(line.quantity.compareTo(BigDecimal("2.5"))).isEqualTo(0)
    }

    @Test
    fun `an invoice of many lines stores a total equal to the sum of its lines`() = runTest {
        val lines = (0 until 9).map { index ->
            line(quantity = "0.333", unitPrice = "99.99", description = "Hour $index", position = index)
        }

        val created = repository.create(priced(lines)).value()
        val reread = repository.byId(created.id).value()

        val summed = reread.lines.map { it.lineTotal.amount }.reduce(BigDecimal::add)
        assertThat(summed).isEqualTo(reread.subtotal.amount)
        assertThat(reread.total.amount).isEqualTo(reread.subtotal.amount)
    }

    @Test
    fun `an invoice whose stored total disagrees with its lines is refused`() = runTest {
        val document = priced(listOf(line(quantity = "1", unitPrice = "1000.00")))
        val tampered = document.copy(total = Money.of("1000.01"))

        val result = repository.create(tampered)

        assertThat(result.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `an invoice whose stored subtotal disagrees with its lines is refused`() = runTest {
        val document = priced(listOf(line(quantity = "1", unitPrice = "1000.00")))
        val tampered = document.copy(subtotal = Money.of("900.00"), total = Money.of("900.00"))

        assertThat(repository.create(tampered).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a duplicate number is refused`() = runTest {
        repository.create(priced(listOf(line(quantity = "1", unitPrice = "100.00")))).value()

        val second = repository.create(priced(listOf(line(quantity = "1", unitPrice = "200.00"))))

        assertThat(second.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeAll().first()).hasSize(1)
    }

    @Test
    fun `the next number is allocated at insert`() = runTest {
        val first = repository.createWithNextNumber(
            priced(listOf(line(quantity = "1", unitPrice = "100.00"))).copy(number = ""),
        ).value()
        val second = repository.createWithNextNumber(
            priced(listOf(line(quantity = "1", unitPrice = "100.00"))).copy(number = ""),
        ).value()

        assertThat(first.number).isEqualTo("INV-2026-001")
        assertThat(second.number).isEqualTo("INV-2026-002")
    }

    @Test
    fun `a stored invoice is given a uuid it keeps`() = runTest {
        val created = repository.create(priced(listOf(line(quantity = "1", unitPrice = "100.00")))).value()

        assertThat(created.uuid).isNotEmpty()
        assertThat(repository.byId(created.id).value().uuid).isEqualTo(created.uuid)
    }

    @Test
    fun `outstanding is the total less what the payments cluster recorded`() = runTest {
        val created = repository.create(
            priced(listOf(line(quantity = "1", unitPrice = "1000.00"))),
        ).value()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE invoices SET amountPaidMinor = 40000 WHERE id = ${created.id}",
        )

        val reread = repository.byId(created.id).value()

        assertThat(reread.amountPaid).isEqualTo(Money.of("400.00"))
        assertThat(reread.outstanding).isEqualTo(Money.of("600.00"))
    }

    @Test
    fun `an update replaces the lines and leaves the paid amount alone`() = runTest {
        val created = repository.create(priced(listOf(line(quantity = "1", unitPrice = "1000.00")))).value()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE invoices SET amountPaidMinor = 40000 WHERE id = ${created.id}",
        )
        val revised = buildTotals(
            created.copy(
                amountPaid = Money.zero(),
                lines = listOf(
                    line(quantity = "2", unitPrice = "1000.00", description = "Revised", position = 0),
                ),
            ),
        ).value()

        val updated = repository.update(revised).value()

        assertThat(updated.total).isEqualTo(Money.of("2000.00"))
        assertThat(updated.amountPaid).isEqualTo(Money.of("400.00"))
        assertThat(updated.lines.map { it.description }).containsExactly("Revised")
    }

    @Test
    fun `binning an invoice hides it from every read and restoring brings it back`() = runTest {
        val created = repository.create(priced(listOf(line(quantity = "1", unitPrice = "1000.00")))).value()

        assertThat(repository.softDelete(created.id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.observeAll().first()).isEmpty()
        assertThat(repository.observeUnpaid().first()).isEmpty()
        assertThat(repository.byId(created.id).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.byNumber(created.number).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.search(null).value()).isEmpty()

        assertThat(repository.restore(created.id).value().deletedAt).isNull()
        assertThat(repository.observeAll().first()).hasSize(1)
    }

    @Test
    fun `only a payment can move an invoice to a paid state`() = runTest {
        val created = repository.create(priced(listOf(line(quantity = "1", unitPrice = "1000.00")))).value()

        assertThat(repository.setStatus(created.id, InvoiceStatus.PAID).errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.setStatus(created.id, InvoiceStatus.PARTIALLY_PAID).errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.setStatus(created.id, InvoiceStatus.SENT).value().status)
            .isEqualTo(InvoiceStatus.SENT)
    }

    @Test
    fun `lines come back in the order they were priced in`() = runTest {
        val lines = listOf(
            line(quantity = "1", unitPrice = "10.00", description = "First", position = 0),
            line(quantity = "1", unitPrice = "20.00", description = "Second", position = 1),
            line(quantity = "1", unitPrice = "30.00", description = "Third", position = 2),
        )

        val created = repository.create(priced(lines)).value()

        val reread = repository.byId(created.id).value()
        assertThat(reread.lines.map { it.description }).containsExactly("First", "Second", "Third").inOrder()
        assertThat(reread.lines.map { it.position }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `an invoice with no lines is refused`() = runTest {
        val empty = priced(emptyList())

        assertThat(repository.create(empty).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `search reads a percent sign as a character, not as a wildcard`() = runTest {
        repository.create(
            priced(listOf(line(quantity = "1", unitPrice = "100.00"))).copy(
                number = "INV-2026-014",
                notes = "50% deposit",
            ),
        ).value()
        repository.create(
            priced(listOf(line(quantity = "1", unitPrice = "100.00"))).copy(
                number = "INV-2026-015",
                notes = "deposit on delivery",
            ),
        ).value()

        assertThat(repository.search("50%").value().map { it.number }).containsExactly("INV-2026-014")
        assertThat(repository.search("014").value().map { it.number }).containsExactly("INV-2026-014")
        assertThat(repository.search("  ").value()).hasSize(2)
    }

    @Test
    fun `overdue counts from Abu's own day`() = runTest {
        repository.create(
            priced(listOf(line(quantity = "1", unitPrice = "1000.00"))).copy(dueOn = LocalDate.of(2026, 3, 19)),
        ).value()
        repository.create(
            priced(listOf(line(quantity = "1", unitPrice = "1000.00"))).copy(
                number = "INV-2026-002",
                dueOn = LocalDate.of(2026, 4, 30),
            ),
        ).value()

        val overdue = repository.observeOverdue().first()

        assertThat(overdue.map { it.number }).containsExactly("INV-2026-001")
    }

    private fun priced(lines: List<InvoiceLine>): Invoice = buildTotals(draft(lines)).value()

    private fun draft(lines: List<InvoiceLine>): Invoice = Invoice(
        number = "INV-2026-001",
        clientId = clientId,
        issuedOn = ISSUED,
        dueOn = DUE,
        subtotal = Money.zero(),
        discount = Money.zero(),
        tax = Money.zero(),
        total = Money.zero(),
        amountPaid = Money.zero(),
        lines = lines,
        createdAt = AT,
        updatedAt = AT,
    )

    private fun line(
        quantity: String,
        unitPrice: String,
        taxPercent: String = "0.00",
        description: String = "Work",
        position: Int = 0,
    ): InvoiceLine = InvoiceLine(
        description = description,
        quantity = BigDecimal(quantity),
        unitPrice = Money.of(unitPrice),
        taxRatePercent = BigDecimal(taxPercent),
        lineTotal = Money.zero(),
        position = position,
    )

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success<T>).value

    private fun <T> AppResult<T>.errorCode(): MiraErrorCode? = (this as? AppResult.Failure)?.error?.code

    private companion object {
        val ISSUED: LocalDate = LocalDate.of(2026, 3, 5)
        val DUE: LocalDate = LocalDate.of(2026, 3, 19)
        val AT: Instant = Instant.ofEpochMilli(1_772_000_000_000L)
    }
}
