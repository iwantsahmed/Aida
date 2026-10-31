package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.rememberMiraHaptics

/**
 * A small pill for a filter or a label. Selected fills with the champagne wash rather than solid
 * champagne, so a row of chips never competes with the screen's one primary action.
 */
@Composable
fun MiraChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val interaction = remember { MutableInteractionSource() }
    val label = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground
    }
    val outline = when {
        !enabled -> MiraTheme.colors.hairline
        selected -> MaterialTheme.colorScheme.primary
        else -> MiraTheme.colors.hairlineStrong
    }
    Row(
        modifier = modifier
            // The chip STAYS 32dp to look at and becomes 48dp to hit. A filter row is chips
            // shoulder to shoulder, so a touch target the size of the pill means every mis-tap
            // selects the neighbour — and a thumb is nearer 9mm than 32dp. This expands the
            // target outward without moving the pill or its neighbours, which is the whole
            // reason to use it rather than padding the row.
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .height(spacing.xl)
            .clip(CircleShape)
            .background(if (selected) MiraTheme.colors.accentWash else Color.Transparent)
            .border(width = spacing.hairline, color = outline, shape = CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(color = MaterialTheme.colorScheme.primary),
                        enabled = enabled,
                        role = Role.Button,
                    ) {
                        haptics.tap()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(spacing.md),
                tint = label,
            )
            Spacer(Modifier.width(spacing.xxs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun MiraChipPreview() {
    MiraPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs)) {
            MiraChip(text = "Open", selected = true, leadingIcon = MiraIcons.Check, onClick = {})
            MiraChip(text = "Done", onClick = {})
            MiraChip(text = "All", onClick = {})
        }
    }
}
