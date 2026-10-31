package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.Money
import java.time.Instant
import java.util.Currency
import kotlinx.coroutines.flow.Flow

/**
 * Money coming in, invoiced or not.
 *
 * [totalReceivedBetween] can be asked to leave out entries that a payment already accounts for,
 * which is what stops a settled invoice being counted once as a payment and again as income.
 */
interface IncomeRepository {

    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<IncomeEntry>>

    /** The window is half-open: [from] counts, [to] does not. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<IncomeEntry>>

    fun observeForClient(clientId: Long): Flow<List<IncomeEntry>>
    /**
     * Reads a income entry **whatever its state, including one in the bin**.
     *
     * That is deliberate: restoring a binned record has to be able to read it first. It does mean
     * a caller showing the result must check `deletedAt` — every listing, search and recall path
     * already filters, so this is the one accessor where a binned row can come back.
     */
    suspend fun byId(id: Long): AppResult<IncomeEntry>

    suspend fun create(entry: IncomeEntry): AppResult<IncomeEntry>

    suspend fun update(entry: IncomeEntry): AppResult<IncomeEntry>

    suspend fun totalReceivedBetween(
        from: Instant,
        to: Instant,
        currency: Currency = Money.BDT,
        excludeEntriesFromPayments: Boolean = false,
    ): AppResult<Money>

    suspend fun delete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<IncomeEntry>

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
