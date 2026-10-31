package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.data.db.entity.AlarmRecordEntity
import com.rayluxeo.aida.data.db.entity.SubtaskEntity
import com.rayluxeo.aida.data.db.entity.TimerRecordEntity
import com.rayluxeo.aida.domain.model.AlarmRecord
import com.rayluxeo.aida.domain.model.Subtask
import com.rayluxeo.aida.domain.model.TimerRecord
import java.time.Duration
import java.time.Instant

/**
 * Subtasks, alarm records and timers, from storage into the domain.
 *
 * Epoch millis are a storage detail and [Instant] is the domain currency; the conversion lives
 * here rather than in a Room TypeConverter so the indexed time columns stay primitive and
 * SQLite can still use their indexes.
 *
 * There is no mapping in the other direction. Each of these rows is created by its repository
 * from the clock and from what the caller asked for, so a domain-to-entity mapper would only
 * ever be handed values it had to overwrite.
 */
internal fun SubtaskEntity.toDomain(): Subtask = Subtask(
    id = id,
    taskId = taskId,
    title = title,
    isDone = isDone,
    completedAt = completedAtEpochMillis?.let(Instant::ofEpochMilli),
    position = position,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun AlarmRecordEntity.toDomain(): AlarmRecord = AlarmRecord(
    id = id,
    reminderId = reminderId,
    label = label,
    triggerAt = Instant.ofEpochMilli(triggerAtEpochMillis),
    isExact = isExact,
    state = state,
    firedAt = firedAtEpochMillis?.let(Instant::ofEpochMilli),
    requestCode = requestCode,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun TimerRecordEntity.toDomain(): TimerRecord = TimerRecord(
    id = id,
    label = label,
    duration = Duration.ofMillis(durationMillis),
    startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
    endsAt = Instant.ofEpochMilli(endsAtEpochMillis),
    state = state,
    remainingWhenPaused = remainingMillisWhenPaused?.let(Duration::ofMillis),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)
