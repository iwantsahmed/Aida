package com.rayluxeo.aida.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * What TalkBack reads for the clear glyph. It is a fixed English word for the same reason the
 * placeholder default is English: this is the design-system layer, and the screen above it is
 * what knows which language Abu is working in.
 */
private const val CLEAR_LABEL = "Clear"

/**
 * Searching is its own gesture, so it gets its own field rather than a [MiraTextField] with a
 * magnifier pushed into the leading slot: a pill instead of a rounded box, a glyph that warms
 * with the border, and a Search key on the keyboard.
 *
 * Autocorrect is off because Abu searches in Banglish as often as in English, and the keyboard's
 * English dictionary turns "kaj" into "kaja" before the query ever reaches the list.
 *
 * The clear glyph exists only while there is something to clear. Tapping it always empties the
 * field; [onClear] is the extra beat for a caller that must also drop a result set, because an
 * empty query and a dismissed search are not the same thing to every screen.
 */
@Composable
fun MiraSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onClear: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val spacing = MiraTheme.spacing
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MiraTheme.colors.hairline
            focused -> MaterialTheme.colorScheme.primary
            else -> MiraTheme.colors.hairlineStrong
        },
        animationSpec = MiraMotion.standard(),
        label = "MiraSearchFieldBorder",
    )
    val glyphColor by animateColorAsState(
        targetValue = if (enabled && focused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MiraMotion.standard(),
        label = "MiraSearchFieldGlyph",
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                onSearch?.invoke()
            },
        ),
        singleLine = true,
        // BasicTextField rejects a single-line field that still claims more than one line.
        maxLines = 1,
        interactionSource = interaction,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    ) { field ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(width = spacing.hairline, color = borderColor, shape = CircleShape)
                .defaultMinSize(minHeight = spacing.xxl + spacing.xs)
                .padding(start = spacing.md, end = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiraIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(spacing.lg),
                tint = glyphColor,
            )
            Spacer(Modifier.width(spacing.sm))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = spacing.sm),
            ) {
                if (value.isEmpty()) {
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
            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter = fadeIn(MiraMotion.enter(MiraMotion.QUICK_MS)) +
                    expandHorizontally(MiraMotion.enter(MiraMotion.QUICK_MS)),
                exit = fadeOut(MiraMotion.exit()) + shrinkHorizontally(MiraMotion.exit()),
                label = "MiraSearchFieldClear",
            ) {
                MiraIconButton(
                    icon = MiraIcons.Close,
                    contentDescription = CLEAR_LABEL,
                    onClick = {
                        onValueChange("")
                        onClear?.invoke()
                    },
                    enabled = enabled,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun MiraSearchFieldPreview() {
    MiraPreviewSurface {
        MiraSearchField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search tasks",
        )
        MiraSearchField(
            value = "landlord",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
        )
        MiraSearchField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        )
    }
}
