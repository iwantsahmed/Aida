package com.rayluxeo.aida.domain.model

import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.Instant

enum class TaskPriority { LOW, NORMAL, HIGH }

/** Where a record came from, so Mira can explain herself later. */
/**
 * Where a record came from, so Mira can explain herself later.
 *
 * [DERIVED] is a record Mira raised from Abu's own data rather than from anything he said — an
 * invoice coming due, a renewal. It is its own value because "why am I being told this?" has a
 * different answer for those, and because the daily sweep needs to recognise its own work.
 */
enum class RecordOrigin { MANUAL, VOICE, TEXT_COMMAND, DERIVED }

/**
 * Where a task stands, derived rather than stored.
 *
 * There is no `status` column: completion, archiving and deletion are three independent facts on
 * the row, and a fourth column repeating them would be a second source of truth that can disagree
 * with the first (`AGENTS.md` §7.12). Order matters here — a deleted task is in the bin whether or
 * not it was done.
 */
enum class TaskStatus { OPEN, DONE, ARCHIVED, DELETED }

data class Task(
    val id: Long = 0L,
    val title: String,
    /** What the task is. [notes] is what Abu added afterwards; they are different fields. */
    val description: String? = null,
    val notes: String? = null,
    val dueAt: Instant? = null,
    /** True when Abu gave a day and no time, so Mira reads it back as a day. */
    val dueAllDay: Boolean = false,
    val estimatedMinutes: Int? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val isDone: Boolean = false,
    val completedAt: Instant? = null,
    val origin: RecordOrigin = RecordOrigin.MANUAL,
    /** Set when the task belongs to a project. */
    val projectId: Long? = null,
    /** Set when the task is for a client, whether or not it also sits under one of their projects. */
    val clientId: Long? = null,
    val tags: List<Tag> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val attachmentIds: List<Long> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    /** Ties the occurrences of a repeating task together. Null for a task that happens once. */
    val seriesUuid: String? = null,
    val archivedAt: Instant? = null,
    /**
     * When the task was sent to the bin, or null while it is live.
     *
     * Deleting is soft so that undo restores the same row rather than inserting a copy. A copy
     * would take a new id, and any reminder pointing at the old one would quietly stop resolving.
     */
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val status: TaskStatus
        get() = when {
            deletedAt != null -> TaskStatus.DELETED
            archivedAt != null -> TaskStatus.ARCHIVED
            isDone -> TaskStatus.DONE
            else -> TaskStatus.OPEN
        }

    val isRecurring: Boolean get() = recurrence != null
}

/** A label Abu put on a task. Its own record, so renaming one renames it everywhere. */
data class Tag(val id: Long = 0L, val name: String)

/**
 * The lists Abu can ask for. [UPCOMING] is deliberately not "everything with a date" — it is what
 * is ahead of today, so it does not silently include the overdue pile that has its own view.
 */
enum class TaskFilter { ALL, OPEN, DONE, DUE_TODAY, UPCOMING, OVERDUE, ARCHIVED, NO_DATE }

/** How a list is ordered. The default is [DUE_DATE], because a task list is about what is next. */
enum class TaskSort { DUE_DATE, PRIORITY, CREATED, UPDATED, TITLE }

/**
 * Everything a list query can narrow on, as one value.
 *
 * One object rather than a long parameter list so that a view, a search and a bulk selection all
 * describe what they want the same way, and so adding a dimension does not change every call site.
 */
data class TaskQuery(
    val filter: TaskFilter = TaskFilter.OPEN,
    val sort: TaskSort = TaskSort.DUE_DATE,
    val ascending: Boolean = true,
    val text: String? = null,
    val projectId: Long? = null,
    val clientId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    val priorities: Set<TaskPriority> = emptySet(),
)
