package com.rayluxeo.aida.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.SpokenLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A recognition service that never answers must not stall the setup screen. */
private const val SUPPORT_CHECK_TIMEOUT_MS = 3_000L

/**
 * Mira's listening side, over the platform [SpeechRecognizer].
 *
 * On-device recognition is preferred whenever a model for the language is actually installed,
 * because that is the only structural guarantee that nothing leaves the phone —
 * `EXTRA_PREFER_OFFLINE` is advisory and engines are free to ignore it. When no on-device model
 * can serve the language, the networked recognizer is used instead rather than failing.
 *
 * Nothing about the device is assumed. Language detection, mid-utterance switching and the very
 * ability to enumerate installed models are all optional parts of the speech API, and every one of
 * them is asked about at runtime. When the answer is no — or when there is no way to ask — Mira
 * listens anyway with whatever the device does offer, because an optional extra going missing must
 * never be the reason the microphone stops working.
 *
 * Every call runs on the main thread: `SpeechRecognizer` documents that its methods "must be
 * invoked only from the main application thread", and violating it fails in ways that look like
 * a flaky microphone.
 */
@Singleton
class SpeechRecognitionManagerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : SpeechRecognitionManager {

    override fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) ||
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Guards [cachedOnDeviceLanguages] against two presses probing the same language at once. */
    private val onDeviceProbe = Mutex()
    private val cachedOnDeviceLanguages = mutableMapOf<SpokenLanguage, Boolean>()

    override suspend fun capabilities(): RecognitionCapabilities {
        if (!isAvailable()) return RecognitionCapabilities.NONE
        return RecognitionCapabilities(
            onDeviceAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context),
            supportedLanguages = recognisableLanguages(),
            supportsLanguageDetection = canRequestLanguageDetection(),
            supportsLanguageSwitching = canRequestLanguageSwitching(),
        )
    }

    override fun listen(request: RecognitionRequest): Flow<SpeechEvent> = callbackFlow {
        val language = resolveRecognitionLanguage(request.mode)
        val wantsOnDevice = request.preferOnDevice && onDeviceServes(language)
        val session = openRecognizer(wantsOnDevice)
        if (session == null) {
            trySend(SpeechEvent.Failed(SpeechFailure.UNAVAILABLE))
            close()
            awaitClose()
            return@callbackFlow
        }

        // The recognizer delivers every callback on the thread it was created on, which is this
        // one, so this is only ever written and read from the main thread.
        var detectedTag: String? = null

        session.recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(SpeechEvent.Ready)
                }

                override fun onBeginningOfSpeech() {
                    trySend(SpeechEvent.BeginningOfSpeech)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    trySend(SpeechEvent.Level(normaliseMicLevel(rmsdB)))
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    trySend(SpeechEvent.EndOfSpeech)
                }

                override fun onError(error: Int) {
                    trySend(SpeechEvent.Failed(speechFailureOf(error)))
                    close()
                }

                override fun onLanguageDetection(results: Bundle) {
                    languageTagIn(results)?.let { detectedTag = it }
                }

                override fun onResults(results: Bundle?) {
                    val hypotheses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val index = topHypothesisIndex(hypotheses)
                    val best = hypotheses?.getOrNull(index)?.trim()
                    if (results == null || best.isNullOrEmpty()) {
                        trySend(SpeechEvent.Failed(SpeechFailure.NO_MATCH))
                    } else {
                        trySend(
                            SpeechEvent.Final(
                                text = best,
                                // Whatever the device reported, else the language it was asked
                                // for; null under AUTO on a device that cannot say, where the
                                // normalizer reads the script for itself.
                                languageTag = languageTagIn(results) ?: detectedTag ?: language?.tag,
                                confidence = confidenceAt(
                                    results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES),
                                    index,
                                ),
                            ),
                        )
                    }
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val hypotheses =
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    hypotheses?.getOrNull(topHypothesisIndex(hypotheses))
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { trySend(SpeechEvent.Partial(it)) }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        session.recognizer.startListening(listenIntent(request, language, session.onDevice))

        // A recognizer that is not destroyed keeps the microphone, and the next session then
        // fails with ERROR_RECOGNIZER_BUSY.
        awaitClose { session.recognizer.destroy() }
    }.flowOn(dispatchers.main)

    private fun listenIntent(
        request: RecognitionRequest,
        language: SpokenLanguage?,
        onDevice: Boolean,
    ): Intent = recognizerIntent(language, preferOffline = onDevice).apply {
        val silenceTimeoutMs = request.silenceTimeout?.toMillis() ?: 3000L
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            silenceTimeoutMs,
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            maxOf(2500L, silenceTimeoutMs - 500L),
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
            1500L,
        )
        // Only under AUTO. With a language named, asking the recognizer to detect one as well
        // would let it answer in a language Abu has just said he does not want.
        if (language == null) allowEitherLanguage()
    }

    /**
     * Asks the recognizer to work out which of Abu's two languages he is speaking, and to stay
     * within them.
     *
     * Both extras are additive: a service that ignores them produces exactly the free-form request
     * it would have received anyway, which still listens. That is why nothing downstream depends
     * on either of them being honoured.
     */
    private fun Intent.allowEitherLanguage() {
        if (canRequestLanguageDetection()) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            putStringArrayListExtra(
                RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                ArrayList(RECOGNITION_LANGUAGE_TAGS),
            )
        }
        if (canRequestLanguageSwitching()) {
            // Balanced rather than quick: Abu's sentences carry English words inside Bengali ones,
            // and a recognizer that switches at the first foreign word mangles the rest of them.
            putExtra(
                RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                RecognizerIntent.LANGUAGE_SWITCH_BALANCED,
            )
            putStringArrayListExtra(
                RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                ArrayList(RECOGNITION_LANGUAGE_TAGS),
            )
        }
    }

    /**
     * Whether the platform will carry a language-detection request at all: Android 13 added the
     * detection extras, Android 14 the switching ones.
     *
     * This is an upper bound and it is deliberately the honest one available. No API exposes
     * whether the installed recognition service honours the extras, and the only way to find out
     * is to send them and watch, so the answer is used solely to decide whether attaching them is
     * worth doing — never whether Mira listens.
     */
    private fun canRequestLanguageDetection(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isAvailable()

    private fun canRequestLanguageSwitching(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && canRequestLanguageDetection()

    /**
     * The language the recognizer says it actually heard.
     *
     * Both keys are read because engines disagree about which one they fill: `DETECTED_LANGUAGE`
     * is the documented one, `EXTRA_LANGUAGE` is what several put in the results bundle instead.
     * A null means the device did not say, which is not the same as English.
     */
    private fun languageTagIn(bundle: Bundle): String? {
        val tag = bundle.getString(SpeechRecognizer.DETECTED_LANGUAGE)
            ?: bundle.getString(RecognizerIntent.EXTRA_LANGUAGE)
        return tag?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun recognisableLanguages(): Set<SpokenLanguage> {
        // checkRecognitionSupport arrived in API 33. On 31-32 there is no way to enumerate models
        // at all, and calling a language unsupported because the question could not be asked would
        // take the microphone away for nothing: assume both, and let startListening tell the truth
        // through ERROR_LANGUAGE_NOT_SUPPORTED.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return RECOGNITION_LANGUAGES

        val reported = withContext(dispatchers.main) {
            reportedLanguages(onDevice = true) + reportedLanguages(onDevice = false)
        }
        // A service that refused to answer, or timed out, has not said no either.
        return if (reported.isEmpty()) RECOGNITION_LANGUAGES else supportedLanguagesOf(reported)
    }

    /** What one recognizer can serve today: installed on-device models plus its online languages. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun reportedLanguages(onDevice: Boolean): List<String> {
        val recognizer = (if (onDevice) createOnDevice() else createNetworked()) ?: return emptyList()
        return try {
            val support = querySupport(recognizer, recognizerIntent(null, preferOffline = onDevice))
            // Installed, not supported: getSupportedOnDeviceLanguages() lists languages that still
            // need downloading, and a language Abu cannot use today is not one Mira can offer.
            support?.let { it.installedOnDeviceLanguages + it.onlineLanguages }.orEmpty()
        } finally {
            recognizer.destroy()
        }
    }

    private suspend fun onDeviceServes(language: SpokenLanguage?): Boolean {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        // For AUTO mode or Bengali, verify that on-device recognition actually has Bengali installed.
        // If not installed on-device, return false so openRecognizer falls back to networked
        // recognition (Google Speech Services), which fully supports Bengali and Banglish.
        val target = language ?: SpokenLanguage.BENGALI

        // Answered once per language and then remembered. The check builds a throwaway recognizer
        // and waits on the service, and it sits directly in front of the microphone opening — so
        // asking it on every press put that delay between Abu's tap and Mira hearing him. A model
        // is installed or removed from a system screen, which restarts nothing here, so the worst
        // a stale answer costs is one session on the wrong recognizer.
        cachedOnDeviceLanguages[target]?.let { return it }
        return onDeviceProbe.withLock {
            cachedOnDeviceLanguages[target] ?: withContext(dispatchers.main) {
                installedOnDeviceLanguages(target).any { matchesSpokenLanguage(it, target) }
            }.also { cachedOnDeviceLanguages[target] = it }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun installedOnDeviceLanguages(language: SpokenLanguage): List<String> {
        val recognizer = createOnDevice() ?: return emptyList()
        return try {
            querySupport(recognizer, recognizerIntent(language, preferOffline = true))
                ?.installedOnDeviceLanguages
                .orEmpty()
        } finally {
            recognizer.destroy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun querySupport(
        recognizer: SpeechRecognizer,
        intent: Intent,
    ): RecognitionSupport? = withTimeoutOrNull(SUPPORT_CHECK_TIMEOUT_MS) {
        suspendCancellableCoroutine<RecognitionSupport?> { continuation ->
            recognizer.checkRecognitionSupport(
                intent,
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        if (continuation.isActive) continuation.resume(recognitionSupport)
                    }

                    override fun onError(error: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }
    }

    private fun recognizerIntent(language: SpokenLanguage?, preferOffline: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Partials reach the screen while Abu is still speaking, so the waveform has words
            // under it rather than a wait.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            val langTag = language?.tag ?: SpokenLanguage.BENGALI.tag
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            if (language == null) {
                val languages = arrayListOf("bn-BD", "en-US")
                putStringArrayListExtra("android.speech.extra.ADDITIONAL_LANGUAGES", languages)
                putStringArrayListExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", languages)
                putExtra("android.speech.extra.ADDITIONAL_LANGUAGES", arrayOf("bn-BD", "en-US"))
            }
        }

    private fun openRecognizer(preferOnDevice: Boolean): RecognizerSession? {
        val first = if (preferOnDevice) createOnDevice() else createNetworked()
        if (first != null) return RecognizerSession(first, preferOnDevice)
        val second = if (preferOnDevice) createNetworked() else createOnDevice()
        return second?.let { RecognizerSession(it, !preferOnDevice) }
    }

    private fun createOnDevice(): SpeechRecognizer? {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return null
        return try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } catch (unsupported: UnsupportedOperationException) {
            // The service can be disabled between the availability check and this call.
            null
        }
    }

    private fun createNetworked(): SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }

    /** Which recognizer actually opened, so the intent can be built to match it. */
    private class RecognizerSession(val recognizer: SpeechRecognizer, val onDevice: Boolean)
}
