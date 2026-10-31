package com.rayluxeo.aida.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.domain.model.TimerRecord
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.repository.TimerRepository
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import com.rayluxeo.aida.domain.scheduler.TimerScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * The alarms behind Abu's countdowns.
 *
 * Exact, because a 25-minute timer that fires at 27 minutes is wrong in a way he will notice at
 * once. Exact alarms are revocable from Android 13 and the check-then-schedule pair is not atomic
 * — the grant can disappear between the two calls — so the SecurityException is an ordinary
 * outcome here rather than a fault. The permission is read through [ExactAlarmAccess] so that one
 * answer serves the whole app, and so that [rescheduleAll] has a caller when the grant returns.
 *
 * Where it degrades to differs from [ReminderSchedulerImpl] on purpose. A reminder falls back to
 * `setWindow`; a timer falls back to `setAndAllowWhileIdle`, because a phone that has been sitting
 * still for the twenty-five minutes of a timer is in Doze precisely when the alarm comes due, and
 * `setWindow` is deferred there while `setAndAllowWhileIdle` is not. The same reasoning is set out
 * at greater length in [BriefSchedulerImpl].
 *
 * It does not use `setAlarmClock`. That call is the OS's idea of an alarm clock — undeferrable,
 * and drawn in the status bar with a time beside it — and it belongs to the alarm clock. A timer
 * is Abu's, not the system's, and it has no business claiming that slot.
 */
@Singleton
class TimerSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exactAlarms: ExactAlarmAccess,
    private val timers: TimerRepository,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : TimerScheduler {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(timer: TimerRecord): AppResult<Unit> {
        if (timer.id <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "Cannot arm an unsaved timer")
        }
        if (timer.state != TimerState.RUNNING) {
            return failure(MiraErrorCode.VALIDATION, "Timer ${timer.id} is ${timer.state}, not running")
        }
        return withContext(dispatchers.io) {
            val pending = PendingIntent.getBroadcast(
                context,
                TimerCodes.requestCode(timer.id),
                fireIntent(timer),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val endsAt = timer.endsAt.toEpochMilli()
            if (exactAlarms.canScheduleExact()) {
                try {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pending)
                    return@withContext AppResult.Success(Unit)
                } catch (revoked: SecurityException) {
                    return@withContext setInexact(endsAt, pending, revoked)
                }
            }
            setInexact(endsAt, pending, null)
        }
    }

    override suspend fun cancel(timerId: Long): AppResult<Unit> = withContext(dispatchers.io) {
        // The extras are deliberately absent: PendingIntent matching ignores them, and rebuilding
        // the bare intent is what lets a cancel find an alarm armed with a label it no longer has.
        val pending = PendingIntent.getBroadcast(
            context,
            TimerCodes.requestCode(timerId),
            Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return@withContext AppResult.Success(Unit)
        alarms.cancel(pending)
        pending.cancel()
        AppResult.Success(Unit)
    }

    /**
     * Re-arms every countdown that still has time left, and is also the way back after the OS
     * hands exact alarms to Mira again — that grant returning is the moment it cancelled every
     * future exact alarm she held.
     *
     * A running timer whose moment has already gone is skipped rather than armed: an alarm set for
     * an instant in the past fires immediately, which would announce a countdown that ended hours
     * ago as though it had just run out. Settling those is the restore's job, not the scheduler's.
     *
     * Every timer is attempted before the first refusal is reported, so one alarm the OS would not
     * take cannot cost Abu the rest of them.
     */
    override suspend fun rescheduleAll(): AppResult<Unit> = timers.active().flatMap { active ->
        val now = clock.now()
        val outcomes = active
            .filter { it.state == TimerState.RUNNING && it.endsAt.isAfter(now) }
            .map { schedule(it) }
        outcomes.firstOrNull { it is AppResult.Failure } ?: AppResult.Success(Unit)
    }

    private fun setInexact(
        endsAt: Long,
        pending: PendingIntent,
        revoked: SecurityException?,
    ): AppResult<Unit> = try {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pending)
        AppResult.Success(Unit)
    } catch (denied: SecurityException) {
        failure(MiraErrorCode.PERMISSION_REQUIRED, "Timer alarm refused", revoked ?: denied)
    }

    /**
     * The label travels in the extras so that a timer can still be announced on a phone whose
     * database will not open at the moment it ends.
     */
    private fun fireIntent(timer: TimerRecord): Intent =
        Intent(context, TimerReceiver::class.java).apply {
            action = TimerReceiver.ACTION_FIRE
            putExtra(TimerReceiver.EXTRA_TIMER_ID, timer.id)
            putExtra(TimerReceiver.EXTRA_LABEL, timer.label)
        }
}

/**
 * Where a timer's alarm and its notification sit in the two id spaces Mira shares across the app.
 *
 * Android matches a PendingIntent on its request code and on the intent's action, component, data,
 * categories and type — **never on its extras**. Two timers sharing a request code would therefore
 * be the same PendingIntent, and arming the second would silently replace the first; Abu would set
 * two timers, see two, and be told about one. So the row id is folded into the code.
 *
 * The three bands cannot meet. Reminders key their alarms on the row id itself, and Room only ever
 * hands out positive ids; the two briefs took -101 and -102; a timer takes -1_000_000 minus its
 * id. Within timers the mapping is one-to-one because Room's ids are distinct, and it cannot wrap
 * into another band without about two billion timers, which is not a number of countdowns.
 *
 * Notification ids are a separate space with the same problem — `notify` replaces by id, app-wide,
 * whatever channel the notification is on — so timers take a band of their own there too, below
 * the briefs' and clear of the reminders'.
 *
 * It is pulled out of [TimerSchedulerImpl] because this arithmetic is the one part of arming a
 * timer that can be wrong in a way no device test would catch, and a plain JUnit test can reach it
 * here with no `AlarmManager` in sight.
 */
internal object TimerCodes {

    private const val REQUEST_CODE_BASE = -1_000_000L
    private const val NOTIFICATION_ID_BASE = -1_000L

    fun requestCode(timerId: Long): Int = (REQUEST_CODE_BASE - timerId).toInt()

    fun notificationId(timerId: Long): Int = (NOTIFICATION_ID_BASE - timerId).toInt()
}
