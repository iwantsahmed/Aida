package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.DayOfWeek
import org.junit.Test

/**
 * The parser reads canonical tokens, so every test here states the text the way the normalizer
 * hands it over: "প্রতি সোমবার" has already become "প্রতি monday", and "prroti soptah" has
 * already become "prroti week". The raw spellings are asserted alongside, because a caller
 * holding Abu's own words must get the same answer as one holding the folded ones.
 */
class RecurrenceExpressionParserTest {

    private fun ruleOf(text: String): RecurrenceRule? = RecurrenceExpressionParser.parse(text)?.rule

    // ---------------------------------------------------------------- daily

    @Test
    fun `reads every day in all three languages`() {
        assertThat(ruleOf("every day")).isEqualTo(RecurrenceRule.Daily())
        assertThat(ruleOf("each day")).isEqualTo(RecurrenceRule.Daily())
        assertThat(ruleOf("prroti din")).isEqualTo(RecurrenceRule.Daily())
        assertThat(ruleOf("প্রতি দিন")).isEqualTo(RecurrenceRule.Daily())
    }

    @Test
    fun `reads the one-word Bengali forms of every day`() {
        assertThat(ruleOf("protidin website check do task create")).isEqualTo(RecurrenceRule.Daily())
        assertThat(ruleOf("প্রতিদিন backup")).isEqualTo(RecurrenceRule.Daily())
        assertThat(ruleOf("roj office khulte hobe")).isEqualTo(RecurrenceRule.Daily())
    }

    // ---------------------------------------------------------------- weekly

    @Test
    fun `reads every week in all three languages`() {
        assertThat(ruleOf("every week")).isEqualTo(RecurrenceRule.Weekly())
        assertThat(ruleOf("prroti week")).isEqualTo(RecurrenceRule.Weekly())
        assertThat(ruleOf("prroti soptah")).isEqualTo(RecurrenceRule.Weekly())
        assertThat(ruleOf("প্রতি সপ্তাহে")).isEqualTo(RecurrenceRule.Weekly())
    }

    /** A weekday spelled any of the ways Abu spells it lands on the same day. */
    @Test
    fun `reads a named weekday in all three languages`() {
        val monday = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY))
        assertThat(ruleOf("every monday")).isEqualTo(monday)
        assertThat(ruleOf("প্রতি monday")).isEqualTo(monday)
        assertThat(ruleOf("prroti shombar")).isEqualTo(monday)
        assertThat(ruleOf("prati সোমবার")).isEqualTo(monday)
    }

    @Test
    fun `reads more than one weekday in a single rule`() {
        assertThat(ruleOf("every monday and thursday review rayluxeo")).isEqualTo(
            RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        )
        assertThat(ruleOf("প্রতি shombar ও budhbar")).isEqualTo(
            RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        )
    }

    /**
     * The day stays in the sentence and only the frequency word is taken out. A repeat says how
     * often something comes back and never when it starts, and the date parser is what turns
     * "monday" into the Monday the series opens on.
     */
    @Test
    fun `leaves the first weekday behind for the date parser`() {
        val parsed = requireNotNull(RecurrenceExpressionParser.parse("every monday remind me to review rayluxeo"))
        assertThat(parsed.matched).isEqualTo("every")
        assertThat(parsed.remainder).isEqualTo("monday remind me to review rayluxeo")
    }

    @Test
    fun `takes the later weekdays out and keeps only the first`() {
        val parsed = requireNotNull(RecurrenceExpressionParser.parse("every monday and thursday review rayluxeo"))
        assertThat(parsed.remainder).isEqualTo("monday review rayluxeo")
        assertThat(parsed.matched).isEqualTo("every and thursday")
    }

    // ---------------------------------------------------------------- weekdays

    @Test
    fun `reads every weekday as its own shape rather than as five days`() {
        assertThat(ruleOf("every weekday")).isEqualTo(RecurrenceRule.Weekdays())
        assertThat(ruleOf("every weekdays")).isEqualTo(RecurrenceRule.Weekdays())
        assertThat(ruleOf("প্রতি কর্মদিবস")).isEqualTo(RecurrenceRule.Weekdays())
    }

    /** Monday to Friday is a fixed set of days; a count in front of it says nothing. */
    @Test
    fun `refuses an interval on every weekday`() {
        assertThat(ruleOf("every 2 weekdays")).isNull()
    }

    // ---------------------------------------------------------------- monthly and yearly

    @Test
    fun `reads every month in all three languages`() {
        assertThat(ruleOf("every month")).isEqualTo(RecurrenceRule.Monthly())
        assertThat(ruleOf("prroti mash")).isEqualTo(RecurrenceRule.Monthly())
        assertThat(ruleOf("প্রতি মাসে")).isEqualTo(RecurrenceRule.Monthly())
    }

    @Test
    fun `reads every year in all three languages`() {
        assertThat(ruleOf("every year")).isEqualTo(RecurrenceRule.Yearly())
        assertThat(ruleOf("prroti bochor")).isEqualTo(RecurrenceRule.Yearly())
        assertThat(ruleOf("প্রতি বছরে")).isEqualTo(RecurrenceRule.Yearly())
    }

    // ---------------------------------------------------------------- counted repeats

    @Test
    fun `reads every n days as its own shape`() {
        assertThat(ruleOf("every 10 days backup nite hobe")).isEqualTo(RecurrenceRule.EveryNDays(10))
        assertThat(ruleOf("প্রতি 3 দিন")).isEqualTo(RecurrenceRule.EveryNDays(3))
        // One day apart is simply daily; the counted shape would say the same thing twice.
        assertThat(ruleOf("every 1 day")).isEqualTo(RecurrenceRule.Daily())
    }

    @Test
    fun `reads a count of weeks, months and years as an interval`() {
        assertThat(ruleOf("every 2 weeks")).isEqualTo(RecurrenceRule.Weekly(interval = 2))
        assertThat(ruleOf("every 3 months")).isEqualTo(RecurrenceRule.Monthly(interval = 3))
        assertThat(ruleOf("every 2 years")).isEqualTo(RecurrenceRule.Yearly(interval = 2))
        assertThat(ruleOf("every 2 friday")).isEqualTo(
            RecurrenceRule.Weekly(days = setOf(DayOfWeek.FRIDAY), interval = 2)
        )
    }

    @Test
    fun `reads every other period as every second one`() {
        assertThat(ruleOf("every other day")).isEqualTo(RecurrenceRule.EveryNDays(2))
        assertThat(ruleOf("every other week")).isEqualTo(RecurrenceRule.Weekly(interval = 2))
        assertThat(ruleOf("every alternate month")).isEqualTo(RecurrenceRule.Monthly(interval = 2))
    }

    /** A count that large is a misread number, not a schedule Abu meant. */
    @Test
    fun `refuses an absurd interval rather than accepting it`() {
        assertThat(ruleOf("every 4000 days")).isNull()
        assertThat(ruleOf("every 0 days")).isNull()
    }

    @Test
    fun `reads through the article between the frequency and the period`() {
        assertThat(ruleOf("every a week")).isEqualTo(RecurrenceRule.Weekly())
    }

    // ---------------------------------------------------------------- what is not a repeat

    /**
     * In English these are adjectives at least as often as adverbs. "a daily report" is a title
     * and "review the inbox daily" is a repeat, and the position is what separates them.
     */
    @Test
    fun `reads a trailing adverb as a repeat and the same word in front of a noun as a name`() {
        assertThat(ruleOf("review the inbox daily")).isEqualTo(RecurrenceRule.Daily())
        assertThat(ruleOf("back up the site weekly")).isEqualTo(RecurrenceRule.Weekly())
        assertThat(ruleOf("create a daily report")).isNull()
        assertThat(ruleOf("create a monthly invoice for ahmed")).isNull()
    }

    @Test
    fun `refuses a frequency word that names no period`() {
        assertThat(ruleOf("every client er invoice search")).isNull()
        assertThat(ruleOf("every")).isNull()
        assertThat(ruleOf("প্রতি rayluxeo")).isNull()
    }

    @Test
    fun `leaves a one-off alone`() {
        assertThat(RecurrenceExpressionParser.parse("next friday website complete do task create")).isNull()
        assertThat(RecurrenceExpressionParser.parse("remind me tomorrow at 10 to call ahmed")).isNull()
        assertThat(RecurrenceExpressionParser.parse("")).isNull()
        assertThat(RecurrenceExpressionParser.parse("   ")).isNull()
    }

    // ---------------------------------------------------------------- what it hands back

    @Test
    fun `takes the whole phrase out when nothing in it names a day`() {
        val parsed = requireNotNull(RecurrenceExpressionParser.parse("every 10 days backup nite hobe"))
        assertThat(parsed.matched).isEqualTo("every 10 days")
        assertThat(parsed.remainder).isEqualTo("backup nite hobe")
    }

    @Test
    fun `takes a one-word repeat out of the middle of a sentence`() {
        val parsed = requireNotNull(RecurrenceExpressionParser.parse("protidin website check do task create"))
        assertThat(parsed.matched).isEqualTo("protidin")
        assertThat(parsed.remainder).isEqualTo("website check do task create")
    }

    // ---------------------------------------------------------------- one vocabulary

    /**
     * The weekday names are read from [Vocabulary], not from a list of their own. A second copy
     * is the defect AGENTS.md §7.12 exists to forbid, and this is what would fail if one were
     * ever introduced and then allowed to drift.
     */
    @Test
    fun `reads every weekday spelling the shared vocabulary knows`() {
        for ((spelling, day) in Vocabulary.weekdays) {
            assertThat(ruleOf("every $spelling")).isEqualTo(RecurrenceRule.Weekly(days = setOf(day)))
        }
    }

    @Test
    fun `the shared vocabulary covers all seven days in both scripts`() {
        assertThat(Vocabulary.weekdays.values.toSet()).containsExactlyElementsIn(DayOfWeek.entries)
        assertThat(Vocabulary.weekdays["শুক্রবার"]).isEqualTo(DayOfWeek.FRIDAY)
        assertThat(Vocabulary.weekdays["shukrobar"]).isEqualTo(DayOfWeek.FRIDAY)
        assertThat(Vocabulary.weekdays[CanonicalToken.FRIDAY]).isEqualTo(DayOfWeek.FRIDAY)
    }

    @Test
    fun `the shared vocabulary answers for a family it has and for one it does not`() {
        assertThat(Vocabulary.formsOf(CanonicalToken.WEEK)).contains("soptah")
        assertThat(Vocabulary.formsOf(CanonicalToken.WEEK)).contains(CanonicalToken.WEEK)
        assertThat(Vocabulary.formsOf("nothing_folds_here")).containsExactly("nothing_folds_here")
    }
}
