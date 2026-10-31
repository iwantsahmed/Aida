package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * Money that arrived, as the domain sees it.
 *
 * The amount is a [Money], never a `Long` and never a `Double`: the minor-unit representation is
 * a storage detail that stops at the mapper. The currency travels inside the amount rather than
 * beside it, so a payment cannot be built carrying one currency's digits under another's code.
 *
 * [invoiceId] and [clientId] are both optional because a payment is a fact about Abu's bank
 * balance first and a settlement second — it outlives the invoice it was matched against.
 */
data class Payment(
    val id: Long = 0L,
    val invoiceId: Long? = null,
    val clientId: Long? = null,
    val amount: Money,
    val method: PaymentMethod = PaymentMethod.OTHER,
    val receivedAt: Instant,
    val reference: String? = null,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * The one rule that decides where an invoice stands once the money against it has changed.
 *
 * It lives in the domain, and not inside the DAO that applies it, because it is the part worth
 * reading and worth testing on its own; the DAO only owns the atomicity. One function serves
 * both directions — a payment recorded and a payment removed — so the two can never drift into
 * disagreeing about what "settled" means.
 */
object PaymentPolicy {

    /**
     * [current] matters only when nothing is left paid. Per [InvoiceStatus], nothing but a
     * payment moves an invoice to PAID or PARTIALLY_PAID, so undoing the last payment reverts
     * exactly those two to SENT and leaves every other status — a draft, a void — alone.
     *
     * A zero-total invoice is never called settled, which keeps an empty draft out of the paid
     * list. Mira has no credit note, so an overpayment is refused before it reaches here rather
     * than being recorded as PAID while the invoice quietly holds money it cannot return.
     */
    fun statusAfter(amountPaidMinor: Long, totalMinor: Long, current: InvoiceStatus): InvoiceStatus =
        when {
            totalMinor > 0L && amountPaidMinor >= totalMinor -> InvoiceStatus.PAID
            amountPaidMinor > 0L -> InvoiceStatus.PARTIALLY_PAID
            current == InvoiceStatus.PAID || current == InvoiceStatus.PARTIALLY_PAID ->
                InvoiceStatus.SENT
            else -> current
        }
}
