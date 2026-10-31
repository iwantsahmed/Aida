package com.rayluxeo.aida.data.repository

import android.content.Context
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.backup.BackupEnvelope
import com.rayluxeo.aida.data.backup.DataExport
import com.rayluxeo.aida.data.backup.DataExportSerializer
import com.rayluxeo.aida.data.backup.toExport
import com.rayluxeo.aida.data.crypto.PortableBackupCipher
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.BackupMetadata
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import com.rayluxeo.aida.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup, restore and export, all against the single on-device database.
 *
 * This lives in `data/` and so may hold [MiraDatabase] and its DAOs directly (AGENTS.md §8). It
 * is the one place that reads the raw database and preferences files, because an encrypted backup
 * is a faithful snapshot of exactly those bytes rather than a re-serialisation that could drift
 * from the schema. The deterministic shaping — the envelope, the JSON and the CSV — is factored
 * out into `data/backup/`, where it is unit-tested without a device.
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: MiraDatabase,
    @ApplicationContext private val context: Context,
    private val portableBackupCipher: PortableBackupCipher,
    private val activityLog: ActivityLogRepository,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : BackupRepository {

    override suspend fun createEncryptedBackup(
        passphrase: String,
        outputStream: OutputStream,
    ): AppResult<BackupMetadata> = withContext(dispatchers.io) {
        val now = clock.now()

        // Fold the write-ahead log back into the main database file and truncate it, so the single
        // file read below is a complete, self-contained snapshot rather than a base missing its
        // most recent writes. A checkpoint failure is not fatal: the file is still readable, it
        // may simply be read alongside a non-empty -wal, which restore recreates anyway.
        runCatching {
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        }

        val dbFile = databaseFile()
        val dbBytes = try {
            dbFile.readBytes()
        } catch (e: Exception) {
            return@withContext failure(MiraErrorCode.STORAGE, "Failed to read database file: ${e.message}", e)
        }

        val prefsFile = preferencesFile()
        val prefsBytes = if (prefsFile.exists()) runCatching { prefsFile.readBytes() }.getOrNull() else null

        // Counts are metadata only — the backup's fidelity is the bytes above. They are read from
        // the same soft-delete-filtered DAO queries the export uses, so they match what Abu sees.
        val taskCount = database.taskDao().listAllForBackup().size
        val noteCount = database.noteDao().listAll().size
        val invoiceCount = database.invoiceDao().observeAll().first().size

        val metadata = BackupMetadata(
            miraDbVersion = MiraDatabase.VERSION,
            createdAt = now,
            taskCount = taskCount,
            noteCount = noteCount,
            invoiceCount = invoiceCount,
        )

        val envelope = BackupEnvelope.create(
            miraDbVersion = metadata.miraDbVersion,
            appVersion = metadata.appVersion,
            createdAtEpochMillis = now.toEpochMilli(),
            deviceModel = metadata.deviceModel,
            databaseName = MiraDatabase.NAME,
            databaseBytes = dbBytes,
            preferencesFileName = if (prefsBytes != null) PREFERENCES_FILE_NAME else null,
            preferencesBytes = prefsBytes,
            taskCount = taskCount,
            noteCount = noteCount,
            invoiceCount = invoiceCount,
        )

        val plainBytes = BackupEnvelope.encode(envelope)
        val encryptedRes = portableBackupCipher.encryptPayload(passphrase, plainBytes)
        val encryptedBytes = (encryptedRes as? AppResult.Success)?.value
            ?: return@withContext encryptedRes as AppResult.Failure

        try {
            outputStream.write(encryptedBytes)
            outputStream.flush()
        } catch (e: Exception) {
            return@withContext failure(MiraErrorCode.STORAGE, "Failed to write backup stream: ${e.message}", e)
        }

        activityLog.record(
            ActivityEntry(
                category = ActivityCategory.BACKUP,
                action = "backup.created",
                summary = "Encrypted portable backup created ($taskCount tasks, $noteCount notes, " +
                    "$invoiceCount invoices)",
                occurredAt = now,
            )
        )
        metadata.asSuccess()
    }

    /**
     * Decrypts and validates a `.mbak`, then replaces the live database and preferences files with
     * its contents.
     *
     * A backup from a newer schema than this build knows is refused with
     * [MiraErrorCode.VALIDATION] rather than written over Abu's data: Room only migrates forward,
     * so an older build cannot read a newer file and attempting it would corrupt the database.
     * A backup from an older schema is allowed — Room migrates it forward on the next open.
     *
     * **A restart is required for the restore to take effect.** The files on disk are replaced
     * here, but the already-open database handle and the in-memory DataStore cache still hold the
     * pre-restore state and would overwrite the restored preferences file on their next write.
     * The database is therefore closed and the stale WAL/SHM side files removed, no further write
     * is made to the database (so nothing lands in the restored file before the process restarts),
     * and the caller is responsible for prompting Abu to restart — `ui/backup` does this.
     */
    override suspend fun restoreEncryptedBackup(
        passphrase: String,
        inputStream: InputStream,
    ): AppResult<BackupMetadata> = withContext(dispatchers.io) {
        val backupBytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            return@withContext failure(MiraErrorCode.STORAGE, "Failed to read backup stream: ${e.message}", e)
        }

        val decryptRes = portableBackupCipher.decryptPayload(passphrase, backupBytes)
        val plainBytes = (decryptRes as? AppResult.Success)?.value
            ?: return@withContext decryptRes as AppResult.Failure

        val envelope = try {
            BackupEnvelope.decode(plainBytes)
        } catch (e: Exception) {
            return@withContext failure(MiraErrorCode.VALIDATION, "Invalid or corrupted backup payload: ${e.message}", e)
        }

        if (envelope.envelopeVersion > BackupEnvelope.ENVELOPE_VERSION) {
            return@withContext failure(
                MiraErrorCode.VALIDATION,
                "Unsupported backup format version ${envelope.envelopeVersion}",
            )
        }
        if (envelope.miraDbVersion > MiraDatabase.VERSION) {
            return@withContext failure(
                MiraErrorCode.VALIDATION,
                "Backup is from a newer version of Mira (schema ${envelope.miraDbVersion} > " +
                    "${MiraDatabase.VERSION}); update the app before restoring",
            )
        }

        val dbBytes = try {
            envelope.databaseBytes()
        } catch (e: Exception) {
            return@withContext failure(MiraErrorCode.VALIDATION, "Corrupted database payload in backup: ${e.message}", e)
        }
        val prefsBytes = try {
            envelope.preferenceBytes()
        } catch (e: Exception) {
            return@withContext failure(MiraErrorCode.VALIDATION, "Corrupted preferences payload in backup: ${e.message}", e)
        }

        try {
            // Close the open handle first so the overwrite does not race a live connection, then
            // drop the WAL/SHM side files whose contents belong to the database being replaced.
            database.close()

            val dbFile = databaseFile()
            dbFile.parentFile?.mkdirs()
            dbFile.writeBytes(dbBytes)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            if (prefsBytes != null) {
                val prefsFile = preferencesFile()
                prefsFile.parentFile?.mkdirs()
                prefsFile.writeBytes(prefsBytes)
            }
        } catch (e: Exception) {
            return@withContext failure(MiraErrorCode.STORAGE, "Failed to replace local data from backup: ${e.message}", e)
        }

        // Deliberately no activity-log entry: the database has just been replaced and closed, and
        // recording one would reopen it and mutate the freshly restored file before the restart.
        BackupMetadata(
            version = envelope.envelopeVersion,
            miraDbVersion = envelope.miraDbVersion,
            appVersion = envelope.appVersion,
            createdAt = Instant.ofEpochMilli(envelope.createdAtEpochMillis),
            deviceModel = envelope.deviceModel,
            taskCount = envelope.taskCount,
            noteCount = envelope.noteCount,
            invoiceCount = envelope.invoiceCount,
        ).asSuccess()
    }

    override suspend fun exportJson(outputStream: OutputStream): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            val document = DataExportSerializer.toJson(gatherExport())
            outputStream.write(document.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            Unit.asSuccess()
        } catch (e: Exception) {
            failure(MiraErrorCode.STORAGE, "Failed to export JSON: ${e.message}", e)
        }
    }

    override suspend fun exportCsv(entityName: String, outputStream: OutputStream): AppResult<Unit> =
        withContext(dispatchers.io) {
            val entity = DataExportSerializer.csvEntityOf(entityName)
                ?: return@withContext failure(
                    MiraErrorCode.VALIDATION,
                    "Unknown export entity '$entityName'",
                )
            try {
                val document = DataExportSerializer.toCsv(entity, gatherExport())
                outputStream.write(document.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                Unit.asSuccess()
            } catch (e: Exception) {
                failure(MiraErrorCode.STORAGE, "Failed to export CSV: ${e.message}", e)
            }
        }

    /**
     * Reads every live record kind the export covers. Each DAO query already excludes
     * soft-deleted rows, so nothing in the bin leaks into an export.
     */
    private suspend fun gatherExport(): DataExport = DataExport(
        exportedAtEpochMillis = clock.now().toEpochMilli(),
        tasks = database.taskDao().listAllForBackup().map { it.toExport() },
        notes = database.noteDao().listAll().map { it.toExport() },
        clients = database.clientDao().listAll().map { it.toExport() },
        invoices = database.invoiceDao().observeAll().first().map { it.toExport() },
        expenses = database.expenseDao().observeAll().first().map { it.toExport() },
        income = database.incomeDao().observeRecent(Int.MAX_VALUE).first().map { it.toExport() },
        payments = database.paymentDao().observeRecent(Int.MAX_VALUE).first().map { it.toExport() },
    )

    private fun databaseFile(): File = context.getDatabasePath(MiraDatabase.NAME)

    /**
     * The backing file of the single preferences DataStore. `preferencesDataStore` stores it at
     * `filesDir/datastore/<name>.preferences_pb`, and the name is `mira_settings`
     * ([com.rayluxeo.aida.data.prefs.MiraPreferences]).
     */
    private fun preferencesFile(): File = File(context.filesDir, "datastore/$PREFERENCES_FILE_NAME")

    private companion object {
        const val PREFERENCES_FILE_NAME = "mira_settings.preferences_pb"
    }
}
