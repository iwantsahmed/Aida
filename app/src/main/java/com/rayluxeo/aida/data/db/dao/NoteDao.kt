package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.NoteAttachmentEntity
import androidx.sqlite.db.SupportSQLiteQuery
import com.rayluxeo.aida.data.db.entity.NoteEntity
import com.rayluxeo.aida.data.db.entity.NoteTagEntity
import com.rayluxeo.aida.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * Notes, and the join that cites files from them.
 *
 * Two rules run through every query here. Deletion is soft, so every list, observation and search
 * filters `deletedAtEpochMillis IS NULL` — the FTS index is external-content and still shadows a
 * binned note, which would otherwise walk straight back into search results. And ordering is
 * uniform — pinned first, most recently touched first — so a note never changes position when the
 * screen switches filters.
 *
 * [findById] is the one deliberate exception to the soft-delete filter: restoring a note has to
 * be able to read it while it is in the bin.
 */
@Dao
abstract class NoteDao {

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY isPinned DESC, updatedAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeAll(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAtEpochMillis IS NULL AND isPinned = 1
        ORDER BY updatedAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observePinned(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAtEpochMillis IS NULL AND clientId = :clientId
        ORDER BY isPinned DESC, updatedAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeForClient(clientId: Long): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAtEpochMillis IS NULL AND projectId = :projectId
        ORDER BY isPinned DESC, updatedAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeForProject(projectId: Long): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY isPinned DESC, updatedAtEpochMillis DESC, id DESC
        """
    )
    abstract suspend fun listAll(): List<NoteEntity>

    /** Unfiltered by design: restore and undo both need to read a note that is in the bin. */
    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun findById(id: Long): NoteEntity?

    /**
     * [match] is FTS4 MATCH syntax, not a literal — callers build it with `ftsMatchOrNull`,
     * because an apostrophe or hyphen from dictation is an operator to SQLite.
     *
     * The soft-delete filter is on the join and not optional: `note_fts` is external-content and
     * keeps shadowing a note after it is binned.
     */
    @Query(
        """
        SELECT notes.* FROM notes
        JOIN note_fts ON note_fts.rowid = notes.id
        WHERE note_fts MATCH :match AND notes.deletedAtEpochMillis IS NULL
        ORDER BY notes.isPinned DESC, notes.updatedAtEpochMillis DESC, notes.id DESC
        """
    )
    abstract suspend fun search(match: String): List<NoteEntity>

    @Insert
    abstract suspend fun insert(note: NoteEntity): Long

    @Update
    abstract suspend fun update(note: NoteEntity): Int

    /**
     * Every list a filter, a sort and a search can ask for, as one statement.
     *
     * Raw for the same reason `TaskDao.observeByQuery` is: the alternative is one `@Query` per
     * combination of filter and sort, which is where a list and the count beside it start
     * disagreeing. The SQL is built by `NoteQuerySql` from a `NoteQuery`, never by a caller, so
     * the soft-delete and archive rules cannot be forgotten at a call site.
     */
    @RawQuery(observedEntities = [NoteEntity::class, NoteTagEntity::class])
    abstract fun observeByQuery(query: SupportSQLiteQuery): Flow<List<NoteEntity>>

    @RawQuery
    abstract suspend fun listByQuery(query: SupportSQLiteQuery): List<NoteEntity>

    /**
     * Filing a note away. Guarded on it being live for the reason [softDelete] is: a second
     * archive must not overwrite the moment Abu first filed it.
     */
    @Query(
        """
        UPDATE notes SET archivedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun archive(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE notes SET archivedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun unarchive(id: Long, atEpochMillis: Long): Int

    /** Clears a link the moment its target goes, for the links SQLite is not enforcing for us. */
    @Query("UPDATE notes SET taskId = :taskId, updatedAtEpochMillis = :atEpochMillis WHERE id = :id")
    abstract suspend fun setTaskLink(id: Long, taskId: Long?, atEpochMillis: Long): Int

    @Query("UPDATE notes SET clientId = :clientId, updatedAtEpochMillis = :atEpochMillis WHERE id = :id")
    abstract suspend fun setClientLink(id: Long, clientId: Long?, atEpochMillis: Long): Int

    @Query("UPDATE notes SET projectId = :projectId, updatedAtEpochMillis = :atEpochMillis WHERE id = :id")
    abstract suspend fun setProjectLink(id: Long, projectId: Long?, atEpochMillis: Long): Int

    // ---------------------------------------------------------------------------- tags

    /**
     * The tags on a page of notes, read in one query rather than one per note.
     *
     * A query per row is what turns a long list into a stutter; this is the same shape
     * `TaskDao` uses to read a page's tags, and the row type carries the note id so the caller
     * can group without a second lookup.
     */
    @Query(
        """
        SELECT nt.noteId AS noteId, t.id AS id, t.name AS name
        FROM note_tags nt
        JOIN tags t ON t.id = nt.tagId
        WHERE nt.noteId IN (:noteIds)
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    abstract suspend fun tagsForNotes(noteIds: List<Long>): List<NoteTagRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun linkTag(link: NoteTagEntity): Long

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    abstract suspend fun unlinkTag(noteId: Long, tagId: Long)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    abstract suspend fun clearTags(noteId: Long)

    /** Replaces a note's whole tag set in one transaction, so a screen never sees it half-applied. */
    @Transaction
    open suspend fun replaceTags(noteId: Long, tagIds: Collection<Long>) {
        clearTags(noteId)
        tagIds.forEach { linkTag(NoteTagEntity(noteId = noteId, tagId = it)) }
    }

    @Query(
        """
        UPDATE notes SET isPinned = :pinned, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setPinned(id: Long, pinned: Boolean, atEpochMillis: Long): Int

    /** Guarded on the row being live, so a second delete reports nothing changed rather than
     *  overwriting the time Abu first binned it. */
    @Query(
        """
        UPDATE notes SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE notes SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, atEpochMillis: Long): Int

    /**
     * The attachment list, joined through to `files`.
     *
     * The `files` table belongs to another DAO. It is queried directly here rather than through
     * that DAO because Room cannot join across two DAOs, and a second copy of its row type would
     * be the thing that drifts. Nothing here writes to `files`.
     *
     * A file that was deleted drops out of the list rather than showing as a row with no name.
     */
    @Query(
        """
        SELECT a.id AS id, a.noteId AS noteId, a.fileId AS fileId, a.position AS position,
               a.createdAtEpochMillis AS createdAtEpochMillis,
               f.displayName AS displayName, f.mimeType AS mimeType,
               f.sizeBytes AS sizeBytes, f.relativePath AS relativePath
        FROM note_attachments AS a
        JOIN files AS f ON f.id = a.fileId
        WHERE a.noteId = :noteId AND f.deletedAtEpochMillis IS NULL
        ORDER BY a.position ASC, a.id ASC
        """
    )
    abstract fun observeAttachments(noteId: Long): Flow<List<NoteAttachmentRow>>

    @Query(
        """
        SELECT a.id AS id, a.noteId AS noteId, a.fileId AS fileId, a.position AS position,
               a.createdAtEpochMillis AS createdAtEpochMillis,
               f.displayName AS displayName, f.mimeType AS mimeType,
               f.sizeBytes AS sizeBytes, f.relativePath AS relativePath
        FROM note_attachments AS a
        JOIN files AS f ON f.id = a.fileId
        WHERE a.id = :id
        """
    )
    abstract suspend fun attachmentById(id: Long): NoteAttachmentRow?

    @Query("SELECT COUNT(*) FROM note_attachments WHERE noteId = :noteId AND fileId = :fileId")
    abstract suspend fun countAttachment(noteId: Long, fileId: Long): Int

    /** Dense from zero, so a list drawn in [NoteAttachmentRow.position] order has no gaps. */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM note_attachments WHERE noteId = :noteId")
    abstract suspend fun nextAttachmentPosition(noteId: Long): Int

    @Insert
    abstract suspend fun insertAttachment(attachment: NoteAttachmentEntity): Long

    @Query("DELETE FROM note_attachments WHERE noteId = :noteId AND fileId = :fileId")
    abstract suspend fun deleteAttachment(noteId: Long, fileId: Long): Int

    /**
     * Attaches a file to a note, once.
     *
     * The duplicate check and the position it hands out have to see the same table, or two files
     * attached together both take position 0 and one of them is refused by the unique index at
     * the last moment. Returns null when the pair is already there — a refusal the repository can
     * word, rather than a constraint exception thrown out of the DAO.
     */
    @Transaction
    open suspend fun attachFile(noteId: Long, fileId: Long, atEpochMillis: Long): Long? {
        if (countAttachment(noteId, fileId) > 0) return null
        return insertAttachment(
            NoteAttachmentEntity(
                noteId = noteId,
                fileId = fileId,
                position = nextAttachmentPosition(noteId),
                createdAtEpochMillis = atEpochMillis,
            ),
        )
    }
}

/**
 * One attachment, already joined to the file it cites.
 *
 * A projection rather than an `@Embedded` relation: the screen needs a file's name and size, not
 * the whole file row with its extracted text, which for a scanned document is the largest column
 * in the database.
 */
data class NoteAttachmentRow(
    val id: Long,
    val noteId: Long,
    val fileId: Long,
    val position: Int,
    val createdAtEpochMillis: Long,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String,
)

/** One row of [NoteDao.tagsForNotes]: a tag, plus which note it was found on. */
data class NoteTagRow(
    val noteId: Long,
    val id: Long,
    val name: String,
)
