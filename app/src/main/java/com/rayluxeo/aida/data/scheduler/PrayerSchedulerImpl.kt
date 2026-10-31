package com.rayluxeo.aida.data.scheduler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.salah.PrayerScheduler
import com.rayluxeo.aida.domain.salah.PrayerTimeCalculator
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.TahajjudReminderMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Lays the day's prayer alarms down with the platform's exact scheduler, and takes them away.
 *
 * It computes rather than stores: [reschedule] reads Abu's saved settings, works out today's times
 * for his saved place and arms every alarm he has enabled, then arms one more — a rollover at the
 * start of tomorrow that runs [reschedule] again. That is what carries the feature from one day to
 * the next without the app being opened, and what [BootReceiver] replays after a reboot or a
 * timezone change. The rollover, the times and every request code are fixed per kind (see
 * [PrayerCodes]), so re-arming replaces the OS's alarm rather than stacking a second one.
 *
 * The date is taken in the location's *saved* zone, not the device's. A traveller whose phone has
 * moved on to another timezone still prays by the place he set, so the times are computed there and
 * the rollover falls at that place's midnight.
 *
 * No Gemini, no network, no WorkManager — the calculator is pure on-device astronomy (§2, §3) and
 * the alarms are plain exact `AlarmManager`, exactly like the reminder and alarm-clock schedulers.
 */
@Singleton
class PrayerSchedulerImpl @Inject constructor(
    private val settingsRepo: SalahSettingsRepository,
    private val calculator: PrayerTimeCalculator,
    private val alarms: PrayerAlarms,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : PrayerScheduler {

    /**
     * With no place set there is nothing to compute from, so every alarm is cancelled and the call
     * succeeds — the OS then holds nothing, which is exactly what an unconfigured Salah asks for.
     *
     * A prayer whose time has already passed today, or whose switch is off, has its own alarm
     * cancelled rather than left stale; the rollover re-arms tomorrow's in full. Each arming is
     * attempted before the first refusal is carried out, so one alarm the OS would not take cannot
     * cost Abu the rest of the day.
     */
    override suspend fun reschedule(): AppResult<Unit> = withContext(dispatchers.io) {
        val settings = settingsRepo.current()
        val location = settings.location
        if (location == null) {
            alarms.cancelAll()
            return@withContext AppResult.Success(Unit)
        }

        val zone = location.zoneId
        val now = clock.now()
        val today = LocalDate.ofInstant(now, zone)
        val tomorrow = today.plusDays(1)
        val todayTimes = calculator.calculate(today, zone, location.coordinates, settings.parameters)
        val tomorrowTimes = calculator.calculate(tomorrow, zone, location.coordinates, settings.parameters)
        val notify = settings.notifications

        var firstFailure: AppResult.Failure? = null
        fun remember(result: AppResult<Unit>) {
            if (result is AppResult.Failure && firstFailure == null) firstFailure = result
        }

        for ((prayer, moment) in todayTimes.obligatory) {
            val at = moment.toInstant()
            if (notify.notifyFor(prayer) && at.isAfter(now)) {
                remember(alarms.armPrayerTime(prayer, today, at))
            } else {
                alarms.cancelPrayerTime(prayer)
            }

            val checkInAt = moment.plusMinutes(notify.defaultAskDelayMinutes.toLong()).toInstant()
            if (notify.checkInFor(prayer) && checkInAt.isAfter(now)) {
                remember(alarms.armCheckIn(prayer, today, checkInAt))
            } else {
                alarms.cancelCheckIn(prayer)
            }
        }

        val tahajjud = settings.tahajjud
        if (tahajjud.reminderEnabled && tahajjud.mode != TahajjudReminderMode.DISABLED) {
            val window = calculator.tahajjudWindow(todayTimes.maghrib, tomorrowTimes.fajr)
            val trigger = TahajjudTrigger.at(window, tahajjud.mode, tahajjud.customMinutesIntoLastThird)
            val at = trigger?.toInstant()
            if (at != null && at.isAfter(now)) {
                remember(alarms.armTahajjud(today, at, window.nextFajr.format(WINDOW_LABEL)))
            } else {
                alarms.cancelTahajjud()
            }
        } else {
            alarms.cancelTahajjud()
        }

        // The daily re-arm, at the saved place's own midnight.
        remember(alarms.armRollover(tomorrow.atStartOfDay(zone).toInstant()))

        firstFailure ?: AppResult.Success(Unit)
    }

    override suspend fun cancelAll(): AppResult<Unit> = withContext(dispatchers.io) {
        alarms.cancelAll()
        AppResult.Success(Unit)
    }

    private companion object {
        /** Fajr as a 24-hour clock time; the wording turns the digits Bengali where it must. */
        val WINDOW_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
