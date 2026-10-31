package com.rayluxeo.aida.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.usecase.CompleteReminderUseCase
import com.rayluxeo.aida.domain.usecase.CompleteTaskByIdUseCase
import com.rayluxeo.aida.domain.usecase.SnoozeAlarmUseCase
import com.rayluxeo.aida.domain.usecase.SnoozeReminderUseCase
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What the buttons on Mira's notifications actually do.
 *
 * A receiver rather than an activity, because acting from the shade must not drag Mira open over
 * whatever Abu was doing. Snoozing is snoozing: the card goes, nothing else moves.
 *
 * The work is real work — a task closed, a reminder pushed, an alarm put off — so it goes through
 * the same use cases the screens use. A receiver has milliseconds of its own, so the suspend calls
 * finish under [goAsync], which keeps the process alive long enough for them.
 *
 * **The card is taken down only once the action has landed.** A snooze that failed and a snooze
 * that worked look identical after the notification disappears, and of the two mistakes leaving
 * the card up is the one Abu can see and act on.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var completeReminder: CompleteReminderUseCase

    @Inject lateinit var snoozeReminder: SnoozeReminderUseCase

    @Inject lateinit var completeTask: CompleteTaskByIdUseCase

    @Inject lateinit var snoozeAlarm: SnoozeAlarmUseCase

    @Inject lateinit var notifications: MiraNotifications

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() call: Hilt's Gradle plugin rewrites this class to extend its
    // generated base and injects that call into the bytecode itself. Writing it here would
    // resolve against BroadcastReceiver's abstract member and fail to compile.
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_NOTIFICATION)

        // Dismiss is only the card going away, and that is synchronous: no coroutine, and no
        // reason to hold the process open for it.
        if (action == ACTION_DISMISS) {
            if (notificationId != NO_NOTIFICATION) notifications.cancel(notificationId)
            return
        }
        if (action !in ASYNC_ACTIONS) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                val landed = when (action) {
                    ACTION_COMPLETE -> complete(intent)
                    ACTION_SNOOZE -> snooze(intent)
                    else -> snoozeTheAlarm(intent)
                }
                if (landed && notificationId != NO_NOTIFICATION) notifications.cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The task is closed first. If that fails the reminder is left alone and the card stays up,
     * because a reminder marked done over a task that is still open is the one outcome here Abu
     * has no way to notice.
     */
    private suspend fun complete(intent: Intent): Boolean {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, NO_ID)
        if (reminderId <= NO_ID) return false
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, NO_ID)
        if (taskId > NO_ID && completeTask(taskId) is AppResult.Failure) return false
        return completeReminder(reminderId) is AppResult.Success
    }

    private suspend fun snooze(intent: Intent): Boolean {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, NO_ID)
        val by = spanOf(intent) ?: return false
        if (reminderId <= NO_ID) return false
        return snoozeReminder(reminderId, by) is AppResult.Success
    }

    private suspend fun snoozeTheAlarm(intent: Intent): Boolean {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, NO_ID)
        val by = spanOf(intent) ?: return false
        if (alarmId <= NO_ID) return false
        return snoozeAlarm(alarmId, by) is AppResult.Success
    }

    /** The span travels in the intent so the button's words and its effect cannot disagree. */
    private fun spanOf(intent: Intent): Duration? = intent
        .getLongExtra(EXTRA_MINUTES, 0L)
        .takeIf { it > 0L }
        ?.let(Duration::ofMinutes)

    companion object {
        const val ACTION_COMPLETE = "com.rayluxeo.aida.action.NOTIFICATION_COMPLETE"
        const val ACTION_SNOOZE = "com.rayluxeo.aida.action.NOTIFICATION_SNOOZE"
        const val ACTION_SNOOZE_ALARM = "com.rayluxeo.aida.action.NOTIFICATION_SNOOZE_ALARM"
        const val ACTION_DISMISS = "com.rayluxeo.aida.action.NOTIFICATION_DISMISS"

        const val EXTRA_REMINDER_ID = "com.rayluxeo.aida.extra.ACTION_REMINDER_ID"

        /** Carried so Mira can open with the reminder as context rather than on a blank prompt. */

        const val EXTRA_TASK_ID = "com.rayluxeo.aida.extra.ACTION_TASK_ID"
        const val EXTRA_ALARM_ID = "com.rayluxeo.aida.extra.ACTION_ALARM_ID"
        const val EXTRA_MINUTES = "com.rayluxeo.aida.extra.ACTION_MINUTES"
        const val EXTRA_NOTIFICATION_ID = "com.rayluxeo.aida.extra.ACTION_NOTIFICATION_ID"

        private const val NO_ID = 0L
        private const val NO_NOTIFICATION = 0

        private val ASYNC_ACTIONS = setOf(ACTION_COMPLETE, ACTION_SNOOZE, ACTION_SNOOZE_ALARM)
    }
}
