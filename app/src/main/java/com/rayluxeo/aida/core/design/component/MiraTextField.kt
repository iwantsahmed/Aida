package com.rayluxeo.aida.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme

private const val DEFAULT_MULTILINE_MAX_LINES = 4

/**
 * Graphite field, hairline border, and champagne only while the caret is in it — the border is
 * how Mira says "I am listening" without adding a second accent to the screen.
 *
 * Built on [BasicTextField] rather than Material's own, because every Material text field arrives
 * with a filled grey container and a floating label that neither belongs on obsidian.
 */
@Composable
fun MiraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = DEFAULT_MULTILINE_MAX_LINES,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MiraTheme.colors.hairline
            focused -> MaterialTheme.colorScheme.primary
            else -> MiraTheme.colors.hairlineStrong
        },
        animationSpec = MiraMotion.standard(),
        label = "MiraTextFieldBorder",
    )

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.xs))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            // BasicTextField rejects a single-line field that claims more than one line.
            maxLines = if (singleLine) 1 else maxLines.coerceAtLeast(1),
            interactionSource = interaction,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        ) { field ->
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(width = spacing.hairline, color = borderColor, shape = shape)
                    .defaultMinSize(minHeight = spacing.xxl + spacing.xs)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(spacing.lg),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(spacing.sm))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
                if (trailing != null) {
                    Spacer(Modifier.width(spacing.sm))
                    trailing()
                }
            }
        }
    }
}

@Preview
@Composable
private fun MiraTextFieldPreview() {
    MiraPreviewSurface {
        MiraTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = "Search",
            placeholder = "Find a task",
            leadingIcon = MiraIcons.Search,
        )
        MiraTextField(
            value = "Call the landlord",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
