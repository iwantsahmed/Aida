package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The fixed clock is Tuesday 10 March 2026 at 10:00 in Dhaka, so every expectation below can be
 * read as a date on Abu's own calendar.
 *
 * Bengali digits are folded to ASCII by the normalizer long before this parser sees them, so the
 * Bengali-script cases go through [BengaliNumerals.toAscii] to arrive in the shape it would hand
 * over. Everything else is written exactly as Abu writes it.
 */
class RelativeDateParserTest {

    private val parser = RelativeDateParser(FixedMiraClock())

    private fun dateOf(text: String): LocalDate? = parser.parse(text)?.date

    private fun parserOn(month: Int, day: Int, hour: Int = 10) =
        RelativeDateParser(FixedMiraClock(FixedMiraClock.dhaka(2026, month, day, hour)))

    // ------------------------------------------------------------------ the named days

    @Test
    fun `reads today in all four languages`() {
        for (form in listOf("today", "aj", "ajke", "aaj", "আজ", "আজকে")) {
            assertThat(dateOf(form)).isEqualTo(LocalDate.of(2026, 3, 10))
        }
    }

    @Test
    fun `reads tomorrow in all four languages`() {
        for (form in listOf("tomorrow", "kal", "kalke", "agamikal", "আগামীকাল", "কালকে")) {
            assertThat(dateOf(form)).isEqualTo(LocalDate.of(2026, 3, 11))
        }
    }

    @Test
    fun `reads the day after tomorrow in all four languages`() {
        for (form in listOf("day_after", "day after tomorrow", "porshu", "porsu", "পরশু")) {
            assertThat(dateOf(form)).isEqualTo(LocalDate.of(2026, 3, 12))
        }
    }

    @Test
    fun `does not read the day after tomorrow as tomorrow`() {
        assertThat(dateOf("day after tomorrow")).isNotEqualTo(dateOf("tomorrow"))
    }

    // ---------------------------------------------------------------------- weekdays

    @Test
    fun `reads a bare weekday as the next one to come round`() {
        assertThat(dateOf("friday")).isEqualTo(LocalDate.of(2026, 3, 13))
        assertThat(dateOf("monday")).isEqualTo(LocalDate.of(2026, 3, 16))
    }

    @Test
    fun `reads a weekday the same way in all three scripts`() {
        val friday = LocalDate.of(2026, 3, 13)
        for (form in listOf("friday", "shukrobar", "sukrobar", "jumabar", "শুক্রবার")) {
            assertThat(dateOf(form)).isEqualTo(friday)
        }
    }

    @Test
    fun `reads today's own weekday as today, and leaves the rolling to the caller`() {
        assertThat(dateOf("tuesday")).isEqualTo(LocalDate.of(2026, 3, 10))
        assertThat(parser.read("tuesday")?.rollsByWeek).isTrue()
    }

    @Test
    fun `this friday is the coming friday`() {
        for (form in listOf("this friday", "ei shukrobar", "এই শুক্রবার", "coming friday")) {
            assertThat(dateOf(form)).isEqualTo(LocalDate.of(2026, 3, 13))
        }
    }

    @Test
    fun `next friday is the friday after the coming one`() {
        for (form in listOf("next friday", "agami shukrobar", "আগামী শুক্রবার", "next shukrobar")) {
            assertThat(dateOf(form)).isEqualTo(LocalDate.of(2026, 3, 20))
        }
    }

    @Test
    fun `on a wednesday this friday is two days away and next friday is nine`() {
        val wednesday = parserOn(month = 3, day = 11)
        assertThat(wednesday.parse("this friday")?.date).isEqualTo(LocalDate.of(2026, 3, 13))
        assertThat(wednesday.parse("next friday")?.date).isEqualTo(LocalDate.of(2026, 3, 20))
    }

    @Test
    fun `a weekday pushed to next week must not be pushed again`() {
        assertThat(parser.read("next friday")?.rollsByWeek).isFalse()
        assertThat(parser.read("friday")?.rollsByWeek).isTrue()
    }

    // ------------------------------------------------------------- weeks and months

    @Test
    fun `reads next week in all four languages`() {
        for (form in listOf("next week", "agami soptah", "আগামী সপ্তাহ", "samner soptah")) {
            assertThat(dateOf(form)).isEqualTo(LocalDate.of(2026, 3, 17))
        }
    }

    @Test
    fun `reads next month in all four languages`() {
        for (form in listOf("next month", "agami mash", "আগামী মাস", "next mas")) {
            assertThat(dateOf(form)).isEqualTo(LocalDate.of(2026, 4, 10))
        }
    }

    // --------------------------------------------------------------- day of the month

    @Test
    fun `reads a day of the month that is still ahead as this month's`() {
        assertThat(dateOf("20 tarik")).isEqualTo(LocalDate.of(2026, 3, 20))
        assertThat(dateOf("masher 20 tarik")).isEqualTo(LocalDate.of(2026, 3, 20))
    }

    @Test
    fun `reads a day of the month that has passed as next month's`() {
        for (form in listOf("5 tarik", "masher 5 tarik", "the 5 th of the month")) {
            assertThat(dateOf(form)).isEqualTo(LocalDate.of(2026, 4, 5))
        }
    }

    @Test
    fun `reads the Bengali day of the month`() {
        val text = BengaliNumerals.toAscii("মাসের ৫ তারিখ")
        assertThat(dateOf(text)).isEqualTo(LocalDate.of(2026, 4, 5))
    }

    @Test
    fun `reads today's own date as today`() {
        assertThat(dateOf("10 tarikh")).isEqualTo(LocalDate.of(2026, 3, 10))
    }

    @Test
    fun `pins a day of the month to next month when Abu says so`() {
        assertThat(dateOf("agami masher 20 tarik")).isEqualTo(LocalDate.of(2026, 4, 20))
        assertThat(dateOf("next month 20 date")).isEqualTo(LocalDate.of(2026, 4, 20))
    }

    @Test
    fun `skips a month too short for the day named`() {
        // April has thirty days, so the thirty-first of "this" month is in May.
        assertThat(parserOn(month = 4, day = 1).parse("31 tarik")?.date)
            .isEqualTo(LocalDate.of(2026, 5, 31))
    }

    @Test
    fun `takes the whole date phrase out of the remainder`() {
        assertThat(parser.parse("the 5 th of the month ahmed ke call")?.remainder)
            .isEqualTo("ahmed ke call")
        assertThat(parser.parse("masher 20 tarik meeting")?.remainder).isEqualTo("meeting")
    }

    // ------------------------------------------------------------ remainder and refusal

    @Test
    fun `leaves everything it did not claim`() {
        val match = parser.parse("ahmed ke call korar reminder kal")
        assertThat(match?.remainder).isEqualTo("ahmed ke call korar reminder")
        assertThat(match?.matched).isEqualTo("kal")
    }

    @Test
    fun `swallows the preposition in front of a day`() {
        val match = parser.parse("on monday call ahmed")
        assertThat(match?.date).isEqualTo(LocalDate.of(2026, 3, 16))
        assertThat(match?.remainder).isEqualTo("call ahmed")
    }

    @Test
    fun `finds nothing in a sentence that names no day`() {
        assertThat(parser.parse("call ahmed")).isNull()
        assertThat(parser.parse("")).isNull()
        assertThat(parser.parse("   ")).isNull()
    }

    @Test
    fun `does not mistake a plain count for a day of the month`() {
        assertThat(parser.parse("call 3 people")).isNull()
        assertThat(parser.parse("12 * 4")).isNull()
    }

    @Test
    fun `does not mistake an amount of money for a date`() {
        assertThat(parser.parse("5 lakh taka")).isNull()
        assertThat(parser.parse("20 thousand taka expense")).isNull()
    }

    @Test
    fun `is surer of a day Abu named than of a whole week`() {
        val named = parser.parse("tomorrow")?.confidence ?: 0f
        val span = parser.parse("next week")?.confidence ?: 0f
        assertThat(named).isGreaterThan(span)
        assertThat(named).isAtMost(1f)
        assertThat(span).isAtLeast(0f)
    }
}
