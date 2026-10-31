package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.model.MeetingNote
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Meetings and everything said in them.
 *
 * A binned meeting is invisible to every read here. It is still on disk — [restore] depends on
 * that — but nothing short of restoring it brings it back into a list, a lookup or a note query.
 */
interface MeetingRepository {

    /** Everything still to come, soonest first, resolved against the clock at collection time. */
    fun observeUpcoming(): Flow<List<Meeting>>

    /** [to] is exclusive, so consecutive windows cannot both claim the instant between them. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<Meeting>>

    fun observeForClient(clientId: Long): Flow<List<Meeting>>

    fun observeForProject(projectId: Long): Flow<List<Meeting>>

    fun observeNotes(meetingId: Long): Flow<List<MeetingNote>>

    /**
     * Action items from every live meeting that have not become tasks yet. Turning one into a
     * task belongs to the tasks side of the app; this only reports which are still waiting.
     */
    fun observeOpenActionItems(): Flow<List<MeetingNote>>

    suspend fun byId(id: Long): AppResult<Meeting>

    suspend fun notesFor(meetingId: Long): AppResult<List<MeetingNote>>

    /** The meeting and [notes] are written together or not at all. */
    suspend fun create(meeting: Meeting, notes: List<MeetingNote> = emptyList()): AppResult<Meeting>

    suspend fun update(meeting: Meeting): AppResult<Meeting>

    suspend fun addNote(note: MeetingNote): AppResult<MeetingNote>

    suspend fun updateNote(note: MeetingNote): AppResult<MeetingNote>

    /** Replaces the meeting's whole set of notes in one transaction. */
    suspend fun replaceNotes(meetingId: Long, notes: List<MeetingNote>): AppResult<List<MeetingNote>>

    /**
     * Records that an action item has become a task, or clears the link with a null [taskId].
     * Creating the task itself is not done here.
     */
    suspend fun linkNoteToTask(noteId: Long, taskId: Long?): AppResult<MeetingNote>

    suspend fun removeNote(noteId: Long): AppResult<Unit>

    /** Soft delete. The row survives so [restore] returns the same meeting, not a copy of it. */
    suspend fun moveToBin(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Meeting>

    /** The permanent delete. Notes go with it; calendar links are emptied rather than removed. */
    suspend fun purge(id: Long): AppResult<Unit>
}
