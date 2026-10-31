package com.rayluxeo.aida.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.repository.VoicePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Voice discovery and auditioning for the Voice Setup screen.
 *
 * ## Mira's voice is chosen by ear
 *
 * `android.speech.tts.Voice` exposes **no gender field** — not in the object, not in its
 * features, nowhere in the public API. Voice names such as `bn-bd-x-ban-local` are opaque
 * engine identifiers with no published format; the middle token is an internal voice-bank code
 * that differs per engine and changes on engine updates. So Mira never infers gender, never
 * labels a voice with one, and never sorts or groups by one. Abu plays the samples and picks the
 * voice he wants. If a future change adds a "female voices" filter or a guessed gender badge, it
 * will be wrong on a real device — do not add one.
 *
 * ## One engine bound at a time, and every one of them released
 *
 * Listing the voices of a particular engine means actually connecting to that engine: there is no
 * way to ask a TTS engine what it offers without binding it and waiting for `onInit`. That wait
 * is bounded — see `bindTts` — because an engine that never answers must not leave the setup
 * screen spinning.
 *
 * Enumeration is serialised through a mutex and only one audition engine is held open at a time,
 * because every live `TextToSpeech` holds a bound service and several at once is a known source
 * of ANRs and audio-focus fights. Every instance this class creates is recorded in [bound] until
 * it is shut down, so [release] can close the ones created for a single engine listing as well as
 * the current one. A `TextToSpeech` that is dropped without `shutdown()` keeps its service bound
 * for the life of the process.
 */
@Singleton
class VoicePersonaManagerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : VoicePersonaManager {

    private val mutex = Mutex()
    private val previewCounter = AtomicLong()

    /**
     * Guards [audition] and [bound]. A plain lock rather than [mutex] because [release] is not a
     * suspend function and must still be able to take every live engine away atomically.
     */
    private val lock = Any()
    private val bound = mutableListOf<TtsSession>()
    private var audition: TtsSession? = null

    /**
     * Requires the `<queries>` manifest entry for `android.intent.action.TTS_SERVICE`. Without
     * it this list silently shrinks to the default engine — no exception, just a wrong answer.
     */
    override suspend fun engines(): List<TtsEngineInfo> {
        return mutex.withLock {
            // getEngines() is device-wide, so whichever instance is bound can answer it.
            val session = currentAudition() ?: auditionSession(null) ?: return emptyList()
            session.tts.engines.orEmpty()
                .filter { !it.name.isNullOrBlank() }
                .map { engine ->
                    TtsEngineInfo(
                        packageName = engine.name,
                        label = engine.label?.takeIf { it.isNotBlank() } ?: engine.name,
                    )
                }
                .sortedBy { it.label.lowercase(Locale.ROOT) }
        }
    }

    override suspend fun voices(enginePackage: String?, language: SpokenLanguage): List<TtsVoiceInfo> {
        return mutex.withLock {
            val session = auditionSession(enginePackage) ?: return emptyList()
            val spoken = session.tts.voices.orEmpty().filter { matchesSpokenLanguage(it.locale, language) }
            describe(spoken, language)
        }
    }

    override suspend fun preview(
        enginePackage: String?,
        voiceName: String,
        language: SpokenLanguage,
        rate: Float,
        pitch: Float,
    ): AppResult<Unit> {
        val session = mutex.withLock {
            val active = auditionSession(enginePackage)
                ?: return failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Engine ${enginePackage ?: "default"} did not start")
            val chosen = active.tts.voices.orEmpty().firstOrNull { it.name == voiceName }
                ?: return failure(MiraErrorCode.NOT_FOUND, "Engine does not offer voice $voiceName")
            if (active.tts.setVoice(chosen) != TextToSpeech.SUCCESS) {
                return failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Engine refused voice $voiceName")
            }
            active.tts.setSpeechRate(rate.coerceIn(VoicePreferences.RATE_RANGE))
            active.tts.setPitch(pitch.coerceIn(VoicePreferences.PITCH_RANGE))
            active
        }
        // Outside the lock, so tapping a second sample interrupts the first instead of queueing.
        return session.speak(sample(language), "mira-preview-${previewCounter.incrementAndGet()}")
    }

    /**
     * Drops every engine this class opened. Call it when voice setup closes, once enumeration and
     * previews are done — a TextToSpeech left bound holds a service for the life of the process.
     *
     * Safe to call twice, and safe to call before the manager has bound anything. Auditioning
     * afterwards simply binds again.
     */
    override fun release() {
        val dropped = synchronized(lock) {
            audition = null
            bound.toList().also { bound.clear() }
        }
        dropped.forEach { it.shutdown() }
    }

    private fun currentAudition(): TtsSession? = synchronized(lock) { audition }

    /**
     * Returns an engine bound to [enginePackage], reusing the open one when it is already the
     * right engine. Switching engines closes the previous one first, so listing four engines'
     * voices in turn leaves one connection behind rather than four.
     */
    private suspend fun auditionSession(enginePackage: String?): TtsSession? {
        val wanted = enginePackage?.takeIf { it.isNotBlank() }
        currentAudition()?.let { if (it.enginePackage == wanted) return it }
        closeAudition()
        // bindTts shuts the instance down itself when the engine fails or never reports back, so
        // a null here has left nothing bound.
        val tts = bindTts(context, wanted, dispatchers.main) ?: return null
        // No suspension point between the bind and the record, so cancellation cannot strand it.
        return TtsSession(tts, wanted).also { session ->
            synchronized(lock) {
                audition = session
                bound += session
            }
        }
    }

    private fun closeAudition() {
        val previous = synchronized(lock) {
            val current = audition
            audition = null
            if (current != null) bound -= current
            current
        }
        previous?.shutdown()
    }

    /**
     * Ordered by what actually matters offline: a voice whose data is still pending download, or
     * that needs the radio, is no use to Mira on a plane, so those sink to the bottom.
     */
    private fun describe(voices: List<Voice>, language: SpokenLanguage): List<TtsVoiceInfo> {
        val locales = voices.associate { it.name to it.locale }
        val labels = voiceLabels(voices.map { it.name }, language) { locales[it] }
        return voices
            .map { candidate ->
                TtsVoiceInfo(
                    name = candidate.name,
                    localeTag = candidate.locale.toLanguageTag(),
                    displayLabel = labels[candidate.name] ?: candidate.name,
                    quality = candidate.quality,
                    latency = candidate.latency,
                    requiresNetwork = candidate.isNetworkConnectionRequired,
                    isInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in candidate.features.orEmpty(),
                )
            }
            .sortedWith(
                compareByDescending<TtsVoiceInfo> { it.isInstalled }
                    .thenBy { it.requiresNetwork }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.name },
            )
    }

    /** Short on purpose: Abu is judging a voice, not sitting through a speech. */
    private fun sample(language: SpokenLanguage): String = when (language) {
        SpokenLanguage.BENGALI -> "আমি এভাবেই কথা বলি, আবু। বলুন, আমি শুনছি।"
        SpokenLanguage.ENGLISH -> "This is how I sound, Abu. Tell me what you need."
    }
}
