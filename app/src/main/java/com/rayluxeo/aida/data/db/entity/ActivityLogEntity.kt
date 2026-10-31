package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.ActivityCategory

/**
 * What Mira did, in order.
 *
 * Append-only and never soft-deleted: the point of a log is that it is not edited. It carries a
 * loose reference — a table name and a row id — rather than a foreign key, because it has to
 * survive the row it describes being deleted. That is deliberate; a log that cascades away with
 * its subject cannot answer "what happened to that invoice".
 */
@Entity(
    tableName = "activity_log",
    indices = [
        Index(value = ["occurredAtEpochMillis"]),
        Index(value = ["category"]),
        Index(value = ["subjectTable", "subjectId"]),
    ],
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: ActivityCategory,
    val action: String,
    val summary: String,
    val subjectTable: String?,
    val subjectId: Long?,
    val occurredAtEpochMillis: Long,
)
