package com.rayluxeo.aida.domain.salah

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

/** The month arithmetic and streaks — computed from the log, and never counting Tahajjud among the five. */
class SalahStatisticsTest {

    private fun day(date: LocalDate, completed: Set<Prayer>, missed: Set<Prayer> = emptySet()): SalahDayTracking {
        val entries = Prayer.OBLIGATORY.map { prayer ->
            val status = when {
                prayer in completed -> PrayerStatus.PRAYED
                prayer in missed -> PrayerStatus.MISSED
                else -> PrayerStatus.NOT_RECORDED
            }
            ObligatoryTracking(date, prayer, status)
        }
        return SalahDayTracking(date, entries, null)
    }

    private val march = YearMonth.of(2025, 3)
    private val allFive = Prayer.OBLIGATORY.toSet()

    @Test
    fun `monthly summary counts completed, missed and days-all-five up to today only`() {
        val days = listOf(
            day(march.atDay(1), allFive),
            day(march.atDay(2), allFive),
            day(march.atDay(3), setOf(Prayer.FAJR, Prayer.DHUHR), missed = setOf(Prayer.ASR)),
        )
        val summary = SalahStatistics.monthlySummary(march, days, emptyList(), today = march.atDay(3))

        assertThat(summary.totalPossible).isEqualTo(3 * 5) // three days have arrived
        assertThat(summary.totalCompleted).isEqualTo(5 + 5 + 2)
        assertThat(summary.missedCount).isEqualTo(1)
        assertThat(summary.daysAllFive).isEqualTo(2)
        assertThat(summary.perPrayerCompleted[Prayer.FAJR]).isEqualTo(3)
        assertThat(summary.perPrayerCompleted[Prayer.ASR]).isEqualTo(2)
        assertThat(summary.percentage).isEqualTo((12 * 100) / 15)
    }

    @Test
    fun `an in-progress today does not break the current streak`() {
        // Two complete days, then today only partly logged — streak should still be 2, not 0.
        val days = listOf(
            day(march.atDay(10), allFive),
            day(march.atDay(11), allFive),
            day(march.atDay(12), setOf(Prayer.FAJR)),
        )
        val byDate = days.associateBy { it.date }
        assertThat(SalahStatistics.currentStreak(byDate, upTo = march.atDay(12))).isEqualTo(2)
    }

    @Test
    fun `best streak is the longest run of all-five days`() {
        val within = (1..7).map { march.atDay(it) }
        val days = listOf(
            day(march.atDay(1), allFive),
            day(march.atDay(2), allFive),
            day(march.atDay(3), setOf(Prayer.FAJR)), // break
            day(march.atDay(4), allFive),
            day(march.atDay(5), allFive),
            day(march.atDay(6), allFive),
            day(march.atDay(7), allFive),
        )
        val byDate = days.associateBy { it.date }
        assertThat(SalahStatistics.bestStreak(byDate, within)).isEqualTo(4)
    }

    @Test
    fun `tahajjud is counted only in its own fields, never among the five`() {
        val days = listOf(day(march.atDay(1), allFive))
        val tahajjud = listOf(
            TahajjudTracking(march.atDay(1), TahajjudStatus.PRAYED),
            TahajjudTracking(march.atDay(2), TahajjudStatus.PRAYED),
            TahajjudTracking(march.atDay(3), TahajjudStatus.NOT_PRAYED),
        )
        val summary = SalahStatistics.monthlySummary(march, days, tahajjud, today = march.atDay(3))

        assertThat(summary.tahajjudNights).isEqualTo(2)
        // The obligatory total for one all-five day is 5 — Tahajjud did not add to it.
        assertThat(summary.totalCompleted).isEqualTo(5)
        assertThat(summary.tahajjudCurrentStreak).isEqualTo(0) // night 3 was not prayed
    }

    @Test
    fun `tahajjud current streak counts consecutive prayed nights ending recently`() {
        val tahajjud = listOf(
            TahajjudTracking(march.atDay(8), TahajjudStatus.PRAYED),
            TahajjudTracking(march.atDay(9), TahajjudStatus.PRAYED),
            TahajjudTracking(march.atDay(10), TahajjudStatus.PRAYED),
        )
        assertThat(SalahStatistics.tahajjudCurrentStreak(tahajjud, upTo = march.atDay(10))).isEqualTo(3)
    }
}
