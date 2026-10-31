package com.rayluxeo.aida.domain.salah

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/** The next-prayer and countdown logic, pinned with hand-built times so no astronomy is involved. */
class SalahClockTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val date: LocalDate = LocalDate.of(2025, 3, 21)

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), zone)

    private val today = PrayerTimes(
        date = date,
        zone = zone,
        fajr = at(5, 0),
        sunrise = at(6, 0),
        dhuhr = at(12, 0),
        asr = at(15, 30),
        maghrib = at(18, 0),
        isha = at(19, 30),
    )
    private val tomorrowFajr = at(5, 0).plusDays(1)

    @Test
    fun `before Fajr the next prayer is Fajr and the night still belongs to Isha`() {
        val info = SalahClock.nextPrayer(at(4, 0).toInstant(), today, tomorrowFajr)

        assertThat(info.next).isEqualTo(Prayer.FAJR)
        assertThat(info.current).isEqualTo(Prayer.ISHA)
        assertThat(info.nextTime).isEqualTo(today.fajr)
        assertThat(info.remaining).isEqualTo(Duration.ofHours(1))
    }

    @Test
    fun `between Dhuhr and Asr the next prayer is Asr`() {
        val info = SalahClock.nextPrayer(at(13, 0).toInstant(), today, tomorrowFajr)

        assertThat(info.next).isEqualTo(Prayer.ASR)
        assertThat(info.current).isEqualTo(Prayer.DHUHR)
        assertThat(info.remaining).isEqualTo(Duration.ofHours(2).plusMinutes(30))
    }

    @Test
    fun `after Isha the next prayer is tomorrow's Fajr`() {
        val info = SalahClock.nextPrayer(at(20, 0).toInstant(), today, tomorrowFajr)

        assertThat(info.next).isEqualTo(Prayer.FAJR)
        assertThat(info.current).isEqualTo(Prayer.ISHA)
        assertThat(info.nextTime).isEqualTo(tomorrowFajr)
        assertThat(info.remaining).isEqualTo(Duration.ofHours(9))
    }

    @Test
    fun `a just-passed prayer reads as zero remaining, never negative`() {
        val remaining = SalahClock.remainingUntil(at(12, 1).toInstant(), today.dhuhr)
        assertThat(remaining).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `at the exact prayer minute that prayer is already current, not next`() {
        // One second after Dhuhr: Dhuhr is current, Asr is next.
        val info = SalahClock.nextPrayer(today.dhuhr.toInstant().plusSeconds(1), today, tomorrowFajr)
        assertThat(info.current).isEqualTo(Prayer.DHUHR)
        assertThat(info.next).isEqualTo(Prayer.ASR)
    }
}
