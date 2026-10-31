package com.rayluxeo.aida.domain.task

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Works out when a repeating task next falls due, from the previous occurrence alone.
 *
 * Pure [java.time] arithmetic: no clock, no repository, no state between calls. The series carries
 * its phase in the date handed to [next], which is what makes a fortnightly task testable without
 * replaying every occurrence since it was created.
 *
 * Two decisions are worth knowing before reading the branches:
 *
 * - **A week runs Monday to Sunday.** [RecurrenceRule.Weekdays] is defined as Monday to Friday, so
 *   the weekly wrap uses the same boundary rather than a second, quietly different one.
 * - **Clamping a short month is not sticky.** A monthly rule that names the 31st lands on the last
 *   day of February and then goes back to the 31st in March. Anchoring on the previous occurrence
 *   instead would turn one February into a permanent 28th-of-the-month task.
 */
class DefaultRecurrenceCalculator @Inject constructor() : RecurrenceCalculator {

    override fun next(rule: RecurrenceRule, after: LocalDate): LocalDate? {
        val candidate = when (rule) {
            // The same arithmetic; the two variants differ only in how Mira says them aloud.
            is RecurrenceRule.Daily, is RecurrenceRule.EveryNDays -> after.plusDays(rule.steps)
            is RecurrenceRule.Weekly -> nextWeekly(rule, after)
            is RecurrenceRule.Weekdays -> nextWeekday(after)
            is RecurrenceRule.Monthly -> nextMonthly(rule, after)
            is RecurrenceRule.Yearly -> nextYearly(rule, after)
        }
        val until = rule.until
        return candidate.takeIf { until == null || !it.isAfter(until) }
    }

    override fun preview(rule: RecurrenceRule, start: LocalDate, count: Int): List<LocalDate> {
        if (count <= 0) return emptyList()
        val until = rule.until
        if (until != null && start.isAfter(until)) return emptyList()

        val dates = ArrayList<LocalDate>(count)
        var current: LocalDate? = start
        while (current != null && dates.size < count) {
            dates += current
            current = next(rule, current)
        }
        return dates
    }

    /**
     * Named days run within the week [after] falls in before the interval is spent, so "every
     * Monday and Thursday" asked on a Tuesday answers Thursday rather than jumping a whole period.
     * When the week is used up, the count starts from that same week: a fortnightly Monday rule
     * asked on a Monday answers a fortnight later, not the Monday in between.
     */
    private fun nextWeekly(rule: RecurrenceRule.Weekly, after: LocalDate): LocalDate {
        val days = rule.days.sortedBy { it.value }
        if (days.isEmpty()) return after.plusWeeks(rule.steps)

        val laterThisWeek = days.firstOrNull { it.value > after.dayOfWeek.value }
        if (laterThisWeek != null) return after.with(TemporalAdjusters.next(laterThisWeek))

        val weekStart = after.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return weekStart.plusWeeks(rule.steps).with(TemporalAdjusters.nextOrSame(days.first()))
    }

    private fun nextWeekday(after: LocalDate): LocalDate {
        var candidate = after.plusDays(1)
        while (candidate.dayOfWeek == DayOfWeek.SATURDAY || candidate.dayOfWeek == DayOfWeek.SUNDAY) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    /**
     * The day comes from the rule wherever it names one, which is what keeps clamping from being
     * sticky. A rule that names no day can only take it from the date it is counting from, so a
     * series that must land on the 31st has to say so — [RecurrenceRule.Monthly.dayOfMonth].
     */
    private fun nextMonthly(rule: RecurrenceRule.Monthly, after: LocalDate): LocalDate {
        val anchorDay = rule.dayOfMonth ?: after.dayOfMonth
        val month = after.withDayOfMonth(1).plusMonths(rule.steps)
        return month.withDayOfMonth(anchorDay.coerceIn(1, month.lengthOfMonth()))
    }

    /**
     * February is the only month whose length changes from year to year, so a yearly occurrence on
     * its last day is kept on its last day. Without that, 29 February clamps to the 28th once and
     * never comes back, and a leap-day task is silently a different task for ever. The cost is that
     * a rule genuinely meant for 28 February moves to the 29th in a leap year — one day out, still
     * in the right month, and recoverable, which the alternative is not.
     */
    private fun nextYearly(rule: RecurrenceRule.Yearly, after: LocalDate): LocalDate {
        val candidate = after.plusYears(rule.steps)
        val lastDayOfFebruary = after.month == Month.FEBRUARY && after.dayOfMonth == after.lengthOfMonth()
        return if (lastDayOfFebruary) candidate.with(TemporalAdjusters.lastDayOfMonth()) else candidate
    }
}

/**
 * The interval as a step count, floored at one.
 *
 * [RecurrenceRule.interval] is documented as always at least 1 but nothing in the type enforces it,
 * and a zero would make [RecurrenceCalculator.preview] hand back the same date until the list was
 * full.
 */
private val RecurrenceRule.steps: Long
    get() = interval.coerceAtLeast(1).toLong()
