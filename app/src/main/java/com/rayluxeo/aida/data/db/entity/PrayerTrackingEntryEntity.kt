package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One prayer's tracking for one day — or a night's Tahajjud, with `prayer = "TAHAJJUD"`. The unique
 * index on (day, prayer) means there is exactly one record per prayer per day, so correcting a
 * status overwrites rather than piling up. Not soft-deleted: a log is kept, only reset wholesale.
 */
@Entity(
    tableName = "prayer_tracking_entry",
    indices = [
        Index(value = ["localDateEpochDay"]),
        Index(value = ["localDateEpochDay", "prayer"], unique = true),
    ],
)
data class PrayerTrackingEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val localDateEpochDay: Long,
    val prayer: String,
    val scheduledAtEpochMillis: Long?,
    val status: String,
    val prayedAtEpochMillis: Long?,
    val trackingSource: String,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
