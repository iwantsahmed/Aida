package com.rayluxeo.aida.voice

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Mira's three tones, synthesised here from arithmetic.
 *
 * These are original: a rising fifth when she starts listening, one lower note when she is done,
 * and the same fifth falling when she could not do it. Nothing is sampled from, modelled on, or
 * transcribed from Siri, Alexa, Google Assistant, Bixby or any other assistant. There are no audio
 * assets in this app for exactly that reason.
 *
 * A sine fundamental alone sounds thin on a phone speaker, so a quiet second harmonic is mixed
 * in for body. The envelope starts and ends at true silence — a tone that begins or ends on a
 * non-zero sample produces an audible click, which is the difference between a considered sound
 * and a cheap one.
 */
internal object MiraTones {

    const val SAMPLE_RATE_HZ = 44_100

    /** D5 up to A5: a rising fifth, warm rather than chirpy, and over before Abu starts talking. */
    val activation: ShortArray by lazy {
        render(
            listOf(
                Tone(frequencyHz = 587.33, durationMs = 120, peak = 0.55),
                Tone(frequencyHz = 880.00, durationMs = 160, peak = 0.50),
            ),
        )
    }

    /** One softer D5 on its own, so a finished turn settles instead of announcing itself. */
    val completion: ShortArray by lazy {
        render(listOf(Tone(frequencyHz = 587.33, durationMs = 240, peak = 0.34)))
    }

    /**
     * A4 down to D4: the activation interval inverted, an octave lower, and no louder than
     * [completion].
     *
     * A falling interval is heard as "that did not land" without any of the dissonance or bite
     * that would make it a reprimand — Mira reports that she could not do something, she does not
     * scold Abu for asking. The lower register and the two notes are what tell it apart from the
     * single settled note of [completion] even across a room.
     */
    val error: ShortArray by lazy {
        render(
            listOf(
                Tone(frequencyHz = 440.00, durationMs = 130, peak = 0.32),
                Tone(frequencyHz = 293.66, durationMs = 240, peak = 0.30),
            ),
        )
    }

    private const val TWO_PI = 2.0 * PI

    /** Fraction of a note spent ramping in. Long enough to avoid a click, short enough to feel instant. */
    private const val ATTACK = 0.08

    private const val DECAY_RATE = 3.0

    /** Second-harmonic level relative to the fundamental. Body, not brightness. */
    private const val HARMONIC_WEIGHT = 0.18

    private data class Tone(val frequencyHz: Double, val durationMs: Int, val peak: Double)

    private fun render(tones: List<Tone>): ShortArray {
        val counts = tones.map { it.durationMs * SAMPLE_RATE_HZ / 1000 }
        val pcm = ShortArray(counts.sum())
        var offset = 0
        tones.forEachIndexed { index, tone ->
            val count = counts[index]
            for (sample in 0 until count) {
                val phase = TWO_PI * tone.frequencyHz * sample / SAMPLE_RATE_HZ
                val wave = (sin(phase) + HARMONIC_WEIGHT * sin(2.0 * phase)) / (1.0 + HARMONIC_WEIGHT)
                val level = wave * envelope(sample.toDouble() / count) * tone.peak
                pcm[offset + sample] = (level * Short.MAX_VALUE).toInt().toShort()
            }
            offset += count
        }
        return pcm
    }

    /**
     * Linear attack, then an exponential decay multiplied by a linear taper so the note reaches
     * exactly zero at its end rather than being cut off mid-cycle.
     */
    private fun envelope(position: Double): Double = when {
        position < ATTACK -> position / ATTACK
        else -> {
            val decayed = (position - ATTACK) / (1.0 - ATTACK)
            exp(-DECAY_RATE * decayed) * (1.0 - decayed)
        }
    }
}
