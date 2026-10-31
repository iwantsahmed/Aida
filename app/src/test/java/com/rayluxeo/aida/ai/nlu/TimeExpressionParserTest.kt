package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalTime
import org.junit.Test

/**
 * The fixed clock reads 10:00, which is what makes the ambiguous cases worth asserting: a bare
 * "10" has just gone, so the next one is tonight.
 *
 * Bengali digits arrive here already folded to ASCII, so the Bengali-script cases go through
 * [BengaliNumerals.toAscii] to reach the shape the normalizer hands over.
 */
class TimeExpressionParserTest {

    private val parser = TimeExpressionParser(FixedMiraClock())

    private val earlyParser = TimeExpressionParser(FixedMiraClock.at(6))

    private fun timeOf(text: String): LocalTime? = parser.parse(text)?.time

    private fun durationOf(text: String): Duration? = parser.parseDuration(text)?.duration

    // --------------------------------------------------------------------- the hour

    @Test
    fun `reads an hour behind a preposition`() {
        assertThat(earlyParser.parse("at 10")?.time).isEqualTo(LocalTime.of(10, 0))
    }

    @Test
    fun `reads an hour behind the Bengali hour marker`() {
        for (form in listOf("10 tay", "10 at", "10 টায়")) {
            assertThat(earlyParser.parse(form)?.time).isEqualTo(LocalTime.of(10, 0))
        }
    }

    @Test
    fun `resolves an unqualified hour to the next time it comes round`() {
        val match = parser.parse("at 10")
        assertThat(match?.time).isEqualTo(LocalTime.of(22, 0))
        assertThat(match?.meridiemWasExplicit).isFalse()
    }

    @Test
    fun `reads am and pm, and calls them explicit`() {
        assertThat(timeOf("10 am")).isEqualTo(LocalTime.of(10, 0))
        assertThat(timeOf("10 pm")).isEqualTo(LocalTime.of(22, 0))
        assertThat(parser.parse("10 pm")?.meridiemWasExplicit).isTrue()
    }

    @Test
    fun `reads midnight and midday through the meridiem`() {
        assertThat(timeOf("12 am")).isEqualTo(LocalTime.MIDNIGHT)
        assertThat(timeOf("12 pm")).isEqualTo(LocalTime.NOON)
    }

    @Test
    fun `reads an hour past twelve as the hour it already is`() {
        val match = parser.parse("at 14")
        assertThat(match?.time).isEqualTo(LocalTime.of(14, 0))
        assertThat(match?.meridiemWasExplicit).isTrue()
    }

    @Test
    fun `reads a time written with a colon`() {
        assertThat(timeOf("10:30")).isEqualTo(LocalTime.of(10, 30))
    }

    @Test
    fun `reads a time written with a dot only when something says it is a time`() {
        assertThat(parser.parse("10.30")).isNull()
        assertThat(timeOf("at 10.30")).isEqualTo(LocalTime.of(10, 30))
        assertThat(timeOf("10.30 tay")).isEqualTo(LocalTime.of(10, 30))
    }

    @Test
    fun `reads half past the hour in English and in Bengali`() {
        assertThat(timeOf("half past ten")).isEqualTo(LocalTime.of(10, 30))
        assertThat(timeOf("sare 10 tay")).isEqualTo(LocalTime.of(10, 30))
        assertThat(timeOf("সাড়ে 10 টায়")).isEqualTo(LocalTime.of(10, 30))
    }

    @Test
    fun `reads an hour written as an English word`() {
        assertThat(timeOf("at ten")).isEqualTo(LocalTime.of(22, 0))
        assertThat(timeOf("at eight pm")).isEqualTo(LocalTime.of(20, 0))
    }

    @Test
    fun `reads o'clock as the hour marker it is`() {
        assertThat(earlyParser.parse("10 o clock")?.time).isEqualTo(LocalTime.of(10, 0))
        assertThat(earlyParser.parse("10 o clock")?.remainder).isEmpty()
    }

    // ---------------------------------------------------------------- parts of the day

    @Test
    fun `reads a part of the day on its own`() {
        assertThat(timeOf("morning")).isEqualTo(LocalTime.of(9, 0))
        assertThat(timeOf("noon")).isEqualTo(LocalTime.of(12, 0))
        assertThat(timeOf("afternoon")).isEqualTo(LocalTime.of(15, 0))
        assertThat(timeOf("evening")).isEqualTo(LocalTime.of(19, 0))
        assertThat(timeOf("night")).isEqualTo(LocalTime.of(21, 0))
        assertThat(timeOf("tonight")).isEqualTo(LocalTime.of(21, 0))
    }

    @Test
    fun `reads a part of the day in Bengali and Banglish`() {
        assertThat(timeOf("shokal")).isEqualTo(LocalTime.of(9, 0))
        assertThat(timeOf("সকালে")).isEqualTo(LocalTime.of(9, 0))
        assertThat(timeOf("dupur")).isEqualTo(LocalTime.of(12, 0))
        assertThat(timeOf("bikel")).isEqualTo(LocalTime.of(15, 0))
        assertThat(timeOf("সন্ধ্যা")).isEqualTo(LocalTime.of(19, 0))
        assertThat(timeOf("raat")).isEqualTo(LocalTime.of(21, 0))
    }

    @Test
    fun `lets a part of the day qualify a bare hour`() {
        assertThat(timeOf("shokal 10 tay")).isEqualTo(LocalTime.of(10, 0))
        assertThat(timeOf("dupur 2 tay")).isEqualTo(LocalTime.of(14, 0))
        assertThat(timeOf("bikal 4 tay")).isEqualTo(LocalTime.of(16, 0))
        assertThat(timeOf("raat 10 tay")).isEqualTo(LocalTime.of(22, 0))
        assertThat(timeOf("evening 8 at")).isEqualTo(LocalTime.of(20, 0))
    }

    @Test
    fun `reads the same qualified hour in all four languages`() {
        val ten = LocalTime.of(10, 0)
        assertThat(timeOf("morning 10 at")).isEqualTo(ten)
        assertThat(timeOf("shokal 10 tay")).isEqualTo(ten)
        assertThat(timeOf(BengaliNumerals.toAscii("সকাল ১০ টায়"))).isEqualTo(ten)
        assertThat(timeOf("morning 10 tay")).isEqualTo(ten)
    }

    @Test
    fun `a qualified hour is not a guess`() {
        assertThat(parser.parse("raat 10 tay")?.meridiemWasExplicit).isTrue()
    }

    // ----------------------------------------------------------- what it refuses to read

    @Test
    fun `does not read a bare number as an hour on its own`() {
        assertThat(parser.parse("10")).isNull()
        assertThat(parser.parse("call 3 people")).isNull()
    }

    @Test
    fun `reads a bare number as an hour once the caller says a date was found`() {
        assertThat(parser.parse("10", anchored = true)?.time).isEqualTo(LocalTime.of(22, 0))
    }

    @Test
    fun `never reads a number written as a word without a marker beside it`() {
        // "ten people" is a count in every sentence Abu writes; only "at ten" is a time.
        assertThat(parser.parse("ten", anchored = true)).isNull()
        assertThat(parser.parse("ten people", anchored = true)).isNull()
    }

    @Test
    fun `finds nothing in a sentence that names no time`() {
        assertThat(parser.parse("call ahmed")).isNull()
        assertThat(parser.parse("")).isNull()
    }

    // ----------------------------------------------------------- the reading it hands on

    @Test
    fun `says whether an hour was named at all`() {
        assertThat(parser.read("morning")?.namesAnHour).isFalse()
        assertThat(parser.read("noon")?.namesAnHour).isTrue()
        assertThat(parser.read("at 10")?.namesAnHour).isTrue()
    }

    @Test
    fun `hands on the hour as Abu said it when nothing fixed the half of the day`() {
        assertThat(parser.read("at 10")?.unqualifiedHour).isEqualTo(10)
        assertThat(parser.read("10 pm")?.unqualifiedHour).isNull()
        assertThat(parser.read("raat 10 tay")?.unqualifiedHour).isNull()
    }

    @Test
    fun `takes the whole time expression out of the remainder`() {
        val match = parser.parse("morning 10 at ahmed to call")
        assertThat(match?.remainder).isEqualTo("ahmed to call")
        assertThat(match?.matched).isEqualTo("morning 10 at")
        assertThat(parser.parse("at 10 pm drink water")?.remainder).isEqualTo("drink water")
    }

    // --------------------------------------------------------------------- durations

    @Test
    fun `reads a count of minutes without needing a marker`() {
        assertThat(durationOf("20 minute")).isEqualTo(Duration.ofMinutes(20))
        assertThat(durationOf("20 minit")).isEqualTo(Duration.ofMinutes(20))
        assertThat(durationOf(BengaliNumerals.toAscii("২০ মিনিট"))).isEqualTo(Duration.ofMinutes(20))
        assertThat(durationOf("twenty minutes")).isEqualTo(Duration.ofMinutes(20))
    }

    @Test
    fun `reads a minute count wrapped in either kind of marker`() {
        assertThat(durationOf("in 20 minutes")).isEqualTo(Duration.ofMinutes(20))
        assertThat(durationOf("20 minute later")).isEqualTo(Duration.ofMinutes(20))
        assertThat(durationOf("20 minit pore")).isEqualTo(Duration.ofMinutes(20))
    }

    @Test
    fun `reads a compound English count`() {
        assertThat(durationOf("forty five minutes")).isEqualTo(Duration.ofMinutes(45))
    }

    @Test
    fun `reads a count of hours only when something marks it as a span`() {
        assertThat(parser.parseDuration("2 hour")).isNull()
        assertThat(durationOf("in 2 hours")).isEqualTo(Duration.ofHours(2))
        assertThat(durationOf("2 hours later")).isEqualTo(Duration.ofHours(2))
        assertThat(durationOf("2 ghonta pore")).isEqualTo(Duration.ofHours(2))
        assertThat(durationOf(BengaliNumerals.toAscii("২ ঘণ্টা পরে"))).isEqualTo(Duration.ofHours(2))
    }

    @Test
    fun `reads half an hour in all four languages`() {
        val half = Duration.ofMinutes(30)
        assertThat(durationOf("half an hour")).isEqualTo(half)
        assertThat(durationOf("adha ghonta")).isEqualTo(half)
        assertThat(durationOf("আধা ঘণ্টা")).isEqualTo(half)
        assertThat(durationOf("half hour")).isEqualTo(half)
    }

    @Test
    fun `reads a count of seconds`() {
        assertThat(durationOf("in 30 seconds")).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `takes the whole duration out of the remainder`() {
        val match = parser.parseDuration("in 20 minutes drink water")
        assertThat(match?.remainder).isEqualTo("drink water")
        assertThat(match?.matched).isEqualTo("in 20 minutes")
    }

    @Test
    fun `finds no duration in a time of day`() {
        assertThat(parser.parseDuration("tomorrow at 10")).isNull()
        assertThat(parser.parseDuration("shokal 10 tay")).isNull()
        assertThat(parser.parseDuration("")).isNull()
    }
}
