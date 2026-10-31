package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.NoteEntity
import com.rayluxeo.aida.data.db.entity.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Recordings Abu kept.
 *
 * Deletion is soft, so every observation filters `deletedAtEpochMillis IS NULL`; [findById] is
 * unfiltered because restore has to read a binned row.
 *
 * Writing a recording up as a note touches two tables, and Room scopes a `@Transaction` to the
 * DAO that declares it. That is why [insertNote] and [noteById] are here rather than on NoteDao:
 * a note that exists while the recording still says it was never written up is exactly the
 * half-written state [saveAsNote] is designed to make impossible.
 */
@Dao
abstract class VoiceNoteDao {

    @Query(
        """
        SELECT * FROM voice_notes
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY recordedAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeAll(): Flow<List<VoiceNoteEntity>>

    /** Oldest first: a backlog is worked through in the order it arrived. */
    @Query(
        """
        SELECT * FROM voice_notes
        WHERE deletedAtEpochMillis IS NULL AND (transcript IS NULL OR transcript = '')
        ORDER BY recordedAtEpochMillis ASC, id ASC
        """
    )
    abstract fun observeAwaitingTranscript(): Flow<List<VoiceNoteEntity>>

    /** Unfiltered by design: restore has to be able to read a binned recording. */
    @Query("SELECT * FROM voice_notes WHERE id = :id")
    abstract suspend fun findById(id: Long): VoiceNoteEntity?

    @Insert
    abstract suspend fun insert(voiceNote: VoiceNoteEntity): Long

    @Update
    abstract suspend fun update(voiceNote: VoiceNoteEntity): Int

    @Query(
        """
        UPDATE voice_notes SET transcript = :transcript, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setTranscript(id: Long, transcript: String, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE voice_notes SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE voice_notes SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, atEpochMillis: Long): Int

    @Query("UPDATE voice_notes SET noteId = :noteId, updatedAtEpochMillis = :atEpochMillis WHERE id = :id")
    abstract suspend fun linkNote(id: Long, noteId: Long, atEpochMillis: Long): Int

    /** See the class KDoc: `notes` is written here only so [saveAsNote] can be one transaction. */
    @Insert
    abstract suspend fun insertNote(note: NoteEntity): Long

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun noteById(id: Long): NoteEntity?

    /**
     * Writes the recording up as a note and links the two together, or writes nothing at all.
     *
     * Returns the id of the note — the existing one when this recording was already written up,
     * so pressing save twice cannot leave Abu with two copies of the same words — or null when
     * there is no such recording, in which case the note was never inserted.
     */
    @Transaction
    open suspend fun saveAsNote(voiceNoteId: Long, note: NoteEntity): Long? {
        val recording = findById(voiceNoteId) ?: return null
        recording.noteId?.let { return it }
        val noteId = insertNote(note)
        linkNote(id = voiceNoteId, noteId = noteId, atEpochMillis = note.updatedAtEpochMillis)
        return noteId
    }
}
