package com.rayluxeo.aida.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.repository.VoicePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Distinguishes Mira's replies from the setup screen's samples in a logcat utterance trace. */
private const val UTTERANCE_PREFIX = "mira-reply"

/**
 * The watchdog on a reply: past the time its words could possibly take to say, a silent engine has
 * stopped reporting back and Mira is released rather than left in SPEAKING. Natural speech is
 * roughly fifteen characters a second; the per-character budget is well over double that, and the
 * base covers the engine warming up and audio focus changing hands, so a real reply is never cut.
 */
private const val SPEECH_TIMEOUT_BASE_MS = 5_000L
private const val SPEECH_TIMEOUT_PER_CHAR_MS = 120L

private fun speechTimeoutMs(text: String): Long =
    SPEECH_TIMEOUT_BASE_MS + text.length.toLong() * SPEECH_TIMEOUT_PER_CHAR_MS

/**
 * Mira's voice: one TextToSpeech connection for the whole process, configured from the
 * [VoicePreferences] Abu chose by ear during voice setup.
 *
 * The preferences are observed rather than read once, so picking a voice in setup changes how she
 * sounds on the very next reply without restarting the app. The engine is bound lazily on the
 * first thing she says and rebound only when Abu changes engine — engine connections are expensive
 * and each one holds a bound service, so there is no reason to hold one before she has spoken.
 *
 * Application-scoped on purpose. [MiraTextToSpeech] has no release hook because this connection is
 * meant to live exactly as long as the process does; the short-lived engines that voice setup opens
 * are [VoicePersonaManager]'s, and it releases those itself.
 *
 * Whether Mira speaks at all is the caller's decision. [VoicePreferences.spokenResponsesEnabled] is
 * read at the top of the turn, so this class speaks whenever it is asked to and never silently
 * swallows a reply — two places gating the same setting is how one of them ends up wrong.
 */
@Singleton
class MiraTextToSpeechImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: VoicePreferenceRepository,
    private val formatter: VoiceResponseFormatter,
    private val dispatchers: DispatcherProvider,
) : MiraTextToSpeech {

    private val _state = MutableStateFlow(SpeakingState.IDLE)
    override val state: StateFlow<SpeakingState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val mutex = Mutex()
    private val turns = AtomicLong()

    @Volatile
    private var session: TtsSession? = null

    @Volatile
    private var preferred: VoicePreferences = VoicePreferences.DEFAULT

    /** False until the stored preferences have been read at least once. Guarded by [mutex]. */
    private var loaded = false

    /** The turn that currently owns [state]; a late callback from an older one must not reset it. */
    @Volatile
    private var speaking: Long? = null

    init {
        scope.launch {
            preferences.preferences.distinctUntilChanged().collect { applyPreferences(it) }
        }
    }

    override suspend fun canSpeak(language: SpokenLanguage): Boolean = mutex.withLock {
        val active = ensureSession() ?: return false
        selectVoice(active.tts, language)
    }

    override suspend fun speak(text: String, language: SpokenLanguage): AppResult<Unit> {
        val segments = formatter.segment(text, language)
        if (segments.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Nothing to speak")

        val turn = turns.incrementAndGet()
        // The whole reply is queued under the lock so two replies cannot interleave their
        // segments; only the waiting happens outside it.
        val queued = mutex.withLock {
            val active = ensureSession()
                ?: return failure(MiraErrorCode.ENGINE_UNAVAILABLE, "No text-to-speech engine started")
            val pending = ArrayList<Deferred<AppResult<Unit>>>(segments.size)
            for ((index, segment) in segments.withIndex()) {
                if (!selectVoice(active.tts, segment.language)) {
                    // Abu is told his voice is missing rather than met with silence, and the part
                    // of the reply already queued is dropped so he does not hear half of it.
                    if (pending.isNotEmpty()) active.stop()
                    return failure(
                        MiraErrorCode.ENGINE_UNAVAILABLE,
                        "No ${segment.language.tag} voice is installed",
                    )
                }
                pending += active.enqueue(
                    text = segment.text,
                    utteranceId = "$UTTERANCE_PREFIX-$turn-$index",
                    flush = index == 0,
                )
            }
            pending
        }

        speaking = turn
        _state.value = SpeakingState.SPEAKING
        return try {
            // A watchdog around the wait. Some engines accept a reply and then never report it
            // finished — no audio focus, a half-installed voice, an output they treat as dead — and
            // that single silent utterance would hold Mira in SPEAKING for the rest of the process,
            // leaving the orb and the ask-bar button sitting on "stop" over a screen that is plainly
            // idle. The bound is past what the words could take to say, so a real reply is never cut.
            withTimeoutOrNull(speechTimeoutMs(text)) { awaitSegments(queued, turn) }
                ?: run {
                    if (speaking == turn) stop()
                    failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Speech did not finish in time")
                }
        } catch (cancellation: CancellationException) {
            if (speaking == turn) stop()
            throw cancellation
        } finally {
            if (speaking == turn) {
                speaking = null
                _state.value = SpeakingState.IDLE
            }
        }
    }

    override fun stop() {
        speaking = null
        session?.stop()
        _state.value = SpeakingState.IDLE
    }

    private suspend fun awaitSegments(
        queued: List<Deferred<AppResult<Unit>>>,
        turn: Long,
    ): AppResult<Unit> {
        for (pending in queued) {
            val outcome = pending.await()
            if (outcome is AppResult.Failure) {
                // What is left of this reply is queued behind a segment that never played. Drop
                // it, so the failure is one clear answer rather than a mangled sentence.
                if (speaking == turn) stop()
                return outcome
            }
        }
        return Unit.asSuccess()
    }

    /**
     * Takes a change from voice setup without waiting for the next reply. A bound engine is
     * re-tuned in place; one whose engine package no longer matches is dropped so the next reply
     * binds the engine Abu just chose.
     */
    private suspend fun applyPreferences(updated: VoicePreferences) = mutex.withLock {
        preferred = updated
        loaded = true
        val current = session ?: return@withLock
        if (current.enginePackage != enginePackage(updated)) {
            current.shutdown()
            session = null
        } else {
            tune(current)
        }
    }

    private suspend fun ensureSession(): TtsSession? {
        if (!loaded) {
            // The preference stream may not have delivered yet on the first reply after launch,
            // and Abu's chosen voice must not be missed by a race against it.
            preferred = preferences.current()
            loaded = true
        }
        session?.let { return it }
        val wanted = enginePackage(preferred)
        val bound = bindTts(context, wanted, dispatchers.main)
        // An engine Abu chose can be uninstalled or disabled later. That must not leave Mira
        // mute, so a failed bind falls back to whatever engine the device has as default.
        val tts = bound ?: (if (wanted == null) null else bindTts(context, null, dispatchers.main)) ?: return null
        val created = TtsSession(tts, wanted)
        tune(created)
        session = created
        return created
    }

    private fun enginePackage(from: VoicePreferences): String? = from.engine?.takeIf { it.isNotBlank() }

    private fun tune(session: TtsSession) {
        session.tts.setSpeechRate(preferred.speechRate.coerceIn(VoicePreferences.RATE_RANGE))
        session.tts.setPitch(preferred.pitch.coerceIn(VoicePreferences.PITCH_RANGE))
    }

    /** @return true when the engine is now set up to speak [language]. */
    private fun selectVoice(tts: TextToSpeech, language: SpokenLanguage): Boolean {
        val chosen = pickVoice(tts, language)
        if (chosen != null && tts.setVoice(chosen) == TextToSpeech.SUCCESS) return true
        // A few engines expose no Voice objects at all; the locale path still produces speech.
        return tts.setLanguage(language.locale) >= TextToSpeech.LANG_AVAILABLE
    }

    /**
     * Voices are matched by the opaque identifier Abu's chosen one was stored under, never read.
     * `android.speech.tts.Voice` carries no gender and a name such as `bn-bd-x-ban-local` is an
     * engine-internal code, so nothing here parses one.
     */
    private fun pickVoice(tts: TextToSpeech, language: SpokenLanguage): Voice? {
        val candidates = tts.voices.orEmpty().filter { matchesSpokenLanguage(it.locale, language) }
        preferred.voiceIdFor(language)?.let { stored ->
            candidates.firstOrNull { it.name == stored }?.let { return it }
        }

        // No stored choice, or the stored one is gone. Pick the best usable voice rather than
        // going silent — offline first, then quality, then responsiveness.
        return candidates
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .minWithOrNull(
                compareBy<Voice> { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency },
            )
    }
}
