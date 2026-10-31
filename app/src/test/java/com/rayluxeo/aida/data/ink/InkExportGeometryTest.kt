package com.rayluxeo.aida.data.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The export's only defence against a page big enough to take the app down with it. Worth testing
 * on its own because it is arithmetic, and because the cases that matter — a page far beyond any
 * screen, a page one pixel wide — are ones nobody will draw by hand to check.
 */
class InkExportGeometryTest {

    @Test
    fun `a page the size of the phone is exported exactly as it was written`() {
        val fitted = InkExportGeometry.fit(widthPx = 1440, heightPx = 3120)

        assertThat(fitted.widthPx).isEqualTo(1440)
        assertThat(fitted.heightPx).isEqualTo(3120)
        assertThat(fitted.scale).isEqualTo(1f)
    }

    @Test
    fun `a page too large to hold in memory is brought inside the budget`() {
        val fitted = InkExportGeometry.fit(widthPx = 20_000, heightPx = 30_000)

        assertThat(fitted.widthPx.toLong() * fitted.heightPx).isAtMost(InkExportGeometry.MAX_PIXELS)
        assertThat(fitted.widthPx).isAtMost(InkExportGeometry.MAX_EDGE_PX)
        assertThat(fitted.heightPx).isAtMost(InkExportGeometry.MAX_EDGE_PX)
        assertThat(fitted.scale).isLessThan(1f)
    }

    @Test
    fun `shrinking keeps the proportions of the handwriting`() {
        val fitted = InkExportGeometry.fit(widthPx = 20_000, heightPx = 30_000)

        val ratio = fitted.widthPx.toFloat() / fitted.heightPx.toFloat()
        assertThat(ratio).isWithin(0.001f).of(20_000f / 30_000f)
    }

    @Test
    fun `a page wider than any decoder will open is clamped by its long edge`() {
        val fitted = InkExportGeometry.fit(widthPx = 30_000, heightPx = 100)

        assertThat(fitted.widthPx).isEqualTo(InkExportGeometry.MAX_EDGE_PX)
        assertThat(fitted.heightPx).isAtLeast(1)
        assertThat(fitted.scale).isWithin(0.0001f).of(InkExportGeometry.MAX_EDGE_PX / 30_000f)
    }

    @Test
    fun `a hairline page keeps a pixel of width rather than scaling away to nothing`() {
        val fitted = InkExportGeometry.fit(widthPx = 1, heightPx = 20_000)

        assertThat(fitted.widthPx).isEqualTo(1)
        assertThat(fitted.heightPx).isEqualTo(InkExportGeometry.MAX_EDGE_PX)
    }

    @Test
    fun `the largest page that still fits is not scaled at all`() {
        val fitted = InkExportGeometry.fit(widthPx = 2_000, heightPx = 4_000)

        assertThat(fitted.scale).isEqualTo(1f)
        assertThat(fitted.widthPx).isEqualTo(2_000)
        assertThat(fitted.heightPx).isEqualTo(4_000)
    }
}
