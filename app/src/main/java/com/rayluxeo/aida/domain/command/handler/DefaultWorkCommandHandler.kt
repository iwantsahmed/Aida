package com.rayluxeo.aida.domain.command.handler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.command.ReminderTarget
import com.rayluxeo.aida.domain.command.TaskTarget
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.SavedPlaceRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.repository.TimerRepository
import com.rayluxeo.aida.domain.scheduler.PlaceWatchResult
import com.rayluxeo.aida.domain.usecase.CancelReminderUseCase
import com.rayluxeo.aida.domain.usecase.CompleteTaskUseCase
import com.rayluxeo.aida.domain.usecase.DeleteTaskUseCase
import com.rayluxeo.aida.domain.usecase.QueryRemindersUseCase
import com.rayluxeo.aida.domain.usecase.ResolveReminderUseCase
import com.rayluxeo.aida.domain.usecase.SnoozeReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreatePlaceReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateTaskUseCase
import com.rayluxeo.aida.domain.usecase.GetDailyBriefUseCase
import com.rayluxeo.aida.domain.usecase.ResolveTaskUseCase
import com.rayluxeo.aida.domain.usecase.SearchTasksUseCase
import com.rayluxeo.aida.domain.usecase.SetAlarmUseCase
import javax.inject.Inject

/**
 * Everything Abu asks Mira to keep track of for him: what he has to do, and when he has to be
 * interrupted about it.
 *
 * Two rules shape every method here.
 *
 * A task Mira cannot pin down is never guessed at. [ResolveTaskUseCase] reports ambiguity, and
 * this handler passes it straight through as [MiraResponse.AmbiguousTask] — a *successful*
 * outcome, because asking which of two tasks Abu meant is Mira working correctly, while
 * completing the wrong one is not something an apology afterwards can undo.
 *
 * Android withholding a permission is likewise not a fault. It arrives as
 * [MiraResponse.PermissionNeeded] rather than an error, so the screen offers the grant instead of
 * apologising for something Abu can fix in one tap.
 */
class DefaultWorkCommandHandler @Inject constructor(
    private val tasks: TaskRepository,
    private val timers: TimerRepository,
    private val savedPlaces: SavedPlaceRepository,
    private val addTask: CreateTaskUseCase,
    private val resolveTask: ResolveTaskUseCase,
    private val markTaskDone: CompleteTaskUseCase,
    private val removeTask: DeleteTaskUseCase,
    private val findTasks: SearchTasksUseCase,
    private val addReminder: CreateReminderUseCase,
    private val listReminders: QueryRemindersUseCase,
    private val resolveReminder: ResolveReminderUseCase,
    private val snoozeReminderUseCase: SnoozeReminderUseCase,
    private val cancelReminderUseCase: CancelReminderUseCase,
    private val addPlaceReminder: CreatePlaceReminderUseCase,
    private val setAlarm: SetAlarmUseCase,
    private val todayBrief: GetDailyBriefUseCase,
    private val clock: MiraClock,
) : WorkCommandHandler {

    override suspend fun createTask(command: AppCommand.CreateTask): AppResult<MiraResponse> =
        addTask(
            title = command.title,
            dueAt = command.dueAt,
            priority = command.priority,
            notes = command.notes,
            recurrence = command.recurrence,
            origin = RecordOrigin.TEXT_COMMAND,
        ).map { MiraResponse.TaskCreated(it.title, it.dueAt, it.recurrence != null) }

    /**
     * A null slot means "leave it as it is", so the stored task is the base and the command only
     * paints over the fields it actually named.
     */
    override suspend fun updateTask(command: AppCommand.UpdateTask): AppResult<MiraResponse> =
        resolveTask(command.target).flatMap { resolution ->
            when (resolution) {
                is ResolveTaskUseCase.Resolution.Exact -> {
                    val current = resolution.task
                    tasks.update(
                        current.copy(
                            title = command.title ?: current.title,
                            dueAt = command.dueAt ?: current.dueAt,
                            priority = command.priority ?: current.priority,
                        )
                    ).map { MiraResponse.TaskUpdated(it.title, it.dueAt) }
                }
                is ResolveTaskUseCase.Resolution.Ambiguous ->
                    askWhichTask(command.target, resolution)
                ResolveTaskUseCase.Resolution.None -> noSuchTask(command.target)
            }
        }

    override suspend fun completeTask(command: AppCommand.CompleteTask): AppResult<MiraResponse> =
        markTaskDone(command.target).flatMap { resolution ->
            when (resolution) {
                is ResolveTaskUseCase.Resolution.Exact ->
                    AppResult.Success(MiraResponse.TaskCompleted(resolution.task.title))
                is ResolveTaskUseCase.Resolution.Ambiguous ->
                    askWhichTask(command.target, resolution)
                ResolveTaskUseCase.Resolution.None -> noSuchTask(command.target)
            }
        }

    /**
     * Resolved then binned, the same shape as [completeTask]. The delete is reversible (the task
     * goes to the bin, restored by its id), which is why — unlike a calendar delete — it is not
     * forced through a confirmation; a misheard one costs a tap to undo, not the record.
     */
    override suspend fun deleteTask(command: AppCommand.DeleteTask): AppResult<MiraResponse> =
        resolveTask(command.target).flatMap { resolution ->
            when (resolution) {
                is ResolveTaskUseCase.Resolution.Exact ->
                    removeTask(resolution.task.id).map { MiraResponse.TaskDeleted(resolution.task.title) }
                is ResolveTaskUseCase.Resolution.Ambiguous -> askWhichTask(command.target, resolution)
                ResolveTaskUseCase.Resolution.None -> noSuchTask(command.target)
            }
        }

    /**
     * The count is every match; the titles are all of them. How many to read out is a question
     * about speech, and it belongs to the renderer rather than to the search.
     */
    override suspend fun searchTasks(command: AppCommand.SearchTasks): AppResult<MiraResponse> =
        findTasks(command.query, command.filter).map { found ->
            MiraResponse.TasksFound(found.size, found.map { it.title })
        }

    override suspend fun createReminder(command: AppCommand.CreateReminder): AppResult<MiraResponse> =
        addReminder(command.title, command.triggerAt, origin = RecordOrigin.TEXT_COMMAND)
            .map { MiraResponse.ReminderCreated(it.title, it.triggerAt) }
            .orPermissionNeeded()

    /** Read-only: the upcoming reminders, narrowed by [command.query] when Abu named one. */
    override suspend fun queryReminders(command: AppCommand.QueryReminders): AppResult<MiraResponse> =
        listReminders().map { all ->
            val needle = command.query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            val matches = if (needle == null) all else all.filter { it.title.lowercase().contains(needle) }
            MiraResponse.RemindersFound(matches.size, matches.map { it.title })
        }

    override suspend fun snoozeReminder(command: AppCommand.SnoozeReminder): AppResult<MiraResponse> =
        resolveReminder(command.target).flatMap { resolution ->
            when (resolution) {
                is ResolveReminderUseCase.Resolution.Exact ->
                    snoozeReminderUseCase(resolution.reminder.id, command.by)
                        .map { MiraResponse.ReminderSnoozed(it.title, it.snoozedUntil ?: it.triggerAt) }
                is ResolveReminderUseCase.Resolution.Ambiguous -> askWhichReminder(command.target, resolution)
                ResolveReminderUseCase.Resolution.None -> noSuchReminder(command.target)
            }
        }

    override suspend fun cancelReminder(command: AppCommand.CancelReminder): AppResult<MiraResponse> =
        resolveReminder(command.target).flatMap { resolution ->
            when (resolution) {
                is ResolveReminderUseCase.Resolution.Exact ->
                    cancelReminderUseCase(resolution.reminder.id)
                        .map { MiraResponse.ReminderCancelled(resolution.reminder.title) }
                is ResolveReminderUseCase.Resolution.Ambiguous -> askWhichReminder(command.target, resolution)
                ResolveReminderUseCase.Resolution.None -> noSuchReminder(command.target)
            }
        }

    /**
     * Resolved against the places Abu has saved, never against a coordinate in the sentence: Mira
     * has no geocoder and will not acquire one.
     *
     * A name she does not know is a *successful* answer listing the ones she does, on the same
     * reasoning as [askWhichTask]. Nothing is written, and "I have the office and the bank" is
     * something Abu can act on in one more sentence, where a blank refusal is not.
     */
    override suspend fun createPlaceReminder(
        command: AppCommand.CreatePlaceReminder,
    ): AppResult<MiraResponse> =
        savedPlaces.byLabelKey(SavedPlace.keyOf(command.placeLabel)).flatMap { known ->
            if (known == null) {
                askWhichPlace(command.placeLabel)
            } else {
                addPlaceReminder(
                    title = command.title,
                    place = known,
                    trigger = command.trigger,
                    origin = RecordOrigin.TEXT_COMMAND,
                ).map { watched ->
                    // The place and the trigger are read back off the stored copy rather than off
                    // the sentence, so what Mira says is what is now waiting on the reminder.
                    MiraResponse.PlaceReminderCreated(
                        title = command.title,
                        placeLabel = watched.place.label,
                        trigger = watched.place.trigger,
                        isWatching = watched.outcome is PlaceWatchResult.Watching,
                    )
                }
            }
        }

    /**
     * An alarm Abu spoke is the same alarm as one he set on the Alarms screen — one concept, one
     * store, one scheduler. It was briefly two, and the spoken half was a reminder that never
     * appeared in his alarm list and could not repeat.
     *
     * The moment the parser produced is read for its time of day against Abu's zone: an alarm
     * clock means seven o'clock wherever he is, which is why `Alarm.at` is a `LocalTime` — see
     * that model's KDoc.
     */
    override suspend fun createAlarm(command: AppCommand.CreateAlarm): AppResult<MiraResponse> =
        setAlarm(
            label = command.label,
            at = command.triggerAt.atZone(clock.zone()).toLocalTime(),
            repeatDays = command.repeatDays,
        )
            .map { alarm ->
                // Taken from the saved alarm rather than from the command, so what Mira says and
                // what will actually ring cannot disagree. `nextTriggerAt` is null only when the
                // platform refused to hold it, and the command's own moment is then the closest
                // honest thing left to read back.
                MiraResponse.AlarmSet(
                    label = alarm.label,
                    triggerAt = alarm.nextTriggerAt ?: command.triggerAt,
                    repeatDays = alarm.repeatDays,
                )
            }
            .orPermissionNeeded()

    /**
     * The end instant comes back from storage rather than being worked out here: the countdown is
     * resolved against the clock once, where it is written down, so the row and what Mira says
     * cannot disagree by the time it took to get from one to the other.
     */
    override suspend fun createTimer(command: AppCommand.CreateTimer): AppResult<MiraResponse> =
        timers.start(command.label, command.duration)
            .map { MiraResponse.TimerStarted(it.label, it.duration, it.endsAt) }

    override suspend fun dailyBrief(): AppResult<MiraResponse> =
        todayBrief().map { MiraResponse.BriefReady(it) }

    private fun askWhichTask(
        target: TaskTarget,
        resolution: ResolveTaskUseCase.Resolution.Ambiguous,
    ): AppResult<MiraResponse> = AppResult.Success(
        MiraResponse.AmbiguousTask(
            title = target.spokenReference(),
            candidates = resolution.candidates.map { it.title },
        )
    )

    /**
     * Named as [askWhichTask] is, and a success for the same reason: Mira has understood the
     * sentence perfectly and is missing one fact only Abu can supply.
     *
     * The list is every place she holds. Which of them are worth reading aloud is a question
     * about speech and belongs to the renderer.
     */
    private suspend fun askWhichPlace(spoken: String): AppResult<MiraResponse> =
        savedPlaces.all().map { known -> MiraResponse.PlaceUnknown(spoken, known.map { it.label }) }

    private fun noSuchTask(target: TaskTarget): AppResult<MiraResponse> =
        failure(MiraErrorCode.NOT_FOUND, "No open task matches ${target.spokenReference()}")

    private fun askWhichReminder(
        target: ReminderTarget,
        resolution: ResolveReminderUseCase.Resolution.Ambiguous,
    ): AppResult<MiraResponse> = AppResult.Success(
        MiraResponse.AmbiguousReminder(
            query = target.spokenReference(),
            candidates = resolution.candidates.map { it.title },
        )
    )

    private fun noSuchReminder(target: ReminderTarget): AppResult<MiraResponse> =
        failure(MiraErrorCode.NOT_FOUND, "No upcoming reminder matches ${target.spokenReference()}")

    private fun ReminderTarget.spokenReference(): String = when (this) {
        is ReminderTarget.ByTitle -> title
    }

    /**
     * An id is not something Abu ever said out loud, so there is nothing of it worth reading back
     * to him. Only a spoken reference can appear in the question.
     */
    private fun TaskTarget.spokenReference(): String = when (this) {
        is TaskTarget.ByTitle -> title
        is TaskTarget.ById -> ""
    }

    /**
     * The scheduler has already fallen back to an inexact window by the time it reports a refusal,
     * so reaching here means nothing at all could be armed. That is a permission Abu can grant,
     * not a failure he should be asked to retry.
     */
    private fun AppResult<MiraResponse>.orPermissionNeeded(): AppResult<MiraResponse> =
        if (this is AppResult.Failure && error.code == MiraErrorCode.PERMISSION_REQUIRED) {
            AppResult.Success(MiraResponse.PermissionNeeded(MiraCapability.EXACT_ALARMS))
        } else {
            this
        }
}
