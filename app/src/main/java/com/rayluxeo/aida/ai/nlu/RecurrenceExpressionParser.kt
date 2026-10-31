package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.DayOfWeek

/**
 * A repeat Abu stated, and his sentence with the frequency words taken back out.
 *
 * [remainder] keeps the first weekday he named: "every Monday" leaves "monday" behind. A repeat
 * says how OFTEN something comes back and never when it starts, and which Monday the series
 * opens on is a date question that [RelativeDateParser] is the one thing in Mira able to answer.
 * Two parsers each claiming half of "every Monday" would leave the series with no first
 * occurrence at all. Every other form is consumed whole, because "every day" and "প্রতি মাসে"
 * leave nothing a date parser could use.
 */
data class ParsedRecurrence(
    val rule: RecurrenceRule,
    val remainder: String,
    val matched: String,
)

/**
 * Reads "every Monday", "প্রতিদিন", "prroti mashe" and "every 10 days" as a [RecurrenceRule].
 *
 * An object rather than an injected class, like [ArithmeticExpression]: it holds no state, has
 * no collaborator and reads no clock, so there is nothing for a caller to configure or
 * substitute.
 *
 * It runs before the day, the span and the amount are read out of a sentence, and that order is
 * load-bearing twice over. A counted repeat puts a number beside a period word — "every 5
 * months" — which is the same shape as a day of the month, and [RelativeDateParser] reading it
 * first files a quarterly review for the fifth. A repeat nobody claimed also leaves a stray
 * "every" sitting at the front of the task's own title.
 *
 * The weekday names come from [Vocabulary.weekdays]. The frequency words do not, and that is
 * deliberate: the Banglish "proti" reduces to the consonant frame "prt", which "part", "port"
 * and "party" also reduce to, so putting it in the shared table would fold three ordinary
 * English words onto "every". They are listed here instead, in the canonical token the
 * normalizer produces AND in the spellings Abu types, which is how [RelativeDateParser] carries
 * its own date vocabulary for the same reason.
 */
object RecurrenceExpressionParser {

    fun parse(text: String): ParsedRecurrence? {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val match = tokens.indices.firstNotNullOfOrNull { index -> readCounted(tokens, index) }
            ?: tokens.indices.firstNotNullOfOrNull { index -> readAdverb(tokens, index) }
            ?: return null

        return ParsedRecurrence(
            rule = match.rule,
            remainder = join(tokens) { it !in match.claimed },
            matched = join(tokens) { it in match.claimed },
        )
    }

    /**
     * The "every ..." forms, which are the only ones that can carry an interval. Returns null
     * when the word is not followed by a period Mira can repeat on — "every client er invoice"
     * names a repeat of nothing.
     */
    private fun readCounted(tokens: List<String>, index: Int): Match? {
        if (tokens[index] !in EVERY_WORDS) return null
        val claimed = mutableSetOf(index)
        var cursor = index + 1
        while (tokens.getOrNull(cursor) in ARTICLES) {
            claimed += cursor
            cursor++
        }

        val interval = readInterval(tokens, cursor) ?: return null
        claimed += interval.claimed
        cursor += interval.claimed.size

        val unit = tokens.getOrNull(cursor) ?: return null
        Vocabulary.weekdays[unit]?.let { first ->
            return Match(
                rule = RecurrenceRule.Weekly(
                    days = readExtraDays(tokens, cursor, first, claimed),
                    interval = interval.value,
                ),
                claimed = claimed,
            )
        }

        claimed += cursor
        return when (unit) {
            // Every day is Daily; every third day is its own shape, because it counts from the
            // occurrence just gone rather than from a fixed cadence.
            in DAY_WORDS -> counted(interval.value, claimed)
            in WEEK_WORDS -> Match(RecurrenceRule.Weekly(interval = interval.value), claimed)
            in MONTH_WORDS -> Match(RecurrenceRule.Monthly(interval = interval.value), claimed)
            in YEAR_WORDS -> Match(RecurrenceRule.Yearly(interval = interval.value), claimed)
            // Monday to Friday is a fixed set of days; an interval on top of it means nothing.
            in WEEKDAY_WORDS -> if (interval.value == 1) Match(RecurrenceRule.Weekdays(), claimed) else null
            else -> null
        }
    }

    private fun counted(interval: Int, claimed: Set<Int>): Match =
        if (interval == 1) {
            Match(RecurrenceRule.Daily(), claimed)
        } else {
            Match(RecurrenceRule.EveryNDays(interval), claimed)
        }

    /**
     * How many periods apart, and how many tokens said so. A count written out in words is not
     * read here: "every three days" is a shape Abu types as "every 3 days", and the number
     * parsers that would read the word belong to money and to spans of time.
     */
    private fun readInterval(tokens: List<String>, cursor: Int): Interval? {
        val token = tokens.getOrNull(cursor) ?: return null
        if (token in ALTERNATE_WORDS) return Interval(value = 2, claimed = setOf(cursor))
        val count = token.toIntOrNull() ?: return Interval(value = 1, claimed = emptySet())
        return if (count in 1..MAX_INTERVAL) Interval(count, setOf(cursor)) else null
    }

    /**
     * "every Monday and Thursday". The days after the first are claimed along with the words
     * joining them; the first is left in the sentence, for the reason [ParsedRecurrence] gives.
     */
    private fun readExtraDays(
        tokens: List<String>,
        firstIndex: Int,
        first: DayOfWeek,
        claimed: MutableSet<Int>,
    ): Set<DayOfWeek> {
        val days = mutableSetOf(first)
        var cursor = firstIndex + 1
        while (tokens.getOrNull(cursor) in DAY_JOINERS) {
            val next = tokens.getOrNull(cursor + 1) ?: break
            val day = Vocabulary.weekdays[next] ?: break
            days += day
            claimed += cursor
            claimed += cursor + 1
            cursor += 2
        }
        return days
    }

    /**
     * The one-word repeats. The Bengali ones are adverbs wherever they sit, but the English ones
     * are adjectives at least as often — "a daily report" is a title, "review the inbox daily"
     * is a repeat — so those count only at the end of the sentence, which is where the adverb
     * lives and the adjective cannot.
     */
    private fun readAdverb(tokens: List<String>, index: Int): Match? {
        ADVERBS[tokens[index]]?.let { return Match(it, setOf(index)) }
        if (index != tokens.lastIndex) return null
        return TRAILING_ADVERBS[tokens[index]]?.let { Match(it, setOf(index)) }
    }

    private fun join(tokens: List<String>, keep: (Int) -> Boolean): String =
        tokens.filterIndexed { index, _ -> keep(index) }.joinToString(separator = " ")

    private data class Match(val rule: RecurrenceRule, val claimed: Set<Int>)

    private data class Interval(val value: Int, val claimed: Set<Int>)

    /** A repeat measured in more than a year of periods is a misread number, not a schedule. */
    private const val MAX_INTERVAL = 365

    /** The words that say a request comes back. */
    private val EVERY_WORDS = setOf(
        "every",
        "each",
        "proti",
        "prroti",
        "prati",
        "protti",
        "protyek",
        "prottek",
        "প্রতি",
        "প্রত্যেক",
    )

    /** "every other week" is every second one. */
    private val ALTERNATE_WORDS = setOf("other", "alternate", "alternative")

    private val ARTICLES = setOf("a", "an", "the")

    /** What joins two days of one weekly rule: "every Monday and Thursday", "প্রতি সোম ও বুধবার". */
    private val DAY_JOINERS = setOf("and", "o", "ar", "ebong", "এবং", "ও", "আর")

    private val DAY_WORDS = setOf("day", "days", "din", "dine", "diner", "দিন", "দিনে", "দিনের")

    private val WEEK_WORDS = Vocabulary.formsOf(CanonicalToken.WEEK)

    private val MONTH_WORDS = Vocabulary.formsOf(CanonicalToken.MONTH)

    /**
     * No canonical token folds a year, because nothing else in Mira has needed one. The
     * spellings sit here rather than going into the shared table, so that a fold nobody asked
     * for cannot reach the rest of the pipeline.
     */
    private val YEAR_WORDS = setOf(
        "year", "years", "bochor", "bosor", "bochore", "bochorer", "বছর", "বছরে", "বছরের",
    )

    private val WEEKDAY_WORDS = setOf("weekday", "weekdays", "kormodibos", "কর্মদিবস")

    /** Bengali repeats that are one word and are adverbs in any position. */
    private val ADVERBS: Map<String, RecurrenceRule> = mapOf(
        "protidin" to RecurrenceRule.Daily(),
        "protidini" to RecurrenceRule.Daily(),
        "roj" to RecurrenceRule.Daily(),
        "rojkar" to RecurrenceRule.Daily(),
        "প্রতিদিন" to RecurrenceRule.Daily(),
        "প্রতিদিনই" to RecurrenceRule.Daily(),
        "প্রত্যেকদিন" to RecurrenceRule.Daily(),
        "রোজ" to RecurrenceRule.Daily(),
    )

    /** The same idea in words that are also adjectives, so only the end of a sentence counts. */
    private val TRAILING_ADVERBS: Map<String, RecurrenceRule> = mapOf(
        "daily" to RecurrenceRule.Daily(),
        "everyday" to RecurrenceRule.Daily(),
        "weekly" to RecurrenceRule.Weekly(),
        "saptahik" to RecurrenceRule.Weekly(),
        "সাপ্তাহিক" to RecurrenceRule.Weekly(),
        "monthly" to RecurrenceRule.Monthly(),
        "mashik" to RecurrenceRule.Monthly(),
        "মাসিক" to RecurrenceRule.Monthly(),
        "yearly" to RecurrenceRule.Yearly(),
        "annually" to RecurrenceRule.Yearly(),
        "barshik" to RecurrenceRule.Yearly(),
        "বার্ষিক" to RecurrenceRule.Yearly(),
    )
}
