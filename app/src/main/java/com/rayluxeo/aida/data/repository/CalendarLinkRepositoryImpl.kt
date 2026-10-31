package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.CalendarLinkDao
import com.rayluxeo.aida.data.db.entity.CalendarLinkEntity
import com.rayluxeo.aida.domain.model.CalendarLink
import com.rayluxeo.aida.domain.repository.CalendarLinkRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The stored half of the calendar connection.
 *
 * Nothing in here touches `CalendarContract` or asks for calendar permission — that belongs to
 * the Calendar area when it is built. This only keeps the pairings, and the copy of each event's
 * title that lets a link still read sensibly when permission has not been granted.
 */
@Singleton
class CalendarLinkRepositoryImpl @Inject constructor(
    private val dao: CalendarLinkDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : CalendarLinkRepository {

    override fun observeUpcoming(): Flow<List<CalendarLink>> = flow<List<CalendarLink>> {
        emitAll(dao.observeUpcoming(clock.now().toEpochMilli()).toDomainList())
    }.flowOn(dispatchers.io).orOnFailure(emptyList())

    override fun observeBetween(from: Instant, to: Instant): Flow<List<CalendarLink>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli())
            .toDomainList()
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeStale(): Flow<List<CalendarLink>> =
        dao.observeStale().toDomainList().flowOn(dispatchers.io).orOnFailure(emptyList())

    override suspend fun bySystemEventId(systemEventId: Long): AppResult<CalendarLink?> =
        storageCall(dispatchers, "load calendar link for system event $systemEventId") {
            dao.findBySystemEventId(systemEventId)?.toDomain()
        }

    override suspend fun forMeeting(meetingId: Long): AppResult<List<CalendarLink>> =
        storageCall(dispatchers, "load calendar links of meeting $meetingId") {
            dao.linksForMeeting(meetingId).map { it.toDomain() }
        }

    override suspend fun forTask(taskId: Long): AppResult<List<CalendarLink>> =
        storageCall(dispatchers, "load calendar links of task $taskId") {
            dao.linksForTask(taskId).map { it.toDomain() }
        }

    override suspend fun forProject(projectId: Long): AppResult<List<CalendarLink>> =
        storageCall(dispatchers, "load calendar links of project $projectId") {
            dao.linksForProject(projectId).map { it.toDomain() }
        }

    override suspend fun link(link: CalendarLink): AppResult<CalendarLink> {
        val inserted = storageCall(dispatchers, "link system event ${link.systemEventId}") {
            dao.linkIfAbsent(link.copy(id = 0L).toEntity())
        }
        return when (inserted) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> inserted.value?.let { AppResult.Success(link.copy(id = it)) }
                ?: failure(
                    MiraErrorCode.VALIDATION,
                    "System event ${link.systemEventId} is already linked",
                )
        }
    }

    override suspend fun update(link: CalendarLink): AppResult<CalendarLink> {
        val stamped = link.copy(updatedAt = clock.now())
        val changed = storageCall(dispatchers, "update calendar link ${link.id}") {
            dao.update(stamped.toEntity())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) AppResult.Success(stamped)
                else failure(MiraErrorCode.NOT_FOUND, "No calendar link with id ${link.id}")
        }
    }

    override suspend fun unlink(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "unlink calendar link $id") { dao.deleteById(id) }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No calendar link with id $id")
        }
    }

    override suspend fun purgeStale(): AppResult<Int> =
        storageCall(dispatchers, "purge stale calendar links") { dao.deleteStale() }

    private fun Flow<List<CalendarLinkEntity>>.toDomainList(): Flow<List<CalendarLink>> =
        map { rows -> rows.map { it.toDomain() } }
}
