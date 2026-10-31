package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.FileDao
import com.rayluxeo.aida.data.db.entity.FileRecordEntity
import com.rayluxeo.aida.data.db.entity.ScanRecordEntity
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.ScanRecord
import com.rayluxeo.aida.domain.repository.FileRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The file index over Room.
 *
 * Deleting never removes a row here. [softDelete] bins it, [listReclaimable] is how the bytes are
 * found again, and [purge] is the only way a record leaves the database — deliberately, after the
 * file itself has gone. Nothing in this class hard-deletes a live record.
 */
@Singleton
class FileRepositoryImpl @Inject constructor(
    private val dao: FileDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : FileRepository {

    override fun observeAll(): Flow<List<FileRecord>> = dao.observeAll().asRecords()

    override fun observeForClient(clientId: Long): Flow<List<FileRecord>> =
        dao.observeForClient(clientId).asRecords()

    override fun observeForProject(projectId: Long): Flow<List<FileRecord>> =
        dao.observeForProject(projectId).asRecords()

    override fun observeBySource(source: FileSource): Flow<List<FileRecord>> =
        dao.observeBySource(source).asRecords()

    override suspend fun byId(id: Long): AppResult<FileRecord> {
        val found = storageCall(dispatchers, "load file $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> {
                val row = found.value
                when {
                    row == null -> failure(MiraErrorCode.NOT_FOUND, "No file with id $id")
                    row.deletedAtEpochMillis != null ->
                        failure(MiraErrorCode.NOT_FOUND, "File $id is in the bin")
                    else -> AppResult.Success(row.toDomain())
                }
            }
        }
    }

    override suspend fun search(query: String?, limit: Int): AppResult<List<FileRecord>> =
        storageCall(dispatchers, "search files") {
            val capped = limit.coerceAtLeast(1)
            val match = query?.let(::ftsMatchOrNull)
            val rows = if (match == null) dao.listAll(capped) else dao.search(match, capped)
            rows.map { it.toDomain() }
        }

    /**
     * The uuid is minted here when the caller has none, which is every case but a restore from a
     * backup: it identifies the file outside this database, so it cannot be left to a row id that
     * only means something inside one copy of it.
     */
    override suspend fun register(file: FileRecord): AppResult<FileRecord> {
        val prepared = file.copy(id = 0L, uuid = file.uuid.ifBlank { newUuid() })
        val registered = storageCall(dispatchers, "register file ${file.relativePath}") {
            dao.register(prepared.toEntity())
        }
        return when (registered) {
            is AppResult.Failure -> registered
            is AppResult.Success -> registered.value.asRegistered(prepared)
        }
    }

    override suspend fun registerScan(file: FileRecord, pageCount: Int): AppResult<FileRecord> {
        if (pageCount < 1) {
            return failure(MiraErrorCode.VALIDATION, "A scan of $pageCount pages is not a scan")
        }
        val prepared = file.copy(id = 0L, uuid = file.uuid.ifBlank { newUuid() })
        val scannedAt = clock.now().toEpochMilli()
        val registered = storageCall(dispatchers, "register scan ${file.relativePath}") {
            dao.registerScan(prepared.toEntity()) { fileId ->
                ScanRecordEntity(
                    fileId = fileId,
                    pageCount = pageCount,
                    recognisedAtEpochMillis = null,
                    createdAtEpochMillis = scannedAt,
                )
            }
        }
        return when (registered) {
            is AppResult.Failure -> registered
            is AppResult.Success -> registered.value.asRegistered(prepared)
        }
    }

    override suspend fun update(file: FileRecord): AppResult<FileRecord> {
        val stamped = file.copy(updatedAt = clock.now())
        val changed = storageCall(dispatchers, "update file ${file.id}") {
            dao.updateMetadata(stamped.toEntity())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success -> when (changed.value) {
                FileDao.PATH_TAKEN ->
                    failure(MiraErrorCode.VALIDATION, "${file.relativePath} belongs to another file")
                0L -> failure(MiraErrorCode.NOT_FOUND, "No file with id ${file.id}")
                else -> byId(file.id)
            }
        }
    }

    override suspend fun recordRecognisedText(fileId: Long, text: String?): AppResult<FileRecord> {
        val changed = storageCall(dispatchers, "store recognised text for file $fileId") {
            dao.recordRecognisedText(fileId, text, clock.now().toEpochMilli())
        }
        return changed.thenReadBack(fileId, "No file with id $fileId")
    }

    override suspend fun scanFor(fileId: Long): AppResult<ScanRecord?> =
        storageCall(dispatchers, "load scan for file $fileId") {
            dao.scanForFile(fileId)?.toDomain()
        }

    override suspend fun softDelete(id: Long): AppResult<Unit> {
        val changed = storageCall(dispatchers, "delete file $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No live file with id $id")
        }
    }

    override suspend fun restore(id: Long): AppResult<FileRecord> {
        val changed = storageCall(dispatchers, "restore file $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }
        return changed.thenReadBack(id, "No file with id $id is in the bin")
    }

    override suspend fun listReclaimable(): AppResult<List<FileRecord>> =
        storageCall(dispatchers, "list deleted files") {
            dao.listDeleted().map { it.toDomain() }
        }

    /**
     * Refuses anything that is not already in the bin. Purging is what makes a file unaccountable
     * — once the row is gone nothing remembers the bytes existed — so it is never the first step.
     */
    override suspend fun purge(id: Long): AppResult<Unit> {
        val found = storageCall(dispatchers, "load file $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> {
                val row = found.value
                when {
                    row == null -> failure(MiraErrorCode.NOT_FOUND, "No file with id $id")
                    row.deletedAtEpochMillis == null ->
                        failure(MiraErrorCode.VALIDATION, "File $id is not in the bin")
                    else -> {
                        val purged = storageCall(dispatchers, "purge file $id") { dao.purgeDeleted(id) }
                        when (purged) {
                            is AppResult.Failure -> purged
                            is AppResult.Success -> AppResult.Success(Unit)
                        }
                    }
                }
            }
        }
    }

    private fun Flow<List<FileRecordEntity>>.asRecords(): Flow<List<FileRecord>> =
        map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    private fun Long.asRegistered(prepared: FileRecord): AppResult<FileRecord> =
        if (this == FileDao.PATH_TAKEN) {
            failure(MiraErrorCode.VALIDATION, "${prepared.relativePath} is already registered")
        } else {
            AppResult.Success(prepared.copy(id = this))
        }

    private suspend fun AppResult<Int>.thenReadBack(id: Long, missing: String): AppResult<FileRecord> =
        when (this) {
            is AppResult.Failure -> this
            is AppResult.Success ->
                if (value > 0) byId(id) else failure(MiraErrorCode.NOT_FOUND, missing)
        }

    private fun newUuid(): String = UUID.randomUUID().toString()
}
