package com.rayluxeo.aida.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme

/** Small enough to read as arrival rather than as a zoom. */
private const val ENTRY_SCALE = 0.94f

/**
 * A decision Abu has to make before anything else continues.
 *
 * Built on [Dialog] rather than Material's `AlertDialog` so the surface is genuinely Mira's:
 * carbon, hairline-edged and generously radiused, instead of Material's elevated grey container
 * with its own type scale. Tapping outside and pressing back both route to [onDismissRequest],
 * so a dialog can never be left on screen with no way out.
 *
 * [destructive] recolours the confirm action rather than adding a warning icon or a red title —
 * one signal, on the control that actually does the damage.
 *
 * @param onDismiss what the [dismissText] action does, when it is more than closing the dialog.
 *   Defaults to [onDismissRequest].
 * @param content an optional slot between the message and the actions, for the rare dialog that
 *   needs Abu to choose something rather than only agree.
 */
@Composable
fun MiraDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entry = animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = MiraMotion.enter(),
        label = "MiraDialogEntry",
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        MiraDialogSurface(
            title = title,
            confirmText = confirmText,
            onConfirm = onConfirm,
            modifier = modifier.graphicsLayer {
                alpha = entry.value
                val scale = ENTRY_SCALE + (1f - ENTRY_SCALE) * entry.value
                scaleX = scale
                scaleY = scale
            },
            message = message,
            dismissText = dismissText,
            onDismiss = onDismiss ?: onDismissRequest,
            destructive = destructive,
            content = content,
        )
    }
}

/**
 * The dialog's visible body, separated from the window that hosts it so it can be previewed
 * and so the entry animation has a single node to drive.
 */
@Composable
private fun MiraDialogSurface(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier,
    message: String?,
    dismissText: String?,
    onDismiss: () -> Unit,
    destructive: Boolean,
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    val spacing = MiraTheme.spacing
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = spacing.hairline, color = MiraTheme.colors.hairline, shape = shape)
            .padding(spacing.lg),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (message != null) {
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (content != null) {
            Spacer(Modifier.height(spacing.md))
            content()
        }
        Spacer(Modifier.height(spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissText != null) {
                MiraButton(
                    text = dismissText,
                    onClick = onDismiss,
                    style = MiraButtonStyle.TEXT,
                )
            }
            MiraButton(
                text = confirmText,
                onClick = onConfirm,
                style = MiraButtonStyle.PRIMARY,
                accent = if (destructive) {
                    MiraTheme.colors.danger
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Preview
@Composable
private fun MiraDialogSurfacePreview() {
    MiraPreviewSurface {
        MiraDialogSurface(
            title = "Delete this note?",
            confirmText = "Delete",
            onConfirm = {},
            modifier = Modifier,
            message = "It is stored only on this phone, so it cannot be recovered afterwards.",
            dismissText = "Keep it",
            onDismiss = {},
            destructive = true,
            content = null,
        )
        MiraDialogSurface(
            title = "Turn on Gemini?",
            confirmText = "Turn on",
            onConfirm = {},
            modifier = Modifier,
            message = "Aida will send an utterance to Google only when she cannot understand it " +
                "on this phone. She works without it.",
            dismissText = null,
            onDismiss = {},
            destructive = false,
            content = null,
        )
    }
}
