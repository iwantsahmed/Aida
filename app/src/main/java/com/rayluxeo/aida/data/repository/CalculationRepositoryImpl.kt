package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.data.db.dao.CalculationDao
import com.rayluxeo.aida.domain.model.CalculationRecord
import com.rayluxeo.aida.domain.repository.CalculationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class CalculationRepositoryImpl @Inject constructor(
    private val dao: CalculationDao,
    private val dispatchers: DispatcherProvider,
) : CalculationRepository {

    override fun observeRecent(limit: Int): Flow<List<CalculationRecord>> =
        dao.observeRecent(limit)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    /** An empty history is a normal state, not a failure, so it comes back as a null success. */
    override suspend fun mostRecent(): AppResult<CalculationRecord?> =
        storageCall(dispatchers, "load the last calculation") { dao.mostRecent()?.toDomain() }

    override suspend fun record(record: CalculationRecord): AppResult<CalculationRecord> =
        storageCall(dispatchers, "record calculation") {
            val id = dao.insert(record.copy(id = 0L).toEntity())
            record.copy(id = id)
        }

    override suspend fun clear(): AppResult<Unit> =
        storageCall(dispatchers, "clear calculation history") { dao.deleteAll() }
}
