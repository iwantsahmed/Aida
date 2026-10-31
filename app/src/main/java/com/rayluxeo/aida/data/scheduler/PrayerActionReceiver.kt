package com.rayluxeo.aida.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.TrackingSource
import com.rayluxeo.aida.domain.usecase.TrackPrayerUseCase
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What the three buttons on a prayer check-in actually do.
 *
 * A broadcast rather than an activity, because answering from the shade must not drag Mira open
 * over whatever Abu was doing. The work goes through [TrackPrayerUseCase], the same use case the
 * screens log through, and the day and prayer travel in the extras so a log recorded from the
 * notification needs nothing read back.
 *
 * - **Yes** logs the prayer as prayed.
 * - **Not yet** logs the gentle in-between [PrayerStatus.NOT_YET] — never [PrayerStatus.MISSED].
 *   A check-in is an offer to log, and declining to say "yes" is not an admission of failure
 *   (Abu's instruction); only he, or a whole day that has passed, ever turns a prayer to missed.
 * - **Remind me** arms a fresh check-in a short while on, so the question comes back later.
 *
 * The card comes down only once the action has landed, exactly as [NotificationActionReceiver]
 * does it: an answer that failed and one that worked look identical after the card is gone, and of
 * the two mistakes leaving it up is the one Abu can see and act on.
 */
@AndroidEntryPoint
class PrayerActionReceiver : BroadcastReceiver() {

    @Inject lateinit var trackPrayer: TrackPrayerUseCase

    @Inject lateinit var alarms: PrayerAlarms

    @Inject lateinit var salahSettings: SalahSettingsRepository

    @Inject lateinit var notifications: MiraNotifications

    @Inject lateinit var clock: MiraClock

    @Inject lateinit var dispatchers: DispatcherProvider

    // No super.onReceive() call: Hilt's Gradle plugin rewrites this class to extend its
    // generated base and injects that call into the bytecode itself. Writing it here would
    // resolve against BroadcastReceiver's abstract member and fail to compile.
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_YES && action != ACTION_NOT_YET && action != ACTION_REMIND) return

        val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { name ->
            runCatching { Prayer.valueOf(name) }.getOrNull()
        } ?: return
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, NO_DAY)
        if (epochDay == NO_DAY) return
        val date = LocalDate.ofEpochDay(epochDay)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_NOTIFICATION)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                val landed = when (action) {
                    ACTION_YES ->
                        trackPrayer(prayer, PrayerStatus.PRAYED, date, source = TrackingSource.CHECK_IN) is AppResult.Success

                    ACTION_NOT_YET ->
                        trackPrayer(prayer, PrayerStatus.NOT_YET, date, source = TrackingSource.CHECK_IN) is AppResult.Success

                    else -> remindLater(prayer, date)
                }
                if (landed && notificationId != NO_NOTIFICATION) notifications.cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Re-arms the check-in a short while on. The delay is the one Abu set for the first ask, read
     * from settings so the figure cannot drift from what the scheduler used. The fixed check-in
     * request code means this replaces the alarm that just fired rather than adding a second.
     */
    private suspend fun remindLater(prayer: Prayer, date: LocalDate): Boolean {
        val minutes = salahSettings.current().notifications.defaultAskDelayMinutes.toLong()
        val at = clock.now().plus(Duration.ofMinutes(minutes))
        return alarms.armCheckIn(prayer, date, at) is AppResult.Success
    }

    companion object {
        const val ACTION_YES = "com.rayluxeo.aida.action.PRAYER_CHECK_IN_YES"
        const val ACTION_NOT_YET = "com.rayluxeo.aida.action.PRAYER_CHECK_IN_NOT_YET"
        const val ACTION_REMIND = "com.rayluxeo.aida.action.PRAYER_CHECK_IN_REMIND"

        const val EXTRA_PRAYER = "com.rayluxeo.aida.extra.PRAYER_ACTION_PRAYER"
        const val EXTRA_EPOCH_DAY = "com.rayluxeo.aida.extra.PRAYER_ACTION_EPOCH_DAY"
        const val EXTRA_NOTIFICATION_ID = "com.rayluxeo.aida.extra.PRAYER_ACTION_NOTIFICATION_ID"

        private const val NO_DAY = Long.MIN_VALUE
        private const val NO_NOTIFICATION = 0
    }
}
