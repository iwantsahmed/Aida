package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every sum Mira has worked out, kept so Abu can ask "what was that again".
 *
 * The result is stored as the exact text that was shown to him, not as a number to be formatted
 * again later: re-deriving it from a stored value would risk showing a different answer than the
 * one he was given. Append-only, like the activity log.
 */
@Entity(
    tableName = "calculation_records",
    indices = [Index(value = ["createdAtEpochMillis"])],
)
data class CalculationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val expression: String,
    val resultText: String,
    val languageTag: String,
    val createdAtEpochMillis: Long,
)
