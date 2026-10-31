package com.rayluxeo.aida.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.rememberMiraHaptics

private const val PRESSED_SCALE = 0.98f

/** How loudly a button asks to be pressed. A screen carries at most one [Primary]. */
enum class MiraButtonStyle {
    /** Champagne fill, obsidian label. The single next step on a screen. */
    PRIMARY,

    /** Hairline border, ivory label. Safe to repeat. */
    SECONDARY,

    /** Label only, in the accent. For "Not now", "Skip", "Learn more". */
    TEXT,
}

/**
 * Mira's button.
 *
 * One component rather than three, because the difference between a primary and a secondary
 * action is emphasis, not behaviour: the target size, the press response and the haptic are
 * identical, and only the fill changes.
 *
 * Disabled keeps its fill and drops to the dim champagne rather than fading towards the
 * background — a button Abu cannot press yet should still be legible enough to explain itself.
 */
@Composable
fun MiraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MiraButtonStyle = MiraButtonStyle.PRIMARY,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = MiraTheme.spacing.lg),
) {
    when (style) {
        MiraButtonStyle.TEXT -> MiraTextButtonBody(text, onClick, modifier, enabled, accent)
        MiraButtonStyle.PRIMARY -> MiraFilledButtonBody(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            container = if (enabled) accent else MiraTheme.colors.accentDim,
            label = MaterialTheme.colorScheme.onPrimary,
            border = null,
            contentPadding = contentPadding,
        )
        MiraButtonStyle.SECONDARY -> MiraFilledButtonBody(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            container = Color.Transparent,
            label = if (enabled) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            border = if (enabled) MiraTheme.colors.hairlineStrong else MiraTheme.colors.hairline,
            contentPadding = contentPadding,
        )
    }
}

/**
 * The press scale is two percent — enough to feel answered, small enough that a column of
 * buttons does not look like it is breathing.
 */
@Composable
private fun MiraFilledButtonBody(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: ImageVector?,
    container: Color,
    label: Color,
    border: Color?,
    contentPadding: PaddingValues = PaddingValues(horizontal = MiraTheme.spacing.lg),
) {
    val spacing = MiraTheme.spacing
    val shape = MaterialTheme.shapes.medium
    val haptics = rememberMiraHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = MiraMotion.instant(),
        label = "MiraButtonPressScale",
    )

    LaunchedEffect(pressed) {
        if (pressed) haptics.tap()
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(spacing.xxl + spacing.xs)
            .clip(shape)
            .background(container)
            .then(
                if (border != null) {
                    Modifier.border(width = spacing.hairline, color = border, shape = shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = label),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(spacing.md + spacing.xxs),
                tint = label,
            )
            Spacer(Modifier.width(spacing.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Keeps a full 48dp target despite having no visible container. */
@Composable
private fun MiraTextButtonBody(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    accent: Color,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = accent),
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.tap()
                onClick()
            }
            .defaultMinSize(minHeight = spacing.xxl)
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun MiraButtonPreview() {
    MiraPreviewSurface {
        MiraButton(
            text = "Save task",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = MiraIcons.Check,
        )
        MiraButton(text = "Save task", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        MiraButton(
            text = "Not now",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        style = MiraButtonStyle.SECONDARY,
        )
        MiraButton(text = "Skip for now", onClick = {}, style = MiraButtonStyle.TEXT)
    }
}
