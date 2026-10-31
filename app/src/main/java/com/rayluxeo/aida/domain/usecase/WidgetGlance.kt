package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.domain.brief.BriefEvent
import com.rayluxeo.aida.domain.brief.BriefReminder
import com.rayluxeo.aida.domain.model.Money

/**
 * The slice of the day a home-screen glance widget shows — the same facts Home draws, trimmed to
 * what fits a cell and stripped of language so [com.rayluxeo.aida.ui.response.WidgetGlanceText] can
 * word it in Abu's language off the device's own render.
 *
 * It is a projection of [com.rayluxeo.aida.domain.brief.DailyBriefReport] with the hide-zero gates
 * already applied, so the widget never has to decide what counts as "nothing": a null [outstanding]
 * means no unpaid invoices (not zero taka), a null [nextEvent] means an empty or unreadable diary,
 * and [isQuiet] is true only when there is genuinely nothing to show. Every amount is [Money], never
 * a Double (§8).
 */
data class WidgetGlance(
    val openTaskCount: Int,
    val dueTodayCount: Int,
    val overdueCount: Int,
    val nextEvent: BriefEvent?,
    val nextReminder: BriefReminder?,
    /** Null unless there is at least one unpaid invoice; never a zero amount standing in for none. */
    val outstanding: Money?,
    val outstandingInvoiceCount: Int,
    /** Null unless something is actually overdue; the overdue slice of [outstanding]. */
    val overdue: Money?,
    val overdueInvoiceCount: Int,
    val calendarReadable: Boolean,
) {
    /** Nothing due, nothing scheduled, nothing owed — the widget says so plainly rather than blankly. */
    val isQuiet: Boolean =
        openTaskCount == 0 &&
            dueTodayCount == 0 &&
            overdueCount == 0 &&
            nextEvent == null &&
            nextReminder == null &&
            outstanding == null
}
