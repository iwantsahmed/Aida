package com.rayluxeo.aida.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A broken or missing engine must never hold the setup screen hostage. */
private const val ENGINE_INIT_TIMEOUT_MS = 5_000L

/**
 * Connects to one TTS engine and waits for it to report itself ready.
 *
 * @param enginePackage the engine's package name, or null for whichever engine the device
 *   has set as default.
 * @param main the dispatcher the engine is constructed on. TextToSpeech binds a service, and
 *   engines behave far more predictably when that happens from the main thread.
 * @return a live instance, or null if the engine failed to initialise or never answered. A
 *   failed instance is shut down before returning, so the caller never inherits a dead binding.
 */
internal suspend fun bindTts(
    context: Context,
    enginePackage: String?,
    main: CoroutineDispatcher,
): TextToSpeech? {
    val latch = TtsInitLatch()
    val tts = withContext(main) {
        if (enginePackage.isNullOrBlank()) {
            TextToSpeech(context, latch)
        } else {
            TextToSpeech(context, latch, enginePackage)
        }
    }
    val status = try {
        withTimeoutOrNull(ENGINE_INIT_TIMEOUT_MS) { latch.await() }
    } catch (cancellation: CancellationException) {
        tts.shutdown()
        throw cancellation
    }
    if (status == TextToSpeech.SUCCESS) return tts
    tts.shutdown()
    return null
}

/**
 * Mira's replies play as media, so her loudness follows the media stream — the one the volume rocker
 * moves while she is open (see `MainActivity.volumeControlStream`). `USAGE_ASSISTANT` routed her to a
 * stream Abu's rocker did not reach on his phone, leaving her voice at a level he could not change;
 * media is the volume he means by "the volume". `CONTENT_TYPE_SPEECH` keeps the system treating it as
 * speech, and ducking of other audio still comes from the speaking audio-focus request, not from here,
 * so she continues to yield to a phone call. (Abu asked her to follow the device volume, 17 Oct 2026.)
 */
private fun speechAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/**
 * A live engine plus the one progress listener it is allowed to have.
 *
 * [enginePackage] is the package that was *asked* for rather than the one that answered, so a
 * caller can tell whether a rebind is needed without re-querying the engine.
 */
internal class TtsSession(
    val tts: TextToSpeech,
    val enginePackage: String?,
) {
    private val utterances = TtsUtterances(tts)

    init {
        tts.setAudioAttributes(speechAttributes())
    }

    /**
     * Puts one utterance into the engine's queue and hands back the handle that settles when the
     * engine is finished with it.
     *
     * Queueing and waiting are separate steps because a reply made of several segments has to go
     * into the queue back to back. Awaiting each segment before queueing the next drains the queue
     * between them, and the gap is audible as a stutter in the middle of a sentence.
     *
     * @param flush true for the first segment of a reply, which clears whatever Mira was saying
     *   before, and false for every segment after it. Flushing each segment in turn would leave
     *   only the last one audible — a silent fault that sounds exactly like a truncated answer.
     */
    fun enqueue(text: String, utteranceId: String, flush: Boolean): Deferred<AppResult<Unit>> =
        utterances.enqueue(text, utteranceId, flush)

    /** Speaks one utterance on its own, clearing the queue, and suspends until it is finished. */
    suspend fun speak(text: String, utteranceId: String): AppResult<Unit> {
        val pending = enqueue(text, utteranceId, flush = true)
        return try {
            pending.await()
        } catch (cancellation: CancellationException) {
            stop()
            throw cancellation
        }
    }

    fun stop() {
        tts.stop()
        // After the engine has been told to stop, nobody may still be waiting on it: some engines
        // never call back for utterances they discarded from the queue, and a coroutine suspended
        // on one of those would hold Mira in SPEAKING for the rest of the process.
        utterances.releaseAll()
    }

    fun shutdown() {
        // stop() first so any coroutine still waiting on an utterance is released rather than
        // left suspended against an engine that is about to disappear.
        stop()
        tts.shutdown()
    }
}

/**
 * TextToSpeech allows exactly one progress listener per instance, so speaking twice would
 * otherwise replace the listener and strand the coroutine waiting on the first utterance.
 * One listener per engine, dispatching by utterance id, keeps every waiter accounted for.
 */
internal class TtsUtterances(private val tts: TextToSpeech) : UtteranceProgressListener() {

    private val waiting = ConcurrentHashMap<String, CompletableDeferred<AppResult<Unit>>>()

    fun enqueue(text: String, utteranceId: String, flush: Boolean): Deferred<AppResult<Unit>> {
        val pending = CompletableDeferred<AppResult<Unit>>()
        // Registered before the engine is asked to speak, because a fast engine can report the
        // utterance done from its own thread before speak() has returned.
        waiting[utteranceId] = pending
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (tts.speak(text, queueMode, null, utteranceId) != TextToSpeech.SUCCESS) {
            settle(utteranceId, failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Engine refused the utterance"))
        }
        return pending
    }

    /** Releases every outstanding waiter. Only correct after the engine has been stopped. */
    fun releaseAll() {
        waiting.keys.toList().forEach { settle(it, Unit.asSuccess()) }
    }

    override fun onStart(utteranceId: String?) = Unit

    override fun onDone(utteranceId: String?) = settle(utteranceId, Unit.asSuccess())

    @Deprecated("Replaced by onError(String, Int), but older engines still call this one.")
    override fun onError(utteranceId: String?) =
        settle(utteranceId, failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Synthesis failed"))

    override fun onError(utteranceId: String?, errorCode: Int) =
        settle(utteranceId, failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Synthesis failed, code $errorCode"))

    /** A flushed utterance was replaced by a newer reply. That is intended, not a failure. */
    override fun onStop(utteranceId: String?, interrupted: Boolean) =
        settle(utteranceId, Unit.asSuccess())

    private fun settle(utteranceId: String?, result: AppResult<Unit>) {
        // remove() is atomic, so exactly one of onDone/onError/onStop can settle a given waiter.
        val pending = utteranceId?.let { waiting.remove(it) } ?: return
        pending.complete(result)
    }
}

/**
 * TextToSpeech can report readiness from an engine-owned thread before its own constructor has
 * returned, so the status is latched and handed to whoever asks for it afterwards rather than
 * being pushed straight into a continuation that may not exist yet.
 */
private class TtsInitLatch : TextToSpeech.OnInitListener {

    private val lock = Any()
    private var status: Int? = null
    private var waiter: CancellableContinuation<Int>? = null

    override fun onInit(status: Int) {
        val pending = synchronized(lock) {
            this.status = status
            waiter.also { waiter = null }
        }
        pending?.resume(status)
    }

    suspend fun await(): Int = suspendCancellableCoroutine { continuation ->
        val known = synchronized(lock) {
            status.also { if (it == null) waiter = continuation }
        }
        if (known != null) {
            continuation.resume(known)
        } else {
            continuation.invokeOnCancellation {
                synchronized(lock) { if (waiter === continuation) waiter = null }
            }
        }
    }
}
