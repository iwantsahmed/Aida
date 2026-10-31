package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SubtaskRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import com.rayluxeo.aida.domain.task.RecurrenceCalculator
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * What finishing a task left behind.
 *
 * [nextOccurrence] is null both for a task that happens once and for the last occurrence of a
 * series that has reached its end date. The two are told apart by [completed] — a repeating task
 * with nothing after it was the last one, and Mira can say so.
 */
data class TaskCompletion(
    val completed: Task,
    val nextOccurrence: Task? = null,
)

/**
 * Closes one task, and opens the next one when the task repeats.
 *
 * The next occurrence is a new row rather than the same row moved forward: a completed Monday and
 * next Monday are two different facts, and squashing them would lose the history Abu can look
 * back at. Storage writes both halves in one transaction, because a crash between them would
 * either lose the repeat or leave him with two open Mondays.
 */
class CompleteTaskByIdUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val recurrences: RecurrenceCalculator,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(id: Long): AppResult<TaskCompletion> {
        val task = when (val found = tasks.byId(id)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        val rule = task.recurrence ?: return close(id)

        val zone = clock.zone()
        val dueDate = task.dueAt?.let { LocalDateTime.ofInstant(it, zone).toLocalDate() }
        // Counted from the day it was due, or from today once that day has gone: finishing
        // something late must not open its next occurrence in the past.
        val from = dueDate?.let { maxOf(it, clock.today()) } ?: clock.today()
        val nextDate = recurrences.next(rule, from) ?: return close(id)

        val nextDueAt = TaskRules.dueAt(
            date = nextDate,
            keepTimeOf = task.dueAt.takeUnless { task.dueAllDay },
            zone = zone,
        )
        return tasks.completeWithNextOccurrence(id, nextDueAt)
            .map { TaskCompletion(completed = it.completed, nextOccurrence = it.next) }
    }

    /** The end of a series is not a failure: the task closes, and nothing follows it. */
    private suspend fun close(id: Long): AppResult<TaskCompletion> =
        tasks.setDone(id, true).map { TaskCompletion(it) }
}

/**
 * Puts a finished task back on the list, clearing the moment it was completed.
 *
 * Reopening one occurrence of a series deliberately leaves the occurrence that followed it alone.
 * That next one is its own row, and by the time Abu reopens this one it may already have been
 * re-dated, given steps, or finished; deleting it to keep the series tidy would throw away work he
 * never asked about. He can remove it himself if he wants it gone.
 */
class ReopenTaskUseCase @Inject constructor(private val tasks: TaskRepository) {
    suspend operator fun invoke(id: Long): AppResult<Task> = tasks.setDone(id, false)
}

/** Out of the way but not gone: an archived task keeps its history and its links. */
class ArchiveTaskUseCase @Inject constructor(private val tasks: TaskRepository) {
    suspend operator fun invoke(id: Long): AppResult<Task> = tasks.archive(id)
}

class UnarchiveTaskUseCase @Inject constructor(private val tasks: TaskRepository) {
    suspend operator fun invoke(id: Long): AppResult<Task> = tasks.unarchive(id)
}

/**
 * Makes another task like this one.
 *
 * The copy is deliberately a plain single task: it carries no completion, no completion time, and
 * no repeat or series of its own. Cloning a series would give Abu two of everything from then on,
 * every week, and he would find out only once the duplicates had piled up. A second series is
 * something he sets up on purpose.
 *
 * The title is copied unchanged. Marking it as a copy would mean a word, and a word belongs to
 * whichever language he is speaking — the domain has no business inventing one.
 *
 * The copy is recorded as Abu's own doing, because duplicating is something he does on the screen
 * in front of him; there is no spoken verb for it to arrive through.
 */
class DuplicateTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val subtasks: SubtaskRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(id: Long): AppResult<Task> {
        val source = when (val found = tasks.byId(id)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        val now = clock.now()
        val copy = Task(
            title = source.title,
            description = source.description,
            notes = source.notes,
            dueAt = source.dueAt,
            dueAllDay = source.dueAllDay,
            estimatedMinutes = source.estimatedMinutes,
            priority = source.priority,
            origin = RecordOrigin.MANUAL,
            projectId = source.projectId,
            clientId = source.clientId,
            createdAt = now,
            updatedAt = now,
        )
        return tasks.create(copy)
            .flatMap { saved -> applyTags(tasks, saved, source.tags.map { it.id }) }
            .flatMap { saved -> addSteps(subtasks, saved, source.subtasks.map { it.title }) }
    }
}

/**
 * Moves a task's due moment forward, and moves its reminder with it.
 *
 * A reminder left where it was would fire for a task that is no longer due then, which is the one
 * way a snooze can quietly make things worse. The lead time is kept — a reminder an hour before
 * the task stays an hour before it — unless keeping it would put the reminder in the past, in
 * which case the reminder lands on the new due moment rather than being dropped.
 */
class SnoozeTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(id: Long, by: Duration): AppResult<Task> {
        if (by.isZero || by.isNegative) {
            return failure(MiraErrorCode.VALIDATION, "Snooze of $by does not move anything")
        }
        val task = when (val found = tasks.byId(id)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        // Snoozing is moving a date Abu already gave. There is nothing to move on a task with no
        // date, and inventing one would be Mira deciding when it is due.
        val due = task.dueAt
            ?: return failure(MiraErrorCode.VALIDATION, "Task $id has no due date to move")

        val now = clock.now()
        // From now when the task is already overdue: "an hour" means an hour from now, not an
        // hour after the moment it was missed.
        val until = maxOf(due, now).plus(by)

        return tasks.snooze(id, until).flatMap { moved ->
            moveReminders(taskId = id, delta = Duration.between(due, until), until = until, now = now)
                .map { moved }
        }
    }

    private suspend fun moveReminders(
        taskId: Long,
        delta: Duration,
        until: Instant,
        now: Instant,
    ): AppResult<Unit> {
        val linked = reminders.observeUpcoming().first().filter { it.linkedTaskId == taskId }
        for (reminder in linked) {
            val shifted = reminder.triggerAt.plus(delta)
            val at = if (shifted.isAfter(now)) shifted else until
            scheduler.cancel(reminder.id).failureOrNull()?.let { return it }
            reminders.cancel(reminder.id).failureOrNull()?.let { return it }
            val created = reminders.create(
                Reminder(
                    title = reminder.title,
                    triggerAt = at,
                    linkedTaskId = taskId,
                    origin = reminder.origin,
                    createdAt = now,
                ),
            )
            when (created) {
                is AppResult.Failure -> return created
                is AppResult.Success -> scheduler.schedule(created.value).failureOrNull()?.let { return it }
            }
        }
        return AppResult.Success(Unit)
    }
}

internal fun AppResult<*>.failureOrNull(): AppResult.Failure? = this as? AppResult.Failure
