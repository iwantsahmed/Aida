package com.rayluxeo.aida.data.scheduler

import com.rayluxeo.aida.domain.scheduler.PlaceReminderScheduler
import com.rayluxeo.aida.domain.scheduler.AlarmClockScheduler
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.automation.AutomationAlarmScheduler
import com.rayluxeo.aida.domain.automation.AutomationPlaceScheduler
import com.rayluxeo.aida.domain.brief.BriefScheduler
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.salah.PrayerScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AlarmManager forgets everything across a reboot, and an app update clears the app's alarms too.
 * The scheduled rows in the database are the durable record, so both events replay them.
 *
 * The two briefs are replayed from their stored schedule for the same reason: without this a
 * morning brief Abu switched on months ago stops at the first restart, and stops silently.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var reminders: ReminderRepository

    @Inject lateinit var briefs: BriefScheduler

    @Inject lateinit var alarms: AlarmClockScheduler

    @Inject lateinit var places: PlaceReminderScheduler

    @Inject lateinit var prayers: PrayerScheduler

    @Inject lateinit var automationAlarms: AutomationAlarmScheduler

    @Inject lateinit var automationPlaces: AutomationPlaceScheduler

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() call: Hilt's Gradle plugin rewrites this class to extend its
    // generated base and injects that call into the bytecode itself. Writing it here would
    // resolve against BroadcastReceiver's abstract member and fail to compile.
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED
        ) {
            return
        }
        val pending = goAsync()
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aida:boot")?.apply {
            acquire(30_000L)
        }
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                // A reboot drops every alarm the OS was holding and every proximity alert with it, so
                // all six of these are gone until something puts them back. Each is attempted
                // independently: a failure replaying one must not cost Abu the others, which is why
                // this is a loop of guarded calls rather than a chain.
                val restorations = listOf<suspend () -> Unit>(
                    { reminders.rescheduleAll() },
                    { briefs.rescheduleAll() },
                    { alarms.rescheduleAll() },
                    // Not a bare rescheduleAll: a timer that ran out while the phone was off has to be
                    // settled and announced, not silently dropped. The restore broadcast runs
                    // RestoreTimersUseCase, which finishes the expired ones and then re-arms the rest.
                    { context.applicationContext.sendBroadcast(TimerReceiver.restoreIntent(context.applicationContext)) },
                    { places.rewatchAll() },
                    // Recompute today's prayer times for the saved place and re-arm every prayer alarm,
                    // including the daily rollover that carries them forward from here.
                    { prayers.reschedule() },
                    // Time-based automations the OS dropped with everything else.
                    { automationAlarms.rescheduleAll() },
                    // Place automations: every proximity alert is gone after a reboot, like the reminder ones.
                    { automationPlaces.rewatchAll() },
                )
                for (restore in restorations) {
                    runCatching { restore() }
                }
            } finally {
                try {
                    wakeLock?.let { if (it.isHeld) it.release() }
                } catch (_: Exception) {}
                pending.finish()
            }
        }
    }
}
