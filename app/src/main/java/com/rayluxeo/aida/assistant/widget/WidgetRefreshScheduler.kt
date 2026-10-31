package com.rayluxeo.aida.assistant.widget

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the "Mira — Today" glance current, the same way [com.rayluxeo.aida.data.work.ReminderWorkScheduler]
 * keeps the daily sweep alive.
 *
 * [ensurePeriodic] is called from `MiraApplication` on every start and is idempotent — one periodic
 * request under a single name, [ExistingPeriodicWorkPolicy.KEEP], so a hundred launches leave one.
 * KEEP also means the queue survives a reboot or reinstall on disk, so a placed glance keeps
 * refreshing without Abu opening anything. [refreshNow] is the prompt path: when Abu leaves the app
 * having changed something (completed a task, recorded a payment), the glance he returns to on the
 * home screen is right at once rather than at the next half-hour tick.
 *
 * No constraints — not charging, not unmetered. The glance reads Abu's own database; making it wait
 * for a charger would freeze the numbers exactly when he is using the phone and would want them live.
 */
@Singleton
class WidgetRefreshScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    fun ensurePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            WidgetRefreshWorker.UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_PERIOD).build(),
        )
    }

    fun refreshNow() {
        workManager.enqueueUniqueWork(
            WidgetRefreshWorker.UNIQUE_ONESHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
        )
    }

    /** Called when the last glance is removed, so an unused widget leaves no periodic work behind. */
    fun cancelPeriodic() {
        workManager.cancelUniqueWork(WidgetRefreshWorker.UNIQUE_PERIODIC_WORK_NAME)
    }

    private companion object {
        /** The floor WorkManager allows; the glance's facts move slowly, so there is no need to beat it. */
        val REFRESH_PERIOD: Duration = Duration.ofMinutes(30)
    }
}
