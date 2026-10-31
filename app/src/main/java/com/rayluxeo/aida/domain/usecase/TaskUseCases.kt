package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.command.TaskTarget
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.Subtask
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.repository.SubtaskRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * The rules every task verb holds to, in one place.
 *
 * Create and edit both refuse a blank title, a nonsensical estimate and an impossible repeat. Two
 * copies of those checks would drift, and the one that drifted would be the path Abu happened to
 * use — a task spoken into existence would accept what the editor refuses.
 */
internal object TaskRules {

    /** @return the trimmed title, or the one failure every verb reports for a blank one. */
    fun title(raw: String): AppResult<String> {
        val clean = raw.trim()
        return if (clean.isEmpty()) {
            failure(MiraErrorCode.VALIDATION, "Task title was blank")
        } else {
            AppResult.Success(clean)
        }
    }

    /** An estimate is minutes of work. Zero or less is not a short task, it is a field left empty. */
    fun estimate(minutes: Int?): AppResult.Failure? =
        if (minutes != null && minutes < 1) {
            failure(MiraErrorCode.VALIDATION, "Estimated minutes was $minutes, which is not work")
        } else {
            null
        }

    /**
     * @param start the day the series counts from, so an end date before it can be caught here
     *   rather than as a repeat that produces nothing and looks like a bug in the calculator.
     */
    fun recurrence(rule: RecurrenceRule?, start: LocalDate): AppResult.Failure? {
        if (rule == null) return null
        if (rule.interval < 1) {
            return failure(MiraErrorCode.VALIDATION, "Recurrence interval was ${rule.interval}")
        }
        val until = rule.until
        if (until != null && until.isBefore(start)) {
            return failure(MiraErrorCode.VALIDATION, "Recurrence ends $until but starts $start")
        }
        return null
    }

    /**
     * The moment a task falls due on [date].
     *
     * [keepTimeOf] carries the time of day of the occurrence being repeated, so "every Monday at
     * ten" stays at ten. Null means the task is a day rather than a moment, and lands at the start
     * of it in Abu's zone.
     */
    fun dueAt(date: LocalDate, keepTimeOf: Instant?, zone: ZoneId): Instant =
        if (keepTimeOf == null) {
            date.atStartOfDay(zone).toInstant()
        } else {
            date.atTime(LocalDateTime.ofInstant(keepTimeOf, zone).toLocalTime()).atZone(zone).toInstant()
        }
}

internal fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Puts [tagIds] on a saved task. Tags are not written by [TaskRepository.update], so every verb
 * that can change them goes through the one port that does.
 */
internal suspend fun applyTags(
    tasks: TaskRepository,
    task: Task,
    tagIds: Collection<Long>,
): AppResult<Task> =
    if (tagIds.isEmpty()) AppResult.Success(task) else tasks.setTags(task.id, tagIds.distinct())

/**
 * Adds [titles] to a saved task as unchecked steps, in the order Abu gave them.
 *
 * The task is created without its steps and they are appended here, rather than handed to
 * [TaskRepository.create] on the task, so the steps cannot be written twice by a repository that
 * also reads them off the row.
 */
internal suspend fun addSteps(
    subtasks: SubtaskRepository,
    task: Task,
    titles: List<String>,
): AppResult<Task> {
    val wanted = titles.mapNotNull { it.trimmedOrNull() }
    if (wanted.isEmpty()) return AppResult.Success(task)
    val added = mutableListOf<Subtask>()
    for (title in wanted) {
        when (val step = subtasks.add(task.id, title)) {
            is AppResult.Failure -> return step
            is AppResult.Success -> added += step.value
        }
    }
    return AppResult.Success(task.copy(subtasks = added))
}

/**
 * The one way a task comes into existence, whatever asked for it.
 *
 * Abu speaking it — "আগামী শুক্রবার website complete করার task add করো" — and Abu filling in the
 * composer reach the same validation, because the command pipeline holds no create of its own.
 */
class CreateTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val subtasks: SubtaskRepository,
    private val clock: MiraClock,
) {

    /**
     * @param tagIds tags that already exist; a tag is made by `TagRepository.ensure` before it can
     *   be worn, so a task is never the thing that quietly creates a label.
     * @param subtaskTitles the steps, added unchecked in the order given.
     */
    suspend operator fun invoke(
        title: String,
        description: String? = null,
        notes: String? = null,
        dueAt: Instant? = null,
        dueAllDay: Boolean = false,
        estimatedMinutes: Int? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        projectId: Long? = null,
        clientId: Long? = null,
        tagIds: Collection<Long> = emptyList(),
        subtaskTitles: List<String> = emptyList(),
        recurrence: RecurrenceRule? = null,
        origin: RecordOrigin = RecordOrigin.MANUAL,
    ): AppResult<Task> {
        val clean = when (val checked = TaskRules.title(title)) {
            is AppResult.Failure -> return checked
            is AppResult.Success -> checked.value
        }
        TaskRules.estimate(estimatedMinutes)?.let { return it }

        val zone = clock.zone()
        val today = clock.today()
        val start = dueAt?.let { LocalDateTime.ofInstant(it, zone).toLocalDate() } ?: today
        TaskRules.recurrence(recurrence, start)?.let { return it }

        // A repeat carries its phase in the date it counts from, so a rule given with no date of
        // its own starts today and follows the rule from there. "Every Monday" asked on a Tuesday
        // is therefore due today and then on Mondays, rather than silently having no date at all.
        val firstDueAt = when {
            recurrence == null -> dueAt
            dueAt != null -> dueAt
            else -> today.atStartOfDay(zone).toInstant()
        }
        val allDay = when {
            firstDueAt == null -> false
            dueAt == null -> true
            else -> dueAllDay
        }

        // Anchor a monthly rule to the day it actually starts on. A Monthly with no day can only
        // take one from the date it is counting from, so after a short month it is stuck: 31 Jan
        // becomes 28 Feb and then 28 Mar for ever. "Protimashe" and "every month" both arrive from
        // the NLU with no day, so this belongs here rather than at one call site.
        val anchored = recurrence.anchoredTo(firstDueAt, zone)

        val now = clock.now()
        val task = Task(
            title = clean,
            description = description.trimmedOrNull(),
            notes = notes.trimmedOrNull(),
            dueAt = firstDueAt,
            dueAllDay = allDay,
            estimatedMinutes = estimatedMinutes,
            priority = priority,
            origin = origin,
            projectId = projectId,
            clientId = clientId,
            recurrence = anchored,
            seriesUuid = anchored?.let { UUID.randomUUID().toString() },
            createdAt = now,
            updatedAt = now,
        )
        return tasks.create(task)
            .flatMap { saved -> applyTags(tasks, saved, tagIds) }
            .flatMap { saved -> addSteps(subtasks, saved, subtaskTitles) }
    }
}

/**
 * Applies the fields Abu can change to the stored task.
 *
 * [edited] is a whole task because the editor holds one, but only the fields he can actually edit
 * are taken from it. Completion, archiving, deletion, creation and origin are facts about what
 * happened to the task, not things a form may overwrite — a screen holding a stale copy would
 * otherwise un-complete a task Abu ticked somewhere else between opening the editor and saving.
 */
class EditTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(edited: Task): AppResult<Task> {
        val clean = when (val checked = TaskRules.title(edited.title)) {
            is AppResult.Failure -> return checked
            is AppResult.Success -> checked.value
        }
        TaskRules.estimate(edited.estimatedMinutes)?.let { return it }
        if (edited.tags.any { it.id == 0L }) {
            return failure(MiraErrorCode.VALIDATION, "A tag must exist before a task can wear it")
        }

        val stored = when (val found = tasks.byId(edited.id)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }

        val zone = clock.zone()
        val today = clock.today()
        val start = edited.dueAt?.let { LocalDateTime.ofInstant(it, zone).toLocalDate() } ?: today
        TaskRules.recurrence(edited.recurrence, start)?.let { return it }

        // A repeat added to an undated task starts today, for the reason given in CreateTaskUseCase.
        val dueAt = when {
            edited.recurrence == null -> edited.dueAt
            edited.dueAt != null -> edited.dueAt
            else -> today.atStartOfDay(zone).toInstant()
        }

        val merged = stored.copy(
            title = clean,
            description = edited.description.trimmedOrNull(),
            notes = edited.notes.trimmedOrNull(),
            dueAt = dueAt,
            dueAllDay = when {
                dueAt == null -> false
                edited.dueAt == null -> true
                else -> edited.dueAllDay
            },
            estimatedMinutes = edited.estimatedMinutes,
            priority = edited.priority,
            projectId = edited.projectId,
            clientId = edited.clientId,
            recurrence = edited.recurrence,
            // A task that stops repeating keeps its series id: the occurrences already completed
            // under it are still one history, and orphaning this row would break that trail.
            seriesUuid = stored.seriesUuid ?: edited.recurrence?.let { UUID.randomUUID().toString() },
            updatedAt = clock.now(),
        )
        return tasks.update(merged).flatMap { saved ->
            if (edited.tags.map { it.id }.toSet() == stored.tags.map { it.id }.toSet()) {
                AppResult.Success(saved)
            } else {
                tasks.setTags(saved.id, edited.tags.map { it.id })
            }
        }
    }
}

/**
 * The checkbox and the swipe, which are one toggle rather than two verbs to the hand using them.
 *
 * It is deliberately thin: ticking is [CompleteTaskByIdUseCase], with everything a repeat implies,
 * and un-ticking is [ReopenTaskUseCase]. Routing here is what stops a screen reaching for the
 * repository's `setDone` directly and quietly skipping the next occurrence.
 */
class SetTaskDoneUseCase @Inject constructor(
    private val complete: CompleteTaskByIdUseCase,
    private val reopen: ReopenTaskUseCase,
) {
    suspend operator fun invoke(id: Long, done: Boolean): AppResult<Task> =
        if (done) complete(id).map { it.completed } else reopen(id)
}

/** Sends a task to the bin. The row survives, so [RestoreTaskUseCase] brings back the same id. */
class DeleteTaskUseCase @Inject constructor(private val tasks: TaskRepository) {
    suspend operator fun invoke(id: Long): AppResult<Unit> = tasks.delete(id)
}

/**
 * Takes a task back out of the bin.
 *
 * The same row is un-binned rather than a copy inserted: a copy would take a new id, and the
 * reminder pointing at the old one would stop resolving without anything appearing to be wrong.
 */
class RestoreTaskUseCase @Inject constructor(private val tasks: TaskRepository) {
    suspend operator fun invoke(id: Long): AppResult<Task> = tasks.restore(id)
}

class SearchTasksUseCase @Inject constructor(private val tasks: TaskRepository) {
    suspend operator fun invoke(query: String?, filter: TaskFilter): AppResult<List<Task>> =
        tasks.search(query.trimmedOrNull(), filter)
}

/**
 * Resolves a spoken reference like "call Ahmed" to a single open task.
 * Ambiguity is reported rather than guessed at — Mira asks instead of completing the wrong thing.
 */
class ResolveTaskUseCase @Inject constructor(private val tasks: TaskRepository) {

    sealed interface Resolution {
        data class Exact(val task: Task) : Resolution
        data class Ambiguous(val candidates: List<Task>) : Resolution
        data object None : Resolution
    }

    suspend operator fun invoke(target: TaskTarget): AppResult<Resolution> = when (target) {
        is TaskTarget.ById -> tasks.byId(target.id).map { Resolution.Exact(it) }
        is TaskTarget.ByTitle -> tasks.search(target.title, TaskFilter.OPEN).map { matches ->
            when {
                matches.isEmpty() -> Resolution.None
                matches.size == 1 -> Resolution.Exact(matches.first())
                else -> {
                    val needle = target.title.trim().lowercase()
                    val exact = matches.filter { it.title.trim().lowercase() == needle }
                    if (exact.size == 1) Resolution.Exact(exact.first()) else Resolution.Ambiguous(matches.take(5))
                }
            }
        }
    }
}

/**
 * Completing by what Abu called it rather than by id.
 *
 * The match is resolved first and the completion itself is [CompleteTaskByIdUseCase], so a
 * repeating task finished by voice opens its next occurrence exactly as one ticked on the screen.
 */
class CompleteTaskUseCase @Inject constructor(
    private val resolve: ResolveTaskUseCase,
    private val complete: CompleteTaskByIdUseCase,
) {
    suspend operator fun invoke(target: TaskTarget): AppResult<ResolveTaskUseCase.Resolution> =
        resolve(target).flatMap { resolution ->
            when (resolution) {
                is ResolveTaskUseCase.Resolution.Exact ->
                    complete(resolution.task.id).map { ResolveTaskUseCase.Resolution.Exact(it.completed) }
                else -> AppResult.Success(resolution)
            }
        }
}

/**
 * Pins a monthly repeat to the day its first occurrence falls on.
 *
 * Only [RecurrenceRule.Monthly] needs it, and only when it names no day: every other rule carries
 * its own phase. Returns the rule unchanged when there is nothing to anchor to, because inventing
 * a day for a task with no date would be a guess.
 */
internal fun RecurrenceRule?.anchoredTo(dueAt: Instant?, zone: ZoneId): RecurrenceRule? {
    val rule = this ?: return null
    if (rule !is RecurrenceRule.Monthly || rule.dayOfMonth != null) return rule
    val day = dueAt?.let { LocalDateTime.ofInstant(it, zone).toLocalDate().dayOfMonth } ?: return rule
    return rule.copy(dayOfMonth = day)
}
