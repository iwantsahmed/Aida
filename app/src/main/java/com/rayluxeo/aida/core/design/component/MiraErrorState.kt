package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * The counterpart to [MiraEmptyState], for when something genuinely failed rather than simply
 * having nothing in it. Same shape, same restraint, and the only difference is the glyph's
 * colour — which is why the two are never confused for each other at a glance.
 *
 * [message] is a sentence Abu can act on: what did not happen, and what he can do about it.
 * A raw exception, an error code or anything that reads as his fault does not belong here; the
 * wording comes from `ResponseRenderer` in his own language, not from a stack trace.
 *
 * The retry action appears only when both [retryText] and [onRetry] are supplied, so there is
 * never a button on screen that cannot do anything.
 */
@Composable
fun MiraErrorState(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    retryText: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MiraIcons.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(spacing.xl),
            tint = MiraTheme.colors.danger,
        )
        Spacer(Modifier.height(spacing.md))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (retryText != null && onRetry != null) {
            Spacer(Modifier.height(spacing.lg))
            MiraButton(text = retryText, onClick = onRetry)
        }
    }
}

@Preview
@Composable
private fun MiraErrorStatePreview() {
    MiraPreviewSurface {
        MiraErrorState(
            message = "Aida could not reach the calendar.",
            detail = "Calendar access is off. It can be turned back on in Settings.",
            retryText = "Try again",
            onRetry = {},
        )
        MiraErrorState(message = "That vault entry could not be opened.")
    }
}
