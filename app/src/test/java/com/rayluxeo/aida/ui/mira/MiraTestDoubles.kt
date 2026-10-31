package com.rayluxeo.aida.ui.mira

import kotlinx.coroutines.flow.emptyFlow
import com.rayluxeo.aida.voice.MiraAudioFocusManager
import com.rayluxeo.aida.voice.AudioFocusPurpose
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandDispatcher
import com.rayluxeo.aida.domain.command.CommandEngine
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.CommandValidator
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.nlu.NormalizedText
import com.rayluxeo.aida.domain.nlu.TextNormalizer
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.voice.MiraEarconPlayer
import com.rayluxeo.aida.voice.MiraTextToSpeech
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.SpeakingState
import com.rayluxeo.aida.voice.SpeechFailure
import com.rayluxeo.aida.voice.VoiceConversationController
import com.rayluxeo.aida.voice.VoiceSessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Collaborators for [MiraViewModel], written by hand because Hilt's local-test transform does not
 * run under AGP 9 (google/dagger#5261) and there is no mocking framework on this classpath.
 *
 * They are deliberately thin: the pipeline under test is the real [ExecuteCommandUseCase], so the
 * fakes only have to be truthful about the seams around it.
 */
class FakeConversationRepository : ConversationRepository {

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

/**
 * The spoken turn as the screen sees it: a state the test moves, and three controls that are only
 * ever counted.
 *
 * Counting is the point. The view model must forward a press and decide nothing about it, so a
 * fake that answered presses with state changes of its own would hide exactly the bug — two state
 * machines disagreeing — that moving this logic out of the view model was meant to remove.
 */
class FakeVoiceConversationController : VoiceConversationController {

    private val _state = MutableStateFlow(VoiceSessionState())
    override val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    var presses = 0
        private set
    var cancels = 0
        private set
    var retries = 0
        private set
    var confirms = 0
        private set
    var dismissals = 0
        private set

    override fun onOrbPressed() {
        presses++
    }

    override fun cancel() {
        cancels++
    }

    override fun retryLast() {
        retries++
    }

    override fun confirmPending() {
        confirms++
    }

    override fun dismissPending() {
        dismissals++
    }

    fun emit(session: VoiceSessionState) {
        _state.value = session
    }

    fun idle(canListen: Boolean = true) {
        emit(VoiceSessionState(canListen = canListen))
    }

    fun working() {
        emit(VoiceSessionState(state = MiraVoiceState.PROCESSING, canListen = true))
    }

    fun listening(amplitude: Float = 0f, partial: String? = null) {
        emit(
            VoiceSessionState(
                state = MiraVoiceState.LISTENING,
                amplitude = amplitude,
                partialTranscript = partial,
                canListen = true,
            ),
        )
    }

    fun failed(failure: SpeechFailure, canListen: Boolean = true) {
        emit(
            VoiceSessionState(
                state = MiraVoiceState.ERROR,
                lastFailure = failure,
                canListen = canListen,
            ),
        )
    }
}

class FakeMiraTextToSpeech : MiraTextToSpeech {

    private val _state = MutableStateFlow(SpeakingState.IDLE)
    override val state: StateFlow<SpeakingState> = _state.asStateFlow()

    val spoken = mutableListOf<Pair<String, SpokenLanguage>>()
    var stops = 0
        private set

    /**
     * Holds each utterance open until something stops it, the way a real engine does.
     *
     * A fake that returned the moment it was asked would make every test look as though Mira had
     * already finished talking, which is precisely the state the interesting cases are not in.
     */
    var holdSpeech = false

    private var utterance: CompletableDeferred<Unit>? = null

    override suspend fun canSpeak(language: SpokenLanguage): Boolean = true

    override suspend fun speak(text: String, language: SpokenLanguage): AppResult<Unit> {
        spoken += text to language
        if (holdSpeech) {
            val open = CompletableDeferred<Unit>()
            utterance = open
            _state.value = SpeakingState.SPEAKING
            open.await()
        }
        return AppResult.Success(Unit)
    }

    override fun stop() {
        stops++
        utterance?.complete(Unit)
        utterance = null
        _state.value = SpeakingState.IDLE
    }

    /** The real engine flips this from its utterance callbacks, which a fake has no way to reach. */
    fun speaking(value: Boolean) {
        _state.value = if (value) SpeakingState.SPEAKING else SpeakingState.IDLE
    }
}

/**
 * Counts the two tones the view model is still allowed to be responsible for.
 *
 * [activations] is counted precisely so a test can show it stays at zero: the tone that marks the
 * start of listening belongs to the spoken turn, and the view model no longer has any business
 * playing it.
 */
class FakeEarconPlayer : MiraEarconPlayer {
    var activations = 0
        private set
    var completions = 0
        private set

    override fun activation() {
        activations++
    }

    override fun completion() {
        completions++
    }

    /** Neither of these is the screen's to play, so neither is worth counting. */
    override fun error() = Unit

    /** The player is application-scoped; releasing it is not a screen's decision. */
    override fun release() = Unit
}

/**
 * Records what Mira asked the system for, and hands the block through unchanged.
 *
 * The interesting assertion is that speaking took focus at all: a reply read out over whatever Abu
 * was already listening to is the defect this fake exists to catch.
 */
class FakeAudioFocusManager : MiraAudioFocusManager {

    val requested = mutableListOf<AudioFocusPurpose>()
    val released = mutableListOf<AudioFocusPurpose>()

    override val focusLost: Flow<Unit> = emptyFlow()

    override suspend fun <T> withFocus(purpose: AudioFocusPurpose, block: suspend () -> T): T {
        requested += purpose
        try {
            return block()
        } finally {
            released += purpose
        }
    }
}

class FakeVoicePreferenceRepository(
    initial: VoicePreferences = VoicePreferences.DEFAULT,
) : VoicePreferenceRepository {

    private val _preferences = MutableStateFlow(initial)
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

class FakeSettingsRepository(initial: MiraSettings = MiraSettings()) : SettingsRepository {

    private val _settings = MutableStateFlow(initial)
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

/** Language is set by the test rather than detected, so a test never depends on the real matcher. */
class FakeTextNormalizer(var language: SpokenLanguage = SpokenLanguage.ENGLISH) : TextNormalizer {
    override fun normalize(raw: String, mode: LanguageMode): NormalizedText = NormalizedText(
        raw = raw,
        normalized = raw.trim().lowercase(),
        language = language,
        script = TextScript.LATIN,
    )
}

class FakeCommandEngine(override val id: EngineId) : CommandEngine {
    var available = true
    var result: EngineResult = EngineResult.NotUnderstood
    override suspend fun isAvailable(): Boolean = available
    override suspend fun interpret(request: CommandRequest): EngineResult = result
}

class PassThroughValidator : CommandValidator {
    override fun validate(command: AppCommand): AppResult<AppCommand> = AppResult.Success(command)
}

class RecordingDispatcher : CommandDispatcher {
    val dispatched = mutableListOf<AppCommand>()
    var response: AppResult<MiraResponse> = AppResult.Success(MiraResponse.NotUnderstood(""))

    override suspend fun dispatch(command: AppCommand): AppResult<MiraResponse> {
        dispatched += command
        return response
    }
}
