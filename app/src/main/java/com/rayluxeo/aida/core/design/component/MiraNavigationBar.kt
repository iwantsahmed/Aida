package com.rayluxeo.aida.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rayluxeo.aida.core.design.ContinuousRoundedCornerShape
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import kotlinx.coroutines.launch

/** One destination in the bar. */
data class MiraNavItem(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

private val ItemWidth = 54.dp
private val ItemHeight = 46.dp
private val IconSize = 24.dp
private val PillPadding = 6.dp
private val PillRadius = 26.dp
private val HighlightRadius = 18.dp

/**
 * The pill is translucent, not solid: on the presence screens the aurora behind it is meant to
 * show right through the bar (Abu, 16 Oct 2026), and over a plain obsidian screen a dark tint on
 * near-black is indistinguishable from the old solid fill. The selected highlight carries a touch
 * more body so it still reads as the chosen slot through the glass.
 */
private const val PillScrimAlpha = 0.52f
private const val HighlightScrimAlpha = 0.66f

/**
 * Mira's bottom navigation: a compact, floating **icon bar**, not a full-width tab strip.
 *
 * The selection is a single soft highlight that *slides* to the tapped icon — and stretches as it
 * goes, its leading edge springing ahead while the trailing edge follows a beat behind, so the pill
 * reads as one elastic body of light rather than a box teleporting between slots. A filled slab
 * across the whole width would be a bright bar on AMOLED black fighting every screen; a small pill
 * that gathers to the one thing selected is the opposite.
 *
 * The bar is data-in, taps-out: it takes [items] and knows nothing about the navigation graph, so
 * the design system never depends on the routes.
 */
@Composable
fun MiraNavigationBar(
    items: List<MiraNavItem>,
    modifier: Modifier = Modifier,
) {
    val selected = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    val haptics = rememberMiraHaptics()

    // The highlight is two independently sprung edges. Whichever edge leads the direction of travel
    // is the stiffer one, so the pill stretches toward where it is going and gathers as it lands.
    val leftEdge = remember { Animatable(selected * ItemWidth.value, Float.VectorConverter) }
    val rightEdge = remember { Animatable((selected + 1) * ItemWidth.value, Float.VectorConverter) }
    LaunchedEffect(selected) {
        val toLeft = selected * ItemWidth.value
        val toRight = toLeft + ItemWidth.value
        val movingRight = toLeft > leftEdge.value
        launch {
            leftEdge.animateTo(toLeft, spring(dampingRatio = 0.72f, stiffness = if (movingRight) 300f else 620f))
        }
        launch {
            rightEdge.animateTo(toRight, spring(dampingRatio = 0.72f, stiffness = if (movingRight) 620f else 300f))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = MiraTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(ContinuousRoundedCornerShape(PillRadius))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = PillScrimAlpha))
                .border(
                    width = MiraTheme.spacing.hairline,
                    color = MiraTheme.colors.hairline,
                    shape = ContinuousRoundedCornerShape(PillRadius),
                )
                .padding(PillPadding),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = leftEdge.value.dp)
                    .width((rightEdge.value - leftEdge.value).dp.coerceAtLeast(0.dp))
                    .height(ItemHeight)
                    .clip(ContinuousRoundedCornerShape(HighlightRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = HighlightScrimAlpha)),
            )
            Row {
                items.forEach { item ->
                    NavIcon(item = item, onTap = { haptics.tap(); item.onClick() })
                }
            }
        }
    }
}

@Composable
private fun NavIcon(item: MiraNavItem, onTap: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (item.selected) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MiraMotion.standard(),
        label = "MiraNavIconTint",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(width = ItemWidth, height = ItemHeight)
            .clip(ContinuousRoundedCornerShape(HighlightRadius))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClickLabel = item.label,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(IconSize),
        )
    }
}


@Preview
@Composable
private fun MiraNavigationBarPreview() {
    MiraPreviewSurface {
        MiraNavigationBar(
            items = listOf(
                MiraNavItem(MiraIcons.Home, "Home", selected = true, onClick = {}),
                MiraNavItem(MiraIcons.GraphicEq, "Aida", selected = false, onClick = {}),
                MiraNavItem(MiraIcons.CheckCircle, "Tasks", selected = false, onClick = {}),
                MiraNavItem(MiraIcons.Lock, "Vault", selected = false, onClick = {}),
            ),
        )
    }
}
