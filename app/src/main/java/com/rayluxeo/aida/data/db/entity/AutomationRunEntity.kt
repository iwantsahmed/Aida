package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.AutomationOutcome

/**
 * One attempt at running an automation.
 *
 * Append-only history. [finishedAtEpochMillis] stays null while a run is in flight, which is how
 * a run interrupted by the process dying can be told apart from one that genuinely failed.
 */
@Entity(
    tableName = "automation_runs",
    foreignKeys = [
        ForeignKey(
            entity = AutomationEntity::class,
            parentColumns = ["id"],
            childColumns = ["automationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["automationId", "startedAtEpochMillis"]),
        Index(value = ["outcome"]),
    ],
)
data class AutomationRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val automationId: Long,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val outcome: AutomationOutcome,
    val detail: String?,
)
