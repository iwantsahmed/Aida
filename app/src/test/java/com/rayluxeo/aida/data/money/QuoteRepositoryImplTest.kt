package com.rayluxeo.aida.data.money

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.money.InvoiceFixtures.insertClient
import com.rayluxeo.aida.data.money.InvoiceFixtures.invoice
import com.rayluxeo.aida.data.money.InvoiceFixtures.item
import com.rayluxeo.aida.data.repository.QuoteRepositoryImpl
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteLine
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.usecase.BuildQuoteTotalsUseCase
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

/** The quote side of [InvoiceRepositoryImplTest]: same guarantees, one extra state change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuoteRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private lateinit var repository: QuoteRepositoryImpl
    private var clientId = 0L

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 20, 9, 0))
    private val buildTotals = BuildQuoteTotalsUseCase()

    @Before
    fun openDatabase() {
        database = InvoiceFixtures.openDatabase()
        clientId = insertClient(database)
        repository = QuoteRepositoryImpl(
            dao = database.quoteDao(),
            clock = clock,
            dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a priced quote round-trips through the database exactly`() = runTest {
        val document = priced(listOf(line(quantity = "2.5", unitPrice = "1234.56", taxPercent = "15.00")))

        val created = repository.create(document).value()
        val reread = repository.byId(created.id).value()

        assertThat(reread.subtotal).isEqualTo(Money.of("3086.40"))
        assertThat(reread.tax).isEqualTo(Money.of("462.96"))
        assertThat(reread.total).isEqualTo(Money.of("3549.36"))
        assertThat(reread.lines.single().lineTotal).isEqualTo(Money.of("3086.40"))
        assertThat(reread.validUntil).isEqualTo(VALID_UNTIL)
    }

    @Test
    fun `a quote whose stored total disagrees with its lines is refused`() = runTest {
        val tampered = priced(listOf(line(quantity = "1", unitPrice = "1000.00")))
            .copy(total = Money.of("999.99"))

        assertThat(repository.create(tampered).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `a duplicate number is refused`() = runTest {
        repository.create(priced(listOf(line(quantity = "1", unitPrice = "100.00")))).value()

        val second = repository.create(priced(listOf(line(quantity = "1", unitPrice = "100.00"))))

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

        assertThat(first.number).isEqualTo("QUO-2026-001")
        assertThat(second.number).isEqualTo("QUO-2026-002")
    }

    @Test
    fun `converting a quote records the invoice and takes it out of the open list`() = runTest {
        val invoiceId = database.invoiceDao().insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item("One")),
        )!!
        val created = repository.create(priced(listOf(line(quantity = "1", unitPrice = "100.00")))).value()

        val converted = repository.markConverted(created.id, invoiceId).value()

        assertThat(converted.convertedInvoiceId).isEqualTo(invoiceId)
        assertThat(converted.isConverted).isTrue()
        assertThat(converted.status).isEqualTo(QuoteStatus.ACCEPTED)
        assertThat(repository.observeOpen().first()).isEmpty()
    }

    @Test
    fun `open quotes are the ones still on the table`() = runTest {
        repository.create(priced(listOf(line(quantity = "1", unitPrice = "100.00")))).value()
        val declined = repository.create(
            priced(listOf(line(quantity = "1", unitPrice = "100.00"))).copy(number = "QUO-2026-002"),
        ).value()
        repository.setStatus(declined.id, QuoteStatus.DECLINED).value()

        val open = repository.observeOpen().first()

        assertThat(open.map { it.number }).containsExactly("QUO-2026-001")
    }

    @Test
    fun `binning a quote hides it and restoring brings it back`() = runTest {
        val created = repository.create(priced(listOf(line(quantity = "1", unitPrice = "100.00")))).value()

        assertThat(repository.softDelete(created.id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.observeAll().first()).isEmpty()
        assertThat(repository.byNumber(created.number).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)

        assertThat(repository.restore(created.id).value().deletedAt).isNull()
        assertThat(repository.observeAll().first()).hasSize(1)
    }

    private fun priced(lines: List<QuoteLine>): Quote = buildTotals(draft(lines)).value()

    private fun draft(lines: List<QuoteLine>): Quote = Quote(
        number = "QUO-2026-001",
        clientId = clientId,
        issuedOn = ISSUED,
        validUntil = VALID_UNTIL,
        subtotal = Money.zero(),
        discount = Money.zero(),
        tax = Money.zero(),
        total = Money.zero(),
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
    ): QuoteLine = QuoteLine(
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
        val VALID_UNTIL: LocalDate = LocalDate.of(2026, 4, 5)
        val AT: Instant = Instant.ofEpochMilli(1_772_000_000_000L)
    }
}
