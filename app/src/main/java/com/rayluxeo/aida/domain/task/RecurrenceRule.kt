package com.rayluxeo.aida.domain.task

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * How often a task comes back.
 *
 * Deliberately not RFC-5545. Abu says "every Monday" and "every weekday"; a general iCalendar
 * engine would be a large surface to get wrong for shapes he will never type. Each variant here
 * maps to something he can say and Mira can read back to him in either language.
 */
sealed interface RecurrenceRule {

    /** How many periods between occurrences. Always at least 1. */
    val interval: Int

    /** The day the series stops, inclusive. Null runs indefinitely. */
    val until: LocalDate?

    data class Daily(override val interval: Int = 1, override val until: LocalDate? = null) : RecurrenceRule

    /**
     * [days] empty means "the same weekday as the first occurrence", which is what Abu means by
     * "every week" without naming a day.
     */
    data class Weekly(
        val days: Set<DayOfWeek> = emptySet(),
        override val interval: Int = 1,
        override val until: LocalDate? = null,
    ) : RecurrenceRule

    /**
     * Monday to Friday. Its own variant rather than a [Weekly] with five days, because Abu says
     * "every weekday" as one idea and Mira must be able to say it back the same way.
     */
    data class Weekdays(override val until: LocalDate? = null) : RecurrenceRule {
        override val interval: Int = 1
    }

    /**
     * The same day each month. A task on the 31st lands on the last day of a shorter month rather
     * than being skipped — skipping is the behaviour nobody wants and everybody ships by accident.
     */
    data class Monthly(
        val dayOfMonth: Int? = null,
        override val interval: Int = 1,
        override val until: LocalDate? = null,
    ) : RecurrenceRule

    data class Yearly(override val interval: Int = 1, override val until: LocalDate? = null) : RecurrenceRule

    /** Every [interval] days, from the last occurrence. The escape hatch for "every 10 days". */
    data class EveryNDays(override val interval: Int, override val until: LocalDate? = null) : RecurrenceRule
}

/**
 * Serialises a rule to the single text column on the task, and back.
 *
 * A port rather than a method on the rule because the format is a storage concern: `domain` owns
 * what a repeat means, `data` owns how it is written down, and a parse that fails must come back
 * as an absence rather than throwing on a row Abu can no longer open.
 */
interface RecurrenceCodec {
    fun encode(rule: RecurrenceRule): String

    /** @return null when the stored text cannot be read, so one bad row cannot break a list. */
    fun decode(stored: String?): RecurrenceRule?
}

/**
 * Works out when a repeating task next falls due.
 *
 * Pure arithmetic over [java.time], so it is fully testable and must never call `Instant.now()`.
 * The caller supplies the moment to count from, which is what makes "every Monday" testable
 * across a DST change and a leap day.
 */
interface RecurrenceCalculator {

    /**
     * @param after the occurrence just completed or the series start.
     * @return the next date the rule lands on, or null when [RecurrenceRule.until] has passed.
     */
    fun next(rule: RecurrenceRule, after: LocalDate): LocalDate?

    /** The first [count] dates from [start] inclusive, for showing Abu what he just set up. */
    fun preview(rule: RecurrenceRule, start: LocalDate, count: Int): List<LocalDate>
}
