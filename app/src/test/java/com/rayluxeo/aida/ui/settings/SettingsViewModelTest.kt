package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.ui.mira.FakeVoicePreferenceRepository
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.repository.CloudAiStatusProvider
import com.rayluxeo.aida.domain.repository.CloudQuotaStatus
import com.rayluxeo.aida.domain.repository.LocalDataEraser
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import com.rayluxeo.aida.domain.usecase.EraseAllDataUseCase
import kotlinx.coroutines.flow.flowOf
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.brief.BriefKind
import com.rayluxeo.aida.domain.brief.BriefSchedule
import com.rayluxeo.aida.domain.brief.BriefScheduleRepository
import com.rayluxeo.aida.domain.brief.BriefScheduler
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.domain.ota.AppUpdate
import com.rayluxeo.aida.domain.ota.UpdateRepository
import com.rayluxeo.aida.domain.usecase.CheckAppUpdateUseCase
import com.rayluxeo.aida.domain.usecase.DownloadAppUpdateUseCase
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Settings has no Save button, so every test here checks the same thing from a different angle:
 * that the control Abu touched reached storage rather than only the screen.
 */
class SettingsViewModelTest {

    private val settings = FakeSettingsRepository()
    private val voicePrefs = FakeVoicePreferenceRepository()
    private val credentials = FakeGeminiCredentialStore()
    private val conversation = RecordingConversationRepository()
    private val scheduler = FakeReminderScheduler()
    private val briefs = FakeBriefScheduleRepository()
    private val briefScheduler = RecordingBriefScheduler()
    private val fakeUpdateRepo = FakeUpdateRepository()
    private val checkAppUpdate = CheckAppUpdateUseCase(fakeUpdateRepo)
    private val downloadAppUpdate = DownloadAppUpdateUseCase(fakeUpdateRepo)

    /** Tuesday 10 March 2026, 10:00 in Dhaka: past the default morning, short of the evening. */
    private val clock = FixedMiraClock.at(hour = 10)

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `every switch and the language choice are written straight through`() = runTest {
        val viewModel = viewModel()

        viewModel.setLanguageMode(LanguageMode.BANGLA)
        viewModel.setSpokenResponses(false)
        viewModel.setActivationSound(false)
        viewModel.setHapticFeedback(false)
        advanceUntilIdle()

        assertThat(settings.stored).isEqualTo(
            MiraSettings(
                languageMode = LanguageMode.BANGLA,
                activationSound = false,
                hapticFeedback = false,
            )
        )
        assertThat(viewModel.state.value.settings.languageMode).isEqualTo(LanguageMode.BANGLA)
        // Spoken responses moved to its own store; the screen still writes it from the same switch.
        assertThat(voicePrefs.current().spokenResponsesEnabled).isFalse()
    }

    @Test
    fun `a saved Gemini key is sealed away and never handed back to the screen`() = runTest {
        val viewModel = viewModel()

        viewModel.openGeminiEditor()
        viewModel.onGeminiKeyDraftChange("  a-real-looking-key  ")
        viewModel.saveGeminiKey()
        advanceUntilIdle()

        assertThat(credentials.heldKey).isEqualTo("a-real-looking-key")
        assertThat(viewModel.state.value.hasGeminiKey).isTrue()
        assertThat(viewModel.state.value.geminiKeyDraft).isEmpty()
        assertThat(viewModel.state.value.geminiEditorOpen).isFalse()
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `a key the store cannot seal is reported instead of being assumed saved`() = runTest {
        credentials.sealingFails = true
        val viewModel = viewModel()

        viewModel.openGeminiEditor()
        viewModel.onGeminiKeyDraftChange("a-real-looking-key")
        viewModel.saveGeminiKey()
        advanceUntilIdle()

        assertThat(credentials.heldKey).isNull()
        assertThat(viewModel.state.value.hasGeminiKey).isFalse()
        assertThat(viewModel.state.value.error).isNotEmpty()
        assertThat(viewModel.state.value.geminiEditorOpen).isTrue()
    }

    @Test
    fun `removing the key puts Mira back on device`() = runTest {
        val viewModel = viewModel()
        viewModel.openGeminiEditor()
        viewModel.onGeminiKeyDraftChange("a-real-looking-key")
        viewModel.saveGeminiKey()
        advanceUntilIdle()

        viewModel.removeGeminiKey()
        advanceUntilIdle()

        assertThat(credentials.heldKey).isNull()
        assertThat(viewModel.state.value.hasGeminiKey).isFalse()
    }

    @Test
    fun `clearing the conversation asks first and reports a storage refusal`() = runTest {
        conversation.failClear = true
        val viewModel = viewModel()

        viewModel.askClearHistory()
        assertThat(viewModel.state.value.confirmingClearHistory).isTrue()
        assertThat(conversation.clears).isEqualTo(0)

        viewModel.confirmClearHistory()
        advanceUntilIdle()

        assertThat(conversation.clears).isEqualTo(1)
        assertThat(viewModel.state.value.confirmingClearHistory).isFalse()
        assertThat(viewModel.state.value.error).isNotEmpty()
    }

    @Test
    fun `a refusal is worded in the language Abu chose, not in the screen's own`() = runTest {
        conversation.failClear = true
        val viewModel = viewModel()
        viewModel.setLanguageMode(LanguageMode.BANGLA)
        advanceUntilIdle()

        viewModel.askClearHistory()
        viewModel.confirmClearHistory()
        advanceUntilIdle()

        assertThat(viewModel.state.value.error)
            .isEqualTo(BengaliSettingsWording.conversationNotCleared)
    }

    @Test
    fun `a revoked exact-alarm permission is noticed when the screen comes back`() = runTest {
        val viewModel = viewModel()
        assertThat(viewModel.state.value.canScheduleExact).isTrue()

        scheduler.exactAllowed = false
        viewModel.refreshExactAlarmPermission()

        assertThat(viewModel.state.value.canScheduleExact).isFalse()
    }

    @Test
    fun `neither brief is armed until Abu turns one on`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.briefSchedule).isEqualTo(BriefSchedule.DEFAULT)
        assertThat(viewModel.state.value.morningNextLabel).isNull()
        assertThat(viewModel.state.value.eveningNextLabel).isNull()
        assertThat(briefScheduler.applied).isEmpty()
    }

    @Test
    fun `switching a brief on stores it and arms the alarm in the same breath`() = runTest {
        val viewModel = viewModel()

        viewModel.setBriefEnabled(BriefKind.MORNING, true)
        advanceUntilIdle()

        assertThat(briefs.stored.morningEnabled).isTrue()
        assertThat(briefScheduler.applied.last()).isEqualTo(briefs.stored)
        // 08:00 has already gone by on the fixed clock, so the next one is tomorrow's.
        assertThat(viewModel.state.value.morningNextLabel).isEqualTo("Next — Tomorrow, 8 am")
    }

    @Test
    fun `an evening review still to come today says so`() = runTest {
        val viewModel = viewModel()

        viewModel.setBriefEnabled(BriefKind.EVENING, true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.eveningNextLabel).isEqualTo("Next — Today, 9 pm")
        assertThat(viewModel.state.value.eveningTimeLabel).isEqualTo("9 pm")
    }

    @Test
    fun `switching a brief off re-arms with it cancelled rather than leaving the alarm set`() =
        runTest {
            val viewModel = viewModel()
            viewModel.setBriefEnabled(BriefKind.MORNING, true)
            advanceUntilIdle()

            viewModel.setBriefEnabled(BriefKind.MORNING, false)
            advanceUntilIdle()

            assertThat(briefs.stored.morningEnabled).isFalse()
            assertThat(briefScheduler.applied.last().morningEnabled).isFalse()
            assertThat(viewModel.state.value.morningNextLabel).isNull()
        }

    @Test
    fun `a time Abu changes is re-armed now, not at the next reboot`() = runTest {
        val viewModel = viewModel()
        viewModel.setBriefEnabled(BriefKind.EVENING, true)
        advanceUntilIdle()

        viewModel.editBriefTime(BriefKind.EVENING)
        assertThat(viewModel.state.value.editingBriefTime).isEqualTo(BriefKind.EVENING)

        viewModel.setBriefTime(BriefKind.EVENING, LocalTime.of(22, 30))
        advanceUntilIdle()

        assertThat(briefs.stored.eveningAt).isEqualTo(LocalTime.of(22, 30))
        assertThat(briefScheduler.applied.last().eveningAt).isEqualTo(LocalTime.of(22, 30))
        assertThat(viewModel.state.value.editingBriefTime).isNull()
        assertThat(viewModel.state.value.eveningNextLabel).isEqualTo("Next — Today, 10:30 pm")
    }

    @Test
    fun `the next occurrence is worded in Bengali when that is the language Abu chose`() = runTest {
        settings.setLanguageMode(LanguageMode.BANGLA)
        val viewModel = viewModel()

        viewModel.setBriefEnabled(BriefKind.MORNING, true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.morningNextLabel)
            .isEqualTo("পরবর্তী — আগামীকাল, সকাল ৮টা")
        assertThat(viewModel.state.value.morningTimeLabel).isEqualTo("সকাল ৮টা")
    }

    @Test
    fun `an alarm the phone refuses is reported rather than left looking armed`() = runTest {
        briefScheduler.result = failure(MiraErrorCode.STORAGE, "alarm refused")
        val viewModel = viewModel()

        viewModel.setBriefEnabled(BriefKind.MORNING, true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isNotEmpty()
        // The setting is what Abu asked for and stays; only the alarm failed.
        assertThat(briefs.stored.morningEnabled).isTrue()
    }

    @Test
    fun `checkForUpdates discovers available github release and updates state`() = runTest {
        val viewModel = viewModel()
        viewModel.checkForUpdates()
        advanceUntilIdle()

        val ota = viewModel.state.value.otaState
        assertThat(ota).isInstanceOf(OtaUpdateState.Available::class.java)
        val available = ota as OtaUpdateState.Available
        assertThat(available.update.latestVersionName).isEqualTo("1.2")
        assertThat(available.update.hasUpdate).isTrue()
    }

    private fun viewModel() = SettingsViewModel(
        settings,
        voicePrefs,
        credentials,
        conversation,
        scheduler,
        briefs,
        briefScheduler,
        FakeUserProfileRepository(),
        FakeCloudAiStatusProvider(),
        EraseAllDataUseCase(FakeLocalDataEraser()),
        checkAppUpdate,
        downloadAppUpdate,
        clock,
        TaskTimeFormatter(clock),
    )
}

private class FakeUserProfileRepository : UserProfileRepository {
    override fun observe() = flowOf<UserProfile?>(null)
    override suspend fun current() = AppResult.Success<UserProfile?>(null)
    override suspend fun save(profile: UserProfile) = AppResult.Success(profile)
}

private class FakeCloudAiStatusProvider : CloudAiStatusProvider {
    override val quota = flowOf(CloudQuotaStatus.AVAILABLE)
}

private class FakeLocalDataEraser : LocalDataEraser {
    override suspend fun eraseEverything() = AppResult.Success(Unit)
}

/** The brief schedule, in memory. [stored] is what the tests assert against. */
private class FakeBriefScheduleRepository : BriefScheduleRepository {

    private val _schedule = MutableStateFlow(BriefSchedule.DEFAULT)
    override val schedule: Flow<BriefSchedule> = _schedule.asStateFlow()

    val stored: BriefSchedule get() = _schedule.value

    override suspend fun current(): BriefSchedule = _schedule.value

    override suspend fun setMorningEnabled(enabled: Boolean) {
        _schedule.update { it.copy(morningEnabled = enabled) }
    }

    override suspend fun setMorningAt(time: LocalTime) {
        _schedule.update { it.copy(morningAt = time) }
    }

    override suspend fun setEveningEnabled(enabled: Boolean) {
        _schedule.update { it.copy(eveningEnabled = enabled) }
    }

    override suspend fun setEveningAt(time: LocalTime) {
        _schedule.update { it.copy(eveningAt = time) }
    }
}

/**
 * Records every schedule it was handed, so a test can tell a setting that was written from one
 * that was actually armed — the whole difference between a working brief and a silent one.
 */
private class RecordingBriefScheduler : BriefScheduler {

    val applied = mutableListOf<BriefSchedule>()
    var result: AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun apply(schedule: BriefSchedule): AppResult<Unit> {
        applied += schedule
        return result
    }

    override suspend fun cancel(kind: BriefKind): AppResult<Unit> = result

    override suspend fun rescheduleAll(): AppResult<Unit> = result
}

private class FakeUpdateRepository : UpdateRepository {
    var updateResult: AppResult<AppUpdate> = AppResult.Success(
        AppUpdate(
            currentVersionName = "1.1",
            currentVersionCode = 2,
            latestVersionName = "1.2",
            latestVersionCode = 3,
            releaseTitle = "Aida v1.2",
            releaseNotes = "New features",
            apkDownloadUrl = "https://example.com/aida.apk",
            apkFileName = "Aida-v1.2.apk",
            apkSizeBytes = 1024L,
            hasUpdate = true,
        ),
    )

    override suspend fun checkForUpdate(
        currentVersionCode: Long,
        currentVersionName: String,
        repositoryOwner: String,
        repositoryName: String,
    ): AppResult<AppUpdate> = updateResult

    override suspend fun downloadUpdate(
        downloadUrl: String,
        targetFileName: String,
        onProgress: (Float) -> Unit,
    ): AppResult<File> = AppResult.Success(File.createTempFile("fake_update", ".apk"))
}
