package com.rayluxeo.aida.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.usecase.AlarmRangUseCase
import com.rayluxeo.aida.domain.usecase.SnoozeElapsedUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Where an alarm clock lands.
 *
 * The sound comes from the notification channel rather than from anything this class plays.
 * Android 17 hardened background audio: playback started from a process that is not in the
 * foreground fails silently and an audio-focus request comes back refused, which is precisely the
 * situation a receiver woken at seven in the morning is in. A channel carrying
 * `AudioAttributes.USAGE_ALARM` is played by the system instead, so the ring does not depend on
 * Mira being allowed to make a noise.
 *
 * The label travels in the intent extras and the notification goes out before Room is asked for
 * anything, for the same reason [ReminderReceiver] does it that way: a database that will not open
 * must not be able to stop an alarm ringing.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRang: AlarmRangUseCase

    @Inject lateinit var snoozeElapsed: SnoozeElapsedUseCase

    @Inject lateinit var dispatchers: DispatcherProvider

    @Inject lateinit var notifications: MiraNotifications

    // No super.onReceive() call: Hilt's Gradle plugin rewrites this class to extend its
    // generated base and injects that call into the bytecode itself. Writing it here would
    // resolve against BroadcastReceiver's abstract member and fail to compile.
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_FIRE && action != ACTION_SNOOZE_ELAPSED) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        if (alarmId <= 0L) return

        // An alarm Abu never named travels as an empty label on purpose: ReminderNotificationWording
        // has a word for a nameless alarm in each language, and substituting anything here — the
        // app's own name included — would put that word out of reach and title the alarm "Mira".
        val label = intent.getStringExtra(EXTRA_LABEL)?.trim().orEmpty()

        val power = context.getSystemService(PowerManager::class.java)
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aida:alarm_receiver")?.apply {
            acquire(30_000L)
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                // MiraNotifications.showAlarm, not a notification built here: that one carries the
                // Snooze and Dismiss actions, names its channel in Abu's language, and rings on
                // the alarm stream. A second copy beside it drifted from all three.
                notifications.showAlarm(alarmId, label)
                // A repeating alarm arms its next day here, a one-time one switches itself off,
                // and a snooze that has run out only gives up the snooze.
                if (action == ACTION_FIRE) alarmRang(alarmId) else snoozeElapsed(alarmId)
            } finally {
                pending.finish()
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.rayluxeo.aida.action.ALARM_FIRE"
        const val ACTION_SNOOZE_ELAPSED = "com.rayluxeo.aida.action.ALARM_SNOOZE_ELAPSED"
        const val EXTRA_ALARM_ID = "com.rayluxeo.aida.extra.ALARM_ID"
        const val EXTRA_LABEL = "com.rayluxeo.aida.extra.ALARM_LABEL"
    }
}
