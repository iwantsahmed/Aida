package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * Groups a list without drawing a box around it. Small, wide-tracked and muted, so the eye reads
 * it as a label rather than as content.
 *
 * The label is upper-cased for its own sake — Bengali has no case, so a Bengali heading passes
 * through unchanged and still gets the tracking and the colour.
 */
@Composable
fun MiraSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Spacer(Modifier.width(spacing.xs))
            trailing()
        }
    }
}

@Preview
@Composable
private fun MiraSectionHeaderPreview() {
    MiraPreviewSurface {
        MiraSectionHeader(text = "Today")
        MiraSectionHeader(
            text = "Open tasks",
            trailing = { MiraButton(text = "See all", onClick = {}) },
        )
    }
}
