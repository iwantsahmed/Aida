package com.rayluxeo.aida.ui.salah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.salah.MonthlySalahSummary
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.SalahDayTracking
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.domain.usecase.GetMonthlySalahUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The monthly Salah tracker.
 *
 * The grid is a live read of the log — [SalahTrackingRepository.observeMonth] re-subscribed as the
 * month changes — while the summary figures come from [GetMonthlySalahUseCase], which computes a
 * streak that may have started before the month in view. Tahajjud is carried in its own fields and
 * never folded into the obligatory score (Abu's instruction). The clock is injected so "today" and
 * the future-day shading are testable without wall-clock flakiness (§4.4).
 */
@HiltViewModel
class SalahMonthViewModel @Inject constructor(
    private val getMonthly: GetMonthlySalahUseCase,
    tracking: SalahTrackingRepository,
    settings: SettingsRepository,
    private val clock: MiraClock,
) : ViewModel() {

    private val today: LocalDate = clock.today()

    /** The current month is the ceiling: there is nothing to show past it but empty future days. */
    private val currentMonth: YearMonth = YearMonth.from(today)

    private val month = MutableStateFlow(currentMonth)
    private val summary = MutableStateFlow<MonthlySalahSummary?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val daysFlow: Flow<List<SalahDayTracking>> =
        month.flatMapLatest { tracking.observeMonth(it) }

    val state: StateFlow<SalahMonthUiState> = combine(
        month,
        daysFlow,
        summary,
        settings.settings,
    ) { month, days, summary, preferences ->
        buildState(month, days, summary, preferences)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = SalahMonthUiState(),
    )

    init {
        recompute()
    }

    fun onPreviousMonth() {
        month.value = month.value.minusMonths(1)
        summary.value = null
        recompute()
    }

    fun onNextMonth() {
        if (month.value >= currentMonth) return
        month.value = month.value.plusMonths(1)
        summary.value = null
        recompute()
    }

    private fun recompute() {
        val target = month.value
        viewModelScope.launch {
            summary.value = getMonthly(target).getOrNull()
        }
    }

    private fun buildState(
        month: YearMonth,
        days: List<SalahDayTracking>,
        summary: MonthlySalahSummary?,
        preferences: MiraSettings,
    ): SalahMonthUiState {
        val language = preferences.languageMode.displayLanguage()
        val words = salahWording(language)
        val firstDay = preferences.firstDayOfWeek
        return SalahMonthUiState(
            isLoading = summary == null,
            language = language,
            monthLabel = words.monthYearLabel(month),
            canGoPrevious = true,
            canGoNext = month < currentMonth,
            weekdayHeaders = weekdayOrder(firstDay),
            weeks = buildWeeks(month, days, firstDay),
            summary = summary,
            countedDays = summary?.let { it.totalPossible / Prayer.OBLIGATORY.size } ?: 0,
        )
    }

    /** The seven weekday headings, rotated so the week starts on Abu's chosen first day. */
    private fun weekdayOrder(firstDay: DayOfWeek): List<DayOfWeek> =
        (0 until DAYS_IN_WEEK).map { firstDay.plus(it.toLong()) }

    private fun buildWeeks(
        month: YearMonth,
        days: List<SalahDayTracking>,
        firstDay: DayOfWeek,
    ): List<List<MonthDayUi?>> {
        val byDate = days.associateBy { it.date }
        val firstOfMonth = month.atDay(1)
        val lead = (firstOfMonth.dayOfWeek.value - firstDay.value + DAYS_IN_WEEK) % DAYS_IN_WEEK

        val cells = ArrayList<MonthDayUi?>()
        repeat(lead) { cells.add(null) }
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val tracking = byDate[date]
            val isFuture = date.isAfter(today)
            cells.add(
                MonthDayUi(
                    dayOfMonth = day,
                    marks = Prayer.OBLIGATORY.map { markOf(tracking, it, isFuture) },
                    tahajjud = tracking?.tahajjud?.status == TahajjudStatus.PRAYED,
                    isFuture = isFuture,
                    isToday = date == today,
                ),
            )
        }
        while (cells.size % DAYS_IN_WEEK != 0) cells.add(null)
        return cells.chunked(DAYS_IN_WEEK)
    }

    private fun markOf(tracking: SalahDayTracking?, prayer: Prayer, isFuture: Boolean): DayMark = when {
        isFuture -> DayMark.FUTURE
        else -> when (tracking?.statusOf(prayer)) {
            PrayerStatus.PRAYED, PrayerStatus.PRAYED_LATE -> DayMark.COMPLETED
            PrayerStatus.MISSED -> DayMark.MISSED
            else -> DayMark.NOT_RECORDED
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        const val DAYS_IN_WEEK = 7
    }
}
