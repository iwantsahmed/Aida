package com.rayluxeo.aida.domain.report

import com.rayluxeo.aida.core.common.MiraClock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The window a report covers, as Abu chooses it. Resolved against the clock into an exact
 * [ReportRange] once, [here][resolve], rather than in each report so "this month" means the same
 * thing to income as it does to expenses.
 */
sealed interface ReportPeriod {

    data object Today : ReportPeriod
    data object ThisWeek : ReportPeriod
    data object ThisMonth : ReportPeriod
    data object LastMonth : ReportPeriod

    /** An arbitrary span Abu names himself, inclusive of both ends. */
    data class Custom(val from: LocalDate, val to: LocalDate) : ReportPeriod
}

/**
 * A resolved window: a half-open instant range for querying (`start <= t < endExclusive`, the shape
 * every "created between" query wants) and the two calendar dates for showing Abu what he is
 * looking at.
 */
data class ReportRange(
    val start: Instant,
    val endExclusive: Instant,
    val fromDate: LocalDate,
    val toDate: LocalDate,
)

/**
 * Turn a period into the exact instants a query runs against, in Abu's own zone.
 *
 * [weekStart] is passed rather than assumed so "this week" can honour Abu's First-Day-of-Week
 * setting; it defaults to Monday, the ISO week, until that setting is wired through. The end is the
 * start of the day after the last day, so a payment at 23:59 on the last day is inside the window.
 */
fun ReportPeriod.resolve(clock: MiraClock, weekStart: DayOfWeek = DayOfWeek.MONDAY): ReportRange {
    val today = clock.today()
    val (from, to) = when (this) {
        ReportPeriod.Today -> today to today
        ReportPeriod.ThisWeek -> {
            val start = today.with(TemporalAdjusters.previousOrSame(weekStart))
            start to start.plusDays(6)
        }
        ReportPeriod.ThisMonth ->
            today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
        ReportPeriod.LastMonth -> {
            val lastMonth = today.minusMonths(1)
            lastMonth.withDayOfMonth(1) to lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())
        }
        is ReportPeriod.Custom -> from to to
    }
    val zone = clock.zone()
    return ReportRange(
        start = from.atStartOfDay(zone).toInstant(),
        endExclusive = to.plusDays(1).atStartOfDay(zone).toInstant(),
        fromDate = from,
        toDate = to,
    )
}
