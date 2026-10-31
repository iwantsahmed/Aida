package com.rayluxeo.aida.ui.reports

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.report.BusinessReport
import com.rayluxeo.aida.domain.report.ReportKey
import com.rayluxeo.aida.domain.report.ReportLine
import com.rayluxeo.aida.domain.report.ReportMetric
import com.rayluxeo.aida.domain.report.ReportRange
import com.rayluxeo.aida.domain.report.ReportType
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/**
 * The report-to-document formatter and the CSV writer, which the screen, the PDF and the CSV all
 * share. The PDF path draws on `android.graphics` and is exercised on device, not here.
 */
class ReportExportTest {

    private val range = ReportRange(
        start = Instant.parse("2026-10-01T00:00:00Z"),
        endExclusive = Instant.parse("2026-11-01T00:00:00Z"),
        fromDate = LocalDate.of(2026, 10, 1),
        toDate = LocalDate.of(2026, 10, 31),
    )

    private fun netSummary() = BusinessReport(
        type = ReportType.NET_SUMMARY,
        range = range,
        total = Money.of("1200"),
        lines = listOf(
            ReportLine(ReportKey.Metric(ReportMetric.INCOME), amount = Money.of("2000")),
            ReportLine(ReportKey.Metric(ReportMetric.EXPENSES), amount = Money.of("800")),
            ReportLine(ReportKey.Metric(ReportMetric.NET), amount = Money.of("1200")),
        ),
    )

    private fun clientRevenue() = BusinessReport(
        type = ReportType.CLIENT_REVENUE,
        range = range,
        total = Money.of("2000"),
        lines = listOf(
            ReportLine(ReportKey.Named("Acme, Inc."), amount = Money.of("2000")),
        ),
    )

    @Test
    fun `buildReportDoc lays out title, range and sections`() {
        val doc = buildReportDoc(listOf(netSummary()), EnglishReportsWording)

        assertThat(doc.title).isEqualTo("Reports")
        assertThat(doc.rangeLabel).isEqualTo("1–31 Oct")
        assertThat(doc.sections).hasSize(1)

        val section = doc.sections.single()
        assertThat(section.title).isEqualTo("Net business summary")
        assertThat(section.headline).contains("৳")
        assertThat(section.rows.map { it.label }).containsExactly("Income", "Expenses", "Net").inOrder()
        assertThat(section.rows.all { it.value.contains("৳") }).isTrue()
    }

    @Test
    fun `writeCsv emits a header, a total and the breakdown rows`() {
        val doc = buildReportDoc(listOf(netSummary()), EnglishReportsWording)
        val csv = exportToString(doc)

        val lines = csv.lines()
        assertThat(lines.first()).isEqualTo("Reports")
        assertThat(lines[1]).isEqualTo("1–31 Oct")
        assertThat(csv).contains("Net business summary")
        assertThat(csv).contains("Total,")
        assertThat(csv).contains("Income,")
        assertThat(csv).contains("Net,")
    }

    @Test
    fun `writeCsv quotes a field that contains a comma`() {
        val doc = buildReportDoc(listOf(clientRevenue()), EnglishReportsWording)
        val csv = exportToString(doc)

        // The client name carries a comma, so it must be wrapped in quotes to stay one field.
        assertThat(csv).contains("\"Acme, Inc.\",")
    }

    @Test
    fun `writeCsv renders a count report without a currency sign`() {
        val taskReport = BusinessReport(
            type = ReportType.TASK_COMPLETION,
            range = range,
            count = 3,
            lines = listOf(
                ReportLine(ReportKey.Metric(ReportMetric.TASKS_COMPLETED), count = 3),
                ReportLine(ReportKey.Metric(ReportMetric.TASKS_OPENED), count = 5),
            ),
        )
        val csv = exportToString(buildReportDoc(listOf(taskReport), EnglishReportsWording))

        assertThat(csv).contains("Task completion")
        assertThat(csv).contains("Completed,3")
        assertThat(csv).contains("Opened,5")
        assertThat(csv).doesNotContain("Completed,৳")
    }

    private fun exportToString(doc: ReportDoc): String {
        val out = ByteArrayOutputStream()
        val result = ReportExporter().writeCsv(doc, out)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return out.toString(Charsets.UTF_8)
    }
}
