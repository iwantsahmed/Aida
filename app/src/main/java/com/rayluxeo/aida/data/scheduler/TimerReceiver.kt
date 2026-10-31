package com.rayluxeo.aida.data.scheduler

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.rayluxeo.aida.R
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TimerRepository
import com.rayluxeo.aida.domain.usecase.FinishTimerUseCase
import com.rayluxeo.aida.domain.usecase.RestoreTimersUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Where a countdown ends, and where the countdowns are picked back up after a reboot.
 *
 * Unlike [ReminderReceiver], this one reads the row before it says anything. A reminder's alarm
 * cannot outlive the reminder — nothing can pause one — but a timer's can: pausing cancels the
 * alarm, and if the OS refuses that cancel the alarm is still coming for a countdown Abu has
 * already stopped. Announcing a timer he paused twenty minutes ago is worse than announcing one a
 * moment later, so the state is checked first and only a running timer is ever announced.
 *
 * The promise still survives a database that will not open: a storage failure falls back to the
 * label carried in the extras, because the alarm exists only because a timer was armed. A timer
 * that is simply gone is a different fact and stays silent.
 */
@AndroidEntryPoint
class TimerReceiver : BroadcastReceiver() {

    @Inject lateinit var timers: TimerRepository

    @Inject lateinit var finishTimer: FinishTimerUseCase

    @Inject lateinit var restoreTimers: RestoreTimersUseCase

    @Inject lateinit var notifications: MiraNotifications

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() call: Hilt's Gradle plugin rewrites this class to extend its
    // generated base and injects that call into the bytecode itself. Writing it here would
    // resolve against BroadcastReceiver's abstract member and fail to compile.
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_FIRE && action != ACTION_RESTORE) return
        val timerId = intent.getLongExtra(EXTRA_TIMER_ID, 0L)
        if (action == ACTION_FIRE && timerId <= 0L) return
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()

        // The application context, because the work outlives onReceive under goAsync and the
        // receiver's own context is only good for the duration of the call.
        val appContext = context.applicationContext

        val power = context.getSystemService(PowerManager::class.java)
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aida:timer_receiver")?.apply {
            acquire(30_000L)
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                val language = unpromptedLanguage(settings.current().languageMode)
                when (action) {
                    ACTION_FIRE -> end(appContext, timerId, label, language)
                    else -> restore(appContext, language)
                }
            } finally {
                pending.finish()
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            }
        }
    }

    /**
     * The announcement goes out before the row is settled. Marking the timer finished is
     * bookkeeping Abu can catch up on; the countdown ending is the thing he asked for.
     */
    private suspend fun end(context: Context, timerId: Long, label: String, language: SpokenLanguage) {
        when (val found = timers.byId(timerId)) {
            is AppResult.Success -> {
                if (found.value.state != TimerState.RUNNING) return
                notifications.showTimerFinished(timerId, found.value.label, ranOutWhileOff = false, language = language)
                finishTimer(timerId)
            }
            is AppResult.Failure -> {
                if (found.error.code != MiraErrorCode.STORAGE) return
                notifications.showTimerFinished(timerId, label, ranOutWhileOff = false, language = language)
            }
        }
    }

    /**
     * A reboot drops every alarm the OS was holding. Whatever ran out while the phone was off is
     * already finished by the time this reports it, and each one is announced on its own so that
     * two timers Abu missed are two lines rather than one that mentions neither.
     */
    private suspend fun restore(context: Context, language: SpokenLanguage) {
        val ranOut = restoreTimers().getOrNull() ?: return
        for (timer in ranOut) {
            notifications.showTimerFinished(timer.id, timer.label, ranOutWhileOff = true, language = language)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.rayluxeo.aida.action.TIMER_FIRE"

        /**
         * Sent from inside Mira after a reboot. The receiver stays unexported and the alarms are
         * named by component, so nothing outside the app can end a timer or fabricate this replay.
         */
        const val ACTION_RESTORE = "com.rayluxeo.aida.action.TIMER_RESTORE"
        const val EXTRA_TIMER_ID = "com.rayluxeo.aida.extra.TIMER_ID"
        const val EXTRA_LABEL = "com.rayluxeo.aida.extra.TIMER_LABEL"

        /** What the boot receiver sends to have the countdowns put back. */
        fun restoreIntent(context: Context): Intent =
            Intent(context, TimerReceiver::class.java).setAction(ACTION_RESTORE)
    }
}
