package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.MeetingDao
import com.rayluxeo.aida.data.db.entity.MeetingEntity
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.model.MeetingNote
import com.rayluxeo.aida.domain.repository.MeetingRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Meetings over the two-table meeting schema.
 *
 * "Upcoming" cuts at the start of the current local day, not the exact instant, and resolves the
 * clock once per collection rather than on a timer. Cutting at the start of today keeps a meeting
 * created a moment ago — stamped at `now` — in the list instead of filtering it out the instant
 * the clock ticks past its start. A screen left open across the start of a meeting keeps showing
 * it until it is re-collected, which is the same trade the task lists make and is worth it on a
 * phone that is usually asleep.
 */
@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val dao: MeetingDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : MeetingRepository {

    override fun observeUpcoming(): Flow<List<Meeting>> = flow<List<Meeting>> {
        val fromStartOfToday = clock.today().atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        emitAll(dao.observeUpcoming(fromStartOfToday).toDomainList())
    }.flowOn(dispatchers.io).orOnFailure(emptyList())

    override fun observeBetween(from: Instant, to: Instant): Flow<List<Meeting>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli())
            .toDomainList()
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeForClient(clientId: Long): Flow<List<Meeting>> =
        dao.observeForClient(clientId).toDomainList().flowOn(dispatchers.io).orOnFailure(emptyList())

    override fun observeForProject(projectId: Long): Flow<List<Meeting>> =
        dao.observeForProject(projectId).toDomainList().flowOn(dispatchers.io).orOnFailure(emptyList())

    override fun observeNotes(meetingId: Long): Flow<List<MeetingNote>> =
        dao.observeNotes(meetingId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeOpenActionItems(): Flow<List<MeetingNote>> =
        dao.observeOpenActionItems()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Meeting> {
        val found = storageCall(dispatchers, "load meeting $id") { dao.findLiveById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No live meeting with id $id")
        }
    }

    override suspend fun notesFor(meetingId: Long): AppResult<List<MeetingNote>> =
        storageCall(dispatchers, "load notes of meeting $meetingId") {
            dao.notesFor(meetingId).map { it.toDomain() }
        }

    override suspend fun create(meeting: Meeting, notes: List<MeetingNote>): AppResult<Meeting> =
        storageCall(dispatchers, "create meeting") {
            val id = dao.insertWithNotes(meeting.copy(id = 0L).toEntity()) { meetingId ->
                notes.map { it.copy(id = 0L, meetingId = meetingId).toEntity() }
            }
            meeting.copy(id = id)
        }

    override suspend fun update(meeting: Meeting): AppResult<Meeting> {
        val changed = storageCall(dispatchers, "update meeting ${meeting.id}") {
            dao.update(meeting.copy(updatedAt = clock.now()).toEntity())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(meeting.id)
                else failure(MiraErrorCode.NOT_FOUND, "No meeting with id ${meeting.id}")
        }
    }

    override suspend fun addNote(note: MeetingNote): AppResult<MeetingNote> {
        val inserted = storageCall(dispatchers, "add note to meeting ${note.meetingId}") {
            dao.insertNote(note.copy(id = 0L).toEntity())
        }
        return when (inserted) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> inserted.value?.let { AppResult.Success(note.copy(id = it)) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No live meeting with id ${note.meetingId}")
        }
    }

    override suspend fun updateNote(note: MeetingNote): AppResult<MeetingNote> {
        val changed = storageCall(dispatchers, "update meeting note ${note.id}") {
            dao.updateNote(note.copy(updatedAt = clock.now()).toEntity())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) noteById(note.id)
                else failure(MiraErrorCode.NOT_FOUND, "No meeting note with id ${note.id}")
        }
    }

    override suspend fun replaceNotes(
        meetingId: Long,
        notes: List<MeetingNote>,
    ): AppResult<List<MeetingNote>> {
        val replaced = storageCall(dispatchers, "replace notes of meeting $meetingId") {
            dao.replaceNotes(meetingId, notes.map { it.copy(id = 0L, meetingId = meetingId).toEntity() })
        }
        return when (replaced) {
            is AppResult.Failure -> replaced
            is AppResult.Success -> replaced.value?.let { ids ->
                AppResult.Success(
                    notes.mapIndexed { index, note ->
                        note.copy(id = ids[index], meetingId = meetingId)
                    },
                )
            } ?: failure(MiraErrorCode.NOT_FOUND, "No live meeting with id $meetingId")
        }
    }

    override suspend fun linkNoteToTask(noteId: Long, taskId: Long?): AppResult<MeetingNote> {
        val changed = storageCall(dispatchers, "link meeting note $noteId to task $taskId") {
            dao.setNoteTask(noteId, taskId, clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) noteById(noteId)
                else failure(MiraErrorCode.NOT_FOUND, "No meeting note with id $noteId")
        }
    }

    override suspend fun removeNote(noteId: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "remove meeting note $noteId") {
            dao.deleteNoteById(noteId)
        }
        return removed.toUnit(what = "meeting note", id = noteId)
    }

    override suspend fun moveToBin(id: Long): AppResult<Unit> {
        val binned = storageCall(dispatchers, "bin meeting $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }
        return binned.toUnit(what = "live meeting", id = id)
    }

    override suspend fun restore(id: Long): AppResult<Meeting> {
        val restored = storageCall(dispatchers, "restore meeting $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }
        return when (restored) {
            is AppResult.Failure -> restored
            is AppResult.Success ->
                if (restored.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No binned meeting with id $id")
        }
    }

    override suspend fun purge(id: Long): AppResult<Unit> {
        val purged = storageCall(dispatchers, "purge meeting $id") { dao.deleteById(id) }
        return purged.toUnit(what = "meeting", id = id)
    }

    private suspend fun noteById(id: Long): AppResult<MeetingNote> {
        val found = storageCall(dispatchers, "load meeting note $id") { dao.findNoteById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No meeting note with id $id")
        }
    }

    private fun Flow<List<MeetingEntity>>.toDomainList(): Flow<List<Meeting>> =
        map { rows -> rows.map { it.toDomain() } }

    /** A write that changed no rows is a missing record, not a storage fault. */
    private fun AppResult<Int>.toUnit(what: String, id: Long): AppResult<Unit> = when (this) {
        is AppResult.Failure -> this
        is AppResult.Success ->
            if (value > 0) AppResult.Success(Unit)
            else failure(MiraErrorCode.NOT_FOUND, "No $what with id $id")
    }
}
