package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.miraRaised
import com.rayluxeo.aida.core.design.rememberMiraHaptics

/**
 * Carbon on obsidian, separated by a hairline rather than a shadow. Elevation on a true-black
 * screen only produces a grey halo, so Mira draws the edge instead of faking the light.
 *
 * The ripple is champagne because the default content-coloured one is nearly invisible on
 * carbon — on a dark surface the touch needs to add light, not remove it.
 */
@Composable
fun MiraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(MiraTheme.spacing.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(shape)
            // Lit rather than outlined: a card on true black has no shadow available to it, so
            // depth comes from a specular edge along the top. See `Modifier.miraRaised`.
            .miraRaised(
                shape = shape,
                colors = MiraTheme.colors,
                fill = MaterialTheme.colorScheme.surface,
            )
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
            .padding(contentPadding),
        content = content,
    )
}

@Preview
@Composable
private fun MiraCardPreview() {
    MiraPreviewSurface {
        MiraCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Tomorrow, 7:30",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Call the landlord about the lease",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MiraCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
            Text(
                text = "Today's brief",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
