package com.rayluxeo.aida.domain.ink

/**
 * What Abu drew, as data.
 *
 * Deliberately not a bitmap. A page of handwriting kept as pixels cannot be undone, re-coloured,
 * re-exported at a different size, or fed to a recogniser; kept as strokes it can be all four, and
 * the exported image becomes something Mira derives rather than the only copy she has.
 *
 * Coordinates are in the page's own pixel space, which is recorded on [InkDrawing]. A stroke laid
 * down on the S24 Ultra and read back on a narrower screen has to scale as a whole, so the size it
 * was drawn at travels with it — without that, replaying strokes on a different display silently
 * rearranges the writing.
 *
 * `Float` is correct here and is not the rule §8 is about: that rule forbids `Float` for MONEY,
 * where a rounding error is a wrong number Abu would act on. A coordinate is a measurement.
 */
data class InkDrawing(
    val widthPx: Int,
    val heightPx: Int,
    val strokes: List<InkStroke>,
) {
    val isEmpty: Boolean get() = strokes.isEmpty()

    companion object {
        /** An empty page of a given size — what a fresh canvas starts from. */
        fun blank(widthPx: Int, heightPx: Int): InkDrawing = InkDrawing(widthPx, heightPx, emptyList())
    }
}

/**
 * One continuous mark: pen down, move, pen up.
 *
 * [widthPx] is the nominal width of the nib. What actually gets drawn varies along the stroke with
 * each point's pressure, which is the whole reason pressure is carried per point rather than per
 * stroke.
 */
data class InkStroke(
    val widthPx: Float,
    val points: List<InkPoint>,
) {
    init {
        require(points.isNotEmpty()) { "A stroke with no points is not a stroke" }
    }
}

/**
 * One sample from the digitiser.
 *
 * [pressure] is what the platform reported, normalised to 0..1. A device with no pressure sensor
 * reports 1.0 for every sample, which is why a flat stroke is a valid stroke and not a bug — the
 * canvas must look deliberate on a phone with a finger and on an S Pen alike.
 *
 * [tiltRadians] is null when the digitiser did not say. Absent rather than zero: zero tilt means
 * the pen is held upright, which is a real and different claim from "unknown".
 *
 * [elapsedMillis] is measured from the first sample of the drawing, never from the wall clock —
 * a stored drawing must replay identically whatever time it is read back at, and `MiraClock`
 * exists precisely so no part of Mira reaches for the clock on its own (§4).
 */
data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = FULL_PRESSURE,
    val tiltRadians: Float? = null,
    val elapsedMillis: Long = 0L,
) {
    companion object {
        /** What a digitiser with no pressure sensor reports, and the default a finger gets. */
        const val FULL_PRESSURE = 1f
    }
}

/**
 * Which implement is in Abu's hand.
 *
 * [ERASER] is here because the S Pen reports it natively — flipping the pen, or holding its
 * button, arrives as `MotionEvent.TOOL_TYPE_ERASER` — so Mira must honour the hardware rather than
 * making him find a button on screen. It is a *mode*, not something that is stored: erasing
 * removes strokes, it does not add white ones (see [InkEdit.Erase]).
 */
enum class InkTool { PEN, ERASER }

/**
 * One undoable change to a drawing.
 *
 * Undo is a stack of these rather than a stack of whole drawings. A page of handwriting is tens of
 * thousands of points, and keeping a copy of all of it per undo step is how a notes app starts
 * dropping strokes on a long page.
 *
 * [Erase] carries every stroke it took, with the position each sat at, because erasing across a
 * crossing removes several at once and undo has to put them all back where they were — not append
 * them to the end, which would reorder what is drawn on top of what.
 */
sealed interface InkEdit {

    data class Add(val stroke: InkStroke) : InkEdit

    data class Erase(val removed: List<IndexedStroke>) : InkEdit

    /** A stroke together with the index it occupied, so undo restores order and not just content. */
    data class IndexedStroke(val index: Int, val stroke: InkStroke)
}
