package com.rayluxeo.aida.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerScheduler
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Where a Salah alarm lands. Four things arrive here, by action:
 *
 * - [ACTION_PRAYER_TIME] — a prayer's time has come, so Mira announces it;
 * - [ACTION_CHECK_IN] — some minutes after the time, she asks whether he prayed;
 * - [ACTION_TAHAJJUD] — the night window Abu asked to be woken for has begun;
 * - [ACTION_ROLLOVER] — the day has turned, so the whole schedule is laid down afresh.
 *
 * Everything runs under [goAsync], which keeps the process alive long enough for the suspend calls.
 * The prayer and the day travel in the extras, so a prayer can still be announced on a phone whose
 * database will not open at the moment it fires — the words are chosen by [MiraNotifications], the
 * one layer that has already read Abu's language.
 */
@AndroidEntryPoint
class PrayerReceiver : BroadcastReceiver() {

    @Inject lateinit var notifications: MiraNotifications

    @Inject lateinit var scheduler: PrayerScheduler

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() call: Hilt's Gradle plugin rewrites this class to extend its
    // generated base and injects that call into the bytecode itself. Writing it here would
    // resolve against BroadcastReceiver's abstract member and fail to compile.
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_PRAYER_TIME &&
            action != ACTION_CHECK_IN &&
            action != ACTION_TAHAJJUD &&
            action != ACTION_ROLLOVER
        ) {
            return
        }
        val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { name ->
            runCatching { Prayer.valueOf(name) }.getOrNull()
        }
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, NO_DAY)
        val windowLabel = intent.getStringExtra(EXTRA_TAHAJJUD_WINDOW).orEmpty()

        val power = context.getSystemService(PowerManager::class.java)
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aida:prayer_receiver")?.apply {
            acquire(30_000L)
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                val language = unpromptedLanguage(settings.current().languageMode)
                when (action) {
                    ACTION_PRAYER_TIME ->
                        prayer?.let { notifications.showPrayerTime(it, language) }

                    ACTION_CHECK_IN ->
                        if (prayer != null && epochDay != NO_DAY) {
                            notifications.showPrayerCheckIn(prayer, LocalDate.ofEpochDay(epochDay), language)
                        }

                    ACTION_TAHAJJUD ->
                        notifications.showTahajjud(windowLabel, language)

                    // The day has turned: recompute and re-arm, which is what carries the schedule
                    // forward without the app ever being opened.
                    else -> scheduler.reschedule()
                }
            } finally {
                pending.finish()
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            }
        }
    }

    companion object {
        const val ACTION_PRAYER_TIME = "com.rayluxeo.aida.action.PRAYER_TIME"
        const val ACTION_CHECK_IN = "com.rayluxeo.aida.action.PRAYER_CHECK_IN"
        const val ACTION_TAHAJJUD = "com.rayluxeo.aida.action.PRAYER_TAHAJJUD"
        const val ACTION_ROLLOVER = "com.rayluxeo.aida.action.PRAYER_ROLLOVER"

        const val EXTRA_PRAYER = "com.rayluxeo.aida.extra.PRAYER"
        const val EXTRA_EPOCH_DAY = "com.rayluxeo.aida.extra.PRAYER_EPOCH_DAY"
        const val EXTRA_TAHAJJUD_WINDOW = "com.rayluxeo.aida.extra.PRAYER_TAHAJJUD_WINDOW"

        private const val NO_DAY = Long.MIN_VALUE
    }
}
