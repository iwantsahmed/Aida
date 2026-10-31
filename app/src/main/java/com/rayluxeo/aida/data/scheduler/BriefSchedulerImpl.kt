package com.rayluxeo.aida.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.brief.BriefKind
import com.rayluxeo.aida.domain.brief.BriefSchedule
import com.rayluxeo.aida.domain.brief.BriefScheduleRepository
import com.rayluxeo.aida.domain.brief.BriefScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Arms Mira's two daily wake-ups with `setAndAllowWhileIdle`, never an exact alarm.
 *
 * `ReminderSchedulerImpl` asks for exactness and degrades when the grant is withheld, because a
 * reminder that arrives late is a promise half kept. A brief makes the opposite trade: nothing
 * turns on it landing at 08:00:00 rather than 08:04, so it never asks for `SCHEDULE_EXACT_ALARM`
 * at all and keeps working on a phone where that permission has been refused.
 *
 * `setAndAllowWhileIdle` is the one inexact call that still fires in Doze, which is the state the
 * phone is actually in at eight in the morning.
 */
@Singleton
class BriefSchedulerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val schedules: BriefScheduleRepository,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : BriefScheduler {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Both briefs are settled on every call — armed when on, cancelled when off — so applying a
     * schedule twice leaves the same two alarms rather than four, and switching one off actually
     * takes it down.
     */
    override suspend fun apply(schedule: BriefSchedule): AppResult<Unit> = withContext(dispatchers.io) {
        val outcomes = listOf(
            settle(BriefKind.MORNING, schedule.morningEnabled, schedule.morningAt),
            settle(BriefKind.EVENING, schedule.eveningEnabled, schedule.eveningAt),
        )
        // Both are attempted before the first failure is reported: a morning brief the platform
        // refused must not leave the evening one unarmed as well.
        outcomes.firstOrNull { it is AppResult.Failure } ?: AppResult.Success(Unit)
    }

    override suspend fun cancel(kind: BriefKind): AppResult<Unit> = withContext(dispatchers.io) {
        disarm(kind)
    }

    override suspend fun rescheduleAll(): AppResult<Unit> = apply(schedules.current())

    private fun settle(kind: BriefKind, enabled: Boolean, at: LocalTime): AppResult<Unit> =
        if (enabled) arm(kind, at) else disarm(kind)

    private fun arm(kind: BriefKind, at: LocalTime): AppResult<Unit> {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(kind),
            fireIntent(kind),
            // UPDATE_CURRENT so that re-applying a changed time replaces the alarm's extras on
            // the one PendingIntent this kind owns, rather than leaving a second one behind.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerAt = BriefOccurrence.next(at, clock.zone(), clock.now()).toEpochMilli()
        return try {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            AppResult.Success(Unit)
        } catch (denied: SecurityException) {
            failure(MiraErrorCode.PERMISSION_REQUIRED, "Brief alarm refused for $kind", denied)
        }
    }

    private fun disarm(kind: BriefKind): AppResult<Unit> {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(kind),
            fireIntent(kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return AppResult.Success(Unit)
        alarms.cancel(pending)
        pending.cancel()
        return AppResult.Success(Unit)
    }

    private fun fireIntent(kind: BriefKind): Intent =
        Intent(context, BriefReceiver::class.java)
            .setAction(BriefReceiver.ACTION_FIRE)
            .putExtra(BriefReceiver.EXTRA_KIND, kind.name)

    /**
     * Negative on purpose. Reminders key their alarms on the row id, and Room only ever hands out
     * positive ids, so these two can never collide with a reminder however many Abu accumulates.
     */
    private fun requestCode(kind: BriefKind): Int = when (kind) {
        BriefKind.MORNING -> MORNING_REQUEST_CODE
        BriefKind.EVENING -> EVENING_REQUEST_CODE
    }

    private companion object {
        const val MORNING_REQUEST_CODE = -101
        const val EVENING_REQUEST_CODE = -102
    }
}

/**
 * When the next brief of the day is due.
 *
 * Pure arithmetic over `java.time`, and deliberately not a method on [BriefSchedulerImpl]: it is
 * the only part of the scheduler that can be wrong in a way no device test would catch — a brief
 * armed a day late, or one that fires twice at the moment Abu chooses it — so it lives where a
 * plain JUnit test can reach it without an `AlarmManager` anywhere in sight.
 */
internal object BriefOccurrence {

    /**
     * The first [at] strictly after [from], read in [zone].
     *
     * Strictly, because "now" is exactly the case that matters: the receiver re-arms the moment
     * the alarm fires, and an occurrence that merely equals [from] would arm the same instant
     * again and fire in a loop.
     *
     * Both candidates go through [ZoneId], so a time that does not exist on a spring-forward
     * morning moves to the far side of the gap instead of being invented, and one that happens
     * twice on an autumn morning takes the earlier of the two.
     */
    fun next(at: LocalTime, zone: ZoneId, from: Instant): Instant {
        val today = from.atZone(zone).toLocalDate()
        val todaysOccurrence = today.atTime(at).atZone(zone).toInstant()
        return if (todaysOccurrence.isAfter(from)) {
            todaysOccurrence
        } else {
            today.plusDays(1).atTime(at).atZone(zone).toInstant()
        }
    }
}
