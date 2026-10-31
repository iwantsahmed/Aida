package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.rayluxeo.aida.data.db.entity.SubtaskEntity
import com.rayluxeo.aida.data.db.entity.TagEntity
import com.rayluxeo.aida.data.db.entity.TaskAttachmentEntity
import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.data.db.entity.TaskTagEntity
import kotlinx.coroutines.flow.Flow

/** One task's tag, flattened, so a whole list's tags arrive in a single read. */
data class TaskTagRow(
    val taskId: Long,
    val tagId: Long,
    val name: String,
)

/** One task's attachment link, in the order Abu put them. The file record itself lives in `files`. */
data class TaskAttachmentRow(
    val taskId: Long,
    val fileId: Long,
)

/** A project's or a client's name, for the headings of the grouped views. */
data class TaskGroupRow(
    val id: Long,
    val name: String,
)

/** A tag and how many live tasks wear it, for a filter list that can show its weight. */
data class TagUsageRow(
    val tagId: Long,
    val name: String,
    val taskCount: Int,
)

/**
 * Every "today" boundary arrives as an explicit epoch-millis window. SQLite's own date functions
 * resolve "now" in UTC on whichever thread runs the query, which would file Abu's 1 a.m. tasks
 * under the wrong day; the repository owns the zone and passes the window in.
 *
 * **Every read here filters `deletedAtEpochMillis IS NULL`**, including the counts and the
 * full-text search, and every live list filters `archivedAtEpochMillis IS NULL` beside it. The one
 * deliberate exception is [findById], which reads a row in whatever state it is in so that undo
 * and restore can find a task no list will show.
 *
 * `tags`, `task_tags` and `task_attachments` are read and written from here rather than from DAOs
 * of their own for a mechanical reason: a `@Transaction` method can only call other methods of its
 * own DAO, and attaching a tag writes the link *and* touches the task's `updatedAtEpochMillis`.
 * Split across two DAOs those two writes could not be made to land together. A tag in Mira exists
 * only to be put on a task, so one DAO is also the honest shape of it.
 *
 * An abstract class rather than an interface for the same reason — `@Transaction` needs a body.
 */
@Dao
abstract class TaskDao {

    // ---- Query-driven reads -------------------------------------------------------------------

    /**
     * The one entry point every view, filter and search goes through. The SQL is assembled by
     * `TaskQuerySql` from a `TaskQuery`, so a list and the count beside it are built from the same
     * predicates and cannot disagree.
     *
     * The observed entities are what a task's appearance actually depends on: its own row, the tags
     * on it, its steps and its attachments. Without them, ticking a step would leave a stale
     * checklist on screen until something else happened to invalidate the query.
     */
    @RawQuery(
        observedEntities = [
            TaskEntity::class,
            TaskTagEntity::class,
            SubtaskEntity::class,
            TaskAttachmentEntity::class,
        ],
    )
    abstract fun observeByQuery(query: SupportSQLiteQuery): Flow<List<TaskEntity>>

    @RawQuery
    abstract suspend fun listByQuery(query: SupportSQLiteQuery): List<TaskEntity>

    @RawQuery
    abstract suspend fun countByQuery(query: SupportSQLiteQuery): Int

    // ---- Counts -------------------------------------------------------------------------------

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE isDone = 0 AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
        """
    )
    abstract fun observeOpenCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE isDone = 0 AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun countOpen(): Int

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE isDone = 0 AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
          AND dueAtEpochMillis >= :startOfDay AND dueAtEpochMillis < :endOfDay
        """
    )
    abstract fun observeDueBetweenCount(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE isDone = 0 AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
          AND dueAtEpochMillis >= :startOfDay AND dueAtEpochMillis < :endOfDay
        """
    )
    abstract suspend fun countDueBetween(startOfDay: Long, endOfDay: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE isDone = 0 AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
          AND dueAtEpochMillis IS NOT NULL AND dueAtEpochMillis < :startOfDay
        """
    )
    abstract fun observeOverdueCount(startOfDay: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE isDone = 0 AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
          AND dueAtEpochMillis IS NOT NULL AND dueAtEpochMillis < :startOfDay
        """
    )
    abstract suspend fun countOverdue(startOfDay: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE isDone = 1 AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
          AND completedAtEpochMillis >= :startOfDay AND completedAtEpochMillis < :endOfDay
        """
    )
    abstract fun observeCompletedBetweenCount(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE isDone = 1 AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
          AND completedAtEpochMillis >= :startOfDay AND completedAtEpochMillis < :endOfDay
        """
    )
    abstract suspend fun countCompletedBetween(startOfDay: Long, endOfDay: Long): Int

    /**
     * Reads the row whatever state it is in — done, archived or in the bin.
     *
     * This is the one read here that does not filter `deletedAtEpochMillis`, because undo has to
     * be able to load the task it is about to put back, and a reminder pointing at a binned task
     * still has to resolve to something (`AGENTS.md` §8).
     */
    @Query("SELECT * FROM tasks WHERE deletedAtEpochMillis IS NULL")
    abstract suspend fun listAllForBackup(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun findById(id: Long): TaskEntity?

    // ---- Hydration ----------------------------------------------------------------------------

    /**
     * Every tag on a whole page of tasks in one read. Loading them per row would be a query per
     * task, which on a long list is the difference between a list that opens and one that stutters.
     */
    @Query(
        """
        SELECT task_tags.taskId AS taskId, tags.id AS tagId, tags.name AS name
        FROM task_tags JOIN tags ON tags.id = task_tags.tagId
        WHERE task_tags.taskId IN (:taskIds)
        ORDER BY tags.name ASC, tags.id ASC
        """
    )
    abstract suspend fun tagsForTasks(taskIds: List<Long>): List<TaskTagRow>

    @Query(
        """
        SELECT * FROM subtasks WHERE taskId IN (:taskIds)
        ORDER BY taskId ASC, position ASC, id ASC
        """
    )
    abstract suspend fun subtasksForTasks(taskIds: List<Long>): List<SubtaskEntity>

    @Query(
        """
        SELECT taskId AS taskId, fileId AS fileId FROM task_attachments
        WHERE taskId IN (:taskIds)
        ORDER BY taskId ASC, position ASC, id ASC
        """
    )
    abstract suspend fun attachmentsForTasks(taskIds: List<Long>): List<TaskAttachmentRow>

    /**
     * Headings for the by-project view.
     *
     * Deliberately unfiltered by `deletedAtEpochMillis`: a project is only unlinked from its tasks
     * when its row is removed outright, so a soft-deleted project still owns tasks and its group
     * must keep its name rather than collapsing into the unassigned pile.
     */
    @Query("SELECT id AS id, name AS name FROM projects WHERE id IN (:ids)")
    abstract suspend fun projectNames(ids: List<Long>): List<TaskGroupRow>

    @Query("SELECT id AS id, name AS name FROM clients WHERE id IN (:ids)")
    abstract suspend fun clientNames(ids: List<Long>): List<TaskGroupRow>

    // ---- Writes -------------------------------------------------------------------------------

    @Insert
    abstract suspend fun insert(task: TaskEntity): Long

    /**
     * Writes a new task together with the tags, steps and attachments it arrived with.
     *
     * The children are only ever written here on creation, never on update: nothing can be stale
     * about a row that did not exist a moment ago, so a task Abu dictated with three steps must
     * not be reachable in a state where it has none.
     *
     * Steps carry only their titles because a brand-new task's steps are undone by definition.
     */
    @Transaction
    open suspend fun insertWithChildren(
        task: TaskEntity,
        tagIds: List<Long>,
        subtaskTitles: List<String>,
        attachmentFileIds: List<Long>,
    ): Long {
        val id = insert(task)
        if (tagIds.isNotEmpty()) {
            insertTaskTags(tagIds.distinct().map { TaskTagEntity(taskId = id, tagId = it) })
        }
        if (subtaskTitles.isNotEmpty()) {
            insertSubtasks(
                subtaskTitles.mapIndexed { index, title ->
                    SubtaskEntity(
                        taskId = id,
                        title = title,
                        isDone = false,
                        completedAtEpochMillis = null,
                        position = index,
                        createdAtEpochMillis = task.createdAtEpochMillis,
                        updatedAtEpochMillis = task.updatedAtEpochMillis,
                    )
                }
            )
        }
        if (attachmentFileIds.isNotEmpty()) {
            insertAttachments(
                attachmentFileIds.distinct().mapIndexed { index, fileId ->
                    TaskAttachmentEntity(
                        taskId = id,
                        fileId = fileId,
                        position = index,
                        createdAtEpochMillis = task.createdAtEpochMillis,
                    )
                }
            )
        }
        return id
    }

    @Update
    abstract suspend fun update(task: TaskEntity): Int

    @Query("UPDATE tasks SET updatedAtEpochMillis = :at WHERE id = :id AND deletedAtEpochMillis IS NULL")
    abstract suspend fun touch(id: Long, at: Long): Int

    @Query(
        """
        UPDATE tasks
        SET isDone = :done, completedAtEpochMillis = :completedAt, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setDone(id: Long, done: Boolean, completedAt: Long?, updatedAt: Long): Int

    /**
     * Completes a task only if it is not already complete, and reports whether it did.
     *
     * [setDone] cannot be used as a guard: it matches on the id alone, so it reports one row
     * changed even when the task was already done. The recurrence rollover relies on that count to
     * decide whether to create the next occurrence, and completing the same Monday twice would
     * otherwise put two of next Monday on Abu's list.
     *
     * The condition is in the statement rather than in a read beforehand so that the count is
     * atomic, whatever else is touching the row.
     */
    @Query(
        """
        UPDATE tasks
        SET isDone = 1, completedAtEpochMillis = :completedAt, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND isDone = 0 AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun completeIfOpen(id: Long, completedAt: Long, updatedAt: Long): Int

    /**
     * The bulk form of [setDone]. One statement rather than a loop, so a bulk complete either
     * lands whole or not at all without needing a transaction around it.
     */
    @Query(
        """
        UPDATE tasks
        SET isDone = :done, completedAtEpochMillis = :completedAt, updatedAtEpochMillis = :updatedAt
        WHERE id IN (:ids) AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setDoneIn(
        ids: List<Long>,
        done: Boolean,
        completedAt: Long?,
        updatedAt: Long,
    ): Int

    /**
     * Moves the due moment and drops the all-day flag: a snooze names a time of day, and leaving
     * the flag set would have Mira read "tomorrow at 9" back to Abu as plain "tomorrow".
     */
    @Query(
        """
        UPDATE tasks
        SET dueAtEpochMillis = :dueAt, dueAllDay = 0, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setDueAt(id: Long, dueAt: Long, updatedAt: Long): Int

    /** Archiving is its own axis: a task can be archived whether or not it was ever done. */
    @Query(
        """
        UPDATE tasks
        SET archivedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun archive(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE tasks
        SET archivedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id IN (:ids) AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun archiveIn(ids: List<Long>, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE tasks
        SET archivedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL AND archivedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun unarchive(id: Long, atEpochMillis: Long): Int

    @Query("UPDATE tasks SET seriesUuid = :seriesUuid, updatedAtEpochMillis = :at WHERE id = :id")
    abstract suspend fun setSeriesUuid(id: Long, seriesUuid: String, at: Long): Int

    /**
     * Sends a task to the bin without removing the row, so undo puts back the same id and the
     * reminders that point at it keep resolving. A task already in the bin matches nothing, which
     * is what makes a repeated delete report "no such task" instead of re-stamping the time.
     */
    @Query(
        """
        UPDATE tasks
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE tasks
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id IN (:ids) AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDeleteIn(ids: List<Long>, atEpochMillis: Long): Int

    /** The other half of [softDelete]. Only a task that is actually in the bin can come back. */
    @Query(
        """
        UPDATE tasks
        SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, atEpochMillis: Long): Int

    /**
     * Removes the row for good, with no undo behind it. This is the purge that empties the bin,
     * not the delete a screen calls — [softDelete] is that one. It is also the only way to make
     * the `SET NULL` rules that point at `tasks` actually fire, so the calendar-link and
     * meeting-note tests reach for it.
     */
    @Query("DELETE FROM tasks WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    // ---- Tags ---------------------------------------------------------------------------------

    @Query("SELECT * FROM tags ORDER BY name ASC, id ASC")
    abstract fun observeTags(): Flow<List<TagEntity>>

    /**
     * Tags with the number of live tasks on each — outstanding only, so the weight beside a filter
     * is the work it would show rather than everything ever labelled with it.
     *
     * A LEFT JOIN, so a tag Abu made and has not used yet still appears with a count of zero
     * rather than vanishing from his own filter list.
     */
    @Query(
        """
        SELECT tags.id AS tagId, tags.name AS name, COUNT(tasks.id) AS taskCount
        FROM tags
        LEFT JOIN task_tags ON task_tags.tagId = tags.id
        LEFT JOIN tasks ON tasks.id = task_tags.taskId
          AND tasks.isDone = 0
          AND tasks.deletedAtEpochMillis IS NULL
          AND tasks.archivedAtEpochMillis IS NULL
        GROUP BY tags.id, tags.name
        ORDER BY tags.name ASC, tags.id ASC
        """
    )
    abstract fun observeTagUsage(): Flow<List<TagUsageRow>>

    @Query("SELECT * FROM tags WHERE id = :id")
    abstract suspend fun findTagById(id: Long): TagEntity?

    /**
     * NOCASE folds ASCII only, which is exactly right here: "Urgent" and "urgent" are one label to
     * Abu, and Bengali has no case to fold, so a Bengali tag matches on its letters alone.
     */
    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findTagByName(name: String): TagEntity?

    @Insert
    abstract suspend fun insertTag(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name, updatedAtEpochMillis = :at WHERE id = :id")
    abstract suspend fun renameTag(id: Long, name: String, at: Long): Int

    /** CASCADE takes the links with it, so the tag comes off every task and the tasks stay. */
    @Query("DELETE FROM tags WHERE id = :id")
    abstract suspend fun deleteTag(id: Long): Int

    /**
     * Returns the tag called [name], creating it only if it is not there.
     *
     * Looking and inserting are one transaction because `tags.name` is unique: a plain insert
     * would throw the second time Abu says "tag it urgent", and a look followed by an insert in
     * two transactions could still lose the race with itself.
     */
    @Transaction
    open suspend fun ensureTag(name: String, at: Long): Long {
        val existing = findTagByName(name)
        if (existing != null) return existing.id
        return insertTag(
            TagEntity(name = name, createdAtEpochMillis = at, updatedAtEpochMillis = at),
        )
    }

    // ---- Tag links ----------------------------------------------------------------------------

    @Query("SELECT tagId FROM task_tags WHERE taskId = :taskId")
    abstract suspend fun tagIdsFor(taskId: Long): List<Long>

    /**
     * IGNORE rather than REPLACE: putting a tag on a task that already carries it is something
     * Abu does by accident, and it should change nothing rather than delete and reinsert the link.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTaskTags(links: List<TaskTagEntity>)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    abstract suspend fun deleteTaskTag(taskId: Long, tagId: Long): Int

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    abstract suspend fun clearTaskTags(taskId: Long)

    /** The link row and the task's `updatedAtEpochMillis` are two tables, so they go together. */
    @Transaction
    open suspend fun addTag(taskId: Long, tagId: Long, at: Long) {
        insertTaskTags(listOf(TaskTagEntity(taskId = taskId, tagId = tagId)))
        touch(taskId, at)
    }

    @Transaction
    open suspend fun removeTag(taskId: Long, tagId: Long, at: Long) {
        deleteTaskTag(taskId, tagId)
        touch(taskId, at)
    }

    /**
     * Replaces the whole set. Clearing and reinserting inside one transaction is what stops a
     * reader seeing the moment in between, when the task has no tags at all.
     */
    @Transaction
    open suspend fun replaceTags(taskId: Long, tagIds: List<Long>, at: Long) {
        clearTaskTags(taskId)
        if (tagIds.isNotEmpty()) {
            insertTaskTags(tagIds.distinct().map { TaskTagEntity(taskId = taskId, tagId = it) })
        }
        touch(taskId, at)
    }

    // ---- Attachments --------------------------------------------------------------------------

    @Query("SELECT MAX(position) FROM task_attachments WHERE taskId = :taskId")
    abstract suspend fun lastAttachmentPosition(taskId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAttachment(attachment: TaskAttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAttachments(attachments: List<TaskAttachmentEntity>)

    @Query("DELETE FROM task_attachments WHERE taskId = :taskId AND fileId = :fileId")
    abstract suspend fun deleteAttachment(taskId: Long, fileId: Long): Int

    @Query("SELECT id FROM task_attachments WHERE taskId = :taskId ORDER BY position ASC, id ASC")
    abstract suspend fun attachmentIdsInOrder(taskId: Long): List<Long>

    @Query("UPDATE task_attachments SET position = :position WHERE id = :id")
    abstract suspend fun setAttachmentPosition(id: Long, position: Int)

    /**
     * Appends the file at the end of the task's list. Reading the last position, inserting and
     * touching the task are one transaction: two files linked together must not land on the same
     * position and then trade places on the next read.
     *
     * Linking a file the task already carries is ignored rather than refused — the link is already
     * exactly what the caller asked for.
     */
    @Transaction
    open suspend fun appendAttachment(taskId: Long, fileId: Long, at: Long): Long {
        val position = (lastAttachmentPosition(taskId) ?: -1) + 1
        val id = insertAttachment(
            TaskAttachmentEntity(
                taskId = taskId,
                fileId = fileId,
                position = position,
                createdAtEpochMillis = at,
            )
        )
        touch(taskId, at)
        return id
    }

    /** Unlinks the file and closes the gap, so positions stay dense and an insert cannot collide. */
    @Transaction
    open suspend fun removeAttachment(taskId: Long, fileId: Long, at: Long): Int {
        val removed = deleteAttachment(taskId, fileId)
        if (removed > 0) {
            attachmentIdsInOrder(taskId).forEachIndexed { index, id ->
                setAttachmentPosition(id = id, position = index)
            }
            touch(taskId, at)
        }
        return removed
    }

    // ---- Recurrence ---------------------------------------------------------------------------

    @Insert
    abstract suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    /**
     * Completes a repeating task and opens its next occurrence, together.
     *
     * Each occurrence is its own row — a finished Monday is a different fact from next Monday —
     * and [seriesUuid] is what ties them together. The completed row is stamped with the uuid too
     * when it did not have one, which is what makes the first roll-over start a series rather than
     * leave an orphan behind.
     *
     * Tags and steps are carried onto the new occurrence, with the steps reset to undone; the
     * steps of a repeating task are how it gets done each time, not a record of one Monday.
     *
     * Notes and attachments are not carried. `description` says what the task is and belongs to
     * every occurrence; `notes` and a linked file are what Abu jotted and attached about *that*
     * Monday, and dragging them forward would have next week's task open already annotated.
     *
     * @return the id of the new occurrence, or null when [id] names no task that could be completed.
     */
    @Transaction
    open suspend fun completeWithNextOccurrence(
        id: Long,
        nextDueAt: Long,
        seriesUuid: String,
        at: Long,
    ): Long? {
        val current = findById(id) ?: return null
        if (current.deletedAtEpochMillis != null) return null
        if (completeIfOpen(id = id, completedAt = at, updatedAt = at) == 0) return null
        if (current.seriesUuid == null) setSeriesUuid(id = id, seriesUuid = seriesUuid, at = at)

        val nextId = insert(
            current.copy(
                id = 0L,
                notes = null,
                dueAtEpochMillis = nextDueAt,
                isDone = false,
                completedAtEpochMillis = null,
                archivedAtEpochMillis = null,
                deletedAtEpochMillis = null,
                seriesUuid = current.seriesUuid ?: seriesUuid,
                createdAtEpochMillis = at,
                updatedAtEpochMillis = at,
            )
        )

        val tagIds = tagIdsFor(id)
        if (tagIds.isNotEmpty()) {
            insertTaskTags(tagIds.map { TaskTagEntity(taskId = nextId, tagId = it) })
        }

        val steps = subtasksForTasks(listOf(id))
        if (steps.isNotEmpty()) {
            insertSubtasks(
                steps.map { step ->
                    step.copy(
                        id = 0L,
                        taskId = nextId,
                        isDone = false,
                        completedAtEpochMillis = null,
                        createdAtEpochMillis = at,
                        updatedAtEpochMillis = at,
                    )
                }
            )
        }
        return nextId
    }
}
