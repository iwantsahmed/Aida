package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.FileSource

/**
 * A document on the phone that Mira knows about.
 *
 * The bytes are never in the database — [relativePath] points inside Mira's own storage. The row
 * is the index: what it is, where it came from, what it belongs to, and whatever text was read
 * out of it so it can be searched without opening it.
 *
 * Deleting the row is a soft delete, because a row removed while the file is still on disk would
 * leave the file unreachable and unaccounted for. Reclaiming the bytes is a separate, deliberate
 * step against the soft-deleted rows.
 */
@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["relativePath"], unique = true),
        Index(value = ["clientId"]),
        Index(value = ["projectId"]),
        Index(value = ["source"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class FileRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    val displayName: String,
    /** Relative to Mira's own files directory. Never an absolute path, which would not survive a restore. */
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val source: FileSource,
    /** Text read out of the document, if any. What makes a scanned receipt findable. */
    val extractedText: String?,
    val clientId: Long?,
    val projectId: Long?,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "file_fts")
@Fts4(contentEntity = FileRecordEntity::class)
data class FileRecordFtsEntity(
    val displayName: String,
    val extractedText: String?,
)
