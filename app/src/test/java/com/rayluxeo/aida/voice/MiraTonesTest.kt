package com.rayluxeo.aida.voice

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

/**
 * The tones are pure arithmetic, so the qualities that make them sound considered rather than
 * cheap — silence at both ends, an actual rising interval, headroom below clipping — can be
 * measured here instead of being judged by ear on a device.
 */
class MiraTonesTest {

    @Test
    fun `activation is a short two-note phrase`() {
        assertThat(MiraTones.activation.size).isEqualTo(FIRST_NOTE + SECOND_NOTE)
        assertThat(seconds(MiraTones.activation)).isLessThan(0.4)
    }

    @Test
    fun `activation begins and ends in silence so it cannot click`() {
        assertThat(MiraTones.activation.first().toInt()).isEqualTo(0)
        assertThat(MiraTones.activation.last().toInt()).isEqualTo(0)
    }

    @Test
    fun `activation rises by a fifth`() {
        val lower = estimateHz(MiraTones.activation, 500, 4_000)
        val upper = estimateHz(MiraTones.activation, FIRST_NOTE + 500, FIRST_NOTE + 4_000)

        assertThat(lower).isWithin(30.0).of(587.33)
        assertThat(upper).isWithin(45.0).of(880.0)
        assertThat(upper / lower).isWithin(0.1).of(1.5)
    }

    @Test
    fun `activation is loud enough to hear and quiet enough not to clip`() {
        val peak = MiraTones.activation.maxOf { abs(it.toInt()) }

        assertThat(peak).isGreaterThan(8_000)
        assertThat(peak).isLessThan(30_000)
    }

    @Test
    fun `completion is one softer note that also starts and ends silent`() {
        val completion = MiraTones.completion

        assertThat(completion.first().toInt()).isEqualTo(0)
        assertThat(completion.last().toInt()).isEqualTo(0)
        assertThat(seconds(completion)).isWithin(0.01).of(0.24)
        assertThat(completion.maxOf { abs(it.toInt()) })
            .isLessThan(MiraTones.activation.maxOf { abs(it.toInt()) })
        // One note, so one steady pitch throughout rather than a step part way.
        assertThat(estimateHz(completion, 500, 4_000)).isWithin(30.0).of(587.33)
    }

    @Test
    fun `error is a short two-note phrase that also starts and ends silent`() {
        val error = MiraTones.error

        assertThat(error.size).isEqualTo(ERROR_FIRST_NOTE + ERROR_SECOND_NOTE)
        assertThat(seconds(error)).isLessThan(0.4)
        assertThat(error.first().toInt()).isEqualTo(0)
        assertThat(error.last().toInt()).isEqualTo(0)
    }

    @Test
    fun `error falls by the fifth activation rises by`() {
        val upper = estimateHz(MiraTones.error, 500, 4_000)
        val lower = estimateHz(MiraTones.error, ERROR_FIRST_NOTE + 500, ERROR_FIRST_NOTE + 4_000)

        assertThat(upper).isWithin(30.0).of(440.0)
        assertThat(lower).isWithin(30.0).of(293.66)
        // Falling, where activation rises: the direction is the whole message.
        assertThat(upper / lower).isWithin(0.1).of(1.5)
    }

    @Test
    fun `error is audible but never louder than completion, because it does not scold`() {
        val peak = MiraTones.error.maxOf { abs(it.toInt()) }

        assertThat(peak).isGreaterThan(6_000)
        assertThat(peak).isLessThan(MiraTones.completion.maxOf { abs(it.toInt()) })
    }

    @Test
    fun `error cannot be mistaken for completion`() {
        // Two notes against one, and a lower register, so they are told apart across a room.
        assertThat(MiraTones.error.size).isNotEqualTo(MiraTones.completion.size)
        assertThat(estimateHz(MiraTones.error, 500, 4_000))
            .isLessThan(estimateHz(MiraTones.completion, 500, 4_000))
    }

    private fun seconds(pcm: ShortArray): Double = pcm.size.toDouble() / MiraTones.SAMPLE_RATE_HZ

    /** Frequency from zero crossings, skipping samples that quantised to silence in the decay. */
    private fun estimateHz(pcm: ShortArray, from: Int, until: Int): Double {
        var crossings = 0
        var lastSign = 0
        for (index in from until until) {
            val sign = when {
                pcm[index] > 0 -> 1
                pcm[index] < 0 -> -1
                else -> 0
            }
            if (sign == 0) continue
            if (lastSign != 0 && sign != lastSign) crossings++
            lastSign = sign
        }
        return crossings / 2.0 / ((until - from).toDouble() / MiraTones.SAMPLE_RATE_HZ)
    }

    private companion object {
        const val FIRST_NOTE = 120 * MiraTones.SAMPLE_RATE_HZ / 1000
        const val SECOND_NOTE = 160 * MiraTones.SAMPLE_RATE_HZ / 1000
        const val ERROR_FIRST_NOTE = 130 * MiraTones.SAMPLE_RATE_HZ / 1000
        const val ERROR_SECOND_NOTE = 240 * MiraTones.SAMPLE_RATE_HZ / 1000
    }
}
