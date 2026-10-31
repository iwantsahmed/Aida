package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.model.Subtask
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SubtaskRepository
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.repository.TaskOccurrence
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import com.rayluxeo.aida.domain.task.DefaultRecurrenceCalculator
import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The task verbs, every one of which Mira reaches through whether Abu typed it, said it in
 * Bengali, swiped it or ticked a box.
 *
 * What is worth asserting here is what he could not notice going wrong: a repeat that quietly
 * stops repeating, a next occurrence opened in the past, steps copied forward already ticked, a
 * snooze that moves the task but leaves the reminder where it was.
 *
 * Nothing is injected — Hilt's local-test transform does not run under AGP 9 (google/dagger#5261)
 * — and the clock never moves, so every date below is the same on every run. The recurrence
 * arithmetic is the real [DefaultRecurrenceCalculator] rather than a stand-in, because a fake that
 * agreed with these expectations would prove only that the test agrees with itself.
 */
class TaskUseCaseTest {

    private val clock = FixedMiraClock()
    private val tasks = FakeTaskRepository(clock)
    private val steps = FakeSubtaskRepository(clock)
    private val reminders = FakeReminderRepository()
    private val scheduler = FakeReminderScheduler()
    private val recurrences = DefaultRecurrenceCalculator()

    private val create = CreateTaskUseCase(tasks, steps, clock)
    private val edit = EditTaskUseCase(tasks, clock)
    private val complete = CompleteTaskByIdUseCase(tasks, recurrences, clock)
    private val reopen = ReopenTaskUseCase(tasks)
    private val setDone = SetTaskDoneUseCase(complete, reopen)
    private val archive = ArchiveTaskUseCase(tasks)
    private val unarchive = UnarchiveTaskUseCase(tasks)
    private val duplicate = DuplicateTaskUseCase(tasks, steps, clock)
    private val snooze = SnoozeTaskUseCase(tasks, reminders, scheduler, clock)
    private val delete = DeleteTaskUseCase(tasks)
    private val restore = RestoreTaskUseCase(tasks)

    // ------------------------------------------------------------------ create

    @Test
    fun `create refuses a blank title`() = runTest {
        val created = create(title = "   ")

        assertThat(created).isInstanceOf(AppResult.Failure::class.java)
        assertThat((created as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(tasks.snapshot()).isEmpty()
    }

    @Test
    fun `create trims the title and keeps description and notes apart`() = runTest {
        val created = create(
            title = "  Finish the homepage  ",
            description = " what it is ",
            notes = "  ",
            estimatedMinutes = 90,
        ).getOrNull()!!

        assertThat(created.title).isEqualTo("Finish the homepage")
        assertThat(created.description).isEqualTo("what it is")
        assertThat(created.notes).isNull()
        assertThat(created.estimatedMinutes).isEqualTo(90)
    }

    @Test
    fun `create refuses an estimate that is not work`() = runTest {
        assertThat(create(title = "Anything", estimatedMinutes = 0).errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(create(title = "Anything", estimatedMinutes = -30).errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `create refuses a repeat with no interval`() = runTest {
        val created = create(title = "Review", recurrence = RecurrenceRule.Daily(interval = 0))

        assertThat(created.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(tasks.snapshot()).isEmpty()
    }

    @Test
    fun `create refuses a repeat that ends before it starts`() = runTest {
        val created = create(
            title = "Review",
            dueAt = FixedMiraClock.dhaka(2026, 3, 12, 10),
            recurrence = RecurrenceRule.Daily(until = LocalDate.of(2026, 3, 11)),
        )

        assertThat(created.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `create dates a repeat that arrived without one`() = runTest {
        val created = create(
            title = "Review Rayluxeo",
            recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
        ).getOrNull()!!

        assertThat(created.dueAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 10, 0))
        assertThat(created.dueAllDay).isTrue()
    }

    @Test
    fun `create ties a repeat to a series and leaves a one-off without one`() = runTest {
        val repeating = create(title = "Review", recurrence = RecurrenceRule.Weekdays()).getOrNull()!!
        val once = create(title = "Send the proposal").getOrNull()!!

        assertThat(repeating.seriesUuid).isNotNull()
        assertThat(once.seriesUuid).isNull()
        assertThat(once.dueAt).isNull()
        assertThat(once.dueAllDay).isFalse()
    }

    @Test
    fun `create adds the steps unchecked and in the order given`() = runTest {
        val created = create(
            title = "Ship the site",
            subtaskTitles = listOf(" copy ", "images", "   "),
        ).getOrNull()!!

        val stored = steps.forTask(created.id)
        assertThat(stored.map { it.title }).containsExactly("copy", "images").inOrder()
        assertThat(stored.none { it.isDone }).isTrue()
        assertThat(created.subtasks.map { it.title }).containsExactly("copy", "images").inOrder()
    }

    @Test
    fun `create reports a step that could not be written`() = runTest {
        steps.failWith = MiraErrorCode.STORAGE

        val created = create(title = "Ship the site", subtaskTitles = listOf("copy"))

        assertThat(created.errorCode()).isEqualTo(MiraErrorCode.STORAGE)
        // The task row is already there: the port has no create-with-steps, so the steps are
        // appended afterwards and a failure among them cannot roll the task back. Saying so is
        // the honest outcome — the alternative is a create that reports success with no steps.
        assertThat(tasks.snapshot()).hasSize(1)
    }

    @Test
    fun `create puts the tags on through the port that owns them`() = runTest {
        val created = create(title = "Invoice Ahmed", tagIds = listOf(7L, 7L, 9L)).getOrNull()!!

        assertThat(tasks.taggedWith(created.id)).containsExactly(7L, 9L)
        assertThat(created.tags.map { it.id }).containsExactly(7L, 9L)
    }

    // ------------------------------------------------------------------ edit

    @Test
    fun `edit applies the named fields and keeps what happened to the task`() = runTest {
        val stored = tasks.seed(
            task(
                title = "Old title",
                isDone = true,
                completedAt = FixedMiraClock.dhaka(2026, 3, 9, 18),
                archivedAt = FixedMiraClock.dhaka(2026, 3, 9, 19),
                origin = RecordOrigin.VOICE,
            ),
        ).first()

        val saved = edit(
            stored.copy(
                title = "New title",
                priority = TaskPriority.HIGH,
                isDone = false,
                completedAt = null,
                archivedAt = null,
                origin = RecordOrigin.MANUAL,
            ),
        ).getOrNull()!!

        assertThat(saved.title).isEqualTo("New title")
        assertThat(saved.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(saved.isDone).isTrue()
        assertThat(saved.completedAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 9, 18))
        assertThat(saved.archivedAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 9, 19))
        assertThat(saved.origin).isEqualTo(RecordOrigin.VOICE)
        assertThat(saved.createdAt).isEqualTo(stored.createdAt)
    }

    @Test
    fun `edit refuses a blank title and an unknown task`() = runTest {
        val stored = tasks.seed(task(title = "Real")).first()

        assertThat(edit(stored.copy(title = " ")).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(edit(task(id = 404L, title = "Ghost")).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `edit refuses a tag that has not been made yet`() = runTest {
        val stored = tasks.seed(task(title = "Real")).first()

        val saved = edit(stored.copy(tags = listOf(Tag(id = 0L, name = "urgent"))))

        assertThat(saved.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `edit replaces the tags only when they changed`() = runTest {
        val stored = tasks.seed(task(title = "Real")).first()
        tasks.setTags(stored.id, listOf(4L))
        tasks.tagWrites = 0

        edit(tasks.row(stored.id)!!.copy(priority = TaskPriority.HIGH))
        assertThat(tasks.tagWrites).isEqualTo(0)

        edit(tasks.row(stored.id)!!.copy(tags = listOf(Tag(id = 5L, name = "later"))))
        assertThat(tasks.tagWrites).isEqualTo(1)
        assertThat(tasks.taggedWith(stored.id)).containsExactly(5L)
    }

    @Test
    fun `edit stops a task being all-day once its date is gone`() = runTest {
        val stored = tasks.seed(
            task(title = "Friday", dueAt = FixedMiraClock.dhaka(2026, 3, 13, 0), dueAllDay = true),
        ).first()

        val saved = edit(stored.copy(dueAt = null)).getOrNull()!!

        assertThat(saved.dueAt).isNull()
        assertThat(saved.dueAllDay).isFalse()
    }

    @Test
    fun `edit opens a series when a repeat is added and keeps it when one is removed`() = runTest {
        val stored = tasks.seed(task(title = "Review", dueAt = FixedMiraClock.dhaka(2026, 3, 16, 10))).first()

        val repeating = edit(stored.copy(recurrence = RecurrenceRule.Weekdays())).getOrNull()!!
        assertThat(repeating.seriesUuid).isNotNull()

        val stopped = edit(repeating.copy(recurrence = null)).getOrNull()!!
        assertThat(stopped.recurrence).isNull()
        assertThat(stopped.seriesUuid).isEqualTo(repeating.seriesUuid)
    }

    // ------------------------------------------------------------------ complete and reopen

    @Test
    fun `complete closes a one-off and opens nothing`() = runTest {
        val stored = tasks.seed(task(title = "Send the proposal")).first()

        val completion = complete(stored.id).getOrNull()!!

        assertThat(completion.completed.isDone).isTrue()
        assertThat(completion.completed.completedAt).isEqualTo(clock.now())
        assertThat(completion.nextOccurrence).isNull()
        assertThat(tasks.completions).isEmpty()
    }

    @Test
    fun `complete opens the next occurrence at the time of day the task was due`() = runTest {
        val stored = tasks.seed(
            task(
                title = "Weekly review",
                dueAt = FixedMiraClock.dhaka(2026, 3, 9, 10),
                recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
                seriesUuid = "series-1",
            ),
        ).first()

        val completion = complete(stored.id).getOrNull()!!

        assertThat(tasks.completions).containsExactly(
            FakeTaskRepository.Completion(stored.id, FixedMiraClock.dhaka(2026, 3, 16, 10)),
        )
        assertThat(completion.nextOccurrence?.dueAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 16, 10))
        assertThat(completion.nextOccurrence?.seriesUuid).isEqualTo("series-1")
        assertThat(completion.nextOccurrence?.isDone).isFalse()
    }

    @Test
    fun `complete keeps a task that was a day a day`() = runTest {
        val stored = tasks.seed(
            task(
                title = "Weekly review",
                dueAt = FixedMiraClock.dhaka(2026, 3, 9, 0),
                dueAllDay = true,
                recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
            ),
        ).first()

        complete(stored.id)

        assertThat(tasks.completions.single().nextDueAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 16, 0))
    }

    @Test
    fun `complete never opens the next occurrence in the past`() = runTest {
        val stored = tasks.seed(
            task(
                title = "Daily standup",
                dueAt = FixedMiraClock.dhaka(2026, 3, 5, 9),
                recurrence = RecurrenceRule.Daily(),
            ),
        ).first()

        complete(stored.id)

        assertThat(tasks.completions.single().nextDueAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 9))
    }

    @Test
    fun `complete ends a series once its end date has gone by`() = runTest {
        val stored = tasks.seed(
            task(
                title = "Daily standup",
                dueAt = FixedMiraClock.dhaka(2026, 3, 9, 9),
                recurrence = RecurrenceRule.Daily(until = LocalDate.of(2026, 3, 10)),
            ),
        ).first()

        val completion = complete(stored.id).getOrNull()!!

        assertThat(completion.nextOccurrence).isNull()
        assertThat(completion.completed.isDone).isTrue()
        assertThat(tasks.completions).isEmpty()
    }

    @Test
    fun `complete carries the work across and leaves this occurrence's own notes behind`() = runTest {
        val stored = tasks.seed(
            task(
                title = "Weekly review",
                description = "what it is",
                notes = "rang him twice today",
                dueAt = FixedMiraClock.dhaka(2026, 3, 9, 10),
                projectId = 3L,
                clientId = 4L,
                recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
            ),
        ).first()
        tasks.setTags(stored.id, listOf(6L))
        steps.add(stored.id, "read the numbers")
        steps.setDone(steps.forTask(stored.id).first().id, true)
        tasks.attachSteps(stored.id, steps.forTask(stored.id))

        val next = complete(stored.id).getOrNull()!!.nextOccurrence!!

        assertThat(next.title).isEqualTo("Weekly review")
        assertThat(next.description).isEqualTo("what it is")
        assertThat(next.notes).isNull()
        assertThat(next.projectId).isEqualTo(3L)
        assertThat(next.clientId).isEqualTo(4L)
        assertThat(next.tags.map { it.id }).containsExactly(6L)
        assertThat(next.subtasks.map { it.title }).containsExactly("read the numbers")
        assertThat(next.subtasks.none { it.isDone }).isTrue()
    }

    @Test
    fun `the tick routes through completion and the untick reopens`() = runTest {
        val stored = tasks.seed(
            task(
                title = "Weekly review",
                dueAt = FixedMiraClock.dhaka(2026, 3, 9, 10),
                recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
            ),
        ).first()

        val done = setDone(stored.id, true).getOrNull()!!
        assertThat(done.isDone).isTrue()
        assertThat(tasks.completions).hasSize(1)

        val open = setDone(stored.id, false).getOrNull()!!
        assertThat(open.isDone).isFalse()
        assertThat(open.completedAt).isNull()
    }

    @Test
    fun `reopening one occurrence leaves the one that followed it alone`() = runTest {
        val stored = tasks.seed(
            task(
                title = "Weekly review",
                dueAt = FixedMiraClock.dhaka(2026, 3, 9, 10),
                recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
            ),
        ).first()
        val next = complete(stored.id).getOrNull()!!.nextOccurrence!!

        reopen(stored.id)

        assertThat(tasks.row(next.id)).isNotNull()
        assertThat(tasks.row(stored.id)!!.isDone).isFalse()
    }

    // ------------------------------------------------------------------ bin, archive, duplicate

    @Test
    fun `delete then restore brings back the same row`() = runTest {
        val stored = tasks.seed(task(title = "Call Ahmed")).first()

        delete(stored.id)
        assertThat(tasks.row(stored.id)!!.deletedAt).isEqualTo(clock.now())

        val back = restore(stored.id).getOrNull()!!
        assertThat(back.id).isEqualTo(stored.id)
        assertThat(back.deletedAt).isNull()
    }

    @Test
    fun `archive puts a task aside and unarchive brings it back`() = runTest {
        val stored = tasks.seed(task(title = "Old brief")).first()

        val archived = archive(stored.id).getOrNull()!!
        assertThat(archived.archivedAt).isEqualTo(clock.now())

        val live = unarchive(stored.id).getOrNull()!!
        assertThat(live.archivedAt).isNull()
    }

    @Test
    fun `duplicate copies the work but not the completion or the repeat`() = runTest {
        val stored = tasks.seed(
            task(
                title = "Weekly review",
                description = "what it is",
                notes = "for this one",
                dueAt = FixedMiraClock.dhaka(2026, 3, 9, 10),
                priority = TaskPriority.HIGH,
                isDone = true,
                completedAt = FixedMiraClock.dhaka(2026, 3, 9, 18),
                projectId = 3L,
                clientId = 4L,
                recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
                seriesUuid = "series-1",
            ),
        ).first()
        tasks.setTags(stored.id, listOf(6L))
        steps.add(stored.id, "read the numbers")
        steps.setDone(steps.forTask(stored.id).first().id, true)
        tasks.attachSteps(stored.id, steps.forTask(stored.id))

        val copy = duplicate(stored.id).getOrNull()!!

        assertThat(copy.id).isNotEqualTo(stored.id)
        assertThat(copy.title).isEqualTo("Weekly review")
        assertThat(copy.description).isEqualTo("what it is")
        assertThat(copy.notes).isEqualTo("for this one")
        assertThat(copy.dueAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 9, 10))
        assertThat(copy.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(copy.projectId).isEqualTo(3L)
        assertThat(copy.clientId).isEqualTo(4L)
        assertThat(copy.tags.map { it.id }).containsExactly(6L)
        assertThat(copy.isDone).isFalse()
        assertThat(copy.completedAt).isNull()
        assertThat(copy.recurrence).isNull()
        assertThat(copy.seriesUuid).isNull()
        assertThat(steps.forTask(copy.id).map { it.title }).containsExactly("read the numbers")
        assertThat(steps.forTask(copy.id).none { it.isDone }).isTrue()
    }

    // ------------------------------------------------------------------ snooze

    @Test
    fun `snooze refuses a task with no date and a duration that moves nothing`() = runTest {
        val undated = tasks.seed(task(title = "Someday")).first()
        val tomorrow = FixedMiraClock.dhaka(2026, 3, 11, 10)
        val dated = tasks.seed(task(title = "Tomorrow", dueAt = tomorrow)).first()

        assertThat(snooze(undated.id, Duration.ofHours(1)).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(snooze(dated.id, Duration.ZERO).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(snooze(dated.id, Duration.ofHours(-2)).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `snooze pushes a future due moment out by the duration`() = runTest {
        val stored = tasks.seed(task(title = "Tomorrow", dueAt = FixedMiraClock.dhaka(2026, 3, 11, 10)))
            .first()

        val moved = snooze(stored.id, Duration.ofHours(2)).getOrNull()!!

        assertThat(moved.dueAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 12))
    }

    @Test
    fun `snooze counts from now when the task is already overdue`() = runTest {
        val stored = tasks.seed(task(title = "Late", dueAt = FixedMiraClock.dhaka(2026, 3, 9, 9))).first()

        val moved = snooze(stored.id, Duration.ofHours(1)).getOrNull()!!

        assertThat(moved.dueAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 10, 11))
        assertThat(moved.dueAllDay).isFalse()
    }

    @Test
    fun `snooze moves the reminder with the task and re-arms it`() = runTest {
        val stored = tasks.seed(task(title = "Call Ahmed", dueAt = FixedMiraClock.dhaka(2026, 3, 11, 10)))
            .first()
        val reminder = reminders.seed(
            Reminder(
                title = "Call Ahmed",
                triggerAt = FixedMiraClock.dhaka(2026, 3, 11, 9),
                linkedTaskId = stored.id,
                createdAt = clock.now(),
            ),
        ).first()

        snooze(stored.id, Duration.ofHours(2))

        assertThat(scheduler.cancelled).containsExactly(reminder.id)
        val live = reminders.scheduledRows()
        assertThat(live).hasSize(1)
        assertThat(live.single().triggerAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 11))
        assertThat(live.single().linkedTaskId).isEqualTo(stored.id)
        assertThat(scheduler.armed).containsExactly(live.single().id)
    }

    @Test
    fun `snooze puts a reminder whose lead time has gone on the new due moment`() = runTest {
        val stored = tasks.seed(task(title = "Late", dueAt = FixedMiraClock.dhaka(2026, 3, 9, 9))).first()
        reminders.seed(
            Reminder(
                title = "Late",
                triggerAt = FixedMiraClock.dhaka(2026, 3, 9, 8),
                linkedTaskId = stored.id,
                createdAt = clock.now(),
            ),
        )

        snooze(stored.id, Duration.ofHours(1))

        assertThat(reminders.scheduledRows().single().triggerAt)
            .isEqualTo(FixedMiraClock.dhaka(2026, 3, 10, 11))
    }

    @Test
    fun `snooze leaves another task's reminder where it was`() = runTest {
        val mine = tasks.seed(task(title = "Mine", dueAt = FixedMiraClock.dhaka(2026, 3, 11, 10))).first()
        val other = tasks.seed(task(title = "Other", dueAt = FixedMiraClock.dhaka(2026, 3, 11, 10))).first()
        reminders.seed(
            Reminder(
                title = "Other",
                triggerAt = FixedMiraClock.dhaka(2026, 3, 11, 9),
                linkedTaskId = other.id,
                createdAt = clock.now(),
            ),
        )

        snooze(mine.id, Duration.ofHours(2))

        assertThat(scheduler.cancelled).isEmpty()
        assertThat(reminders.scheduledRows().single().triggerAt)
            .isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 9))
    }

    @Test
    fun `a monthly repeat is pinned to the day it starts on`() = runTest {
        // "Protimashe" and "every month" both arrive from the NLU with no day. A Monthly that
        // names none can only take one from the date it is counting from, so after a short month
        // it is stuck — 31 Jan becomes 28 Feb and then 28 Mar for ever.
        val lastDay = LocalDate.of(2026, 1, 31).atTime(9, 0).atZone(clock.zone()).toInstant()

        val created = create(
            title = "Send the monthly invoice",
            dueAt = lastDay,
            recurrence = RecurrenceRule.Monthly(),
        ).getOrNull()!!

        assertThat((created.recurrence as RecurrenceRule.Monthly).dayOfMonth).isEqualTo(31)
    }

    @Test
    fun `a monthly repeat that already names a day is left alone`() = runTest {
        val first = LocalDate.of(2026, 1, 31).atTime(9, 0).atZone(clock.zone()).toInstant()

        val created = create(
            title = "Pay the rent",
            dueAt = first,
            recurrence = RecurrenceRule.Monthly(dayOfMonth = 1),
        ).getOrNull()!!

        assertThat((created.recurrence as RecurrenceRule.Monthly).dayOfMonth).isEqualTo(1)
    }
}

// ---------------------------------------------------------------------- shared helpers

/** A task with only the fields a test cares about spelled out. */
internal fun task(
    id: Long = 0L,
    title: String,
    description: String? = null,
    notes: String? = null,
    dueAt: Instant? = null,
    dueAllDay: Boolean = false,
    estimatedMinutes: Int? = null,
    priority: TaskPriority = TaskPriority.NORMAL,
    isDone: Boolean = false,
    completedAt: Instant? = null,
    origin: RecordOrigin = RecordOrigin.MANUAL,
    projectId: Long? = null,
    clientId: Long? = null,
    recurrence: RecurrenceRule? = null,
    seriesUuid: String? = null,
    archivedAt: Instant? = null,
    createdAt: Instant = FixedMiraClock.dhaka(2026, 3, 1, 9),
): Task = Task(
    id = id,
    title = title,
    description = description,
    notes = notes,
    dueAt = dueAt,
    dueAllDay = dueAllDay,
    estimatedMinutes = estimatedMinutes,
    priority = priority,
    isDone = isDone,
    completedAt = completedAt,
    origin = origin,
    projectId = projectId,
    clientId = clientId,
    recurrence = recurrence,
    seriesUuid = seriesUuid,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = createdAt,
)

internal fun AppResult<*>.errorCode(): MiraErrorCode? = (this as? AppResult.Failure)?.error?.code

/**
 * Storage for the task verbs, behaving the way the port says storage behaves.
 *
 * Three of those behaviours are the point of it rather than convenience: [update] does not read
 * tags off the task it is given, [byId] answers for a row in any state so restore can find one,
 * and [completeWithNextOccurrence] copies a task's steps across unchecked and leaves that
 * occurrence's own notes behind.
 */
internal class FakeTaskRepository(private val clock: MiraClock) : TaskRepository {

    private val rows = mutableListOf<Task>()
    private var nextId = 1L

    /** Makes the next bulk write fail, so the partial-outcome paths can be exercised. */
    var failBulkWith: MiraErrorCode? = null

    /** How often a task's tags were rewritten, to prove an edit does not rewrite them for nothing. */
    var tagWrites: Int = 0

    val completions = mutableListOf<Completion>()

    data class Completion(val id: Long, val nextDueAt: Instant)

    fun seed(vararg tasks: Task): List<Task> = tasks.map { task ->
        val saved = if (task.id == 0L) {
            task.copy(id = nextId++)
        } else {
            task.also { nextId = maxOf(nextId, it.id + 1) }
        }
        rows += saved
        saved
    }

    fun snapshot(): List<Task> = rows.toList()

    fun row(id: Long): Task? = rows.firstOrNull { it.id == id }

    fun taggedWith(id: Long): List<Long> = row(id)?.tags?.map { it.id } ?: emptyList()

    /** Puts steps on the stored row, the way a repository hands back a whole task. */
    fun attachSteps(id: Long, steps: List<Subtask>) {
        row(id)?.let { replace(it.copy(subtasks = steps)) }
    }

    override fun observe(filter: TaskFilter): Flow<List<Task>> = flowOf(live())

    override fun observe(query: TaskQuery): Flow<List<Task>> = flowOf(live())

    override fun observeCounts(): Flow<TaskCounts> = flowOf(
        TaskCounts(open = live().count { !it.isDone }, dueToday = 0, overdue = 0, completedToday = 0),
    )

    override suspend fun byId(id: Long): AppResult<Task> =
        row(id)?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "No task with id $id")

    override suspend fun list(query: TaskQuery): AppResult<List<Task>> = AppResult.Success(live())

    override suspend fun count(query: TaskQuery): AppResult<Int> = AppResult.Success(live().size)

    override suspend fun search(query: String?, filter: TaskFilter): AppResult<List<Task>> {
        val needle = query?.lowercase()
        return AppResult.Success(
            live()
                .filter { needle == null || it.title.lowercase().contains(needle) }
                .filter { filter != TaskFilter.OPEN || !it.isDone },
        )
    }

    override suspend fun groupedByProject(query: TaskQuery): AppResult<List<TaskGroup>> =
        AppResult.Success(live().groupBy { it.projectId }.map { (id, tasks) -> TaskGroup(id, null, tasks) })

    override suspend fun groupedByClient(query: TaskQuery): AppResult<List<TaskGroup>> =
        AppResult.Success(live().groupBy { it.clientId }.map { (id, tasks) -> TaskGroup(id, null, tasks) })

    override suspend fun create(task: Task): AppResult<Task> {
        val saved = task.copy(id = nextId++)
        rows += saved
        return AppResult.Success(saved)
    }

    override suspend fun update(task: Task): AppResult<Task> {
        val existing = row(task.id) ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id ${task.id}")
        val saved = task.copy(
            tags = existing.tags,
            subtasks = existing.subtasks,
            attachmentIds = existing.attachmentIds,
            updatedAt = clock.now(),
        )
        replace(saved)
        return AppResult.Success(saved)
    }

    override suspend fun setDone(id: Long, done: Boolean): AppResult<Task> {
        val existing = liveRow(id) ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        val saved = existing.copy(
            isDone = done,
            completedAt = if (done) clock.now() else null,
            updatedAt = clock.now(),
        )
        replace(saved)
        return AppResult.Success(saved)
    }

    override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean): AppResult<Int> =
        bulk(ids) { task ->
            task.copy(isDone = done, completedAt = if (done) clock.now() else null, updatedAt = clock.now())
        }

    override suspend fun snooze(id: Long, until: Instant): AppResult<Task> {
        val existing = liveRow(id) ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        val saved = existing.copy(dueAt = until, dueAllDay = false, updatedAt = clock.now())
        replace(saved)
        return AppResult.Success(saved)
    }

    override suspend fun archive(id: Long): AppResult<Task> = stamp(id) {
        it.copy(archivedAt = it.archivedAt ?: clock.now(), updatedAt = clock.now())
    }

    override suspend fun unarchive(id: Long): AppResult<Task> = stamp(id) {
        it.copy(archivedAt = null, updatedAt = clock.now())
    }

    override suspend fun archiveAll(ids: Collection<Long>): AppResult<Int> =
        bulk(ids) { it.copy(archivedAt = it.archivedAt ?: clock.now(), updatedAt = clock.now()) }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val existing = liveRow(id) ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        replace(existing.copy(deletedAt = clock.now(), updatedAt = clock.now()))
        return AppResult.Success(Unit)
    }

    override suspend fun deleteAll(ids: Collection<Long>): AppResult<Int> =
        bulk(ids) { it.copy(deletedAt = clock.now(), updatedAt = clock.now()) }

    override suspend fun restore(id: Long): AppResult<Task> {
        val existing = row(id)?.takeIf { it.deletedAt != null }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No deleted task with id $id")
        val saved = existing.copy(deletedAt = null, updatedAt = clock.now())
        replace(saved)
        return AppResult.Success(saved)
    }

    override suspend fun completeWithNextOccurrence(id: Long, nextDueAt: Instant): AppResult<TaskOccurrence> {
        val existing = liveRow(id) ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        completions += Completion(id, nextDueAt)
        val completed = existing.copy(isDone = true, completedAt = clock.now(), updatedAt = clock.now())
        replace(completed)
        val next = existing.copy(
            id = nextId++,
            notes = null,
            dueAt = nextDueAt,
            isDone = false,
            completedAt = null,
            attachmentIds = emptyList(),
            subtasks = existing.subtasks.map { it.copy(isDone = false, completedAt = null) },
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        rows += next
        return AppResult.Success(TaskOccurrence(completed = completed, next = next))
    }

    override suspend fun setTags(taskId: Long, tagIds: Collection<Long>): AppResult<Task> {
        tagWrites++
        return stamp(taskId) { task ->
            task.copy(tags = tagIds.distinct().map { Tag(id = it, name = "tag-$it") })
        }
    }

    override suspend fun addTag(taskId: Long, tagId: Long): AppResult<Task> = stamp(taskId) { task ->
        if (task.tags.any { it.id == tagId }) task else task.copy(tags = task.tags + Tag(tagId, "tag-$tagId"))
    }

    override suspend fun removeTag(taskId: Long, tagId: Long): AppResult<Task> = stamp(taskId) { task ->
        task.copy(tags = task.tags.filterNot { it.id == tagId })
    }

    override suspend fun attachFile(taskId: Long, fileId: Long): AppResult<Task> = stamp(taskId) { task ->
        if (fileId in task.attachmentIds) task else task.copy(attachmentIds = task.attachmentIds + fileId)
    }

    override suspend fun detachFile(taskId: Long, fileId: Long): AppResult<Task> = stamp(taskId) { task ->
        task.copy(attachmentIds = task.attachmentIds - fileId)
    }

    private fun live(): List<Task> = rows.filter { it.deletedAt == null }

    private fun liveRow(id: Long): Task? = row(id)?.takeIf { it.deletedAt == null }

    private fun replace(task: Task) {
        val at = rows.indexOfFirst { it.id == task.id }
        if (at >= 0) rows[at] = task
    }

    private fun stamp(id: Long, change: (Task) -> Task): AppResult<Task> {
        val existing = liveRow(id) ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        val saved = change(existing)
        replace(saved)
        return AppResult.Success(saved)
    }

    private fun bulk(ids: Collection<Long>, change: (Task) -> Task): AppResult<Int> {
        failBulkWith?.let { code ->
            failBulkWith = null
            return failure(code, "Injected by FakeTaskRepository")
        }
        var changed = 0
        ids.forEach { id ->
            liveRow(id)?.let { existing ->
                replace(change(existing))
                changed++
            }
        }
        return AppResult.Success(changed)
    }
}

/** The steps inside a task, appended in order the way the real repository appends them. */
internal class FakeSubtaskRepository(private val clock: MiraClock) : SubtaskRepository {

    private val rows = mutableListOf<Subtask>()
    private var nextId = 1L

    /** Set to make the next [add] fail, so a half-written task can be exercised. */
    var failWith: MiraErrorCode? = null

    fun forTask(taskId: Long): List<Subtask> = rows.filter { it.taskId == taskId }.sortedBy { it.position }

    override fun observeForTask(taskId: Long): Flow<List<Subtask>> = flowOf(forTask(taskId))

    override suspend fun byId(id: Long): AppResult<Subtask> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")

    override suspend fun add(taskId: Long, title: String): AppResult<Subtask> {
        failWith?.let { code ->
            failWith = null
            return failure(code, "Injected by FakeSubtaskRepository")
        }
        val clean = title.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Subtask title was blank")
        val step = Subtask(
            id = nextId++,
            taskId = taskId,
            title = clean,
            position = forTask(taskId).size,
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        rows += step
        return AppResult.Success(step)
    }

    override suspend fun rename(id: Long, title: String): AppResult<Subtask> {
        val clean = title.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Subtask title was blank")
        return change(id) { it.copy(title = clean, updatedAt = clock.now()) }
    }

    override suspend fun setDone(id: Long, done: Boolean): AppResult<Subtask> = change(id) {
        it.copy(isDone = done, completedAt = if (done) clock.now() else null, updatedAt = clock.now())
    }

    override suspend fun reorder(taskId: Long, orderedIds: List<Long>): AppResult<Unit> {
        val mine = forTask(taskId).map { it.id }.toSet()
        if (mine != orderedIds.toSet()) {
            return failure(MiraErrorCode.VALIDATION, "Reorder did not name every step of task $taskId")
        }
        orderedIds.forEachIndexed { position, id -> change(id) { it.copy(position = position) } }
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        if (rows.none { it.id == id }) return failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")
        rows.removeAll { it.id == id }
        return AppResult.Success(Unit)
    }

    private fun change(id: Long, edit: (Subtask) -> Subtask): AppResult<Subtask> {
        val at = rows.indexOfFirst { it.id == id }
        if (at < 0) return failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")
        val saved = edit(rows[at])
        rows[at] = saved
        return AppResult.Success(saved)
    }
}

/** Only what a snooze needs: the scheduled reminders, and what happens when one is moved. */
internal class FakeReminderRepository : ReminderRepository {

    private val rows = mutableListOf<Reminder>()
    private var nextId = 1L

    fun seed(vararg reminders: Reminder): List<Reminder> = reminders.map { reminder ->
        val saved = reminder.copy(id = nextId++)
        rows += saved
        saved
    }

    fun scheduledRows(): List<Reminder> = rows.filter { it.state == ReminderState.SCHEDULED }

    override fun observeUpcoming(): Flow<List<Reminder>> = flowOf(scheduledRows().sortedBy { it.armedAt })

    override fun observeByKind(kind: ReminderKind): Flow<List<Reminder>> =
        flowOf(scheduledRows().filter { it.kind == kind }.sortedBy { it.armedAt })

    override suspend fun byId(id: Long): AppResult<Reminder> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No reminder with id $id")

    override suspend fun update(reminder: Reminder): AppResult<Reminder> {
        val at = rows.indexOfFirst { it.id == reminder.id }
        if (at < 0) return failure(MiraErrorCode.NOT_FOUND, "No reminder with id ${reminder.id}")
        rows[at] = reminder
        return AppResult.Success(reminder)
    }

    override suspend fun create(reminder: Reminder): AppResult<Reminder> {
        val saved = reminder.copy(id = nextId++, state = ReminderState.SCHEDULED)
        rows += saved
        return AppResult.Success(saved)
    }

    override suspend fun markFired(id: Long): AppResult<Unit> = setState(id, ReminderState.FIRED)

    override suspend fun cancel(id: Long): AppResult<Unit> = setState(id, ReminderState.CANCELLED)

    override suspend fun nextScheduled(): AppResult<Reminder?> =
        AppResult.Success(scheduledRows().minByOrNull { it.triggerAt })

    override suspend fun rescheduleAll(): AppResult<Int> = AppResult.Success(scheduledRows().size)

    private fun setState(id: Long, state: ReminderState): AppResult<Unit> {
        val at = rows.indexOfFirst { it.id == id }
        if (at < 0) return failure(MiraErrorCode.NOT_FOUND, "No reminder with id $id")
        rows[at] = rows[at].copy(state = state)
        return AppResult.Success(Unit)
    }
    override suspend fun listOfKinds(
        kinds: Collection<ReminderKind>,
    ): AppResult<List<Reminder>> = AppResult.Success(rows.filter { it.kind in kinds })

}

/** Records what reached the OS alarm, so a moved reminder can be proved to have been re-armed. */
internal class FakeReminderScheduler : ReminderScheduler {

    val armed = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()

    override fun canScheduleExact(): Boolean = true

    override suspend fun schedule(reminder: Reminder): AppResult<Unit> {
        armed += reminder.id
        return AppResult.Success(Unit)
    }

    override suspend fun cancel(reminderId: Long): AppResult<Unit> {
        cancelled += reminderId
        return AppResult.Success(Unit)
    }
}
