package com.rayluxeo.aida.ui.alarms

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.usecase.CancelTimerUseCase
import com.rayluxeo.aida.domain.usecase.PauseTimerUseCase
import com.rayluxeo.aida.domain.usecase.ResumeTimerUseCase
import com.rayluxeo.aida.domain.usecase.StartTimerUseCase
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import com.rayluxeo.aida.ui.tasks.FakeSettingsRepository
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The timers screen, assembled by hand — Hilt's local-test transform does not run under AGP 9.
 *
 * The first test is the one that matters. A countdown decremented by a ticking counter passes a
 * naive test and is wrong the moment the process is killed, so what is asserted here is that
 * moving the clock alone changes the number on screen.
 */
class TimersViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 9, 0))
    private val timers = FakeTimerRepository(clock)
    private val scheduler = FakeTimerScheduler()
    private val exactAlarms = FakeExactAlarmAccess()
    private val settings = FakeSettingsRepository()
    private val renderer = MiraResponseRenderer(clock)

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the countdown comes from the clock rather than from a counter`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        startTimer(viewModel, label = "Tea", minutes = "25")

        assertThat(viewModel.uiState.value.timers.single().countdown).isEqualTo("25:00")

        // Nothing in the app moved; only the wall clock did, exactly as it does while Mira is
        // closed. The row has to follow it.
        clock.moveTo(LocalDateTime.of(2026, 3, 5, 9, 1))
        advanceTimeBy(1_100)
        runCurrent()

        assertThat(viewModel.uiState.value.timers.single().countdown).isEqualTo("24:00")
    }

    @Test
    fun `several timers run at once, soonest first`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        startTimer(viewModel, label = "Bread", minutes = "40")
        startTimer(viewModel, label = "Tea", minutes = "5")

        assertThat(viewModel.uiState.value.timers.map { it.label }).containsExactly("Tea", "Bread").inOrder()
        assertThat(scheduler.scheduled).hasSize(2)
    }

    @Test
    fun `pausing freezes what is left and takes the alarm down`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)
        startTimer(viewModel, label = "Tea", minutes = "25")
        val id = viewModel.uiState.value.timers.single().id

        clock.moveTo(LocalDateTime.of(2026, 3, 5, 9, 5))
        viewModel.onPause(id)
        runCurrent()

        assertThat(scheduler.cancelled).containsExactly(id)
        assertThat(viewModel.uiState.value.timers.single().isRunning).isFalse()
        assertThat(viewModel.uiState.value.timers.single().countdown).isEqualTo("20:00")

        // A paused countdown is measured from nothing, so the clock moving must not move it.
        clock.moveTo(LocalDateTime.of(2026, 3, 5, 9, 40))
        advanceTimeBy(1_100)
        runCurrent()

        assertThat(viewModel.uiState.value.timers.single().countdown).isEqualTo("20:00")
    }

    @Test
    fun `resuming arms the new ending and starts counting again`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)
        startTimer(viewModel, label = "Tea", minutes = "25")
        val id = viewModel.uiState.value.timers.single().id

        clock.moveTo(LocalDateTime.of(2026, 3, 5, 9, 5))
        viewModel.onPause(id)
        runCurrent()
        clock.moveTo(LocalDateTime.of(2026, 3, 5, 10, 0))
        viewModel.onResume(id)
        // Past one tick: the countdown is read from the ticker rather than stored, so moving the
        // clock is not enough on its own — the flow has to emit again before the screen knows.
        advanceTimeBy(1_100)
        runCurrent()

        assertThat(viewModel.uiState.value.timers.single().isRunning).isTrue()
        assertThat(viewModel.uiState.value.timers.single().countdown).isEqualTo("20:00")
        assertThat(timers.snapshot().single().endsAt)
            .isEqualTo(LocalDateTime.of(2026, 3, 5, 10, 20).atZone(FakeMiraClock.DHAKA).toInstant())
    }

    @Test
    fun `cancelling takes the row off the screen and the alarm off the OS`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)
        startTimer(viewModel, label = "Tea", minutes = "25")
        val id = viewModel.uiState.value.timers.single().id

        viewModel.onCancel(id)
        runCurrent()

        assertThat(viewModel.uiState.value.timers).isEmpty()
        assertThat(scheduler.cancelled).contains(id)
        assertThat(timers.snapshot().single().state).isEqualTo(TimerState.CANCELLED)
    }

    /** A refused start leaves the sheet open with what Abu typed, and says what went wrong. */
    @Test
    fun `a timer that cannot be started says so and keeps the sheet`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)
        timers.failWith = MiraErrorCode.STORAGE

        startTimer(viewModel, label = "Tea", minutes = "25")

        assertThat(viewModel.uiState.value.timers).isEmpty()
        assertThat(viewModel.uiState.value.composer?.minutes).isEqualTo("25")
        assertThat(viewModel.uiState.value.composer?.isSaving).isFalse()
        assertThat(viewModel.uiState.value.notice?.tone).isEqualTo(AlarmsNoticeTone.PROBLEM)
    }

    /** A field that is empty, or holds more than a day, is not a countdown Mira will run. */
    @Test
    fun `the start button waits for a length that can actually be run`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)
        viewModel.onOpenComposer()
        runCurrent()

        assertThat(viewModel.uiState.value.composer?.canStart).isFalse()

        viewModel.onComposerMinutesChange("0")
        runCurrent()
        assertThat(viewModel.uiState.value.composer?.canStart).isFalse()

        viewModel.onSelectPreset(25)
        runCurrent()
        assertThat(viewModel.uiState.value.composer?.duration).isEqualTo(Duration.ofMinutes(25))
        assertThat(viewModel.uiState.value.composer?.canStart).isTrue()
    }

    /** Letters in a numeric field would silently mean zero, so they never get in. */
    @Test
    fun `the minutes field keeps only digits`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)
        viewModel.onOpenComposer()

        viewModel.onComposerMinutesChange("2a5")
        runCurrent()

        assertThat(viewModel.uiState.value.composer?.minutes).isEqualTo("25")
    }

    @Test
    fun `withdrawing exact alarms is reported while the screen is open`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.canScheduleExact).isTrue()

        exactAlarms.set(false)
        runCurrent()

        assertThat(viewModel.uiState.value.canScheduleExact).isFalse()
    }

    private fun createViewModel() = TimersViewModel(
        timers = timers,
        startTimer = StartTimerUseCase(timers, scheduler),
        pauseTimer = PauseTimerUseCase(timers, scheduler),
        resumeTimer = ResumeTimerUseCase(timers, scheduler),
        cancelTimer = CancelTimerUseCase(timers, scheduler),
        exactAlarms = exactAlarms,
        settings = settings,
        clock = clock,
        renderer = renderer,
    )

    private fun TestScope.startTimer(viewModel: TimersViewModel, label: String, minutes: String) {
        viewModel.onOpenComposer()
        viewModel.onComposerLabelChange(label)
        viewModel.onComposerMinutesChange(minutes)
        viewModel.onStartTimer()
        runCurrent()
    }

    /** `StateFlow.collect()` with no argument does not compile; the member shadows the extension. */
    private fun TestScope.observe(state: StateFlow<TimersUiState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }
}
