package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.command.ReminderTarget
import com.rayluxeo.aida.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.first
import com.rayluxeo.aida.domain.scheduler.AlarmClockScheduler
import com.rayluxeo.aida.domain.scheduler.TimerScheduler
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import com.rayluxeo.aida.domain.task.RecurrenceCalculator
import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Creates any of the kinds in [ReminderKind].
 *
 * One use case rather than eight, because the kinds differ in their wording, their links and the
 * buttons on their notification — not in how a reminder is validated, stored or armed. Eight
 * copies of this would be eight places for the snooze rule to drift.
 *
 * The row is written before the alarm is armed, and the row is taken back if arming fails: a
 * reminder that exists but is not armed would fire at the next reboot, hours after Mira told Abu
 * she could not set it.
 */
class CreateReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val clock: MiraClock,
) {
    /**
     * [linkedTaskId] and the four links beside it tie the reminder to the record it grew out of.
     *
     * The link lives here rather than as a column on that record, because one relationship with
     * two homes is a second source of truth, and this is the home that can actually be scheduled
     * and cancelled. Every one of them is SET NULL on the storage side, so deleting the invoice
     * leaves the reminder standing rather than silently dropping something Abu asked to be told
     * about.
     *
     * A kind that is *about* a record is refused without it: an invoice reminder with no invoice
     * has nothing to say and nothing to open. [ReminderKind.BIRTHDAY] is deliberately not in that
     * set — a birthday may be a client's or it may be Abu's brother's.
     */
    suspend operator fun invoke(
        title: String,
        triggerAt: Instant,
        kind: ReminderKind = ReminderKind.ONE_TIME,
        recurrence: RecurrenceRule? = null,
        origin: RecordOrigin = RecordOrigin.MANUAL,
        linkedTaskId: Long? = null,
        linkedInvoiceId: Long? = null,
        linkedClientId: Long? = null,
        linkedProjectId: Long? = null,
        linkedSubscriptionId: Long? = null,
    ): AppResult<Reminder> {
        val clean = title.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Reminder title was blank")

        // A place reminder is exempt: it has no moment of its own and fires when Abu arrives.
        if (kind != ReminderKind.PLACE && !triggerAt.isAfter(clock.now())) {
            return failure(MiraErrorCode.VALIDATION, "Reminder time $triggerAt is not in the future")
        }
        if (kind == ReminderKind.RECURRING && recurrence == null) {
            return failure(MiraErrorCode.VALIDATION, "A recurring reminder needs a repeat rule")
        }
        val missing = kind.missingLink(
            taskId = linkedTaskId,
            invoiceId = linkedInvoiceId,
            clientId = linkedClientId,
            projectId = linkedProjectId,
            subscriptionId = linkedSubscriptionId,
        )
        if (missing != null) {
            return failure(MiraErrorCode.VALIDATION, "A $kind reminder needs its $missing")
        }

        val draft = Reminder(
            title = clean,
            triggerAt = triggerAt,
            kind = kind,
            // The same anchoring a repeating task gets, from the same helper: a monthly reminder
            // that names no day is pinned to the day its first occurrence falls on.
            recurrence = recurrence.anchoredTo(triggerAt, clock.zone()),
            linkedTaskId = linkedTaskId,
            linkedInvoiceId = linkedInvoiceId,
            linkedClientId = linkedClientId,
            linkedProjectId = linkedProjectId,
            linkedSubscriptionId = linkedSubscriptionId,
            origin = origin,
            createdAt = clock.now(),
        )
        return when (val saved = reminders.create(draft)) {
            is AppResult.Failure -> saved
            is AppResult.Success -> armOrWithdraw(saved.value)
        }
    }

    private suspend fun armOrWithdraw(saved: Reminder): AppResult<Reminder> {
        // A place reminder is armed by the place scheduler, not by a clock.
        if (saved.waitsForPlace) return AppResult.Success(saved)
        return when (val armed = scheduler.schedule(saved)) {
            is AppResult.Success -> AppResult.Success(saved)
            is AppResult.Failure -> {
                reminders.cancel(saved.id)
                armed
            }
        }
    }
}

/**
 * Ends the reminder — and with it the series, if it repeats.
 *
 * This is the destructive verb. [CompleteReminderUseCase] finishes the occurrence in front of Abu
 * and lets a repeating reminder come back; this one means he does not want to hear about it again.
 */
class CancelReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> =
        scheduler.cancel(id).flatMap { reminders.cancel(id) }
}

/**
 * Finishes the occurrence Abu is looking at, and arms the next one if the reminder repeats.
 *
 * This is the notification's "Complete". A weekly follow-up he has just dealt with is done for
 * this week and due again next week; ending the series takes [CancelReminderUseCase].
 */
class CompleteReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val recurrences: RecurrenceCalculator,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(id: Long): AppResult<Reminder> {
        val found = reminders.byId(id)
        val reminder = when (found) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        if (reminder.state == ReminderState.CANCELLED) {
            return failure(MiraErrorCode.VALIDATION, "Reminder $id was already cancelled")
        }
        // Disarm this occurrence first, including a snooze in front of it. A failure here is not
        // worth stopping for: the reminder is about to be re-armed or finished either way, and
        // both of those replace whatever the OS is still holding under this reminder's id.
        scheduler.cancel(id)
        return advanceReminder(reminder, reminders, scheduler, recurrences, clock)
    }
}

/**
 * Pushes a reminder away for a while.
 *
 * `snoozedUntil` is set and the original trigger is left exactly where it was, so calling the
 * snooze off with [UnsnoozeReminderUseCase] puts the reminder back at the time Abu chose rather
 * than at one Mira invented — and a repeating reminder still counts its next occurrence from its
 * own moment rather than from wherever the snoozing left it.
 *
 * A fired reminder is the normal case here: the notification Abu is snoozing from only exists
 * after the reminder went off, so this puts it back to [ReminderState.SCHEDULED].
 */
class SnoozeReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(id: Long, by: Duration): AppResult<Reminder> {
        if (by.isZero || by.isNegative) {
            return failure(MiraErrorCode.VALIDATION, "Snooze span $by is not a length of time")
        }
        val found = reminders.byId(id)
        val reminder = when (found) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        if (reminder.state == ReminderState.CANCELLED) {
            return failure(MiraErrorCode.VALIDATION, "Reminder $id was cancelled and cannot be snoozed")
        }
        if (reminder.waitsForPlace) {
            return failure(MiraErrorCode.VALIDATION, "Reminder $id waits for a place, not for a time")
        }
        val snoozed = reminder.copy(
            snoozedUntil = clock.now().plus(by),
            state = ReminderState.SCHEDULED,
        )
        return reminders.update(snoozed).flatMap { stored ->
            scheduler.schedule(stored).map { stored }
        }
    }

    companion object {
        /** The two spans the notification offers, so the words and the arithmetic cannot drift. */
        val SHORT: Duration = Duration.ofMinutes(10)
        val LONG: Duration = Duration.ofHours(1)
    }
}

/**
 * Calls a snooze off and puts the reminder back on its own moment.
 *
 * If that moment has already gone by, the reminder fires at once. That is the honest reading of
 * "put it back where it was" — the alternative is Mira choosing a new time on Abu's behalf, which
 * is the thing the untouched trigger exists to avoid.
 */
class UnsnoozeReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(id: Long): AppResult<Reminder> {
        val found = reminders.byId(id)
        val reminder = when (found) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        if (!reminder.isSnoozed) return AppResult.Success(reminder)
        return restoreAfterSnooze(reminder, reminders, scheduler)
    }
}

/**
 * What happens to a reminder after its alarm lands.
 *
 * Call this from the alarm's landing point **after** the notification has been posted and from a
 * `finally`, so it runs even when posting failed. A repeating reminder that only re-arms on the
 * happy path is a reminder that stops repeating the first time anything goes wrong, and Abu has
 * no way to notice that until the week he needed it.
 *
 * Two firings look alike and mean different things:
 *
 * - the reminder's own moment — a repeating one advances to its next occurrence, a one-off ends;
 * - a snooze going off — the snooze is spent and the reminder goes back to its own moment, which
 *   is why a snooze must never advance the series.
 *
 * They are told apart by the trigger still being in the future, which is only true of the second.
 *
 * The advanced row is written **before** the next alarm is armed. A failed arming then leaves a
 * scheduled reminder that the next boot re-arms, rather than a series that has silently stopped.
 */
class ReminderFiredUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val recurrences: RecurrenceCalculator,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(id: Long): AppResult<Reminder> {
        val found = reminders.byId(id)
        val reminder = when (found) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        if (reminder.state == ReminderState.CANCELLED) {
            return failure(MiraErrorCode.VALIDATION, "Reminder $id was cancelled before it fired")
        }
        // The reminder's own moment is still ahead, so what went off was the snooze in front of it.
        if (reminder.triggerAt.isAfter(clock.now())) {
            return restoreAfterSnooze(reminder, reminders, scheduler)
        }
        return advanceReminder(reminder, reminders, scheduler, recurrences, clock)
    }
}

/**
 * Keeps Mira's alarms in step with the exact-alarm permission.
 *
 * When Abu takes the permission away the platform stops the app and cancels **every future exact
 * alarm Mira holds**, silently; when he gives it back, nothing is restored. Either direction is a
 * reason to re-arm from the database, which is the only record those reminders ever existed:
 * granted restores them exactly, revoked restores them as inexact windows a few minutes wide.
 *
 * Without this a reminder simply stops arriving, and that is the one failure in this system Abu
 * has no way to notice.
 *
 * Collected for the life of the process, so it never returns.
 */
class KeepRemindersArmedUseCase @Inject constructor(
    private val exactAlarms: ExactAlarmAccess,
    private val reminders: ReminderRepository,
    private val alarms: AlarmClockScheduler,
    private val timers: TimerScheduler,
) {
    /**
     * Collects for the life of the app, so it is launched from an application scope rather than
     * from a screen.
     *
     * All three are replayed on EITHER direction. Granted is the obvious one — the platform
     * cancelled every future exact alarm when the permission went away, and these rows are the
     * only record those reminders ever existed. Revoked matters just as much: each scheduler then
     * re-arms what it can as an inexact window, which is a few minutes late rather than never.
     *
     * Each is attempted independently. A failure replaying one must not cost Abu the other two.
     */
    suspend operator fun invoke() {
        exactAlarms.changes.collect {
            runCatching { reminders.rescheduleAll() }
            runCatching { alarms.rescheduleAll() }
            runCatching { timers.rescheduleAll() }
        }
    }
}

/**
 * Moves a reminder past the occurrence that has just been dealt with and arms whatever comes next,
 * or ends it when the series has run out.
 *
 * Shared by [ReminderFiredUseCase] and [CompleteReminderUseCase], which reach it by different
 * routes but want the same thing of the reminder afterwards. Any snooze is spent on the way
 * through: the occurrence it was pushing away is now behind Abu either way.
 *
 * The row is written **before** the next alarm is armed, so a failed arming leaves a scheduled
 * reminder that the next boot re-arms rather than a series that has silently stopped.
 */
private suspend fun advanceReminder(
    reminder: Reminder,
    reminders: ReminderRepository,
    scheduler: ReminderScheduler,
    recurrences: RecurrenceCalculator,
    clock: MiraClock,
): AppResult<Reminder> {
    val spent = reminder.copy(snoozedUntil = null)
    val next = spent.recurrence?.let { rule ->
        nextReminderOccurrence(
            rule = rule,
            from = spent.triggerAt,
            notBefore = clock.now(),
            zone = clock.zone(),
            recurrences = recurrences,
        )
    }
    if (next == null) return reminders.update(spent.copy(state = ReminderState.FIRED))
    val repeated = spent.copy(triggerAt = next, state = ReminderState.SCHEDULED)
    return reminders.update(repeated).flatMap { stored -> scheduler.schedule(stored).map { stored } }
}

/**
 * Drops a snooze and puts the reminder back on its own moment.
 *
 * Separate from [advanceReminder] because neither of its two callers may move the series. Abu
 * calling a snooze off has not dealt with the occurrence, and a snooze going off before the
 * reminder's own time has arrived has not either — in both cases the occurrence is still ahead
 * of him.
 */
private suspend fun restoreAfterSnooze(
    reminder: Reminder,
    reminders: ReminderRepository,
    scheduler: ReminderScheduler,
): AppResult<Reminder> {
    val restored = reminder.copy(snoozedUntil = null, state = ReminderState.SCHEDULED)
    return reminders.update(restored).flatMap { stored -> scheduler.schedule(stored).map { stored } }
}

/**
 * The next moment a rule lands on after [from], skipping anything already in the past.
 *
 * The time of day is carried over from [from] rather than recomputed, so "every Monday at eight"
 * stays at eight through a daylight change — the date arithmetic belongs to the shared
 * [RecurrenceCalculator] and only the hour is added back here.
 *
 * Occurrences that have already gone by are stepped over rather than fired: a daily reminder on a
 * phone that was off for a fortnight is due tomorrow, not fourteen times this morning. The step is
 * bounded because a rule whose interval the calculator cannot advance would otherwise spin for
 * ever, and a reminder that quietly stops is better than an app that hangs.
 *
 * @return null when the series has run out, which ends the reminder.
 */
private fun nextReminderOccurrence(
    rule: RecurrenceRule,
    from: Instant,
    notBefore: Instant,
    zone: ZoneId,
    recurrences: RecurrenceCalculator,
): Instant? {
    val local = from.atZone(zone)
    val timeOfDay = local.toLocalTime()
    var date = local.toLocalDate()
    var steps = 0
    while (steps++ < MAX_REMINDER_OCCURRENCE_STEPS) {
        date = recurrences.next(rule, date) ?: return null
        val candidate = ZonedDateTime.of(date, timeOfDay, zone).toInstant()
        if (candidate.isAfter(notBefore)) return candidate
    }
    return null
}

/** Ten years of a daily rule, which is far past any gap a phone being off can open. */
private const val MAX_REMINDER_OCCURRENCE_STEPS = 4000

/**
 * The link a kind cannot mean anything without, or null when it stands on its own.
 *
 * Exhaustive over [ReminderKind] on purpose: adding a kind without deciding this is a compile
 * error rather than a reminder that opens nothing when Abu taps it.
 */
private fun ReminderKind.missingLink(
    taskId: Long?,
    invoiceId: Long?,
    clientId: Long?,
    projectId: Long?,
    subscriptionId: Long?,
): String? = when (this) {
    ReminderKind.TASK -> "task".takeIf { taskId == null }
    ReminderKind.INVOICE -> "invoice".takeIf { invoiceId == null }
    ReminderKind.CLIENT_FOLLOW_UP -> "client".takeIf { clientId == null }
    ReminderKind.PROJECT_DEADLINE -> "project".takeIf { projectId == null }
    ReminderKind.SUBSCRIPTION -> "subscription".takeIf { subscriptionId == null }
    ReminderKind.ONE_TIME,
    ReminderKind.RECURRING,
    ReminderKind.BIRTHDAY,
    ReminderKind.PLACE,
    -> null
}

/**
 * Finds the upcoming reminder Abu named, the way [ResolveTaskUseCase] finds a task — so a spoken
 * "snooze the dentist reminder" acts on exactly one, and an ambiguous name asks which rather than
 * guessing. Matches against the upcoming list by title (a contained phrase), preferring an exact
 * title when several contain the words.
 */
class ResolveReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
) {

    sealed interface Resolution {
        data class Exact(val reminder: Reminder) : Resolution
        data class Ambiguous(val candidates: List<Reminder>) : Resolution
        data object None : Resolution
    }

    suspend operator fun invoke(target: ReminderTarget): AppResult<Resolution> = when (target) {
        is ReminderTarget.ByTitle -> {
            val needle = target.title.trim().lowercase()
            // The spoken phrase ("the call reminder") rarely equals the stored title ("call the
            // bank"), so match on shared words rather than demanding one contain the other whole.
            val needleWords = needle.split(WHITESPACE).filter { it.length >= MIN_MATCH_WORD }.toSet()
            val upcoming = runCatching { reminders.observeUpcoming().first() }.getOrDefault(emptyList())
            val matches = upcoming.filter { reminder ->
                val title = reminder.title.trim().lowercase()
                title.contains(needle) || needle.contains(title) || needleWords.any { title.contains(it) }
            }
            AppResult.Success(
                when {
                    matches.isEmpty() -> Resolution.None
                    matches.size == 1 -> Resolution.Exact(matches.first())
                    else -> {
                        val exact = matches.filter { it.title.trim().lowercase() == needle }
                        if (exact.size == 1) Resolution.Exact(exact.first()) else Resolution.Ambiguous(matches.take(5))
                    }
                },
            )
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val MIN_MATCH_WORD = 3
    }
}

/** The upcoming reminders, soonest first — read-only, so Mira can say what is coming without acting. */
class QueryRemindersUseCase @Inject constructor(
    private val reminders: ReminderRepository,
) {
    suspend operator fun invoke(): AppResult<List<Reminder>> {
        val upcoming = runCatching { reminders.observeUpcoming().first() }.getOrDefault(emptyList())
        return AppResult.Success(upcoming.sortedBy { it.snoozedUntil ?: it.triggerAt })
    }
}
