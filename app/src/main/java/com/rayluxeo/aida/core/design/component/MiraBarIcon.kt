package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.rememberMiraHaptics

/**
 * A 48dp glyph target for a screen's top band, with an optional champagne dot at its shoulder.
 *
 * The dot is a true indicator: passed [showDot] only when there is genuinely something waiting, so
 * the bell is never lit for decoration.
 */
@Composable
fun MiraBarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(spacing.xxl)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = accent),
                role = Role.Button,
                onClickLabel = contentDescription,
            ) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(spacing.md)
                    .size(spacing.xs)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}
