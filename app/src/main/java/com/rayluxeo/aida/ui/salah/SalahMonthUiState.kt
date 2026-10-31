package com.rayluxeo.aida.ui.salah

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.salah.MonthlySalahSummary
import java.time.DayOfWeek

/**
 * Everything the monthly Salah tracker draws from.
 *
 * The grid is pre-arranged into weeks here so the screen only lays out what it is handed —
 * [weeks] is week rows of seven cells, a null cell being the padding before the first of the month
 * or after the last. [summary] is the deterministic domain figure; its labels are worded in the
 * screen from the same [SalahWording], exactly as the reports screen renders its domain reports.
 */
data class SalahMonthUiState(
    val isLoading: Boolean = true,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val monthLabel: String = "",
    val canGoPrevious: Boolean = true,
    val canGoNext: Boolean = false,
    val weekdayHeaders: List<DayOfWeek> = emptyList(),
    val weeks: List<List<MonthDayUi?>> = emptyList(),
    val summary: MonthlySalahSummary? = null,
    /** Days of the month that have arrived — the per-prayer denominator for the summary. */
    val countedDays: Int = 0,
)

/** How one prayer stands on one day, reduced to the four states a small mark can carry. */
enum class DayMark {
    /** Prayed or prayed late — a champagne-filled mark. */
    COMPLETED,

    /** Explicitly missed — a dimmed soft-red mark. */
    MISSED,

    /** The day has arrived but this prayer was not logged — a muted outline. */
    NOT_RECORDED,

    /** A day still to come — a faint outline. */
    FUTURE,
}

/** One day's cell in the grid: its five prayer marks, whether Tahajjud was prayed, and where it sits. */
data class MonthDayUi(
    val dayOfMonth: Int,
    val marks: List<DayMark>,
    val tahajjud: Boolean,
    val isFuture: Boolean,
    val isToday: Boolean,
)
