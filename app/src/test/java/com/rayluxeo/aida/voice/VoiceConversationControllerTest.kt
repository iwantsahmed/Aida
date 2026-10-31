package com.rayluxeo.aida.voice

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandDispatcher
import com.rayluxeo.aida.domain.command.CommandInterpreter
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.CommandValidator
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.command.RoutedCommand
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.domain.nlu.NormalizedText
import com.rayluxeo.aida.domain.nlu.TextNormalizer
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Drives [VoiceConversationControllerImpl] over the real command pipeline with hand-written seams,
 * because Hilt cannot be used in local unit tests under AGP 9 (google/dagger#5261) and there is no
 * mocking framework on this classpath.
 *
 * The dispatcher is unconfined so that every intermediate state is actually observed: the point of
 * most of these tests is the *order* Mira passes through, and a conflated `StateFlow` read at the
 * end of a turn would hide exactly the transitions that matter.
 *
 * What is NOT covered here, and needs an instrumented test: anything behind the framework classes
 * these fakes stand in for — that a real `SpeechRecognizer` ends its flow after a final result,
 * that a real `TextToSpeech` returns from `speak` when stopped, and that abandoning audio focus
 * actually reaches `AudioManager`.
 */
class VoiceConversationControllerTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 9, 24, 9, 0))
    private val recognizer = FakeSpeechRecognitionManager()
    private val voice = FakeMiraTextToSpeech()
    private val earcons = RecordingEarconPlayer()
    private val audioFocus = FakeAudioFocusManager()
    private val conversation = RecordingConversationRepository()
    private val settings = StubSettingsRepository()
    private val preferences = StubVoicePreferenceRepository()
    private val interpreter = StubCommandInterpreter()
    private val commands = RecordingCommandDispatcher()
    private val renderer = MiraResponseRenderer(clock)

    /** Every state the orb passed through, in order, with repeats collapsed. */
    private val states = mutableListOf<MiraVoiceState>()

    // ------------------------------------------------------------------ the ordinary turn

    @Test
    fun `a spoken command is heard, run and answered out loud`() = runTest(unconfined()) {
        understands(AppCommand.CreateTask("call the landlord"))
        commands.response = AppResult.Success(MiraResponse.TaskCreated("call the landlord", null))
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()

        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.LISTENING)
        assertThat(earcons.activations).isEqualTo(1)
        assertThat(recognizer.sessions).isEqualTo(1)

        recognizer.emit(SpeechEvent.Partial("add a task to"))
        recognizer.emit(SpeechEvent.Level(0.7f))
        runCurrent()

        assertThat(controller.state.value.partialTranscript).isEqualTo("add a task to")
        assertThat(controller.state.value.amplitude).isEqualTo(0.7f)

        recognizer.emit(SpeechEvent.Final("add a task to call the landlord", "en-US", -1f))
        advanceUntilIdle()

        assertThat(commands.dispatched).containsExactly(AppCommand.CreateTask("call the landlord"))
        assertThat(conversation.recorded.map { it.role })
            .containsExactly(TurnRole.ABU, TurnRole.MIRA)
            .inOrder()
        assertThat(conversation.recorded.first().text).isEqualTo("add a task to call the landlord")
        assertThat(conversation.recorded.first().source).isEqualTo(InputSource.VOICE)
        assertThat(conversation.recorded.last().text).contains("call the landlord")
        assertThat(voice.spoken).hasSize(1)
        assertThat(earcons.completions).isEqualTo(1)
        assertThat(controller.state.value.partialTranscript).isNull()
        assertThat(controller.state.value.amplitude).isEqualTo(0f)
        settleTheOrb()
        assertThat(states)
            .containsAtLeast(MiraVoiceState.LISTENING, MiraVoiceState.SUCCESS, MiraVoiceState.IDLE)
            .inOrder()
    }

    @Test
    fun `the orb shows every stage of a turn, in order`() = runTest(unconfined()) {
        understands(AppCommand.CreateTask("call the landlord"))
        commands.response = AppResult.Success(MiraResponse.TaskCreated("call the landlord", null))
        // Each stage is held open in turn. Without that the whole turn runs in one pass and
        // StateFlow, being conflated, reports only where it ended up — which would let the orb
        // skip a stage entirely without any test noticing.
        interpreter.holdUntilReleased = true
        commands.holdUntilReleased = true
        voice.holdUntilStopped = true
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()
        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.LISTENING)

        recognizer.emit(SpeechEvent.Final("add a task to call the landlord", "en-US", -1f))
        advanceUntilIdle()
        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.PROCESSING)

        interpreter.release()
        advanceUntilIdle()
        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.EXECUTING)

        commands.release()
        advanceUntilIdle()
        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.SPEAKING)

        voice.stop()
        advanceUntilIdle()
        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.SUCCESS)

        settleTheOrb()

        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.IDLE)
        assertThat(states)
            .containsAtLeast(
                MiraVoiceState.LISTENING,
                MiraVoiceState.PROCESSING,
                MiraVoiceState.EXECUTING,
                MiraVoiceState.SPEAKING,
                MiraVoiceState.SUCCESS,
                MiraVoiceState.IDLE,
            )
            .inOrder()
    }

    @Test
    fun `the microphone and the speaker are each given back after they are used`() =
        runTest(unconfined()) {
            understands(AppCommand.CreateTask("call the landlord"))
            val controller = newController()

            controller.onOrbPressed()
            runCurrent()
            recognizer.emit(SpeechEvent.Final("add a task", "en-US", -1f))
            advanceUntilIdle()

            assertThat(audioFocus.requested)
                .containsExactly(AudioFocusPurpose.LISTENING, AudioFocusPurpose.SPEAKING)
                .inOrder()
            assertThat(audioFocus.released)
                .containsExactly(AudioFocusPurpose.LISTENING, AudioFocusPurpose.SPEAKING)
                .inOrder()
        }

    @Test
    fun `the language Abu chose is what she listens for`() = runTest(unconfined()) {
        settings.setLanguageMode(LanguageMode.BANGLA)
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()

        assertThat(recognizer.lastRequest?.mode).isEqualTo(LanguageMode.BANGLA)
        assertThat(recognizer.lastRequest?.preferOnDevice).isTrue()
    }

    // ------------------------------------------------------------------ what a press means

    @Test
    fun `pressing while she listens submits what she heard so far`() = runTest(unconfined()) {
        understands(AppCommand.CreateTask("landlord"))
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()
        recognizer.emit(SpeechEvent.Partial("add a task to call the landlord"))
        runCurrent()

        controller.onOrbPressed()
        advanceUntilIdle()

        assertThat(commands.dispatched).hasSize(1)
        assertThat(conversation.recorded.first().text).isEqualTo("add a task to call the landlord")
        assertThat(audioFocus.released).contains(AudioFocusPurpose.LISTENING)
        assertThat(recognizer.sessions).isEqualTo(1)
    }

    @Test
    fun `pressing while she speaks stops her and does not start listening`() =
        runTest(unconfined()) {
            understands(AppCommand.CreateTask("landlord"))
            voice.holdUntilStopped = true
            val controller = newController()

            controller.onOrbPressed()
            runCurrent()
            recognizer.emit(SpeechEvent.Final("add a task", "en-US", -1f))
            advanceUntilIdle()

            assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.SPEAKING)

            controller.onOrbPressed()
            advanceUntilIdle()

            assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.IDLE)
            assertThat(voice.stops).isEqualTo(1)
            assertThat(recognizer.sessions).isEqualTo(1)
            assertThat(earcons.completions).isEqualTo(0)
            assertThat(states).doesNotContain(MiraVoiceState.SUCCESS)
        }

    @Test
    fun `a press is ignored while the command is in flight`() = runTest(unconfined()) {
        understands(AppCommand.CreateTask("landlord"))
        commands.holdUntilReleased = true
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()
        recognizer.emit(SpeechEvent.Final("add a task", "en-US", -1f))
        advanceUntilIdle()

        // EXECUTING, not PROCESSING: interpreting is over and the command is being written.
        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.EXECUTING)

        controller.onOrbPressed()
        advanceUntilIdle()

        assertThat(recognizer.sessions).isEqualTo(1)
        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.EXECUTING)

        commands.release()
        advanceUntilIdle()

        assertThat(commands.dispatched).hasSize(1)
        assertThat(states).contains(MiraVoiceState.SUCCESS)
    }

    @Test
    fun `cancel abandons the session and nothing is submitted`() = runTest(unconfined()) {
        understands(AppCommand.CreateTask("landlord"))
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()
        recognizer.emit(SpeechEvent.Partial("add a task"))
        runCurrent()

        controller.cancel()
        advanceUntilIdle()

        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.IDLE)
        assertThat(controller.state.value.partialTranscript).isNull()
        assertThat(audioFocus.released).contains(AudioFocusPurpose.LISTENING)
        assertThat(commands.dispatched).isEmpty()
        assertThat(conversation.recorded).isEmpty()
    }

    // ------------------------------------------------------------------ when it goes wrong

    @Test
    fun `a retryable failure is shown and retryLast re-runs what she caught`() =
        runTest(unconfined()) {
            understands(AppCommand.CreateTask("landlord"))
            val controller = newController()

            controller.onOrbPressed()
            runCurrent()
            recognizer.emit(SpeechEvent.Partial("add a task to call the landlord"))
            runCurrent()
            recognizer.emit(SpeechEvent.Failed(SpeechFailure.BUSY))
            recognizer.endSession()
            advanceUntilIdle()

            assertThat(controller.state.value.lastFailure).isEqualTo(SpeechFailure.BUSY)
            settleTheOrb()
            assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.IDLE)
            assertThat(states).containsAtLeast(MiraVoiceState.ERROR, MiraVoiceState.IDLE).inOrder()
            assertThat(earcons.errors).isEqualTo(1)
            assertThat(commands.dispatched).isEmpty()

            controller.retryLast()
            advanceUntilIdle()

            assertThat(commands.dispatched).hasSize(1)
            assertThat(conversation.recorded.first().text)
                .isEqualTo("add a task to call the landlord")
            assertThat(recognizer.sessions).isEqualTo(1)
        }

    @Test
    fun `partial speech caught before timeout or no-match is delivered rather than dropped`() =
        runTest(unconfined()) {
            understands(AppCommand.CreateTask("Ahmed er invoice"))
            val controller = newController()

            controller.onOrbPressed()
            runCurrent()
            recognizer.emit(SpeechEvent.Partial("Ahmed er invoice banao"))
            runCurrent()
            recognizer.emit(SpeechEvent.Failed(SpeechFailure.TIMEOUT))
            recognizer.endSession()
            advanceUntilIdle()

            // Utterance was delivered and executed instead of lost to a timeout
            assertThat(commands.dispatched).hasSize(1)
            assertThat(conversation.recorded.first().text).isEqualTo("Ahmed er invoice banao")
            assertThat(states).contains(MiraVoiceState.SUCCESS)
        }

    @Test
    fun `retryLast does nothing when trying again could not help`() = runTest(unconfined()) {
        understands(AppCommand.CreateTask("landlord"))
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()
        recognizer.emit(SpeechEvent.Partial("add a task"))
        runCurrent()
        recognizer.emit(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE))
        recognizer.endSession()
        advanceUntilIdle()

        controller.retryLast()
        advanceUntilIdle()

        assertThat(commands.dispatched).isEmpty()
        assertThat(conversation.recorded).isEmpty()
    }

    @Test
    fun `saying nothing at all is not a failure`() = runTest(unconfined()) {
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()
        recognizer.endSession()
        advanceUntilIdle()

        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.IDLE)
        assertThat(controller.state.value.lastFailure).isNull()
        assertThat(states).doesNotContain(MiraVoiceState.ERROR)
        assertThat(earcons.errors).isEqualTo(0)
        assertThat(commands.dispatched).isEmpty()
    }

    @Test
    fun `a refused microphone is reported instead of opening a session`() = runTest(unconfined()) {
        recognizer.permitted = false
        val controller = newController()

        assertThat(controller.state.value.canListen).isFalse()

        controller.onOrbPressed()
        advanceUntilIdle()

        assertThat(recognizer.sessions).isEqualTo(0)
        assertThat(controller.state.value.lastFailure).isEqualTo(SpeechFailure.PERMISSION)
        assertThat(states).contains(MiraVoiceState.ERROR)
        assertThat(earcons.errors).isEqualTo(1)
    }

    @Test
    fun `a phone with no recognizer says so rather than pretending to listen`() =
        runTest(unconfined()) {
            recognizer.available = false
            val controller = newController()

            controller.onOrbPressed()
            advanceUntilIdle()

            assertThat(recognizer.sessions).isEqualTo(0)
            assertThat(controller.state.value.lastFailure).isEqualTo(SpeechFailure.UNAVAILABLE)
        }

    @Test
    fun `a command she could not understand ends the turn as a failure, still answered`() =
        runTest(unconfined()) {
            interpreter.routed = RoutedCommand(EngineResult.NotUnderstood, EngineId.LOCAL)
            val controller = newController()

            controller.onOrbPressed()
            runCurrent()
            recognizer.emit(SpeechEvent.Final("blaaargh", "en-US", -1f))
            advanceUntilIdle()

            assertThat(commands.dispatched).isEmpty()
            assertThat(conversation.recorded.last().role).isEqualTo(TurnRole.MIRA)
            assertThat(voice.spoken).hasSize(1)
            assertThat(earcons.errors).isEqualTo(1)
            assertThat(earcons.completions).isEqualTo(0)
            assertThat(states).contains(MiraVoiceState.ERROR)
        }

    // ------------------------------------------------------------------ Abu's settings

    @Test
    fun `with spoken replies off she writes the answer down and stays quiet`() =
        runTest(unconfined()) {
            understands(AppCommand.CreateTask("landlord"))
            preferences.setSpokenResponsesEnabled(false)
            val controller = newController()

            controller.onOrbPressed()
            runCurrent()
            recognizer.emit(SpeechEvent.Final("add a task", "en-US", -1f))
            advanceUntilIdle()

            assertThat(voice.spoken).isEmpty()
            assertThat(audioFocus.requested).containsExactly(AudioFocusPurpose.LISTENING)
            assertThat(conversation.recorded.last().role).isEqualTo(TurnRole.MIRA)
            assertThat(states).doesNotContain(MiraVoiceState.SPEAKING)
            assertThat(states).contains(MiraVoiceState.SUCCESS)
        }

    @Test
    fun `with her sounds off the turn runs without a single tone`() = runTest(unconfined()) {
        understands(AppCommand.CreateTask("landlord"))
        settings.setActivationSound(false)
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()
        recognizer.emit(SpeechEvent.Final("add a task", "en-US", -1f))
        advanceUntilIdle()

        assertThat(earcons.activations).isEqualTo(0)
        assertThat(earcons.completions).isEqualTo(0)
        assertThat(earcons.errors).isEqualTo(0)
        assertThat(commands.dispatched).hasSize(1)
    }

    // ------------------------------------------------------------------ the audio underneath

    @Test
    fun `losing the audio to a call stops her mid-sentence`() = runTest(unconfined()) {
        understands(AppCommand.CreateTask("landlord"))
        voice.holdUntilStopped = true
        val controller = newController()

        controller.onOrbPressed()
        runCurrent()
        recognizer.emit(SpeechEvent.Final("add a task", "en-US", -1f))
        advanceUntilIdle()

        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.SPEAKING)

        audioFocus.loseFocus()
        advanceUntilIdle()

        assertThat(voice.stops).isEqualTo(1)
        assertThat(controller.state.value.state).isEqualTo(MiraVoiceState.IDLE)
    }

    // ------------------------------------------------------------------ plumbing

    private fun understands(command: AppCommand) {
        interpreter.routed = RoutedCommand(
            result = EngineResult.Interpreted(command, CONFIDENT),
            engine = EngineId.LOCAL,
        )
        commands.response = AppResult.Success(MiraResponse.TaskCreated("call the landlord", null))
    }

    /**
     * Steps past the moment SUCCESS or ERROR is held on the orb.
     *
     * The hold runs in the controller's own scope, and [advanceUntilIdle] does not advance virtual
     * time for work that exists only in `backgroundScope` — so without this the orb is still
     * showing how the turn ended rather than having settled.
     */
    private fun TestScope.settleTheOrb() {
        advanceTimeBy(TURN_END_HOLD_MS + 1)
        runCurrent()
    }

    private fun TestScope.newController(): VoiceConversationController {
        val execute = ExecuteCommandUseCase(
            normalizer = FixedTextNormalizer(),
            interpreter = interpreter,
            validator = PassThroughCommandValidator(),
            dispatcher = commands,
            conversation = conversation,
            contextManager = com.rayluxeo.aida.ai.assistant.ConversationContextManager(),
            settings = settings,
            clock = clock,
        )
        val controller = VoiceConversationControllerImpl(
            recognizer = recognizer,
            voice = voice,
            earcons = earcons,
            audioFocus = audioFocus,
            execute = execute,
            renderer = renderer,
            settings = settings,
            voicePreferences = preferences,
            scope = backgroundScope,
        )
        backgroundScope.launch {
            controller.state.map { it.state }.distinctUntilChanged().collect { states += it }
        }
        return controller
    }

    private fun unconfined() = UnconfinedTestDispatcher()

    private companion object {
        /** Comfortably above `EngineResult.CONFIRM_THRESHOLD`, so nothing waits for a yes. */
        const val CONFIDENT = 0.95f
    }

    /** Hands out one channel per listening session, exactly as the real cold flow does. */
    private class FakeSpeechRecognitionManager : SpeechRecognitionManager {

        var available = true
        var permitted = true
        var sessions = 0
        var lastRequest: RecognitionRequest? = null

        private var channel: Channel<SpeechEvent>? = null

        override fun isAvailable(): Boolean = available

        override fun hasPermission(): Boolean = permitted

        override suspend fun capabilities(): RecognitionCapabilities = RecognitionCapabilities(
            onDeviceAvailable = true,
            supportedLanguages = setOf(SpokenLanguage.ENGLISH, SpokenLanguage.BENGALI),
            supportsLanguageDetection = false,
            supportsLanguageSwitching = false,
        )

        override fun listen(request: RecognitionRequest): Flow<SpeechEvent> {
            lastRequest = request
            sessions++
            val fresh = Channel<SpeechEvent>(Channel.UNLIMITED)
            channel = fresh
            return fresh.receiveAsFlow()
        }

        suspend fun emit(event: SpeechEvent) {
            channel?.send(event)
        }

        /** The real recognizer ends its flow when the session is over; so does this. */
        fun endSession() {
            channel?.close()
        }
    }

    private class FakeMiraTextToSpeech : MiraTextToSpeech {

        private val _state = MutableStateFlow(SpeakingState.IDLE)
        override val state: StateFlow<SpeakingState> = _state.asStateFlow()

        val spoken = mutableListOf<Pair<String, SpokenLanguage>>()
        var stops = 0

        /** Keeps `speak` suspended so a test can interrupt her half way through a reply. */
        var holdUntilStopped = false

        private var utterance: CompletableDeferred<Unit>? = null

        override suspend fun canSpeak(language: SpokenLanguage): Boolean = true

        override suspend fun speak(text: String, language: SpokenLanguage): AppResult<Unit> {
            spoken += text to language
            if (holdUntilStopped) {
                val pending = CompletableDeferred<Unit>()
                utterance = pending
                _state.value = SpeakingState.SPEAKING
                pending.await()
                _state.value = SpeakingState.IDLE
            }
            return AppResult.Success(Unit)
        }

        override fun stop() {
            stops++
            utterance?.complete(Unit)
            utterance = null
            _state.value = SpeakingState.IDLE
        }
    }

    private class RecordingEarconPlayer : MiraEarconPlayer {
        var activations = 0
        var completions = 0
        var errors = 0
        var released = 0

        override fun activation() {
            activations++
        }

        override fun completion() {
            completions++
        }

        override fun error() {
            errors++
        }

        override fun release() {
            released++
        }
    }

    private class FakeAudioFocusManager : MiraAudioFocusManager {

        val requested = mutableListOf<AudioFocusPurpose>()
        val released = mutableListOf<AudioFocusPurpose>()

        private val lost = Channel<Unit>(Channel.UNLIMITED)
        override val focusLost: Flow<Unit> = lost.receiveAsFlow()

        override suspend fun <T> withFocus(purpose: AudioFocusPurpose, block: suspend () -> T): T {
            requested += purpose
            try {
                return block()
            } finally {
                released += purpose
            }
        }

        suspend fun loseFocus() {
            lost.send(Unit)
        }
    }

    private class RecordingConversationRepository : ConversationRepository {

        private val turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
        private var nextId = 1L

        val recorded: List<ConversationTurn> get() = turns.value

        override fun observeRecent(limit: Int): Flow<List<ConversationTurn>> = turns.asStateFlow()

        override suspend fun append(turn: ConversationTurn): AppResult<ConversationTurn> {
            val stored = turn.copy(id = nextId++)
            turns.update { it + stored }
            return AppResult.Success(stored)
        }

        override suspend fun clear(): AppResult<Unit> {
            turns.value = emptyList()
            return AppResult.Success(Unit)
        }
    }

    private class StubSettingsRepository : SettingsRepository {

        private val _settings = MutableStateFlow(MiraSettings())
        override val settings: Flow<MiraSettings> = _settings.asStateFlow()

        override suspend fun current(): MiraSettings = _settings.value

        override suspend fun setLanguageMode(mode: LanguageMode) {
            _settings.update { it.copy(languageMode = mode) }
        }

        override suspend fun setActivationSound(enabled: Boolean) {
            _settings.update { it.copy(activationSound = enabled) }
        }

        override suspend fun setHapticFeedback(enabled: Boolean) {
            _settings.update { it.copy(hapticFeedback = enabled) }
        }

        override suspend fun setOnboardingComplete(complete: Boolean) {
            _settings.update { it.copy(onboardingComplete = complete) }
        }
    }

    private class StubVoicePreferenceRepository : VoicePreferenceRepository {

        private val _preferences = MutableStateFlow(VoicePreferences.DEFAULT)
        override val preferences: Flow<VoicePreferences> = _preferences.asStateFlow()

        override suspend fun current(): VoicePreferences = _preferences.value

        override suspend fun setEngine(packageName: String?) {
            _preferences.update { it.copy(engine = packageName) }
        }

        override suspend fun setVoiceId(language: SpokenLanguage, voiceId: String?) {
            _preferences.update {
                when (language) {
                    SpokenLanguage.ENGLISH -> it.copy(englishVoiceId = voiceId)
                    SpokenLanguage.BENGALI -> it.copy(bengaliVoiceId = voiceId)
                }
            }
        }

        override suspend fun setSpeechRate(rate: Float) {
            _preferences.update { it.copy(speechRate = rate) }
        }

        override suspend fun setPitch(pitch: Float) {
            _preferences.update { it.copy(pitch = pitch) }
        }

        override suspend fun setSpokenResponsesEnabled(enabled: Boolean) {
            _preferences.update { it.copy(spokenResponsesEnabled = enabled) }
        }

        override suspend fun setSetupComplete(complete: Boolean) {
            _preferences.update { it.copy(setupComplete = complete) }
        }
    }

    /** Language is set by the test rather than detected, so no test depends on the real matcher. */
    private class FixedTextNormalizer : TextNormalizer {
        override fun normalize(raw: String, mode: LanguageMode): NormalizedText = NormalizedText(
            raw = raw,
            normalized = raw.trim().lowercase(),
            language = SpokenLanguage.ENGLISH,
            script = TextScript.LATIN,
        )
    }

    private class StubCommandInterpreter : CommandInterpreter {
        var routed = RoutedCommand(EngineResult.NotUnderstood, EngineId.LOCAL)

        /**
         * Keeps interpretation in flight. Without a suspension point here PROCESSING and EXECUTING
         * are set in the same pass and StateFlow conflates them away, so a test could not tell the
         * two apart — which is the whole distinction the orb is drawing.
         */
        var holdUntilReleased = false
        private var gate: CompletableDeferred<Unit>? = null

        override suspend fun route(request: CommandRequest): RoutedCommand {
            if (holdUntilReleased) {
                val pending = CompletableDeferred<Unit>()
                gate = pending
                pending.await()
            }
            return routed
        }

        fun release() {
            gate?.complete(Unit)
            gate = null
        }
    }

    private class PassThroughCommandValidator : CommandValidator {
        override fun validate(command: AppCommand): AppResult<AppCommand> =
            AppResult.Success(command)
    }

    private class RecordingCommandDispatcher : CommandDispatcher {

        val dispatched = mutableListOf<AppCommand>()
        var response: AppResult<MiraResponse> = AppResult.Success(MiraResponse.NotUnderstood(""))

        /** Keeps the command in flight so a test can press the orb while it is running. */
        var holdUntilReleased = false

        private var gate: CompletableDeferred<Unit>? = null

        override suspend fun dispatch(command: AppCommand): AppResult<MiraResponse> {
            if (holdUntilReleased) {
                val pending = CompletableDeferred<Unit>()
                gate = pending
                pending.await()
            }
            dispatched += command
            return response
        }

        fun release() {
            gate?.complete(Unit)
            gate = null
        }
    }
}
