package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What happened when a document was scanned with the camera.
 *
 * Separate from [FileRecordEntity] because a scan is an event with its own outcome — how many
 * pages, whether text recognition ran and when — while the file is the lasting artefact. One
 * file has at most one scan; the unique index says so.
 */
@Entity(
    tableName = "scan_records",
    foreignKeys = [
        ForeignKey(
            entity = FileRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["fileId"], unique = true)],
)
data class ScanRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileId: Long,
    val pageCount: Int,
    /** Null until text recognition has been run over the scan. */
    val recognisedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
)
