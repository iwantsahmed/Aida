package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.location.CurrentPlaceFinder
import com.rayluxeo.aida.domain.location.PlaceFix
import com.rayluxeo.aida.domain.salah.BuiltInCities
import com.rayluxeo.aida.domain.salah.CalculationMethod
import com.rayluxeo.aida.domain.salah.HijriCalendar
import com.rayluxeo.aida.domain.salah.MonthlySalahSummary
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerCity
import com.rayluxeo.aida.domain.salah.PrayerLocation
import com.rayluxeo.aida.domain.salah.PrayerScheduler
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.PrayerTimeCalculator
import com.rayluxeo.aida.domain.salah.SalahDay
import com.rayluxeo.aida.domain.salah.SalahSettings
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.SalahStatistics
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.domain.salah.TrackingSource
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Resolves one day's prayer times for Abu's saved place. Fails with [MiraErrorCode.NOT_FOUND] when
 * no location is set — the signal the screen turns into first-time setup rather than a wrong time.
 */
class GetSalahDayUseCase @Inject constructor(
    private val settingsRepo: SalahSettingsRepository,
    private val calculator: PrayerTimeCalculator,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(date: LocalDate = clock.today()): AppResult<SalahDay> {
        val settings = settingsRepo.current()
        val location = settings.location
            ?: return failure(MiraErrorCode.NOT_FOUND, "No prayer location set")
        val zone = location.zoneId
        val today = calculator.calculate(date, zone, location.coordinates, settings.parameters)
        val tomorrow = calculator.calculate(date.plusDays(1), zone, location.coordinates, settings.parameters)
        return SalahDay(
            date = date,
            location = location,
            times = today,
            tomorrowFajr = tomorrow.fajr,
            tahajjud = calculator.tahajjudWindow(today.maghrib, tomorrow.fajr),
            hijri = HijriCalendar.from(date),
        ).asSuccess()
    }
}

/**
 * A month of Salah, computed deterministically from the log. Fetches well before the month so a
 * current streak that started earlier is counted in full, while the month's own figures only count
 * days that have arrived.
 */
class GetMonthlySalahUseCase @Inject constructor(
    private val tracking: SalahTrackingRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(month: YearMonth = YearMonth.from(clock.today())): AppResult<MonthlySalahSummary> {
        val end = month.atEndOfMonth()
        val lookbackStart = month.atDay(1).minusDays(STREAK_LOOKBACK_DAYS)
        val days = tracking.daysBetween(lookbackStart, end).getOrNull().orEmpty()
        val tahajjud = tracking.tahajjudBetween(lookbackStart, end).getOrNull().orEmpty()
        return SalahStatistics.monthlySummary(month, days, tahajjud, clock.today()).asSuccess()
    }

    private companion object {
        const val STREAK_LOOKBACK_DAYS = 400L
    }
}

/**
 * Records where an obligatory prayer stands. The moment it is marked prayed is stamped from the
 * clock (§4.4); correcting a mistaken status simply writes the new one. [scheduledAt] links the log
 * to the prayer's computed time when the caller has it.
 */
class TrackPrayerUseCase @Inject constructor(
    private val tracking: SalahTrackingRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        prayer: Prayer,
        status: PrayerStatus,
        date: LocalDate = clock.today(),
        scheduledAt: Instant? = null,
        source: TrackingSource = TrackingSource.MANUAL,
    ): AppResult<Unit> {
        val prayedAt = if (status.isCompleted) clock.now() else null
        return tracking.setStatus(date, prayer, status, scheduledAt, prayedAt, source, null)
    }
}

/** Records a Tahajjud night. Voluntary, so only prayed or not — never missed. */
class TrackTahajjudUseCase @Inject constructor(
    private val tracking: SalahTrackingRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        status: TahajjudStatus,
        date: LocalDate = clock.today(),
        source: TrackingSource = TrackingSource.MANUAL,
    ): AppResult<Unit> {
        val prayedAt = if (status == TahajjudStatus.PRAYED) clock.now() else null
        return tracking.setTahajjud(date, status, prayedAt, source, null)
    }
}

/**
 * Saves the current place as the prayer location — one fix, on a tap, with the device's zone
 * captured now so the saved place keeps working offline and away from home. Never silently replaces
 * the saved location: the caller offers this, Abu accepts it. A missing grant or disabled radio
 * comes back as a failure the UI turns into the right settings screen.
 */
class SaveCurrentLocationUseCase @Inject constructor(
    private val placeFinder: CurrentPlaceFinder,
    private val settingsRepo: SalahSettingsRepository,
    private val scheduler: PrayerScheduler,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(cityLabel: String): AppResult<PrayerLocation> = when (val fix = placeFinder.here()) {
        is PlaceFix.Found -> {
            val location = PrayerLocation(
                latitude = fix.latitude,
                longitude = fix.longitude,
                cityLabel = cityLabel.trim(),
                zoneId = clock.zone(),
            )
            settingsRepo.save(settingsRepo.current().copy(location = location))
            scheduler.reschedule()
            location.asSuccess()
        }
        is PlaceFix.Unavailable -> failure(MiraErrorCode.PERMISSION_REQUIRED, "Location unavailable: ${fix.reason}")
    }
}

/**
 * Sets Abu's prayer location directly by city name or known city, without requiring any GPS or location tracking.
 */
class SetPrayerCityUseCase @Inject constructor(
    private val settingsRepo: SalahSettingsRepository,
    private val scheduler: PrayerScheduler,
) {
    suspend operator fun invoke(city: PrayerCity, useBengaliLabel: Boolean = false): AppResult<PrayerLocation> {
        val location = city.toPrayerLocation(useBengaliLabel)
        val currentSettings = settingsRepo.current()
        settingsRepo.save(currentSettings.copy(location = location))
        scheduler.reschedule()
        return location.asSuccess()
    }

    suspend operator fun invoke(cityNameOrQuery: String, useBengaliLabel: Boolean = false): AppResult<PrayerLocation> {
        val city = BuiltInCities.findByName(cityNameOrQuery) ?: BuiltInCities.DEFAULT
        return invoke(city, useBengaliLabel)
    }
}

/** Saves changed Salah settings and re-arms the alarms to match, in one step. */
class UpdateSalahSettingsUseCase @Inject constructor(
    private val settingsRepo: SalahSettingsRepository,
    private val scheduler: PrayerScheduler,
) {
    suspend operator fun invoke(settings: SalahSettings): AppResult<SalahSettings> {
        val saved = settingsRepo.save(settings)
        scheduler.reschedule()
        return saved
    }
}

/** Erases all Salah tracking — the "Reset Salah Tracking" action, behind a strong confirmation. */
class ResetSalahTrackingUseCase @Inject constructor(
    private val tracking: SalahTrackingRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = tracking.clearAll()
}
