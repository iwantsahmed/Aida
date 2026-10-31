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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * An empty list is a normal state, not a fault. Dim champagne glyph, one calm line, and an
 * optional way forward — nothing red, nothing apologetic, no illustration.
 */
@Composable
fun MiraEmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(spacing.xl),
            tint = MiraTheme.colors.accentDim,
        )
        Spacer(Modifier.height(spacing.md))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(spacing.lg))
            MiraButton(text = actionText, onClick = onAction)
        }
    }
}

@Preview
@Composable
private fun MiraEmptyStatePreview() {
    MiraPreviewSurface {
        MiraEmptyState(
            icon = MiraIcons.Inbox,
            message = "Nothing open right now.",
            actionText = "Add a task",
            onAction = {},
        )
    }
}
