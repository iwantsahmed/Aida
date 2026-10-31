package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.ScanRecord
import kotlinx.coroutines.flow.Flow

/**
 * The index of the documents Mira holds on disk.
 *
 * Two rules shape this port and neither is negotiable further down:
 *
 * 1. **Deleting is always soft.** [softDelete] hides the record; the bytes stay where they are.
 *    [listReclaimable] is how a later pass finds those bytes, and [purge] is the one call that
 *    removes a record for good — after its file has actually been deleted from disk, never
 *    before. Nothing else can hard-delete a file record.
 * 2. **A path is registered once.** Registering a `relativePath` that is already known fails with
 *    [com.rayluxeo.aida.core.common.MiraErrorCode.VALIDATION] rather than creating a second
 *    record for one file.
 */
interface FileRepository {

    fun observeAll(): Flow<List<FileRecord>>

    fun observeForClient(clientId: Long): Flow<List<FileRecord>>

    fun observeForProject(projectId: Long): Flow<List<FileRecord>>

    fun observeBySource(source: FileSource): Flow<List<FileRecord>>

    suspend fun byId(id: Long): AppResult<FileRecord>

    /** Full-text search over display name and extracted text. A blank query lists recent files. */
    suspend fun search(query: String?, limit: Int = DEFAULT_SEARCH_LIMIT): AppResult<List<FileRecord>>

    /**
     * Registers a file that is already on disk. The returned record carries the assigned id and
     * uuid; the caller's copy of either is ignored.
     */
    suspend fun register(file: FileRecord): AppResult<FileRecord>

    /** Registers a scanned document and the record of the scan together, or neither. */
    suspend fun registerScan(file: FileRecord, pageCount: Int): AppResult<FileRecord>

    suspend fun update(file: FileRecord): AppResult<FileRecord>

    /** Stores the text read out of a scan and stamps that scan as recognised, in one write. */
    suspend fun recordRecognisedText(fileId: Long, text: String?): AppResult<FileRecord>

    suspend fun scanFor(fileId: Long): AppResult<ScanRecord?>

    suspend fun softDelete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<FileRecord>

    /** The soft-deleted records, oldest first: the work list for reclaiming disk space. */
    suspend fun listReclaimable(): AppResult<List<FileRecord>>

    /**
     * Removes a soft-deleted record for good. Call it only once the file itself is off the disk,
     * because nothing else remembers that the file was ever there. A record that is not in the
     * bin is refused.
     */
    suspend fun purge(id: Long): AppResult<Unit>

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 50
    }
}
