package com.rayluxeo.aida.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The grant id standing for "no focus was taken", so there is nothing to give back. */
private const val NO_GRANT = 0L

/**
 * Takes the audio from the rest of the phone for exactly as long as Mira is using it.
 *
 * The whole value of this class is in the `finally`: focus that is requested and never abandoned
 * leaves every other app on the phone ducked or paused indefinitely, and the block is cancelled
 * routinely — Abu taps the orb again, a call arrives, the screen goes. Abandoning is therefore a
 * plain synchronized call rather than anything that suspends, because a `finally` that suspends in
 * a cancelled coroutine never reaches the line that matters.
 *
 * Focus is never a precondition. A refused request means Mira is polite about the audio, not that
 * she stops listening to Abu: [withFocus] runs the block either way. The one thing she does react
 * to is losing focus mid-turn, which [focusLost] reports so she can stop talking underneath a call
 * rather than competing with it.
 */
@Singleton
class MiraAudioFocusManagerImpl @Inject constructor(
    @param:ApplicationContext context: Context,
) : MiraAudioFocusManager {

    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)

    /**
     * No replay: a loss that happened before anyone was listening is history, and re-delivering it
     * to a later collector would stop a turn that had nothing to do with it.
     */
    private val lostEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val focusLost: Flow<Unit> = lostEvents.asSharedFlow()

    private val callbackHandler = Handler(Looper.getMainLooper())

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (AudioFocusPolicy.isLoss(change)) lostEvents.tryEmit(Unit)
    }

    private val lock = Any()

    /** The one outstanding request, or null when Mira holds no focus. */
    private var held: Grant? = null
    private var lastGrantId = NO_GRANT

    override suspend fun <T> withFocus(purpose: AudioFocusPurpose, block: suspend () -> T): T {
        val grantId = acquire(purpose)
        return try {
            block()
        } finally {
            release(grantId)
        }
    }

    /** @return the id to hand back to [release], or [NO_GRANT] when no focus was taken. */
    private fun acquire(purpose: AudioFocusPurpose): Long {
        val manager = audioManager ?: return NO_GRANT
        return synchronized(lock) {
            // A tone is a moment long, so it never displaces a turn that is already under way.
            if (AudioFocusPolicy.proceedsWithoutFocus(purpose) && held != null) {
                return@synchronized NO_GRANT
            }

            // Mira does one thing at a time, so a new purpose replaces the old one outright. This
            // is also what stops a listening session that is being cancelled from abandoning the
            // focus the speaking that replaced it has just taken.
            held?.let { manager.abandonAudioFocusRequest(it.request) }
            held = null

            val request = buildRequest(purpose)
            if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return@synchronized NO_GRANT
            }
            val id = lastGrantId + 1
            lastGrantId = id
            held = Grant(id, request)
            id
        }
    }

    private fun release(grantId: Long) {
        if (grantId == NO_GRANT) return
        val manager = audioManager ?: return
        synchronized(lock) {
            val current = held ?: return
            // A newer purpose already took over; abandoning now would silence Mira mid-sentence.
            if (current.id != grantId) return
            manager.abandonAudioFocusRequest(current.request)
            held = null
        }
    }

    private fun buildRequest(purpose: AudioFocusPurpose): AudioFocusRequest =
        AudioFocusRequest.Builder(AudioFocusPolicy.gainFor(purpose))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioFocusPolicy.usageFor(purpose))
                    .setContentType(AudioFocusPolicy.contentTypeFor(purpose))
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener, callbackHandler)
            .build()

    private class Grant(val id: Long, val request: AudioFocusRequest)
}

/**
 * What Mira asks the audio system for, and what she does with the answer.
 *
 * Kept as plain arithmetic over the platform constants so the decisions that actually matter —
 * that listening pauses other audio rather than ducking it, and that a duckable loss is not a
 * reason to stop — can be asserted in a local unit test. [MiraAudioFocusManagerImpl] itself needs
 * a real [AudioManager] and cannot be.
 */
internal object AudioFocusPolicy {

    /**
     * Listening is exclusive on purpose: a podcast ducked to a murmur under an open microphone is
     * still what the recognizer hears, so music has to pause rather than drop in volume.
     */
    fun gainFor(purpose: AudioFocusPurpose): Int = when (purpose) {
        AudioFocusPurpose.LISTENING -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
        AudioFocusPurpose.SPEAKING -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        AudioFocusPurpose.EARCON -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    }

    fun usageFor(purpose: AudioFocusPurpose): Int = when (purpose) {
        AudioFocusPurpose.LISTENING, AudioFocusPurpose.SPEAKING -> AudioAttributes.USAGE_ASSISTANT
        AudioFocusPurpose.EARCON -> AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
    }

    fun contentTypeFor(purpose: AudioFocusPurpose): Int = when (purpose) {
        AudioFocusPurpose.LISTENING, AudioFocusPurpose.SPEAKING -> AudioAttributes.CONTENT_TYPE_SPEECH
        AudioFocusPurpose.EARCON -> AudioAttributes.CONTENT_TYPE_SONIFICATION
    }

    /** True when the work is worth doing even if the request is refused. */
    fun proceedsWithoutFocus(purpose: AudioFocusPurpose): Boolean =
        purpose == AudioFocusPurpose.EARCON

    /**
     * True only for the losses Mira must stop for. A duckable loss means something short is
     * playing over her — a notification — and going quiet for it would lose the rest of the reply.
     */
    fun isLoss(focusChange: Int): Boolean =
        focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
}
