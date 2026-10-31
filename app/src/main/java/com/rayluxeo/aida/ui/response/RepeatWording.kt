package com.rayluxeo.aida.ui.response

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * How one language says a set of repeat days.
 *
 * Only the words live here. The judgement — all seven days is "every day", Monday to Friday is one
 * idea rather than five names, and anything else is read in week order — belongs to [repeatPhrase]
 * and is shared, because the same set of days is read back in two places: Mira's reply when she
 * sets an alarm, and the Alarms screen's own row. One alarm described as "every weekday" in the
 * reply and "Mon · Tue · Wed · Thu · Fri" in the list is one alarm Abu has to check twice.
 *
 * A caller outside this package supplies its own words — a row has room for "Mon", a sentence
 * wants "Monday" — and still gets the same decision.
 */
internal interface RepeatWording {

    /** All seven days, said as one idea rather than listed. */
    val everyDay: String

    /** Monday to Friday, said as one idea, because that is how Abu asks for it. */
    val everyWeekday: String

    /** One day, named as it belongs in the phrase this wording is for. */
    fun day(day: DayOfWeek): String

    /** How this language strings the named days together. */
    fun joinDays(names: List<String>): String
}

/**
 * Monday to Friday. One definition, so "every weekday" cannot quietly come to mean two different
 * sets of days in two parts of the app.
 */
internal val WEEKDAY_SET: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

/**
 * The one place a set of repeat days becomes words.
 *
 * Null for an empty set: a one-time alarm has no repeat to read back, and what is said instead is
 * the caller's business — Mira names the single moment, an Alarms row says "Once".
 */
internal fun repeatPhrase(days: Set<DayOfWeek>, words: RepeatWording): String? = when {
    days.isEmpty() -> null
    days.size == DayOfWeek.entries.size -> words.everyDay
    days == WEEKDAY_SET -> words.everyWeekday
    else -> words.joinDays(DayOfWeek.entries.filter { it in days }.map { words.day(it) })
}

/** Lower case throughout, because every one of these phrases lands inside a sentence. */
internal object EnglishRepeatWording : RepeatWording {

    override val everyDay: String = "every day"

    override val everyWeekday: String = "every weekday"

    override fun day(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.US)

    override fun joinDays(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names.first()
        else -> "${names.dropLast(1).joinToString(", ")} and ${names.last()}"
    }
}

/**
 * The days are written out rather than taken from `DayOfWeek.getDisplayName`, whose Bengali output
 * depends on whatever locale data the JVM happens to carry. Mira's voice does not vary with that.
 *
 * "কর্মদিবস" is the working week as the Alarms screen already names it, kept identical on purpose.
 */
internal object BengaliRepeatWording : RepeatWording {

    override val everyDay: String = "প্রতিদিন"

    override val everyWeekday: String = "প্রতি কর্মদিবসে"

    override fun day(day: DayOfWeek): String = bengaliWeekdayName(day)

    override fun joinDays(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names.first()
        else -> "${names.dropLast(1).joinToString(", ")} ও ${names.last()}"
    }
}
