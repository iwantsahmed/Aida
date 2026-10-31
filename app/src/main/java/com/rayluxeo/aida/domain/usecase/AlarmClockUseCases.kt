package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.alarm.NextAlarmTime
import com.rayluxeo.aida.domain.model.Alarm
import com.rayluxeo.aida.domain.repository.AlarmRepository
import com.rayluxeo.aida.domain.scheduler.AlarmClockScheduler
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

/**
 * Setting an alarm.
 *
 * The moment it will ring is worked out before the row is written, so the alarm reaches storage
 * already knowing when it goes off and nothing has to come back for a second write on the happy
 * path. An empty set of repeat days is a one-time alarm: it switches itself off once it has rung.
 *
 * **A blank label is accepted and stored blank.** An alarm, unlike a reminder, need not be about
 * anything — "wake me up at 7" names no subject, and refusing it would turn the commonest alarm
 * Abu sets into an error. The word shown in its place is user-facing text and so belongs to the
 * presentation layer (§4), which already supplies it: `AlarmsViewModel` renders
 * `label.ifBlank { words.untitledAlarm }`, exactly as `TimersViewModel` does for a timer. Writing
 * "Alarm" into the row here instead would freeze one language into storage and leave it there
 * after Abu switched to the other.
 */
class SetAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmClockScheduler,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(
        label: String,
        at: LocalTime,
        repeatDays: Set<DayOfWeek> = emptySet(),
    ): AppResult<Alarm> {
        val clean = label.trim()
        val now = clock.now()
        val alarm = Alarm(
            label = clean,
            at = at,
            repeatDays = repeatDays,
            isEnabled = true,
            nextTriggerAt = NextAlarmTime.after(at, repeatDays, clock.zone(), now),
            createdAt = now,
            updatedAt = now,
        )
        return alarms.create(alarm).flatMap { saved -> armAndStore(alarms, scheduler, saved) }
    }
}

/**
 * Changing an alarm's label, time or days.
 *
 * Any snooze is dropped. A snooze is a push against a particular morning, and once the alarm has
 * been moved there is nothing left for it to be measured from.
 *
 * Blank is allowed here for the same reason it is in [SetAlarmUseCase], and because the two must
 * agree: an alarm Abu never named would otherwise be one he could not edit at all — the sheet
 * opens on its empty label and saving it back would be refused.
 */
class UpdateAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmClockScheduler,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(
        id: Long,
        label: String,
        at: LocalTime,
        repeatDays: Set<DayOfWeek>,
    ): AppResult<Alarm> {
        val clean = label.trim()
        return alarms.byId(id).flatMap { existing ->
            val now = clock.now()
            val edited = existing.copy(
                label = clean,
                at = at,
                repeatDays = repeatDays,
                nextTriggerAt = if (existing.isEnabled) {
                    NextAlarmTime.after(at, repeatDays, clock.zone(), now)
                } else {
                    null
                },
                snoozedUntil = null,
                updatedAt = now,
            )
            alarms.update(edited).flatMap { saved -> armAndStore(alarms, scheduler, saved) }
        }
    }
}

/**
 * The switch beside an alarm.
 *
 * Switching one off takes the alarm out of the OS as well as out of the row, and switching it on
 * works out the next occurrence from today rather than resurrecting whatever moment was stored
 * when it was last switched off — which, for an alarm that has been off for a week, is a morning
 * that has already been and gone.
 */
class ToggleAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmClockScheduler,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(id: Long, isEnabled: Boolean): AppResult<Alarm> =
        alarms.byId(id).flatMap { alarm ->
            val now = clock.now()
            val switched = alarm.copy(
                isEnabled = isEnabled,
                nextTriggerAt = if (isEnabled) {
                    NextAlarmTime.after(alarm.at, alarm.repeatDays, clock.zone(), now)
                } else {
                    null
                },
                snoozedUntil = null,
                updatedAt = now,
            )
            alarms.update(switched).flatMap { saved -> armAndStore(alarms, scheduler, saved) }
        }
}

/**
 * Pushing a ringing alarm away for a while.
 *
 * The alarm's own time is deliberately untouched, exactly as a snoozed reminder keeps its trigger:
 * the snooze is a second moment in front of the first, so tomorrow's seven o'clock is still seven
 * o'clock rather than ten past for ever after.
 */
class SnoozeAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmClockScheduler,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(id: Long, by: Duration): AppResult<Alarm> {
        if (by.isZero || by.isNegative) {
            return failure(MiraErrorCode.VALIDATION, "Snooze was $by")
        }
        return alarms.byId(id).flatMap { alarm ->
            val now = clock.now()
            val snoozed = alarm.copy(snoozedUntil = now.plus(by), updatedAt = now)
            alarms.update(snoozed).flatMap { saved -> armAndStore(alarms, scheduler, saved) }
        }
    }
}

/**
 * The alarm's own moment arrived.
 *
 * A one-time alarm switches itself off, because it has done the single thing it was for and
 * leaving it enabled would arm it again for tomorrow. A repeating one arms its next day. Either
 * way the snooze is spent: the alarm has just sounded, so there is nothing left in front of it.
 */
class AlarmRangUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmClockScheduler,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(id: Long): AppResult<Alarm> =
        alarms.byId(id).flatMap { alarm ->
            val now = clock.now()
            if (alarm.repeats) {
                // Searched from the moment it was armed for rather than from now, so a receiver
                // running a hair ahead of the stored instant cannot re-arm the occurrence that
                // has just gone off.
                val from = maxOf(now, alarm.nextTriggerAt ?: now)
                val advanced = alarm.copy(
                    nextTriggerAt = NextAlarmTime.after(alarm.at, alarm.repeatDays, clock.zone(), from),
                    snoozedUntil = null,
                    updatedAt = now,
                )
                alarms.update(advanced).flatMap { saved -> armAndStore(alarms, scheduler, saved) }
            } else {
                val spent = alarm.copy(
                    isEnabled = false,
                    nextTriggerAt = null,
                    snoozedUntil = null,
                    updatedAt = now,
                )
                alarms.update(spent).flatMap { saved -> scheduler.cancel(saved.id).map { saved } }
            }
        }
}

/**
 * A snooze ran out and the alarm sounded again.
 *
 * Only the snooze is cleared. A repeating alarm's next occurrence was armed when it first rang and
 * must be left exactly where it is — clearing it here is how a daily alarm would quietly stop
 * after the first morning Abu pressed snooze.
 */
class SnoozeElapsedUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(id: Long): AppResult<Alarm> =
        alarms.byId(id).flatMap { alarm ->
            alarms.update(alarm.copy(snoozedUntil = null, updatedAt = clock.now()))
        }
}

/** Removing an alarm. The OS is told first, so a delete that fails cannot leave one ringing. */
class DeleteAlarmUseCase @Inject constructor(
    private val alarms: AlarmRepository,
    private val scheduler: AlarmClockScheduler,
) {

    suspend operator fun invoke(id: Long): AppResult<Unit> =
        scheduler.cancel(id).flatMap { alarms.delete(id) }
}

/**
 * Arms [alarm] and leaves the row agreeing with whatever the operating system accepted.
 *
 * When it refuses — exact alarms withdrawn is the case that actually happens — the stored moments
 * are cleared rather than the alarm being thrown away. The alarm is still Abu's; it simply has
 * nothing armed behind it, and the replay puts it back the moment the permission returns. Keeping
 * the moment on the row instead would leave his list promising a morning that will never come.
 */
private suspend fun armAndStore(
    alarms: AlarmRepository,
    scheduler: AlarmClockScheduler,
    alarm: Alarm,
): AppResult<Alarm> = when (val scheduled = scheduler.schedule(alarm)) {
    is AppResult.Failure -> {
        alarms.update(alarm.copy(nextTriggerAt = null, snoozedUntil = null))
        scheduled
    }
    is AppResult.Success -> AppResult.Success(alarm)
}
