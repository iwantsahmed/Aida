package com.rayluxeo.aida.ui.salah

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraIcons
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.salah.MonthlySalahSummary
import com.rayluxeo.aida.domain.salah.Prayer

/** How far the dimmed soft-red of a missed mark is taken down from the full danger tone. */
private const val MISSED_MARK_ALPHA = 0.6f

/**
 * Mira's monthly Salah tracker: a weekday-aligned heatmap of the five prayers for every day of the
 * month, a summary panel of the obligatory figures, and a separate Tahajjud panel — the voluntary
 * night prayer is never folded into the obligatory score (Abu's instruction). The month defaults to
 * the current one; previous and next step through, next stopping at the current month.
 */
@Composable
fun SalahMonthScreen(
    onBack: () -> Unit,
    viewModel: SalahMonthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val words = salahWording(state.language)
    val spacing = MiraTheme.spacing

    MiraScaffold(
        topBar = { MiraTopBar(title = words.monthTitle, onBack = onBack) },
    ) { padding ->
        if (state.isLoading) {
            MiraListSkeleton(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                item {
                    MonthNavHeader(
                        state = state,
                        words = words,
                        onPrevious = viewModel::onPreviousMonth,
                        onNext = viewModel::onNextMonth,
                    )
                }
                item { CalendarGrid(state = state, words = words) }
                state.summary?.let { summary ->
                    item { SummaryPanel(summary = summary, countedDays = state.countedDays, words = words) }
                    item { TahajjudPanel(summary = summary, words = words) }
                }
                item { Spacer(Modifier.height(spacing.lg)) }
            }
        }
    }
}

@Composable
private fun MonthNavHeader(
    state: SalahMonthUiState,
    words: SalahWording,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiraIconButton(
            icon = MiraIcons.ArrowBack,
            contentDescription = words.previousMonth,
            onClick = onPrevious,
            enabled = state.canGoPrevious,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.monthLabel,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        MiraIconButton(
            icon = MiraIcons.KeyboardArrowRight,
            contentDescription = words.nextMonth,
            onClick = onNext,
            enabled = state.canGoNext,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalendarGrid(state: SalahMonthUiState, words: SalahWording) {
    val spacing = MiraTheme.spacing
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            state.weekdayHeaders.forEach { day ->
                Text(
                    text = words.weekdayNarrow(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(spacing.xs))
        state.weeks.forEachIndexed { index, week ->
            if (index > 0) Spacer(Modifier.height(spacing.xs))
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (cell != null) DayCell(cell = cell, words = words)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: MonthDayUi, words: SalahWording) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = words.number(cell.dayOfMonth),
            style = MaterialTheme.typography.labelMedium,
            color = when {
                cell.isToday -> MaterialTheme.colorScheme.primary
                cell.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.hairline)) {
            cell.marks.forEach { PrayerMark(mark = it) }
        }
        // Tahajjud reads as a thin champagne underline rather than a sixth dot, so the night prayer
        // is marked on the calendar without being counted among the five.
        if (cell.tahajjud) {
            Box(
                modifier = Modifier
                    .width(spacing.sm)
                    .height(spacing.hairline)
                    .background(MaterialTheme.colorScheme.primary),
            )
        } else {
            Spacer(Modifier.height(spacing.hairline))
        }
    }
}

@Composable
private fun PrayerMark(mark: DayMark) {
    val spacing = MiraTheme.spacing
    val colors = MiraTheme.colors
    val base = Modifier.size(spacing.xxs)
    val modifier = when (mark) {
        DayMark.COMPLETED -> base.background(MaterialTheme.colorScheme.primary, CircleShape)
        DayMark.MISSED -> base.background(colors.danger.copy(alpha = MISSED_MARK_ALPHA), CircleShape)
        DayMark.NOT_RECORDED -> base.border(spacing.hairline, colors.hairlineStrong, CircleShape)
        DayMark.FUTURE -> base.border(spacing.hairline, colors.hairline, CircleShape)
    }
    Box(modifier = modifier)
}

@Composable
private fun SummaryPanel(summary: MonthlySalahSummary, countedDays: Int, words: SalahWording) {
    val spacing = MiraTheme.spacing
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            MiraSectionHeader(text = words.summaryTitle)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = words.percentage(summary.percentage),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = words.completedOf(summary.totalCompleted, summary.totalPossible),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            MiraDivider()
            MiraSectionHeader(text = words.byPrayerTitle)
            Prayer.OBLIGATORY.forEach { prayer ->
                StatRow(
                    label = words.prayerName(prayer),
                    value = words.completedOf(summary.perPrayerCompleted[prayer] ?: 0, countedDays),
                )
            }

            MiraDivider()
            StatRow(words.daysAllFiveTitle, words.number(summary.daysAllFive))
            StatRow(words.currentStreakTitle, words.days(summary.currentStreak))
            StatRow(words.bestStreakTitle, words.days(summary.bestStreak))
            StatRow(words.missedTitle, words.number(summary.missedCount))
        }
    }
}

@Composable
private fun TahajjudPanel(summary: MonthlySalahSummary, words: SalahWording) {
    val spacing = MiraTheme.spacing
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MiraIcons.Bedtime,
                    contentDescription = null,
                    tint = MiraTheme.colors.accentDim,
                )
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = words.tahajjudTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StatRow(words.tahajjudNightsTitle, words.nights(summary.tahajjudNights))
            StatRow(words.tahajjudStreakTitle, words.nights(summary.tahajjudCurrentStreak))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
