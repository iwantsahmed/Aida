package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock.Companion.dhaka
import com.rayluxeo.aida.core.common.MiraClock
import java.time.Instant
import org.junit.Test

/** Every expectation is stated as a Dhaka wall-clock reading, the way Abu would say it. */
class MiraDateTimeParserTest {

    private val parser = MiraDateTimeParser(FixedMiraClock())

    private fun instantOf(normalized: String): Instant? = parser.parse(normalized)?.instant

    private fun parserAt(hour: Int, minute: Int = 0) = MiraDateTimeParser(FixedMiraClock.at(hour, minute))

    private fun parserOn(clock: MiraClock) =
        MiraDateTimeParser(RelativeDateParser(clock), TimeExpressionParser(clock), clock)

    @Test
    fun `resolves tomorrow with an explicit hour`() {
        assertThat(instantOf("tomorrow at 10")).isEqualTo(dhaka(2026, 3, 11, 10))
    }

    @Test
    fun `resolves the Banglish hour marker that follows the number`() {
        assertThat(instantOf("tomorrow 10 at")).isEqualTo(dhaka(2026, 3, 11, 10))
    }

    @Test
    fun `resolves today`() {
        assertThat(parserAt(6).parse("today")?.instant).isEqualTo(dhaka(2026, 3, 10, 9))
    }

    @Test
    fun `resolves the day after tomorrow`() {
        assertThat(instantOf("day_after")).isEqualTo(dhaka(2026, 3, 12, 9))
    }

    @Test
    fun `resolves tonight to the evening of the same day`() {
        assertThat(instantOf("tonight")).isEqualTo(dhaka(2026, 3, 10, 21))
    }

    @Test
    fun `resolves a weekday to its next occurrence`() {
        assertThat(instantOf("friday")).isEqualTo(dhaka(2026, 3, 13, 9))
        assertThat(instantOf("monday")).isEqualTo(dhaka(2026, 3, 16, 9))
    }

    @Test
    fun `rolls a weekday by a week when today's has already passed`() {
        // The fixed clock is a Tuesday at 10:00, so this Tuesday's default 09:00 is behind us.
        assertThat(instantOf("tuesday")).isEqualTo(dhaka(2026, 3, 17, 9))
    }

    @Test
    fun `reads a time written with a colon`() {
        assertThat(instantOf("tomorrow at 10:30")).isEqualTo(dhaka(2026, 3, 11, 10, 30))
        assertThat(instantOf("10:30")).isEqualTo(dhaka(2026, 3, 10, 10, 30))
    }

    @Test
    fun `reads am and pm`() {
        assertThat(instantOf("tomorrow 10 am")).isEqualTo(dhaka(2026, 3, 11, 10))
        assertThat(instantOf("tomorrow at 10 pm")).isEqualTo(dhaka(2026, 3, 11, 22))
        assertThat(instantOf("10 pm")).isEqualTo(dhaka(2026, 3, 10, 22))
    }

    @Test
    fun `reads midnight and midday through the meridiem`() {
        assertThat(instantOf("tomorrow at 12 am")).isEqualTo(dhaka(2026, 3, 11, 0))
        assertThat(instantOf("tomorrow at 12 pm")).isEqualTo(dhaka(2026, 3, 11, 12))
    }

    @Test
    fun `lets the morning qualify a bare hour`() {
        assertThat(instantOf("tomorrow morning 10 at")).isEqualTo(dhaka(2026, 3, 11, 10))
    }

    @Test
    fun `lets the evening qualify a bare hour`() {
        assertThat(instantOf("tomorrow evening 8 at")).isEqualTo(dhaka(2026, 3, 11, 20))
    }

    @Test
    fun `lets the night qualify a bare hour`() {
        assertThat(instantOf("tomorrow night 10 at")).isEqualTo(dhaka(2026, 3, 11, 22))
    }

    @Test
    fun `lets the afternoon qualify a bare hour`() {
        assertThat(instantOf("tomorrow afternoon 4 at")).isEqualTo(dhaka(2026, 3, 11, 16))
    }

    @Test
    fun `resolves noon on its own to twelve`() {
        val match = parser.parse("tomorrow noon")
        assertThat(match?.instant).isEqualTo(dhaka(2026, 3, 11, 12))
        assertThat(match?.hasExplicitTime).isTrue()
    }

    @Test
    fun `resolves a bare hour to its next occurrence`() {
        // 10:00 has just gone at the fixed clock, so a bare "10" means tonight.
        assertThat(instantOf("at 10")).isEqualTo(dhaka(2026, 3, 10, 22))
        assertThat(parserAt(6).parse("at 10")?.instant).isEqualTo(dhaka(2026, 3, 10, 10))
    }

    @Test
    fun `reads a bare hour inside a tomorrow phrase as tomorrow morning`() {
        assertThat(instantOf("tomorrow 10 at")).isEqualTo(dhaka(2026, 3, 11, 10))
        assertThat(instantOf("tomorrow at 10")).isEqualTo(dhaka(2026, 3, 11, 10))
    }

    @Test
    fun `resolves a duration in minutes`() {
        assertThat(instantOf("in 20 minutes")).isEqualTo(dhaka(2026, 3, 10, 10, 20))
        assertThat(instantOf("20 minute later")).isEqualTo(dhaka(2026, 3, 10, 10, 20))
    }

    @Test
    fun `resolves a duration in hours only when it is marked as a span`() {
        assertThat(instantOf("2 hour later")).isEqualTo(dhaka(2026, 3, 10, 12))
        assertThat(instantOf("in 2 hours")).isEqualTo(dhaka(2026, 3, 10, 12))
    }

    @Test
    fun `defaults to nine in the morning when a day carries no time`() {
        val match = parser.parse("tomorrow")
        assertThat(match?.instant).isEqualTo(dhaka(2026, 3, 11, 9))
        assertThat(match?.hasExplicitTime).isFalse()
    }

    @Test
    fun `marks an explicit time as explicit`() {
        assertThat(parser.parse("tomorrow at 10")?.hasExplicitTime).isTrue()
    }

    @Test
    fun `removes the time expression and leaves the rest`() {
        assertThat(parser.parse("tomorrow morning 10 at ahmed to call")?.remainder)
            .isEqualTo("ahmed to call")
        assertThat(parser.parse("remind me tomorrow at 10 to call ahmed")?.remainder)
            .isEqualTo("remind me to call ahmed")
        assertThat(parser.parse("in 20 minutes drink water")?.remainder).isEqualTo("drink water")
    }

    @Test
    fun `reports higher confidence when both a day and a time were stated`() {
        val both = parser.parse("tomorrow at 10")?.confidence ?: 0f
        val dayOnly = parser.parse("tomorrow")?.confidence ?: 0f
        assertThat(both).isGreaterThan(dayOnly)
        assertThat(both).isAtMost(1f)
        assertThat(dayOnly).isAtLeast(0f)
    }

    @Test
    fun `finds nothing in a sentence with no time in it`() {
        assertThat(parser.parse("call ahmed")).isNull()
        assertThat(parser.parse("")).isNull()
    }

    @Test
    fun `does not mistake a plain count for an hour`() {
        assertThat(parser.parse("call 3 people")).isNull()
    }

    // ------------------------------------------------- the same moment, four languages

    @Test
    fun `gives the same moment to all four ways of naming it`() {
        val expected = dhaka(2026, 3, 11, 10)
        assertThat(instantOf("tomorrow morning at 10")).isEqualTo(expected)
        assertThat(instantOf(BengaliNumerals.toAscii("আগামীকাল সকাল ১০ টায়"))).isEqualTo(expected)
        assertThat(instantOf("kal shokal 10 tay")).isEqualTo(expected)
        assertThat(instantOf("tomorrow 10 tay")).isEqualTo(expected)
    }

    @Test
    fun `resolves the Bengali tonight to this evening`() {
        assertThat(instantOf("aj rate")).isEqualTo(dhaka(2026, 3, 10, 21))
        assertThat(instantOf("raat 10 tay")).isEqualTo(dhaka(2026, 3, 10, 22))
    }

    // ------------------------------------------------------- this week and the next one

    @Test
    fun `separates this friday from next friday`() {
        assertThat(instantOf("this friday at 10 pm")).isEqualTo(dhaka(2026, 3, 13, 22))
        assertThat(instantOf("next friday at 10 pm")).isEqualTo(dhaka(2026, 3, 20, 22))
    }

    @Test
    fun `rolls a weekday on only when the moment it carries has gone`() {
        // Friday 13 March at 18:00. The default 09:00 is behind us, so a bare "friday" means
        // next week's; an hour Abu stated for tonight is still ahead and stays put.
        val fridayEvening = parserOn(FixedMiraClock(dhaka(2026, 3, 13, 18)))
        assertThat(fridayEvening.parse("friday")?.instant).isEqualTo(dhaka(2026, 3, 20, 9))
        assertThat(fridayEvening.parse("friday at 10 pm")?.instant).isEqualTo(dhaka(2026, 3, 13, 22))
    }

    @Test
    fun `resolves next week and next month to nine in the morning`() {
        assertThat(instantOf("next week")).isEqualTo(dhaka(2026, 3, 17, 9))
        assertThat(instantOf("agami soptah")).isEqualTo(dhaka(2026, 3, 17, 9))
        assertThat(instantOf("next month")).isEqualTo(dhaka(2026, 4, 10, 9))
        assertThat(instantOf("আগামী মাস")).isEqualTo(dhaka(2026, 4, 10, 9))
    }

    @Test
    fun `resolves a day of the month with an hour on it`() {
        assertThat(instantOf("masher 5 tarik bikal 4 tay")).isEqualTo(dhaka(2026, 4, 5, 16))
        assertThat(instantOf("the 20 th of the month at 10 pm")).isEqualTo(dhaka(2026, 3, 20, 22))
    }

    // ----------------------------------------------------------------- more expressions

    @Test
    fun `resolves half past the hour`() {
        assertThat(instantOf("tomorrow half past ten")).isEqualTo(dhaka(2026, 3, 11, 10, 30))
        assertThat(instantOf("kal sare 10 tay")).isEqualTo(dhaka(2026, 3, 11, 10, 30))
    }

    @Test
    fun `resolves a Bengali duration`() {
        assertThat(instantOf("2 ghonta pore")).isEqualTo(dhaka(2026, 3, 10, 12))
        assertThat(instantOf("20 minit pore")).isEqualTo(dhaka(2026, 3, 10, 10, 20))
        assertThat(instantOf("adha ghonta pore")).isEqualTo(dhaka(2026, 3, 10, 10, 30))
    }

    @Test
    fun `reads a part of the day with no day named as today's`() {
        assertThat(instantOf("bikal 4 tay")).isEqualTo(dhaka(2026, 3, 10, 16))
        assertThat(instantOf("shokal 10 tay")).isEqualTo(dhaka(2026, 3, 11, 10))
    }

    @Test
    fun `grades a bare part of the day below a stated hour`() {
        val dayPartOnly = parser.parse("tonight")?.confidence ?: 0f
        val stated = parser.parse("tonight at 11 pm")?.confidence ?: 0f
        assertThat(stated).isGreaterThan(dayPartOnly)
    }

    @Test
    fun `is built from the two parsers it composes`() {
        val clock = FixedMiraClock()
        val composed = MiraDateTimeParser(RelativeDateParser(clock), TimeExpressionParser(clock), clock)
        assertThat(composed.parse("kal shokal 10 tay")?.instant).isEqualTo(dhaka(2026, 3, 11, 10))
    }
}
