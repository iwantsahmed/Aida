package com.rayluxeo.aida.ui.settings

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.assistant.AssistantEntryPoint
import com.rayluxeo.aida.assistant.AssistantRoleController
import com.rayluxeo.aida.assistant.AssistantRoleState
import com.rayluxeo.aida.assistant.AssistantStatus
import com.rayluxeo.aida.assistant.HotwordCapabilityDetector
import com.rayluxeo.aida.assistant.HotwordSupport
import com.rayluxeo.aida.assistant.HotwordVerdict
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.MiraSettings
import kotlinx.coroutines.CompletableDeferred
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
 * The assistant screen's one job is to never claim something it has not verified, so these tests
 * pin the verdicts rather than the wording: which checks ran, which were skipped, which could not
 * be answered at all, and whether a session was actually attempted.
 *
 * Two things here need a device and are deliberately not faked into looking covered. Whether
 * `RoleManager` and `VoiceInteractionService.isActiveService` report the truth belongs to the
 * controller and to an instrumented test; and `settingsIntent()` returns null throughout, because
 * constructing an `Intent` in a local unit test is not possible — the branch where a settings
 * screen does exist is covered against [assistantChecks] directly, which takes that fact as a
 * parameter.
 */
class AssistantSetupViewModelTest {

    private val roles = FakeAssistantRoles()
    private val hotwords = FakeHotwordDetector()

    /** Only the language is read from here, and only to choose which phrasebook the screen uses. */
    private val settings = FakeSettingsRepository()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a refresh reports what Android said and stops loading`() = runTest {
        roles.status = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA)
        hotwords.support = HotwordSupport(
            verdict = HotwordVerdict.SYSTEM_ONLY,
            alternatives = listOf(AssistantEntryPoint.SYSTEM_ASSIST_GESTURE),
        )
        val viewModel = AssistantSetupViewModel(roles, hotwords, settings)

        viewModel.refresh(ready())
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.loading).isFalse()
        assertThat(state.status?.isMira).isTrue()
        assertThat(state.hotword?.alternatives)
            .containsExactly(AssistantEntryPoint.SYSTEM_ASSIST_GESTURE)
        assertThat(state.settingsAvailable).isFalse()
        assertThat(state.canRunTest).isTrue()
    }

    @Test
    fun `everything in place passes all four checks and opens a session`() = runTest {
        roles.status = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA)
        val viewModel = AssistantSetupViewModel(roles, hotwords, settings)
        var sessionAttempts = 0

        viewModel.refresh(ready())
        advanceUntilIdle()
        viewModel.runTest(ready()) {
            sessionAttempts++
            true
        }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(sessionAttempts).isEqualTo(1)
        assertThat(state.testing).isFalse()
        assertThat(state.allChecksPassed).isTrue()
        assertThat(state.checks.map { it.reason }).containsExactly(
            AssistantCheckReason.ROLE_HELD,
            AssistantCheckReason.SERVICE_ACTIVE,
            AssistantCheckReason.MICROPHONE_GRANTED,
            AssistantCheckReason.SESSION_OPENED,
        ).inOrder()
    }

    @Test
    fun `a role held elsewhere fails and no session is attempted`() = runTest {
        roles.status = AssistantStatus(
            state = AssistantRoleState.AVAILABLE,
            holder = "com.samsung.android.bixby.agent",
            holderLabel = "Bixby",
        )
        val viewModel = AssistantSetupViewModel(roles, hotwords, settings)
        var sessionAttempts = 0

        viewModel.refresh(ready())
        advanceUntilIdle()
        viewModel.runTest(ready().copy(serviceActive = false)) {
            sessionAttempts++
            true
        }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(sessionAttempts).isEqualTo(0)
        assertThat(state.allChecksPassed).isFalse()
        assertThat(state.checks.map { it.reason }).containsExactly(
            AssistantCheckReason.ROLE_HELD_BY_OTHER,
            AssistantCheckReason.SERVICE_NOT_BOUND,
            AssistantCheckReason.MICROPHONE_GRANTED,
            AssistantCheckReason.SESSION_NOT_TRIED,
        ).inOrder()
        assertThat(state.checks.last().outcome).isEqualTo(AssistantCheckOutcome.SKIPPED)
    }

    @Test
    fun `a build with no assist entry reports the session unchecked rather than failed`() = runTest {
        roles.status = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA)
        val viewModel = AssistantSetupViewModel(roles, hotwords, settings)

        viewModel.refresh(ready())
        advanceUntilIdle()
        viewModel.runTest(ready()) { false }
        advanceUntilIdle()

        val session = viewModel.state.value.checks.single { it.id == AssistantCheckId.SESSION }
        assertThat(session.outcome).isEqualTo(AssistantCheckOutcome.UNAVAILABLE)
        assertThat(session.reason).isEqualTo(AssistantCheckReason.SESSION_NO_ENTRY_POINT)
        assertThat(viewModel.state.value.failedChecks).isEmpty()
        // Unchecked is not a pass: nothing on this screen may tick a box it did not verify.
        assertThat(viewModel.state.value.allChecksPassed).isFalse()
    }

    @Test
    fun `a missing microphone fails with the grant offered, and still shows Abu a session`() =
        runTest {
            roles.status = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA)
            val viewModel = AssistantSetupViewModel(roles, hotwords, settings)
            var sessionAttempts = 0

            viewModel.refresh(ready())
            advanceUntilIdle()
            viewModel.runTest(ready().copy(microphoneGranted = false)) {
                sessionAttempts++
                true
            }
            advanceUntilIdle()

            val microphone = viewModel.state.value.checks
                .single { it.id == AssistantCheckId.MICROPHONE }
            assertThat(sessionAttempts).isEqualTo(1)
            assertThat(microphone.outcome).isEqualTo(AssistantCheckOutcome.FAILED)
            assertThat(microphone.fix).isEqualTo(AssistantFix.GRANT_MICROPHONE)
        }

    @Test
    fun `the role is read again when the test runs, not taken from the last look`() = runTest {
        roles.status = AssistantStatus(state = AssistantRoleState.AVAILABLE, holder = "other.app")
        val viewModel = AssistantSetupViewModel(roles, hotwords, settings)

        viewModel.refresh(ready())
        advanceUntilIdle()

        // Abu grants the role in Android's settings while the screen sits open.
        roles.status = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA)
        viewModel.runTest(ready()) { true }
        advanceUntilIdle()

        assertThat(roles.statusReads).isEqualTo(2)
        assertThat(viewModel.state.value.checks.first().reason)
            .isEqualTo(AssistantCheckReason.ROLE_HELD)
    }

    @Test
    fun `a result survives a resume that changed nothing`() = runTest {
        roles.status = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA)
        val viewModel = AssistantSetupViewModel(roles, hotwords, settings)

        viewModel.refresh(ready())
        advanceUntilIdle()
        viewModel.runTest(ready()) { true }
        advanceUntilIdle()
        viewModel.refresh(ready())
        advanceUntilIdle()

        assertThat(viewModel.state.value.checks).hasSize(4)
    }

    @Test
    fun `a result is dropped as soon as the phone underneath it changes`() = runTest {
        roles.status = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA)
        val viewModel = AssistantSetupViewModel(roles, hotwords, settings)

        viewModel.refresh(ready())
        advanceUntilIdle()
        viewModel.runTest(ready().copy(microphoneGranted = false)) { true }
        advanceUntilIdle()
        assertThat(viewModel.state.value.checks).isNotEmpty()

        // Abu granted the microphone from the dialog: the row that said he had not is now a lie.
        viewModel.refresh(ready())
        advanceUntilIdle()

        assertThat(viewModel.state.value.checks).isEmpty()
    }

    @Test
    fun `a second test is refused while one is still running`() = runTest {
        roles.status = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA)
        val viewModel = AssistantSetupViewModel(roles, hotwords, settings)
        var sessionAttempts = 0

        viewModel.refresh(ready())
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        roles.gate = gate
        viewModel.runTest(ready()) {
            sessionAttempts++
            true
        }
        // The first run is now stopped inside its role read, so this one has to be turned away
        // rather than open a second session on top of the first.
        viewModel.runTest(ready()) {
            sessionAttempts++
            true
        }
        assertThat(viewModel.state.value.testing).isTrue()

        roles.gate = null
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(sessionAttempts).isEqualTo(1)
        assertThat(viewModel.state.value.testing).isFalse()
    }

    @Test
    fun `a notice is raised and cleared without touching anything else`() = runTest {
        roles.status = AssistantStatus(state = AssistantRoleState.UNSUPPORTED)
        val viewModel = AssistantSetupViewModel(roles, hotwords, settings)

        viewModel.refresh(ready())
        advanceUntilIdle()
        viewModel.reportProblem("No screen to open")
        assertThat(viewModel.state.value.notice).isEqualTo("No screen to open")

        viewModel.dismissNotice()
        assertThat(viewModel.state.value.notice).isNull()
        assertThat(viewModel.state.value.status?.state).isEqualTo(AssistantRoleState.UNSUPPORTED)
    }

    @Test
    fun `the settings screen is only offered as a fix when this phone has one`() {
        val status = AssistantStatus(state = AssistantRoleState.AVAILABLE, holder = "other.app")
        val facts = ready().copy(serviceActive = false)

        val withScreen = assistantChecks(
            status = status,
            facts = facts,
            settingsAvailable = true,
            session = AssistantSessionProbe.NOT_TRIED,
        )
        val withoutScreen = assistantChecks(
            status = status,
            facts = facts,
            settingsAvailable = false,
            session = AssistantSessionProbe.NOT_TRIED,
        )

        assertThat(withScreen.single { it.id == AssistantCheckId.ROLE }.fix)
            .isEqualTo(AssistantFix.OPEN_ASSISTANT_SETTINGS)
        assertThat(withScreen.single { it.id == AssistantCheckId.SERVICE }.fix)
            .isEqualTo(AssistantFix.OPEN_ASSISTANT_SETTINGS)
        assertThat(withoutScreen.single { it.id == AssistantCheckId.ROLE }.fix).isNull()
        assertThat(withoutScreen.single { it.id == AssistantCheckId.SERVICE }.fix).isNull()
    }

    @Test
    fun `a service missing from the build offers no fix, because no setting can add one`() {
        val checks = assistantChecks(
            status = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA),
            facts = AssistantSystemFacts(
                microphoneGranted = true,
                serviceDeclared = false,
                serviceActive = false,
            ),
            settingsAvailable = true,
            session = AssistantSessionProbe.NOT_TRIED,
        )

        val service = checks.single { it.id == AssistantCheckId.SERVICE }
        assertThat(service.reason).isEqualTo(AssistantCheckReason.SERVICE_NOT_DECLARED)
        assertThat(service.fix).isNull()
    }

    @Test
    fun `a phone that cannot change its assistant says so and offers nothing`() {
        val checks = assistantChecks(
            status = AssistantStatus(state = AssistantRoleState.UNSUPPORTED),
            facts = ready().copy(serviceActive = false),
            settingsAvailable = false,
            session = AssistantSessionProbe.NOT_TRIED,
        )

        val role = checks.first()
        assertThat(role.reason).isEqualTo(AssistantCheckReason.ROLE_UNSUPPORTED)
        assertThat(role.fix).isNull()
    }

    @Test
    fun `a session is only worth opening once the role and the service are both real`() {
        val mira = AssistantStatus(state = AssistantRoleState.HELD, holder = MIRA)
        val other = AssistantStatus(state = AssistantRoleState.AVAILABLE, holder = "other.app")

        assertThat(shouldOpenSession(mira, ready())).isTrue()
        assertThat(shouldOpenSession(mira, ready().copy(serviceActive = false))).isFalse()
        assertThat(shouldOpenSession(other, ready())).isFalse()
        // The microphone is not a precondition: a session that opens deaf is still worth seeing.
        assertThat(shouldOpenSession(mira, ready().copy(microphoneGranted = false))).isTrue()
    }

    @Test
    fun `the screen takes Abu's chosen language and keeps following it`() = runTest {
        val chosen = FakeSettingsRepository(MiraSettings(languageMode = LanguageMode.BANGLA))
        val viewModel = AssistantSetupViewModel(roles, hotwords, chosen)
        advanceUntilIdle()

        assertThat(viewModel.state.value.language).isEqualTo(SpokenLanguage.BENGALI)

        // Changed one screen away, and this one has to move with it rather than keep the language
        // it was opened in.
        chosen.setLanguageMode(LanguageMode.ENGLISH)
        advanceUntilIdle()

        assertThat(viewModel.state.value.language).isEqualTo(SpokenLanguage.ENGLISH)
    }

    private fun ready() = AssistantSystemFacts(
        microphoneGranted = true,
        serviceDeclared = true,
        serviceActive = true,
    )
}

private const val MIRA = "com.rayluxeo.aida"

/**
 * Answers whatever the test last set, and counts the asking, so a test can prove the role is
 * re-read rather than remembered.
 *
 * [settingsIntent] is always null: an `Intent` cannot be constructed in a local unit test, and
 * the honest consequence is that the "a settings screen exists" branch is covered against the
 * pure check builder instead of through here.
 */
private class FakeAssistantRoles : AssistantRoleController {

    var status: AssistantStatus = AssistantStatus(state = AssistantRoleState.AVAILABLE)
    var statusReads = 0

    /** Holds a read open, so a test can try to start a second run while the first is mid-flight. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun status(): AssistantStatus {
        statusReads++
        gate?.await()
        return status
    }

    override fun settingsIntent(): Intent? = null
}

private class FakeHotwordDetector : HotwordCapabilityDetector {

    var support: HotwordSupport = HotwordSupport(
        verdict = HotwordVerdict.SYSTEM_ONLY,
        alternatives = listOf(
            AssistantEntryPoint.SYSTEM_ASSIST_GESTURE,
            AssistantEntryPoint.VOICE_BUTTON,
        ),
    )

    override suspend fun detect(): HotwordSupport = support
}
