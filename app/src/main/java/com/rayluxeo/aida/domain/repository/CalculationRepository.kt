package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.CalculationRecord
import kotlinx.coroutines.flow.Flow

/**
 * What Mira has already worked out for Abu.
 *
 * Append-only: there is no update and no single delete, because a record of an answer he was
 * given is only worth keeping if it cannot be quietly rewritten afterwards. [clear] is the one
 * way to remove any of it, and it removes all of it.
 */
interface CalculationRepository {

    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<CalculationRecord>>

    /** What "what was that again" resolves to. Null when Mira has worked nothing out yet. */
    suspend fun mostRecent(): AppResult<CalculationRecord?>

    suspend fun record(record: CalculationRecord): AppResult<CalculationRecord>

    suspend fun clear(): AppResult<Unit>

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
