package com.rayluxeo.aida.ui.alarms

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.model.Alarm
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.usecase.DeleteAlarmUseCase
import com.rayluxeo.aida.domain.usecase.SetAlarmUseCase
import com.rayluxeo.aida.domain.usecase.ToggleAlarmUseCase
import com.rayluxeo.aida.domain.usecase.UpdateAlarmUseCase
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import com.rayluxeo.aida.ui.response.WEEKDAY_SET
import com.rayluxeo.aida.ui.tasks.FakeSettingsRepository
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The alarm clock, assembled by hand — Hilt's local-test transform does not run under AGP 9.
 *
 * The line under an alarm is what these tests are mostly about. A row that names a ring time it
 * will not keep is the one mistake on this screen Abu has no way to notice until the morning it
 * matters, so a switched-off alarm, a snoozed one and an armed one each have to say what is
 * actually true of them.
 */
class AlarmsViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 9, 0))
    private val alarms = FakeAlarmRepository()
    private val scheduler = FakeAlarmClockScheduler()
    private val exactAlarms = FakeExactAlarmAccess()
    private val settings = FakeSettingsRepository()
    private val renderer = MiraResponseRenderer(clock)
    private val times = TaskTimeFormatter(clock)

    private val yesterday = LocalDateTime.of(2026, 3, 4, 9, 0).atZone(FakeMiraClock.DHAKA).toInstant()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a switched-off alarm says it is off rather than naming a moment`() = runTest {
        alarms.seed(alarm(id = 1L, label = "Fajr", at = LocalTime.of(5, 0), isEnabled = false))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val row = viewModel.uiState.value.alarms.single()
        assertThat(row.isEnabled).isFalse()
        assertThat(row.supporting).isEqualTo("Off")
        assertThat(row.timeLabel).isEqualTo("5 am")
        assertThat(row.repeatLabel).isEqualTo("Once")
    }

    @Test
    fun `switching an alarm on arms it and names the next ring`() = runTest {
        alarms.seed(alarm(id = 1L, label = "Standup", at = LocalTime.of(7, 0), isEnabled = false))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSetEnabled(1L, true)
        runCurrent()

        assertThat(scheduler.scheduled).containsExactly(1L)
        assertThat(viewModel.uiState.value.alarms.single().supporting).isEqualTo("Rings Tomorrow, 7 am")
    }

    @Test
    fun `switching one off clears the moment as well as the switch`() = runTest {
        alarms.seed(
            alarm(
                id = 1L,
                label = "Standup",
                at = LocalTime.of(7, 0),
                isEnabled = true,
                nextTriggerAt = LocalDateTime.of(2026, 3, 6, 7, 0).atZone(FakeMiraClock.DHAKA).toInstant(),
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSetEnabled(1L, false)
        runCurrent()

        assertThat(alarms.snapshot().single().nextTriggerAt).isNull()
        assertThat(viewModel.uiState.value.alarms.single().supporting).isEqualTo("Off")
    }

    @Test
    fun `a snoozed alarm says where it was pushed to, not where it started`() = runTest {
        alarms.seed(
            alarm(
                id = 1L,
                label = "Fajr",
                at = LocalTime.of(5, 0),
                isEnabled = true,
                nextTriggerAt = LocalDateTime.of(2026, 3, 6, 5, 0).atZone(FakeMiraClock.DHAKA).toInstant(),
                snoozedUntil = LocalDateTime.of(2026, 3, 5, 9, 10).atZone(FakeMiraClock.DHAKA).toInstant(),
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.alarms.single().supporting)
            .isEqualTo("Snoozed until Today, 9:10 am")
    }

    @Test
    fun `a repeating alarm reads its days back as a shape rather than a list`() = runTest {
        alarms.seed(
            alarm(id = 1L, label = "Standup", at = LocalTime.of(9, 30), repeatDays = WEEKDAY_SET),
            alarm(id = 2L, label = "Fajr", at = LocalTime.of(5, 0), repeatDays = EVERY_DAY),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val rows = viewModel.uiState.value.alarms.associateBy { it.id }
        assertThat(rows.getValue(1L).repeatLabel).isEqualTo("Weekdays")
        assertThat(rows.getValue(2L).repeatLabel).isEqualTo("Every day")
    }

    @Test
    fun `saving a new alarm stores it, arms it and says so`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onAddAlarm()
        viewModel.onEditorLabelChange("Fajr")
        viewModel.onEditorTimeChange(LocalTime.of(5, 0))
        viewModel.onToggleRepeatDay(DayOfWeek.MONDAY)
        viewModel.onSaveAlarm()
        runCurrent()

        val saved = alarms.snapshot().single()
        assertThat(saved.label).isEqualTo("Fajr")
        assertThat(saved.at).isEqualTo(LocalTime.of(5, 0))
        assertThat(saved.repeatDays).containsExactly(DayOfWeek.MONDAY)
        assertThat(scheduler.scheduled).containsExactly(saved.id)
        assertThat(viewModel.uiState.value.editor).isNull()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Alarm saved.")
    }

    /** Abu's seven o'clock is not "for" anything, so an unlabelled alarm is saved, not refused. */
    @Test
    fun `an alarm with no label is still saved`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onAddAlarm()
        viewModel.onEditorTimeChange(LocalTime.of(7, 0))
        viewModel.onSaveAlarm()
        runCurrent()

        // Stored blank, named on the way out. A word written into the row would be one language
        // frozen into Abu's data, and would still read "Alarm" after he switched to বাংলা.
        assertThat(alarms.snapshot().single().label).isEmpty()
        assertThat(viewModel.uiState.value.alarms.single().label).isEqualTo("Alarm")
    }

    @Test
    fun `the editor opens on an existing alarm and rewrites it in place`() = runTest {
        alarms.seed(alarm(id = 1L, label = "Standup", at = LocalTime.of(9, 30), repeatDays = WEEKDAY_SET))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onEditAlarm(1L)
        runCurrent()

        val editor = viewModel.uiState.value.editor
        assertThat(editor?.isNew).isFalse()
        assertThat(editor?.label).isEqualTo("Standup")
        assertThat(editor?.time).isEqualTo(LocalTime.of(9, 30))
        assertThat(editor?.repeatDays).isEqualTo(WEEKDAY_SET)
        assertThat(editor?.timeLabel).isEqualTo("9:30 am")

        viewModel.onSelectRepeatPreset(emptySet())
        viewModel.onEditorTimeChange(LocalTime.of(10, 0))
        viewModel.onSaveAlarm()
        runCurrent()

        val saved = alarms.snapshot().single()
        assertThat(saved.id).isEqualTo(1L)
        assertThat(saved.at).isEqualTo(LocalTime.of(10, 0))
        assertThat(saved.repeatDays).isEmpty()
    }

    @Test
    fun `deleting takes the row off the screen and the alarm off the OS`() = runTest {
        alarms.seed(alarm(id = 1L, label = "Standup", at = LocalTime.of(9, 30)))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onEditAlarm(1L)
        runCurrent()
        viewModel.onDeleteAlarm()
        runCurrent()

        assertThat(alarms.snapshot()).isEmpty()
        assertThat(scheduler.cancelled).containsExactly(1L)
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Alarm deleted.")
    }

    /**
     * The permission is withdrawn while the screen is open, which is exactly how Android behaves:
     * it stops the app and cancels every future exact alarm without saying anything.
     */
    @Test
    fun `withdrawing exact alarms is reported while the screen is open`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.canScheduleExact).isTrue()

        exactAlarms.set(false)
        runCurrent()

        assertThat(viewModel.uiState.value.canScheduleExact).isFalse()
    }

    /** An alarm the OS refuses keeps no moment on its row: nothing may promise a ring it lost. */
    @Test
    fun `an alarm the OS refuses is reported and left with nothing armed`() = runTest {
        scheduler.refuseWith = MiraErrorCode.PERMISSION_REQUIRED
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onAddAlarm()
        viewModel.onEditorLabelChange("Fajr")
        viewModel.onEditorTimeChange(LocalTime.of(5, 0))
        viewModel.onSaveAlarm()
        runCurrent()

        assertThat(alarms.snapshot().single().nextTriggerAt).isNull()
        assertThat(viewModel.uiState.value.alarms.single().supporting)
            .isEqualTo("On, but Android is not holding it")
        assertThat(viewModel.uiState.value.notice?.tone).isEqualTo(AlarmsNoticeTone.PROBLEM)
        assertThat(viewModel.uiState.value.editor?.isSaving).isFalse()
    }

    @Test
    fun `the rows are worded in Bengali when Abu has chosen it`() = runTest {
        alarms.seed(alarm(id = 1L, label = "Fajr", at = LocalTime.of(5, 0), isEnabled = false))
        val viewModel = createViewModel(
            FakeSettingsRepository(MiraSettings(languageMode = LanguageMode.BANGLA)),
        )
        observe(viewModel.uiState)

        val row = viewModel.uiState.value.alarms.single()
        assertThat(row.supporting).isEqualTo("বন্ধ")
        assertThat(row.timeLabel).isEqualTo("সকাল ৫টা")
        assertThat(row.repeatLabel).isEqualTo("একবার")
    }

    private fun alarm(
        id: Long,
        label: String,
        at: LocalTime,
        repeatDays: Set<DayOfWeek> = emptySet(),
        isEnabled: Boolean = true,
        nextTriggerAt: Instant? = null,
        snoozedUntil: Instant? = null,
    ) = Alarm(
        id = id,
        label = label,
        at = at,
        repeatDays = repeatDays,
        isEnabled = isEnabled,
        nextTriggerAt = nextTriggerAt,
        snoozedUntil = snoozedUntil,
        createdAt = yesterday,
        updatedAt = yesterday,
    )

    private fun createViewModel(
        settings: SettingsRepository = this.settings,
    ) = AlarmsViewModel(
        alarms = alarms,
        setAlarm = SetAlarmUseCase(alarms, scheduler, clock),
        updateAlarm = UpdateAlarmUseCase(alarms, scheduler, clock),
        toggleAlarm = ToggleAlarmUseCase(alarms, scheduler, clock),
        deleteAlarm = DeleteAlarmUseCase(alarms, scheduler),
        exactAlarms = exactAlarms,
        settings = settings,
        clock = clock,
        times = times,
        renderer = renderer,
    )

    /** `StateFlow.collect()` with no argument does not compile; the member shadows the extension. */
    private fun TestScope.observe(state: StateFlow<AlarmsUiState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }
}
