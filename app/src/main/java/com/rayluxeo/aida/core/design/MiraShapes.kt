package com.rayluxeo.aida.core.design

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A rounded rectangle whose corners have **continuous curvature**.
 *
 * Android's `RoundedCornerShape` draws a quarter-circle, so the curvature jumps from zero along
 * the straight edge to 1/r the instant the arc begins. The eye reads that discontinuity even when
 * it cannot name it, and it is most of why a stock Android card looks like a box with rounded
 * corners while an iOS one looks like a single drawn object. A continuous corner ramps the
 * curvature in over a longer run of the edge, so there is no seam to see.
 *
 * Drawn here as one cubic per corner rather than by sampling a superellipse: a curve of four
 * segments costs nothing to rasterise, can be reused for clipping and shadows, and at the radii
 * Mira uses is indistinguishable from the real thing.
 *
 * The corner needs [SMOOTHING] times its radius of straight edge to develop in. When a side is
 * too short to give it that — a small chip, a narrow field — the run is scaled down to what fits,
 * because a corner that overshoots the midpoint of an edge turns the rectangle into a lens.
 */
class ContinuousRoundedCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(radius: Dp) : this(
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
    )

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (maxOf(topStart, topEnd, bottomEnd, bottomStart) <= 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val (ts, te) = if (layoutDirection == LayoutDirection.Ltr) {
            topStart to topEnd
        } else {
            topEnd to topStart
        }
        val (bs, be) = if (layoutDirection == LayoutDirection.Ltr) {
            bottomStart to bottomEnd
        } else {
            bottomEnd to bottomStart
        }

        // Never more than half of the shorter side: past that the corners meet and the shape
        // stops being a rectangle at all.
        val cap = size.minDimension / 2f
        fun run(r: Float) = minOf(r * SMOOTHING, cap)

        val tsRun = run(ts)
        val teRun = run(te)
        val beRun = run(be)
        val bsRun = run(bs)

        val path = Path().apply {
            moveTo(tsRun, 0f)
            lineTo(size.width - teRun, 0f)
            cubic(size.width - teRun * PULL, 0f, size.width, teRun * PULL, size.width, teRun)
            lineTo(size.width, size.height - beRun)
            cubic(
                size.width, size.height - beRun * PULL,
                size.width - beRun * PULL, size.height,
                size.width - beRun, size.height,
            )
            lineTo(bsRun, size.height)
            cubic(bsRun * PULL, size.height, 0f, size.height - bsRun * PULL, 0f, size.height - bsRun)
            lineTo(0f, tsRun)
            cubic(0f, tsRun * PULL, tsRun * PULL, 0f, tsRun, 0f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = ContinuousRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    private companion object {
        /**
         * How far along each edge the corner reaches, as a multiple of the radius.
         *
         * 1.0 would be a plain quarter-circle. Apple's corners sit near 1.5; past about 1.6 the
         * shape starts to look inflated rather than smooth.
         */
        const val SMOOTHING = 1.5f

        /**
         * How close the control points sit to the true corner, along the run.
         *
         * This is the number that decides whether the curve reads as continuous. Lower and the
         * corner flattens into a chamfer; higher and it tightens back into the circular arc this
         * class exists to avoid.
         */
        const val PULL = 0.32f
    }
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)

/** Named so the four corners below read as corners rather than as twelve loose numbers. */
private fun Path.cubic(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
    cubicTo(c1x, c1y, c2x, c2y, x, y)

/**
 * Mira's corner radii. Generous rather than sharp: on a black ground a softened edge reads as a
 * considered object, where a hard corner reads as an unstyled box.
 *
 * Every one is continuous — see [ContinuousRoundedCornerShape]. A single circular corner left
 * among them is visible as soon as it sits beside one of these.
 */
internal val MiraShapes = Shapes(
    extraSmall = ContinuousRoundedCornerShape(10.dp),
    small = ContinuousRoundedCornerShape(14.dp),
    medium = ContinuousRoundedCornerShape(20.dp),
    large = ContinuousRoundedCornerShape(28.dp),
    extraLarge = ContinuousRoundedCornerShape(36.dp),
)
