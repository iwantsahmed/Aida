package com.rayluxeo.aida.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.rememberMiraHaptics


/**
 * Two or three mutually exclusive options. The champagne indicator slides between segments so the
 * change reads as one object moving rather than two labels swapping colour.
 *
 * Segments carry no ripple of their own: the indicator plus the haptic is already the answer, and
 * a ripple underneath a moving block just muddies it.
 */
@Composable
fun MiraSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return

    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val trackShape = MaterialTheme.shapes.medium
    val indicatorShape = MaterialTheme.shapes.small
    val selected = selectedIndex.coerceIn(0, options.lastIndex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(spacing.xxl)
            .clip(trackShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selected,
            animationSpec = MiraMotion.standard(),
            label = "MiraSegmentedIndicator",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(spacing.xxs)
                .clip(indicatorShape)
                .background(MiraTheme.colors.accentWash)
                .border(
                    width = spacing.hairline,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MiraTheme.colors.accentDim
                    },
                    shape = indicatorShape,
                ),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selected
                val labelColor by animateColorAsState(
                    // A disabled control drops every label to muted, including the selected one,
                    // so nothing on it still reads as live.
                    targetValue = if (enabled && isSelected) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = MiraMotion.quick(),
                    label = "MiraSegmentLabel",
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            role = Role.RadioButton,
                        ) {
                            if (!isSelected) {
                                haptics.tap()
                                onSelect(index)
                            }
                        }
                        .padding(horizontal = spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        color = labelColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MiraSegmentedControlPreview() {
    MiraPreviewSurface {
        MiraSegmentedControl(
            options = listOf("Open", "Done", "All"),
            selectedIndex = 0,
            onSelect = {},
        )
        MiraSegmentedControl(
            options = listOf("English", "বাংলা"),
            selectedIndex = 1,
            onSelect = {},
        )
    }
}
