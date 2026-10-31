package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.rememberMiraHaptics

private const val SUPPORTING_MAX_LINES = 2

/**
 * One line of a list: something on the left, the words, something on the right. Generous height
 * because a dense row on a dark screen turns a list into a wall.
 *
 * [trailingText] is the selected-value label of a settings row — "Karachi", "Hanafi", "20 min" —
 * placed before [trailing] (usually a chevron). It is weighted, so a long value right-aligns and
 * ellipsises inside its own share rather than starving the title: an unweighted trailing is
 * measured first and would crush the title down to one letter. The title keeps the larger share,
 * which is why the row's own title and supporting stay legible even when the value is long.
 */
@Composable
fun MiraListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    trailingText: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
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
            .defaultMinSize(minHeight = spacing.xxl + spacing.md)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(spacing.md))
        }
        Column(modifier = Modifier.weight(if (trailingText != null) 3f else 1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = SUPPORTING_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            Spacer(Modifier.width(spacing.md))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(2f),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(spacing.md))
            trailing()
        }
    }
}

@Preview
@Composable
private fun MiraListRowPreview() {
    MiraPreviewSurface {
        MiraListRow(
            title = "Call the landlord about the lease",
            supporting = "Tomorrow, 7:30",
            onClick = {},
            leading = {
                Icon(
                    imageVector = MiraIcons.TaskAlt,
                    contentDescription = null,
                    tint = MiraTheme.colors.accentDim,
                )
            },
            trailing = {
                Icon(
                    imageVector = MiraIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        MiraDivider()
        MiraListRow(title = "Pick up the parcel")
    }
}
