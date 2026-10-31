package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.core.common.MiraClock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the day Abu named, in any of the four ways he writes one, and hands back the sentence
 * with that day taken out.
 *
 * It answers a calendar day and nothing else — no hour, no zone, no instant. The time of day is
 * [TimeExpressionParser]'s work, and only [MiraDateTimeParser] puts the two together, because
 * which "10" he meant depends on the day the 10 lands on.
 *
 * Every table below lists the canonical token the normalizer produces *and* the raw spellings
 * Abu types. The pipeline feeds this parser normalized text, but a caller holding his own words
 * gets the same answer, and a spelling the normalizer has not learned yet still lands.
 */
@Singleton
class RelativeDateParser @Inject constructor(
    private val clock: MiraClock,
) {

    /**
     * The date, plus the one thing the date alone cannot settle: whether a moment that has
     * already gone should move to next week. Abu saying "friday" on a Friday evening means the
     * next one, but "agami shukrobar" is already a week out and must not move again.
     */
    data class Reading(
        val date: ParsedDate,
        val rollsByWeek: Boolean,
    )

    fun parse(text: String): ParsedDate? = read(text)?.date

    fun read(text: String): Reading? {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        // The day-of-month form is tried first because it swallows the month word that the
        // "next month" rule would otherwise claim on its own: "agami masher 5 tarik" is the
        // fifth of next month, not the whole of next month.
        val match = matchNamedMonthDay(tokens)
            ?: matchDayOfMonth(tokens)
            ?: matchNamedDay(tokens)
            ?: matchWeekday(tokens)
            ?: matchWeekOrMonth(tokens)
            ?: return null

        return Reading(
            date = ParsedDate(
                date = match.date,
                remainder = join(tokens) { it !in match.claimed },
                matched = join(tokens) { it in match.claimed },
                confidence = match.confidence,
            ),
            rollsByWeek = match.rollsByWeek,
        )
    }

    private fun join(tokens: List<String>, keep: (Int) -> Boolean): String =
        tokens.filterIndexed { index, _ -> keep(index) }.joinToString(separator = " ")

    private fun matchNamedDay(tokens: List<String>): Match? {
        for (index in tokens.indices) {
            val offset = NAMED_DAYS[tokens[index]] ?: continue
            val claimed = mutableSetOf(index)
            // "day after tomorrow" is one expression. Read word by word it lands a day early,
            // and the phrase survives unfolded whenever it reaches here as Abu typed it.
            if (offset == TOMORROW_OFFSET && index >= 2 &&
                tokens[index - 2] == DAY_AFTER_LEAD && tokens[index - 1] == DAY_AFTER_JOIN
            ) {
                claimed += index - 1
                claimed += index - 2
                return Match(
                    date = clock.today().plusDays(DAY_AFTER_OFFSET),
                    rollsByWeek = false,
                    confidence = NAMED_DAY_CONFIDENCE,
                    claimed = claimed,
                )
            }
            // "আগামী কাল" written with a space is still just tomorrow; the qualifier is noise
            // in front of a day that already names itself.
            if (tokens.getOrNull(index - 1) in DAY_QUALIFIERS) claimed += index - 1
            return Match(
                date = clock.today().plusDays(offset),
                rollsByWeek = false,
                confidence = NAMED_DAY_CONFIDENCE,
                claimed = claimed,
            )
        }
        return null
    }

    /**
     * A bare weekday, "ei shukrobar" and "coming friday" all mean the next one to come round,
     * today included. "agami shukrobar" means the one after that: on a Wednesday, this Friday
     * is two days away and next Friday is nine.
     */
    private fun matchWeekday(tokens: List<String>): Match? {
        for (index in tokens.indices) {
            val weekday = WEEKDAYS[tokens[index]] ?: continue
            val claimed = mutableSetOf(index)
            val qualifier = tokens.getOrNull(index - 1)
            if (qualifier in DAY_QUALIFIERS) claimed += index - 1
            val pushed = qualifier in NEXT_WORDS
            val coming = comingWeekday(weekday)
            return Match(
                date = if (pushed) coming.plusWeeks(1) else coming,
                rollsByWeek = !pushed,
                confidence = WEEKDAY_CONFIDENCE,
                claimed = claimed,
            )
        }
        return null
    }

    private fun matchWeekOrMonth(tokens: List<String>): Match? {
        for (index in 1 until tokens.size) {
            if (tokens[index - 1] !in NEXT_WORDS) continue
            val date = when (tokens[index]) {
                in WEEK_WORDS -> clock.today().plusWeeks(1)
                in MONTH_WORDS -> clock.today().plusMonths(1)
                else -> null
            } ?: continue
            return Match(
                date = date,
                rollsByWeek = false,
                confidence = SPAN_CONFIDENCE,
                claimed = setOf(index - 1, index),
            )
        }
        return null
    }

    private fun matchNamedMonthDay(tokens: List<String>): Match? {
        for (index in tokens.indices) {
            val tokenClean = tokens[index].trim { !it.isLetterOrDigit() }
            val month = MONTH_NAMES[tokens[index]] ?: MONTH_NAMES[tokenClean] ?: continue
            val claimed = mutableSetOf(index)

            // Look for day before: "[day] of [month]" or "[day] [month]"
            var dayBeforeIndex: Int? = null
            var cursor = index - 1
            while (cursor >= 0 && (tokens[cursor] in FILLERS || tokens[cursor] in DATE_MARKERS)) cursor--
            if (cursor >= 0) {
                val day = parseDayNumber(tokens[cursor])
                if (day != null) dayBeforeIndex = cursor
            }

            // Look for day after: "[month] [day]"
            var dayAfterIndex: Int? = null
            cursor = index + 1
            while (cursor < tokens.size && (tokens[cursor] in FILLERS || tokens[cursor] in DATE_MARKERS)) cursor++
            if (cursor < tokens.size) {
                val day = parseDayNumber(tokens[cursor])
                if (day != null) dayAfterIndex = cursor
            }

            val dayIndex = dayBeforeIndex ?: dayAfterIndex ?: continue
            val dayOfMonth = parseDayNumber(tokens[dayIndex]) ?: continue
            claimed += dayIndex

            val start = minOf(dayIndex, index)
            val end = maxOf(dayIndex, index)
            for (i in start..end) claimed += i

            val markerAfter = dayIndex + 1
            if (markerAfter < tokens.size && tokens[markerAfter] in DATE_MARKERS) claimed += markerAfter

            val lead = start - 1
            if (lead >= 0) {
                val leadToken = tokens[lead].trim { !it.isLetterOrDigit() }
                if (tokens[lead] in DAY_QUALIFIERS || leadToken in DAY_QUALIFIERS) claimed += lead
            }

            val today = clock.today()
            val year = today.year
            val maxDaysInMonth = month.length(java.time.Year.isLeap(year.toLong()))
            val validDay = dayOfMonth.coerceIn(1, maxDaysInMonth)
            val date = LocalDate.of(year, month, validDay)

            return Match(
                date = date,
                rollsByWeek = false,
                confidence = DAY_OF_MONTH_CONFIDENCE,
                claimed = claimed,
            )
        }
        return null
    }

    private fun parseDayNumber(token: String): Int? {
        val clean = token.trim { !it.isLetterOrDigit() }
            .removeSuffix("th").removeSuffix("st").removeSuffix("nd").removeSuffix("rd")
            .trim { !it.isDigit() }
        return clean.toIntOrNull()?.takeIf { it in 1..MAX_DAY_OF_MONTH }
    }

    /**
     * "মাসের ৫ তারিখ" and "the 5th of the month". A day that has already been this month means
     * next month's — Abu is asking for a date, and a date in the past is never the one he wants.
     */
    private fun matchDayOfMonth(tokens: List<String>): Match? {
        for (index in tokens.indices) {
            val dayOfMonth = tokens[index].toIntOrNull()?.takeIf { it in 1..MAX_DAY_OF_MONTH } ?: continue
            val marker = (index + 1).takeIf { tokens.getOrNull(it) in DATE_MARKERS }
            val month = monthWordBefore(tokens, index) ?: monthWordAfter(tokens, index)
            if (marker == null && month == null) continue

            val claimed = mutableSetOf(index)
            marker?.let { claimed += it }
            month?.let { claimed += it }
            val pinned = nextMonthQualifier(tokens, month)
            pinned?.let { claimed += it }
            absorbFillers(tokens, claimed)

            val base = if (pinned != null) clock.today().plusMonths(1) else clock.today()
            return Match(
                date = settleDayOfMonth(dayOfMonth, base, pinned = pinned != null),
                rollsByWeek = false,
                confidence = DAY_OF_MONTH_CONFIDENCE,
                claimed = claimed,
            )
        }
        return null
    }

    /** "agami masher 20 tarik" — the month word can carry a "next" of its own. */
    private fun nextMonthQualifier(tokens: List<String>, month: Int?): Int? {
        if (month == null) return null
        return (month - 1).takeIf { it >= 0 && tokens[it] in NEXT_WORDS }
    }

    /**
     * Bengali puts the month in front — "মাসের ৫ তারিখ" — and normalization folds the genitive
     * "masher" onto the same token as a plain "mash", so the position is all that is left to
     * tell them apart. The number is capped at 31 either way, which is what keeps a count of
     * months out of here.
     */
    private fun monthWordBefore(tokens: List<String>, index: Int): Int? {
        var cursor = index - 1
        while (cursor >= 0 && tokens[cursor] in FILLERS) cursor--
        return cursor.takeIf { it >= 0 && tokens[it] in MONTH_WORDS }
    }

    private fun monthWordAfter(tokens: List<String>, index: Int): Int? {
        var cursor = index + 1
        while (cursor < tokens.size && (tokens[cursor] in FILLERS || tokens[cursor] in DATE_MARKERS)) cursor++
        return cursor.takeIf { it < tokens.size && tokens[it] in MONTH_WORDS }
    }

    /** Takes the "of the" out of "the 5th of the month" so the remainder is a usable title. */
    private fun absorbFillers(tokens: List<String>, claimed: MutableSet<Int>) {
        for (index in claimed.min()..claimed.max()) {
            if (index !in claimed && tokens[index] in FILLERS) claimed += index
        }
        val leading = claimed.min() - 1
        if (leading >= 0 && tokens[leading] in FILLERS) claimed += leading
    }

    private fun settleDayOfMonth(dayOfMonth: Int, base: LocalDate, pinned: Boolean): LocalDate {
        var month = YearMonth.from(base)
        if (!pinned && dayOfMonth < base.dayOfMonth) month = month.plusMonths(1)
        while (dayOfMonth > month.lengthOfMonth()) month = month.plusMonths(1)
        return month.atDay(dayOfMonth)
    }

    private fun comingWeekday(target: DayOfWeek): LocalDate {
        val today = clock.today()
        val ahead = (target.value - today.dayOfWeek.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
        return today.plusDays(ahead.toLong())
    }

    private data class Match(
        val date: LocalDate,
        val rollsByWeek: Boolean,
        val confidence: Float,
        val claimed: Set<Int>,
    )

    private companion object {
        const val DAYS_IN_WEEK = 7
        const val MAX_DAY_OF_MONTH = 31

        const val TOMORROW_OFFSET = 1L
        const val DAY_AFTER_OFFSET = 2L
        const val DAY_AFTER_LEAD = "day"
        const val DAY_AFTER_JOIN = "after"

        const val NAMED_DAY_CONFIDENCE = 0.95f
        const val WEEKDAY_CONFIDENCE = 0.9f
        const val DAY_OF_MONTH_CONFIDENCE = 0.9f
        const val SPAN_CONFIDENCE = 0.75f

        val NEXT_WORDS = setOf(
            CanonicalToken.NEXT, "agami", "agamee", "aagami", "samner", "আগামী", "আগামি", "সামনের",
        )

        /** "this" and "coming" point at the same day a bare weekday does: the next one round. */
        val THIS_WORDS = setOf(CanonicalToken.THIS, "coming", "ei", "এই")

        val DAY_QUALIFIERS = NEXT_WORDS + THIS_WORDS + setOf("on", "by", "due")

        val MONTH_NAMES: Map<String, Month> = mapOf(
            Month.JANUARY to listOf("january", "jan", "জানুয়ারি", "জানুয়ারি"),
            Month.FEBRUARY to listOf("february", "feb", "ফেব্রুয়ারি", "ফেব্রুয়ারি"),
            Month.MARCH to listOf("march", "mar", "মার্চ"),
            Month.APRIL to listOf("april", "apr", "এপ্রিল"),
            Month.MAY to listOf("may", "মে"),
            Month.JUNE to listOf("june", "jun", "জুন"),
            Month.JULY to listOf("july", "jul", "জুলাই"),
            Month.AUGUST to listOf("august", "aug", "আগস্ট", "আগষ্ট"),
            Month.SEPTEMBER to listOf("september", "sep", "sept", "সেপ্টেম্বর"),
            Month.OCTOBER to listOf("october", "oct", "অক্টোবর"),
            Month.NOVEMBER to listOf("november", "nov", "নভেম্বর"),
            Month.DECEMBER to listOf("december", "dec", "ডিসেম্বর"),
        ).flatMap { (month, forms) ->
            forms.flatMap { form -> listOf(form to month, "${form}er" to month, "${form}এর" to month) }
        }.toMap()

        val WEEK_WORDS = setOf(
            CanonicalToken.WEEK, "soptah", "sopta", "shoptah", "saptah",
            "সপ্তাহ", "সপ্তাহে", "সপ্তাহের",
        )

        val MONTH_WORDS = setOf(
            CanonicalToken.MONTH, "mash", "mas", "mashe", "masher", "maser",
            "মাস", "মাসে", "মাসের",
        )

        /** What turns a bare number into the day of a month: "৫ তারিখ", "5 tarik", "5th". */
        val DATE_MARKERS = setOf(
            CanonicalToken.DATE, "tarik", "tarikh", "tarike", "tarikhe", "তারিখ", "তারিখে",
            "st", "nd", "rd", "th",
        )

        /** Grammar that sits inside a date phrase and belongs to it rather than to the title. */
        val FILLERS = setOf("of", "the", "a", "an", "er", "এর")

        /**
         * Bengali "kal" is both yesterday and tomorrow. Mira records nothing about the past, and
         * every command that carries a day is scheduling something, so the forward reading is the
         * only one that can be acted on.
         */
        val NAMED_DAYS: Map<String, Long> = mapOf(
            0L to listOf(
                CanonicalToken.TODAY, "aj", "aaj", "ajke", "aajke", "ajker", "aajker",
                "আজ", "আজকে", "আজকের",
            ),
            TOMORROW_OFFSET to listOf(
                CanonicalToken.TOMORROW, "kal", "kaal", "kall", "kalke", "kaalke", "kalker",
                "agamikal", "agamikaal", "agamical", "আগামীকাল", "কাল", "কালকে",
            ),
            DAY_AFTER_OFFSET to listOf(
                CanonicalToken.DAY_AFTER, "porshu", "porsu", "parshu", "পরশু", "পরশুদিন",
            ),
        ).flatMap { (offset, forms) -> forms.map { form -> form to offset } }.toMap()

        val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
            DayOfWeek.SATURDAY to listOf(CanonicalToken.SATURDAY, "shonibar", "sonibar", "শনিবার"),
            DayOfWeek.SUNDAY to listOf(CanonicalToken.SUNDAY, "robibar", "rabibar", "রবিবার"),
            DayOfWeek.MONDAY to listOf(CanonicalToken.MONDAY, "shombar", "sombar", "somobar", "সোমবার"),
            DayOfWeek.TUESDAY to listOf(CanonicalToken.TUESDAY, "mongolbar", "mangalbar", "মঙ্গলবার"),
            DayOfWeek.WEDNESDAY to listOf(CanonicalToken.WEDNESDAY, "budhbar", "budbar", "বুধবার"),
            DayOfWeek.THURSDAY to listOf(
                CanonicalToken.THURSDAY, "brihoshpotibar", "brihospotibar", "bishudbar", "বৃহস্পতিবার",
            ),
            DayOfWeek.FRIDAY to listOf(
                CanonicalToken.FRIDAY, "shukrobar", "sukrobar", "jumabar", "শুক্রবার",
            ),
        ).flatMap { (day, forms) -> forms.map { form -> form to day } }.toMap()
    }
}
