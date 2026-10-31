package com.rayluxeo.aida.core.design

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The width of the soft fade at a scrolling row's edge: wide enough that a chip caught under it
 * reads as "continues", narrow enough not to swallow a whole chip. A design-system constant, kept
 * here with the modifier that spends it rather than in [MiraSpacing], because it is a property of
 * this one effect and not a rhythm any screen composes against.
 */
private val FadingEdgeWidth: Dp = 28.dp

/**
 * Fades a scrolling row's horizontal edges to transparent wherever there is still content to reach
 * that way — the quiet, standard "there is more, swipe" affordance.
 *
 * Mira's filter and tab rows are single scrolling lines of chips: a second permanent row would push
 * every list down to buy a swipe on one. Without a fade, such a row simply ends at the screen edge
 * mid-chip, and the chips past it look like they do not exist — the thing Abu flagged, where only
 * the first three of a longer set read as the whole set. This draws a short gradient mask at each
 * end, present only when that end can still scroll, so a row that fits shows no fade at all and a
 * row that overflows says so on both the side it came from and the side it is going to.
 *
 * Apply it OUTSIDE the scroll so it masks the clipped viewport, not the scrolling content:
 * `Modifier.fadingHorizontalEdges(state).horizontalScroll(state)`. [canScrollStart]/[canScrollEnd]
 * are read at DRAW time, so the fade tracks the scroll without recomposing.
 */
fun Modifier.fadingHorizontalEdges(
    canScrollStart: () -> Boolean,
    canScrollEnd: () -> Boolean,
    edgeWidth: Dp = FadingEdgeWidth,
): Modifier = this
    // DstIn multiplies the mask's alpha into this layer's own pixels, so a faded edge reveals
    // whatever sits behind the row rather than painting black over it — but only against the layer's
    // own content, which is why it needs its own offscreen layer to blend into.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = edgeWidth.toPx().coerceAtMost(size.width / 2f)
        if (w <= 0f) return@drawWithContent
        if (canScrollStart()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = w,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (canScrollEnd()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - w,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** Fade the edges of a [androidx.compose.foundation.horizontalScroll] row, driven by its state. */
fun Modifier.fadingHorizontalEdges(
    scrollState: ScrollState,
    edgeWidth: Dp = FadingEdgeWidth,
): Modifier = fadingHorizontalEdges(
    canScrollStart = { scrollState.canScrollBackward },
    canScrollEnd = { scrollState.canScrollForward },
    edgeWidth = edgeWidth,
)

/** Fade the edges of a `LazyRow`, driven by its [LazyListState]. */
fun Modifier.fadingHorizontalEdges(
    listState: LazyListState,
    edgeWidth: Dp = FadingEdgeWidth,
): Modifier = fadingHorizontalEdges(
    canScrollStart = { listState.canScrollBackward },
    canScrollEnd = { listState.canScrollForward },
    edgeWidth = edgeWidth,
)
