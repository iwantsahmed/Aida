package com.rayluxeo.aida.ui.reports

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import java.io.OutputStream
import javax.inject.Inject

/**
 * Writes a [ReportDoc] out as CSV or PDF, on the device, with no charting or document library (§2).
 *
 * The PDF is drawn with Android's own [PdfDocument] and [android.graphics.Canvas], the same approach
 * the invoice generator uses, onto white A4 pages that paginate when a page fills. Text is drawn
 * with the platform's default family, whose system fallback covers Bengali, so a report of Abu's
 * work prints in whichever language he read it in.
 */
class ReportExporter @Inject constructor() {

    fun writeCsv(doc: ReportDoc, out: OutputStream): AppResult<Unit> = try {
        out.bufferedWriter().use { writer ->
            writer.appendLine(cell(doc.title))
            writer.appendLine(cell(doc.rangeLabel))
            doc.sections.forEach { section ->
                writer.appendLine()
                writer.appendLine(cell(section.title))
                section.headline?.let { writer.appendLine("${cell(TOTAL_KEY)},${cell(it)}") }
                section.rows.forEach { writer.appendLine("${cell(it.label)},${cell(it.value)}") }
            }
        }
        Unit.asSuccess()
    } catch (e: Exception) {
        failure(MiraErrorCode.STORAGE, "CSV export failed: ${e.message}")
    }

    fun writePdf(doc: ReportDoc, out: OutputStream): AppResult<Unit> {
        val document = PdfDocument()
        return try {
            var pageNumber = 1
            var page = document.startPage(pageInfo(pageNumber))
            var canvas = page.canvas.also { it.drawColor(Color.WHITE) }
            var y = MARGIN_TOP

            canvas.drawText(doc.title, MARGIN, y, titlePaint)
            y += 26f
            if (doc.rangeLabel.isNotEmpty()) {
                canvas.drawText(doc.rangeLabel, MARGIN, y, mutedPaint)
                y += 24f
            }

            for (section in doc.sections) {
                val needed = 22f + (if (section.headline != null) 20f else 0f) + section.rows.size * 16f + 16f
                if (y + needed > PAGE_HEIGHT - MARGIN_TOP) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(pageInfo(pageNumber))
                    canvas = page.canvas.also { it.drawColor(Color.WHITE) }
                    y = MARGIN_TOP
                }
                canvas.drawText(section.title, MARGIN, y, sectionPaint)
                y += 20f
                section.headline?.let {
                    canvas.drawText(it, MARGIN, y, headlinePaint)
                    y += 20f
                }
                for (row in section.rows) {
                    canvas.drawText(row.label, MARGIN, y, rowPaint)
                    canvas.drawText(row.value, PAGE_WIDTH - MARGIN, y, rowRightPaint)
                    y += 16f
                }
                y += 16f
            }

            document.finishPage(page)
            out.use { document.writeTo(it) }
            Unit.asSuccess()
        } catch (e: Exception) {
            failure(MiraErrorCode.STORAGE, "PDF export failed: ${e.message}")
        } finally {
            document.close()
        }
    }

    private fun pageInfo(number: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), number).create()

    /** RFC-4180 quoting: wrap and double a quote only when a cell actually needs it. */
    private fun cell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_CHAMPAGNE
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_MUTED
        textSize = 10f
    }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_CARBON
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_CHAMPAGNE
        textSize = 15f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_CARBON
        textSize = 11f
    }
    private val rowRightPaint = Paint(rowPaint).apply { textAlign = Paint.Align.RIGHT }

    private companion object {
        const val TOTAL_KEY = "Total"
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN = 40f
        const val MARGIN_TOP = 56f

        val COLOR_CARBON = Color.rgb(17, 17, 17)
        val COLOR_MUTED = Color.rgb(115, 115, 110)
        val COLOR_CHAMPAGNE = Color.rgb(168, 133, 76)
    }
}
