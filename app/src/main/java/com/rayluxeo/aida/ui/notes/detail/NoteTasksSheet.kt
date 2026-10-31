package com.rayluxeo.aida.ui.notes.detail

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.ui.notes.NoteSheetList

/**
 * Every line of the note, with the ones that look like something to do already ticked.
 *
 * Every line is shown, including the ones Mira did not tick: she decides what to suggest, Abu
 * decides what becomes a task. Nothing is created until he says so, which is why the sheet has a
 * Create button and not a row that acts on the tap.
 *
 * **The lines scroll; the two buttons do not.** See [NoteSheetList] — a dozen-line note used to
 * push Cancel and Create off the bottom of the sheet with no way to reach them.
 */
@Composable
internal fun NoteTasksSheet(
    state: NoteTasksUiState,
    words: NoteDetailWording,
    onToggle: (Int) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraBottomSheet(onDismissRequest = onDismiss, modifier = modifier, title = words.tasksTitle) {
        NoteSheetList {
            state.candidates.forEachIndexed { index, candidate ->
                // A hairline goes BETWEEN two rows and never under the last of them; the step of
                // air above the buttons is already separating them from the list.
                if (index > 0) MiraDivider()
                MiraListRow(
                    title = candidate.line,
                    onClick = { onToggle(index) },
                    enabled = !state.isCreating,
                    leading = {
                        Icon(
                            imageVector = if (candidate.isSelected) {
                                MiraIcons.CheckBox
                            } else {
                                MiraIcons.CheckBoxOutlineBlank
                            },
                            contentDescription = null,
                            tint = if (candidate.isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(spacing.lg))
        Row(modifier = Modifier.fillMaxWidth()) {
            MiraButton(
                text = words.cancel,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                style = MiraButtonStyle.SECONDARY,
                enabled = !state.isCreating,
            )
            Spacer(Modifier.width(spacing.sm))
            MiraButton(
                text = words.tasksCreate,
                onClick = onCreate,
                modifier = Modifier.weight(1f),
                enabled = state.canCreate,
            )
        }
    }
}

@Preview
@Composable
private fun NoteTasksSheetPreview() {
    MiraPreviewSurface {
        MiraListRow(
            title = "Ask the landlord about the deposit",
            leading = {
                Icon(
                    imageVector = MiraIcons.CheckBox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        MiraDivider()
        MiraListRow(
            title = "Rent went up last year too",
            leading = {
                Icon(
                    imageVector = MiraIcons.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}
