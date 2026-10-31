package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import java.time.Instant
import java.util.Currency
import kotlinx.coroutines.flow.Flow

/**
 * Money that arrived.
 *
 * [record] is the only way in, for both an unattached payment and one settling an invoice. There
 * is no separate "attach to invoice" call because that would leave a window in which a payment
 * exists and the invoice it settles does not know about it; whichever kind it is, one call does
 * the whole thing or none of it.
 */
interface PaymentRepository {

    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<Payment>>

    fun observeForInvoice(invoiceId: Long): Flow<List<Payment>>

    suspend fun byId(id: Long): AppResult<Payment>

    suspend fun forInvoice(invoiceId: Long): AppResult<List<Payment>>

    suspend fun search(query: String?, limit: Int = DEFAULT_LIMIT): AppResult<List<Payment>> =
        AppResult.Success(emptyList())

    /**
     * Records the payment, and where it names an invoice also adds it to that invoice's paid
     * figure and moves the invoice's status, in one transaction.
     *
     * Refused, with nothing written, when the invoice is gone, voided, in a different currency —
     * Mira holds no exchange rates and a guessed one would be worse than a refusal — or when the
     * amount is larger than what is still outstanding.
     */
    suspend fun record(payment: Payment): AppResult<Payment>

    /** The window is half-open: [from] counts, [to] does not. */
    suspend fun totalReceivedBetween(
        from: Instant,
        to: Instant,
        currency: Currency = Money.BDT,
    ): AppResult<Money>

    /**
     * A real delete, because payments carry no bin: a payment that did not happen is a mistake
     * to be removed, not a record to be archived. Where it settled an invoice, the invoice's
     * paid figure and status are walked back in the same transaction — an invoice still reading
     * PAID with nothing behind it is the failure this guards against.
     */
    suspend fun delete(id: Long): AppResult<Unit>

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
