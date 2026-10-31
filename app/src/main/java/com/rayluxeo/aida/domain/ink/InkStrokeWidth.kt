package com.rayluxeo.aida.domain.ink

/**
 * How wide a mark is at one point of a stroke, given how hard Abu was pressing there.
 *
 * **One function, in `domain`, called by both sides of the feature.** The canvas draws the live
 * stroke with it and the rasterizer draws the exported PNG with it. They each carried their own
 * copy once, tuned apart — 0.45..1.35 of the nib on screen against 0.35..1.00 in the file — so
 * every image Abu exported came out roughly a quarter thinner than the page he had written, and
 * nothing on screen told him. He would have found out when someone else opened the file. That is
 * the defect AGENTS.md §7.12 exists to forbid: one grammar, one source of truth.
 *
 * **The curve is the canvas's, not the exporter's.** The canvas is the surface Abu writes on, so
 * it is the reference: an export is derived from the page and has to match it, never the other way
 * round. Retuning the export to a narrower range would have made the picture agree with the file
 * and disagree with the hand that wrote it.
 *
 * A digitiser with no pressure sensor reports [InkPoint.FULL_PRESSURE] for every sample, so a
 * finger-drawn page is an even line at the heaviest width rather than a page that varies at
 * random — deliberate, and the same on screen and in the file.
 *
 * @param nominalWidthPx the nib's own width: [InkStroke.widthPx] for a stroke already laid down,
 *   and the live nib width while one is still being drawn.
 * @param pressure what the digitiser reported, normalised to 0..1. A reading outside that range is
 *   clamped, and one that is not a number at all counts as full pressure — a NaN width draws
 *   nothing at all, and a stroke Abu made must never come out as nothing.
 */
fun strokeWidthAt(nominalWidthPx: Float, pressure: Float): Float {
    val reported = if (pressure.isFinite()) pressure else InkPoint.FULL_PRESSURE
    val span = HEAVIEST_FACTOR - LIGHTEST_FACTOR
    return nominalWidthPx * (LIGHTEST_FACTOR + span * reported.coerceIn(0f, 1f))
}

/** The lightest a mark gets. Never zero: a stroke barely touched should still be a stroke. */
private const val LIGHTEST_FACTOR = 0.45f

/** The heaviest. Just over the nominal nib, so full pressure reads as deliberate weight. */
private const val HEAVIEST_FACTOR = 1.35f
