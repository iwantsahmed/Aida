package com.rayluxeo.aida.ui.reports

import com.rayluxeo.aida.domain.report.BusinessReport
import com.rayluxeo.aida.domain.report.ReportKey
import com.rayluxeo.aida.domain.report.ReportLine

/**
 * A report rendered down to plain strings — a title, a headline and labelled rows — so the screen,
 * a CSV and a PDF all lay out the same words. Money and digits are already in Abu's language here;
 * the domain [BusinessReport] carried only [com.rayluxeo.aida.domain.model.Money] and counts (§4).
 */
data class ReportDoc(
    val title: String,
    val rangeLabel: String,
    val sections: List<ReportSection>,
)

data class ReportSection(
    val title: String,
    val headline: String?,
    val rows: List<ReportRow>,
)

data class ReportRow(val label: String, val value: String)

/** Turn the computed reports into one laid-out document in Abu's language. */
internal fun buildReportDoc(reports: List<BusinessReport>, words: ReportsWording): ReportDoc {
    val range = reports.firstOrNull()?.range
    return ReportDoc(
        title = words.screenTitle,
        rangeLabel = range?.let { words.rangeLabel(it.fromDate, it.toDate) }.orEmpty(),
        sections = reports.map { report ->
            ReportSection(
                title = words.reportTitle(report.type),
                headline = report.total?.let(words::money) ?: report.count?.let(words::number),
                rows = report.lines.map { line ->
                    ReportRow(reportLineLabel(line.key, words), reportLineValue(line, words).orEmpty())
                },
            )
        },
    )
}

internal fun reportLineLabel(key: ReportKey, words: ReportsWording): String = when (key) {
    is ReportKey.Category -> words.categoryLabel(key.category)
    is ReportKey.Status -> words.statusLabel(key.status)
    is ReportKey.Named -> key.name
    is ReportKey.Metric -> words.metricLabel(key.metric)
}

internal fun reportLineValue(line: ReportLine, words: ReportsWording): String? =
    line.amount?.let(words::money) ?: line.count?.let(words::number)
