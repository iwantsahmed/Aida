package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.FileRecordEntity
import com.rayluxeo.aida.data.db.entity.ScanRecordEntity
import com.rayluxeo.aida.domain.model.FileSource
import kotlinx.coroutines.flow.Flow

/**
 * The files Mira holds, and the scans that produced some of them.
 *
 * Every list, stream and search filters `deletedAtEpochMillis IS NULL`, with one deliberate
 * exception: [listDeleted] returns exactly the rows the others hide. That is the only reason the
 * column exists — the bytes of a soft-deleted file are still on disk, and a later pass needs to
 * find them to reclaim them. A row hard-deleted the moment Abu deletes a file would leave those
 * bytes unreachable and unaccounted for.
 *
 * `relativePath` is unique, so registration goes through [register] and renaming through
 * [updateMetadata]; both answer [PATH_TAKEN] instead of throwing a constraint violation out of
 * the insert. Each does its check and its write inside one transaction, so two imports of the
 * same document arriving together cannot both find the path free.
 */
@Dao
abstract class FileDao {

    @Query(
        """
        SELECT * FROM files
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeAll(): Flow<List<FileRecordEntity>>

    @Query(
        """
        SELECT * FROM files
        WHERE deletedAtEpochMillis IS NULL AND clientId = :clientId
        ORDER BY createdAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeForClient(clientId: Long): Flow<List<FileRecordEntity>>

    @Query(
        """
        SELECT * FROM files
        WHERE deletedAtEpochMillis IS NULL AND projectId = :projectId
        ORDER BY createdAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeForProject(projectId: Long): Flow<List<FileRecordEntity>>

    @Query(
        """
        SELECT * FROM files
        WHERE deletedAtEpochMillis IS NULL AND source = :source
        ORDER BY createdAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeBySource(source: FileSource): Flow<List<FileRecordEntity>>

    @Query(
        """
        SELECT * FROM files
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis DESC, id DESC
        LIMIT :limit
        """
    )
    abstract suspend fun listAll(limit: Int): List<FileRecordEntity>

    /**
     * A key lookup answers about any row, binned or not — restoring and reclaiming both have to
     * be able to see one. Deciding whether a binned row counts as found belongs to the caller.
     */
    @Query("SELECT * FROM files WHERE id = :id")
    abstract suspend fun findById(id: Long): FileRecordEntity?

    @Query("SELECT * FROM files WHERE relativePath = :relativePath")
    abstract suspend fun findByPath(relativePath: String): FileRecordEntity?

    /**
     * [match] is FTS4 MATCH syntax, not a literal — callers build it with `ftsMatchOrNull`,
     * because an apostrophe or a hyphen out of dictation is an operator to SQLite.
     *
     * The index is external-content over `files` and so still carries the soft-deleted rows; the
     * join back to the table is what filters them out again.
     */
    @Query(
        """
        SELECT f.* FROM files AS f
        JOIN file_fts ON file_fts.rowid = f.id
        WHERE file_fts MATCH :match AND f.deletedAtEpochMillis IS NULL
        ORDER BY f.createdAtEpochMillis DESC, f.id DESC
        LIMIT :limit
        """
    )
    abstract suspend fun search(match: String, limit: Int): List<FileRecordEntity>

    /** The rows every other read hides: soft-deleted, bytes still on disk, oldest first. */
    @Query(
        """
        SELECT * FROM files
        WHERE deletedAtEpochMillis IS NOT NULL
        ORDER BY deletedAtEpochMillis ASC, id ASC
        """
    )
    abstract suspend fun listDeleted(): List<FileRecordEntity>

    @Query("SELECT * FROM scan_records WHERE fileId = :fileId")
    abstract suspend fun scanForFile(fileId: Long): ScanRecordEntity?

    @Query(
        """
        UPDATE files
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE files
        SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, atEpochMillis: Long): Int

    /**
     * Removes the row for good, once the file itself is off the disk. The predicate is the guard:
     * only a row already in the bin can be purged, never a live one reached by a mistyped id. The
     * scan record belonging to it goes with it by cascade.
     */
    @Query("DELETE FROM files WHERE id = :id AND deletedAtEpochMillis IS NOT NULL")
    abstract suspend fun purgeDeleted(id: Long): Int

    /**
     * Registers a file, or refuses. Returns the new row id, or [PATH_TAKEN] when that path is
     * already registered — a plain answer the repository turns into a validation failure, rather
     * than a constraint violation thrown from inside the insert.
     */
    @Transaction
    open suspend fun register(file: FileRecordEntity): Long {
        if (findByPath(file.relativePath) != null) return PATH_TAKEN
        return insert(file)
    }

    /**
     * A scanned document is a file plus the record of the scan that made it, and neither half is
     * meaningful alone. Both are written or neither is: a file with no scan reads as an import,
     * and a scan with no file points at nothing.
     */
    @Transaction
    open suspend fun registerScan(
        file: FileRecordEntity,
        scan: (Long) -> ScanRecordEntity,
    ): Long {
        val fileId = register(file)
        if (fileId == PATH_TAKEN) return PATH_TAKEN
        insertScan(scan(fileId))
        return fileId
    }

    /**
     * Writes the editable fields of a file and nothing else. Returns rows changed, 0 when there
     * is no such row, or [PATH_TAKEN] when the new path belongs to a different file.
     *
     * Three fields are taken from the stored row rather than from the argument, because each is
     * owned elsewhere: `uuid` is the identity that left the phone on an export, `createdAt` is
     * when the file arrived, and `deletedAt` belongs to [softDelete] and [restore]. Letting an
     * edit carry them would let a stale copy of a record silently un-bin a file.
     */
    @Transaction
    open suspend fun updateMetadata(file: FileRecordEntity): Long {
        val stored = findById(file.id) ?: return 0L
        val clash = findByPath(file.relativePath)
        if (clash != null && clash.id != file.id) return PATH_TAKEN
        return update(
            file.copy(
                uuid = stored.uuid,
                deletedAtEpochMillis = stored.deletedAtEpochMillis,
                createdAtEpochMillis = stored.createdAtEpochMillis,
            ),
        ).toLong()
    }

    /**
     * Text recognition finishing changes two tables: the file gains the text it was read out of,
     * and its scan is stamped as recognised. Half of that would leave a scan claiming to have
     * been read with nothing to show for it. A binned file is left alone.
     */
    @Transaction
    open suspend fun recordRecognisedText(fileId: Long, text: String?, atEpochMillis: Long): Int {
        val stored = findById(fileId) ?: return 0
        if (stored.deletedAtEpochMillis != null) return 0
        val changed = setExtractedText(fileId, text, atEpochMillis)
        setScanRecognised(fileId, atEpochMillis)
        return changed
    }

    @Insert
    abstract suspend fun insert(file: FileRecordEntity): Long

    @Insert
    abstract suspend fun insertScan(scan: ScanRecordEntity): Long

    @Update
    abstract suspend fun update(file: FileRecordEntity): Int

    @Query(
        """
        UPDATE files
        SET extractedText = :text, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :fileId
        """
    )
    abstract suspend fun setExtractedText(fileId: Long, text: String?, atEpochMillis: Long): Int

    @Query("UPDATE scan_records SET recognisedAtEpochMillis = :atEpochMillis WHERE fileId = :fileId")
    abstract suspend fun setScanRecognised(fileId: Long, atEpochMillis: Long): Int

    companion object {
        /** Not a row id and not a row count: the answer when `relativePath` is already taken. */
        const val PATH_TAKEN = -1L
    }
}
