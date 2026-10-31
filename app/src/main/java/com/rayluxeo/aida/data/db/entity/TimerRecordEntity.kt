package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.TimerState

/**
 * A countdown.
 *
 * [endsAtEpochMillis] is stored rather than a remaining duration, so a running timer stays
 * correct across a process death: the answer comes from the wall clock, not from whatever the
 * app last managed to write down. [remainingMillisWhenPaused] is only meaningful while paused.
 */
@Entity(
    tableName = "timer_records",
    indices = [
        Index(value = ["state"]),
        Index(value = ["endsAtEpochMillis"]),
    ],
)
data class TimerRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val durationMillis: Long,
    val startedAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
    val state: TimerState,
    val remainingMillisWhenPaused: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
