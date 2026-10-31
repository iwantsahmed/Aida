package com.rayluxeo.aida.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place the Salah alarms are armed and cancelled at the operating system.
 *
 * It is a helper of its own, rather than private to [PrayerSchedulerImpl], because two callers arm
 * a prayer alarm: the scheduler lays down the whole day, and [PrayerActionReceiver] re-arms a
 * single check-in when Abu taps "remind me". Keeping the `AlarmManager` code here means both reach
 * the OS the same way — same request codes (so a re-arm replaces rather than duplicates), same
 * exact-to-inexact fallback — and the receiver does not depend on the scheduler's concrete class.
 *
 * Every alarm is exact where the OS allows it and degrades to an inexact window where it does not,
 * exactly as [ReminderSchedulerImpl] does: a prayer time announced a minute late is a compromise;
 * one that never arrives is a broken promise. The grant is read through [ExactAlarmAccess] so one
 * answer serves the whole app, and the check-then-schedule race is caught as the ordinary outcome
 * it is rather than a crash.
 *
 * Every arm targets [PrayerReceiver] and carries the prayer and the day in its extras, so the
 * notification can still be posted on a phone whose database will not open at the moment it fires.
 */
@Singleton
class PrayerAlarms @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exactAlarms: ExactAlarmAccess,
) {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun armPrayerTime(prayer: Prayer, date: LocalDate, at: Instant): AppResult<Unit> =
        arm(PrayerCodes.prayerTimeRequestCode(prayer), fireIntent(PrayerReceiver.ACTION_PRAYER_TIME, prayer, date), at)

    fun armCheckIn(prayer: Prayer, date: LocalDate, at: Instant): AppResult<Unit> =
        arm(PrayerCodes.checkInRequestCode(prayer), fireIntent(PrayerReceiver.ACTION_CHECK_IN, prayer, date), at)

    /** [windowLabel] is Fajr in `HH:mm`, carried so the notification can say how long the window lasts. */
    fun armTahajjud(date: LocalDate, at: Instant, windowLabel: String): AppResult<Unit> {
        val intent = fireIntent(PrayerReceiver.ACTION_TAHAJJUD, Prayer.TAHAJJUD, date)
            .putExtra(PrayerReceiver.EXTRA_TAHAJJUD_WINDOW, windowLabel)
        return arm(PrayerCodes.tahajjudRequestCode(), intent, at)
    }

    /** The daily re-arm, so each new day's times are laid down without the app being opened. */
    fun armRollover(at: Instant): AppResult<Unit> =
        arm(PrayerCodes.rolloverRequestCode(), rolloverIntent(), at)

    fun cancelPrayerTime(prayer: Prayer) =
        drop(PrayerCodes.prayerTimeRequestCode(prayer), PrayerReceiver.ACTION_PRAYER_TIME)

    fun cancelCheckIn(prayer: Prayer) =
        drop(PrayerCodes.checkInRequestCode(prayer), PrayerReceiver.ACTION_CHECK_IN)

    fun cancelTahajjud() =
        drop(PrayerCodes.tahajjudRequestCode(), PrayerReceiver.ACTION_TAHAJJUD)

    /** Drops every Salah alarm, the rollover included — the whole feature going quiet. */
    fun cancelAll() {
        for (prayer in Prayer.OBLIGATORY) {
            cancelPrayerTime(prayer)
            cancelCheckIn(prayer)
        }
        cancelTahajjud()
        drop(PrayerCodes.rolloverRequestCode(), PrayerReceiver.ACTION_ROLLOVER)
    }

    private fun arm(requestCode: Int, intent: Intent, at: Instant): AppResult<Unit> {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerAt = at.toEpochMilli()
        if (exactAlarms.canScheduleExact()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return AppResult.Success(Unit)
            } catch (revoked: SecurityException) {
                return setWindow(triggerAt, pending, revoked)
            }
        }
        return setWindow(triggerAt, pending, null)
    }

    private fun setWindow(
        triggerAt: Long,
        pending: PendingIntent,
        revoked: SecurityException?,
    ): AppResult<Unit> = try {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        AppResult.Success(Unit)
    } catch (denied: SecurityException) {
        failure(MiraErrorCode.PERMISSION_REQUIRED, "Prayer alarm refused", revoked ?: denied)
    }

    /**
     * The extras are deliberately absent on a cancel: `PendingIntent` matching ignores them, and
     * rebuilding the bare intent with its action alone is what lets the cancel find an alarm armed
     * with a prayer and day it no longer carries here.
     */
    private fun drop(requestCode: Int, action: String) {
        val existing = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PrayerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarms.cancel(existing)
        existing.cancel()
    }

    private fun fireIntent(action: String, prayer: Prayer, date: LocalDate): Intent =
        Intent(context, PrayerReceiver::class.java)
            .setAction(action)
            .putExtra(PrayerReceiver.EXTRA_PRAYER, prayer.name)
            .putExtra(PrayerReceiver.EXTRA_EPOCH_DAY, date.toEpochDay())

    private fun rolloverIntent(): Intent =
        Intent(context, PrayerReceiver::class.java).setAction(PrayerReceiver.ACTION_ROLLOVER)

    private companion object {
        const val INEXACT_WINDOW_MILLIS = 60_000L
    }
}
