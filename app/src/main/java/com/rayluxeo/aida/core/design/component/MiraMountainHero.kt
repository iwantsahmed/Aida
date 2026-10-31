package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp as lerpF
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * The gold-lit mountain behind the greeting on Home.
 *
 * DRAWN, not photographed. The obvious way to get a hero like this is to bundle a moody landscape
 * photo, and it is the wrong way three times over for Mira: a photo cannot re-tint when the accent
 * changes, it weighs far more than the few hundred bytes of maths below, and it is one more asset
 * whose licence someone has to keep honest. §3 also rules out a network image outright. So the
 * ridges, the rim light and the atmosphere are all generated here from the palette, which means
 * the hero is offline by construction and moves with the theme rather than being frozen against it.
 *
 * The light is fixed at the upper right — every rim highlight and the atmospheric bloom agree on
 * where the sun is, because a landscape lit from two directions reads as a mistake even to someone
 * who cannot say why. The whole thing fades to obsidian at its base and toward its leading edge, so
 * the greeting laid over it sits on near-black and never has to fight the picture for contrast.
 */
@Composable
fun MiraMountainHero(modifier: Modifier = Modifier) {
    val obsidian = MaterialTheme.colorScheme.background
    val champagne = MaterialTheme.colorScheme.primary
    val champagneDim = MiraTheme.colors.accentDim

    Canvas(modifier = modifier.fillMaxSize()) {
        // The sun: a warm bloom low behind the tallest peak, never a disc. A visible sun would
        // make this an illustration; an unseen source makes it a place.
        val sun = Offset(size.width * SUN_X, size.height * SUN_Y)
        drawCircle(
            brush = Brush.radialGradient(
                0f to champagne.copy(alpha = SUN_CORE_ALPHA),
                SUN_MID_STOP to champagneDim.copy(alpha = SUN_MID_ALPHA),
                1f to Color.Transparent,
                center = sun,
                radius = size.minDimension * SUN_RADIUS,
            ),
            radius = size.minDimension * SUN_RADIUS,
            center = sun,
        )

        // Back to front: each ridge is nearer, darker and less lit than the one behind it, which
        // is the whole of how distance reads on a bare silhouette.
        RIDGES.forEachIndexed { index, ridge ->
            val depth = index.toFloat() / (RIDGES.size - 1)
            drawRidge(
                profile = ridge,
                obsidian = obsidian,
                champagne = champagne,
                champagneDim = champagneDim,
                // Farther ridges catch a touch more warmth in the fill (haze lightens distance)
                // and far more of the rim light; the nearest ridge is almost pure shadow.
                fillWarmth = lerpF(FILL_WARMTH_NEAR, FILL_WARMTH_FAR, 1f - depth),
                rimAlpha = lerpF(RIM_ALPHA_NEAR, RIM_ALPHA_FAR, 1f - depth),
            )
        }

        // The leading edge is wiped back to obsidian so the greeting reads on near-black, and the
        // base is faded out so the picture dissolves into the content below rather than ending on
        // a line.
        drawRect(
            brush = Brush.horizontalGradient(
                0f to obsidian,
                LEADING_CLEAR_STOP to Color.Transparent,
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                BASE_FADE_STOP to Color.Transparent,
                1f to obsidian,
            ),
        )
    }
}

/** Draws one ridge: a filled silhouette down to the base, then a lit line along its crest. */
private fun DrawScope.drawRidge(
    profile: List<Offset>,
    obsidian: Color,
    champagne: Color,
    champagneDim: Color,
    fillWarmth: Float,
    rimAlpha: Float,
) {
    fun point(p: Offset) = Offset(p.x * size.width, p.y * size.height)

    val silhouette = Path().apply {
        val first = point(profile.first())
        moveTo(first.x, first.y)
        profile.drop(1).forEach { lineTo(point(it).x, point(it).y) }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(
        path = silhouette,
        brush = Brush.verticalGradient(
            // The crest carries a breath of warmth; by the base it is obsidian, so the fills stack
            // without a seam where one ridge crosses in front of another.
            0f to lerp(obsidian, champagne, fillWarmth),
            FILL_FADE_STOP to obsidian,
        ),
    )

    val crest = Path().apply {
        val first = point(profile.first())
        moveTo(first.x, first.y)
        profile.drop(1).forEach { lineTo(point(it).x, point(it).y) }
    }
    drawPath(
        path = crest,
        // Dim on the left, bright on the right: light from the upper right, stated once as a
        // horizontal gradient rather than computed slope by slope.
        brush = Brush.horizontalGradient(
            0f to champagneDim.copy(alpha = rimAlpha * RIM_SHADOW_SIDE),
            1f to champagne.copy(alpha = rimAlpha),
        ),
        style = Stroke(width = size.minDimension * RIM_WIDTH, cap = StrokeCap.Round),
    )
}

/**
 * Three crest profiles, back to front, each a normalised polyline: x and y run 0..1 across the
 * hero, y measured from the top. Hand-set rather than random so the skyline is the same every
 * launch — a mountain that reshaped itself each morning would be the opposite of calm — and so the
 * tallest, best-lit peak sits under the sun at the upper right.
 */
private val RIDGES: List<List<Offset>> = listOf(
    listOf(
        Offset(0f, 0.66f), Offset(0.16f, 0.52f), Offset(0.29f, 0.60f), Offset(0.43f, 0.40f),
        Offset(0.55f, 0.50f), Offset(0.67f, 0.28f), Offset(0.79f, 0.22f), Offset(0.90f, 0.34f),
        Offset(1f, 0.27f),
    ),
    listOf(
        Offset(0f, 0.82f), Offset(0.13f, 0.73f), Offset(0.27f, 0.80f), Offset(0.41f, 0.63f),
        Offset(0.53f, 0.72f), Offset(0.64f, 0.50f), Offset(0.76f, 0.46f), Offset(0.87f, 0.57f),
        Offset(1f, 0.50f),
    ),
    listOf(
        Offset(0f, 0.97f), Offset(0.16f, 0.90f), Offset(0.31f, 0.98f), Offset(0.45f, 0.84f),
        Offset(0.57f, 0.91f), Offset(0.69f, 0.72f), Offset(0.81f, 0.70f), Offset(0.91f, 0.81f),
        Offset(1f, 0.75f),
    ),
)

private const val SUN_X = 0.80f
private const val SUN_Y = 0.24f
private const val SUN_RADIUS = 0.85f
private const val SUN_CORE_ALPHA = 0.24f
private const val SUN_MID_ALPHA = 0.10f
private const val SUN_MID_STOP = 0.4f

private const val FILL_WARMTH_NEAR = 0.02f
private const val FILL_WARMTH_FAR = 0.12f
private const val FILL_FADE_STOP = 0.7f

private const val RIM_ALPHA_NEAR = 0.20f
private const val RIM_ALPHA_FAR = 0.55f
private const val RIM_SHADOW_SIDE = 0.25f
private const val RIM_WIDTH = 0.006f

/** The left third is wiped clear for the greeting; the picture lives on the right. */
private const val LEADING_CLEAR_STOP = 0.42f
private const val BASE_FADE_STOP = 0.6f
