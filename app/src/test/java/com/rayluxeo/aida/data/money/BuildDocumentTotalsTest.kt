package com.rayluxeo.aida.data.money

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceLine
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteLine
import com.rayluxeo.aida.domain.usecase.BuildInvoiceTotalsUseCase
import com.rayluxeo.aida.domain.usecase.BuildQuoteTotalsUseCase
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import org.junit.Test

/**
 * The arithmetic that decides what Abu charges. Plain JUnit: none of this touches Android, and a
 * figure that is wrong here is wrong before any database is involved.
 */
class BuildDocumentTotalsTest {

    private val buildInvoiceTotals = BuildInvoiceTotalsUseCase()
    private val buildQuoteTotals = BuildQuoteTotalsUseCase()

    @Test
    fun `two and a half units at 1234-56 with 15 percent tax price exactly`() {
        val priced = buildInvoiceTotals(
            invoiceOf(listOf(line(quantity = "2.5", unitPrice = "1234.56", taxPercent = "15.00"))),
        ).value()

        assertThat(priced.lines.single().lineTotal.amount).isEqualTo(BigDecimal("3086.40"))
        assertThat(priced.subtotal.amount).isEqualTo(BigDecimal("3086.40"))
        assertThat(priced.tax.amount).isEqualTo(BigDecimal("462.96"))
        assertThat(priced.total.amount).isEqualTo(BigDecimal("3549.36"))
    }

    @Test
    fun `the subtotal is exactly the sum of the stored line totals`() {
        val lines = (0 until 7).map { index ->
            line(quantity = "0.333", unitPrice = "99.99", description = "Hour $index", position = index)
        }

        val priced = buildInvoiceTotals(invoiceOf(lines)).value()

        val summed = priced.lines.map { it.lineTotal.amount }.reduce(BigDecimal::add)
        assertThat(summed).isEqualTo(priced.subtotal.amount)
        assertThat(priced.subtotal.amount).isEqualTo(BigDecimal("233.10"))
    }

    /**
     * Three lines of half a paisa each. Rounding once per line gives 0.03; summing the raw
     * products first and rounding at the end would give 0.02, and the printed lines would then
     * not add up to the printed total.
     */
    @Test
    fun `rounding happens per line, not once at the bottom`() {
        val lines = (0 until 3).map { index ->
            line(quantity = "0.005", unitPrice = "1.00", description = "Sliver $index", position = index)
        }

        val priced = buildInvoiceTotals(invoiceOf(lines)).value()

        assertThat(priced.lines.map { it.lineTotal.amount }).containsExactly(
            BigDecimal("0.01"),
            BigDecimal("0.01"),
            BigDecimal("0.01"),
        )
        assertThat(priced.subtotal.amount).isEqualTo(BigDecimal("0.03"))
    }

    @Test
    fun `the discount comes off after tax and leaves the tax alone`() {
        val priced = buildInvoiceTotals(
            invoiceOf(
                lines = listOf(line(quantity = "1", unitPrice = "1000.00", taxPercent = "15.00")),
                discount = Money.of("100.00"),
            ),
        ).value()

        assertThat(priced.subtotal.amount).isEqualTo(BigDecimal("1000.00"))
        assertThat(priced.tax.amount).isEqualTo(BigDecimal("150.00"))
        assertThat(priced.total.amount).isEqualTo(BigDecimal("1050.00"))
    }

    @Test
    fun `a discount bigger than the document is refused`() {
        val result = buildInvoiceTotals(
            invoiceOf(
                lines = listOf(line(quantity = "1", unitPrice = "1000.00", taxPercent = "15.00")),
                discount = Money.of("2000.00"),
            ),
        )

        assertThat(result.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a line priced in another currency is refused`() {
        val dollars = InvoiceLine(
            description = "Imported plugin",
            quantity = BigDecimal("1"),
            unitPrice = Money(BigDecimal("10.00"), Currency.getInstance("USD")),
            taxRatePercent = BigDecimal("0.00"),
            lineTotal = Money.zero(),
            position = 0,
        )

        val result = buildInvoiceTotals(invoiceOf(listOf(dollars)))

        assertThat(result.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a negative quantity is refused`() {
        val result = buildInvoiceTotals(invoiceOf(listOf(line(quantity = "-1", unitPrice = "100.00"))))

        assertThat(result.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a quantity finer than the database stores is refused`() {
        val result = buildInvoiceTotals(invoiceOf(listOf(line(quantity = "0.0005", unitPrice = "100.00"))))

        assertThat(result.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `pricing renumbers the lines into order`() {
        val lines = listOf(
            line(quantity = "1", unitPrice = "10.00", description = "First", position = 5),
            line(quantity = "1", unitPrice = "20.00", description = "Second", position = 9),
        )

        val priced = buildInvoiceTotals(invoiceOf(lines)).value()

        assertThat(priced.lines.map { it.position }).containsExactly(0, 1).inOrder()
        assertThat(priced.lines.map { it.description }).containsExactly("First", "Second").inOrder()
    }

    @Test
    fun `a quote prices to the same figures as the invoice it will become`() {
        val quote = Quote(
            number = "QUO-2026-001",
            clientId = 1L,
            issuedOn = ISSUED,
            validUntil = DUE,
            subtotal = Money.zero(),
            discount = Money.of("100.00"),
            tax = Money.zero(),
            total = Money.zero(),
            lines = listOf(
                QuoteLine(
                    description = "Design, 2.5 days",
                    quantity = BigDecimal("2.5"),
                    unitPrice = Money.of("1234.56"),
                    taxRatePercent = BigDecimal("15.00"),
                    lineTotal = Money.zero(),
                    position = 0,
                ),
            ),
            createdAt = AT,
            updatedAt = AT,
        )

        val priced = buildQuoteTotals(quote).value()
        val asInvoice = buildInvoiceTotals(
            invoiceOf(
                lines = listOf(line(quantity = "2.5", unitPrice = "1234.56", taxPercent = "15.00")),
                discount = Money.of("100.00"),
            ),
        ).value()

        assertThat(priced.subtotal).isEqualTo(asInvoice.subtotal)
        assertThat(priced.tax).isEqualTo(asInvoice.tax)
        assertThat(priced.total).isEqualTo(asInvoice.total)
        assertThat(priced.total.amount).isEqualTo(BigDecimal("3449.36"))
    }

    @Test
    fun `an invoice with no lines prices to zero`() {
        val priced = buildInvoiceTotals(invoiceOf(emptyList())).value()

        assertThat(priced.subtotal.amount).isEqualTo(BigDecimal("0.00"))
        assertThat(priced.tax.amount).isEqualTo(BigDecimal("0.00"))
        assertThat(priced.total.amount).isEqualTo(BigDecimal("0.00"))
    }

    private fun invoiceOf(lines: List<InvoiceLine>, discount: Money = Money.zero()): Invoice = Invoice(
        number = "INV-2026-001",
        clientId = 1L,
        issuedOn = ISSUED,
        dueOn = DUE,
        subtotal = Money.zero(),
        discount = discount,
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
