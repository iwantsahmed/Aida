package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.MemoryFact
import kotlinx.coroutines.flow.Flow

/**
 * What Mira has been told to remember.
 *
 * [search] and [recall] run the same query and differ in one thing: [recall] stamps the facts it
 * returns as used. Abu browsing his own memory list must not inflate the counts that tell him
 * which facts Mira actually leans on, so the browsing path is the one without the side effect.
 */
interface MemoryRepository {

    fun observe(): Flow<List<MemoryFact>>

    fun observeByCategory(category: com.rayluxeo.aida.domain.model.MemoryCategory): Flow<List<MemoryFact>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    /** Every live fact about one subject, newest first. */
    fun observeForSubject(subject: String): Flow<List<MemoryFact>>

    /**
     * Reads a memory fact **whatever its state, including one in the bin**.
     *
     * That is deliberate: restoring a binned record has to be able to read it first. It does mean
     * a caller showing the result must check `deletedAt` — every listing, search and recall path
     * already filters, so this is the one accessor where a binned row can come back.
     */
    suspend fun byId(id: Long): AppResult<MemoryFact>

    /**
     * A read with no side effect, for the memory screen. A blank query returns the facts Mira
     * leans on most, up to [limit], rather than nothing.
     */
    suspend fun search(query: String?, limit: Int = DEFAULT_LIMIT): AppResult<List<MemoryFact>>

    /**
     * What Mira calls when she is about to use what she knows. Recording the use is part of the
     * same transaction as the lookup, and is an increment in SQL rather than a read-modify-write,
     * so two recalls arriving together cannot lose one.
     */
    suspend fun recall(query: String, limit: Int = DEFAULT_LIMIT): AppResult<List<MemoryFact>>

    suspend fun remember(fact: MemoryFact): AppResult<MemoryFact>

    suspend fun update(fact: MemoryFact): AppResult<MemoryFact>

    suspend fun setPinned(id: Long, isPinned: Boolean): AppResult<MemoryFact> =
        com.rayluxeo.aida.core.common.failure(com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND)

    suspend fun updateCategory(id: Long, category: com.rayluxeo.aida.domain.model.MemoryCategory): AppResult<MemoryFact> =
        com.rayluxeo.aida.core.common.failure(com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND)

    /** Soft: a fact Mira was told to forget can be brought back, and stops being recalled at once. */
    suspend fun forget(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Unit>

    companion object {
        /** More than Mira can work into one answer is more than there is any point fetching. */
        const val DEFAULT_LIMIT = 10
    }
}
