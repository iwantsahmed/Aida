package com.rayluxeo.aida.domain.alarm

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * March 2026 is the reference month across this project's tests: the 9th is a Monday. Dhaka is
 * Abu's own zone and never shifts, so the daylight-saving cases use New York — the alarm column is
 * a time of day precisely so that 07:00 is still 07:00 on a morning that is 23 hours after the
 * last one, and that is only provable in a zone that does it.
 */
class NextAlarmTimeTest {

    @Test
    fun `an alarm with no repeat days rings later today when the time is still ahead`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = emptySet(),
            zone = DHAKA,
            after = dhaka("2026-03-10T06:00"),
        )

        assertThat(next).isEqualTo(dhaka("2026-03-10T07:00"))
    }

    @Test
    fun `an alarm with no repeat days rolls to tomorrow once its time has gone`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = emptySet(),
            zone = DHAKA,
            after = dhaka("2026-03-10T08:00"),
        )

        assertThat(next).isEqualTo(dhaka("2026-03-11T07:00"))
    }

    @Test
    fun `the minute that is ticking now counts as passed`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = emptySet(),
            zone = DHAKA,
            after = dhaka("2026-03-10T07:00"),
        )

        assertThat(next).isEqualTo(dhaka("2026-03-11T07:00"))
    }

    @Test
    fun `one second before its time the alarm still belongs to today`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = emptySet(),
            zone = DHAKA,
            after = dhaka("2026-03-10T06:59:59"),
        )

        assertThat(next).isEqualTo(dhaka("2026-03-10T07:00"))
    }

    @Test
    fun `repeat days pick the next of those weekdays`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            zone = DHAKA,
            after = dhaka("2026-03-09T12:00"),
        )

        assertThat(next).isEqualTo(dhaka("2026-03-10T07:00"))
    }

    @Test
    fun `a repeat day whose time has gone moves to the next listed day`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            zone = DHAKA,
            after = dhaka("2026-03-10T08:00"),
        )

        assertThat(next).isEqualTo(dhaka("2026-03-11T07:00"))
    }

    @Test
    fun `a single repeat day that has passed waits a whole week`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = setOf(DayOfWeek.MONDAY),
            zone = DHAKA,
            after = dhaka("2026-03-09T07:00"),
        )

        assertThat(next).isEqualTo(dhaka("2026-03-16T07:00"))
    }

    @Test
    fun `a weekday alarm skips the weekend`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = WEEKDAYS,
            zone = DHAKA,
            after = dhaka("2026-03-13T08:00"),
        )

        assertThat(next).isEqualTo(dhaka("2026-03-16T07:00"))
    }

    @Test
    fun `all seven repeat days behave like every day`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = DayOfWeek.entries.toSet(),
            zone = DHAKA,
            after = dhaka("2026-03-10T08:00"),
        )

        assertThat(next).isEqualTo(dhaka("2026-03-11T07:00"))
    }

    @Test
    fun `seven o'clock stays seven o'clock across the spring transition`() {
        val from = newYork("2026-03-07T07:00:01")

        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = emptySet(),
            zone = NEW_YORK,
            after = from,
        )

        assertThat(ZonedDateTime.ofInstant(next, NEW_YORK).toLocalTime()).isEqualTo(LocalTime.of(7, 0))
        assertThat(ZonedDateTime.ofInstant(next, NEW_YORK).toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 8))
        // The day the clocks go forward is 23 hours long, and the alarm follows the clock.
        assertThat(Duration.between(from, next)).isEqualTo(Duration.ofHours(23).minusSeconds(1))
    }

    @Test
    fun `seven o'clock stays seven o'clock across the autumn transition`() {
        val from = newYork("2026-10-31T07:00:01")

        val next = NextAlarmTime.after(
            at = LocalTime.of(7, 0),
            repeatDays = emptySet(),
            zone = NEW_YORK,
            after = from,
        )

        assertThat(ZonedDateTime.ofInstant(next, NEW_YORK).toLocalTime()).isEqualTo(LocalTime.of(7, 0))
        assertThat(ZonedDateTime.ofInstant(next, NEW_YORK).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(Duration.between(from, next)).isEqualTo(Duration.ofHours(25).minusSeconds(1))
    }

    @Test
    fun `an alarm set inside the hour the clocks skip rings at the end of the gap`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(2, 30),
            repeatDays = emptySet(),
            zone = NEW_YORK,
            after = newYork("2026-03-07T12:00"),
        )

        val local = ZonedDateTime.ofInstant(next, NEW_YORK)
        assertThat(local.toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 8))
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(3, 30))
    }

    @Test
    fun `an alarm set inside the hour the clocks repeat rings the first time round`() {
        val next = NextAlarmTime.after(
            at = LocalTime.of(1, 30),
            repeatDays = emptySet(),
            zone = NEW_YORK,
            after = newYork("2026-10-31T12:00"),
        )

        assertThat(next).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"))
    }

    private fun dhaka(local: String): Instant = LocalDateTime.parse(local).atZone(DHAKA).toInstant()

    private fun newYork(local: String): Instant = LocalDateTime.parse(local).atZone(NEW_YORK).toInstant()

    private companion object {
        val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val WEEKDAYS = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}
