package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.domain.scheduler.TimerScheduler
import com.rayluxeo.aida.domain.scheduler.AlarmClockScheduler
import com.rayluxeo.aida.domain.model.TimerRecord
import com.rayluxeo.aida.domain.model.Alarm
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import com.rayluxeo.aida.domain.task.DefaultRecurrenceCalculator
import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The reminder verbs, across all eight kinds.
 *
 * What is worth asserting here is what Abu could not notice going wrong: a repeat that quietly
 * stops repeating, a snooze that advances the series, a reminder row that survives an arming
 * failure and then goes off days later, a daily reminder firing once for every day the phone was
 * off, and alarms that are never rebuilt after the exact-alarm permission moves.
 *
 * Nothing is injected — Hilt's local-test transform does not run under AGP 9 (google/dagger#5261)
 * — and the recurrence arithmetic is the real [DefaultRecurrenceCalculator] rather than a stand-in,
 * because a fake that agreed with these expectations would prove only that the test agrees with
 * itself. Every instant below is a Dhaka wall-clock reading, which is how Abu thinks about them.
 */
class ReminderUseCaseTest {

    private val clock = ReminderTestClock(FixedMiraClock.DEFAULT_NOW)
    private val scheduler = ArmedAlarms()
    private val reminders = StoredReminders(scheduler)
    private val recurrences = DefaultRecurrenceCalculator()
    private val exactAlarms = SwitchableExactAlarmAccess()

    private val create = CreateReminderUseCase(reminders, scheduler, clock)
    private val cancel = CancelReminderUseCase(reminders, scheduler)
    private val complete = CompleteReminderUseCase(reminders, scheduler, recurrences, clock)
    private val snooze = SnoozeReminderUseCase(reminders, scheduler, clock)
    private val unsnooze = UnsnoozeReminderUseCase(reminders, scheduler)
    private val fired = ReminderFiredUseCase(reminders, scheduler, recurrences, clock)
    // Re-arming now covers the alarm clock and the timers too: when the permission comes back,
    // everything exact the platform cancelled has to be put back, not only the reminders.
    private val recordingAlarms = RecordingAlarmClockScheduler()
    private val recordingTimers = RecordingTimerScheduler()
    private val keepArmed =
        KeepRemindersArmedUseCase(exactAlarms, reminders, recordingAlarms, recordingTimers)

    // --- creating the eight kinds -------------------------------------------------------------

    @Test
    fun `an invoice reminder carries its invoice and is armed`() = runTest {
        val result = create(
            title = "Chase invoice 118",
            triggerAt = dhaka(2026, 3, 12, 10),
            kind = ReminderKind.INVOICE,
            linkedInvoiceId = 118L,
        )

        val saved = result.getOrNull()
        assertThat(saved).isNotNull()
        assertThat(saved!!.kind).isEqualTo(ReminderKind.INVOICE)
        assertThat(saved.linkedInvoiceId).isEqualTo(118L)
        assertThat(scheduler.armedAt(saved.id)).isEqualTo(dhaka(2026, 3, 12, 10))
    }

    @Test
    fun `a kind that is about a record is refused without it`() = runTest {
        val aboutARecord = listOf(
            ReminderKind.INVOICE,
            ReminderKind.CLIENT_FOLLOW_UP,
            ReminderKind.PROJECT_DEADLINE,
            ReminderKind.SUBSCRIPTION,
            ReminderKind.TASK,
        )
        for (kind in aboutARecord) {
            val result = create(
                title = "Something about a $kind",
                triggerAt = dhaka(2026, 3, 12, 10),
                kind = kind,
            )
            assertThat(result).isInstanceOf(AppResult.Failure::class.java)
            assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        }
        assertThat(reminders.all()).isEmpty()
    }

    /** A birthday may be a client's or it may be Abu's brother's, so it needs no link. */
    @Test
    fun `a birthday reminder stands on its own`() = runTest {
        val result = create(
            title = "Rafi's birthday",
            triggerAt = dhaka(2026, 4, 2, 9),
            kind = ReminderKind.BIRTHDAY,
            recurrence = RecurrenceRule.Yearly(),
        )

        assertThat(result.getOrNull()?.repeats).isTrue()
    }

    @Test
    fun `a recurring reminder without a rule is refused`() = runTest {
        val result = create(
            title = "Weekly review",
            triggerAt = dhaka(2026, 3, 11, 9),
            kind = ReminderKind.RECURRING,
        )

        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    /**
     * A row that outlives a failed arming is worse than no row: nothing fires now, and then the
     * next reboot arms it and Mira says something hours after telling Abu she could not.
     */
    @Test
    fun `a reminder that could not be armed is withdrawn`() = runTest {
        scheduler.refuseWith = MiraErrorCode.PERMISSION_REQUIRED

        val result = create(title = "Call the bank", triggerAt = dhaka(2026, 3, 12, 10))

        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.PERMISSION_REQUIRED)
        assertThat(reminders.all().single().state).isEqualTo(ReminderState.CANCELLED)
    }

    /** A place reminder has no moment; the place scheduler arms it, not the clock. */
    @Test
    fun `a place reminder is not given to the time scheduler`() = runTest {
        val result = create(
            title = "Buy printer ink",
            triggerAt = clock.now(),
            kind = ReminderKind.PLACE,
        )

        assertThat(result.getOrNull()).isNotNull()
        assertThat(scheduler.armed).isEmpty()
    }

    @Test
    fun `reminders can be listed by kind`() = runTest {
        create(
            title = "Renew hosting",
            triggerAt = dhaka(2026, 3, 20, 9),
            kind = ReminderKind.SUBSCRIPTION,
            linkedSubscriptionId = 4L,
        )
        create(title = "Call the bank", triggerAt = dhaka(2026, 3, 12, 10))

        val subscriptions = reminders.observeByKind(ReminderKind.SUBSCRIPTION).first()

        assertThat(subscriptions.map { it.title }).containsExactly("Renew hosting")
    }

    // --- snooze --------------------------------------------------------------------------------

    /**
     * The trigger is left exactly where Abu set it. That is the whole reason for a second column:
     * calling the snooze off has to put the reminder back at his time rather than at one Mira
     * worked out.
     */
    @Test
    fun `snoozing moves the alarm and leaves the trigger alone`() = runTest {
        val original = dhaka(2026, 3, 10, 11)
        val saved = create(title = "Stand up", triggerAt = original).getOrNull()!!
        clock.moveTo(original)
        reminders.forceState(saved.id, ReminderState.FIRED)

        val snoozed = snooze(saved.id, SnoozeReminderUseCase.SHORT).getOrNull()!!

        assertThat(snoozed.triggerAt).isEqualTo(original)
        assertThat(snoozed.snoozedUntil).isEqualTo(clock.now().plus(SnoozeReminderUseCase.SHORT))
        assertThat(snoozed.state).isEqualTo(ReminderState.SCHEDULED)
        assertThat(scheduler.armedAt(saved.id)).isEqualTo(clock.now().plus(SnoozeReminderUseCase.SHORT))
    }

    @Test
    fun `calling a snooze off puts the reminder back where Abu set it`() = runTest {
        val original = dhaka(2026, 3, 10, 11)
        val saved = create(title = "Stand up", triggerAt = original).getOrNull()!!
        clock.moveTo(original)
        snooze(saved.id, SnoozeReminderUseCase.LONG)

        val restored = unsnooze(saved.id).getOrNull()!!

        assertThat(restored.snoozedUntil).isNull()
        assertThat(scheduler.armedAt(saved.id)).isEqualTo(original)
    }

    @Test
    fun `a snooze of no length is refused`() = runTest {
        val saved = create(title = "Stand up", triggerAt = dhaka(2026, 3, 10, 11)).getOrNull()!!

        val result = snooze(saved.id, Duration.ZERO)

        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    /**
     * A snooze going off is not the occurrence going off. Advancing here would cost Abu the
     * occurrence he asked to be reminded about ten minutes later.
     */
    @Test
    fun `a snooze firing does not move the series on`() = runTest {
        val weekly = weeklyWednesday()
        clock.moveTo(dhaka(2026, 3, 10, 12))
        snooze(weekly.id, SnoozeReminderUseCase.SHORT)
        clock.moveTo(dhaka(2026, 3, 10, 12).plus(SnoozeReminderUseCase.SHORT))

        val after = fired(weekly.id).getOrNull()!!

        assertThat(after.triggerAt).isEqualTo(dhaka(2026, 3, 11, 9))
        assertThat(after.snoozedUntil).isNull()
        assertThat(scheduler.armedAt(weekly.id)).isEqualTo(dhaka(2026, 3, 11, 9))
    }

    // --- recurrence ----------------------------------------------------------------------------

    @Test
    fun `a weekly reminder firing arms the same time next week`() = runTest {
        val weekly = weeklyWednesday()
        clock.moveTo(dhaka(2026, 3, 11, 9))

        val after = fired(weekly.id).getOrNull()!!

        assertThat(after.triggerAt).isEqualTo(dhaka(2026, 3, 18, 9))
        assertThat(after.state).isEqualTo(ReminderState.SCHEDULED)
        assertThat(scheduler.armedAt(weekly.id)).isEqualTo(dhaka(2026, 3, 18, 9))
    }

    /**
     * A phone that was off for a fortnight owes Abu one reminder, not fourteen. The occurrences in
     * between are stepped over rather than fired.
     */
    @Test
    fun `a daily reminder missed for a fortnight lands once, tomorrow`() = runTest {
        val daily = reminders.seed(
            reminder(
                title = "Take the tablets",
                triggerAt = dhaka(2026, 2, 24, 7),
                kind = ReminderKind.RECURRING,
                recurrence = RecurrenceRule.Daily(),
            )
        )

        val after = fired(daily.id).getOrNull()!!

        assertThat(after.triggerAt).isEqualTo(dhaka(2026, 3, 11, 7))
        assertThat(reminders.updates(daily.id)).isEqualTo(1)
    }

    @Test
    fun `a series past its end date finishes instead of repeating`() = runTest {
        val weekly = reminders.seed(
            reminder(
                title = "Sprint check-in",
                triggerAt = dhaka(2026, 3, 11, 9),
                kind = ReminderKind.RECURRING,
                recurrence = RecurrenceRule.Weekly(
                    days = setOf(DayOfWeek.WEDNESDAY),
                    until = LocalDate.of(2026, 3, 15),
                ),
            )
        )
        clock.moveTo(dhaka(2026, 3, 11, 9))

        val after = fired(weekly.id).getOrNull()!!

        assertThat(after.state).isEqualTo(ReminderState.FIRED)
        assertThat(scheduler.armed).isEmpty()
    }

    /**
     * The re-arm has to survive the firing going badly, or a reminder that errors once stops for
     * ever. The row is advanced and left scheduled, which is what the next boot rebuilds from.
     */
    @Test
    fun `a series still moves on when the alarm cannot be armed`() = runTest {
        val weekly = weeklyWednesday()
        clock.moveTo(dhaka(2026, 3, 11, 9))
        scheduler.refuseWith = MiraErrorCode.PERMISSION_REQUIRED

        val result = fired(weekly.id)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val stored = reminders.byId(weekly.id).getOrNull()!!
        assertThat(stored.triggerAt).isEqualTo(dhaka(2026, 3, 18, 9))
        assertThat(stored.state).isEqualTo(ReminderState.SCHEDULED)
    }

    // --- completing an occurrence against ending the series -------------------------------------

    /** Completing is about this occurrence; a repeating reminder comes back. */
    @Test
    fun `completing early still moves the series on`() = runTest {
        val weekly = weeklyWednesday()

        val after = complete(weekly.id).getOrNull()!!

        assertThat(after.triggerAt).isEqualTo(dhaka(2026, 3, 18, 9))
        assertThat(after.state).isEqualTo(ReminderState.SCHEDULED)
    }

    @Test
    fun `completing a one-off ends it`() = runTest {
        val saved = create(title = "Call the bank", triggerAt = dhaka(2026, 3, 12, 10)).getOrNull()!!

        val after = complete(saved.id).getOrNull()!!

        assertThat(after.state).isEqualTo(ReminderState.FIRED)
    }

    /** Cancelling is about the whole thing: a repeating reminder does not come back. */
    @Test
    fun `cancelling ends the series`() = runTest {
        val weekly = weeklyWednesday()

        cancel(weekly.id)

        assertThat(reminders.byId(weekly.id).getOrNull()!!.state).isEqualTo(ReminderState.CANCELLED)
        assertThat(scheduler.cancelled).contains(weekly.id)
    }

    // --- the exact-alarm permission --------------------------------------------------------------

    /**
     * Both directions matter. The platform cancels every future exact alarm when the permission is
     * withdrawn, so a revoke has to re-arm them as inexact windows; a grant has to restore them to
     * the minute. Neither is announced any other way.
     */
    @Test
    fun `every reminder is re-armed whenever the exact-alarm permission moves`() = runTest {
        create(title = "Call the bank", triggerAt = dhaka(2026, 3, 12, 10))
        backgroundScope.launch { keepArmed() }
        runCurrent()

        exactAlarms.change(to = false)
        runCurrent()
        assertThat(reminders.rearms).isEqualTo(1)

        exactAlarms.change(to = true)
        runCurrent()
        assertThat(reminders.rearms).isEqualTo(2)
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private suspend fun weeklyWednesday(): Reminder = create(
        title = "Sprint check-in",
        triggerAt = dhaka(2026, 3, 11, 9),
        kind = ReminderKind.RECURRING,
        recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.WEDNESDAY)),
    ).getOrNull()!!

    private fun reminder(
        title: String,
        triggerAt: Instant,
        kind: ReminderKind = ReminderKind.ONE_TIME,
        recurrence: RecurrenceRule? = null,
    ) = Reminder(
        title = title,
        triggerAt = triggerAt,
        kind = kind,
        recurrence = recurrence,
        createdAt = FixedMiraClock.DEFAULT_NOW,
    )

    private fun dhaka(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        FixedMiraClock.dhaka(year, month, day, hour, minute)
}

/** A clock Abu's phone can be pushed forward on, because every rule here is about elapsed time. */
private class ReminderTestClock(private var instant: Instant) : MiraClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = FixedMiraClock.DHAKA
    fun moveTo(to: Instant) {
        instant = to
    }
}

/**
 * Records what reached the OS and at which moment, so a snoozed reminder can be proved to have
 * been armed for the time Abu pushed it to rather than the one he pushed away.
 */
private class ArmedAlarms : ReminderScheduler {

    val armed = mutableMapOf<Long, Instant>()
    val cancelled = mutableListOf<Long>()
    var refuseWith: MiraErrorCode? = null

    override fun canScheduleExact(): Boolean = true

    override suspend fun schedule(reminder: Reminder): AppResult<Unit> {
        refuseWith?.let { return failure(it, "Fake scheduler refused") }
        armed[reminder.id] = reminder.armedAt
        return AppResult.Success(Unit)
    }

    override suspend fun cancel(reminderId: Long): AppResult<Unit> {
        cancelled += reminderId
        armed -= reminderId
        return AppResult.Success(Unit)
    }

    fun armedAt(reminderId: Long): Instant? = armed[reminderId]
}

/** Counts writes per reminder, so "advanced once, not fourteen times" is something to assert. */
private class StoredReminders(private val scheduler: ReminderScheduler) : ReminderRepository {

    private val rows = mutableListOf<Reminder>()
    private val writes = mutableMapOf<Long, Int>()
    private var nextId = 1L
    var rearms = 0

    fun all(): List<Reminder> = rows.toList()

    fun updates(id: Long): Int = writes[id] ?: 0

    fun seed(reminder: Reminder): Reminder {
        val saved = reminder.copy(id = nextId++)
        rows += saved
        return saved
    }

    fun forceState(id: Long, state: ReminderState) {
        val at = rows.indexOfFirst { it.id == id }
        rows[at] = rows[at].copy(state = state)
    }

    override fun observeUpcoming(): Flow<List<Reminder>> =
        flowOf(rows.filter { it.state == ReminderState.SCHEDULED }.sortedBy { it.armedAt })

    override fun observeByKind(kind: ReminderKind): Flow<List<Reminder>> = flowOf(
        rows.filter { it.state == ReminderState.SCHEDULED && it.kind == kind }.sortedBy { it.armedAt }
    )

    override suspend fun byId(id: Long): AppResult<Reminder> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No reminder with id $id")

    override suspend fun create(reminder: Reminder): AppResult<Reminder> =
        AppResult.Success(seed(reminder.copy(id = 0L, state = ReminderState.SCHEDULED)))

    override suspend fun update(reminder: Reminder): AppResult<Reminder> {
        val at = rows.indexOfFirst { it.id == reminder.id }
        if (at < 0) return failure(MiraErrorCode.NOT_FOUND, "No reminder with id ${reminder.id}")
        rows[at] = reminder
        writes[reminder.id] = (writes[reminder.id] ?: 0) + 1
        return AppResult.Success(reminder)
    }

    override suspend fun markFired(id: Long): AppResult<Unit> = setState(id, ReminderState.FIRED)

    override suspend fun cancel(id: Long): AppResult<Unit> = setState(id, ReminderState.CANCELLED)

    override suspend fun nextScheduled(): AppResult<Reminder?> = AppResult.Success(
        rows.filter { it.state == ReminderState.SCHEDULED }.minByOrNull { it.armedAt }
    )

    override suspend fun rescheduleAll(): AppResult<Int> {
        rearms++
        var armed = 0
        for (row in rows.filter { it.state == ReminderState.SCHEDULED && !it.waitsForPlace }) {
            if (scheduler.schedule(row) is AppResult.Success) armed++
        }
        return AppResult.Success(armed)
    }

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

/** Stands in for the platform broadcast that says the exact-alarm permission has moved. */
private class SwitchableExactAlarmAccess : ExactAlarmAccess {

    private var granted = true
    private val broadcasts = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)

    override fun canScheduleExact(): Boolean = granted

    override val changes: Flow<Boolean> get() = broadcasts

    suspend fun change(to: Boolean) {
        granted = to
        broadcasts.emit(to)
    }
}

private class RecordingAlarmClockScheduler : AlarmClockScheduler {
    var rescheduled = 0
        private set

    override suspend fun schedule(alarm: Alarm): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun cancel(alarmId: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun rescheduleAll(): AppResult<Unit> {
        rescheduled++
        return AppResult.Success(Unit)
    }
}

private class RecordingTimerScheduler : TimerScheduler {
    var rescheduled = 0
        private set

    override suspend fun schedule(timer: TimerRecord): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun cancel(timerId: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun rescheduleAll(): AppResult<Unit> {
        rescheduled++
        return AppResult.Success(Unit)
    }
}
