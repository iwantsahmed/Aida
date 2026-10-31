package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.domain.model.Subtask
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.task.RecurrenceCodec
import java.time.Instant

/**
 * Tasks, between storage and the domain.
 *
 * Epoch millis are a storage detail and [Instant] is the domain currency; the conversion lives
 * here rather than in a Room TypeConverter so the indexed time columns stay primitive and SQLite
 * can still use their indexes.
 *
 * The repeat rule is text on the row and a [com.rayluxeo.aida.domain.task.RecurrenceRule] in the
 * domain, so a [RecurrenceCodec] is passed in rather than a second serialisation being invented
 * here. A rule the codec cannot read comes back as no rule at all: one unreadable row must not
 * take a list down with it.
 *
 * Tags, steps and attachments arrive as separate reads because they live in separate tables. They
 * default to empty so a caller that has not hydrated them says so explicitly.
 */
internal fun TaskEntity.toDomain(
    codec: RecurrenceCodec,
    tags: List<Tag> = emptyList(),
    subtasks: List<Subtask> = emptyList(),
    attachmentIds: List<Long> = emptyList(),
): Task = Task(
    id = id,
    title = title,
    description = description,
    notes = notes,
    dueAt = dueAtEpochMillis?.let(Instant::ofEpochMilli),
    dueAllDay = dueAllDay,
    estimatedMinutes = estimatedMinutes,
    priority = priority,
    isDone = isDone,
    completedAt = completedAtEpochMillis?.let(Instant::ofEpochMilli),
    origin = origin,
    projectId = projectId,
    clientId = clientId,
    tags = tags,
    subtasks = subtasks,
    attachmentIds = attachmentIds,
    recurrence = codec.decode(recurrenceRule),
    seriesUuid = seriesUuid,
    archivedAt = archivedAtEpochMillis?.let(Instant::ofEpochMilli),
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

/**
 * The task's own columns only.
 *
 * `Task.tags`, `Task.subtasks` and `Task.attachmentIds` are rows in other tables and are written
 * through their own repository calls. Folding them in here would mean any caller holding a task
 * loaded a moment ago could silently strip a tag added since.
 */
internal fun Task.toEntity(codec: RecurrenceCodec): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    notes = notes,
    dueAtEpochMillis = dueAt?.toEpochMilli(),
    dueAllDay = dueAllDay,
    estimatedMinutes = estimatedMinutes,
    priority = priority,
    isDone = isDone,
    completedAtEpochMillis = completedAt?.toEpochMilli(),
    origin = origin,
    projectId = projectId,
    clientId = clientId,
    recurrenceRule = recurrence?.let(codec::encode),
    seriesUuid = seriesUuid,
    archivedAtEpochMillis = archivedAt?.toEpochMilli(),
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)
