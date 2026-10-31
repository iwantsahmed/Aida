package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import kotlinx.coroutines.flow.Flow

/**
 * Abu's bills.
 *
 * An invoice is always carried with its lines, because a total shown without the lines behind it
 * is a number nobody can check. Every write therefore replaces the header and the lines together,
 * and a document whose stored totals do not add up to its own lines is refused with
 * [com.rayluxeo.aida.core.common.MiraErrorCode.VALIDATION] rather than written —
 * `BuildInvoiceTotalsUseCase` is what produces figures that pass.
 *
 * Soft deletion is what the observation and lookup methods honour: a binned invoice is invisible
 * to all of them until [restore] brings it back. Its number stays spent either way, because a
 * number that has been in front of a client cannot be handed to somebody else.
 *
 * [Invoice.amountPaid] belongs to the payments cluster. Nothing here writes it, and [setStatus]
 * refuses to move an invoice to a paid state for the same reason: only money arriving does that.
 */
interface InvoiceRepository {

    fun observeAll(): Flow<List<Invoice>>

    fun observeForClient(clientId: Long): Flow<List<Invoice>>

    /** Everything still owed, soonest due first. Voided invoices are not owed. */
    fun observeUnpaid(): Flow<List<Invoice>>

    /** Unpaid and past its due date, judged against Abu's own day rather than UTC's. */
    fun observeOverdue(): Flow<List<Invoice>>

    suspend fun byId(id: Long): AppResult<Invoice>

    suspend fun byNumber(number: String): AppResult<Invoice>

    /** Matches the number and the notes. A blank query returns every live invoice. */
    suspend fun search(query: String?): AppResult<List<Invoice>>

    /** Stores the invoice under the number it already carries, refusing a duplicate. */
    suspend fun create(invoice: Invoice): AppResult<Invoice>

    /**
     * Stores the invoice under the next free number for the year it was issued in. The number is
     * allocated inside the insert's transaction, so two invoices raised together cannot collide.
     */
    suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice>

    suspend fun update(invoice: Invoice): AppResult<Invoice>

    /** Refuses [InvoiceStatus.PAID] and [InvoiceStatus.PARTIALLY_PAID]: only a payment sets those. */
    suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice>

    suspend fun softDelete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Invoice>

    /** Permanent. The lines go with it; payments matched against it are orphaned, not erased. */
    suspend fun delete(id: Long): AppResult<Unit>
}
