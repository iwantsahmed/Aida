package com.rayluxeo.aida.ui.ink

import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkStroke
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Which strokes the eraser just went over.
 *
 * Mira erases **whole strokes**, not pixels. Two reasons, and the second is the one that decides
 * it: a drawing kept as strokes has no pixels to rub out, and a stroke that has had a hole cut in
 * it can no longer be read back as one mark by anything — a recogniser, an export at a different
 * size, or Abu's own eye. A letter he no longer wants is one stroke; taking it whole is what he
 * means.
 *
 * The eraser is matched as the **segment it travelled**, not as the point it is at now. A flick
 * across a line moves further between two samples than the nib is wide, and a point test would
 * pass straight through and leave the line standing — which reads as an eraser that does not work
 * rather than as one that was moved too quickly.
 */
internal object InkEraser {

    /**
     * The indices of every stroke within [radiusPx] of the eraser's path from ([fromX], [fromY])
     * to ([toX], [toY]), lowest first.
     *
     * Indices rather than strokes, because that is what undo needs: an erase restores each mark
     * to the position it held, and two identical marks — an `i` dotted twice — cannot be told
     * apart by value.
     */
    fun strokesTouching(
        drawing: InkDrawing,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        radiusPx: Float,
    ): List<Int> = drawing.strokes.indices.filter { index ->
        touches(drawing.strokes[index], fromX, fromY, toX, toY, radiusPx)
    }

    private fun touches(
        stroke: InkStroke,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        radiusPx: Float,
    ): Boolean {
        // Half the nib counts too: a thick mark is hit when the eraser reaches its edge, not only
        // when it reaches the centre line the points describe.
        val reach = radiusPx + stroke.widthPx / 2f
        val points = stroke.points
        if (points.size == 1) {
            val only = points.first()
            return pointToSegment(only.x, only.y, fromX, fromY, toX, toY) <= reach
        }
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            if (segmentToSegment(a.x, a.y, b.x, b.y, fromX, fromY, toX, toY) <= reach) return true
        }
        return false
    }

    /** Zero where the two cross; otherwise how near the four endpoints come to the other line. */
    private fun segmentToSegment(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        dx: Float,
        dy: Float,
    ): Float {
        if (segmentsCross(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return min(
            min(pointToSegment(ax, ay, cx, cy, dx, dy), pointToSegment(bx, by, cx, cy, dx, dy)),
            min(pointToSegment(cx, cy, ax, ay, bx, by), pointToSegment(dx, dy, ax, ay, bx, by)),
        )
    }

    private fun segmentsCross(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        dx: Float,
        dy: Float,
    ): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) &&
            ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
    }

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float =
        (bx - ax) * (py - ay) - (by - ay) * (px - ax)

    private fun pointToSegment(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        val vx = bx - ax
        val vy = by - ay
        val lengthSquared = vx * vx + vy * vy
        val t = if (lengthSquared <= 0f) {
            0f
        } else {
            max(0f, min(1f, ((px - ax) * vx + (py - ay) * vy) / lengthSquared))
        }
        val nearestX = ax + t * vx
        val nearestY = ay + t * vy
        val dx = px - nearestX
        val dy = py - nearestY
        return sqrt(dx * dx + dy * dy)
    }
}
