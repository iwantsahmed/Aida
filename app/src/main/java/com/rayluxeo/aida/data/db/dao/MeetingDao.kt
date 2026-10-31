package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.MeetingEntity
import com.rayluxeo.aida.data.db.entity.MeetingNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Meetings and the notes taken in them.
 *
 * Every read filters `deletedAtEpochMillis IS NULL`. A binned meeting stays on disk so undo can
 * restore the same row rather than a copy under a new id, which means a query that forgets the
 * filter puts a deleted meeting straight back on Abu's screen.
 *
 * The note queries filter on the *meeting's* deletion, because `meeting_notes` has no column of
 * its own: a note belongs to a moment, and when that moment is in the bin so is everything said
 * in it.
 *
 * Writes that touch both tables go through the transactions at the bottom. A meeting dictated in
 * one breath arrives as a parent and its lines together, and half of that landing on disk is
 * worse than none of it.
 */
@Dao
abstract class MeetingDao {

    @Query(
        """
        SELECT * FROM meetings
        WHERE deletedAtEpochMillis IS NULL AND startsAtEpochMillis >= :fromEpochMillis
        ORDER BY startsAtEpochMillis ASC, id ASC
        """
    )
    abstract fun observeUpcoming(fromEpochMillis: Long): Flow<List<MeetingEntity>>

    /** [toEpochMillis] is exclusive, so a day window and the next one cannot both claim midnight. */
    @Query(
        """
        SELECT * FROM meetings
        WHERE deletedAtEpochMillis IS NULL
          AND startsAtEpochMillis >= :fromEpochMillis
          AND startsAtEpochMillis < :toEpochMillis
        ORDER BY startsAtEpochMillis ASC, id ASC
        """
    )
    abstract fun observeBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<MeetingEntity>>

    @Query(
        """
        SELECT * FROM meetings
        WHERE deletedAtEpochMillis IS NULL AND clientId = :clientId
        ORDER BY startsAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeForClient(clientId: Long): Flow<List<MeetingEntity>>

    @Query(
        """
        SELECT * FROM meetings
        WHERE deletedAtEpochMillis IS NULL AND projectId = :projectId
        ORDER BY startsAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeForProject(projectId: Long): Flow<List<MeetingEntity>>

    @Query(
        """
        SELECT n.* FROM meeting_notes AS n
        JOIN meetings AS m ON m.id = n.meetingId
        WHERE n.meetingId = :meetingId AND m.deletedAtEpochMillis IS NULL
        ORDER BY n.createdAtEpochMillis ASC, n.id ASC
        """
    )
    abstract fun observeNotes(meetingId: Long): Flow<List<MeetingNoteEntity>>

    /**
     * The action items nobody has acted on. Ordered by the meeting they came out of, newest
     * first, because the ones agreed most recently are the ones still expected.
     */
    @Query(
        """
        SELECT n.* FROM meeting_notes AS n
        JOIN meetings AS m ON m.id = n.meetingId
        WHERE n.isActionItem = 1 AND n.taskId IS NULL AND m.deletedAtEpochMillis IS NULL
        ORDER BY m.startsAtEpochMillis DESC, n.createdAtEpochMillis ASC, n.id ASC
        """
    )
    abstract fun observeOpenActionItems(): Flow<List<MeetingNoteEntity>>

    @Query(
        """
        SELECT n.* FROM meeting_notes AS n
        JOIN meetings AS m ON m.id = n.meetingId
        WHERE n.meetingId = :meetingId AND m.deletedAtEpochMillis IS NULL
        ORDER BY n.createdAtEpochMillis ASC, n.id ASC
        """
    )
    abstract suspend fun notesFor(meetingId: Long): List<MeetingNoteEntity>

    @Query("SELECT * FROM meetings WHERE id = :id AND deletedAtEpochMillis IS NULL")
    abstract suspend fun findLiveById(id: Long): MeetingEntity?

    /** Binned rows included, because restoring one has to be able to find it first. */
    @Query("SELECT * FROM meetings WHERE id = :id")
    abstract suspend fun findById(id: Long): MeetingEntity?

    @Query("SELECT * FROM meeting_notes WHERE id = :id")
    abstract suspend fun findNoteById(id: Long): MeetingNoteEntity?

    @Insert
    abstract suspend fun insert(meeting: MeetingEntity): Long

    @Update
    abstract suspend fun update(meeting: MeetingEntity): Int

    @Insert
    abstract suspend fun insertNoteRow(note: MeetingNoteEntity): Long

    @Update
    abstract suspend fun updateNote(note: MeetingNoteEntity): Int

    @Query("UPDATE meeting_notes SET taskId = :taskId, updatedAtEpochMillis = :atEpochMillis WHERE id = :id")
    abstract suspend fun setNoteTask(id: Long, taskId: Long?, atEpochMillis: Long): Int

    @Query("DELETE FROM meeting_notes WHERE id = :id")
    abstract suspend fun deleteNoteById(id: Long): Int

    @Query("DELETE FROM meeting_notes WHERE meetingId = :meetingId")
    abstract suspend fun deleteNotesFor(meetingId: Long): Int

    /** Only a live meeting can be binned, so binning the same one twice reports nothing changed. */
    @Query(
        """
        UPDATE meetings SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE meetings SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, atEpochMillis: Long): Int

    /** The real delete. Notes go with it by cascade; calendar links are emptied, not removed. */
    @Query("DELETE FROM meetings WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    /**
     * Writes the meeting and the notes it arrived with in one go. [notes] is given the new
     * meeting's id because the rows cannot be stamped with it until the insert has happened, and
     * a note inserted outside this transaction could be left pointing at a meeting that rolled
     * back.
     */
    @Transaction
    open suspend fun insertWithNotes(
        meeting: MeetingEntity,
        notes: (Long) -> List<MeetingNoteEntity>,
    ): Long {
        val meetingId = insert(meeting)
        notes(meetingId).forEach { insertNoteRow(it) }
        return meetingId
    }

    /** Null when there is no live meeting to attach to, which the foreign key would reject anyway. */
    @Transaction
    open suspend fun insertNote(note: MeetingNoteEntity): Long? {
        if (findLiveById(note.meetingId) == null) return null
        return insertNoteRow(note)
    }

    /**
     * Swaps a meeting's whole set of notes. The delete and the inserts are one unit: a failure
     * part way through must not leave Abu with a meeting whose notes are half the old set.
     *
     * Null distinguishes "no such live meeting" from "replaced with nothing", which an empty
     * list could not.
     */
    @Transaction
    open suspend fun replaceNotes(meetingId: Long, notes: List<MeetingNoteEntity>): List<Long>? {
        if (findLiveById(meetingId) == null) return null
        deleteNotesFor(meetingId)
        return notes.map { insertNoteRow(it.copy(id = 0L, meetingId = meetingId)) }
    }
}
