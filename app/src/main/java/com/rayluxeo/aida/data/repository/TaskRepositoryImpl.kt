package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.TaskDao
import com.rayluxeo.aida.data.db.dao.TaskGroupRow
import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.repository.TaskOccurrence
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.task.RecurrenceCodec
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Every list, count, search and filter is one [TaskQuery] shaped into SQL by [TaskQuerySql], so
 * the Today list and the "3 due today" beside it are answered by the same predicates.
 *
 * Tasks come back whole. A task's tags, steps and attachments live in three other tables, and
 * they are read for a whole page at once rather than per row: a query per task turns a long list
 * into a stutter, and a screen that fetched them itself would show Abu a task with no tags for a
 * frame.
 *
 * The day-dependent filters resolve their window once per collection. A screen that stays open
 * across midnight keeps yesterday's window until it is re-collected; that is a deliberate trade
 * against re-running every query on a timer for a phone that is usually asleep at midnight.
 */
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val recurrence: RecurrenceCodec,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : TaskRepository {

    override fun observe(filter: TaskFilter): Flow<List<Task>> = observe(TaskQuery(filter = filter))

    override fun observe(query: TaskQuery): Flow<List<Task>> = flow<List<Task>> {
        val sql = TaskQuerySql.rows(query, clock.dayWindow()).asRoomQuery()
        emitAll(dao.observeByQuery(sql).map { rows -> hydrate(rows) })
    }.flowOn(dispatchers.io).orOnFailure(emptyList())

    override fun observeCounts(): Flow<TaskCounts> = flow<TaskCounts> {
        val window = clock.dayWindow()
        emitAll(
            combine(
                dao.observeOpenCount(),
                dao.observeDueBetweenCount(window.startMillis, window.endMillis),
                dao.observeOverdueCount(window.startMillis),
                dao.observeCompletedBetweenCount(window.startMillis, window.endMillis),
            ) { open, dueToday, overdue, completedToday ->
                TaskCounts(
                    open = open,
                    dueToday = dueToday,
                    overdue = overdue,
                    completedToday = completedToday,
                )
            }
        )
    }.flowOn(dispatchers.io).orOnFailure(EMPTY_COUNTS)

    override suspend fun byId(id: Long): AppResult<Task> {
        val found = storageCall(dispatchers, "load task $id") {
            dao.findById(id)?.let { hydrate(listOf(it)).first() }
        }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        }
    }

    override suspend fun list(query: TaskQuery): AppResult<List<Task>> =
        storageCall(dispatchers, "list tasks") {
            hydrate(dao.listByQuery(TaskQuerySql.rows(query, clock.dayWindow()).asRoomQuery()))
        }

    override suspend fun count(query: TaskQuery): AppResult<Int> =
        storageCall(dispatchers, "count tasks") {
            dao.countByQuery(TaskQuerySql.count(query, clock.dayWindow()).asRoomQuery())
        }

    override suspend fun search(query: String?, filter: TaskFilter): AppResult<List<Task>> =
        list(TaskQuery(filter = filter, text = query))

    override suspend fun groupedByProject(query: TaskQuery): AppResult<List<TaskGroup>> =
        grouped(query, key = { it.projectId }) { dao.projectNames(it) }

    override suspend fun groupedByClient(query: TaskQuery): AppResult<List<TaskGroup>> =
        grouped(query, key = { it.clientId }) { dao.clientNames(it) }

    /**
     * Tags, steps and attachments given on a brand-new task are written with it, in one
     * transaction. That is the opposite of [update] on purpose: nothing can be stale about a row
     * that did not exist a moment ago, and a task that arrives with three steps should not be
     * reachable in a state where it has none.
     */
    override suspend fun create(task: Task): AppResult<Task> {
        val inserted = storageCall(dispatchers, "create task") {
            dao.insertWithChildren(
                task = task.copy(id = 0L).toEntity(recurrence),
                tagIds = task.tags.map { it.id },
                subtaskTitles = task.subtasks.map { it.title },
                attachmentFileIds = task.attachmentIds,
            )
        }
        return when (inserted) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> byId(inserted.value)
        }
    }

    override suspend fun update(task: Task): AppResult<Task> {
        val now = clock.now()
        val updated = storageCall(dispatchers, "update task ${task.id}") {
            dao.update(task.copy(updatedAt = now).toEntity(recurrence))
        }
        return updated.thenLoad(task.id)
    }

    override suspend fun setDone(id: Long, done: Boolean): AppResult<Task> {
        val now = clock.now().toEpochMilli()
        val changed = storageCall(dispatchers, "set task $id done=$done") {
            dao.setDone(id = id, done = done, completedAt = if (done) now else null, updatedAt = now)
        }
        return changed.thenLoad(id)
    }

    override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean): AppResult<Int> {
        if (ids.isEmpty()) return AppResult.Success(0)
        val now = clock.now().toEpochMilli()
        return storageCall(dispatchers, "set ${ids.size} tasks done=$done") {
            dao.setDoneIn(
                ids = ids.distinct(),
                done = done,
                completedAt = if (done) now else null,
                updatedAt = now,
            )
        }
    }

    override suspend fun snooze(id: Long, until: Instant): AppResult<Task> {
        val now = clock.now().toEpochMilli()
        val changed = storageCall(dispatchers, "snooze task $id") {
            dao.setDueAt(id = id, dueAt = until.toEpochMilli(), updatedAt = now)
        }
        return changed.thenLoad(id)
    }

    override suspend fun archive(id: Long): AppResult<Task> {
        val changed = storageCall(dispatchers, "archive task $id") {
            dao.archive(id, clock.now().toEpochMilli())
        }
        return changed.thenLoad(id)
    }

    override suspend fun unarchive(id: Long): AppResult<Task> {
        val changed = storageCall(dispatchers, "unarchive task $id") {
            dao.unarchive(id, clock.now().toEpochMilli())
        }
        return changed.thenLoad(id)
    }

    override suspend fun archiveAll(ids: Collection<Long>): AppResult<Int> {
        if (ids.isEmpty()) return AppResult.Success(0)
        return storageCall(dispatchers, "archive ${ids.size} tasks") {
            dao.archiveIn(ids.distinct(), clock.now().toEpochMilli())
        }
    }

    /**
     * Soft delete. The row stays, carrying the moment it was binned, so undo restores the same id
     * and a reminder whose `linkedTaskId` points at it keeps resolving. Every list read in
     * [TaskDao] filters the column, so from every screen's point of view the task is gone.
     */
    override suspend fun delete(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "delete task $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        }
    }

    override suspend fun deleteAll(ids: Collection<Long>): AppResult<Int> {
        if (ids.isEmpty()) return AppResult.Success(0)
        return storageCall(dispatchers, "delete ${ids.size} tasks") {
            dao.softDeleteIn(ids.distinct(), clock.now().toEpochMilli())
        }
    }

    override suspend fun restore(id: Long): AppResult<Task> {
        val restored = storageCall(dispatchers, "restore task $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }
        return when (restored) {
            is AppResult.Failure -> restored
            is AppResult.Success ->
                if (restored.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No deleted task with id $id")
        }
    }

    override suspend fun completeWithNextOccurrence(
        id: Long,
        nextDueAt: Instant,
    ): AppResult<TaskOccurrence> {
        val now = clock.now().toEpochMilli()
        val opened = storageCall(dispatchers, "roll task $id over") {
            dao.completeWithNextOccurrence(
                id = id,
                nextDueAt = nextDueAt.toEpochMilli(),
                seriesUuid = UUID.randomUUID().toString(),
                at = now,
            )
        }
        return when (opened) {
            is AppResult.Failure -> opened
            is AppResult.Success -> {
                val nextId = opened.value
                    ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
                when (val completed = byId(id)) {
                    is AppResult.Failure -> completed
                    is AppResult.Success -> when (val next = byId(nextId)) {
                        is AppResult.Failure -> next
                        is AppResult.Success ->
                            AppResult.Success(TaskOccurrence(completed.value, next.value))
                    }
                }
            }
        }
    }

    override suspend fun setTags(taskId: Long, tagIds: Collection<Long>): AppResult<Task> {
        val changed = storageCall(dispatchers, "set tags on task $taskId") {
            dao.replaceTags(taskId, tagIds.distinct(), clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success -> byId(taskId)
        }
    }

    override suspend fun addTag(taskId: Long, tagId: Long): AppResult<Task> {
        val changed = storageCall(dispatchers, "tag task $taskId with $tagId") {
            dao.addTag(taskId, tagId, clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success -> byId(taskId)
        }
    }

    override suspend fun removeTag(taskId: Long, tagId: Long): AppResult<Task> {
        val changed = storageCall(dispatchers, "untag task $taskId") {
            dao.removeTag(taskId, tagId, clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success -> byId(taskId)
        }
    }

    override suspend fun attachFile(taskId: Long, fileId: Long): AppResult<Task> {
        val linked = storageCall(dispatchers, "attach file $fileId to task $taskId") {
            dao.appendAttachment(taskId, fileId, clock.now().toEpochMilli())
        }
        return when (linked) {
            is AppResult.Failure -> linked
            is AppResult.Success -> byId(taskId)
        }
    }

    override suspend fun detachFile(taskId: Long, fileId: Long): AppResult<Task> {
        val removed = storageCall(dispatchers, "detach file $fileId from task $taskId") {
            dao.removeAttachment(taskId, fileId, clock.now().toEpochMilli())
        }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) byId(taskId)
                else failure(MiraErrorCode.NOT_FOUND, "File $fileId is not on task $taskId")
        }
    }

    /**
     * Loads the tags, steps and attachments of a whole page of tasks in three reads rather than
     * three per task. [CHUNK] keeps each `IN (...)` under SQLite's limit on bound variables, which
     * a few years of Abu's tasks will eventually reach.
     */
    private suspend fun hydrate(rows: List<TaskEntity>): List<Task> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.id }
        val tags = ids.chunked(CHUNK).flatMap { dao.tagsForTasks(it) }.groupBy { it.taskId }
        val steps = ids.chunked(CHUNK).flatMap { dao.subtasksForTasks(it) }.groupBy { it.taskId }
        val files = ids.chunked(CHUNK).flatMap { dao.attachmentsForTasks(it) }.groupBy { it.taskId }
        return rows.map { row ->
            row.toDomain(
                codec = recurrence,
                tags = tags[row.id].orEmpty().map { Tag(id = it.tagId, name = it.name) },
                subtasks = steps[row.id].orEmpty().map { it.toDomain() },
                attachmentIds = files[row.id].orEmpty().map { it.fileId },
            )
        }
    }

    /**
     * Groups the tasks [query] selects and resolves each heading's name.
     *
     * Tasks with no project or no client come back in one group whose id and name are null. What
     * that heading is called is a wording decision, so it belongs to `ui/`, not here.
     */
    private suspend fun grouped(
        query: TaskQuery,
        key: (Task) -> Long?,
        names: suspend (List<Long>) -> List<TaskGroupRow>,
    ): AppResult<List<TaskGroup>> {
        val tasks = when (val listed = list(query)) {
            is AppResult.Failure -> return listed
            is AppResult.Success -> listed.value
        }
        if (tasks.isEmpty()) return AppResult.Success(emptyList())

        val groupIds = tasks.mapNotNull(key).distinct()
        val nameById = when (
            val resolved = storageCall(dispatchers, "resolve ${groupIds.size} task group names") {
                groupIds.chunked(CHUNK).flatMap { names(it) }.associate { it.id to it.name }
            }
        ) {
            is AppResult.Failure -> return resolved
            is AppResult.Success -> resolved.value
        }

        val groups = tasks.groupBy(key).map { (id, members) ->
            TaskGroup(id = id, name = id?.let(nameById::get), tasks = members)
        }
        // Named groups first, alphabetically; the tasks that belong to nothing sit at the end.
        return AppResult.Success(
            groups.sortedWith(
                compareBy<TaskGroup> { it.id == null }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() }
                    .thenBy { it.id ?: 0L },
            )
        )
    }

    /** Re-reads the row an update touched, or reports the id that matched nothing. */
    private suspend fun AppResult<Int>.thenLoad(id: Long): AppResult<Task> = when (this) {
        is AppResult.Failure -> this
        is AppResult.Success ->
            if (value > 0) byId(id)
            else failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
    }

    private companion object {
        val EMPTY_COUNTS = TaskCounts(open = 0, dueToday = 0, overdue = 0, completedToday = 0)

        /** Well under SQLite's 999 bound-variable ceiling, with room for the rest of a statement. */
        const val CHUNK = 900
    }
}
