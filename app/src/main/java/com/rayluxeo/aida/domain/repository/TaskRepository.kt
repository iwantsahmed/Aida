package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskQuery
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Everything Mira can ask storage about Abu's tasks.
 *
 * [TaskQuery] is the single way a caller narrows a list. Today, Upcoming, Overdue, All, Completed,
 * the search field and every filter chip are the same query object with different values, so a
 * list and the count beside it are answered by one set of predicates and cannot drift apart.
 *
 * Every task handed back is whole: its tags, its steps and its attachments are already on it.
 * A screen that had to fetch those separately would render a task with no tags for one frame,
 * and a caller that forgot to would show Abu a task that looks emptier than it is.
 */
interface TaskRepository {

    /** Shorthand for `observe(TaskQuery(filter = filter))`, which is most of the call sites. */
    fun observe(filter: TaskFilter): Flow<List<Task>>

    fun observe(query: TaskQuery): Flow<List<Task>>

    fun observeCounts(): Flow<TaskCounts>

    /**
     * Reads the row whatever state it is in — done, archived or in the bin — because restore and
     * undo have to be able to find a task that no list will show.
     */
    suspend fun byId(id: Long): AppResult<Task>

    suspend fun list(query: TaskQuery): AppResult<List<Task>>

    /** The same predicates as [list], counted in SQLite rather than by loading the rows. */
    suspend fun count(query: TaskQuery): AppResult<Int>

    /** Full-text search over title, description and notes. A blank query returns [filter] whole. */
    suspend fun search(query: String?, filter: TaskFilter): AppResult<List<Task>>

    /**
     * The same tasks [list] would return, split by project with the project names resolved.
     * Tasks with no project come back in a group whose id and name are null; the words for that
     * heading belong to the presentation layer, not here.
     */
    suspend fun groupedByProject(query: TaskQuery): AppResult<List<TaskGroup>>

    suspend fun groupedByClient(query: TaskQuery): AppResult<List<TaskGroup>>

    suspend fun create(task: Task): AppResult<Task>

    /**
     * Writes the task's own columns. Tags and attachments are deliberately not read from [task]:
     * they are edited through [setTags] and [attachFile], so a caller holding a stale copy cannot
     * silently strip a tag Abu added on another screen.
     */
    suspend fun update(task: Task): AppResult<Task>

    /** Ticking a task never ticks its steps — a task can be finished with steps left undone. */
    suspend fun setDone(id: Long, done: Boolean): AppResult<Task>

    /** @return how many of [ids] changed state; ids that no longer exist are simply not counted. */
    suspend fun setDoneAll(ids: Collection<Long>, done: Boolean): AppResult<Int>

    /**
     * Moves the due moment to [until]. A snooze names a time, so an all-day task stops being
     * all-day — otherwise Mira would read "tomorrow 9am" back to Abu as "tomorrow".
     */
    suspend fun snooze(id: Long, until: Instant): AppResult<Task>

    suspend fun archive(id: Long): AppResult<Task>

    suspend fun unarchive(id: Long): AppResult<Task>

    suspend fun archiveAll(ids: Collection<Long>): AppResult<Int>

    /** Sends the task to the bin. The row survives, so [restore] brings back the same id. */
    suspend fun delete(id: Long): AppResult<Unit>

    suspend fun deleteAll(ids: Collection<Long>): AppResult<Int>

    /** Undoes a [delete]. Fails for a task that is not in the bin. */
    suspend fun restore(id: Long): AppResult<Task>

    /**
     * Completes a repeating task and opens its next occurrence at [nextDueAt], in one transaction.
     *
     * The caller works out [nextDueAt] from the rule — `domain/task/RecurrenceCalculator` owns that
     * arithmetic. Storage's job is that the two writes land together: a crash between them would
     * either lose the repeat or leave Abu with two open Mondays.
     *
     * The next occurrence inherits the title, description, tags and steps, with the steps
     * unchecked. It does not inherit the notes or the attachments: those are what Abu wrote and
     * attached about the occurrence he just finished, and next Monday should not open already
     * annotated.
     */
    suspend fun completeWithNextOccurrence(id: Long, nextDueAt: Instant): AppResult<TaskOccurrence>

    /** Replaces the task's tags outright. Tags not named here are detached, not deleted. */
    suspend fun setTags(taskId: Long, tagIds: Collection<Long>): AppResult<Task>

    /** Attaching a tag the task already carries succeeds and changes nothing. */
    suspend fun addTag(taskId: Long, tagId: Long): AppResult<Task>

    suspend fun removeTag(taskId: Long, tagId: Long): AppResult<Task>

    /**
     * Links an existing file record to the task, at the end of its list. The file itself is not
     * copied or moved — `files` stays the record and this is only the link to it.
     */
    suspend fun attachFile(taskId: Long, fileId: Long): AppResult<Task>

    suspend fun detachFile(taskId: Long, fileId: Long): AppResult<Task>
}

/**
 * The numbers the home screen and the daily brief read.
 *
 * [dueToday] and [overdue] are disjoint by construction: overdue means an earlier day, never
 * earlier today, so "2 overdue, 3 due today" never counts the same task twice.
 */
data class TaskCounts(
    val open: Int,
    val dueToday: Int,
    val overdue: Int,
    val completedToday: Int,
)

/**
 * One heading and the tasks under it, for the by-project and by-client views.
 *
 * A null [id] is the group of tasks that belong to nothing. It carries no name because naming it
 * is a wording decision, and wording is rendered per language in `ui/`.
 */
data class TaskGroup(
    val id: Long?,
    val name: String?,
    val tasks: List<Task>,
)

/** What a repeating task leaves behind: the occurrence just finished, and the one now open. */
data class TaskOccurrence(
    val completed: Task,
    val next: Task,
)
