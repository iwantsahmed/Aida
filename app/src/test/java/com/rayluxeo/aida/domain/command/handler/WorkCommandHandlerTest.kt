package com.rayluxeo.aida.domain.command.handler

import com.rayluxeo.aida.domain.usecase.ReopenTaskUseCase
import com.rayluxeo.aida.domain.usecase.CompleteTaskByIdUseCase
import com.rayluxeo.aida.domain.task.DefaultRecurrenceCalculator
import com.rayluxeo.aida.domain.repository.TaskOccurrence
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.model.TaskQuery
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.command.TaskTarget
import com.rayluxeo.aida.domain.model.Alarm
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TimerRecord
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.repository.AlarmRepository
import com.rayluxeo.aida.domain.repository.BriefRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.repository.TimerRepository
import com.rayluxeo.aida.domain.scheduler.AlarmClockScheduler
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.domain.scheduler.PlaceWatchResult
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import com.rayluxeo.aida.domain.usecase.CompleteTaskUseCase
import com.rayluxeo.aida.domain.usecase.CreatePlaceReminderUseCase
import com.rayluxeo.aida.domain.usecase.CancelReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import com.rayluxeo.aida.domain.usecase.DeleteTaskUseCase
import com.rayluxeo.aida.domain.usecase.QueryRemindersUseCase
import com.rayluxeo.aida.domain.usecase.ResolveReminderUseCase
import com.rayluxeo.aida.domain.usecase.SnoozeReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateTaskUseCase
import com.rayluxeo.aida.domain.usecase.GetDailyBriefUseCase
import com.rayluxeo.aida.domain.usecase.ResolveTaskUseCase
import com.rayluxeo.aida.domain.usecase.SearchTasksUseCase
import com.rayluxeo.aida.domain.usecase.FakePlaceWatchScheduler
import com.rayluxeo.aida.domain.usecase.FakeReminderPlaceRepository
import com.rayluxeo.aida.domain.usecase.FakeSavedPlaceRepository
import com.rayluxeo.aida.domain.usecase.SetAlarmUseCase
import com.rayluxeo.aida.domain.usecase.SetTaskDoneUseCase
import com.rayluxeo.aida.domain.usecase.WatchPlaceReminderUseCase
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The work handler carries out everything Abu asks Mira to keep track of, so the things worth
 * asserting here are the ones that would hurt him if they were wrong: a task completed because
 * Mira guessed between two of them, an alarm that reports success without being armed, a timer
 * whose end instant disagrees with the row it was written into.
 *
 * Nothing is injected — Hilt's local-test transform does not run under AGP 9 (google/dagger#5261)
 * — and the clock never moves, so "tomorrow at ten" is the same instant on every run.
 */
class WorkCommandHandlerTest {

    private val clock = FixedMiraClock()
    private val tomorrowAtTen: Instant = FixedMiraClock.dhaka(2026, 3, 11, 10)

    private val tasks = FakeTaskRepository(clock)
    private val reminders = FakeReminderRepository()
    private val alarms = FakeAlarmRepository()
    private val alarmScheduler = FakeAlarmClockScheduler()
    private val timers = FakeTimerRepository(clock)
    private val briefs = FakeBriefRepository()
    private val scheduler = FakeReminderScheduler()
    private val subtasks = com.rayluxeo.aida.ui.tasks.FakeSubtaskRepository()
    private val savedPlaces = FakeSavedPlaceRepository()
    private val reminderPlaces = FakeReminderPlaceRepository()
    private val placeScheduler = FakePlaceWatchScheduler()

    private val handler = DefaultWorkCommandHandler(
        tasks = tasks,
        timers = timers,
        savedPlaces = savedPlaces,
        addTask = CreateTaskUseCase(tasks, subtasks, clock),
        resolveTask = ResolveTaskUseCase(tasks),
        markTaskDone = CompleteTaskUseCase(
            resolve = ResolveTaskUseCase(tasks),
            complete = CompleteTaskByIdUseCase(tasks, DefaultRecurrenceCalculator(), clock),
        ),
        removeTask = DeleteTaskUseCase(tasks),
        findTasks = SearchTasksUseCase(tasks),
        addReminder = CreateReminderUseCase(reminders, scheduler, clock),
        listReminders = QueryRemindersUseCase(reminders),
        resolveReminder = ResolveReminderUseCase(reminders),
        snoozeReminderUseCase = SnoozeReminderUseCase(reminders, scheduler, clock),
        cancelReminderUseCase = CancelReminderUseCase(reminders, scheduler),
        addPlaceReminder = CreatePlaceReminderUseCase(
            reminders = reminders,
            createReminder = CreateReminderUseCase(reminders, scheduler, clock),
            watchPlace = WatchPlaceReminderUseCase(reminderPlaces, placeScheduler, clock),
            clock = clock,
        ),
        setAlarm = SetAlarmUseCase(alarms, alarmScheduler, clock),
        todayBrief = GetDailyBriefUseCase(briefs),
        clock = clock,
    )

    // ------------------------------------------------------------------ tasks

    @Test
    fun `creating a task stores it and reads back what was stored`() = runTest {
        val response = succeeded(
            handler.createTask(AppCommand.CreateTask(title = "Call Ahmed", dueAt = tomorrowAtTen))
        )

        assertThat(response).isEqualTo(MiraResponse.TaskCreated("Call Ahmed", tomorrowAtTen))
        assertThat(tasks.rows.value.single().title).isEqualTo("Call Ahmed")
        assertThat(tasks.rows.value.single().dueAt).isEqualTo(tomorrowAtTen)
    }

    @Test
    fun `a task created from a command is marked as coming from a command`() = runTest {
        succeeded(handler.createTask(AppCommand.CreateTask(title = "Send the invoice")))

        assertThat(tasks.rows.value.single().origin).isEqualTo(RecordOrigin.TEXT_COMMAND)
    }

    @Test
    fun `a blank task title never reaches storage`() = runTest {
        assertThat(failedWith(handler.createTask(AppCommand.CreateTask(title = "   "))))
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(tasks.rows.value).isEmpty()
    }

    @Test
    fun `a storage failure creating a task stays a failure`() = runTest {
        tasks.failWith = MiraErrorCode.STORAGE

        assertThat(failedWith(handler.createTask(AppCommand.CreateTask(title = "Call Ahmed"))))
            .isEqualTo(MiraErrorCode.STORAGE)
    }

    @Test
    fun `updating a task changes only the fields the command named`() = runTest {
        val existing = tasks.seed(title = "Call Ahmed", dueAt = null, priority = TaskPriority.NORMAL)

        val response = succeeded(
            handler.updateTask(
                AppCommand.UpdateTask(
                    target = TaskTarget.ById(existing.id),
                    dueAt = tomorrowAtTen,
                )
            )
        )

        assertThat(response).isEqualTo(MiraResponse.TaskUpdated("Call Ahmed", tomorrowAtTen))
        val stored = tasks.rows.value.single()
        assertThat(stored.title).isEqualTo("Call Ahmed")
        assertThat(stored.priority).isEqualTo(TaskPriority.NORMAL)
        assertThat(stored.dueAt).isEqualTo(tomorrowAtTen)
    }

    @Test
    fun `updating a task can rename it and raise its priority at once`() = runTest {
        val existing = tasks.seed(title = "Call Ahmed", dueAt = tomorrowAtTen)

        succeeded(
            handler.updateTask(
                AppCommand.UpdateTask(
                    target = TaskTarget.ByTitle("Call Ahmed"),
                    title = "Call Ahmed about the invoice",
                    priority = TaskPriority.HIGH,
                )
            )
        )

        val stored = tasks.rows.value.single { it.id == existing.id }
        assertThat(stored.title).isEqualTo("Call Ahmed about the invoice")
        assertThat(stored.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(stored.dueAt).isEqualTo(tomorrowAtTen)
    }

    @Test
    fun `an ambiguous update asks which task rather than picking one`() = runTest {
        tasks.seed(title = "Call Ahmed about the invoice")
        tasks.seed(title = "Call Ahmed back")

        val response = succeeded(
            handler.updateTask(
                AppCommand.UpdateTask(target = TaskTarget.ByTitle("Call Ahmed"), priority = TaskPriority.HIGH)
            )
        )

        assertThat(response).isInstanceOf(MiraResponse.AmbiguousTask::class.java)
        val asked = response as MiraResponse.AmbiguousTask
        assertThat(asked.title).isEqualTo("Call Ahmed")
        assertThat(asked.candidates)
            .containsExactly("Call Ahmed about the invoice", "Call Ahmed back")
        assertThat(tasks.rows.value.map { it.priority }).containsExactly(
            TaskPriority.NORMAL,
            TaskPriority.NORMAL,
        )
    }

    @Test
    fun `an exact title wins over the longer titles that also contain it`() = runTest {
        tasks.seed(title = "Call Ahmed")
        tasks.seed(title = "Call Ahmed back")

        val response = succeeded(
            handler.updateTask(
                AppCommand.UpdateTask(target = TaskTarget.ByTitle("Call Ahmed"), dueAt = tomorrowAtTen)
            )
        )

        assertThat(response).isEqualTo(MiraResponse.TaskUpdated("Call Ahmed", tomorrowAtTen))
    }

    @Test
    fun `updating a task nothing matches is a not found failure`() = runTest {
        assertThat(
            failedWith(
                handler.updateTask(
                    AppCommand.UpdateTask(target = TaskTarget.ByTitle("Call Ahmed"), priority = TaskPriority.HIGH)
                )
            )
        ).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `completing a task reads back the stored title rather than what Abu said`() = runTest {
        tasks.seed(title = "Call Ahmed about the invoice")

        val response = succeeded(
            handler.completeTask(AppCommand.CompleteTask(TaskTarget.ByTitle("invoice")))
        )

        assertThat(response).isEqualTo(MiraResponse.TaskCompleted("Call Ahmed about the invoice"))
        assertThat(tasks.rows.value.single().isDone).isTrue()
        assertThat(tasks.rows.value.single().completedAt).isEqualTo(clock.now())
    }

    @Test
    fun `an ambiguous completion completes nothing`() = runTest {
        tasks.seed(title = "Call Ahmed about the invoice")
        tasks.seed(title = "Call Ahmed back")

        val response = succeeded(
            handler.completeTask(AppCommand.CompleteTask(TaskTarget.ByTitle("Call Ahmed")))
        )

        assertThat(response).isInstanceOf(MiraResponse.AmbiguousTask::class.java)
        assertThat(tasks.rows.value.none { it.isDone }).isTrue()
    }

    @Test
    fun `completing a task nothing matches is a not found failure`() = runTest {
        assertThat(failedWith(handler.completeTask(AppCommand.CompleteTask(TaskTarget.ByTitle("Call Ahmed")))))
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `an already completed task is not found by a completion, which only looks at open ones`() = runTest {
        val done = tasks.seed(title = "Call Ahmed")
        tasks.rows.value = listOf(done.copy(isDone = true, completedAt = clock.now()))

        assertThat(failedWith(handler.completeTask(AppCommand.CompleteTask(TaskTarget.ByTitle("Call Ahmed")))))
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a search reports every match and every title, and lets the renderer decide how many to say`() =
        runTest {
            tasks.seed(title = "Call Ahmed")
            tasks.seed(title = "Call Ahmed back")
            tasks.seed(title = "Call Ahmed about the invoice")
            tasks.seed(title = "Buy paper")

            val response = succeeded(handler.searchTasks(AppCommand.SearchTasks(query = "Ahmed")))

            assertThat(response).isInstanceOf(MiraResponse.TasksFound::class.java)
            val found = response as MiraResponse.TasksFound
            assertThat(found.count).isEqualTo(3)
            assertThat(found.titles)
                .containsExactly("Call Ahmed", "Call Ahmed back", "Call Ahmed about the invoice")
        }

    @Test
    fun `a search that matches nothing is an empty answer, not a failure`() = runTest {
        tasks.seed(title = "Buy paper")

        val response = succeeded(handler.searchTasks(AppCommand.SearchTasks(query = "Ahmed")))

        assertThat(response).isEqualTo(MiraResponse.TasksFound(0, emptyList()))
    }

    @Test
    fun `a search with no query returns the whole open list`() = runTest {
        tasks.seed(title = "Call Ahmed")
        tasks.seed(title = "Buy paper")

        val response = succeeded(handler.searchTasks(AppCommand.SearchTasks(query = null)))

        assertThat((response as MiraResponse.TasksFound).count).isEqualTo(2)
    }

    // ------------------------------------------------------------------ reminders

    @Test
    fun `a reminder is stored and armed with the operating system`() = runTest {
        val response = succeeded(
            handler.createReminder(AppCommand.CreateReminder("Call Ahmed", tomorrowAtTen))
        )

        assertThat(response).isEqualTo(MiraResponse.ReminderCreated("Call Ahmed", tomorrowAtTen))
        assertThat(reminders.rows.single().title).isEqualTo("Call Ahmed")
        assertThat(scheduler.armed.single().triggerAt).isEqualTo(tomorrowAtTen)
    }

    @Test
    fun `a reminder in the past is refused`() = runTest {
        val alreadyGone = clock.now().minus(Duration.ofHours(1))

        assertThat(failedWith(handler.createReminder(AppCommand.CreateReminder("Call Ahmed", alreadyGone))))
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(scheduler.armed).isEmpty()
    }

    @Test
    fun `withheld exact alarms do not stop a reminder, because a window still arrives`() = runTest {
        scheduler.exact = false

        val response = succeeded(
            handler.createReminder(AppCommand.CreateReminder("Call Ahmed", tomorrowAtTen))
        )

        assertThat(response).isEqualTo(MiraResponse.ReminderCreated("Call Ahmed", tomorrowAtTen))
        assertThat(scheduler.armed).hasSize(1)
    }

    @Test
    fun `a reminder the OS refuses outright asks for the permission instead of apologising`() = runTest {
        scheduler.refuseEverything = true

        val response = succeeded(
            handler.createReminder(AppCommand.CreateReminder("Call Ahmed", tomorrowAtTen))
        )

        assertThat(response).isEqualTo(MiraResponse.PermissionNeeded(MiraCapability.EXACT_ALARMS))
    }

    @Test
    fun `a storage failure creating a reminder is not mistaken for a missing permission`() = runTest {
        reminders.failWith = MiraErrorCode.STORAGE

        assertThat(failedWith(handler.createReminder(AppCommand.CreateReminder("Call Ahmed", tomorrowAtTen))))
            .isEqualTo(MiraErrorCode.STORAGE)
    }

    // ------------------------------------------------------- reminders that wait at a place

    @Test
    fun `a place reminder is set against the place Abu saved, not against what he just said`() =
        runTest {
            val office = savedPlaces.seed("Office", latitude = 23.7806, longitude = 90.4074)

            val response = succeeded(
                handler.createPlaceReminder(
                    AppCommand.CreatePlaceReminder("Buy printer ink", "the office", PlaceTrigger.ENTER)
                )
            )

            assertThat(response).isEqualTo(
                MiraResponse.PlaceReminderCreated(
                    title = "Buy printer ink",
                    placeLabel = "Office",
                    trigger = PlaceTrigger.ENTER,
                    isWatching = true,
                )
            )
            assertThat(reminders.rows.single().kind).isEqualTo(ReminderKind.PLACE)
            val attached = reminderPlaces.stored.single()
            assertThat(attached.latitude).isEqualTo(office.latitude)
            assertThat(attached.longitude).isEqualTo(office.longitude)
        }

    /**
     * Mira has no geocoder, so a name she does not hold is a question rather than a fault — and
     * the answer has to list what she does hold, or Abu has nothing to correct himself with.
     */
    @Test
    fun `a place Mira does not know is answered with the ones she does, and nothing is written`() =
        runTest {
            savedPlaces.seed("Office")
            savedPlaces.seed("The bank")

            val response = succeeded(
                handler.createPlaceReminder(
                    AppCommand.CreatePlaceReminder("Buy printer ink", "the studio", PlaceTrigger.ENTER)
                )
            )

            assertThat(response)
                .isEqualTo(MiraResponse.PlaceUnknown("the studio", listOf("Office", "The bank")))
            assertThat(reminders.rows).isEmpty()
            assertThat(reminderPlaces.stored).isEmpty()
        }

    @Test
    fun `a place reminder the platform will not watch is still stored, and says it is not armed`() =
        runTest {
            savedPlaces.seed("Office")
            placeScheduler.outcome = PlaceWatchResult.Refused(PlaceWatchRefusal.PERMISSION_MISSING)

            val response = succeeded(
                handler.createPlaceReminder(
                    AppCommand.CreatePlaceReminder("Buy printer ink", "Office", PlaceTrigger.ENTER)
                )
            )

            assertThat(response).isEqualTo(
                MiraResponse.PlaceReminderCreated(
                    title = "Buy printer ink",
                    placeLabel = "Office",
                    trigger = PlaceTrigger.ENTER,
                    isWatching = false,
                )
            )
            assertThat(reminders.rows.single().state).isEqualTo(ReminderState.SCHEDULED)
        }

    @Test
    fun `leaving a place is a trigger of its own, and it is read back`() = runTest {
        savedPlaces.seed("Office")

        val response = succeeded(
            handler.createPlaceReminder(
                AppCommand.CreatePlaceReminder("Call Ahmed", "Office", PlaceTrigger.EXIT)
            )
        )

        assertThat((response as MiraResponse.PlaceReminderCreated).trigger).isEqualTo(PlaceTrigger.EXIT)
        assertThat(reminderPlaces.stored.single().trigger).isEqualTo(PlaceTrigger.EXIT)
    }

    // ------------------------------------------------------------------ alarms

    /**
     * The defect this replaced: a spoken alarm used to write a Reminder, which never appeared on
     * the Alarms screen, never rang on the alarm stream and could not repeat. One concept, one
     * store.
     */
    @Test
    fun `a spoken alarm becomes an alarm rather than a reminder`() = runTest {
        val response = succeeded(handler.createAlarm(AppCommand.CreateAlarm("Wake up", tomorrowAtTen)))

        val stored = alarms.rows.single()
        assertThat(stored.label).isEqualTo("Wake up")
        assertThat(stored.isEnabled).isTrue()
        assertThat(alarmScheduler.armed.single().id).isEqualTo(stored.id)
        assertThat(reminders.rows).isEmpty()
        assertThat(scheduler.armed).isEmpty()
        assertThat(response)
            .isEqualTo(MiraResponse.AlarmSet("Wake up", tomorrowAtTen, emptySet()))
    }

    /**
     * The moment the parser produced is read for its time of day in Abu's zone. An alarm clock
     * means ten o'clock wherever he is, which is why the stored column is a `LocalTime`.
     */
    @Test
    fun `the alarm is stored as a time of day in Abu's zone`() = runTest {
        succeeded(handler.createAlarm(AppCommand.CreateAlarm("Wake up", tomorrowAtTen)))

        assertThat(alarms.rows.single().at).isEqualTo(LocalTime.of(10, 0))
    }

    @Test
    fun `a repeating alarm keeps its days and rings on the next one`() = runTest {
        val weekdays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
        // Seven this morning has already gone by: the clock stands at ten on Tuesday.
        val sevenThisMorning = FixedMiraClock.dhaka(2026, 3, 10, 7)

        val response = succeeded(
            handler.createAlarm(AppCommand.CreateAlarm("Fajr", sevenThisMorning, weekdays))
        )

        val stored = alarms.rows.single()
        assertThat(stored.repeatDays).isEqualTo(weekdays)
        assertThat(stored.nextTriggerAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 7))
        assertThat(response)
            .isEqualTo(MiraResponse.AlarmSet("Fajr", FixedMiraClock.dhaka(2026, 3, 11, 7), weekdays))
    }

    /**
     * What Mira says is taken off the saved alarm rather than off the command, so her words and
     * the morning that will actually ring cannot disagree.
     */
    @Test
    fun `a one-time alarm whose time has gone by today is read back as tomorrow`() = runTest {
        val sevenThisMorning = FixedMiraClock.dhaka(2026, 3, 10, 7)

        val response = succeeded(handler.createAlarm(AppCommand.CreateAlarm("Wake up", sevenThisMorning)))

        assertThat((response as MiraResponse.AlarmSet).triggerAt)
            .isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 7))
        assertThat(alarms.rows.single().nextTriggerAt).isEqualTo(response.triggerAt)
    }

    /** "Wake me up at 7" names no subject. An alarm need not be about anything. */
    @Test
    fun `an alarm Abu did not name is stored without one rather than refused`() = runTest {
        val response = succeeded(handler.createAlarm(AppCommand.CreateAlarm("   ", tomorrowAtTen)))

        assertThat(alarms.rows.single().label).isEmpty()
        assertThat((response as MiraResponse.AlarmSet).label).isEmpty()
    }

    @Test
    fun `an alarm Abu did name keeps its name`() = runTest {
        succeeded(handler.createAlarm(AppCommand.CreateAlarm("  fajr  ", tomorrowAtTen)))

        assertThat(alarms.rows.single().label).isEqualTo("fajr")
    }

    @Test
    fun `an alarm nothing could be armed for asks for the permission`() = runTest {
        alarmScheduler.refuseEverything = true

        val response = succeeded(handler.createAlarm(AppCommand.CreateAlarm("Wake up", tomorrowAtTen)))

        assertThat(response).isEqualTo(MiraResponse.PermissionNeeded(MiraCapability.EXACT_ALARMS))
    }

    /**
     * The alarm is still Abu's — the permission may come back and the replay will arm it then —
     * but nothing on the row may claim a morning the platform never accepted.
     */
    @Test
    fun `an alarm the platform refused keeps nothing claiming a moment`() = runTest {
        alarmScheduler.refuseEverything = true

        succeeded(handler.createAlarm(AppCommand.CreateAlarm("Wake up", tomorrowAtTen)))

        assertThat(alarms.rows.single().nextTriggerAt).isNull()
    }

    // ------------------------------------------------------------------ timers

    @Test
    fun `a timer ends a duration after now and says so`() = runTest {
        val tenMinutes = Duration.ofMinutes(10)

        val response = succeeded(handler.createTimer(AppCommand.CreateTimer("Tea", tenMinutes)))

        assertThat(response)
            .isEqualTo(MiraResponse.TimerStarted("Tea", tenMinutes, clock.now().plus(tenMinutes)))
    }

    @Test
    fun `what a timer reports is what was written down, not a second calculation`() = runTest {
        succeeded(handler.createTimer(AppCommand.CreateTimer("Tea", Duration.ofMinutes(10))))

        val stored = timers.rows.single()
        assertThat(stored.endsAt).isEqualTo(clock.now().plus(Duration.ofMinutes(10)))
        assertThat(stored.state).isEqualTo(TimerState.RUNNING)
        assertThat(stored.remainingWhenPaused).isNull()
    }

    @Test
    fun `a timer with no duration is refused`() = runTest {
        assertThat(failedWith(handler.createTimer(AppCommand.CreateTimer("Tea", Duration.ZERO))))
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(timers.rows).isEmpty()
    }

    // ------------------------------------------------------------------ the brief

    @Test
    fun `the brief is handed over whole`() = runTest {
        briefs.brief = DailyBrief(
            date = clock.today(),
            dayPart = DayPart.MORNING,
            openTaskCount = 4,
            dueTodayCount = 2,
            overdueCount = 1,
            completedTodayCount = 3,
            nextReminderTitle = "Call Ahmed",
            nextReminderAt = tomorrowAtTen,
        )

        val response = succeeded(handler.dailyBrief())

        assertThat(response).isEqualTo(MiraResponse.BriefReady(briefs.brief))
    }

    @Test
    fun `a brief that cannot be assembled is a failure, not an empty brief`() = runTest {
        briefs.failWith = MiraErrorCode.STORAGE

        assertThat(failedWith(handler.dailyBrief())).isEqualTo(MiraErrorCode.STORAGE)
    }

    // ------------------------------------------------------------------ helpers

    private fun succeeded(result: AppResult<MiraResponse>): MiraResponse {
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return (result as AppResult.Success).value
    }

    private fun failedWith(result: AppResult<MiraResponse>): MiraErrorCode {
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        return (result as AppResult.Failure).error.code
    }
}

/**
 * Behaves like the real task store in the ways the handler depends on: ids are issued on insert,
 * a search is a case-insensitive substring of the title, and the open filter hides completed work
 * — which is what makes "complete it again" a miss rather than a second completion.
 */
private class FakeTaskRepository(private val clock: MiraClock) : TaskRepository {

    val rows = MutableStateFlow<List<Task>>(emptyList())
    private var nextId = 1L

    /** Set to make the next write fail, so the failure paths are exercised rather than assumed. */
    var failWith: MiraErrorCode? = null

    fun seed(
        title: String,
        dueAt: Instant? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
    ): Task {
        val task = Task(
            id = nextId++,
            title = title,
            dueAt = dueAt,
            priority = priority,
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        rows.value = rows.value + task
        return task
    }

    override fun observe(filter: TaskFilter): Flow<List<Task>> =
        rows.map { list -> list.filter { it.matches(filter) } }

    override fun observeCounts(): Flow<TaskCounts> = rows.map { list ->
        TaskCounts(
            open = list.count { !it.isDone },
            dueToday = 0,
            overdue = 0,
            completedToday = list.count { it.isDone },
        )
    }

    override suspend fun byId(id: Long): AppResult<Task> =
        rows.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No task with id $id")

    override suspend fun search(query: String?, filter: TaskFilter): AppResult<List<Task>> {
        consumeFailure()?.let { return it }
        val needle = query?.trim()?.lowercase()
        return AppResult.Success(
            rows.value
                .filter { needle == null || it.title.lowercase().contains(needle) }
                .filter { it.matches(filter) }
        )
    }

    override suspend fun create(task: Task): AppResult<Task> {
        consumeFailure()?.let { return it }
        val saved = task.copy(id = nextId++)
        rows.value = rows.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun update(task: Task): AppResult<Task> {
        consumeFailure()?.let { return it }
        if (rows.value.none { it.id == task.id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No task with id ${task.id}")
        }
        val saved = task.copy(updatedAt = clock.now())
        rows.value = rows.value.map { if (it.id == saved.id) saved else it }
        return AppResult.Success(saved)
    }

    override suspend fun setDone(id: Long, done: Boolean): AppResult<Task> {
        consumeFailure()?.let { return it }
        val existing = rows.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        val saved = existing.copy(
            isDone = done,
            completedAt = if (done) clock.now() else null,
            updatedAt = clock.now(),
        )
        rows.value = rows.value.map { if (it.id == id) saved else it }
        return AppResult.Success(saved)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(deletedAt = clock.now()) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun restore(id: Long): AppResult<Task> {
        val existing = rows.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        val saved = existing.copy(deletedAt = null, updatedAt = clock.now())
        rows.value = rows.value.map { if (it.id == id) saved else it }
        return AppResult.Success(saved)
    }

    private fun consumeFailure(): AppResult.Failure? = failWith?.let {
        failWith = null
        failure(it, "Fake task storage refused the write")
    }

    private fun Task.matches(filter: TaskFilter): Boolean = deletedAt == null && when (filter) {
        TaskFilter.ALL -> true
        TaskFilter.OPEN -> !isDone
        TaskFilter.DONE -> isDone
        TaskFilter.DUE_TODAY, TaskFilter.OVERDUE, TaskFilter.UPCOMING -> !isDone && dueAt != null
        TaskFilter.NO_DATE -> !isDone && dueAt == null
        TaskFilter.ARCHIVED -> archivedAt != null
    }

        // Added when TaskRepository grew for the task system. These are not part of what this
        // test exercises, and a fake that answered them quietly would let a future change reach
        // for them unnoticed — so they fail loudly instead.
        override fun observe(query: TaskQuery): Flow<List<Task>> = unsupported("observe(query)")
        override suspend fun list(query: TaskQuery): AppResult<List<Task>> = unsupported("list")
        override suspend fun count(query: TaskQuery): AppResult<Int> = unsupported("count")
        override suspend fun groupedByProject(query: TaskQuery): AppResult<List<TaskGroup>> =
            unsupported("groupedByProject")
        override suspend fun groupedByClient(query: TaskQuery): AppResult<List<TaskGroup>> =
            unsupported("groupedByClient")
        override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean): AppResult<Int> =
            unsupported("setDoneAll")
        override suspend fun snooze(id: Long, until: Instant): AppResult<Task> = unsupported("snooze")
        override suspend fun archive(id: Long): AppResult<Task> = unsupported("archive")
        override suspend fun unarchive(id: Long): AppResult<Task> = unsupported("unarchive")
        override suspend fun archiveAll(ids: Collection<Long>): AppResult<Int> = unsupported("archiveAll")
        override suspend fun deleteAll(ids: Collection<Long>): AppResult<Int> = unsupported("deleteAll")
        override suspend fun completeWithNextOccurrence(
            id: Long,
            nextDueAt: Instant,
        ): AppResult<TaskOccurrence> = unsupported("completeWithNextOccurrence")
        override suspend fun setTags(taskId: Long, tagIds: Collection<Long>): AppResult<Task> =
            unsupported("setTags")
        override suspend fun addTag(taskId: Long, tagId: Long): AppResult<Task> = unsupported("addTag")
        override suspend fun removeTag(taskId: Long, tagId: Long): AppResult<Task> = unsupported("removeTag")

        override suspend fun attachFile(taskId: Long, fileId: Long): AppResult<Task> =
            unsupported("attachFile")
        override suspend fun detachFile(taskId: Long, fileId: Long): AppResult<Task> =
            unsupported("detachFile")

        private fun unsupported(name: String): Nothing =
            throw UnsupportedOperationException("This test's task fake was asked for $name")
}

private class FakeReminderRepository : ReminderRepository {

    val rows = mutableListOf<Reminder>()
    private var nextId = 1L

    var failWith: MiraErrorCode? = null

    override fun observeUpcoming(): Flow<List<Reminder>> =
        MutableStateFlow(rows.filter { it.state == ReminderState.SCHEDULED })

    override fun observeByKind(kind: ReminderKind): Flow<List<Reminder>> =
        MutableStateFlow(rows.filter { it.state == ReminderState.SCHEDULED && it.kind == kind })

    override suspend fun byId(id: Long): AppResult<Reminder> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No reminder with id $id")

    override suspend fun update(reminder: Reminder): AppResult<Reminder> {
        val index = rows.indexOfFirst { it.id == reminder.id }
        if (index < 0) return failure(MiraErrorCode.NOT_FOUND, "No reminder with id ${reminder.id}")
        rows[index] = reminder
        return AppResult.Success(reminder)
    }

    override suspend fun create(reminder: Reminder): AppResult<Reminder> {
        failWith?.let {
            failWith = null
            return failure(it, "Fake reminder storage refused the write")
        }
        val saved = reminder.copy(id = nextId++)
        rows += saved
        return AppResult.Success(saved)
    }

    override suspend fun markFired(id: Long): AppResult<Unit> = move(id, ReminderState.FIRED)

    override suspend fun cancel(id: Long): AppResult<Unit> = move(id, ReminderState.CANCELLED)

    override suspend fun nextScheduled(): AppResult<Reminder?> = AppResult.Success(
        rows.filter { it.state == ReminderState.SCHEDULED }.minByOrNull { it.triggerAt }
    )

    override suspend fun rescheduleAll(): AppResult<Int> =
        AppResult.Success(rows.count { it.state == ReminderState.SCHEDULED })

    private fun move(id: Long, to: ReminderState): AppResult<Unit> {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return failure(MiraErrorCode.NOT_FOUND, "No reminder with id $id")
        rows[index] = rows[index].copy(state = to)
        return AppResult.Success(Unit)
    }
        override suspend fun listOfKinds(
            kinds: Collection<ReminderKind>,
        ): AppResult<List<Reminder>> = AppResult.Success(rows.filter { it.kind in kinds })

}

/**
 * The alarm clock's own store, which is where a spoken alarm now lands.
 *
 * It keeps the whole row rather than only the fields the handler reads, because the assertions
 * that matter are about what the alarm looks like afterwards — its time of day, its days, and
 * whether anything still claims a moment once the platform has refused it.
 */
private class FakeAlarmRepository : AlarmRepository {

    val rows = mutableListOf<Alarm>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<Alarm>> = MutableStateFlow(rows.toList())

    override suspend fun byId(id: Long): AppResult<Alarm> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No alarm with id $id")

    override suspend fun enabledOrSnoozed(): AppResult<List<Alarm>> =
        AppResult.Success(rows.filter { it.isEnabled || it.snoozedUntil != null })

    override suspend fun create(alarm: Alarm): AppResult<Alarm> {
        val saved = alarm.copy(id = nextId++)
        rows += saved
        return AppResult.Success(saved)
    }

    override suspend fun update(alarm: Alarm): AppResult<Alarm> {
        val index = rows.indexOfFirst { it.id == alarm.id }
        if (index < 0) return failure(MiraErrorCode.NOT_FOUND, "No alarm with id ${alarm.id}")
        rows[index] = alarm
        return AppResult.Success(alarm)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        if (!rows.removeAll { it.id == id }) return failure(MiraErrorCode.NOT_FOUND, "No alarm with id $id")
        return AppResult.Success(Unit)
    }
}

/**
 * Stands in for `setAlarmClock`, with the one outcome that actually happens: exact alarms
 * withheld, so nothing at all can be armed.
 */
private class FakeAlarmClockScheduler : AlarmClockScheduler {

    val armed = mutableListOf<Alarm>()

    var refuseEverything: Boolean = false

    override suspend fun schedule(alarm: Alarm): AppResult<Unit> {
        if (refuseEverything) {
            return failure(MiraErrorCode.PERMISSION_REQUIRED, "Exact alarms are not granted")
        }
        armed += alarm
        return AppResult.Success(Unit)
    }

    override suspend fun cancel(alarmId: Long): AppResult<Unit> {
        armed.removeAll { it.id == alarmId }
        return AppResult.Success(Unit)
    }

    override suspend fun rescheduleAll(): AppResult<Unit> = AppResult.Success(Unit)
}

/** Mirrors the real countdown store: the end instant is worked out here, once, from the clock. */
private class FakeTimerRepository(private val clock: MiraClock) : TimerRepository {

    val rows = mutableListOf<TimerRecord>()
    private var nextId = 1L

    override fun observeActive(): Flow<List<TimerRecord>> = MutableStateFlow(
        rows.filter { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }
    )

    override suspend fun active(): AppResult<List<TimerRecord>> = AppResult.Success(
        rows.filter { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }
    )

    override suspend fun byId(id: Long): AppResult<TimerRecord> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No timer with id $id")

    override suspend fun start(label: String, duration: Duration): AppResult<TimerRecord> {
        if (duration.isZero || duration.isNegative) {
            return failure(MiraErrorCode.VALIDATION, "Timer duration was $duration")
        }
        val startedAt = clock.now()
        val saved = TimerRecord(
            id = nextId++,
            label = label.trim(),
            duration = duration,
            startedAt = startedAt,
            endsAt = startedAt.plus(duration),
            state = TimerState.RUNNING,
            createdAt = startedAt,
            updatedAt = startedAt,
        )
        rows += saved
        return AppResult.Success(saved)
    }

    override suspend fun pause(id: Long): AppResult<TimerRecord> =
        move(id, TimerState.PAUSED) { it.copy(remainingWhenPaused = it.remainingAt(clock.now())) }

    override suspend fun resume(id: Long): AppResult<TimerRecord> =
        move(id, TimerState.RUNNING) {
            val left = it.remainingWhenPaused ?: Duration.ZERO
            it.copy(endsAt = clock.now().plus(left), remainingWhenPaused = null)
        }

    override suspend fun finish(id: Long): AppResult<TimerRecord> = move(id, TimerState.FINISHED)

    override suspend fun cancel(id: Long): AppResult<TimerRecord> = move(id, TimerState.CANCELLED)

    private fun move(
        id: Long,
        to: TimerState,
        adjust: (TimerRecord) -> TimerRecord = { it },
    ): AppResult<TimerRecord> {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return failure(MiraErrorCode.NOT_FOUND, "No timer with id $id")
        val saved = adjust(rows[index]).copy(state = to, updatedAt = clock.now())
        rows[index] = saved
        return AppResult.Success(saved)
    }
}

private class FakeBriefRepository : BriefRepository {

    var brief: DailyBrief = DailyBrief(
        date = FixedMiraClock().today(),
        dayPart = DayPart.MORNING,
        openTaskCount = 0,
        dueTodayCount = 0,
        overdueCount = 0,
        completedTodayCount = 0,
        nextReminderTitle = null,
        nextReminderAt = null,
    )

    var failWith: MiraErrorCode? = null

    override suspend fun todayBrief(): AppResult<DailyBrief> =
        failWith?.let { failure(it, "Fake brief storage could not read") } ?: AppResult.Success(brief)
}

/**
 * Stands in for AlarmManager with the two outcomes that actually matter: exact alarms withheld,
 * which the real scheduler absorbs into a window, and a refusal to schedule anything at all.
 */
private class FakeReminderScheduler : ReminderScheduler {

    val armed = mutableListOf<Reminder>()

    var exact: Boolean = true
    var refuseEverything: Boolean = false

    override fun canScheduleExact(): Boolean = exact

    override suspend fun schedule(reminder: Reminder): AppResult<Unit> {
        if (refuseEverything) {
            return failure(MiraErrorCode.PERMISSION_REQUIRED, "Alarm scheduling refused")
        }
        if (reminder.id <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "Cannot arm an unsaved reminder")
        }
        armed += reminder
        return AppResult.Success(Unit)
    }

    override suspend fun cancel(reminderId: Long): AppResult<Unit> {
        armed.removeAll { it.id == reminderId }
        return AppResult.Success(Unit)
    }
}
