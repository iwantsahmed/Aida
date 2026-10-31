package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.TaskPriority

/**
 * Instants live here as epoch millis rather than as a converted type: the due and completion
 * columns are indexed and compared against day windows, and an index over a converted column
 * would be unusable to SQLite.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        // SET NULL, never CASCADE: a task Abu did is a fact that outlives the client it was for.
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["dueAtEpochMillis"]),
        Index(value = ["isDone"]),
        Index(value = ["projectId"]),
        Index(value = ["clientId"]),
        Index(value = ["deletedAtEpochMillis"]),
        Index(value = ["archivedAtEpochMillis"]),
        Index(value = ["seriesUuid"]),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    /** What the task is. [notes] is for what Abu jotted afterwards; the two are not the same field. */
    val description: String? = null,
    val notes: String?,
    val dueAtEpochMillis: Long?,
    /**
     * True when Abu gave a day and no time.
     *
     * The instant is still stored, at the start of that day in his zone, because every query and
     * index here works on millis. Without the flag Mira cannot tell "Friday" from "Friday at
     * midnight", and would read the first back to him as the second.
     */
    val dueAllDay: Boolean = false,
    val estimatedMinutes: Int? = null,
    val priority: TaskPriority,
    val isDone: Boolean,
    val completedAtEpochMillis: Long?,
    val origin: RecordOrigin,
    /** Set when the task belongs to a project. `SET NULL`, so deleting a project keeps its tasks. */
    val projectId: Long? = null,
    /** Set when the task is for a client, whether or not it also belongs to one of their projects. */
    val clientId: Long? = null,
    /**
     * The repeat rule, serialised by `domain/task/RecurrenceRule`. Null for a task that happens once.
     */
    val recurrenceRule: String? = null,
    /**
     * Ties every occurrence of a repeating task together.
     *
     * Each occurrence is its own row, because a completed Monday is a different fact from next
     * Monday and squashing them into one row would lose the history. The uuid is what lets Mira
     * find the series when Abu edits or ends it.
     */
    val seriesUuid: String? = null,
    /**
     * Out of the way but not gone. Archiving is a separate axis from completion — a task can be
     * archived whether or not it was ever done — which is why this is its own column rather than
     * another value in a status enum beside [isDone].
     */
    val archivedAtEpochMillis: Long? = null,
    val deletedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/**
 * External-content FTS index over [TaskEntity]. Room generates the triggers that keep it in step
 * with the tasks table, so searching "ahmed" finds "Call Ahmed" without a LIKE scan and without
 * a second copy of Abu's data to keep honest.
 */
@Entity(tableName = "task_fts")
@Fts4(contentEntity = TaskEntity::class)
data class TaskFtsEntity(
    val title: String,
    val description: String?,
    val notes: String?,
)

/**
 * A label Abu can put on a task, kept in its own table rather than as free text on the task.
 *
 * One table means renaming a tag renames it everywhere and the filter list is the tags that exist,
 * rather than whatever spellings happen to be in use.
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/**
 * CASCADE on both sides, because a link row is meaningless without either end — unlike a payment,
 * which is a fact that must outlive the invoice it was against (`AGENTS.md` §8).
 */
@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class TaskTagEntity(
    val taskId: Long,
    val tagId: Long,
)

/** Mirrors `note_attachments`: the file stays the record, this is only the link to it. */
@Entity(
    tableName = "task_attachments",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FileRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["taskId", "fileId"], unique = true),
        Index(value = ["fileId"]),
    ],
)
data class TaskAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val fileId: Long,
    val position: Int,
    val createdAtEpochMillis: Long,
)
