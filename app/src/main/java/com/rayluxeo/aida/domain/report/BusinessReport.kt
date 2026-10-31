package com.rayluxeo.aida.domain.report

import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.ProjectStatus

/**
 * One computed report: a headline figure and an optional breakdown.
 *
 * It carries domain values only — [Money], counts, enums, data names — and never a formatted
 * string, because wording and currency formatting belong to the presentation layer (§4). [total]
 * is null when the report has no single money figure, or when its amounts spanned more than one
 * currency and Mira has no rates to combine them; [currencyMixed] tells the UI which of those it
 * was, so it can say "mixed currencies" rather than show a wrong zero.
 */
data class BusinessReport(
    val type: ReportType,
    val range: ReportRange,
    val total: Money? = null,
    val count: Int? = null,
    val lines: List<ReportLine> = emptyList(),
    val currencyMixed: Boolean = false,
)

/** One breakdown row: what it is about, and the amount and/or count for it. */
data class ReportLine(
    val key: ReportKey,
    val amount: Money? = null,
    val count: Int? = null,
)

/**
 * What a headline or a line is about, kept as data so the presentation layer words it per language.
 * An expense category and a project status are enums Mira already knows how to say; a client,
 * project, invoice or subscription name is Abu's own text and is shown as written; a [Metric] is a
 * fixed figure whose label is the renderer's.
 */
sealed interface ReportKey {
    data class Category(val category: ExpenseCategory) : ReportKey
    data class Status(val status: ProjectStatus) : ReportKey
    data class Named(val name: String) : ReportKey
    data class Metric(val metric: ReportMetric) : ReportKey
}

/** The fixed figures a report headline or line can carry, worded by the presentation layer. */
enum class ReportMetric {
    INCOME,
    EXPENSES,
    NET,
    PAYMENTS_RECEIVED,
    OUTSTANDING,
    TASKS_COMPLETED,
    TASKS_OPENED,
    TASKS_OVERDUE,
    SUBSCRIPTIONS_MONTHLY,
}
