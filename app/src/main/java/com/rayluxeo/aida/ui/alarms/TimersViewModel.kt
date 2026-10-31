package com.rayluxeo.aida.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.TimerRecord
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TimerRepository
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import com.rayluxeo.aida.domain.usecase.CancelTimerUseCase
import com.rayluxeo.aida.domain.usecase.PauseTimerUseCase
import com.rayluxeo.aida.domain.usecase.ResumeTimerUseCase
import com.rayluxeo.aida.domain.usecase.StartTimerUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The countdowns.
 *
 * What is left of a timer is never stored and never decremented here. Every tick reads
 * [TimerRecord.remainingAt] against the injected clock, so a countdown is still correct after the
 * process has been killed and rebuilt — the wall clock is the truth, not a number Mira last
 * managed to write down.
 *
 * Several timers run at once because the OS holds one alarm per timer id; nothing about the
 * screen limits it to one.
 *
 * Every state change goes through a use case. The repository is observed directly for the read
 * path only, which is the one thing AGENTS.md §4 allows a view model to do on its own.
 */
@HiltViewModel
class TimersViewModel @Inject constructor(
    timers: TimerRepository,
    private val startTimer: StartTimerUseCase,
    private val pauseTimer: PauseTimerUseCase,
    private val resumeTimer: ResumeTimerUseCase,
    private val cancelTimer: CancelTimerUseCase,
    private val exactAlarms: ExactAlarmAccess,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
    private val renderer: ResponseRenderer,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    /**
     * One second at a time, and only while the screen is actually being looked at: the whole
     * chain is [SharingStarted.WhileSubscribed], so nothing wakes up behind a closed screen.
     */
    private val ticks: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MILLIS)
        }
    }

    /**
     * The permission, starting from what it is now.
     *
     * [ExactAlarmAccess.changes] only reports changes, so without the opening value the combine
     * below would never produce a first state at all.
     */
    private val exactAccess: Flow<Boolean> =
        exactAlarms.changes.onStart { emit(exactAlarms.canScheduleExact()) }

    val uiState: StateFlow<TimersUiState> = combine(
        local,
        timers.observeActive(),
        ticks,
        settings.settings,
        exactAccess,
    ) { state, active, now, preferences, canScheduleExact ->
        val language = preferences.languageMode.displayLanguage()
        TimersUiState(
            isLoading = false,
            language = language,
            timers = active.map { it.toRow(now, language) },
            composer = state.composer,
            canScheduleExact = canScheduleExact,
            notice = state.notice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = TimersUiState(),
    )

    fun onOpenComposer() = local.update { it.copy(composer = TimerComposerUiState()) }

    fun onCloseComposer() = local.update { it.copy(composer = null) }

    fun onComposerLabelChange(label: String) = updateComposer { it.copy(label = label) }

    /** Digits only: the field feeds a duration, and a stray letter would silently mean zero. */
    fun onComposerMinutesChange(minutes: String) = updateComposer {
        it.copy(minutes = minutes.filter { character -> character.isDigit() }.take(MAX_MINUTE_DIGITS))
    }

    fun onSelectPreset(minutes: Long) = updateComposer { it.copy(minutes = minutes.toString()) }

    fun onStartTimer() {
        val composer = local.value.composer ?: return
        val duration = composer.duration ?: return
        updateComposer { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val started = startTimer(composer.label.trim(), duration)) {
                is AppResult.Success -> local.update { it.copy(composer = null, notice = null) }
                is AppResult.Failure -> {
                    updateComposer { it.copy(isSaving = false) }
                    showProblem(started.error.code)
                }
            }
        }
    }

    fun onPause(id: Long) = perform(id) { pauseTimer(it) }

    fun onResume(id: Long) = perform(id) { resumeTimer(it) }

    fun onCancel(id: Long) = perform(id) { cancelTimer(it) }

    fun onDismissNotice() = local.update { it.copy(notice = null) }

    /**
     * Nothing is said on success: the row moving, pausing or leaving the list says it already,
     * and a line of text repeating what Abu can see is noise. A refusal is different, because
     * the screen would otherwise look as though the tap did nothing.
     */
    private fun perform(id: Long, action: suspend (Long) -> AppResult<TimerRecord>) {
        viewModelScope.launch {
            val outcome = action(id)
            if (outcome is AppResult.Failure) showProblem(outcome.error.code)
        }
    }

    private fun updateComposer(transform: (TimerComposerUiState) -> TimerComposerUiState) =
        local.update { state -> state.composer?.let { state.copy(composer = transform(it)) } ?: state }

    private suspend fun showProblem(code: MiraErrorCode) {
        val language = settings.current().languageMode.displayLanguage()
        local.update {
            it.copy(
                notice = AlarmsNotice(
                    message = renderer.display(MiraResponse.Problem(code), language),
                    tone = AlarmsNoticeTone.PROBLEM,
                ),
            )
        }
    }

    private fun TimerRecord.toRow(now: Instant, language: SpokenLanguage): TimerRow {
        val words = alarmsWording(language)
        return TimerRow(
            id = id,
            label = label.ifBlank { words.untitledTimer },
            countdown = words.countdown(remainingAt(now)),
            durationLabel = if (state == TimerState.PAUSED) {
                words.timerPaused
            } else {
                words.durationSummary(duration)
            },
            isRunning = state == TimerState.RUNNING,
        )
    }

    private data class LocalState(
        val composer: TimerComposerUiState? = null,
        val notice: AlarmsNotice? = null,
    )

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /** Four digits is 24 hours and then some; beyond that the field is not a countdown. */
        const val MAX_MINUTE_DIGITS = 4
    }
}
