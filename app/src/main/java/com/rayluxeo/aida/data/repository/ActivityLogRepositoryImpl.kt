package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.data.db.dao.ActivityLogDao
import com.rayluxeo.aida.data.db.entity.ActivityLogEntity
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.ActivitySubject
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The activity log over Room.
 *
 * There is no update and no delete, here or in the DAO. The entry's `occurredAt` comes from the
 * caller rather than from the clock in this class: what is being logged already happened, and
 * stamping it at the moment it reaches storage would quietly reorder anything recorded late.
 */
@Singleton
class ActivityLogRepositoryImpl @Inject constructor(
    private val dao: ActivityLogDao,
    private val dispatchers: DispatcherProvider,
) : ActivityLogRepository {

    override fun observeRecent(limit: Int): Flow<List<ActivityEntry>> =
        dao.observeRecent(limit.coerceAtLeast(1)).asEntries()

    override fun observeByCategory(category: ActivityCategory, limit: Int): Flow<List<ActivityEntry>> =
        dao.observeByCategory(category, limit.coerceAtLeast(1)).asEntries()

    override fun observeForSubject(subject: ActivitySubject): Flow<List<ActivityEntry>> =
        dao.observeForSubject(subject.table, subject.id).asEntries()

    override suspend fun between(from: Instant, to: Instant): AppResult<List<ActivityEntry>> =
        storageCall(dispatchers, "read activity between $from and $to") {
            dao.listBetween(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain() }
        }

    override suspend fun record(entry: ActivityEntry): AppResult<ActivityEntry> =
        storageCall(dispatchers, "record activity ${entry.action}") {
            val id = dao.insert(entry.copy(id = 0L).toEntity())
            entry.copy(id = id)
        }

    private fun Flow<List<ActivityLogEntity>>.asEntries(): Flow<List<ActivityEntry>> =
        map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())
}
