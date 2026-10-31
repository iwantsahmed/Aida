package com.rayluxeo.aida.ui.salah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerCity
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.SalahClock
import com.rayluxeo.aida.domain.salah.SalahDay
import com.rayluxeo.aida.domain.salah.SalahDayTracking
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.domain.usecase.GetSalahDayUseCase
import com.rayluxeo.aida.domain.usecase.SetPrayerCityUseCase
import com.rayluxeo.aida.domain.usecase.TrackPrayerUseCase
import com.rayluxeo.aida.domain.usecase.TrackTahajjudUseCase
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Mira's Salah main screen.
 *
 * The countdown is never stored and never decremented here. Every tick reads the clock and asks
 * [SalahClock] for the next prayer again, so the hero is still correct after the process has been
 * rebuilt — the wall clock is the truth, exactly as it is for the timer countdowns. The whole tick
 * chain is [SharingStarted.WhileSubscribed], so nothing wakes up behind a closed screen (§3).
 *
 * Every write goes through a use case; the tracking repository is observed directly only for the
 * read path, which is the one thing §4 lets a view model do on its own. The prayer times, the
 * dates and the countdown are worded here because they depend on the injected clock and formatter;
 * the static chrome and the status words are rendered in the screen from the same wording.
 */
@HiltViewModel
class SalahViewModel @Inject constructor(
    private val getSalahDay: GetSalahDayUseCase,
    private val trackPrayer: TrackPrayerUseCase,
    private val trackTahajjud: TrackTahajjudUseCase,
    private val setPrayerCity: SetPrayerCityUseCase,
    tracking: SalahTrackingRepository,
    salahSettings: SalahSettingsRepository,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
    private val timeFormatter: TaskTimeFormatter,
) : ViewModel() {

    /** Fixed for the life of the screen: a day view is about one day, and "today" is that day. */
    private val today: LocalDate = clock.today()

    private val dayState = MutableStateFlow<DayLoad>(DayLoad.Loading)
    private val local = MutableStateFlow(LocalState())

    private val ticks: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MILLIS)
        }
    }

    private val language: Flow<SpokenLanguage> =
        settings.settings.map { it.languageMode.displayLanguage() }

    val state: StateFlow<SalahUiState> = combine(
        dayState,
        tracking.observeDay(today),
        ticks,
        language,
        local,
    ) { day, dayTracking, now, language, localState ->
        buildState(day, dayTracking, now, language, localState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = SalahUiState(),
    )

    init {
        // A location (or a change to the calculation parameters) must re-resolve the day, so the
        // settings flow is the trigger rather than a one-shot load. Unconfigured is read straight
        // from [isConfigured] so the setup prompt never flashes a wrong time first.
        viewModelScope.launch {
            salahSettings.settings.collect { configured ->
                if (!configured.isConfigured) {
                    dayState.value = DayLoad.NotConfigured
                } else {
                    when (val result = getSalahDay(today)) {
                        is AppResult.Success -> dayState.value = DayLoad.Ready(result.value)
                        is AppResult.Failure -> dayState.value = DayLoad.NotConfigured
                    }
                }
            }
        }
    }

    /** Sets a city directly from the setup prompt without asking for GPS. */
    fun selectCity(city: PrayerCity) {
        viewModelScope.launch {
            val useBengali = (state.value.language == SpokenLanguage.BENGALI)
            setPrayerCity(city, useBengali)
        }
    }

    /** The one-tap mark: a prayer was prayed on time. The other states live behind the status sheet. */
    fun onMarkPrayed(prayer: Prayer) = setStatus(prayer, PrayerStatus.PRAYED)

    /** Correct a prayer to any status Abu chose from the sheet, so a wrong tap is never stuck. */
    fun onSetStatus(prayer: Prayer, status: PrayerStatus) = setStatus(prayer, status)

    /** The one-tap mark for Tahajjud. */
    fun onMarkTahajjud() = setTahajjud(TahajjudStatus.PRAYED)

    fun onSetTahajjud(status: TahajjudStatus) = setTahajjud(status)

    fun onDismissNotice() = local.update { it.copy(notice = null) }

    private fun setStatus(prayer: Prayer, status: PrayerStatus) {
        viewModelScope.launch {
            val scheduledAt = readyDay()?.times?.time(prayer)?.toInstant()
            val result = trackPrayer(prayer, status, today, scheduledAt)
            if (result is AppResult.Failure) showProblem()
        }
    }

    private fun setTahajjud(status: TahajjudStatus) {
        viewModelScope.launch {
            val result = trackTahajjud(status, today)
            if (result is AppResult.Failure) showProblem()
        }
    }

    private fun readyDay(): SalahDay? = (dayState.value as? DayLoad.Ready)?.day

    private suspend fun showProblem() {
        val language = settings.current().languageMode.displayLanguage()
        local.update { it.copy(notice = salahWording(language).trackingFailed) }
    }

    private fun buildState(
        day: DayLoad,
        dayTracking: SalahDayTracking,
        now: Instant,
        language: SpokenLanguage,
        localState: LocalState,
    ): SalahUiState {
        val words = salahWording(language)
        return when (day) {
            DayLoad.Loading -> SalahUiState(
                isLoading = true,
                language = language,
                notice = localState.notice,
            )

            DayLoad.NotConfigured -> SalahUiState(
                isLoading = false,
                isConfigured = false,
                language = language,
                gregorianDate = timeFormatter.dateHeadline(today, language),
                notice = localState.notice,
            )

            is DayLoad.Ready -> {
                val salahDay = day.day
                val next = SalahClock.nextPrayer(now, salahDay.times, salahDay.tomorrowFajr)
                val remaining = SalahClock.remainingUntil(now, next.nextTime)
                val nextName = words.prayerName(next.next)
                val activePrayer = next.current
                val rawHadith = com.rayluxeo.aida.domain.salah.WaqtHadithProvider.forPrayer(activePrayer, salahDay.date)
                val hadithUi = WaqtHadithUi(
                    prayer = activePrayer,
                    prayerName = words.prayerName(activePrayer),
                    arabic = rawHadith.arabic,
                    bangla = rawHadith.bangla,
                    reference = rawHadith.reference,
                )
                SalahUiState(
                    isLoading = false,
                    isConfigured = true,
                    language = language,
                    gregorianDate = timeFormatter.dateHeadline(salahDay.date, language),
                    hijriDate = words.hijriLabel(salahDay.hijri),
                    locationLabel = salahDay.location.cityLabel,
                    hero = SalahHeroUi(
                        currentPrayer = next.current,
                        nextPrayer = next.next,
                        nextPrayerName = nextName,
                        nextPrayerTime = timeFormatter.timeLabel(next.nextTime.toLocalTime(), language),
                        untilText = words.until(nextName),
                        countdown = words.countdown(remaining),
                    ),
                    prayers = Prayer.OBLIGATORY.map { prayer ->
                        val time = salahDay.times.time(prayer)!!
                        val stored = dayTracking.statusOf(prayer)
                        val display = displayStatus(stored, time.toInstant(), now)
                        PrayerRowUi(
                            prayer = prayer,
                            name = words.prayerName(prayer),
                            time = timeFormatter.timeLabel(time.toLocalTime(), language),
                            storedStatus = stored,
                            displayStatus = display,
                            statusLabel = words.statusLabel(display),
                        )
                    },
                    tahajjud = run {
                        val window = salahDay.tahajjud
                        val status = dayTracking.tahajjud?.status ?: TahajjudStatus.NOT_RECORDED
                        val from = timeFormatter.timeLabel(window.lastThirdStart.toLocalTime(), language)
                        val to = timeFormatter.timeLabel(window.nextFajr.toLocalTime(), language)
                        TahajjudCardUi(
                            status = status,
                            statusLabel = words.tahajjudStatusLabel(status),
                            windowLabel = "$from — $to",
                        )
                    },
                    hadith = hadithUi,
                    notice = localState.notice,
                )
            }
        }
    }

    /**
     * What the circle shows for a prayer the log has not touched: before its time it is
     * [PrayerStatus.UPCOMING], after it the gentle [PrayerStatus.NOT_YET] — never a failure. A
     * status Abu has set himself is shown verbatim.
     */
    private fun displayStatus(stored: PrayerStatus, time: Instant, now: Instant): PrayerStatus =
        when {
            stored != PrayerStatus.NOT_RECORDED -> stored
            now.isBefore(time) -> PrayerStatus.UPCOMING
            else -> PrayerStatus.NOT_YET
        }

    /** How the day's times have resolved — or that no place is set yet. */
    private sealed interface DayLoad {
        data object Loading : DayLoad
        data object NotConfigured : DayLoad
        data class Ready(val day: SalahDay) : DayLoad
    }

    private data class LocalState(val notice: String? = null)

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
