package com.rayluxeo.aida.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays Mira's activation, completion and error tones.
 *
 * Each tone is pre-rendered once by [MiraTones] and loaded into a static [AudioTrack] buffer, so
 * replaying one is a rewind rather than a fresh allocation — it has to be instant, because the
 * activation tone is the acknowledgement that Mira heard the tap at all. The buffers are built on
 * first use rather than at construction, so a session that never plays a tone never pays for one.
 *
 * The tones are original by construction: they are arithmetic in [MiraTones], there is no audio
 * file in this project, and nothing here imitates Siri, Alexa, Google Assistant or Bixby.
 *
 * They are routed as USAGE_ASSISTANCE_SONIFICATION: a UI acknowledgement, not media. Nothing here
 * takes audio focus, because a tone lasts a third of a second and is never worth pausing what Abu
 * is listening to — [MiraAudioFocusManager] offers [AudioFocusPurpose.EARCON] for a caller that
 * wants the duck anyway, and it is deliberately not taken here.
 */
@Singleton
class MiraEarconPlayerImpl @Inject constructor() : MiraEarconPlayer {

    private val lock = Any()
    private var activationTrack: AudioTrack? = null
    private var completionTrack: AudioTrack? = null
    private var errorTrack: AudioTrack? = null

    override fun activation() {
        synchronized(lock) {
            activationTrack = play(activationTrack, MiraTones.activation)
        }
    }

    override fun completion() {
        synchronized(lock) {
            completionTrack = play(completionTrack, MiraTones.completion)
        }
    }

    override fun error() {
        synchronized(lock) {
            errorTrack = play(errorTrack, MiraTones.error)
        }
    }

    override fun release() {
        synchronized(lock) {
            listOfNotNull(activationTrack, completionTrack, errorTrack).forEach { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) track.stop()
                track.release()
            }
            activationTrack = null
            completionTrack = null
            errorTrack = null
        }
    }

    /** @return the track to keep, or null when the device would not give us one. */
    private fun play(existing: AudioTrack?, pcm: ShortArray): AudioTrack? {
        val track = existing ?: load(pcm) ?: return null
        if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
        track.reloadStaticData()
        track.play()
        return track
    }

    private fun load(pcm: ShortArray): AudioTrack? {
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(MiraTones.SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (unsupported: UnsupportedOperationException) {
            // No audio output the device will accept these parameters on. A missing tone is a
            // small loss; a crash on the way into a listening session is not acceptable.
            return null
        }

        if (track.state != AudioTrack.STATE_NO_STATIC_DATA) {
            track.release()
            return null
        }
        track.write(pcm, 0, pcm.size)
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        return track
    }
}
