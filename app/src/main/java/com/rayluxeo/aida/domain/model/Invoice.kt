package com.rayluxeo.aida.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/**
 * One priced line of an invoice.
 *
 * [lineTotal] is carried rather than derived on read. Quantity times unit price is where the
 * figure came from, not what it means: correcting a rate next month must not quietly restate a
 * bill Abu has already handed over.
 */
data class InvoiceLine(
    val id: Long = 0L,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: Money,
    /** A percentage, not a fraction: 15.00 means fifteen percent. */
    val taxRatePercent: BigDecimal,
    /** Net of tax. The sum of every line's [lineTotal] is exactly [Invoice.subtotal]. */
    val lineTotal: Money,
    val position: Int,
)

/**
 * A bill Abu has issued.
 *
 * The totals are stored, not recomputed, for the same reason [InvoiceLine.lineTotal] is. Keeping
 * them truthful is therefore somebody's job: `BuildInvoiceTotalsUseCase` is the only thing that
 * derives them, and the repository refuses to write a document whose totals disagree with its
 * own lines.
 *
 * Every amount here is in one currency — the one [total] carries — because an invoice that mixes
 * currencies has no meaningful total.
 */
data class Invoice(
    val id: Long = 0L,
    /**
     * Stable across exports, backups and restores, where the integer key is not. Blank on a
     * document that has never been stored; [com.rayluxeo.aida.domain.repository.InvoiceRepository]
     * mints one at insert.
     */
    val uuid: String = "",
    /** What Abu quotes on the phone, such as "INV-2026-014". Unique across every invoice. */
    val number: String,
    val clientId: Long,
    val projectId: Long? = null,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    val issuedOn: LocalDate,
    val dueOn: LocalDate,
    val subtotal: Money,
    val discount: Money,
    val tax: Money,
    val total: Money,
    /** Maintained by the payments cluster. Nothing on the invoice path writes it. */
    val amountPaid: Money,
    val notes: String? = null,
    val lines: List<InvoiceLine> = emptyList(),
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** The currency of the document, and therefore of every amount on it. */
    val currency: Currency get() = total.currency

    /** What Abu is still owed on this bill. Negative would mean he was overpaid. */
    val outstanding: Money get() = total - amountPaid
}
