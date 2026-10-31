package com.rayluxeo.aida.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.core.design.component.MiraBarChart
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChartSlice
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.component.miraChartColor
import com.rayluxeo.aida.domain.report.BusinessReport
import com.rayluxeo.aida.domain.report.ReportKey
import com.rayluxeo.aida.domain.report.ReportPeriod
import com.rayluxeo.aida.domain.report.ReportType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The preset windows, in the order the chips show them. Custom is picked separately. */
private val PRESET_PERIODS = listOf(
    ReportPeriod.Today,
    ReportPeriod.ThisWeek,
    ReportPeriod.ThisMonth,
    ReportPeriod.LastMonth,
)

/** A stand-in Custom value whose only use is to read the "Custom" chip label; its dates are ignored. */
private val CUSTOM_CHIP_LABEL = ReportPeriod.Custom(LocalDate.EPOCH, LocalDate.EPOCH)

@Composable
fun ReportsScreen(
    onNavigateUp: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val words = reportsWording(state.language)
    val spacing = MiraTheme.spacing
    val context = LocalContext.current

    var showRangePicker by remember { mutableStateOf(false) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it) }?.let(viewModel::exportPdf)
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it) }?.let(viewModel::exportCsv)
    }

    val exportInProgress = state.export == ExportStatus.RUNNING

    MiraScaffold(
        topBar = {
            MiraTopBar(title = words.screenTitle, onBack = onNavigateUp)
        },
        horizontalPadding = 0.dp,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val periodScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingHorizontalEdges(periodScroll)
                    .horizontalScroll(periodScroll)
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                PRESET_PERIODS.forEach { preset ->
                    MiraChip(
                        text = words.periodLabel(preset),
                        selected = state.period == preset,
                        onClick = { viewModel.selectPeriod(preset) },
                    )
                }
                MiraChip(
                    text = words.periodLabel(CUSTOM_CHIP_LABEL),
                    selected = state.period is ReportPeriod.Custom,
                    onClick = { showRangePicker = true },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                state.reports.firstOrNull()?.range?.let { range ->
                    Text(
                        text = words.rangeLabel(range.fromDate, range.toDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    MiraButton(
                        text = words.exportPdf,
                        onClick = { pdfLauncher.launch(EXPORT_PDF_NAME) },
                        style = MiraButtonStyle.TEXT,
                        enabled = !exportInProgress && state.reports.isNotEmpty(),
                    )
                    MiraButton(
                        text = words.exportCsv,
                        onClick = { csvLauncher.launch(EXPORT_CSV_NAME) },
                        style = MiraButtonStyle.TEXT,
                        enabled = !exportInProgress && state.reports.isNotEmpty(),
                    )
                }
            }

            ExportNotice(status = state.export, words = words, onDismiss = viewModel::dismissExport)

            if (state.isLoading && state.reports.isEmpty()) {
                MiraListSkeleton(
                    contentPadding = PaddingValues(horizontal = spacing.screenHorizontal),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = spacing.screenHorizontal,
                        end = spacing.screenHorizontal,
                        top = spacing.sm,
                        bottom = spacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    items(state.reports, key = { it.type.name }) { report ->
                        ReportCard(report = report, words = words)
                    }
                }
            }
        }
    }

    if (showRangePicker) {
        RangePickerSheet(
            words = words,
            onDismiss = { showRangePicker = false },
            onConfirm = { from, to ->
                viewModel.selectCustom(from, to)
                showRangePicker = false
            },
        )
    }
}

@Composable
private fun ExportNotice(status: ExportStatus, words: ReportsWording, onDismiss: () -> Unit) {
    val spacing = MiraTheme.spacing
    val (text, tone, dismissable) = when (status) {
        ExportStatus.IDLE -> return
        ExportStatus.RUNNING -> Triple(words.exporting, MiraNoticeTone.INFO, false)
        ExportStatus.READY -> Triple(words.exportReady, MiraNoticeTone.CONFIRM, true)
        ExportStatus.FAILED -> Triple(words.exportFailed, MiraNoticeTone.DANGER, true)
    }
    MiraInlineNotice(
        text = text,
        tone = tone,
        modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        onDismiss = if (dismissable) onDismiss else null,
    )
}

@Composable
private fun RangePickerSheet(
    words: ReportsWording,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val spacing = MiraTheme.spacing
    val pickerState = rememberDateRangePickerState()
    val start = pickerState.selectedStartDateMillis
    val end = pickerState.selectedEndDateMillis
    // The calendar is tall, so open fully expanded — a half detent would hide the action row.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    MiraBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // The month grid is tall; bound the sheet so the picker scrolls inside the weighted space
        // and the confirm/cancel row below it is always reachable.
        Column(modifier = Modifier.fillMaxHeight(RANGE_SHEET_HEIGHT_FRACTION)) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.weight(1f),
                title = null,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
            ) {
                MiraButton(
                    text = words.rangeCancel,
                    onClick = onDismiss,
                    style = MiraButtonStyle.TEXT,
                )
                MiraButton(
                    text = words.rangeApply,
                    onClick = {
                        if (start != null && end != null) {
                            onConfirm(start.toUtcLocalDate(), end.toUtcLocalDate())
                        }
                    },
                    enabled = start != null && end != null,
                )
            }
        }
    }
}

/** Material's date picker reports a UTC-midnight epoch; the calendar day is read back in UTC. */
private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun ReportCard(report: BusinessReport, words: ReportsWording) {
    val spacing = MiraTheme.spacing
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = words.reportTitle(report.type),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )

            val headline = report.total?.let(words::money) ?: report.count?.let(words::number)
            if (headline != null) {
                val negativeNet = report.type == ReportType.NET_SUMMARY &&
                    (report.total?.amount?.signum() ?: 0) < 0
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineSmall,
                    color = when {
                        negativeNet -> MiraTheme.colors.danger
                        report.total != null -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onBackground
                    },
                )
            }

            if (report.currencyMixed) {
                Text(
                    text = words.mixedCurrencies,
                    style = MaterialTheme.typography.bodySmall,
                    color = MiraTheme.colors.danger,
                )
            }

            ReportBody(report = report, words = words)
        }
    }
}

@Composable
private fun ReportBody(report: BusinessReport, words: ReportsWording) {
    val spacing = MiraTheme.spacing
    val lines = report.lines
    if (lines.isEmpty()) return

    val allMetric = lines.all { it.key is ReportKey.Metric }
    val amountLines = lines.filter { it.amount != null }

    // A breakdown of named things or categories reads best as bars; metric lines and bare counts
    // read best as a plain labelled list.
    if (!allMetric && amountLines.size >= 2) {
        MiraBarChart(
            slices = amountLines.mapIndexed { index, line ->
                MiraChartSlice(
                    label = reportLineLabel(line.key, words),
                    weight = line.amount!!.amount.toFloat().coerceAtLeast(0f),
                    valueLabel = words.money(line.amount),
                    color = miraChartColor(index),
                )
            },
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = reportLineLabel(line.key, words),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    reportLineValue(line, words)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val EXPORT_PDF_NAME = "mira-report.pdf"
private const val EXPORT_CSV_NAME = "mira-report.csv"

/** The range sheet claims most of the screen so the month grid and the action row both fit. */
private const val RANGE_SHEET_HEIGHT_FRACTION = 0.9f
