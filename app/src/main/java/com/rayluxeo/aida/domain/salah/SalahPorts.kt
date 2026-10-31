package com.rayluxeo.aida.domain.salah

import com.rayluxeo.aida.core.common.AppResult
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/**
 * Abu's one Salah configuration — his place and how prayer times are worked out for it. A single
 * record; [save] replaces it whole. This is the settings-style straight-through exception (§4), so a
 * view model may write a toggle through it, but anything that must also re-arm the alarms goes
 * through a use case instead.
 */
interface SalahSettingsRepository {
    val settings: Flow<SalahSettings>
    suspend fun current(): SalahSettings
    suspend fun save(settings: SalahSettings): AppResult<SalahSettings>
}

/**
 * The prayer log — what Abu prayed and when. Append-and-correct: a status can be set and reset, but
 * the record of when it was marked prayed is kept. Tahajjud is logged here too, apart from the five.
 */
interface SalahTrackingRepository {
    fun observeDay(date: LocalDate): Flow<SalahDayTracking>
    fun observeMonth(month: YearMonth): Flow<List<SalahDayTracking>>
    suspend fun daysBetween(from: LocalDate, to: LocalDate): AppResult<List<SalahDayTracking>>
    suspend fun tahajjudBetween(from: LocalDate, to: LocalDate): AppResult<List<TahajjudTracking>>

    suspend fun setStatus(
        date: LocalDate,
        prayer: Prayer,
        status: PrayerStatus,
        scheduledAt: Instant?,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ): AppResult<Unit>

    suspend fun setTahajjud(
        date: LocalDate,
        status: TahajjudStatus,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ): AppResult<Unit>

    /** Erase every tracking entry — the strong "Reset Salah Tracking" action, behind a confirmation. */
    suspend fun clearAll(): AppResult<Unit>
}

/**
 * Arms the day's prayer-time, check-in and Tahajjud alarms with the platform's exact scheduler, and
 * cancels them. The implementation re-arms each day and after a reboot or a timezone change, and
 * never double-schedules (§ the scheduling contract). No WorkManager — exact AlarmManager, like the
 * reminder and alarm-clock schedulers.
 */
interface PrayerScheduler {
    /** Recompute today's times from the saved settings and arm every alarm Abu has enabled. */
    suspend fun reschedule(): AppResult<Unit>

    /** Cancel every prayer alarm — used when Abu turns prayer notifications off or resets. */
    suspend fun cancelAll(): AppResult<Unit>
}
