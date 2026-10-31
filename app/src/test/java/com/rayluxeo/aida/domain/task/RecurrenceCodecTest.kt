package com.rayluxeo.aida.domain.task

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

class RecurrenceCodecTest {

    private val codec = TextRecurrenceCodec()

    @Test
    fun `every variant survives a round trip`() {
        val rules = listOf(
            RecurrenceRule.Daily(),
            RecurrenceRule.Daily(interval = 3, until = date("2026-12-31")),
            RecurrenceRule.Weekly(),
            RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
            RecurrenceRule.Weekly(
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
                interval = 2,
                until = date("2026-12-31"),
            ),
            RecurrenceRule.Weekdays(),
            RecurrenceRule.Weekdays(until = date("2026-12-31")),
            RecurrenceRule.Monthly(),
            RecurrenceRule.Monthly(dayOfMonth = 31, interval = 2, until = date("2027-01-31")),
            RecurrenceRule.Yearly(),
            RecurrenceRule.Yearly(interval = 4, until = date("2040-02-29")),
            RecurrenceRule.EveryNDays(interval = 10),
            RecurrenceRule.EveryNDays(interval = 3, until = date("2026-12-31")),
        )

        for (rule in rules) {
            assertThat(codec.decode(codec.encode(rule))).isEqualTo(rule)
        }
    }

    @Test
    fun `the stored text says what the rule is`() {
        assertThat(codec.encode(RecurrenceRule.Daily())).isEqualTo("freq=DAILY;interval=1")
        assertThat(codec.encode(RecurrenceRule.Weekdays())).isEqualTo("freq=WEEKDAYS;interval=1")
        assertThat(codec.encode(RecurrenceRule.Monthly(dayOfMonth = 31)))
            .isEqualTo("freq=MONTHLY;interval=1;day=31")
        assertThat(codec.encode(RecurrenceRule.EveryNDays(interval = 10)))
            .isEqualTo("freq=EVERY_N_DAYS;interval=10")
    }

    @Test
    fun `days are written in week order whatever order the rule holds them in`() {
        val rule = RecurrenceRule.Weekly(
            days = linkedSetOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY),
            interval = 2,
            until = date("2026-12-31"),
        )

        assertThat(codec.encode(rule))
            .isEqualTo("freq=WEEKLY;interval=2;days=MONDAY,THURSDAY;until=2026-12-31")
    }

    @Test
    fun `a weekly rule naming no day writes no day list`() {
        assertThat(codec.encode(RecurrenceRule.Weekly(interval = 2)))
            .isEqualTo("freq=WEEKLY;interval=2")
        assertThat(codec.decode("freq=WEEKLY;interval=2"))
            .isEqualTo(RecurrenceRule.Weekly(interval = 2))
    }

    @Test
    fun `nothing stored is no rule`() {
        assertThat(codec.decode(null)).isNull()
        assertThat(codec.decode("")).isNull()
        assertThat(codec.decode("   ")).isNull()
        assertThat(codec.decode(";;")).isNull()
    }

    @Test
    fun `text that is not a field list is no rule`() {
        assertThat(codec.decode("every monday please")).isNull()
        assertThat(codec.decode("=DAILY")).isNull()
        assertThat(codec.decode("freq=")).isNull()
    }

    @Test
    fun `a frequency this build does not know is no rule rather than the wrong one`() {
        assertThat(codec.decode("freq=HOURLY;interval=1")).isNull()
    }

    @Test
    fun `a field this build does not know is ignored`() {
        val decoded = codec.decode("freq=WEEKLY;interval=1;days=MONDAY;count=10;byhour=9")

        assertThat(decoded).isEqualTo(RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)))
    }

    @Test
    fun `a missing interval reads as every one`() {
        assertThat(codec.decode("freq=DAILY")).isEqualTo(RecurrenceRule.Daily())
    }

    @Test
    fun `an interval that is not a count above zero is no rule`() {
        assertThat(codec.decode("freq=DAILY;interval=one")).isNull()
        assertThat(codec.decode("freq=DAILY;interval=0")).isNull()
        assertThat(codec.decode("freq=DAILY;interval=-2")).isNull()
        assertThat(codec.decode("freq=DAILY;interval=1.5")).isNull()
    }

    @Test
    fun `an unreadable end date is no rule rather than a series that never ends`() {
        assertThat(codec.decode("freq=DAILY;interval=1;until=31-12-2026")).isNull()
        assertThat(codec.decode("freq=DAILY;interval=1;until=soon")).isNull()
    }

    @Test
    fun `an unreadable day name is no rule rather than fewer days than Abu asked for`() {
        assertThat(codec.decode("freq=WEEKLY;interval=1;days=MONDAY,FUNDAY")).isNull()
    }

    @Test
    fun `a day of the month outside a month is no rule`() {
        assertThat(codec.decode("freq=MONTHLY;interval=1;day=32")).isNull()
        assertThat(codec.decode("freq=MONTHLY;interval=1;day=0")).isNull()
        assertThat(codec.decode("freq=MONTHLY;interval=1;day=last")).isNull()
    }

    @Test
    fun `a monthly rule with no day reads back with none`() {
        assertThat(codec.decode("freq=MONTHLY;interval=1")).isEqualTo(RecurrenceRule.Monthly())
    }

    @Test
    fun `case and spacing in a stored row do not change the rule`() {
        val decoded = codec.decode(" FREQ=weekly ; interval=2 ; days=monday, thursday ")

        assertThat(decoded).isEqualTo(
            RecurrenceRule.Weekly(
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                interval = 2,
            ),
        )
    }

    private fun date(text: String): LocalDate = LocalDate.parse(text)
}
