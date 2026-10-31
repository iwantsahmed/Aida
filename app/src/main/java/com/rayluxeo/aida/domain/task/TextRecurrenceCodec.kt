package com.rayluxeo.aida.domain.task

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Stores a rule as a short `key=value` list, so a person reading Abu's row in a database dump can
 * tell what it means without this file in front of them:
 *
 * ```
 * freq=DAILY;interval=1
 * freq=WEEKLY;interval=2;days=MONDAY,THURSDAY;until=2026-12-31
 * freq=MONTHLY;interval=1;day=31
 * ```
 *
 * `freq` and `interval` are always written. `days` (weekly), `day` (monthly) and `until` appear
 * only where the rule carries them, and the days are written in week order so that two equal rules
 * always produce the same text.
 *
 * Reading is deliberately asymmetric. A key this build does not know is ignored, so a row written
 * by a later version still comes back as the rule the two versions have in common. A value this
 * build cannot read is not: a rule whose `until` or whose day list is unreadable would repeat on
 * the wrong days or for ever, so [decode] gives back no rule at all and the task stays a task that
 * happens once.
 */
class TextRecurrenceCodec @Inject constructor() : RecurrenceCodec {

    override fun encode(rule: RecurrenceRule): String {
        val variant: Pair<String, List<String>> = when (rule) {
            is RecurrenceRule.Daily -> FREQ_DAILY to emptyList()
            is RecurrenceRule.Weekly -> FREQ_WEEKLY to rule.days.encodedDays()
            is RecurrenceRule.Weekdays -> FREQ_WEEKDAYS to emptyList()
            is RecurrenceRule.Monthly -> FREQ_MONTHLY to rule.dayOfMonth.encodedDayOfMonth()
            is RecurrenceRule.Yearly -> FREQ_YEARLY to emptyList()
            is RecurrenceRule.EveryNDays -> FREQ_EVERY_N_DAYS to emptyList()
        }
        val (freq, extras) = variant

        val fields = buildList {
            add("$KEY_FREQ=$freq")
            add("$KEY_INTERVAL=${rule.interval.coerceAtLeast(1)}")
            addAll(extras)
            rule.until?.let { add("$KEY_UNTIL=$it") }
        }
        return fields.joinToString(FIELD_SEPARATOR)
    }

    override fun decode(stored: String?): RecurrenceRule? {
        val fields = fieldsOf(stored) ?: return null

        val interval = fields[KEY_INTERVAL]?.let { it.toIntOrNull() ?: return null } ?: DEFAULT_INTERVAL
        if (interval < 1) return null
        val until = fields[KEY_UNTIL]?.let { dateOrNull(it) ?: return null }

        return when (fields[KEY_FREQ]?.uppercase()) {
            FREQ_DAILY -> RecurrenceRule.Daily(interval = interval, until = until)

            FREQ_WEEKLY -> RecurrenceRule.Weekly(
                days = daysOrNull(fields[KEY_DAYS]) ?: return null,
                interval = interval,
                until = until,
            )

            FREQ_WEEKDAYS -> RecurrenceRule.Weekdays(until = until)

            FREQ_MONTHLY -> RecurrenceRule.Monthly(
                dayOfMonth = fields[KEY_DAY]?.let { dayOfMonthOrNull(it) ?: return null },
                interval = interval,
                until = until,
            )

            FREQ_YEARLY -> RecurrenceRule.Yearly(interval = interval, until = until)

            FREQ_EVERY_N_DAYS -> RecurrenceRule.EveryNDays(interval = interval, until = until)

            else -> null
        }
    }

    /** @return null when a field is not a `key=value` pair, which means the text is not a rule. */
    private fun fieldsOf(stored: String?): Map<String, String>? {
        val text = stored?.trim().orEmpty()
        if (text.isEmpty()) return null

        val fields = mutableMapOf<String, String>()
        for (field in text.split(FIELD_SEPARATOR)) {
            if (field.isBlank()) continue
            val split = field.indexOf(KEY_VALUE_SEPARATOR)
            if (split <= 0) return null
            fields[field.take(split).trim().lowercase()] = field.substring(split + 1).trim()
        }
        return fields.takeIf { it.isNotEmpty() }
    }

    /**
     * @return an empty set when the rule names no day, which is a weekly rule on its own weekday,
     *   and null when a name cannot be read — dropping one silently would repeat the task on fewer
     *   days than Abu asked for.
     */
    private fun daysOrNull(encoded: String?): Set<DayOfWeek>? {
        if (encoded.isNullOrBlank()) return emptySet()

        val days = LinkedHashSet<DayOfWeek>()
        for (name in encoded.split(DAY_SEPARATOR)) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) continue
            days += dayOrNull(trimmed) ?: return null
        }
        return days
    }

    private fun dayOrNull(name: String): DayOfWeek? = try {
        DayOfWeek.valueOf(name.uppercase())
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun dayOfMonthOrNull(encoded: String): Int? =
        encoded.toIntOrNull()?.takeIf { it in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH }

    private fun dateOrNull(encoded: String): LocalDate? = try {
        LocalDate.parse(encoded)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun Set<DayOfWeek>.encodedDays(): List<String> =
        if (isEmpty()) emptyList()
        else listOf("$KEY_DAYS=${sortedBy { it.value }.joinToString(DAY_SEPARATOR) { it.name }}")

    private fun Int?.encodedDayOfMonth(): List<String> =
        if (this == null) emptyList() else listOf("$KEY_DAY=$this")

    private companion object {
        const val FIELD_SEPARATOR = ";"
        const val DAY_SEPARATOR = ","
        const val KEY_VALUE_SEPARATOR = '='

        const val KEY_FREQ = "freq"
        const val KEY_INTERVAL = "interval"
        const val KEY_DAYS = "days"
        const val KEY_DAY = "day"
        const val KEY_UNTIL = "until"

        const val FREQ_DAILY = "DAILY"
        const val FREQ_WEEKLY = "WEEKLY"
        const val FREQ_WEEKDAYS = "WEEKDAYS"
        const val FREQ_MONTHLY = "MONTHLY"
        const val FREQ_YEARLY = "YEARLY"
        const val FREQ_EVERY_N_DAYS = "EVERY_N_DAYS"

        const val DEFAULT_INTERVAL = 1
        const val MIN_DAY_OF_MONTH = 1
        const val MAX_DAY_OF_MONTH = 31
    }
}
