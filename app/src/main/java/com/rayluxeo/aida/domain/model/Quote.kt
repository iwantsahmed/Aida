package com.rayluxeo.aida.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/** One priced line of a quote. Exactly the shape of an invoice line, for the same reasons. */
data class QuoteLine(
    val id: Long = 0L,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: Money,
    /** A percentage, not a fraction: 15.00 means fifteen percent. */
    val taxRatePercent: BigDecimal,
    /** Net of tax. The sum of every line's [lineTotal] is exactly [Quote.subtotal]. */
    val lineTotal: Money,
    val position: Int,
)

/**
 * A price Abu has offered, before it becomes a bill.
 *
 * [convertedInvoiceId] records that the offer was taken up. It survives the invoice being voided,
 * because the quote is the evidence of what was agreed and that is worth more than the tidiness
 * of a dangling reference.
 */
data class Quote(
    val id: Long = 0L,
    /** Blank until the quote has been stored; the repository mints it at insert. */
    val uuid: String = "",
    /** What Abu quotes on the phone, such as "QUO-2026-007". Unique across every quote. */
    val number: String,
    val clientId: Long,
    val projectId: Long? = null,
    val status: QuoteStatus = QuoteStatus.DRAFT,
    val issuedOn: LocalDate,
    val validUntil: LocalDate? = null,
    val subtotal: Money,
    val discount: Money,
    val tax: Money,
    val total: Money,
    val convertedInvoiceId: Long? = null,
    val notes: String? = null,
    val lines: List<QuoteLine> = emptyList(),
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** The currency of the document, and therefore of every amount on it. */
    val currency: Currency get() = total.currency

    /** True once this quote has become an invoice; a converted quote is not re-sendable. */
    val isConverted: Boolean get() = convertedInvoiceId != null
}
