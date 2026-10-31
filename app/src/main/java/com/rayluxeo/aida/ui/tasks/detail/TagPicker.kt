package com.rayluxeo.aida.ui.tasks.detail

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraTextField

/**
 * The labels on a task.
 *
 * The tags already in use are offered before the text field, because picking "client-work" is how
 * one label stays one label — typed again it becomes a second spelling, and the filter that was
 * supposed to gather the work splits it in two.
 */
@Composable
internal fun TagPicker(
    selected: List<String>,
    suggestions: List<String>,
    words: TaskDetailWording,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraBottomSheet(onDismissRequest = onDismiss, modifier = modifier, title = words.tagsSheetTitle) {
        TagPickerBody(
            selected = selected,
            suggestions = suggestions,
            words = words,
            onAdd = onAdd,
            onRemove = onRemove,
            onDone = onDismiss,
        )
    }
}

@Composable
private fun ColumnScope.TagPickerBody(
    selected: List<String>,
    suggestions: List<String>,
    words: TaskDetailWording,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDone: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    var draft by remember { mutableStateOf("") }

    if (selected.isNotEmpty()) {
        SelectedTags(selected = selected, words = words, onRemove = onRemove)
        Spacer(Modifier.height(spacing.sm))
    }

    MiraTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = words.tagPlaceholder,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                onAdd(draft)
                draft = ""
            },
        ),
        trailing = {
            if (draft.isNotBlank()) {
                MiraIconButton(
                    icon = MiraIcons.Add,
                    contentDescription = words.addTag,
                    onClick = {
                        onAdd(draft)
                        draft = ""
                    },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )

    if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(spacing.sm))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            suggestions.forEach { name ->
                MiraChip(text = name, onClick = { onAdd(name) })
            }
        }
    }

    Spacer(Modifier.height(spacing.lg))
    MiraButton(text = words.done, onClick = onDone, modifier = Modifier.fillMaxWidth())
}

/** The tags already on the task, each with the one control that takes it off again. */
@Composable
internal fun SelectedTags(
    selected: List<String>,
    words: TaskDetailWording,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        selected.forEach { name ->
            MiraChip(
                text = name,
                // The chip is the remove control, so the reader is told that rather than the name.
                modifier = Modifier.semantics { contentDescription = words.removeTag(name) },
                selected = true,
                leadingIcon = MiraIcons.Close,
                onClick = { onRemove(name) },
            )
        }
    }
}

@Preview
@Composable
private fun TagPickerPreview() {
    MiraPreviewSurface {
        Text(text = "Tags", style = MaterialTheme.typography.labelMedium)
        TagPickerBody(
            selected = listOf("rayluxeo", "urgent"),
            suggestions = listOf("client-work", "admin"),
            words = EnglishTaskDetailWording,
            onAdd = {},
            onRemove = {},
            onDone = {},
        )
    }
}
