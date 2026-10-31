package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.core.common.MiraClock
import java.time.Duration
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the time of day, and the spans of time a timer is set for, out of an utterance.
 *
 * A part of the day is the key to the whole thing. Bengali states the hour on a twelve-hour
 * dial and lets "সকাল", "বিকেল" or "রাত" say which half is meant, so "bikal 4" is 16:00 and
 * "raat 10" is 22:00 while "shokal 10" is plain 10:00. When nothing says which half, the hour
 * is a guess: it resolves to the next time that hour comes round and
 * [ParsedTime.meridiemWasExplicit] is false so the caller knows it was one.
 *
 * As with [RelativeDateParser], both the canonical tokens and Abu's own spellings are listed.
 */
@Singleton
class TimeExpressionParser @Inject constructor(
    private val clock: MiraClock,
) {

    /**
     * The time, plus the two things a [LocalTime] cannot carry on its own: whether an hour was
     * named at all, and — when it was named without anything fixing morning or evening — the
     * hour exactly as Abu said it. [MiraDateTimeParser] needs the second one because a guess
     * made against today is the wrong guess once a date has been attached to it.
     */
    data class Reading(
        val time: ParsedTime,
        /** False when only a stretch of the day was named, as in "kal shokal" with no hour. */
        val namesAnHour: Boolean,
        /** 1..12 as written, when nothing said which half of the day it belongs to. */
        val unqualifiedHour: Int?,
    )

    /**
     * [anchored] lets a caller that has already found a date say so. A bare number is only an
     * hour when something else in the sentence established that a time is being discussed —
     * otherwise "call 3 people" would be scheduled for three o'clock.
     */
    fun parse(text: String, anchored: Boolean = false): ParsedTime? = read(text, anchored)?.time

    fun read(text: String, anchored: Boolean = false): Reading? {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val consumed = BooleanArray(tokens.size)

        val dayPart = matchDayPart(tokens, consumed)
        val clockTime = matchClockTime(tokens, consumed, anchored = anchored || dayPart != null)
        val resolved = when {
            clockTime != null -> resolveHour(clockTime, dayPart)
            dayPart != null -> resolveDayPart(dayPart)
            else -> return null
        }

        return Reading(
            time = ParsedTime(
                time = resolved.time,
                remainder = join(tokens, consumed, keep = false),
                matched = join(tokens, consumed, keep = true),
                meridiemWasExplicit = resolved.meridiemWasExplicit,
            ),
            namesAnHour = resolved.namesAnHour,
            unqualifiedHour = resolved.unqualifiedHour,
        )
    }

    /** "20 minute", "in half an hour", "২ ঘণ্টা পরে" — a span, never a point on the clock. */
    fun parseDuration(text: String): ParsedDuration? {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        for (index in tokens.indices) {
            val span = matchHalfUnit(tokens, index) ?: matchCountedUnit(tokens, index) ?: continue
            val consumed = BooleanArray(tokens.size)
            for (position in span.claimed) consumed[position] = true
            return ParsedDuration(
                duration = span.duration,
                remainder = join(tokens, consumed, keep = false),
                matched = join(tokens, consumed, keep = true),
            )
        }
        return null
    }

    private fun resolveDayPart(dayPart: DayPart): Resolved = Resolved(
        time = LocalTime.of(dayPart.defaultHour, 0),
        // Noon is an hour; morning is a stretch of one. Only the first is a time Abu would
        // recognise as the one he asked for.
        namesAnHour = dayPart == DayPart.NOON,
        meridiemWasExplicit = true,
        unqualifiedHour = null,
    )

    private fun resolveHour(clockTime: ClockTime, dayPart: DayPart?): Resolved {
        val qualified = when {
            clockTime.meridiem != null -> applyMeridiem(clockTime.hour, clockTime.meridiem)
            dayPart != null -> dayPart.qualify(clockTime.hour)
            clockTime.hour !in 1..HOURS_ON_A_DIAL -> clockTime.hour
            else -> null
        }
        if (qualified != null) {
            return Resolved(
                time = LocalTime.of(qualified, clockTime.minute),
                namesAnHour = true,
                meridiemWasExplicit = true,
                unqualifiedHour = null,
            )
        }
        return Resolved(
            time = nextOccurrenceToday(clockTime.hour, clockTime.minute),
            namesAnHour = true,
            meridiemWasExplicit = false,
            unqualifiedHour = clockTime.hour,
        )
    }

    private fun nextOccurrenceToday(hour: Int, minute: Int): LocalTime {
        val morning = LocalTime.of(hour % HOURS_ON_A_DIAL, minute)
        val evening = LocalTime.of(hour % HOURS_ON_A_DIAL + HOURS_ON_A_DIAL, minute)
        val now = clock.localNow().toLocalTime()
        return when {
            morning.isAfter(now) -> morning
            evening.isAfter(now) -> evening
            else -> morning
        }
    }

    private fun applyMeridiem(hour: Int, meridiem: Meridiem): Int = when {
        meridiem == Meridiem.AM -> if (hour == HOURS_ON_A_DIAL) 0 else hour % HOURS_ON_A_DIAL
        hour == HOURS_ON_A_DIAL -> HOURS_ON_A_DIAL
        else -> (hour % HOURS_ON_A_DIAL) + HOURS_ON_A_DIAL
    }

    private fun matchDayPart(tokens: List<String>, consumed: BooleanArray): DayPart? {
        for (index in tokens.indices) {
            if (consumed[index]) continue
            val part = DAY_PARTS[tokens[index]] ?: continue
            consumed[index] = true
            return part
        }
        return null
    }

    /**
     * Finds the hour, preferring the forms that state themselves outright. A number written as
     * a word is only ever read as an hour next to a marker — "at ten" is a time, "ten people"
     * is a count, and English uses the same word for both.
     */
    private fun matchClockTime(
        tokens: List<String>,
        consumed: BooleanArray,
        anchored: Boolean,
    ): ClockTime? {
        for (index in tokens.indices) {
            if (consumed[index]) continue

            val halfPast = matchHalfPast(tokens, consumed, index)
            if (halfPast != null) return halfPast

            val written = parseWrittenTime(tokens[index])
            if (written != null) {
                val meridiem = meridiemAt(tokens, consumed, index + 1)
                val markers = if (meridiem == null) markerRun(tokens, consumed, index + 1) else 0
                val preposition = prepositionAt(tokens, consumed, index - 1)
                // "10.30" is also how an amount of money is written. The dotted form needs
                // something saying a time is meant; "10:30" says it on its own.
                if (written.dotted && meridiem == null && markers == 0 && !preposition && !anchored) continue
                claim(consumed, index..index, meridiem != null, markers, preposition)
                return written.time.copy(meridiem = meridiem)
            }

            val number = numberAt(tokens, index)?.takeIf { it.length == 1 } ?: continue
            val hour = number.value.takeIf { it in 0..LAST_HOUR } ?: continue
            val meridiem = meridiemAt(tokens, consumed, index + 1)
            val markers = if (meridiem == null) markerRun(tokens, consumed, index + 1) else 0
            val preposition = prepositionAt(tokens, consumed, index - 1)
            val stated = meridiem != null || markers > 0 || preposition
            if (!stated && !(anchored && number.isDigits)) continue
            claim(consumed, index..index, meridiem != null, markers, preposition)
            return ClockTime(hour, 0, meridiem)
        }
        return null
    }

    /** "half past ten", "সাড়ে ১০টায়" — the same reading, said two ways. */
    private fun matchHalfPast(tokens: List<String>, consumed: BooleanArray, index: Int): ClockTime? {
        val hourIndex = when {
            tokens[index] in HALF_WORDS && tokens.getOrNull(index + 1) in PAST_WORDS -> index + 2
            tokens[index] in HALF_PAST_WORDS -> index + 1
            else -> return null
        }
        if (hourIndex >= tokens.size || consumed[hourIndex]) return null
        val number = numberAt(tokens, hourIndex)?.takeIf { it.length == 1 } ?: return null
        val hour = number.value.takeIf { it in 0..LAST_HOUR } ?: return null

        val meridiem = meridiemAt(tokens, consumed, hourIndex + 1)
        val markers = if (meridiem == null) markerRun(tokens, consumed, hourIndex + 1) else 0
        val preposition = prepositionAt(tokens, consumed, index - 1)
        claim(consumed, index..hourIndex, meridiem != null, markers, preposition)
        return ClockTime(hour, MINUTES_IN_HALF_HOUR, meridiem)
    }

    /** Marks the hour and everything that came with it: its markers, its meridiem, its preposition. */
    private fun claim(
        consumed: BooleanArray,
        span: IntRange,
        takesMeridiem: Boolean,
        markers: Int,
        takesPreposition: Boolean,
    ) {
        for (position in span) consumed[position] = true
        if (takesMeridiem) consumed[span.last + 1] = true
        for (position in span.last + 1 until span.last + 1 + markers) consumed[position] = true
        if (takesPreposition) consumed[span.first - 1] = true
    }

    /** Counts the hour markers trailing a number: "10 tay", "10 o clock", "10 ta". */
    private fun markerRun(tokens: List<String>, consumed: BooleanArray, from: Int): Int {
        var cursor = from
        while (cursor < tokens.size && !consumed[cursor]) {
            val token = tokens[cursor]
            val opensOClock = token == O_CLOCK_LEAD && tokens.getOrNull(cursor + 1) == O_CLOCK
            if (token !in HOUR_MARKERS && !opensOClock) break
            cursor++
        }
        return cursor - from
    }

    private fun parseWrittenTime(token: String): WrittenTime? {
        val separator = token.firstOrNull { it == ':' || it == '.' } ?: return null
        val parts = token.split(separator)
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull()?.takeIf { it in 0..LAST_HOUR } ?: return null
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0..LAST_MINUTE } ?: return null
        // A dot only separates hours from minutes when the minutes are written in full: "5.5"
        // is half of something, never five past five.
        if (separator == '.' && parts[1].length != MINUTE_DIGITS) return null
        return WrittenTime(ClockTime(hour, minute, meridiem = null), dotted = separator == '.')
    }

    private fun matchHalfUnit(tokens: List<String>, index: Int): DurationClaim? {
        if (tokens[index] !in HALF_WORDS) return null
        var cursor = index + 1
        while (cursor < tokens.size && tokens[cursor] in ARTICLES) cursor++
        val unit = DURATION_UNITS[tokens.getOrNull(cursor)] ?: return null
        val claimed = (index..cursor).toMutableSet()
        if (index > 0 && tokens[index - 1] in DURATION_LEAD_INS) claimed += index - 1
        if (tokens.getOrNull(cursor + 1) in DURATION_TRAILERS) claimed += cursor + 1
        return DurationClaim(Duration.of(1, unit).dividedBy(2), claimed)
    }

    private fun matchCountedUnit(tokens: List<String>, index: Int): DurationClaim? {
        val number = numberAt(tokens, index)?.takeIf { it.value > 0 } ?: return null
        val unitIndex = index + number.length
        val unit = DURATION_UNITS[tokens.getOrNull(unitIndex)] ?: return null
        val leadIn = index > 0 && tokens[index - 1] in DURATION_LEAD_INS
        val trailer = tokens.getOrNull(unitIndex + 1) in DURATION_TRAILERS
        // "2 hour" reads just as easily as a clock time, so an hour count needs a word marking
        // it as a span. A count of minutes or seconds never names an hour of the day.
        if (!leadIn && !trailer && unit == ChronoUnit.HOURS) return null
        val claimed = (index..unitIndex).toMutableSet()
        if (leadIn) claimed += index - 1
        if (trailer) claimed += unitIndex + 1
        return DurationClaim(Duration.of(number.value.toLong(), unit), claimed)
    }

    /**
     * A number written as digits, as one English word, or as a tens-and-ones pair
     * ("forty five"). Bengali words for numbers are [BanglaNumberParser]'s subject; the digits
     * it would produce arrive here already converted.
     */
    private fun numberAt(tokens: List<String>, index: Int): NumberSpan? {
        val token = tokens.getOrNull(index) ?: return null
        token.toIntOrNull()?.let { return NumberSpan(it, length = 1, isDigits = true) }
        ENGLISH_TENS[token]?.let { tens ->
            val ones = tokens.getOrNull(index + 1)?.let { ENGLISH_ONES[it] }
            return if (ones != null && ones in 1..LAST_ONE) {
                NumberSpan(tens + ones, length = 2, isDigits = false)
            } else {
                NumberSpan(tens, length = 1, isDigits = false)
            }
        }
        return ENGLISH_ONES[token]?.let { NumberSpan(it, length = 1, isDigits = false) }
    }

    private fun meridiemAt(tokens: List<String>, consumed: BooleanArray, index: Int): Meridiem? {
        if (index >= tokens.size || consumed[index]) return null
        return MERIDIEMS[tokens[index]]
    }

    private fun prepositionAt(tokens: List<String>, consumed: BooleanArray, index: Int): Boolean =
        index >= 0 && !consumed[index] && tokens[index] in TIME_PREPOSITIONS

    private fun join(tokens: List<String>, consumed: BooleanArray, keep: Boolean): String =
        tokens.filterIndexed { index, _ -> consumed[index] == keep }.joinToString(separator = " ")

    private data class Resolved(
        val time: LocalTime,
        val namesAnHour: Boolean,
        val meridiemWasExplicit: Boolean,
        val unqualifiedHour: Int?,
    )

    private data class ClockTime(val hour: Int, val minute: Int, val meridiem: Meridiem?)

    private data class WrittenTime(val time: ClockTime, val dotted: Boolean)

    private data class NumberSpan(val value: Int, val length: Int, val isDigits: Boolean)

    private data class DurationClaim(val duration: Duration, val claimed: Set<Int>)

    private enum class Meridiem { AM, PM }

    /**
     * A part of the day, and what it does to a bare hour beside it. "raat 10" is 22:00 while
     * "shokal 10" is 10:00, which is the whole reason day parts are tracked separately.
     */
    private enum class DayPart(val defaultHour: Int) {
        MORNING(9) {
            override fun qualify(hour: Int): Int = if (hour == 12) 0 else hour
        },
        NOON(12) {
            override fun qualify(hour: Int): Int = if (hour in 1..4) hour + 12 else hour
        },
        AFTERNOON(15) {
            override fun qualify(hour: Int): Int = if (hour in 1..11) hour + 12 else hour
        },
        EVENING(19) {
            override fun qualify(hour: Int): Int = if (hour in 1..11) hour + 12 else hour
        },
        NIGHT(21) {
            override fun qualify(hour: Int): Int = if (hour in 1..11) hour + 12 else hour
        };

        abstract fun qualify(hour: Int): Int
    }

    private companion object {
        const val HOURS_ON_A_DIAL = 12
        const val LAST_HOUR = 23
        const val LAST_MINUTE = 59
        const val LAST_ONE = 9
        const val MINUTE_DIGITS = 2
        const val MINUTES_IN_HALF_HOUR = 30

        const val O_CLOCK_LEAD = "o"
        const val O_CLOCK = "clock"

        val MERIDIEMS = mapOf("am" to Meridiem.AM, "pm" to Meridiem.PM)

        val TIME_PREPOSITIONS = setOf(CanonicalToken.AT, "on", "by", "around")

        /**
         * Bengali marks the hour with a suffix rather than a preposition: "১০টায়" is "at ten".
         * These only mean the hour when they follow a number, which is the only place this
         * parser looks for them.
         */
        val HOUR_MARKERS = setOf(
            CanonicalToken.AT, "tay", "tey", "ta", "te", "tar", "টায়", "টা", "টার", "তে",
            O_CLOCK, "sharp",
        )

        val HALF_WORDS = setOf(
            CanonicalToken.HALF, "adha", "adhek", "ordhek", "আধা", "আধ", "অর্ধেক",
        )

        val PAST_WORDS = setOf("past")

        /** Bengali says "half past ten" in one word, in front of the hour. */
        val HALF_PAST_WORDS = setOf("sare", "saare", "সাড়ে")

        val ARTICLES = setOf("a", "an", "the")

        val DURATION_LEAD_INS = setOf("in", "after", "within")

        val DURATION_TRAILERS = setOf(CanonicalToken.LATER, "pore", "por", "porey", "baade", "পরে", "পর")

        val DURATION_UNITS = mapOf(
            CanonicalToken.HOUR to ChronoUnit.HOURS,
            "hours" to ChronoUnit.HOURS,
            "hr" to ChronoUnit.HOURS,
            "hrs" to ChronoUnit.HOURS,
            "ghonta" to ChronoUnit.HOURS,
            "ghanta" to ChronoUnit.HOURS,
            "ghontay" to ChronoUnit.HOURS,
            "ghontar" to ChronoUnit.HOURS,
            "ঘন্টা" to ChronoUnit.HOURS,
            "ঘণ্টা" to ChronoUnit.HOURS,
            "ঘন্টায়" to ChronoUnit.HOURS,
            CanonicalToken.MINUTE to ChronoUnit.MINUTES,
            "minutes" to ChronoUnit.MINUTES,
            "min" to ChronoUnit.MINUTES,
            "mins" to ChronoUnit.MINUTES,
            "minit" to ChronoUnit.MINUTES,
            "minite" to ChronoUnit.MINUTES,
            "মিনিট" to ChronoUnit.MINUTES,
            "মিনিটে" to ChronoUnit.MINUTES,
            "second" to ChronoUnit.SECONDS,
            "seconds" to ChronoUnit.SECONDS,
            "sec" to ChronoUnit.SECONDS,
            "secs" to ChronoUnit.SECONDS,
            "সেকেন্ড" to ChronoUnit.SECONDS,
        )

        // "tonight" sits with the night rather than with the days: it names an evening, and the
        // day it falls on is simply today, which the caller already assumes.
        val DAY_PARTS: Map<String, DayPart> = mapOf(
            DayPart.MORNING to listOf(
                CanonicalToken.MORNING, "shokal", "sokal", "skal", "shokale", "sokale",
                "bhor", "vor", "সকাল", "সকালে", "ভোর",
            ),
            DayPart.NOON to listOf(
                CanonicalToken.NOON, "midday", "dupur", "dupure", "dupor", "দুপুর", "দুপুরে",
            ),
            DayPart.AFTERNOON to listOf(
                CanonicalToken.AFTERNOON, "bikal", "bikel", "bikale", "bikele",
                "বিকেল", "বিকাল", "বিকেলে",
            ),
            DayPart.EVENING to listOf(
                CanonicalToken.EVENING, "shondha", "sondha", "sondhya", "shondhya", "shondhay",
                "সন্ধ্যা", "সন্ধ্যায়",
            ),
            DayPart.NIGHT to listOf(
                CanonicalToken.NIGHT, CanonicalToken.TONIGHT, "raat", "rat", "raate", "rate",
                "ajrate", "aajrate", "রাত", "রাতে", "আজরাতে",
            ),
        ).flatMap { (part, forms) -> forms.map { form -> form to part } }.toMap()

        val ENGLISH_ONES: Map<String, Int> = listOf(
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen",
        ).withIndex().associate { (number, word) -> word to number }

        val ENGLISH_TENS = mapOf(
            "twenty" to 20,
            "thirty" to 30,
            "forty" to 40,
            "fourty" to 40,
            "fifty" to 50,
            "sixty" to 60,
        )
    }
}
