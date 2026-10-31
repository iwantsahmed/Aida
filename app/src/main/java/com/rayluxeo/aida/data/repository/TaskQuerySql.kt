package com.rayluxeo.aida.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.model.TaskSort

/**
 * A statement and the values to bind to it, kept apart so the shaping can be tested without a
 * database. [SimpleSQLiteQuery] hides its arguments once built, and an argument list that has
 * silently slipped out of step with its placeholders is exactly the bug worth a test.
 */
internal data class TaskSql(val sql: String, val args: List<Any>) {
    fun asRoomQuery(): SupportSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
}

/**
 * Turns a [TaskQuery] into SQL.
 *
 * Today, Upcoming, Overdue, All, Completed, the search field and every filter chip are one query
 * object with different values, and they are assembled here in one place. [rows] and [count] share
 * their FROM and WHERE exactly, which is what makes a list and the number beside it incapable of
 * disagreeing.
 *
 * Day boundaries arrive as a resolved [DayWindow] rather than being worked out in SQL: SQLite's
 * date functions answer in UTC, which would file Abu's 1 a.m. tasks under the wrong day.
 */
internal object TaskQuerySql {

    fun rows(query: TaskQuery, window: DayWindow): TaskSql {
        val where = whereClause(query, window)
        return TaskSql(
            sql = "SELECT tasks.* FROM tasks${fromJoins(query)} WHERE ${where.sql} " +
                "ORDER BY ${orderClause(query)}",
            args = where.args,
        )
    }

    fun count(query: TaskQuery, window: DayWindow): TaskSql {
        val where = whereClause(query, window)
        return TaskSql(
            sql = "SELECT COUNT(*) FROM tasks${fromJoins(query)} WHERE ${where.sql}",
            args = where.args,
        )
    }

    /**
     * The free-text part of [query] as FTS4 MATCH syntax, or null when nothing searchable remains.
     *
     * Whether to join the index and whether to bind a match are the same decision, and they are
     * made here once so the two halves of the statement cannot disagree about it.
     */
    private fun matchOrNull(query: TaskQuery): String? = query.text?.let(::ftsMatchOrNull)

    private fun fromJoins(query: TaskQuery): String =
        if (matchOrNull(query) == null) "" else " JOIN task_fts ON task_fts.rowid = tasks.id"

    private fun whereClause(query: TaskQuery, window: DayWindow): TaskSql {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // The FTS predicate comes first because its placeholder is the first one in the statement.
        // The index is external-content and shadows deleted rows too, so the soft-delete filter
        // below sits on the content table, never on task_fts.
        matchOrNull(query)?.let {
            clauses += "task_fts MATCH ?"
            args.add(it)
        }

        clauses += "tasks.deletedAtEpochMillis IS NULL"
        when (query.filter) {
            TaskFilter.ALL -> clauses += NOT_ARCHIVED
            TaskFilter.OPEN -> {
                clauses += NOT_ARCHIVED
                clauses += "tasks.isDone = 0"
            }
            TaskFilter.DONE -> {
                clauses += NOT_ARCHIVED
                clauses += "tasks.isDone = 1"
            }
            TaskFilter.DUE_TODAY -> {
                clauses += NOT_ARCHIVED
                clauses += "tasks.isDone = 0"
                clauses += "tasks.dueAtEpochMillis >= ? AND tasks.dueAtEpochMillis < ?"
                args.add(window.startMillis)
                args.add(window.endMillis)
            }
            TaskFilter.OVERDUE -> {
                clauses += NOT_ARCHIVED
                clauses += "tasks.isDone = 0"
                clauses += "tasks.dueAtEpochMillis IS NOT NULL AND tasks.dueAtEpochMillis < ?"
                args.add(window.startMillis)
            }
            TaskFilter.UPCOMING -> {
                clauses += NOT_ARCHIVED
                clauses += "tasks.isDone = 0"
                clauses += "tasks.dueAtEpochMillis IS NOT NULL AND tasks.dueAtEpochMillis >= ?"
                args.add(window.endMillis)
            }
            TaskFilter.ARCHIVED -> clauses += "tasks.archivedAtEpochMillis IS NOT NULL"
            TaskFilter.NO_DATE -> {
                clauses += NOT_ARCHIVED
                clauses += "tasks.isDone = 0"
                clauses += "tasks.dueAtEpochMillis IS NULL"
            }
        }

        query.projectId?.let {
            clauses += "tasks.projectId = ?"
            args.add(it)
        }
        query.clientId?.let {
            clauses += "tasks.clientId = ?"
            args.add(it)
        }
        if (query.priorities.isNotEmpty()) {
            val ordered = TaskPriority.entries.filter { it in query.priorities }
            clauses += "tasks.priority IN (${placeholders(ordered.size)})"
            args.addAll(ordered.map { it.name })
        }
        if (query.tagIds.isNotEmpty()) {
            // Every named tag, not any of them: a second chip narrows the list, it does not widen it.
            val ordered = query.tagIds.sorted()
            clauses += "tasks.id IN (SELECT taskId FROM task_tags WHERE tagId IN " +
                "(${placeholders(ordered.size)}) GROUP BY taskId HAVING COUNT(DISTINCT tagId) = ?)"
            args.addAll(ordered)
            args.add(ordered.size)
        }

        return TaskSql(sql = clauses.joinToString(separator = " AND "), args = args)
    }

    /**
     * Finished work always sinks. On a list that can hold both — All, Archived — outstanding tasks
     * come first whatever Abu sorted by, and on the lists where every row has the same state the
     * clause costs nothing. Every order ends on `tasks.id` so two rows that tie keep a stable
     * place instead of swapping between reads.
     *
     * Undated tasks sort last whichever way the dates run — reversing a list should not drag the
     * tasks with no date to the top of it.
     *
     * Ascending on [TaskSort.PRIORITY] means most important first. A priority sort exists to put
     * the urgent work at the top, so that is what its plain direction does.
     */
    private fun orderClause(query: TaskQuery): String {
        val direction = if (query.ascending) "ASC" else "DESC"
        val body = when (query.sort) {
            TaskSort.DUE_DATE ->
                "tasks.dueAtEpochMillis IS NULL ASC, tasks.dueAtEpochMillis $direction"
            TaskSort.PRIORITY ->
                "$PRIORITY_RANK $direction, tasks.dueAtEpochMillis IS NULL ASC, " +
                    "tasks.dueAtEpochMillis ASC"
            TaskSort.CREATED -> "tasks.createdAtEpochMillis $direction"
            TaskSort.UPDATED -> "tasks.updatedAtEpochMillis $direction"
            TaskSort.TITLE -> "tasks.title COLLATE NOCASE $direction"
        }
        return "tasks.isDone ASC, $body, tasks.id ASC"
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(separator = ", ")

    /** The second half of "live": the first, not being in the bin, is added for every filter. */
    private const val NOT_ARCHIVED = "tasks.archivedAtEpochMillis IS NULL"

    /**
     * Priority is stored by name, so ordering the column itself would read HIGH, LOW, NORMAL.
     * The rank is built from the enum rather than typed out, so reordering [TaskPriority] cannot
     * leave a stale ladder behind here.
     */
    private val PRIORITY_RANK: String = buildString {
        val ranked = listOf(TaskPriority.HIGH, TaskPriority.NORMAL, TaskPriority.LOW)
        append("CASE tasks.priority")
        ranked.forEachIndexed { index, priority -> append(" WHEN '${priority.name}' THEN $index") }
        append(" ELSE ${ranked.size} END")
    }
}
