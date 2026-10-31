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
 * Keeps the daily sweep in WorkManager's queue.
 *
 * Called from `MiraApplication` on every start, which is why it has to be idempotent: the work is
 * enqueued under one name with [ExistingPeriodicWorkPolicy.KEEP], so a hundred launches leave one
 * periodic request rather than a hundred. KEEP also means an already-queued sweep is never
 * disturbed — it keeps its place in the schedule instead of being pushed a whole period away every
 * time Abu opens Mira, which is what a replace would do. The cost of that trade is real and worth
 * writing down: changing [SWEEP_PERIOD] or the request's shape will not reach a phone that already
 * has the old one. Doing that needs a new [DerivedReminderWorker.UNIQUE_WORK_NAME].
 *
 * **No constraints.** Not charging, not unmetered, not battery-not-low. The sweep reads Abu's own
 * database and writes reminders; making it wait for a charger would mean the morning he most needs
 * telling about an overdue invoice is the morning Mira says nothing.
 */
@Singleton
class ReminderWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val clock: MiraClock,
) {

    fun ensureDailySweep() {
        workManager.enqueueUniquePeriodicWork(
            DerivedReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request(),
        )
    }

    private fun request(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<DerivedReminderWorker>(SWEEP_PERIOD)
            .setInitialDelay(untilFirstSweep())
            .build()

    /**
     * How long until the first sweep, aimed at the hour Mira may first speak.
     *
     * The aim is all it is. Periodic work is deferrable by definition and the OS may run it hours
     * late, or batch it with something else — which is exactly why the reminders it raises carry
     * their own alarms, and why [DerivedReminderRules] places them inside Abu's waking hours
     * rather than trusting this to have landed in the morning.
     */
    private fun untilFirstSweep(): Duration {
        val now = clock.now()
        val zone = clock.zone()
        val local = now.atZone(zone)
        val todaysSweep = local.toLocalDate().atTime(DerivedReminderRules.WAKING_FROM).atZone(zone).toInstant()
        val next = if (todaysSweep.isAfter(now)) {
            todaysSweep
        } else {
            local.toLocalDate().plusDays(1).atTime(DerivedReminderRules.WAKING_FROM).atZone(zone).toInstant()
        }
        return Duration.between(now, next)
    }

    private companion object {
        /**
         * Once a day. The facts the sweep reads — a due date, a renewal, a silence measured in
         * weeks — all move a day at a time, so looking more often would find the same answers and
         * cost battery for them.
         */
        val SWEEP_PERIOD: Duration = Duration.ofDays(1)
    }
}
