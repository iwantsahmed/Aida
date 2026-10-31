package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.AutomationTrigger

/**
 * Something Mira does on her own.
 *
 * The trigger and action configurations are JSON strings. That is deliberate: the shape of a
 * place trigger has nothing in common with the shape of a time trigger, and a table wide enough
 * to hold every variant as columns would be mostly nulls. The cost is that Room cannot validate
 * them, so parsing is the repository's job and a malformed row is reported rather than crashing.
 */
@Entity(
    tableName = "automations",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["trigger"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String?,
    val trigger: AutomationTrigger,
    val triggerConfigJson: String,
    val actionConfigJson: String,
    val isEnabled: Boolean,
    val lastRunAtEpochMillis: Long?,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
