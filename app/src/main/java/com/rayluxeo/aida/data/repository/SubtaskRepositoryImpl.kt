package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.SubtaskDao
import com.rayluxeo.aida.data.db.entity.SubtaskEntity
import com.rayluxeo.aida.domain.model.Subtask
import com.rayluxeo.aida.domain.repository.SubtaskRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Adding a step to a task that does not exist is refused by the foreign key rather than by a
 * lookup here: SQLite is the authority on whether the parent is there, and a check in Kotlin
 * would only be a second opinion that can go stale. The constraint surfaces as a storage
 * failure, never as an exception at the call site.
 */
@Singleton
class SubtaskRepositoryImpl @Inject constructor(
    private val dao: SubtaskDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : SubtaskRepository {

    override fun observeForTask(taskId: Long): Flow<List<Subtask>> =
        dao.observeForTask(taskId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Subtask> {
        val found = storageCall(dispatchers, "load subtask $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")
        }
    }

    override suspend fun add(taskId: Long, title: String): AppResult<Subtask> {
        val clean = title.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Subtask title was blank")
        val now = clock.now().toEpochMilli()
        val inserted = storageCall(dispatchers, "add subtask to task $taskId") {
            dao.append(
                SubtaskEntity(
                    taskId = taskId,
                    title = clean,
                    isDone = false,
                    completedAtEpochMillis = null,
                    position = 0,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            )
        }
        return when (inserted) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> byId(inserted.value)
        }
    }

    override suspend fun rename(id: Long, title: String): AppResult<Subtask> {
        val clean = title.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Subtask title was blank")
        val changed = storageCall(dispatchers, "rename subtask $id") {
            dao.rename(id = id, title = clean, updatedAt = clock.now().toEpochMilli())
        }
        return changed.thenLoad(id)
    }

    override suspend fun setDone(id: Long, done: Boolean): AppResult<Subtask> {
        val now = clock.now().toEpochMilli()
        val changed = storageCall(dispatchers, "set subtask $id done=$done") {
            dao.setDone(
                id = id,
                done = done,
                completedAt = if (done) now else null,
                updatedAt = now,
            )
        }
        return changed.thenLoad(id)
    }

    /**
     * The list is checked against what the task actually holds before anything is written, so the
     * renumbering itself can never be partial. Checking inside the transaction and throwing would
     * roll back correctly too, but it would report a caller's mistake as a storage fault.
     */
    override suspend fun reorder(taskId: Long, orderedIds: List<Long>): AppResult<Unit> {
        val current = storageCall(dispatchers, "list subtasks of task $taskId") {
            dao.listForTask(taskId)
        }
        return when (current) {
            is AppResult.Failure -> current
            is AppResult.Success -> {
                val known = current.value.map { it.id }
                if (orderedIds.size != known.size || orderedIds.toSet() != known.toSet()) {
                    failure(
                        MiraErrorCode.VALIDATION,
                        "Reorder list does not name exactly the steps of task $taskId",
                    )
                } else {
                    storageCall(dispatchers, "reorder subtasks of task $taskId") {
                        dao.reorder(taskId, orderedIds, clock.now().toEpochMilli())
                    }
                }
            }
        }
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "delete subtask $id") { dao.deleteById(id) }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")
        }
    }

    /** Re-reads the row an update touched, or reports the id that matched nothing. */
    private suspend fun AppResult<Int>.thenLoad(id: Long): AppResult<Subtask> = when (this) {
        is AppResult.Failure -> this
        is AppResult.Success ->
            if (value > 0) byId(id)
            else failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")
    }
}
