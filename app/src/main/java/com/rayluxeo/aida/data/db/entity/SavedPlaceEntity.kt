package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A place Abu named, on disk.
 *
 * [labelKey] carries the unique index rather than [label] doing it, so that "Office", "office"
 * and "the office" are one place instead of three. The key is derived by
 * `SavedPlace.keyOf` before the row is written; storing it means the index and the lookup cannot
 * drift apart, which they would the moment a query started lowercasing on its own.
 *
 * No `deletedAtEpochMillis`, and that is deliberate — see `SavedPlace` for the reasoning. A
 * reminder already holds its own copy of the coordinate in `reminder_places`, so nothing armed
 * depends on this row surviving.
 */
@Entity(
    tableName = "saved_places",
    indices = [
        Index(value = ["labelKey"], unique = true),
        Index(value = ["label"]),
    ],
)
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val labelKey: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
