package com.rayluxeo.aida.ui.mira

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.CommandRouter
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ContactTarget
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Contact
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import com.rayluxeo.aida.voice.SpeechFailure
import java.time.LocalDateTime
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
 * Drives [MiraViewModel] over the real command pipeline with hand-written seams, because Hilt
 * cannot be used in local unit tests under AGP 9 (google/dagger#5261).
 *
 * The spoken turn itself is not tested here: it belongs to `VoiceConversationController` and is
 * tested against that. What is tested here is the seam — that the screen shows what the
 * controller reports, forwards the press without interpreting it, and never offers a way out of a
 * failure that has none.
 *
 * The dispatcher is unconfined so that a typed line, the command it produces and the reply it
 * logs all settle within one statement — which is how they behave on the device too.
 */
class MiraViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 9, 24, 9, 0))
    private val conversation = FakeConversationRepository()
    private val controller = FakeVoiceConversationController()
    private val tts = FakeMiraTextToSpeech()
    private val earcons = FakeEarconPlayer()
    private val normalizer = FakeTextNormalizer()
    private val local = FakeCommandEngine(EngineId.LOCAL)
    private val gemini = FakeCommandEngine(EngineId.GEMINI).apply { available = false }
    private val dispatcher = RecordingDispatcher()
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
    fun `a typed line is executed and logged in both voices, and never spoken`() = runTest {
        local.result = EngineResult.Interpreted(AppCommand.CreateTask("call the landlord"), CONFIDENT)
        dispatcher.response = AppResult.Success(MiraResponse.TaskCreated("call the landlord", null))
        val viewModel = viewModel()

        viewModel.onDraftChange("add a task to call the landlord")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(dispatcher.dispatched).hasSize(1)
        assertThat(conversation.recorded.map { it.role })
            .containsExactly(TurnRole.ABU, TurnRole.MIRA)
            .inOrder()
        assertThat(conversation.recorded.first().text).isEqualTo("add a task to call the landlord")
        assertThat(conversation.recorded.last().text).contains("call the landlord")
        // A line Abu typed he is already reading, so Mira answers on the screen and stays silent;
        // the completion tone still marks that it landed.
        assertThat(tts.spoken).isEmpty()
        assertThat(earcons.completions).isEqualTo(1)
        assertThat(viewModel.state.value.draft).isEmpty()
        assertThat(viewModel.state.value.lastEngine).isEqualTo(EngineId.LOCAL)
        assertThat(viewModel.state.value.working).isFalse()
    }

    @Test
    fun `typing still works with no microphone at all`() = runTest {
        local.result = EngineResult.Interpreted(AppCommand.GetDailyBrief, CONFIDENT)
        dispatcher.response = AppResult.Success(MiraResponse.TasksFound(0, emptyList()))
        val viewModel = viewModel()
        controller.idle(canListen = false)
        viewModel.onMicrophonePermissionResult(granted = false, canAskAgain = false)
        advanceUntilIdle()

        viewModel.onDraftChange("what's on today")
        assertThat(viewModel.state.value.canSend).isTrue()
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(dispatcher.dispatched).containsExactly(AppCommand.GetDailyBrief)
        assertThat(conversation.recorded).hasSize(2)
        // Typed in, read on the screen: nothing is spoken.
        assertThat(tts.spoken).isEmpty()
    }

    @Test
    fun `an uncertain mutating command waits for a yes before it touches anything`() = runTest {
        local.result = EngineResult.Interpreted(AppCommand.CreateTask("call the landlord"), UNSURE)
        dispatcher.response = AppResult.Success(MiraResponse.TaskCreated("call the landlord", null))
        val viewModel = viewModel()

        viewModel.onDraftChange("call landlord thing")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(dispatcher.dispatched).isEmpty()
        assertThat(viewModel.state.value.pending).isNotNull()

        viewModel.confirmPending()
        advanceUntilIdle()

        assertThat(dispatcher.dispatched).containsExactly(AppCommand.CreateTask("call the landlord"))
        assertThat(viewModel.state.value.pending).isNull()
        assertThat(conversation.recorded.last().commandName).isEqualTo("CreateTask")
    }

    @Test
    fun `cancelling a confirmation runs nothing and says so in the transcript`() = runTest {
        local.result = EngineResult.Interpreted(AppCommand.CreateTask("call the landlord"), UNSURE)
        val viewModel = viewModel()
        viewModel.onDraftChange("call landlord thing")
        viewModel.sendDraft()
        advanceUntilIdle()

        viewModel.cancelPending()
        advanceUntilIdle()

        assertThat(dispatcher.dispatched).isEmpty()
        assertThat(viewModel.state.value.pending).isNull()
        assertThat(conversation.recorded.last().role).isEqualTo(TurnRole.MIRA)
        assertThat(conversation.recorded.last().text).isEqualTo("Left it.")
    }

    @Test
    fun `a typed turn stays on the screen even with spoken replies enabled`() = runTest {
        local.result = EngineResult.Interpreted(AppCommand.GetDailyBrief, CONFIDENT)
        dispatcher.response = AppResult.Success(MiraResponse.TasksFound(0, emptyList()))
        // Spoken replies are left on; typing is silent regardless of that setting, and with the
        // activation sound off there is no completion tone either — the whole turn is on the screen.
        val viewModel = viewModel(
            settings = MiraSettings(activationSound = false),
            preferences = VoicePreferences(spokenResponsesEnabled = true),
        )

        viewModel.onDraftChange("what's on today")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(conversation.recorded).hasSize(2)
        assertThat(tts.spoken).isEmpty()
        assertThat(earcons.completions).isEqualTo(0)
    }

    @Test
    fun `the press is forwarded to the controller and interpreted nowhere else`() = runTest {
        val viewModel = viewModel()
        controller.listening()
        advanceUntilIdle()

        viewModel.onOrbPressed()
        advanceUntilIdle()

        assertThat(controller.presses).isEqualTo(1)
        // Nothing local decided what the press meant: no state was moved and nothing was stopped.
        assertThat(viewModel.state.value.listening).isTrue()
        assertThat(tts.stops).isEqualTo(0)
        // The tone that marks the start of listening is the controller's now.
        assertThat(earcons.activations).isEqualTo(0)
    }

    @Test
    fun `the spoken turn is shown exactly as the controller reports it`() = runTest {
        val viewModel = viewModel()

        controller.listening(amplitude = LOUD, partial = "kal shokal dosh tay")
        advanceUntilIdle()

        assertThat(viewModel.state.value.listening).isTrue()
        assertThat(viewModel.state.value.voice.amplitude).isEqualTo(LOUD)
        assertThat(viewModel.state.value.partial).isEqualTo("kal shokal dosh tay")

        controller.idle()
        advanceUntilIdle()

        // The partial belongs to the session that heard it; it is not a turn and does not linger.
        assertThat(viewModel.state.value.partial).isNull()
    }

    @Test
    fun `a listening session clears what the last reply had prepared`() = runTest {
        preparingACall()
        dispatcher.response = AppResult.Success(MiraResponse.CallPrepared(AHMED))
        val viewModel = viewModel()
        viewModel.onDraftChange("Ahmed ke call koro")
        viewModel.sendDraft()
        advanceUntilIdle()
        assertThat(viewModel.state.value.action).isNotNull()

        controller.listening()
        advanceUntilIdle()

        assertThat(viewModel.state.value.action).isNull()
        assertThat(viewModel.state.value.notice).isNull()
    }

    @Test
    fun `a failure worth repeating offers a retry, and the retry goes to the controller`() = runTest {
        val viewModel = viewModel()

        controller.failed(SpeechFailure.NO_MATCH)
        advanceUntilIdle()

        assertThat(viewModel.state.value.notice?.text).isNotEmpty()
        assertThat(viewModel.state.value.notice?.offer).isEqualTo(NoticeOffer.RETRY)

        viewModel.retryVoice()
        advanceUntilIdle()

        assertThat(controller.retries).isEqualTo(1)
        // The words stay until the controller reports that something has actually started again.
        // A retry it could not honour must not look as though it had been taken.
        assertThat(viewModel.state.value.notice).isNotNull()

        controller.working()
        advanceUntilIdle()
        assertThat(viewModel.state.value.notice).isNull()
    }

    @Test
    fun `a typed reply is silent, so the orb press goes straight to the controller`() = runTest {
        local.result = EngineResult.Interpreted(AppCommand.GetDailyBrief, CONFIDENT)
        dispatcher.response = AppResult.Success(MiraResponse.TasksFound(0, emptyList()))
        val viewModel = viewModel()

        viewModel.onDraftChange("what's on today")
        viewModel.sendDraft()
        advanceUntilIdle()

        // The reply is on the screen, not in the speaker, so Mira is not speaking after a typed line.
        assertThat(tts.spoken).isEmpty()
        assertThat(viewModel.state.value.speaking).isFalse()

        viewModel.onOrbPressed()
        advanceUntilIdle()

        // With nothing of its own playing, the press is the controller's to read — and it stops
        // nothing, because nothing was started here to stop.
        assertThat(controller.presses).isEqualTo(1)
        assertThat(tts.stops).isEqualTo(0)
    }

    @Test
    fun `a failure that cannot succeed again is stated without a retry`() = runTest {
        val viewModel = viewModel()

        controller.failed(SpeechFailure.UNAVAILABLE, canListen = false)
        advanceUntilIdle()

        assertThat(viewModel.state.value.notice?.text).isNotEmpty()
        assertThat(viewModel.state.value.notice?.offer).isNull()
        assertThat(conversation.recorded).isEmpty()
    }

    @Test
    fun `each failure gets its own words`() = runTest {
        val viewModel = viewModel()

        controller.failed(SpeechFailure.NO_MATCH)
        advanceUntilIdle()
        val notHeard = viewModel.state.value.notice?.text

        controller.listening()
        advanceUntilIdle()
        controller.failed(SpeechFailure.LANGUAGE_UNAVAILABLE, canListen = false)
        advanceUntilIdle()

        assertThat(viewModel.state.value.notice?.text).isNotEqualTo(notHeard)
    }

    @Test
    fun `a dismissed failure stays dismissed until Mira listens again`() = runTest {
        val viewModel = viewModel()

        controller.failed(SpeechFailure.NO_MATCH)
        advanceUntilIdle()
        viewModel.dismissNotice()
        assertThat(viewModel.state.value.notice).isNull()

        // The controller goes on reporting the last failure; a notice already read must not spring
        // back every time anything else about the session changes.
        controller.idle()
        advanceUntilIdle()
        controller.failed(SpeechFailure.NO_MATCH)
        advanceUntilIdle()
        assertThat(viewModel.state.value.notice).isNull()

        controller.listening()
        advanceUntilIdle()
        controller.failed(SpeechFailure.NO_MATCH)
        advanceUntilIdle()
        assertThat(viewModel.state.value.notice).isNotNull()
    }

    @Test
    fun `a refused microphone is stated plainly and leaves typing untouched`() = runTest {
        val viewModel = viewModel()

        viewModel.onMicrophonePermissionResult(granted = false, canAskAgain = true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.microphone).isEqualTo(MicrophoneAccess.ASKABLE)
        assertThat(viewModel.state.value.notice?.text).isNotEmpty()
        assertThat(viewModel.state.value.notice?.offer).isEqualTo(NoticeOffer.ALLOW_MICROPHONE)
        assertThat(controller.presses).isEqualTo(0)

        viewModel.onDraftChange("what's on today")
        assertThat(viewModel.state.value.canSend).isTrue()
    }

    @Test
    fun `a refusal Android will not raise again leaves the settings screen as the way in`() = runTest {
        val viewModel = viewModel()
        controller.idle(canListen = false)

        viewModel.onMicrophonePermissionResult(granted = false, canAskAgain = false)
        advanceUntilIdle()

        assertThat(viewModel.state.value.microphone).isEqualTo(MicrophoneAccess.SETTINGS_ONLY)
        assertThat(viewModel.state.value.notice?.offer).isEqualTo(NoticeOffer.MICROPHONE_SETTINGS)
        // An orb that cannot do anything is not left looking as though it could.
        assertThat(viewModel.state.value.canListen).isFalse()
    }

    @Test
    fun `granting the microphone finishes the press that asked for it`() = runTest {
        val viewModel = viewModel()
        viewModel.onMicrophonePermissionResult(granted = false, canAskAgain = true)
        advanceUntilIdle()

        viewModel.onMicrophonePermissionResult(granted = true, canAskAgain = false)
        advanceUntilIdle()

        assertThat(viewModel.state.value.microphone).isEqualTo(MicrophoneAccess.GRANTED)
        assertThat(viewModel.state.value.notice).isNull()
        assertThat(controller.presses).isEqualTo(1)
    }

    @Test
    fun `a grant made on a settings screen is picked up on the way back in`() = runTest {
        val viewModel = viewModel()
        controller.idle(canListen = false)
        viewModel.onMicrophonePermissionResult(granted = false, canAskAgain = false)
        advanceUntilIdle()

        viewModel.onMicrophoneChecked(granted = true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.microphone).isEqualTo(MicrophoneAccess.GRANTED)
        assertThat(viewModel.state.value.notice).isNull()
        // Coming back into the app is not a press. Abu asks again when he is ready.
        assertThat(controller.presses).isEqualTo(0)
    }

    @Test
    fun `a microphone taken back while Mira is running can be asked for again`() = runTest {
        val viewModel = viewModel()
        viewModel.onMicrophoneChecked(granted = true)
        advanceUntilIdle()

        viewModel.onMicrophoneChecked(granted = false)
        advanceUntilIdle()

        assertThat(viewModel.state.value.microphone).isEqualTo(MicrophoneAccess.ASKABLE)
        assertThat(viewModel.state.value.canListen).isTrue()
    }

    @Test
    fun `the voice indicator follows Mira speaking, not the command behind it`() = runTest {
        local.result = EngineResult.Interpreted(AppCommand.GetDailyBrief, CONFIDENT)
        dispatcher.response = AppResult.Success(MiraResponse.TasksFound(0, emptyList()))
        val viewModel = viewModel()

        viewModel.onDraftChange("what's on today")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(viewModel.state.value.working).isFalse()
        assertThat(viewModel.state.value.speaking).isFalse()

        tts.speaking(true)
        advanceUntilIdle()
        assertThat(viewModel.state.value.speaking).isTrue()

        tts.speaking(false)
        advanceUntilIdle()
        assertThat(viewModel.state.value.speaking).isFalse()
    }

    @Test
    fun `a prepared call is held out on Mira's own turn rather than dialled`() = runTest {
        preparingACall()
        dispatcher.response = AppResult.Success(MiraResponse.CallPrepared(AHMED))
        val viewModel = viewModel()

        viewModel.onDraftChange("Ahmed ke call koro")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(viewModel.state.value.action).isEqualTo(MiraAction.Call(AHMED_NUMBER))
        assertThat(conversation.recorded.last().role).isEqualTo(TurnRole.MIRA)
        assertThat(viewModel.state.value.actionTurnId).isEqualTo(conversation.recorded.last().id)
    }

    @Test
    fun `a contact with no number is never offered as a call`() = runTest {
        preparingACall()
        dispatcher.response =
            AppResult.Success(MiraResponse.CallPrepared(AHMED.copy(phoneNumber = null)))
        val viewModel = viewModel()

        viewModel.onDraftChange("Ahmed ke call koro")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(viewModel.state.value.action).isNull()
        assertThat(viewModel.state.value.actionTurnId).isNull()
    }

    @Test
    fun `a prepared message carries the draft Mira wrote`() = runTest {
        local.result = EngineResult.Interpreted(
            AppCommand.PrepareMessage(ContactTarget.ByName("Ahmed"), "ten minutes late"),
            CONFIDENT,
        )
        dispatcher.response =
            AppResult.Success(MiraResponse.MessagePrepared(AHMED, "ten minutes late"))
        val viewModel = viewModel()

        viewModel.onDraftChange("Ahmed ke message koro ten minutes late")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(viewModel.state.value.action)
            .isEqualTo(MiraAction.Message(AHMED_NUMBER, "ten minutes late"))
    }

    @Test
    fun `a permission Mira needs is offered as a grant, not only apologised for`() = runTest {
        preparingACall()
        dispatcher.response =
            AppResult.Success(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS))
        val viewModel = viewModel()

        viewModel.onDraftChange("Ahmed ke call koro")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(viewModel.state.value.action)
            .isEqualTo(MiraAction.Grant(MiraCapability.CONTACTS, viaSettings = false))
    }

    @Test
    fun `exact alarms go straight to the settings screen, which is the only way in`() = runTest {
        local.result = EngineResult.Interpreted(AppCommand.GetDailyBrief, CONFIDENT)
        dispatcher.response =
            AppResult.Success(MiraResponse.PermissionNeeded(MiraCapability.EXACT_ALARMS))
        val viewModel = viewModel()

        viewModel.onDraftChange("what's on today")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(viewModel.state.value.action)
            .isEqualTo(MiraAction.Grant(MiraCapability.EXACT_ALARMS, viaSettings = true))
    }

    @Test
    fun `granting what Mira asked for finishes what Abu asked for`() = runTest {
        preparingACall()
        dispatcher.response =
            AppResult.Success(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS))
        val viewModel = viewModel()
        viewModel.onDraftChange("Ahmed ke call koro")
        viewModel.sendDraft()
        advanceUntilIdle()

        dispatcher.response = AppResult.Success(MiraResponse.CallPrepared(AHMED))
        viewModel.onCapabilityResult(
            capability = MiraCapability.CONTACTS,
            granted = true,
            canAskAgain = false,
        )
        advanceUntilIdle()

        assertThat(dispatcher.dispatched).hasSize(2)
        assertThat(viewModel.state.value.action).isEqualTo(MiraAction.Call(AHMED_NUMBER))
        assertThat(viewModel.state.value.working).isFalse()
    }

    @Test
    fun `a refusal Android will not raise again moves the control to settings`() = runTest {
        preparingACall()
        dispatcher.response =
            AppResult.Success(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS))
        val viewModel = viewModel()
        viewModel.onDraftChange("Ahmed ke call koro")
        viewModel.sendDraft()
        advanceUntilIdle()

        viewModel.onCapabilityResult(
            capability = MiraCapability.CONTACTS,
            granted = false,
            canAskAgain = false,
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.action)
            .isEqualTo(MiraAction.Grant(MiraCapability.CONTACTS, viaSettings = true))
        assertThat(dispatcher.dispatched).hasSize(1)
    }

    @Test
    fun `a refusal Android will raise again leaves the offer where it was`() = runTest {
        preparingACall()
        dispatcher.response =
            AppResult.Success(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS))
        val viewModel = viewModel()
        viewModel.onDraftChange("Ahmed ke call koro")
        viewModel.sendDraft()
        advanceUntilIdle()

        viewModel.onCapabilityResult(
            capability = MiraCapability.CONTACTS,
            granted = false,
            canAskAgain = true,
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.action)
            .isEqualTo(MiraAction.Grant(MiraCapability.CONTACTS, viaSettings = false))
    }

    @Test
    fun `a grant after a yes does not ask Abu to agree a second time`() = runTest {
        val event = AppCommand.CreateCalendarEvent("meeting", clock.now().plusSeconds(3_600))
        local.result = EngineResult.Interpreted(event, UNSURE)
        dispatcher.response =
            AppResult.Success(MiraResponse.PermissionNeeded(MiraCapability.CALENDAR))
        val viewModel = viewModel()
        viewModel.onDraftChange("kal 10 tay meeting")
        viewModel.sendDraft()
        advanceUntilIdle()

        viewModel.confirmPending()
        advanceUntilIdle()
        assertThat(viewModel.state.value.action)
            .isEqualTo(MiraAction.Grant(MiraCapability.CALENDAR, viaSettings = false))

        dispatcher.response =
            AppResult.Success(MiraResponse.CalendarEventCreated("meeting", event.startsAt))
        viewModel.onCapabilityResult(
            capability = MiraCapability.CALENDAR,
            granted = true,
            canAskAgain = false,
        )
        advanceUntilIdle()

        assertThat(dispatcher.dispatched).containsExactly(event, event).inOrder()
        assertThat(viewModel.state.value.pending).isNull()
        assertThat(viewModel.state.value.action).isNull()
    }

    @Test
    fun `an answer to a grant Mira is no longer offering changes nothing`() = runTest {
        local.result = EngineResult.Interpreted(AppCommand.GetDailyBrief, CONFIDENT)
        dispatcher.response = AppResult.Success(MiraResponse.TasksFound(0, emptyList()))
        val viewModel = viewModel()
        viewModel.onDraftChange("what's on today")
        viewModel.sendDraft()
        advanceUntilIdle()

        viewModel.onCapabilityResult(
            capability = MiraCapability.CONTACTS,
            granted = true,
            canAskAgain = false,
        )
        advanceUntilIdle()

        assertThat(dispatcher.dispatched).hasSize(1)
        assertThat(viewModel.state.value.action).isNull()
    }

    @Test
    fun `the next thing Abu says clears what the last reply offered`() = runTest {
        preparingACall()
        dispatcher.response = AppResult.Success(MiraResponse.CallPrepared(AHMED))
        val viewModel = viewModel()
        viewModel.onDraftChange("Ahmed ke call koro")
        viewModel.sendDraft()
        advanceUntilIdle()
        assertThat(viewModel.state.value.action).isNotNull()

        local.result = EngineResult.Interpreted(AppCommand.GetDailyBrief, CONFIDENT)
        dispatcher.response = AppResult.Success(MiraResponse.TasksFound(0, emptyList()))
        viewModel.onDraftChange("what's on today")
        viewModel.sendDraft()
        advanceUntilIdle()

        assertThat(viewModel.state.value.action).isNull()
        assertThat(viewModel.state.value.actionTurnId).isNull()
    }

    @Test
    fun `a control this phone cannot open stops being offered and says so`() = runTest {
        preparingACall()
        dispatcher.response = AppResult.Success(MiraResponse.CallPrepared(AHMED))
        val viewModel = viewModel()
        viewModel.onDraftChange("Ahmed ke call koro")
        viewModel.sendDraft()
        advanceUntilIdle()

        viewModel.onActionUnavailable()

        assertThat(viewModel.state.value.action).isNull()
        assertThat(viewModel.state.value.notice?.text).isNotEmpty()
        assertThat(viewModel.state.value.notice?.offer).isNull()
    }

    /**
     * The screen words its own chrome, so Abu's language setting has to reach it. It used to stop
     * at the view model, which is why the status line above a Bengali exchange read "Listening".
     */
    @Test
    fun `Abu's language setting reaches the screen`() = runTest {
        val viewModel = viewModel(MiraSettings(languageMode = LanguageMode.BANGLA))
        advanceUntilIdle()

        assertThat(viewModel.state.value.language).isEqualTo(SpokenLanguage.BENGALI)
    }

    /**
     * A notice belongs to no particular utterance, so it is worded in whatever language the screen
     * is speaking — one rule, on the state, rather than one here and another on the screen.
     */
    @Test
    fun `a speech failure is worded in the language the screen is speaking`() = runTest {
        val viewModel = viewModel(MiraSettings(languageMode = LanguageMode.BANGLA))
        advanceUntilIdle()

        controller.failed(SpeechFailure.NO_MATCH)
        advanceUntilIdle()

        assertThat(viewModel.state.value.notice?.text)
            .isEqualTo(speechFailureWording(SpeechFailure.NO_MATCH, SpokenLanguage.BENGALI))
    }

    /** The command behind every call test, so only the response under test differs between them. */
    private fun preparingACall() {
        local.result = EngineResult.Interpreted(
            AppCommand.PrepareCall(ContactTarget.ByName("Ahmed")),
            CONFIDENT,
        )
    }

    private fun viewModel(
        settings: MiraSettings = MiraSettings(),
        preferences: VoicePreferences = VoicePreferences.DEFAULT,
    ): MiraViewModel {
        val settingsRepository = FakeSettingsRepository(settings)
        val execute = ExecuteCommandUseCase(
            normalizer = normalizer,
            interpreter = CommandRouter(local, gemini),
            validator = PassThroughValidator(),
            dispatcher = dispatcher,
            conversation = conversation,
            contextManager = com.rayluxeo.aida.ai.assistant.ConversationContextManager(),
            settings = settingsRepository,
            clock = clock,
        )
        return MiraViewModel(
            execute = execute,
            controller = controller,
            tts = tts,
            earcons = earcons,
            renderer = renderer,
            entry = MiraEntryCoordinator(),
            conversation = conversation,
            settings = settingsRepository,
            voicePreferences = FakeVoicePreferenceRepository(preferences),
        )
    }
}

/** Comfortably above and below [EngineResult.CONFIRM_THRESHOLD], so the gate is the thing tested. */
private const val CONFIDENT = 0.95f
private const val UNSURE = 0.40f
private const val LOUD = 0.80f

private const val AHMED_NUMBER = "+8801712345678"

/** Someone the address book could have handed back, with a number Mira could actually reach. */
private val AHMED = Contact(
    systemId = 42L,
    displayName = "Ahmed Rahman",
    phoneNumber = AHMED_NUMBER,
    email = null,
)
