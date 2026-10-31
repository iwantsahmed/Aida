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
 * Keeps the record-trigger check in WorkManager's queue, mirroring [ReminderWorkScheduler].
 *
 * Enqueued on every start with [ExistingPeriodicWorkPolicy.KEEP], so it is idempotent and survives a
 * reinstall without resetting its place in the schedule. Changing [CHECK_PERIOD] or the request shape
 * will not reach a phone that already holds the old one — that would need a new
 * [RecordTriggerWorker.UNIQUE_WORK_NAME]. No constraints: a rule that watches for an overdue invoice
 * must not wait for a charger.
 */
@Singleton
class RecordTriggerWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val clock: MiraClock,
) {

    fun ensureDailyCheck() {
        workManager.enqueueUniquePeriodicWork(
            RecordTriggerWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request(),
        )
    }

    private fun request(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<RecordTriggerWorker>(CHECK_PERIOD)
            .setInitialDelay(untilFirstCheck())
            .build()

    /**
     * Aimed at the hour Mira may first speak — the same waking hour the derived-reminder sweep aims
     * for, so the two daily passes land together. Periodic work is deferrable, so this is an aim, not
     * a guarantee; the ledger makes a late or doubled run harmless.
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
        /** Once a day: the facts it reads — a due date, a renewal — move a day at a time. */
        val CHECK_PERIOD: Duration = Duration.ofDays(1)
    }
}
