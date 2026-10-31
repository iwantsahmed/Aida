package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.brief.DailyBriefEngine
import com.rayluxeo.aida.domain.brief.DailyBriefReport
import javax.inject.Inject

/**
 * The widget's single read path, wrapping the same [DailyBriefEngine] Home uses so the glance and
 * the app can never disagree about what is due or what is owed.
 *
 * The projection — and the hide-zero gates — live here rather than in the widget's renderer so they
 * are plain-Kotlin testable without a launcher: "overdue money only when something is actually
 * overdue", "outstanding only when an invoice exists". A failure from the engine is returned as a
 * failure, not flattened to an empty glance, so the worker can leave the last good widget in place
 * rather than blanking it on a bad read.
 */
class GetWidgetGlanceUseCase @Inject constructor(
    private val briefEngine: DailyBriefEngine,
) {

    suspend operator fun invoke(): AppResult<WidgetGlance> =
        when (val brief = briefEngine.today()) {
            is AppResult.Failure -> brief
            is AppResult.Success -> AppResult.Success(project(brief.value))
        }

    private fun project(report: DailyBriefReport): WidgetGlance {
        val money = report.money
        val hasOverdue = money.overdueCount > 0 && money.overdueTotal != null
        return WidgetGlance(
            openTaskCount = report.openTaskCount,
            dueTodayCount = report.dueTodayCount,
            overdueCount = report.overdueCount,
            nextEvent = report.nextEvent,
            nextReminder = report.upcomingReminders.firstOrNull(),
            // The engine already nulls outstanding when there are no unpaid invoices; the invoice
            // count rides along so the widget can say "across 2 invoices" the way Home does.
            outstanding = money.outstandingTotal.takeIf { money.invoiceCount > 0 },
            outstandingInvoiceCount = money.invoiceCount,
            overdue = money.overdueTotal.takeIf { hasOverdue },
            overdueInvoiceCount = money.overdueCount,
            calendarReadable = report.calendarReadable,
        )
    }
}
