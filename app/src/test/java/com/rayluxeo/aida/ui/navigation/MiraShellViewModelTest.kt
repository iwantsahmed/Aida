package com.rayluxeo.aida.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.mira.MiraEntryCoordinator
import com.rayluxeo.aida.ui.mira.MiraEntryIntent
import com.rayluxeo.aida.ui.vault.AppLockController
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * The onboarding gate, built from a hand-written fake: Hilt's test transform does not run under
 * AGP 9, so the view model is constructed here the way Dagger would construct it.
 */
class MiraShellViewModelTest {

    private val settings = DeferredSettingsRepository()
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 10, 15, 10, 0))
    private val appLock = AppLockController(clock)

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // App-lock defaults to shut for a fresh process; the tests about onboarding, haptics and
        // language run with it already open, so the lock fields sit at their "off" defaults. The
        // lock's own behaviour is covered by the dedicated tests at the foot of this file.
        appLock.unlock()
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the shell decides nothing until the settings store has answered`() = runTest {
        val viewModel = MiraShellViewModel(settings, MiraEntryCoordinator(), appLock)
        observe(viewModel.state)

        assertThat(viewModel.state.value).isEqualTo(MiraShellState.Loading)
    }

    @Test
    fun `a clean install resolves to onboarding still to do`() = runTest {
        val viewModel = MiraShellViewModel(settings, MiraEntryCoordinator(), appLock)
        observe(viewModel.state)

        settings.answer(MiraSettings(onboardingComplete = false))
        runCurrent()

        assertThat(viewModel.state.value)
            .isEqualTo(
                MiraShellState.Ready(
                    onboardingComplete = false,
                    hapticsEnabled = true,
                    language = SpokenLanguage.ENGLISH,
                ),
            )
    }

    @Test
    fun `a returning run resolves to onboarding done`() = runTest {
        val viewModel = MiraShellViewModel(settings, MiraEntryCoordinator(), appLock)
        observe(viewModel.state)

        settings.answer(MiraSettings(onboardingComplete = true))
        runCurrent()

        assertThat(viewModel.state.value)
            .isEqualTo(
                MiraShellState.Ready(
                    onboardingComplete = true,
                    hapticsEnabled = true,
                    language = SpokenLanguage.ENGLISH,
                ),
            )
    }

    @Test
    fun `turning haptics off reaches the shell without waiting for a restart`() = runTest {
        val viewModel = MiraShellViewModel(settings, MiraEntryCoordinator(), appLock)
        observe(viewModel.state)

        settings.answer(MiraSettings(onboardingComplete = true, hapticFeedback = true))
        runCurrent()
        settings.answer(MiraSettings(onboardingComplete = true, hapticFeedback = false))
        runCurrent()

        assertThat(viewModel.state.value)
            .isEqualTo(
                MiraShellState.Ready(
                    onboardingComplete = true,
                    hapticsEnabled = false,
                    language = SpokenLanguage.ENGLISH,
                ),
            )
    }

    /**
     * The tab strip, the hub and a planned area are worded from the shell's own state, so a
     * language switch has to reach it the same way the haptics switch does. AUTO settles on
     * English, exactly as the text normalizer does for an utterance with no Bengali signal.
     */
    @Test
    fun `the chosen language reaches the shell, and AUTO settles on English`() = runTest {
        val viewModel = MiraShellViewModel(settings, MiraEntryCoordinator(), appLock)
        observe(viewModel.state)

        settings.answer(MiraSettings(onboardingComplete = true, languageMode = LanguageMode.BANGLA))
        runCurrent()
        assertThat((viewModel.state.value as MiraShellState.Ready).language)
            .isEqualTo(SpokenLanguage.BENGALI)

        settings.answer(MiraSettings(onboardingComplete = true, languageMode = LanguageMode.AUTO))
        runCurrent()
        assertThat((viewModel.state.value as MiraShellState.Ready).language)
            .isEqualTo(SpokenLanguage.ENGLISH)
    }

    /**
     * "Ask Mira about this document" from Files has to survive the tab switch. The shell records
     * it on the shared coordinator, where the Mira screen reads it on arrival — see
     * [MiraEntryCoordinator].
     */
    @Test
    fun `askMira records the question as a pending Ask intent`() = runTest {
        val entry = MiraEntryCoordinator()
        val viewModel = MiraShellViewModel(settings, entry, appLock)

        viewModel.askMira("Analyze this document: Invoice.pdf")

        assertThat(entry.pending.value)
            .isEqualTo(MiraEntryIntent.Ask("Analyze this document: Invoice.pdf"))
    }

    /** The Talk shortcut and the voice tile ask the tab to come up listening, not just to open. */
    @Test
    fun `startListening records a StartListening intent`() = runTest {
        val entry = MiraEntryCoordinator()
        val viewModel = MiraShellViewModel(settings, entry, appLock)

        viewModel.startListening()

        assertThat(entry.pending.value).isEqualTo(MiraEntryIntent.StartListening)
    }

    /**
     * The whole setting is one field, so the gate at the shell reads [MiraShellState.Ready.appLockEnabled]
     * and [MiraShellState.Ready.lockWhenBackgrounded] straight from the store, and the live locked
     * flag from the controller. A fresh controller is shut, which is what gates a cold start.
     */
    @Test
    fun `an enabled lock reaches the shell shut`() = runTest {
        appLock.lock()
        val viewModel = MiraShellViewModel(settings, MiraEntryCoordinator(), appLock)
        observe(viewModel.state)

        settings.answer(
            MiraSettings(onboardingComplete = true, appLockEnabled = true, lockWhenBackgrounded = false),
        )
        runCurrent()

        val ready = viewModel.state.value as MiraShellState.Ready
        assertThat(ready.appLockEnabled).isTrue()
        assertThat(ready.lockWhenBackgrounded).isFalse()
        assertThat(ready.locked).isTrue()
    }

    /** Leaving with "lock when I leave" on shuts it at once; the prompt clears it. */
    @Test
    fun `backgrounding with lock-when-I-leave on, and unlock, move the flag the shell reads`() = runTest {
        val viewModel = MiraShellViewModel(settings, MiraEntryCoordinator(), appLock)
        observe(viewModel.state)
        settings.answer(
            MiraSettings(onboardingComplete = true, appLockEnabled = true, lockWhenBackgrounded = true),
        )
        runCurrent()

        viewModel.unlockApp()
        runCurrent()
        assertThat((viewModel.state.value as MiraShellState.Ready).locked).isFalse()

        viewModel.onAppBackgrounded()
        runCurrent()
        assertThat((viewModel.state.value as MiraShellState.Ready).locked).isTrue()
    }

    /** With "lock when I leave" off, the Auto-lock delay decides: a short trip stays open, a long one re-locks. */
    @Test
    fun `the auto-lock delay re-locks only after enough time away`() = runTest {
        val viewModel = MiraShellViewModel(settings, MiraEntryCoordinator(), appLock)
        observe(viewModel.state)
        settings.answer(
            MiraSettings(
                onboardingComplete = true,
                appLockEnabled = true,
                lockWhenBackgrounded = false,
                appLockTimeoutSeconds = 60,
            ),
        )
        runCurrent()
        viewModel.unlockApp()
        runCurrent()

        // A short trip: leaving records the moment but does not lock, and a quick return stays open.
        viewModel.onAppBackgrounded()
        runCurrent()
        assertThat((viewModel.state.value as MiraShellState.Ready).locked).isFalse()
        clock.moveTo(LocalDateTime.of(2026, 10, 15, 10, 0, 30))
        viewModel.onAppForegrounded()
        runCurrent()
        assertThat((viewModel.state.value as MiraShellState.Ready).locked).isFalse()

        // A long trip: away past the delay re-locks on return.
        viewModel.onAppBackgrounded()
        runCurrent()
        clock.moveTo(LocalDateTime.of(2026, 10, 15, 10, 2, 0))
        viewModel.onAppForegrounded()
        runCurrent()
        assertThat((viewModel.state.value as MiraShellState.Ready).locked).isTrue()
    }

    /** Keeps the `WhileSubscribed` state flow hot for the length of the test. */
    private fun TestScope.observe(state: StateFlow<MiraShellState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }
}

/**
 * A settings store that stays silent until a test tells it to speak, because the whole point of
 * the gate is what the shell does in the milliseconds before DataStore replies.
 */
private class DeferredSettingsRepository : SettingsRepository {

    private val emissions = MutableSharedFlow<MiraSettings>(replay = 1)

    override val settings: Flow<MiraSettings> = emissions

    suspend fun answer(stored: MiraSettings) = emissions.emit(stored)

    override suspend fun current(): MiraSettings =
        error("MiraShellViewModel must observe the settings flow, not read a one-shot snapshot")

    override suspend fun setLanguageMode(mode: LanguageMode) = neverWritten()

    override suspend fun setActivationSound(enabled: Boolean) = neverWritten()

    override suspend fun setHapticFeedback(enabled: Boolean) = neverWritten()

    override suspend fun setOnboardingComplete(complete: Boolean) = neverWritten()

    private fun neverWritten(): Nothing = error("MiraShellViewModel never writes settings")
}
