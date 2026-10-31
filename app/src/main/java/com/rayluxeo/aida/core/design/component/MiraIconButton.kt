package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.rememberMiraHaptics

/**
 * A bare glyph in a 48dp target. The box is deliberately larger than the icon: minimal icons
 * only stay minimal if they are not also small to hit.
 */
@Composable
fun MiraIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(spacing.xxl)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = tint),
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(spacing.lg),
            tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun MiraIconButtonPreview() {
    MiraPreviewSurface {
        MiraIconButton(
            icon = MiraIcons.ArrowBack,
            contentDescription = "Back",
            onClick = {},
        )
    }
}
