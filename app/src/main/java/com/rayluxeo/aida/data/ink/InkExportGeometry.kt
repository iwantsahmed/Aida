package com.rayluxeo.aida.data.ink

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How large an exported page is allowed to be, and by how much it must shrink to get there.
 *
 * Kept apart from the rasterizer, and kept as arithmetic on four numbers, because it is the part
 * of exporting that decides whether Abu's phone survives a very long page — and the part that can
 * be tested without a device, a bitmap or a stylus.
 *
 * The ceiling exists because the bitmap is allocated whole: an ARGB_8888 page costs four bytes a
 * pixel, so the eight-megapixel budget below is a transient 32 MB allocation at worst. A canvas
 * the size of an S24 Ultra screen — 1440 by 3120, four and a half megapixels — is under it and is
 * exported at exactly the size it was written at, which is the case that matters; only a page far
 * beyond any real screen is scaled, and it is scaled rather than refused, because an export Abu
 * cannot get at all is worse than one slightly smaller than he drew.
 */
internal object InkExportGeometry {

    /**
     * The widest a single edge may be. Beyond this many pixels an image stops being reliably
     * openable elsewhere — it is the texture limit most viewers and decoders are built around —
     * so an export past it would be a file Abu cannot use.
     */
    const val MAX_EDGE_PX = 8_192

    /** Four bytes a pixel: this budget is a 32 MB bitmap while the PNG is being written. */
    const val MAX_PIXELS = 8_000_000L

    /**
     * The size to allocate for a page of [widthPx] by [heightPx], which the caller has already
     * established are positive.
     *
     * Rounds down, so the result is never a pixel over either budget, and never below one pixel in
     * either direction: a page one pixel wide and twenty thousand tall scales to a width that
     * would otherwise floor to nothing at all.
     */
    fun fit(widthPx: Int, heightPx: Int): Size {
        val longestEdge = max(widthPx, heightPx)
        val pixels = widthPx.toLong() * heightPx.toLong()
        val edgeScale = if (longestEdge <= MAX_EDGE_PX) 1.0 else MAX_EDGE_PX.toDouble() / longestEdge
        val areaScale = if (pixels <= MAX_PIXELS) 1.0 else sqrt(MAX_PIXELS.toDouble() / pixels)
        val scale = min(edgeScale, areaScale)
        // A page that already fits is returned untouched rather than multiplied by one: the page
        // Abu actually draws on takes this path, and it must come out at the size he wrote at.
        if (scale >= 1.0) return Size(widthPx = widthPx, heightPx = heightPx, scale = 1f)
        return Size(
            widthPx = floor(widthPx * scale).toInt().coerceIn(1, MAX_EDGE_PX),
            heightPx = floor(heightPx * scale).toInt().coerceIn(1, MAX_EDGE_PX),
            scale = scale.toFloat(),
        )
    }

    /**
     * [scale] is applied to the canvas rather than to the strokes, so stroke widths shrink with the
     * geometry and a scaled export reads as the same handwriting rather than as a thicker pen.
     */
    data class Size(
        val widthPx: Int,
        val heightPx: Int,
        val scale: Float,
    )
}
