package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.MemoryDao
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * What Mira remembers, over the memory and memory_fts tables.
 *
 * The one thing worth knowing about this class: [recall] has a side effect and [search] does not.
 * Recall counts are how Abu decides what Mira should keep, so they must count the times she used
 * a fact to answer him and nothing else — not the times he scrolled past it in a list.
 */
@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val dao: MemoryDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : MemoryRepository {

    override fun observe(): Flow<List<MemoryFact>> =
        dao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeByCategory(category: com.rayluxeo.aida.domain.model.MemoryCategory): Flow<List<MemoryFact>> =
        dao.observeByCategory(category)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeForSubject(subject: String): Flow<List<MemoryFact>> =
        dao.observeForSubject(subject)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<MemoryFact> {
        val found = storageCall(dispatchers, "load memory $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No memory with id $id")
        }
    }

    override suspend fun search(query: String?, limit: Int): AppResult<List<MemoryFact>> =
        storageCall(dispatchers, "search memory") {
            val rows = query?.let(::ftsMatchOrNull)
                ?.let { dao.search(it, limit.coerceAtLeast(1)) }
                ?: dao.listAll(limit.coerceAtLeast(1))
            rows.map { it.toDomain() }
        }

    /**
     * A query with nothing searchable in it recalls nothing. Falling back to "every fact" here
     * would stamp a recall on the whole table for a stray punctuation mark.
     */
    override suspend fun recall(query: String, limit: Int): AppResult<List<MemoryFact>> =
        storageCall(dispatchers, "recall memory") {
            val match = ftsMatchOrNull(query) ?: return@storageCall emptyList<MemoryFact>()
            dao.searchAndRecordRecall(
                match = match,
                limit = limit.coerceAtLeast(1),
                atEpochMillis = clock.now().toEpochMilli(),
            ).map { it.toDomain() }
        }

    /** A fact Mira has just been told has been recalled zero times, whatever the caller passed. */
    override suspend fun remember(fact: MemoryFact): AppResult<MemoryFact> =
        storageCall(dispatchers, "remember fact") {
            val clean = fact.copy(id = 0L, deletedAt = null, recallCount = 0, lastRecalledAt = null)
            clean.copy(id = dao.insert(clean.toEntity()))
        }

    override suspend fun update(fact: MemoryFact): AppResult<MemoryFact> {
        val updated = storageCall(dispatchers, "update memory ${fact.id}") {
            dao.update(fact.copy(updatedAt = clock.now()).toEntity())
        }
        return when (updated) {
            is AppResult.Failure -> updated
            is AppResult.Success ->
                if (updated.value > 0) byId(fact.id)
                else failure(MiraErrorCode.NOT_FOUND, "No memory with id ${fact.id}")
        }
    }

    override suspend fun setPinned(id: Long, isPinned: Boolean): AppResult<MemoryFact> {
        val count = storageCall(dispatchers, "pin memory $id") {
            dao.setPinned(id, isPinned, clock.now().toEpochMilli())
        }
        return when (count) {
            is AppResult.Failure -> count
            is AppResult.Success ->
                if (count.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No memory with id $id")
        }
    }

    override suspend fun updateCategory(
        id: Long,
        category: com.rayluxeo.aida.domain.model.MemoryCategory,
    ): AppResult<MemoryFact> {
        val count = storageCall(dispatchers, "update memory category $id") {
            dao.updateCategory(id, category, clock.now().toEpochMilli())
        }
        return when (count) {
            is AppResult.Failure -> count
            is AppResult.Success ->
                if (count.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No memory with id $id")
        }
    }

    override suspend fun forget(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "forget memory $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }.orNotFound("No live memory with id $id")

    override suspend fun restore(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "restore memory $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }.orNotFound("No forgotten memory with id $id")
}
