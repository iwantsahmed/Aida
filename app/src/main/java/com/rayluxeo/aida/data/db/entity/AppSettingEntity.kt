package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Settings that belong with the data rather than with the app shell.
 *
 * The boundary matters: DataStore holds what Mira needs before the database is open — language,
 * voice, haptics, whether onboarding is done. This table holds settings that only mean anything
 * alongside the records, such as the next invoice number or a default tax rate, so they are
 * written inside the same transaction as the row that consumes them.
 */
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAtEpochMillis: Long,
)
