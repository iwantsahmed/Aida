package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.BackupMetadata
import java.io.InputStream
import java.io.OutputStream

/**
 * Taking Abu's data off the phone and bringing it back.
 *
 * Two shapes, for two jobs. An **encrypted backup** ([createEncryptedBackup] /
 * [restoreEncryptedBackup]) is a faithful whole-database snapshot meant to be read back by Mira,
 * sealed with the passphrase so it is safe to keep anywhere. A **plain export** ([exportJson] /
 * [exportCsv]) is for Abu to read in another program and is never restored from.
 */
interface BackupRepository {

    /**
     * Writes an encrypted, self-contained snapshot of the database and preferences to [outputStream].
     * The returned [BackupMetadata] carries the record counts and the schema version it was taken at.
     */
    suspend fun createEncryptedBackup(passphrase: String, outputStream: OutputStream): AppResult<BackupMetadata>

    /**
     * Decrypts [inputStream], refuses a backup from a newer schema than this build can read, and
     * replaces the live database and preferences files with its contents.
     *
     * **A successful restore requires the app to be restarted to take effect** — the open database
     * handle and the in-memory preferences cache still hold the pre-restore state until the process
     * restarts. The returned [BackupMetadata] describes the backup that was restored. Callers must
     * prompt Abu to restart.
     */
    suspend fun restoreEncryptedBackup(passphrase: String, inputStream: InputStream): AppResult<BackupMetadata>

    /** Writes every live record (soft-deleted rows excluded) as one JSON document to [outputStream]. */
    suspend fun exportJson(outputStream: OutputStream): AppResult<Unit>

    /**
     * Writes the live rows of one entity as RFC-4180 CSV to [outputStream]. [entityName] is one of
     * the identifiers the export supports ("tasks", "notes", "clients", "invoices", "expenses",
     * "income", "payments"); an unknown name is refused with
     * [com.rayluxeo.aida.core.common.MiraErrorCode.VALIDATION].
     */
    suspend fun exportCsv(entityName: String, outputStream: OutputStream): AppResult<Unit>
}
