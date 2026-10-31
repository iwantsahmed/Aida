package com.rayluxeo.aida.domain.automation

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The arithmetic behind an armed alarm: given "now", when does a time-based rule next fire. This is
 * the one place that answer is computed — to arm the first alarm and, after it fires, to arm the
 * next — so a wrong answer here is a rule that fires at the wrong hour or never fires again.
 *
 * Every case pins an explicit zone and an explicit "now" rather than reading the wall clock, because
 * "the next 08:00" is only testable at all if "now" is a value and not whenever the test happened to
 * run.
 */
class AutomationNextRunTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")

    /** A Wednesday, 06:30 local — before an 08:00 trigger, after a 05:00 one. */
    private val wednesdayMorning: java.time.Instant =
        LocalDateTime.of(LocalDate.of(2026, 10, 14), LocalTime.of(6, 30)).atZone(zone).toInstant()

    private fun local(instant: java.time.Instant): LocalDateTime =
        LocalDateTime.ofInstant(instant, zone)

    @Test
    fun `a daily time still ahead today fires today`() {
        val next = AutomationNextRun.next(
            AutomationTriggerConfig.TimeOfDay(minuteOfDay = 8 * 60),
            after = wednesdayMorning,
            zone = zone,
        )!!
        assertThat(local(next)).isEqualTo(LocalDateTime.of(LocalDate.of(2026, 10, 14), LocalTime.of(8, 0)))
    }

    @Test
    fun `a daily time already past today rolls to tomorrow`() {
        val next = AutomationNextRun.next(
            AutomationTriggerConfig.TimeOfDay(minuteOfDay = 5 * 60),
            after = wednesdayMorning,
            zone = zone,
        )!!
        assertThat(local(next)).isEqualTo(LocalDateTime.of(LocalDate.of(2026, 10, 15), LocalTime.of(5, 0)))
    }

    @Test
    fun `a weekday-restricted time skips forward to the first allowed day`() {
        // Wednesday 06:30 now; a Friday-only 09:00 rule must land on Friday the 16th.
        val next = AutomationNextRun.next(
            AutomationTriggerConfig.TimeOfDay(
                minuteOfDay = 9 * 60,
                daysOfWeek = setOf(DayOfWeek.FRIDAY),
            ),
            after = wednesdayMorning,
            zone = zone,
        )!!
        val landed = local(next)
        assertThat(landed.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)
        assertThat(landed).isEqualTo(LocalDateTime.of(LocalDate.of(2026, 10, 16), LocalTime.of(9, 0)))
    }

    @Test
    fun `today counts as an allowed day only while the time is still ahead`() {
        // Wednesday is allowed, but 05:00 has already passed, so the next Wednesday 05:00 is a week on.
        val next = AutomationNextRun.next(
            AutomationTriggerConfig.TimeOfDay(
                minuteOfDay = 5 * 60,
                daysOfWeek = setOf(DayOfWeek.WEDNESDAY),
            ),
            after = wednesdayMorning,
            zone = zone,
        )!!
        assertThat(local(next)).isEqualTo(LocalDateTime.of(LocalDate.of(2026, 10, 21), LocalTime.of(5, 0)))
    }

    @Test
    fun `an interval counts forward from now by its minutes`() {
        val next = AutomationNextRun.next(
            AutomationTriggerConfig.Interval(everyMinutes = 90),
            after = wednesdayMorning,
            zone = zone,
        )!!
        assertThat(local(next)).isEqualTo(LocalDateTime.of(LocalDate.of(2026, 10, 14), LocalTime.of(8, 0)))
    }

    @Test
    fun `a day-of-week trigger behaves like a one-day time-of-day`() {
        val next = AutomationNextRun.next(
            AutomationTriggerConfig.DayOfWeekTrigger(
                dayOfWeek = DayOfWeek.SUNDAY,
                minuteOfDay = 7 * 60,
            ),
            after = wednesdayMorning,
            zone = zone,
        )!!
        val landed = local(next)
        assertThat(landed.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(landed.toLocalTime()).isEqualTo(LocalTime.of(7, 0))
        assertThat(landed.isAfter(local(wednesdayMorning))).isTrue()
    }

    @Test
    fun `a fixed date in the future fires once, at its time`() {
        val next = AutomationNextRun.next(
            AutomationTriggerConfig.DateTrigger(
                date = LocalDate.of(2026, 12, 25),
                minuteOfDay = 10 * 60 + 30,
            ),
            after = wednesdayMorning,
            zone = zone,
        )!!
        assertThat(local(next)).isEqualTo(LocalDateTime.of(LocalDate.of(2026, 12, 25), LocalTime.of(10, 30)))
    }

    @Test
    fun `a fixed date already past never fires again`() {
        val next = AutomationNextRun.next(
            AutomationTriggerConfig.DateTrigger(
                date = LocalDate.of(2020, 1, 1),
                minuteOfDay = 9 * 60,
            ),
            after = wednesdayMorning,
            zone = zone,
        )
        assertThat(next).isNull()
    }

    @Test
    fun `triggers that are not on the clock are never scheduled`() {
        val offClock = listOf(
            AutomationTriggerConfig.Manual,
            AutomationTriggerConfig.AppStart,
            AutomationTriggerConfig.PowerConnected,
            AutomationTriggerConfig.PowerDisconnected,
            AutomationTriggerConfig.InvoiceDue,
            AutomationTriggerConfig.TaskOverdue,
        )
        offClock.forEach { config ->
            assertThat(AutomationNextRun.isTimeBased(config)).isFalse()
            assertThat(AutomationNextRun.next(config, wednesdayMorning, zone)).isNull()
        }
    }

    @Test
    fun `the time-based triggers all report themselves as such`() {
        val onClock = listOf(
            AutomationTriggerConfig.TimeOfDay(minuteOfDay = 0),
            AutomationTriggerConfig.Interval(everyMinutes = 15),
            AutomationTriggerConfig.DayOfWeekTrigger(DayOfWeek.MONDAY, minuteOfDay = 0),
            AutomationTriggerConfig.DateTrigger(LocalDate.of(2027, 1, 1), minuteOfDay = 0),
        )
        onClock.forEach { config ->
            assertThat(AutomationNextRun.isTimeBased(config)).isTrue()
        }
    }
}
