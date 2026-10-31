package com.rayluxeo.aida.core.design

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline

/**
 * How a raised surface catches light on a black screen.
 *
 * Mira has no shadows — §10 forbids them as decoration, and on a true-black AMOLED panel a shadow
 * is invisible anyway: there is nothing darker to cast onto. So depth has to come from the other
 * direction. A real object under a light picks up a bright edge along its top and falls away
 * toward its bottom, and reproducing just that is enough to make a card read as lying ON the
 * background rather than being painted INTO it.
 *
 * Two parts, both barely there and both doing a job:
 *
 * - a **vertical wash** across the fill, a couple of percent lighter at the top, which gives the
 *   surface a direction;
 * - a **specular edge**, one hairline of light along the upper border only, fading out by the
 *   halfway point. This is the part that does the work. A border drawn evenly all the way round
 *   reads as an outline — a drawn box. A border that is bright at the top and gone by the bottom
 *   reads as an edge catching a light that is somewhere above the phone.
 *
 * Deliberately not glassmorphism, which §10 also forbids: nothing here blurs what is behind it,
 * and the effect survives being screenshotted onto a white document.
 */
fun Modifier.miraRaised(
    shape: Shape,
    colors: MiraExtendedColors,
    fill: Color,
    strength: Float = 1f,
): Modifier = this
    .background(
        brush = Brush.verticalGradient(
            listOf(fill.lighten(TOP_LIFT * strength), fill, fill.lighten(-BOTTOM_SINK * strength)),
        ),
        shape = shape,
    )
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        // Stops at the halfway point rather than fading to the bottom: an edge lit from above has
        // no business glinting on its underside, and carrying it further is what turns a lit edge
        // back into an outline.
        val specular = Brush.linearGradient(
            0f to colors.hairlineStrong.scaleAlpha(strength),
            SPECULAR_FADE to colors.hairline.scaleAlpha(strength * HALFWAY_ALPHA),
            1f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(0f, size.height),
        )
        onDrawBehind {
            drawOutline(outline = outline, brush = specular, style = HairlineStroke)
        }
    }

private val HairlineStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)

/** A couple of percent, no more. Past that the wash becomes a gradient Abu can see. */
private const val TOP_LIFT = 0.035f
private const val BOTTOM_SINK = 0.02f
private const val SPECULAR_FADE = 0.5f
private const val HALFWAY_ALPHA = 0.35f

private fun Color.lighten(by: Float): Color = if (by >= 0f) {
    Color(
        red = red + (1f - red) * by,
        green = green + (1f - green) * by,
        blue = blue + (1f - blue) * by,
        alpha = alpha,
    )
} else {
    Color(red = red * (1f + by), green = green * (1f + by), blue = blue * (1f + by), alpha = alpha)
}

private fun Color.scaleAlpha(by: Float): Color = copy(alpha = (alpha * by).coerceIn(0f, 1f))
