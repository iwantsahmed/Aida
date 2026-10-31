package com.rayluxeo.aida.voice

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration

// ------------------------------------------------------------------ listening

/**
 * What the recognizer installed on this particular phone can actually do.
 *
 * Every one of these is discovered at runtime, never assumed. Android's speech APIs are a
 * contract with whichever recognition service the OEM shipped, and the useful parts — on-device
 * models, language detection, mid-utterance switching — are all optional. Mira asks, and when the
 * answer is no she does without rather than failing.
 */
data class RecognitionCapabilities(
    val onDeviceAvailable: Boolean,
    /** The subset of {bn-BD, en-US} this device can actually recognise. May be empty. */
    val supportedLanguages: Set<SpokenLanguage>,
    /** ACTION_GET_LANGUAGE_DETECTION. Absent on most devices; never required. */
    val supportsLanguageDetection: Boolean,
    /** EXTRA_ENABLE_LANGUAGE_SWITCH. Absent on most devices; never required. */
    val supportsLanguageSwitching: Boolean,
) {
    companion object {
        val NONE = RecognitionCapabilities(false, emptySet(), false, false)
    }
}

data class RecognitionRequest(
    val mode: LanguageMode,
    /**
     * On-device keeps Abu's voice on the phone, which is the whole point. It is a preference and
     * not a guarantee: when no on-device model exists for the language, falling back is better
     * than refusing to listen.
     */
    val preferOnDevice: Boolean = true,
    /** How long a pause ends the utterance. Null leaves it to the recognizer's own judgement. */
    val silenceTimeout: Duration? = null,
)

sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data object BeginningOfSpeech : SpeechEvent

    /** Normalised 0f..1f microphone level, for the orb and the waveform. */
    data class Level(val rms: Float) : SpeechEvent

    data class Partial(val text: String) : SpeechEvent

    /**
     * [confidence] is -1f when the recognizer declined to score the result, which many do.
     * Treat an absent score as "no opinion", never as low confidence.
     */
    data class Final(
        val text: String,
        val languageTag: String?,
        val confidence: Float,
    ) : SpeechEvent

    data object EndOfSpeech : SpeechEvent

    data class Failed(val reason: SpeechFailure) : SpeechEvent
}

enum class SpeechFailure {
    NO_MATCH, TIMEOUT, PERMISSION, NETWORK, BUSY, UNAVAILABLE, LANGUAGE_UNAVAILABLE, UNKNOWN;

    /** True when trying the same thing again could plausibly work. */
    val isRetryable: Boolean
        get() = this == NO_MATCH || this == TIMEOUT || this == NETWORK || this == BUSY
}

/**
 * Mira's listening side.
 *
 * [listen] returns a cold flow per session: collecting starts the microphone and cancelling the
 * collection releases it. There is no stop() because a leaked recognizer holds the microphone
 * open, and tying the lifetime to the collection is the only way to be sure it is released.
 */
interface SpeechRecognitionManager {
    fun isAvailable(): Boolean
    fun hasPermission(): Boolean
    suspend fun capabilities(): RecognitionCapabilities
    fun listen(request: RecognitionRequest): Flow<SpeechEvent>
}

// ------------------------------------------------------------------ speaking

enum class SpeakingState { IDLE, SPEAKING }

/** One stretch of a reply that should be spoken by one voice. */
data class SpokenSegment(val text: String, val language: SpokenLanguage)

/**
 * Decides which voice says what.
 *
 * Mira's Bengali replies carry English words — names, "call", "reminder" — and switching voice
 * for each one makes her sound like two people arguing. The rule is that a segment has to be
 * worth a switch: a run of the other language is only broken out when it is long enough to be a
 * clause of its own, and anything shorter is spoken in the sentence's own voice.
 */
interface VoiceResponseFormatter {
    fun segment(text: String, primary: SpokenLanguage): List<SpokenSegment>
}

/** Mira's speaking side. Always the voice Abu chose by ear, never one picked by name. */
interface MiraTextToSpeech {
    val state: StateFlow<SpeakingState>

    /** True when a usable voice exists for [language] under the stored preferences. */
    suspend fun canSpeak(language: SpokenLanguage): Boolean

    /**
     * Speaks the whole reply, segmenting it first so a mixed-language sentence is not chopped
     * between voices. Returns once the last segment has finished or been stopped.
     */
    suspend fun speak(text: String, language: SpokenLanguage): AppResult<Unit>

    /** Stops immediately. Safe to call when nothing is speaking. */
    fun stop()
}

// ------------------------------------------------------------------ choosing the voice

data class TtsEngineInfo(val packageName: String, val label: String)

/**
 * A device voice exactly as the engine reports it.
 *
 * Gender is deliberately absent: `android.speech.tts.Voice` does not expose it, and guessing it
 * from a voice name is forbidden. Abu listens to the samples and picks.
 */
data class TtsVoiceInfo(
    val name: String,
    val localeTag: String,
    val displayLabel: String,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean,
    val isInstalled: Boolean,
)

/** Drives voice discovery and auditioning during setup. */
interface VoicePersonaManager {
    suspend fun engines(): List<TtsEngineInfo>
    suspend fun voices(enginePackage: String?, language: SpokenLanguage): List<TtsVoiceInfo>

    /** Speaks a short sample in [language] with the given voice and settings. */
    suspend fun preview(
        enginePackage: String?,
        voiceName: String,
        language: SpokenLanguage,
        rate: Float,
        pitch: Float,
    ): AppResult<Unit>

    fun release()
}

// ------------------------------------------------------------------ the audio itself

/** Why Mira wants the audio output or input right now. */
enum class AudioFocusPurpose {
    /** Transient exclusive: whatever else is playing should pause, not duck. */
    LISTENING,

    /** Transient may-duck: a podcast can stay on underneath. */
    SPEAKING,

    /** A moment long. Never worth interrupting anything for. */
    EARCON,
}

/**
 * Asks Android for the audio before Mira uses it, and gives it back afterwards.
 *
 * Without this Mira talks over music and the recognizer hears it. [focusLost] is the other half:
 * when something else takes the audio — a call, an alarm — Mira must stop speaking rather than
 * compete with it.
 */
interface MiraAudioFocusManager {
    suspend fun <T> withFocus(purpose: AudioFocusPurpose, block: suspend () -> T): T
    val focusLost: Flow<Unit>
}

/**
 * Mira's own two sounds, synthesised rather than sampled.
 *
 * They are original by construction: there is no audio file in this project to have copied one
 * into, and nothing here imitates Siri, Alexa, Google Assistant or Bixby.
 */
interface MiraEarconPlayer {
    fun activation()
    fun completion()
    fun error()
    fun release()
}
