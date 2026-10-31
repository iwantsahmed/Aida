package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.TimerRecord
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.repository.TimerRepository
import com.rayluxeo.aida.domain.scheduler.TimerScheduler
import java.time.Duration
import javax.inject.Inject

/**
 * The countdown verbs.
 *
 * A timer exists in two places that must agree: the row, which is the durable truth and survives
 * a process death, and the alarm in the OS, which is the only thing that will actually interrupt
 * Abu. Every verb here writes the row first and then moves the alarm to match, because a row with
 * no alarm behind it is a timer that is merely quiet, while an alarm with no row behind it is a
 * timer that rings for a countdown Abu already stopped.
 *
 * Nothing here reads the wall clock for arithmetic: what is left of a timer comes from
 * [TimerRecord.remainingAt], and the end instant is resolved once, in the repository.
 */
class StartTimerUseCase @Inject constructor(
    private val timers: TimerRepository,
    private val scheduler: TimerScheduler,
) {

    /**
     * [label] may be empty — an unlabelled timer is ordinary, and naming it is the presentation
     * layer's job, in Abu's language.
     *
     * A timer the OS refuses to arm is taken back out of storage rather than left running: a row
     * counting down to a moment nothing will announce is the one failure Abu cannot notice until
     * the moment has gone.
     */
    suspend operator fun invoke(label: String, duration: Duration): AppResult<TimerRecord> =
        timers.start(label, duration).flatMap { started ->
            when (val armed = scheduler.schedule(started)) {
                is AppResult.Failure -> {
                    timers.cancel(started.id)
                    armed
                }
                is AppResult.Success -> AppResult.Success(started)
            }
        }
}

/**
 * Freezes a countdown and takes its alarm down.
 *
 * The result of disarming is deliberately not folded into what is returned. The pause is what Abu
 * asked for and it has already happened in the one place that matters; an alarm the OS would not
 * let Mira withdraw is harmless, because the fire path refuses to end a timer that is not running.
 */
class PauseTimerUseCase @Inject constructor(
    private val timers: TimerRepository,
    private val scheduler: TimerScheduler,
) {

    suspend operator fun invoke(id: Long): AppResult<TimerRecord> =
        timers.pause(id).flatMap { paused ->
            scheduler.cancel(id)
            AppResult.Success(paused)
        }
}

/**
 * Restarts a paused countdown from what was left of it.
 *
 * If the OS will not arm the new end, the timer is put back the way it was found. Leaving it
 * running instead would show Abu a countdown that nothing is going to announce, and a paused
 * timer he can resume again is the more honest of the two states.
 */
class ResumeTimerUseCase @Inject constructor(
    private val timers: TimerRepository,
    private val scheduler: TimerScheduler,
) {

    suspend operator fun invoke(id: Long): AppResult<TimerRecord> =
        timers.resume(id).flatMap { resumed ->
            when (val armed = scheduler.schedule(resumed)) {
                is AppResult.Failure -> {
                    timers.pause(id)
                    armed
                }
                is AppResult.Success -> AppResult.Success(resumed)
            }
        }
}

/** Stops a countdown before its moment and withdraws its alarm. */
class CancelTimerUseCase @Inject constructor(
    private val timers: TimerRepository,
    private val scheduler: TimerScheduler,
) {

    suspend operator fun invoke(id: Long): AppResult<TimerRecord> =
        timers.cancel(id).flatMap { cancelled ->
            scheduler.cancel(id)
            AppResult.Success(cancelled)
        }
}

/**
 * Marks a countdown as having run out.
 *
 * The alarm is withdrawn as well even though the usual caller is the alarm itself firing: the
 * other caller is the reboot restore, where the moment passed while the phone was off and there
 * may still be a PendingIntent registered against a timer that is now over.
 */
class FinishTimerUseCase @Inject constructor(
    private val timers: TimerRepository,
    private val scheduler: TimerScheduler,
) {

    suspend operator fun invoke(id: Long): AppResult<TimerRecord> =
        timers.finish(id).flatMap { finished ->
            scheduler.cancel(id)
            AppResult.Success(finished)
        }
}

/**
 * Puts the timers back after a reboot, which drops every alarm the OS was holding.
 *
 * Three states, three different answers, and the first of them is the one worth being careful
 * about:
 *
 * - A **running** timer whose moment passed while the phone was off has already happened. It is
 *   finished and handed back so the caller can tell Abu, rather than re-armed for an instant in
 *   the past — an alarm set to a moment that has gone fires immediately, so re-arming would
 *   announce a 25-minute timer as though it had just ended hours after it did.
 * - A **running** timer with time left is re-armed against the end instant it always had. Nothing
 *   is recomputed: [TimerRecord.endsAt] is wall-clock truth and a reboot does not move it.
 * - A **paused** timer is left exactly alone. What is left of it was measured when it was paused
 *   and is not counted against the wall clock, so it neither expires nor drifts while the phone is
 *   off, and it has no end instant to arm. It comes back paused and is armed when Abu resumes it.
 */
class RestoreTimersUseCase @Inject constructor(
    private val timers: TimerRepository,
    private val scheduler: TimerScheduler,
    private val finishTimer: FinishTimerUseCase,
    private val clock: MiraClock,
) {

    /**
     * Returns the timers that ran out while the phone was off, in the order they ended.
     *
     * A failure to re-arm the survivors deliberately does not replace that list. This path runs
     * once, inside a boot broadcast, and its whole purpose is to tell Abu what he missed; a
     * refused alarm will surface again the next time Mira arms anything, while the announcement
     * has no second chance.
     */
    suspend operator fun invoke(): AppResult<List<TimerRecord>> =
        timers.active().map { active ->
            val now = clock.now()
            val ranOut = active
                .filter { it.state == TimerState.RUNNING && !it.endsAt.isAfter(now) }
                .sortedBy { it.endsAt }
                .mapNotNull { finishTimer(it.id).getOrNull() }
            // After the overdue ones are settled, so that nothing is armed for a moment that has
            // already gone: the scheduler re-arms whatever is still running.
            scheduler.rescheduleAll()
            ranOut
        }
}
