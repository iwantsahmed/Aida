package com.rayluxeo.aida.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Onboarding is the one flow Abu sees exactly once, so what is tested here is what would strand
 * him: losing his place, a skipped permission blocking the way forward, or the last step letting
 * him out without the flag reaching storage.
 */
class OnboardingViewModelTest {

    private val settings = FakeSettingsRepository()
    private val userProfile = FakeUserProfileRepository()
    private val clock = FixedMiraClock()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the flow moves one step at a time in both directions`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.step).isEqualTo(OnboardingStep.WELCOME)
        assertThat(viewModel.state.value.canGoBack).isFalse()

        viewModel.next()
        advanceUntilIdle()
        assertThat(viewModel.state.value.step).isEqualTo(OnboardingStep.NAME)
        assertThat(viewModel.state.value.canGoBack).isTrue()

        viewModel.next()
        advanceUntilIdle()
        assertThat(viewModel.state.value.step).isEqualTo(OnboardingStep.LANGUAGE)

        viewModel.back()
        advanceUntilIdle()
        assertThat(viewModel.state.value.step).isEqualTo(OnboardingStep.NAME)
    }

    @Test
    fun `the first step has nowhere back to and the last has nowhere forward`() = runTest {
        val viewModel = viewModel()

        viewModel.back()
        advanceUntilIdle()
        assertThat(viewModel.state.value.step).isEqualTo(OnboardingStep.WELCOME)

        repeat(OnboardingStep.count * 2) { viewModel.next() }
        advanceUntilIdle()
        assertThat(viewModel.state.value.step).isEqualTo(OnboardingStep.FINISH)
    }

    /** A process death mid-setup must not throw Abu back to the welcome. */
    @Test
    fun `a new view model over the same saved state resumes on the same step`() = runTest {
        val savedState = SavedStateHandle()
        val first = viewModel(savedState)
        repeat(OnboardingStep.MICROPHONE.ordinal) { first.next() }
        advanceUntilIdle()

        val restored = viewModel(savedState)
        advanceUntilIdle()

        assertThat(restored.state.value.step).isEqualTo(OnboardingStep.MICROPHONE)
    }

    @Test
    fun `the language choice is written the moment it is made`() = runTest {
        val viewModel = viewModel()

        viewModel.setLanguageMode(LanguageMode.BANGLA)
        advanceUntilIdle()

        assertThat(settings.stored.languageMode).isEqualTo(LanguageMode.BANGLA)
        assertThat(viewModel.state.value.languageMode).isEqualTo(LanguageMode.BANGLA)
    }

    @Test
    fun `skipping the microphone advances without claiming a permission Mira does not have`() = runTest {
        val viewModel = viewModel()
        repeat(OnboardingStep.MICROPHONE.ordinal) { viewModel.next() }
        advanceUntilIdle()

        viewModel.next()
        advanceUntilIdle()

        assertThat(viewModel.state.value.step).isEqualTo(OnboardingStep.NOTIFICATIONS)
        assertThat(viewModel.state.value.microphone).isEqualTo(PermissionState.NOT_GRANTED)
    }

    @Test
    fun `a refusal Android will not repeat becomes blocked, and a later look leaves it alone`() = runTest {
        val viewModel = viewModel()

        viewModel.onMicrophoneResult(granted = false, canAskAgain = false)
        advanceUntilIdle()
        assertThat(viewModel.state.value.microphone).isEqualTo(PermissionState.BLOCKED)

        viewModel.onMicrophoneChecked(granted = false)
        advanceUntilIdle()
        assertThat(viewModel.state.value.microphone).isEqualTo(PermissionState.BLOCKED)

        viewModel.onMicrophoneChecked(granted = true)
        advanceUntilIdle()
        assertThat(viewModel.state.value.microphone).isEqualTo(PermissionState.GRANTED)
    }

    @Test
    fun `a refusal Android will repeat stays worth asking about`() = runTest {
        val viewModel = viewModel()

        viewModel.onMicrophoneResult(granted = false, canAskAgain = true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.microphone).isEqualTo(PermissionState.DENIED)
    }

    /** Below API 33 there is no notification dialog, so "off" already means settings-only. */
    @Test
    fun `notifications that cannot be requested are reported as settings-only`() = runTest {
        val viewModel = viewModel()

        viewModel.onNotificationsChecked(granted = false, requestable = false)
        advanceUntilIdle()
        assertThat(viewModel.state.value.notifications).isEqualTo(PermissionState.BLOCKED)

        viewModel.onNotificationsChecked(granted = true, requestable = false)
        advanceUntilIdle()
        assertThat(viewModel.state.value.notifications).isEqualTo(PermissionState.GRANTED)
    }

    @Test
    fun `finishing stores the flag before the screen is told to leave`() = runTest {
        val viewModel = viewModel()
        repeat(OnboardingStep.FINISH.ordinal) { viewModel.next() }
        advanceUntilIdle()

        viewModel.finish()
        advanceUntilIdle()

        assertThat(settings.stored.onboardingComplete).isTrue()
        assertThat(viewModel.state.value.finished).isTrue()
        assertThat(viewModel.state.value.finishing).isFalse()
    }

    @Test
    fun `finishing twice writes once`() = runTest {
        val viewModel = viewModel()

        viewModel.finish()
        viewModel.finish()
        advanceUntilIdle()
        viewModel.finish()
        advanceUntilIdle()

        assertThat(settings.completionWrites).isEqualTo(1)
    }

    @Test
    fun `saving user name writes to settings and user profile`() = runTest {
        val viewModel = viewModel()
        viewModel.onUserNameChange("Abu")
        viewModel.saveUserName("Abu")
        advanceUntilIdle()

        assertThat(settings.stored.userName).isEqualTo("Abu")
        assertThat(userProfile.current().getOrNull()?.displayName).isEqualTo("Abu")
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        OnboardingViewModel(settings = settings, userProfile = userProfile, clock = clock, savedState = savedState)
}
