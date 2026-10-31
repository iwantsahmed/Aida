package com.rayluxeo.aida.domain.task

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

/**
 * The dates are written out rather than computed, so a test that starts failing says which day it
 * meant. March 2026 is the reference month: the 9th is a Monday and the 10th a Tuesday, which is
 * where `FixedMiraClock` stands too.
 */
class RecurrenceCalculatorTest {

    private val calculator = DefaultRecurrenceCalculator()

    @Test
    fun `a daily rule advances by its interval`() {
        assertThat(calculator.next(RecurrenceRule.Daily(), date("2026-03-10")))
            .isEqualTo(date("2026-03-11"))
        assertThat(calculator.next(RecurrenceRule.Daily(interval = 3), date("2026-03-10")))
            .isEqualTo(date("2026-03-13"))
    }

    @Test
    fun `every n days counts in days from the last occurrence`() {
        assertThat(calculator.next(RecurrenceRule.EveryNDays(interval = 10), date("2026-03-10")))
            .isEqualTo(date("2026-03-20"))
    }

    @Test
    fun `an interval below one is treated as one rather than standing still`() {
        assertThat(calculator.next(RecurrenceRule.Daily(interval = 0), date("2026-03-10")))
            .isEqualTo(date("2026-03-11"))
        assertThat(calculator.preview(RecurrenceRule.Daily(interval = 0), date("2026-03-10"), 3))
            .containsExactly(date("2026-03-10"), date("2026-03-11"), date("2026-03-12"))
            .inOrder()
    }

    @Test
    fun `a weekly rule naming no day keeps the same weekday`() {
        assertThat(calculator.next(RecurrenceRule.Weekly(), date("2026-03-10")))
            .isEqualTo(date("2026-03-17"))
        assertThat(calculator.next(RecurrenceRule.Weekly(interval = 2), date("2026-03-10")))
            .isEqualTo(date("2026-03-24"))
    }

    @Test
    fun `every monday and thursday from a tuesday is that thursday`() {
        val rule = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        assertThat(calculator.next(rule, date("2026-03-10"))).isEqualTo(date("2026-03-12"))
    }

    @Test
    fun `a weekly rule wraps to the earliest named day of the next week`() {
        val rule = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        assertThat(calculator.next(rule, date("2026-03-12"))).isEqualTo(date("2026-03-16"))
    }

    @Test
    fun `every two weeks from a monday is a fortnight later`() {
        val rule = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY), interval = 2)

        assertThat(calculator.next(rule, date("2026-03-09"))).isEqualTo(date("2026-03-23"))
    }

    @Test
    fun `a fortnightly rule finishes its week before the interval is spent`() {
        val rule = RecurrenceRule.Weekly(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            interval = 2,
        )

        assertThat(calculator.next(rule, date("2026-03-09"))).isEqualTo(date("2026-03-12"))
        assertThat(calculator.next(rule, date("2026-03-12"))).isEqualTo(date("2026-03-23"))
    }

    @Test
    fun `a weekly rule on a sunday counts the week from the monday before it`() {
        val rule = RecurrenceRule.Weekly(days = setOf(DayOfWeek.SUNDAY))

        assertThat(calculator.next(rule, date("2026-03-15"))).isEqualTo(date("2026-03-22"))
    }

    @Test
    fun `a weekday rule steps over the weekend`() {
        val rule = RecurrenceRule.Weekdays()

        assertThat(calculator.next(rule, date("2026-03-12"))).isEqualTo(date("2026-03-13"))
        assertThat(calculator.next(rule, date("2026-03-13"))).isEqualTo(date("2026-03-16"))
        assertThat(calculator.next(rule, date("2026-03-14"))).isEqualTo(date("2026-03-16"))
        assertThat(calculator.next(rule, date("2026-03-15"))).isEqualTo(date("2026-03-16"))
    }

    @Test
    fun `a monthly rule on the thirty first comes back to the thirty first after february`() {
        val rule = RecurrenceRule.Monthly(dayOfMonth = 31)

        assertThat(calculator.next(rule, date("2026-01-31"))).isEqualTo(date("2026-02-28"))
        assertThat(calculator.next(rule, date("2026-02-28"))).isEqualTo(date("2026-03-31"))
        assertThat(calculator.preview(rule, date("2026-01-31"), 4)).containsExactly(
            date("2026-01-31"),
            date("2026-02-28"),
            date("2026-03-31"),
            date("2026-04-30"),
        ).inOrder()
    }

    @Test
    fun `a monthly rule on the thirty first lands on the twenty ninth in a leap february`() {
        assertThat(calculator.next(RecurrenceRule.Monthly(dayOfMonth = 31), date("2024-01-31")))
            .isEqualTo(date("2024-02-29"))
    }

    @Test
    fun `a monthly rule naming no day takes it from the date it counts from`() {
        val rule = RecurrenceRule.Monthly()

        assertThat(calculator.next(rule, date("2026-01-31"))).isEqualTo(date("2026-02-28"))
        assertThat(calculator.next(rule, date("2026-02-28"))).isEqualTo(date("2026-03-28"))
    }

    @Test
    fun `a quarterly rule keeps its day three months on`() {
        val rule = RecurrenceRule.Monthly(dayOfMonth = 15, interval = 3)

        assertThat(calculator.next(rule, date("2026-01-15"))).isEqualTo(date("2026-04-15"))
    }

    @Test
    fun `a yearly rule keeps the same date`() {
        assertThat(calculator.next(RecurrenceRule.Yearly(), date("2026-03-10")))
            .isEqualTo(date("2027-03-10"))
        assertThat(calculator.next(RecurrenceRule.Yearly(), date("2026-04-30")))
            .isEqualTo(date("2027-04-30"))
    }

    @Test
    fun `a yearly rule on the twenty ninth of february returns to it in the next leap year`() {
        assertThat(calculator.preview(RecurrenceRule.Yearly(), date("2024-02-29"), 5)).containsExactly(
            date("2024-02-29"),
            date("2025-02-28"),
            date("2026-02-28"),
            date("2027-02-28"),
            date("2028-02-29"),
        ).inOrder()
    }

    @Test
    fun `a leap day rule repeating every four years never leaves the twenty ninth`() {
        assertThat(calculator.next(RecurrenceRule.Yearly(interval = 4), date("2024-02-29")))
            .isEqualTo(date("2028-02-29"))
    }

    @Test
    fun `until includes an occurrence falling on it`() {
        val rule = RecurrenceRule.Daily(until = date("2026-03-11"))

        assertThat(calculator.next(rule, date("2026-03-10"))).isEqualTo(date("2026-03-11"))
    }

    @Test
    fun `until ends the series the day after its last occurrence`() {
        val rule = RecurrenceRule.Daily(until = date("2026-03-11"))

        assertThat(calculator.next(rule, date("2026-03-11"))).isNull()
    }

    @Test
    fun `a weekly series stops once it has passed its until`() {
        val rule = RecurrenceRule.Weekly(
            days = setOf(DayOfWeek.MONDAY),
            until = date("2026-03-16"),
        )

        assertThat(calculator.next(rule, date("2026-03-09"))).isEqualTo(date("2026-03-16"))
        assertThat(calculator.next(rule, date("2026-03-16"))).isNull()
    }

    @Test
    fun `preview starts at the date it is given`() {
        assertThat(calculator.preview(RecurrenceRule.Daily(), date("2026-03-10"), 3))
            .containsExactly(date("2026-03-10"), date("2026-03-11"), date("2026-03-12"))
            .inOrder()
    }

    @Test
    fun `preview stops early when the rule runs out`() {
        val rule = RecurrenceRule.Daily(until = date("2026-03-12"))

        assertThat(calculator.preview(rule, date("2026-03-10"), 5))
            .containsExactly(date("2026-03-10"), date("2026-03-11"), date("2026-03-12"))
            .inOrder()
    }

    @Test
    fun `preview of a weekday rule skips the weekend`() {
        assertThat(calculator.preview(RecurrenceRule.Weekdays(), date("2026-03-13"), 3))
            .containsExactly(date("2026-03-13"), date("2026-03-16"), date("2026-03-17"))
            .inOrder()
    }

    @Test
    fun `preview of nothing is an empty list`() {
        assertThat(calculator.preview(RecurrenceRule.Daily(), date("2026-03-10"), 0)).isEmpty()
        assertThat(calculator.preview(RecurrenceRule.Daily(), date("2026-03-10"), -3)).isEmpty()
    }

    @Test
    fun `preview of a series that has already ended is empty`() {
        val rule = RecurrenceRule.Daily(until = date("2026-03-09"))

        assertThat(calculator.preview(rule, date("2026-03-10"), 3)).isEmpty()
    }

    private fun date(text: String): LocalDate = LocalDate.parse(text)
}
