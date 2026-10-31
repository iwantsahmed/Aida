package com.rayluxeo.aida.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.repository.AlarmRecordRepository
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Exact alarms are a revocable permission on API 31+, and the check-then-schedule pair is not
 * atomic: the grant can disappear between the two calls. So the SecurityException is treated as a
 * normal outcome and the alarm is re-armed as an inexact window instead. A reminder arriving a
 * minute late is a compromise; a reminder that never arrives is a broken promise.
 *
 * Which of the two happened is written to the alarm ledger as `isExact`, so Mira can tell Abu his
 * reminder may be a few minutes late rather than implying it will be punctual. That is the whole
 * reason the ledger carries the flag.
 */
@Singleton
class ReminderSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exactAlarms: ExactAlarmAccess,
    private val ledger: AlarmRecordRepository,
    private val dispatchers: DispatcherProvider,
) : ReminderScheduler {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = exactAlarms.canScheduleExact()

    /**
     * Arms [Reminder.armedAt] rather than the trigger, so a snoozed reminder goes off at the time
     * Abu pushed it to and not at the one he pushed away.
     */
    override suspend fun schedule(reminder: Reminder): AppResult<Unit> {
        if (reminder.id <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "Cannot arm an unsaved reminder")
        }
        if (reminder.waitsForPlace) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Reminder ${reminder.id} waits for a place and has no moment to arm",
            )
        }
        return withContext(dispatchers.io) {
            val requestCode = requestCode(reminder.id)
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                fireIntent(reminder),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val triggerAt = reminder.armedAt.toEpochMilli()
            when (val armed = arm(triggerAt, pending)) {
                is AppResult.Failure -> armed
                is AppResult.Success -> {
                    record(reminder, triggerAt, isExact = armed.value, requestCode = requestCode)
                    AppResult.Success(Unit)
                }
            }
        }
    }

    override suspend fun cancel(reminderId: Long): AppResult<Unit> = withContext(dispatchers.io) {
        val requestCode = requestCode(reminderId)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (pending != null) {
            alarms.cancel(pending)
            pending.cancel()
        }
        // Closed even when there was no PendingIntent left to cancel: the platform drops exact
        // alarms on its own when the permission goes, and an open ledger row would then claim an
        // alarm the OS is no longer holding.
        closeOpenRecord(requestCode)
        AppResult.Success(Unit)
    }

    /** @return true when the OS took it as an exact alarm, false when it settled for a window. */
    private fun arm(triggerAt: Long, pending: PendingIntent): AppResult<Boolean> {
        if (exactAlarms.canScheduleExact()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return AppResult.Success(true)
            } catch (revoked: SecurityException) {
                return setWindow(triggerAt, pending, revoked)
            }
        }
        return setWindow(triggerAt, pending, null)
    }

    private fun setWindow(
        triggerAt: Long,
        pending: PendingIntent,
        revoked: SecurityException?,
    ): AppResult<Boolean> = try {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        AppResult.Success(false)
    } catch (denied: SecurityException) {
        failure(MiraErrorCode.PERMISSION_REQUIRED, "Alarm scheduling refused", revoked ?: denied)
    }

    /**
     * Writes the arming down, and deliberately does not report its failure.
     *
     * By the time this runs the OS is already holding the alarm. Returning a failure from here
     * would tell the caller a reminder had not been armed when it had, and that is the one lie
     * this system must not tell. The ledger is history; a gap in it costs an explanation later,
     * not a reminder.
     *
     * The previous open row for this request code is closed first, because re-arming replaces the
     * OS alarm rather than adding one — leaving both open would make `scheduledByRequestCode`
     * answer with an arming that no longer exists.
     */
    private suspend fun record(
        reminder: Reminder,
        triggerAt: Long,
        isExact: Boolean,
        requestCode: Int,
    ) {
        closeOpenRecord(requestCode)
        ledger.record(
            reminderId = reminder.id,
            label = reminder.title,
            triggerAt = Instant.ofEpochMilli(triggerAt),
            isExact = isExact,
            requestCode = requestCode,
        )
    }

    private suspend fun closeOpenRecord(requestCode: Int) {
        val open = ledger.scheduledByRequestCode(requestCode).getOrNull() ?: return
        ledger.cancel(open.id)
    }

    private fun fireIntent(reminder: Reminder): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
        }

    /** The reminder id keys the alarm, so re-arming the same reminder replaces rather than duplicates. */
    private fun requestCode(reminderId: Long): Int = reminderId.toInt()

    private companion object {
        const val INEXACT_WINDOW_MILLIS = 60_000L
    }
}
