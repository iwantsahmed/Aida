package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme

/** How loudly a notice speaks. The text always carries the meaning; tone only reinforces it. */
enum class MiraNoticeTone {
    /** Something happened that Abu should know about, but nothing went wrong. */
    INFO,

    /** Something Mira could not do. Never a raw exception — always a sentence. */
    DANGER,

    /** Something finished, and there may be a way to undo it. */
    CONFIRM,
}

/**
 * A transient line that sits in the flow of a screen and can be waved away.
 *
 * Distinct from [MiraErrorState], which owns a whole screen when its content could not load and
 * offers a retry. This one never takes the screen: it appears above the content Abu is already
 * reading, says one thing, optionally offers one action, and leaves.
 *
 * It exists because five screens had each grown their own copy of the same Row — danger-coloured
 * body text beside a close button — and they had already drifted on container, padding and tone.
 */
@Composable
fun MiraInlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    tone: MiraNoticeTone = MiraNoticeTone.INFO,
    dismissContentDescription: String? = null,
    onDismiss: (() -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    val textColour = when (tone) {
        MiraNoticeTone.INFO -> MaterialTheme.colorScheme.onSurface
        MiraNoticeTone.DANGER -> MiraTheme.colors.danger
        MiraNoticeTone.CONFIRM -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = spacing.md, top = spacing.xs, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColour,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            MiraButton(text = actionText, onClick = onAction, style = MiraButtonStyle.TEXT)
        }
        if (onDismiss != null) {
            MiraIconButton(
                icon = MiraIcons.Close,
                contentDescription = dismissContentDescription,
                onClick = onDismiss,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun MiraInlineNoticePreview() {
    MiraPreviewSurface {
        MiraInlineNotice(
            text = "Aida could not reach the vault.",
            tone = MiraNoticeTone.DANGER,
            dismissContentDescription = "Dismiss",
            onDismiss = {},
        )
        MiraInlineNotice(
            text = "Task deleted.",
            tone = MiraNoticeTone.CONFIRM,
            actionText = "Undo",
            onAction = {},
            dismissContentDescription = "Dismiss",
            onDismiss = {},
        )
        MiraInlineNotice(text = "Reminders may arrive late.")
    }
}
