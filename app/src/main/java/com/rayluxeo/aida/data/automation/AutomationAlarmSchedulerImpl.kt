package com.rayluxeo.aida.data.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.automation.AutomationAlarmScheduler
import com.rayluxeo.aida.domain.automation.AutomationNextRun
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.repository.AutomationRepository
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * AlarmManager behind the time-based automation triggers, built on the same reasoning as
 * [com.rayluxeo.aida.data.scheduler.ReminderSchedulerImpl]: exact alarms are a revocable permission
 * on API 31+, so a lost grant degrades to an inexact window rather than dropping the rule. A rule
 * firing a minute late is a compromise; one that never fires is a broken promise.
 */
@Singleton
class AutomationAlarmSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exactAlarms: ExactAlarmAccess,
    private val automationRepository: AutomationRepository,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : AutomationAlarmScheduler {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun sync(automation: Automation): AppResult<Unit> = withContext(dispatchers.io) {
        if (automation.id <= 0L) {
            return@withContext failure(MiraErrorCode.VALIDATION, "Cannot arm an unsaved automation")
        }
        // Anything that is not an enabled, future, time-based trigger ends with no alarm: a disabled
        // rule, a device-event rule, a date already gone.
        val next = if (automation.isEnabled && AutomationNextRun.isTimeBased(automation.trigger)) {
            AutomationNextRun.next(automation.trigger, clock.now(), clock.zone())
        } else {
            null
        }
        if (next == null) {
            cancelAlarm(automation.id)
            return@withContext Unit.asSuccess()
        }
        arm(automation.id, next.toEpochMilli())
    }

    override suspend fun cancel(automationId: Long): AppResult<Unit> = withContext(dispatchers.io) {
        cancelAlarm(automationId)
        Unit.asSuccess()
    }

    override suspend fun rescheduleAll(): AppResult<Unit> = withContext(dispatchers.io) {
        val enabled = runCatching { automationRepository.observeEnabled().first() }
            .getOrDefault(emptyList())
        enabled.filter { AutomationNextRun.isTimeBased(it.trigger) }.forEach { sync(it) }
        Unit.asSuccess()
    }

    private fun arm(automationId: Long, triggerAt: Long): AppResult<Unit> {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(automationId),
            fireIntent(automationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return try {
            if (exactAlarms.canScheduleExact()) {
                try {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                } catch (_: SecurityException) {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            Unit.asSuccess()
        } catch (denied: SecurityException) {
            failure(MiraErrorCode.PERMISSION_REQUIRED, "Alarm scheduling refused", denied)
        }
    }

    private fun cancelAlarm(automationId: Long) {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(automationId),
            Intent(context, AutomationAlarmReceiver::class.java).setAction(AutomationAlarmReceiver.ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (pending != null) {
            alarms.cancel(pending)
            pending.cancel()
        }
    }

    private fun fireIntent(automationId: Long): Intent =
        Intent(context, AutomationAlarmReceiver::class.java).apply {
            action = AutomationAlarmReceiver.ACTION_FIRE
            putExtra(AutomationAlarmReceiver.EXTRA_AUTOMATION_ID, automationId)
        }

    /**
     * Offset away from the raw ids reminders, alarms and timers key on, so an automation and a
     * reminder that happen to share a row id can never overwrite each other's alarm. (The target
     * receiver class already differs, but the offset makes it impossible to reason wrong about.)
     */
    private fun requestCode(automationId: Long): Int = (AUTOMATION_REQUEST_OFFSET + automationId).toInt()

    private companion object {
        const val INEXACT_WINDOW_MILLIS = 60_000L
        const val AUTOMATION_REQUEST_OFFSET = 900_000L
    }
}
