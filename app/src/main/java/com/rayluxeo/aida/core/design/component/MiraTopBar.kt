package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * A title and, at most, one thing on either side of it. No elevation, no surface tint, no
 * scroll-collapse theatrics — the bar should read as the top of the page rather than a control
 * strip bolted onto it.
 *
 * The bar carries its own top and horizontal insets because the scaffold hands the insets to
 * whichever bar is present. It paints an opaque obsidian background over the whole bar, status-bar
 * strip included, so a screen whose list scrolls edge to edge (content padded, not clipped, by the
 * scaffold insets) passes cleanly behind the title instead of bleeding its text through — the bar
 * stays flat and tintless, it simply occludes rather than letting black-on-black overlap.
 */
@Composable
fun MiraTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "Back",
    action: @Composable (() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
            .height(spacing.xxl + spacing.md)
            .padding(
                // A 48dp button centres its 24dp glyph exactly on the screen gutter from here,
                // so the back arrow lines up with the title of a bar that has no back arrow.
                start = if (onBack != null) spacing.sm else spacing.screenHorizontal,
                end = if (action != null) spacing.sm else spacing.screenHorizontal,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            MiraIconButton(
                icon = MiraIcons.ArrowBack,
                contentDescription = backContentDescription,
                onClick = onBack,
            )
            Spacer(Modifier.width(spacing.xxs))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null) {
            Spacer(Modifier.width(spacing.xs))
            action()
        }
    }
}

@Preview
@Composable
private fun MiraTopBarPreview() {
    MiraPreviewSurface {
        MiraTopBar(title = "Tasks", subtitle = "Four open")
        MiraTopBar(
            title = "Voice setup",
            onBack = {},
            action = {
                MiraIconButton(
                    icon = MiraIcons.Settings,
                    contentDescription = "Settings",
                    onClick = {},
                )
            },
        )
    }
}
