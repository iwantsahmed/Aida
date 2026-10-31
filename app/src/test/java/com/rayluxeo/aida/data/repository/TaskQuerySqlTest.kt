package com.rayluxeo.aida.data.repository

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.model.TaskSort
import org.junit.Test

/**
 * The shaping of a [TaskQuery] into SQL, tested without a database.
 *
 * No Hilt and no Robolectric: this is a pure function over a query and a day window, and the
 * things worth pinning here — that every read filters the bin, that Upcoming and Overdue cannot
 * overlap, that the bound values stay in step with their placeholders — are all visible in the
 * text it produces.
 */
class TaskQuerySqlTest {

    private val window = DayWindow(startMillis = 1_000L, endMillis = 2_000L)

    @Test
    fun `every filter keeps binned tasks out`() {
        for (filter in TaskFilter.entries) {
            val sql = TaskQuerySql.rows(TaskQuery(filter = filter), window).sql

            assertThat(sql).contains("tasks.deletedAtEpochMillis IS NULL")
        }
    }

    @Test
    fun `every live filter keeps archived tasks out`() {
        val live = TaskFilter.entries - TaskFilter.ARCHIVED

        for (filter in live) {
            val sql = TaskQuerySql.rows(TaskQuery(filter = filter), window).sql

            assertThat(sql).contains("tasks.archivedAtEpochMillis IS NULL")
        }
    }

    @Test
    fun `the archived view is the one that asks for archived rows`() {
        val sql = TaskQuerySql.rows(TaskQuery(filter = TaskFilter.ARCHIVED), window).sql

        assertThat(sql).contains("tasks.archivedAtEpochMillis IS NOT NULL")
        assertThat(sql).doesNotContain("tasks.archivedAtEpochMillis IS NULL")
    }

    @Test
    fun `due today binds the window as a half open range`() {
        val built = TaskQuerySql.rows(TaskQuery(filter = TaskFilter.DUE_TODAY), window)

        assertThat(built.sql).contains("tasks.dueAtEpochMillis >= ? AND tasks.dueAtEpochMillis < ?")
        assertThat(built.args).containsExactly(1_000L, 2_000L).inOrder()
    }

    @Test
    fun `overdue and upcoming cannot hold the same task`() {
        val overdue = TaskQuerySql.rows(TaskQuery(filter = TaskFilter.OVERDUE), window)
        val upcoming = TaskQuerySql.rows(TaskQuery(filter = TaskFilter.UPCOMING), window)

        assertThat(overdue.sql).contains("tasks.dueAtEpochMillis < ?")
        assertThat(overdue.args).containsExactly(1_000L)
        assertThat(upcoming.sql).contains("tasks.dueAtEpochMillis >= ?")
        assertThat(upcoming.args).containsExactly(2_000L)
    }

    @Test
    fun `text search joins the index and binds the match first`() {
        val built = TaskQuerySql.rows(
            TaskQuery(filter = TaskFilter.DUE_TODAY, text = "Ahmed"),
            window,
        )

        assertThat(built.sql).contains("JOIN task_fts ON task_fts.rowid = tasks.id")
        assertThat(built.sql).contains("task_fts MATCH ?")
        assertThat(built.args).containsExactly("Ahmed*", 1_000L, 2_000L).inOrder()
    }

    /** The operand of MATCH is a hidden column named after the table, so an alias breaks it. */
    @Test
    fun `the index is never aliased in a match`() {
        val sql = TaskQuerySql.rows(TaskQuery(text = "rent"), window).sql

        assertThat(sql).doesNotContain("task_fts AS")
        assertThat(sql).contains("task_fts MATCH ?")
    }

    /** The filter is what keeps deleted rows out, because the index shadows them too. */
    @Test
    fun `a search still filters the bin on the content table`() {
        val sql = TaskQuerySql.rows(TaskQuery(text = "rent"), window).sql

        assertThat(sql).contains("tasks.deletedAtEpochMillis IS NULL")
    }

    @Test
    fun `text with nothing searchable in it is no search at all`() {
        for (text in listOf("", "   ", "!!!", "-")) {
            val built = TaskQuerySql.rows(TaskQuery(text = text), window)

            assertThat(built.sql).doesNotContain("task_fts")
            assertThat(built.args).isEmpty()
        }
    }

    /**
     * Bengali text must at least reach the index. What it is broken into on the way is the
     * sanitiser's business, and `FtsQuery` currently splits a Bengali word at its vowel signs —
     * see the report that came with this change.
     */
    @Test
    fun `bengali text reaches the index`() {
        val built = TaskQuerySql.rows(TaskQuery(text = "ওয়েবসাইট"), window)

        assertThat(built.sql).contains("task_fts MATCH ?")
        assertThat(built.args).isNotEmpty()
    }

    @Test
    fun `project and client narrow the same query`() {
        val built = TaskQuerySql.rows(
            TaskQuery(filter = TaskFilter.ALL, projectId = 7L, clientId = 9L),
            window,
        )

        assertThat(built.sql).contains("tasks.projectId = ?")
        assertThat(built.sql).contains("tasks.clientId = ?")
        assertThat(built.args).containsExactly(7L, 9L).inOrder()
    }

    @Test
    fun `priorities bind by name in the order the enum declares them`() {
        val built = TaskQuerySql.rows(
            TaskQuery(filter = TaskFilter.ALL, priorities = setOf(TaskPriority.HIGH, TaskPriority.LOW)),
            window,
        )

        assertThat(built.sql).contains("tasks.priority IN (?, ?)")
        assertThat(built.args).containsExactly("LOW", "HIGH").inOrder()
    }

    /** Two chips narrow the list. A task must carry both tags, not either of them. */
    @Test
    fun `several tags are required together`() {
        val built = TaskQuerySql.rows(
            TaskQuery(filter = TaskFilter.ALL, tagIds = setOf(4L, 2L)),
            window,
        )

        assertThat(built.sql).contains("COUNT(DISTINCT tagId) = ?")
        assertThat(built.args).containsExactly(2L, 4L, 2).inOrder()
    }

    @Test
    fun `the count asks the same question as the list`() {
        val query = TaskQuery(
            filter = TaskFilter.OVERDUE,
            text = "invoice",
            projectId = 3L,
            tagIds = setOf(8L),
            priorities = setOf(TaskPriority.HIGH),
        )

        val rows = TaskQuerySql.rows(query, window)
        val count = TaskQuerySql.count(query, window)

        assertThat(count.sql).startsWith("SELECT COUNT(*) FROM tasks")
        assertThat(count.args).isEqualTo(rows.args)
        assertThat(count.sql.substringAfter(" WHERE "))
            .isEqualTo(rows.sql.substringAfter(" WHERE ").substringBefore(" ORDER BY "))
    }

    @Test
    fun `finished work sinks on every sort`() {
        for (sort in TaskSort.entries) {
            val sql = TaskQuerySql.rows(TaskQuery(filter = TaskFilter.ALL, sort = sort), window).sql

            assertThat(sql).contains("ORDER BY tasks.isDone ASC,")
            assertThat(sql).endsWith("tasks.id ASC")
        }
    }

    @Test
    fun `undated tasks stay at the bottom when the dates are reversed`() {
        for (ascending in listOf(true, false)) {
            val sql = TaskQuerySql.rows(
                TaskQuery(sort = TaskSort.DUE_DATE, ascending = ascending),
                window,
            ).sql

            assertThat(sql).contains("tasks.dueAtEpochMillis IS NULL ASC")
        }
    }

    @Test
    fun `sorting by priority puts the urgent work first`() {
        val sql = TaskQuerySql.rows(TaskQuery(sort = TaskSort.PRIORITY), window).sql

        assertThat(sql).contains("WHEN 'HIGH' THEN 0")
        assertThat(sql).contains("WHEN 'NORMAL' THEN 1")
        assertThat(sql).contains("WHEN 'LOW' THEN 2")
        assertThat(sql).contains("END ASC")
    }

    @Test
    fun `sorting by title ignores case`() {
        val sql = TaskQuerySql.rows(TaskQuery(sort = TaskSort.TITLE), window).sql

        assertThat(sql).contains("tasks.title COLLATE NOCASE ASC")
    }

    @Test
    fun `reversing a sort reverses only the column it is about`() {
        val sql = TaskQuerySql.rows(
            TaskQuery(sort = TaskSort.CREATED, ascending = false),
            window,
        ).sql

        assertThat(sql).contains("tasks.createdAtEpochMillis DESC")
        assertThat(sql).contains("tasks.isDone ASC")
    }
}
