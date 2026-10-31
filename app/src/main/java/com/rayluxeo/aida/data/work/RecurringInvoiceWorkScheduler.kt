package com.rayluxeo.aida.data.work

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.reminder.DerivedReminderRules
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the recurring-invoice sweep in WorkManager's queue, mirroring [RecordTriggerWorkScheduler].
 *
 * Enqueued on every start with [ExistingPeriodicWorkPolicy.KEEP], so it is idempotent and survives a
 * reinstall without resetting its place in the schedule. Changing [CHECK_PERIOD] or the request shape
 * will not reach a phone that already holds the old one — that would need a new
 * [RecurringInvoiceWorker.UNIQUE_WORK_NAME]. No constraints: a month's billing must not wait for a
 * charger.
 */
@Singleton
class RecurringInvoiceWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val clock: MiraClock,
) {

    fun ensureDailyCheck() {
        workManager.enqueueUniquePeriodicWork(
            RecurringInvoiceWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request(),
        )
    }

    private fun request(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<RecurringInvoiceWorker>(CHECK_PERIOD)
            .setInitialDelay(untilFirstCheck())
            .build()

    /**
     * Aimed at the waking hour the other daily sweeps aim for, so a recurring invoice's draft and the
     * reminder it raises are both waiting the morning they come due. Periodic work is deferrable, so
     * this is an aim, not a guarantee; a profile is only acted on once its next-run date has fallen
     * due, so a late or doubled run is harmless.
     */
    private fun untilFirstCheck(): Duration {
        val now = clock.now()
        val zone = clock.zone()
        val todaysCheck = now.atZone(zone).toLocalDate()
            .atTime(DerivedReminderRules.WAKING_FROM).atZone(zone).toInstant()
        val next = if (todaysCheck.isAfter(now)) {
            todaysCheck
        } else {
            now.atZone(zone).toLocalDate().plusDays(1)
                .atTime(DerivedReminderRules.WAKING_FROM).atZone(zone).toInstant()
        }
        return Duration.between(now, next)
    }

    private companion object {
        /** Once a day: a billing cycle is measured in days at the shortest. */
        val CHECK_PERIOD: Duration = Duration.ofDays(1)
    }
}
