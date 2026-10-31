package com.rayluxeo.aida.domain.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rule that decides how heavy a mark is, tested in numbers rather than in pixels.
 *
 * It is here because it is the one piece of arithmetic in this feature that two layers both need
 * the same answer from, and it went wrong exactly that way: the canvas and the exporter each had
 * their own copy and the exported page came out about a quarter thinner than the one Abu wrote.
 * The tolerances are tight on purpose — a drift of a few percent is invisible in one image and
 * obvious when the image is laid beside the screen.
 */
class InkStrokeWidthTest {

    @Test
    fun `the lightest touch still leaves a mark, at under half the nib`() {
        assertThat(strokeWidthAt(nominalWidthPx = 10f, pressure = 0f)).isWithin(TOLERANCE).of(4.5f)
    }

    @Test
    fun `full pressure is the heaviest the nib goes, a little over its nominal width`() {
        assertThat(strokeWidthAt(nominalWidthPx = 10f, pressure = 1f)).isWithin(TOLERANCE).of(13.5f)
    }

    @Test
    fun `half pressure lands halfway between the two, not at half the width`() {
        assertThat(strokeWidthAt(nominalWidthPx = 10f, pressure = 0.5f)).isWithin(TOLERANCE).of(9f)
    }

    @Test
    fun `a digitiser reading outside nought to one is clamped rather than run off the end`() {
        assertThat(strokeWidthAt(10f, -2f)).isWithin(TOLERANCE).of(4.5f)
        assertThat(strokeWidthAt(10f, 9f)).isWithin(TOLERANCE).of(13.5f)
    }

    @Test
    fun `a pressure that is not a number at all draws at full pressure, never at nothing`() {
        val notANumber = strokeWidthAt(nominalWidthPx = 10f, pressure = Float.NaN)

        assertThat(notANumber).isFinite()
        assertThat(notANumber).isWithin(TOLERANCE).of(13.5f)
    }

    @Test
    fun `a phone with no pressure sensor gets one even weight down the whole stroke`() {
        val sensorless = listOf(InkPoint(1f, 1f), InkPoint(2f, 2f), InkPoint(3f, 3f))

        val widths = sensorless.map { strokeWidthAt(nominalWidthPx = 5f, pressure = it.pressure) }

        assertThat(widths.toSet()).hasSize(1)
        assertThat(widths.first()).isWithin(TOLERANCE).of(6.75f)
    }

    @Test
    fun `pressing harder never draws thinner`() {
        val widths = (0..20).map { strokeWidthAt(nominalWidthPx = 3.5f, pressure = it / 20f) }

        assertThat(widths).isInStrictOrder()
        assertThat(widths.first()).isGreaterThan(0f)
    }

    @Test
    fun `the width scales with the nib it was given`() {
        assertThat(strokeWidthAt(nominalWidthPx = 8f, pressure = 0.3f))
            .isWithin(TOLERANCE)
            .of(strokeWidthAt(nominalWidthPx = 4f, pressure = 0.3f) * 2f)
    }

    private companion object {
        /** Far tighter than the drift between the two copies that made this rule worth sharing. */
        const val TOLERANCE = 0.0001f
    }
}
