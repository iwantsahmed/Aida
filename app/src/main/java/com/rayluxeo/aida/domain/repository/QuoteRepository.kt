package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteStatus
import kotlinx.coroutines.flow.Flow

/**
 * Prices Abu has offered.
 *
 * Shaped like [InvoiceRepository] deliberately: same line-and-header write, same refusal of a
 * document whose totals disagree with its lines, same treatment of soft deletion and of numbers
 * that have already been shown to a client.
 *
 * [markConverted] is the one operation a quote has that an invoice does not. It records which
 * bill the quote became; voiding that bill later leaves the quote standing, because the quote is
 * the evidence of what was agreed.
 */
interface QuoteRepository {

    fun observeAll(): Flow<List<Quote>>

    fun observeForClient(clientId: Long): Flow<List<Quote>>

    /** Offers still in play: drafted or sent, not yet turned into a bill. */
    fun observeOpen(): Flow<List<Quote>>

    suspend fun byId(id: Long): AppResult<Quote>

    suspend fun byNumber(number: String): AppResult<Quote>

    /** Matches the number and the notes. A blank query returns every live quote. */
    suspend fun search(query: String?): AppResult<List<Quote>>

    suspend fun create(quote: Quote): AppResult<Quote>

    /** Allocates the next free number for the year the quote was issued in, inside the insert. */
    suspend fun createWithNextNumber(quote: Quote): AppResult<Quote>

    suspend fun update(quote: Quote): AppResult<Quote>

    suspend fun setStatus(id: Long, status: QuoteStatus): AppResult<Quote>

    /** Records that this quote became [invoiceId], and accepts it in the same write. */
    suspend fun markConverted(id: Long, invoiceId: Long): AppResult<Quote>

    suspend fun softDelete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Quote>

    /** Permanent. The lines go with it. */
    suspend fun delete(id: Long): AppResult<Unit>
}
