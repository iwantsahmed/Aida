package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row of the record-driven automation fire ledger: a record occurrence an automation has
 * already fired for.
 *
 * Append-only bookkeeping, like [AutomationRunEntity], and CASCADE-deleted with its automation so a
 * binned rule leaves no ledger behind. [source] holds a [com.rayluxeo.aida.domain.automation.RecordTriggerSource]
 * name as plain text (no overridden properties, no `@Parcelize` — §7.5/§7.6). The unique index on
 * (automationId, source, recordId, occurrenceFromEpochMillis) is the dedup key: inserting the same
 * occurrence twice is ignored, so a worker retry cannot fire or record it twice.
 */
@Entity(
    tableName = "automation_record_fires",
    foreignKeys = [
        ForeignKey(
            entity = AutomationEntity::class,
            parentColumns = ["id"],
            childColumns = ["automationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["automationId", "source", "recordId", "occurrenceFromEpochMillis"],
            unique = true,
        ),
        Index(value = ["firedAtEpochMillis"]),
    ],
)
data class AutomationFireEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val automationId: Long,
    val source: String,
    val recordId: Long,
    val occurrenceFromEpochMillis: Long,
    val firedAtEpochMillis: Long,
)
