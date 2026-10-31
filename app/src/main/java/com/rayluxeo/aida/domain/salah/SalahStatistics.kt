package com.rayluxeo.aida.domain.salah

import java.time.LocalDate
import java.time.YearMonth

/**
 * A month of Salah, worked out deterministically from the tracking records — the figures the month
 * view and the "how was my Salah" answers read. Nothing here judges: it counts completed, missed and
 * not-yet-recorded prayers and leaves the wording to the presentation layer (§4, Abu's instruction).
 *
 * [totalPossible] counts only days that have actually arrived, so a half-finished month is not
 * reported as mostly missed — the denominator grows with the month.
 */
data class MonthlySalahSummary(
    val month: YearMonth,
    val perPrayerCompleted: Map<Prayer, Int>,
    val totalCompleted: Int,
    val totalPossible: Int,
    val missedCount: Int,
    val daysAllFive: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val tahajjudNights: Int,
    val tahajjudCurrentStreak: Int,
) {
    /** 0–100, and 0 for a month that has not started rather than a misleading full mark. */
    val percentage: Int get() = if (totalPossible == 0) 0 else (totalCompleted * 100) / totalPossible
}

/**
 * The deterministic Salah arithmetic. Pure — it takes the day to reason about rather than reading a
 * clock, so a streak and a month-to-date percentage are both testable (§4.4). Tahajjud is counted
 * only in its own fields and never folded into the obligatory totals (Abu's instruction).
 */
object SalahStatistics {

    fun monthlySummary(
        month: YearMonth,
        days: List<SalahDayTracking>,
        tahajjud: List<TahajjudTracking>,
        today: LocalDate,
    ): MonthlySalahSummary {
        val byDate = days.associateBy { it.date }
        val lastCountedDay = minOf(month.atEndOfMonth(), today)
        val countedDays: List<LocalDate> =
            if (lastCountedDay.isBefore(month.atDay(1))) emptyList()
            else (1..lastCountedDay.dayOfMonth).map { month.atDay(it) }

        val perPrayer = Prayer.OBLIGATORY.associateWith { prayer ->
            countedDays.count { date -> byDate[date]?.statusOf(prayer)?.isCompleted == true }
        }
        val totalCompleted = perPrayer.values.sum()
        val totalPossible = countedDays.size * Prayer.OBLIGATORY.size
        val missed = countedDays.sumOf { date ->
            Prayer.OBLIGATORY.count { byDate[date]?.statusOf(it) == PrayerStatus.MISSED }
        }
        val daysAllFive = countedDays.count { byDate[it]?.isAllFive == true }

        return MonthlySalahSummary(
            month = month,
            perPrayerCompleted = perPrayer,
            totalCompleted = totalCompleted,
            totalPossible = totalPossible,
            missedCount = missed,
            daysAllFive = daysAllFive,
            currentStreak = currentStreak(byDate, today),
            bestStreak = bestStreak(byDate, countedDays),
            tahajjudNights = tahajjudNights(tahajjud, month, today),
            tahajjudCurrentStreak = tahajjudCurrentStreak(tahajjud, today),
        )
    }

    /**
     * Consecutive all-five days ending at [upTo]. A day still in progress does not break the run:
     * if today is not yet complete the count simply ends at yesterday, rather than resetting to zero.
     */
    fun currentStreak(byDate: Map<LocalDate, SalahDayTracking>, upTo: LocalDate): Int {
        var day = upTo
        if (byDate[day]?.isAllFive != true) day = day.minusDays(1)
        var streak = 0
        while (byDate[day]?.isAllFive == true) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** The longest run of consecutive all-five days among [within]. */
    fun bestStreak(byDate: Map<LocalDate, SalahDayTracking>, within: List<LocalDate>): Int {
        var best = 0
        var run = 0
        for (date in within.sorted()) {
            if (byDate[date]?.isAllFive == true) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
        }
        return best
    }

    /** Nights this month, up to [today], that Tahajjud was prayed. */
    fun tahajjudNights(tahajjud: List<TahajjudTracking>, month: YearMonth, today: LocalDate): Int =
        tahajjud.count {
            it.status == TahajjudStatus.PRAYED &&
                YearMonth.from(it.localDate) == month &&
                !it.localDate.isAfter(today)
        }

    /**
     * Consecutive nights up to [upTo] that Tahajjud was prayed. An *unlogged* most-recent night
     * (tonight, which has not happened yet) does not reset the streak — but a night explicitly
     * recorded as not prayed does, because that is a real break rather than a blank.
     */
    fun tahajjudCurrentStreak(tahajjud: List<TahajjudTracking>, upTo: LocalDate): Int {
        val byDate = tahajjud.associateBy { it.localDate }
        var day = upTo
        if (byDate[day] == null || byDate[day]?.status == TahajjudStatus.NOT_RECORDED) {
            day = day.minusDays(1)
        }
        var streak = 0
        while (byDate[day]?.status == TahajjudStatus.PRAYED) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
