package com.rayluxeo.aida.domain.command.handler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.SalahClock
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import com.rayluxeo.aida.domain.salah.TahajjudReminderMode
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.domain.salah.TrackingSource
import com.rayluxeo.aida.domain.usecase.GetMonthlySalahUseCase
import com.rayluxeo.aida.domain.usecase.GetSalahDayUseCase
import com.rayluxeo.aida.domain.usecase.TrackPrayerUseCase
import com.rayluxeo.aida.domain.usecase.TrackTahajjudUseCase
import com.rayluxeo.aida.domain.usecase.UpdateSalahSettingsUseCase
import java.time.YearMonth
import javax.inject.Inject

/**
 * Prayer times, prayer tracking and the Tahajjud reminder.
 *
 * Everything here is deterministic and on-device: the times come from [GetSalahDayUseCase]'s
 * astronomical calculation and the counts from [GetMonthlySalahUseCase]'s arithmetic over the log.
 * Nothing is asked of a language model — a prayer time or a streak is a fact, not a guess, and
 * must be identical with the radio off (AGENTS §2, §3).
 *
 * A query that needs a place Abu has not set comes back as the use case's own
 * [com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND] failure, which the screen turns into
 * first-time setup rather than a wrong time.
 */
class DefaultSalahCommandHandler @Inject constructor(
    private val getSalahDay: GetSalahDayUseCase,
    private val getMonthlySalah: GetMonthlySalahUseCase,
    private val trackPrayer: TrackPrayerUseCase,
    private val trackTahajjud: TrackTahajjudUseCase,
    private val updateSettings: UpdateSalahSettingsUseCase,
    private val settingsRepo: SalahSettingsRepository,
    private val tracking: SalahTrackingRepository,
    private val clock: MiraClock,
) : SalahCommandHandler {

    override suspend fun markPrayerPrayed(command: AppCommand.MarkPrayerPrayed): AppResult<MiraResponse> {
        val date = command.onDate ?: clock.today()
        val tracked = if (command.prayer == Prayer.TAHAJJUD) {
            // Tahajjud is voluntary and logged apart from the five; it has no PrayerStatus.
            trackTahajjud(TahajjudStatus.PRAYED, date, TrackingSource.VOICE)
        } else {
            // Link the log to the prayer's computed moment when a place is set; best-effort, so a
            // missing location never blocks the record itself.
            val scheduledAt = getSalahDay(date).getOrNull()?.times?.time(command.prayer)?.toInstant()
            trackPrayer(command.prayer, PrayerStatus.PRAYED, date, scheduledAt, TrackingSource.VOICE)
        }
        return tracked.map { MiraResponse.PrayerMarked(command.prayer, command.onDate) }
    }

    override suspend fun queryNextPrayer(): AppResult<MiraResponse> {
        val day = when (val fetched = getSalahDay()) {
            is AppResult.Failure -> return fetched
            is AppResult.Success -> fetched.value
        }
        val info = SalahClock.nextPrayer(clock.now(), day.times, day.tomorrowFajr)
        return AppResult.Success(
            MiraResponse.NextPrayerReport(info.next, info.nextTime.toInstant(), info.remaining),
        )
    }

    override suspend fun queryPrayerTimeRemaining(
        command: AppCommand.QueryPrayerTimeRemaining,
    ): AppResult<MiraResponse> {
        val day = when (val fetched = getSalahDay()) {
            is AppResult.Failure -> return fetched
            is AppResult.Success -> fetched.value
        }
        val prayer = command.prayer
            ?: return AppResult.Success(
                SalahClock.nextPrayer(clock.now(), day.times, day.tomorrowFajr).let {
                    MiraResponse.PrayerTimeRemainingReport(it.next, it.nextTime.toInstant(), it.remaining)
                },
            )
        // Tahajjud has no fixed time; its window opens at the last third of the night.
        val time = day.times.time(prayer) ?: day.tahajjud.lastThirdStart
        return AppResult.Success(
            MiraResponse.PrayerTimeRemainingReport(
                prayer = prayer,
                at = time.toInstant(),
                remaining = SalahClock.remainingUntil(clock.now(), time),
            ),
        )
    }

    override suspend fun queryPrayerTime(command: AppCommand.QueryPrayerTime): AppResult<MiraResponse> {
        val date = command.onDate ?: clock.today()
        val day = when (val fetched = getSalahDay(date)) {
            is AppResult.Failure -> return fetched
            is AppResult.Success -> fetched.value
        }
        val time = day.times.time(command.prayer) ?: day.tahajjud.lastThirdStart
        return AppResult.Success(
            MiraResponse.PrayerTimeReport(command.prayer, time.toInstant(), command.onDate),
        )
    }

    override suspend fun queryPrayerTracking(
        command: AppCommand.QueryPrayerTracking,
    ): AppResult<MiraResponse> {
        val from = command.from
        val to = command.to
        // A single-day window is read back as a day's count of the five rather than a month's
        // totals, which is what [MiraResponse.PrayerTrackingReport.perPrayer] being null signals.
        if (from != null && to == from.plusDays(1)) {
            val day = tracking.daysBetween(from, from).getOrNull()?.firstOrNull { it.date == from }
            return AppResult.Success(
                MiraResponse.PrayerTrackingReport(
                    from = from,
                    to = to,
                    completed = day?.completedCount ?: 0,
                    possible = Prayer.OBLIGATORY.size,
                    daysAllFive = if (day?.isAllFive == true) 1 else 0,
                    tahajjudNights = if (day?.tahajjud?.status == TahajjudStatus.PRAYED) 1 else 0,
                    perPrayer = null,
                ),
            )
        }
        val month = from?.let { YearMonth.from(it) } ?: YearMonth.from(clock.today())
        val summary = when (val fetched = getMonthlySalah(month)) {
            is AppResult.Failure -> return fetched
            is AppResult.Success -> fetched.value
        }
        return AppResult.Success(
            MiraResponse.PrayerTrackingReport(
                from = from ?: month.atDay(1),
                to = to ?: month.plusMonths(1).atDay(1),
                completed = summary.totalCompleted,
                possible = summary.totalPossible,
                daysAllFive = summary.daysAllFive,
                tahajjudNights = summary.tahajjudNights,
                perPrayer = summary.perPrayerCompleted,
            ),
        )
    }

    override suspend fun setTahajjudReminder(): AppResult<MiraResponse> {
        val current = settingsRepo.current()
        val updated = current.copy(
            tahajjud = current.tahajjud.copy(
                reminderEnabled = true,
                mode = TahajjudReminderMode.LAST_THIRD,
            ),
        )
        return when (val saved = updateSettings(updated)) {
            is AppResult.Failure -> saved
            is AppResult.Success -> {
                // Tonight's last-third start, when a place is set; null otherwise, and the
                // renderer then confirms the reminder without naming a minute it does not have.
                val at = getSalahDay().getOrNull()?.tahajjud?.lastThirdStart?.toInstant()
                AppResult.Success(MiraResponse.TahajjudReminderSet(at))
            }
        }
    }
}
