package com.rayluxeo.aida.core.design.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * The placeholder a screen shows while its content loads — the shape of what is coming, shimmering
 * gently, instead of a spinner on an empty screen.
 *
 * A spinner says only "wait"; a skeleton says "a list is coming, this long, in these rows", so the
 * screen does not jump when the data lands and the wait feels shorter than it is. The shimmer is a
 * single pale band travelling across graphite, slow and shallow — the same restraint as the rest of
 * Mira's motion (one `MiraMotion.SHIMMER_MS` sweep, linear). Every placeholder on a screen shares
 * one [rememberMiraShimmer] brush, so the whole skeleton shimmers as one surface rather than each
 * block running its own clock out of step.
 *
 * Use it only for the *initial load* of a screen's content. An action already under way — a command
 * being carried out, a voice turn being thought through — is a [MiraLoadingIndicator], because there
 * the shape of the result is not yet known and a skeleton would be a lie about what is coming.
 */

/** A shimmer brush for a whole skeleton. Build it once at the top of a loading layout and hand it to
 *  every [MiraSkeletonBox] under it, so the band sweeps across them together. */
@Composable
fun rememberMiraShimmer(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = lerp(base, MaterialTheme.colorScheme.onBackground, HIGHLIGHT_BLEND)
    val transition = rememberInfiniteTransition(label = "miraShimmer")
    val x by transition.animateFloat(
        initialValue = -BAND_PX,
        targetValue = TRAVEL_PX,
        animationSpec = infiniteRepeatable(
            animation = tween(MiraMotion.SHIMMER_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "miraShimmerX",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + BAND_PX, 0f),
    )
}

/** One shimmering block, the atom every skeleton is built from. Pass a shared [brush] from
 *  [rememberMiraShimmer] so it sweeps in step with its siblings. */
@Composable
fun MiraSkeletonBox(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(modifier.clip(shape).background(brush))
}

/** A placeholder for one line of text: a short rounded bar at roughly a line's height. */
@Composable
fun MiraSkeletonLine(
    brush: Brush,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = LINE_HEIGHT,
) {
    MiraSkeletonBox(
        brush = brush,
        modifier = modifier.fillMaxWidth(widthFraction).height(height),
        shape = MaterialTheme.shapes.extraSmall,
    )
}

/**
 * A list's worth of placeholder rows, shaped like [MiraListRow]: a round leading mark and two lines
 * of unequal width. The default [rows] fills a phone; the widths are hand-varied per row so the
 * block reads as a list of different entries rather than a printed pattern.
 */
@Composable
fun MiraListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = DEFAULT_ROWS,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = MiraTheme.spacing
    val brush = rememberMiraShimmer()
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        repeat(rows) { index ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                MiraSkeletonBox(
                    brush = brush,
                    modifier = Modifier.size(spacing.xl),
                    shape = CircleShape,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    MiraSkeletonLine(brush = brush, widthFraction = TITLE_WIDTHS[index % TITLE_WIDTHS.size])
                    MiraSkeletonLine(
                        brush = brush,
                        widthFraction = SUBTITLE_WIDTHS[index % SUBTITLE_WIDTHS.size],
                        height = LINE_HEIGHT_SMALL,
                    )
                }
            }
        }
    }
}

/** How light the sweeping band is over the graphite base. */
private const val HIGHLIGHT_BLEND = 0.10f

/** The band and its travel, in pixels. The travel runs past the widest phone so the band clears the
 *  right edge before it restarts; the band is wide enough to read as a soft sheen, not a line. */
private const val BAND_PX = 420f
private const val TRAVEL_PX = 1400f

private val LINE_HEIGHT = 14.dp
private val LINE_HEIGHT_SMALL = 11.dp

private const val DEFAULT_ROWS = 7

/** Hand-varied widths so the rows read as different entries, not a repeated stamp. */
private val TITLE_WIDTHS = listOf(0.62f, 0.48f, 0.7f, 0.54f, 0.66f, 0.44f, 0.58f)
private val SUBTITLE_WIDTHS = listOf(0.38f, 0.3f, 0.44f, 0.34f, 0.28f, 0.4f, 0.32f)

@Preview
@Composable
private fun MiraListSkeletonPreview() {
    MiraPreviewSurface {
        MiraListSkeleton(rows = 5)
    }
}
