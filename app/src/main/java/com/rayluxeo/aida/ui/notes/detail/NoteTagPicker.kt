package com.rayluxeo.aida.ui.notes.detail

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import com.rayluxeo.aida.ui.notes.NoteSheetList

/**
 * The labels on a note.
 *
 * Tags already in use are offered before the text field, because picking "rayluxeo" is how one
 * label stays one label — typed again with different capitals it becomes a second spelling, and
 * the filter that was meant to gather the work splits it in two. Notes and tasks draw from the
 * same vocabulary, so a label made here is one a task can wear tomorrow.
 */
@Composable
internal fun NoteTagPicker(
    selected: List<NoteTagUi>,
    suggestions: List<NoteTagUi>,
    words: NoteDetailWording,
    onAdd: (String) -> Unit,
    onRemove: (Long) -> Unit,
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
    selected: List<NoteTagUi>,
    suggestions: List<NoteTagUi>,
    words: NoteDetailWording,
    onAdd: (String) -> Unit,
    onRemove: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    var draft by remember { mutableStateOf("") }

    if (selected.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            selected.forEach { tag ->
                MiraChip(
                    text = tag.name,
                    modifier = Modifier.semantics { contentDescription = words.removeTag(tag.name) },
                    selected = true,
                    leadingIcon = MiraIcons.Close,
                    onClick = { onRemove(tag.id) },
                )
            }
        }
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

    // Capped and scrolling, because this is the one part of the sheet that grows with Abu's whole
    // vocabulary rather than with this note: left to run, the labels past the bottom of the sheet
    // could not be tapped and Done went with them. See [NoteSheetList].
    if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(spacing.sm))
        NoteSheetList {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                suggestions.forEach { tag ->
                    MiraChip(text = tag.name, onClick = { onAdd(tag.name) })
                }
            }
        }
    }

    Spacer(Modifier.height(spacing.lg))
    MiraButton(text = words.done, onClick = onDone, modifier = Modifier.fillMaxWidth())
}

@Preview
@Composable
private fun NoteTagPickerPreview() {
    MiraPreviewSurface {
        MiraChip(text = "rent", selected = true, leadingIcon = MiraIcons.Close, onClick = {})
        MiraChip(text = "rayluxeo", onClick = {})
    }
}
