package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraError
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * What a bulk action actually did.
 *
 * Both halves are reported because a bulk complete that quietly drops three of twenty is worse
 * than one that says so: the twenty disappear from the list either way, and only the count tells
 * Abu which of them he still has to deal with. [succeeded] is in the order he selected them, so a
 * screen can keep the failures selected and let him try again.
 */
data class BulkTaskResult(
    val succeeded: List<Long>,
    val failed: List<FailedTask>,
) {
    val requested: Int get() = succeeded.size + failed.size
    val allSucceeded: Boolean get() = failed.isEmpty()
}

/** One task a bulk action could not carry out, and the reason it could not. */
data class FailedTask(
    val taskId: Long,
    val error: MiraError,
)

/** The selection, split into the tasks that are really there and the ids that are not. */
private class Selection(
    val found: List<Task>,
    val missing: List<FailedTask>,
)

/**
 * Reads every selected task before anything is written.
 *
 * It is what lets a bulk action name the ids it could not act on. The transactional bulk writes
 * report a count of rows changed, and a count cannot tell a task that was already archived from
 * one that is no longer there.
 */
private suspend fun select(tasks: TaskRepository, ids: List<Long>): Selection {
    val found = mutableListOf<Task>()
    val missing = mutableListOf<FailedTask>()
    for (id in ids) {
        when (val task = tasks.byId(id)) {
            is AppResult.Failure -> missing += FailedTask(id, task.error)
            is AppResult.Success -> found += task.value
        }
    }
    return Selection(found, missing)
}

/** Keeps the reported order the one Abu selected in, whichever path each id took. */
private fun result(ids: List<Long>, succeeded: Collection<Long>, failed: List<FailedTask>): BulkTaskResult {
    val ok = succeeded.toSet()
    return BulkTaskResult(succeeded = ids.filter { it in ok }, failed = failed)
}

private fun emptySelection(): AppResult.Failure =
    failure(MiraErrorCode.VALIDATION, "No tasks were selected")

/**
 * Finishes everything selected.
 *
 * The tasks that happen once are closed in one transaction; the repeating ones go one at a time
 * through [CompleteTaskByIdUseCase], because each has its own next occurrence to open and its own
 * date to work out. Ticking twenty tasks at once must not be the path where a repeat silently
 * stops repeating.
 */
class BulkCompleteTasksUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val complete: CompleteTaskByIdUseCase,
) {

    suspend operator fun invoke(ids: Collection<Long>): AppResult<BulkTaskResult> {
        val unique = ids.distinct()
        if (unique.isEmpty()) return emptySelection()

        val selection = select(tasks, unique)
        val failed = selection.missing.toMutableList()
        val succeeded = mutableListOf<Long>()

        val (repeating, once) = selection.found.partition { it.isRecurring }
        if (once.isNotEmpty()) {
            when (val done = tasks.setDoneAll(once.map { it.id }, true)) {
                is AppResult.Failure -> once.forEach { failed += FailedTask(it.id, done.error) }
                is AppResult.Success -> succeeded += once.map { it.id }
            }
        }
        for (task in repeating) {
            when (val done = complete(task.id)) {
                is AppResult.Failure -> failed += FailedTask(task.id, done.error)
                is AppResult.Success -> succeeded += task.id
            }
        }
        return AppResult.Success(result(unique, succeeded, failed))
    }
}

/** Archives everything selected in one transaction. Ids that are not there are reported, not hidden. */
class BulkArchiveTasksUseCase @Inject constructor(private val tasks: TaskRepository) {

    suspend operator fun invoke(ids: Collection<Long>): AppResult<BulkTaskResult> {
        val unique = ids.distinct()
        if (unique.isEmpty()) return emptySelection()

        val selection = select(tasks, unique)
        val failed = selection.missing.toMutableList()
        val succeeded = mutableListOf<Long>()
        if (selection.found.isNotEmpty()) {
            val present = selection.found.map { it.id }
            when (val archived = tasks.archiveAll(present)) {
                is AppResult.Failure -> present.forEach { failed += FailedTask(it, archived.error) }
                is AppResult.Success -> succeeded += present
            }
        }
        return AppResult.Success(result(unique, succeeded, failed))
    }
}

/**
 * Sends everything selected to the bin, in one transaction.
 *
 * The rows survive, so every one of them can be restored with the id it had — which is what keeps
 * a reminder pointing at a task Abu binned by accident resolving after he undoes it.
 */
class BulkDeleteTasksUseCase @Inject constructor(private val tasks: TaskRepository) {

    suspend operator fun invoke(ids: Collection<Long>): AppResult<BulkTaskResult> {
        val unique = ids.distinct()
        if (unique.isEmpty()) return emptySelection()

        val selection = select(tasks, unique)
        val failed = selection.missing.toMutableList()
        val succeeded = mutableListOf<Long>()
        if (selection.found.isNotEmpty()) {
            val present = selection.found.map { it.id }
            when (val removed = tasks.deleteAll(present)) {
                is AppResult.Failure -> present.forEach { failed += FailedTask(it, removed.error) }
                is AppResult.Success -> succeeded += present
            }
        }
        return AppResult.Success(result(unique, succeeded, failed))
    }
}
