package com.rayluxeo.aida.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.MainActivity
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.alarm.NextAlarmTime
import com.rayluxeo.aida.domain.model.Alarm
import com.rayluxeo.aida.domain.repository.AlarmRepository
import com.rayluxeo.aida.domain.scheduler.AlarmClockScheduler
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * The alarm clock, over `AlarmManager.setAlarmClock`.
 *
 * `setAlarmClock` rather than the `setExactAndAllowWhileIdle` a reminder uses: it is the one
 * scheduling call the OS refuses to defer for Doze or battery saving, and it is what puts the
 * alarm in the status bar and in the system's "next alarm" slot. A reminder must not use it — a
 * status bar full of alarm icons for things Abu asked to be told about is a different product.
 *
 * Where `ReminderSchedulerImpl` falls back to an inexact window when exact alarms are refused,
 * this one refuses outright. A reminder a minute late is a compromise worth making; an alarm clock
 * that might drift is not an alarm clock, and saying so through the result is the only honest
 * answer available.
 *
 * Two alarms can stand behind one row: the alarm's own moment, and a snooze Abu has pushed in
 * front of it. They are separate PendingIntents — same request code, different actions, which is
 * all `Intent.filterEquals` needs to keep them apart — because a snooze must not quietly replace
 * the next occurrence of a repeating alarm.
 */
@Singleton
class AlarmClockSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarms: AlarmRepository,
    private val exactAlarms: ExactAlarmAccess,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : AlarmClockScheduler {

    private val manager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Makes the operating system agree with [alarm] exactly: it arms the moments still ahead and
     * cancels the ones that are not, so calling it after any edit leaves nothing stale behind.
     *
     * An alarm with nothing left to arm is a success rather than an error — the OS then holds
     * nothing for it, which is precisely what the row asks for.
     */
    override suspend fun schedule(alarm: Alarm): AppResult<Unit> {
        if (alarm.id <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "Cannot arm an unsaved alarm")
        }
        val now = clock.now()
        val ownMoment = alarm.nextTriggerAt?.takeIf { alarm.isEnabled && it.isAfter(now) }
        val snoozeMoment = alarm.snoozedUntil?.takeIf { it.isAfter(now) }
        if (ownMoment == null && snoozeMoment == null) return cancel(alarm.id)
        if (!exactAlarms.canScheduleExact()) {
            return failure(MiraErrorCode.PERMISSION_REQUIRED, "Exact alarms are not granted")
        }
        return withContext(dispatchers.io) {
            val own = arm(alarm, AlarmReceiver.ACTION_FIRE, ownMoment)
            val snoozed = arm(alarm, AlarmReceiver.ACTION_SNOOZE_ELAPSED, snoozeMoment)
            if (own is AppResult.Failure) own else snoozed
        }
    }

    override suspend fun cancel(alarmId: Long): AppResult<Unit> = withContext(dispatchers.io) {
        drop(alarmId, AlarmReceiver.ACTION_FIRE)
        drop(alarmId, AlarmReceiver.ACTION_SNOOZE_ELAPSED)
        AppResult.Success(Unit)
    }

    /**
     * Replayed after a reboot, after a package replacement, and the moment exact alarms are
     * granted again — all three leave Mira holding nothing at the OS.
     *
     * The armed moment is recomputed rather than replayed. A reminder whose time passed while the
     * phone was off should fire as soon as it comes back, which is why the reminder replay arms
     * those unchanged; an alarm clock must not, because a seven o'clock alarm going off at ten is
     * worse than one that stayed quiet. The recomputed moment is written back too: the row is what
     * Abu's list shows him, and a list that disagrees with the OS is the failure this area exists
     * to prevent.
     *
     * A single alarm that cannot be restored does not stop the rest. The first failure is carried
     * out so the caller still hears that something went wrong.
     */
    override suspend fun rescheduleAll(): AppResult<Unit> {
        if (!exactAlarms.canScheduleExact()) {
            return failure(MiraErrorCode.PERMISSION_REQUIRED, "Exact alarms are not granted")
        }
        return when (val armable = alarms.enabledOrSnoozed()) {
            is AppResult.Failure -> armable
            is AppResult.Success -> {
                var firstFailure: AppResult.Failure? = null
                for (alarm in armable.value) {
                    val restored = restore(alarm)
                    if (restored is AppResult.Failure && firstFailure == null) firstFailure = restored
                }
                firstFailure ?: AppResult.Success(Unit)
            }
        }
    }

    private suspend fun restore(alarm: Alarm): AppResult<Unit> {
        val now = clock.now()
        val refreshed = alarm.copy(
            nextTriggerAt = if (alarm.isEnabled) {
                NextAlarmTime.after(alarm.at, alarm.repeatDays, clock.zone(), now)
            } else {
                null
            },
            snoozedUntil = alarm.snoozedUntil?.takeIf { it.isAfter(now) },
            updatedAt = now,
        )
        return when (val stored = alarms.update(refreshed)) {
            is AppResult.Failure -> stored
            is AppResult.Success -> schedule(stored.value)
        }
    }

    /** Arms [at], or cancels whatever was standing under [action] when it is null or already spent. */
    private fun arm(alarm: Alarm, action: String, at: Instant?): AppResult<Unit> {
        if (at == null) {
            drop(alarm.id, action)
            return AppResult.Success(Unit)
        }
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode(alarm.id),
            fireIntent(alarm, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val info = AlarmManager.AlarmClockInfo(at.toEpochMilli(), showIntent(alarm.id))
        return try {
            manager.setAlarmClock(info, operation)
            AppResult.Success(Unit)
        } catch (revoked: SecurityException) {
            // The grant can disappear between the check above and this call; the pair is not atomic.
            failure(MiraErrorCode.PERMISSION_REQUIRED, "Exact alarms were revoked while arming", revoked)
        }
    }

    private fun drop(alarmId: Long, action: String) {
        val existing = PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        manager.cancel(existing)
        existing.cancel()
    }

    /**
     * The label travels in the extras so an alarm still says what it is for when the database
     * cannot be opened at the moment it rings — the same reason [ReminderReceiver] carries its
     * title rather than reading it back.
     */
    private fun fireIntent(alarm: Alarm, action: String): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            .putExtra(AlarmReceiver.EXTRA_LABEL, alarm.label)

    /**
     * Where the status bar's alarm icon goes. `AlarmClockInfo` wants somewhere for Abu to land
     * when he taps it, and the honest destination is Mira herself.
     */
    private fun showIntent(alarmId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(alarmId),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Keyed on the alarm's own id, so re-arming replaces rather than duplicates. It cannot collide
     * with a reminder carrying the same number: those PendingIntents name a different receiver,
     * and the component is part of what `Intent.filterEquals` compares.
     */
    private fun requestCode(alarmId: Long): Int = alarmId.toInt()
}
