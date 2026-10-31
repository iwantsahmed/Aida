package com.rayluxeo.aida.ui.notes.detail

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.ui.notes.NoteSelectedMark
import com.rayluxeo.aida.ui.notes.NoteSheetList

/**
 * Ties the note to a client, a project or a task — or cuts it loose.
 *
 * One picker for all three because the choice is the same shape each time: a list of names, one
 * of them possibly already chosen, and "None" at the top so unlinking is never harder than
 * linking. Three near-identical sheets would be three places for that to drift.
 *
 * The names sit in a [NoteSheetList] so a long client book scrolls inside the sheet instead of
 * running off the bottom of it, which is what used to put everything after the first handful of
 * clients out of reach.
 */
@Composable
internal fun NoteLinkPicker(
    picker: NoteLinkPickerUiState,
    words: NoteDetailWording,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = when (picker.kind) {
            NoteLinkKind.CLIENT -> words.clientSheetTitle
            NoteLinkKind.PROJECT -> words.projectSheetTitle
            NoteLinkKind.TASK -> words.taskSheetTitle
        },
    ) {
        NoteSheetList {
            MiraListRow(
                title = words.none,
                onClick = { onPick(null) },
                trailing = if (picker.selectedId == null) {
                    { NoteSelectedMark() }
                } else {
                    null
                },
            )

            if (picker.options.isEmpty()) {
                Spacer(Modifier.height(MiraTheme.spacing.sm))
                Text(
                    text = when (picker.kind) {
                        NoteLinkKind.CLIENT -> words.noClients
                        NoteLinkKind.PROJECT -> words.noProjects
                        NoteLinkKind.TASK -> words.noTasks
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // A hairline between two rows, never under the last of them: at the foot of a sheet it
            // would be a line ruled across the gap and then the edge of the sheet itself.
            picker.options.forEach { option ->
                MiraDivider()
                MiraListRow(
                    title = option.label,
                    onClick = { onPick(option.id) },
                    trailing = if (option.id == picker.selectedId) {
                        { NoteSelectedMark() }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun NoteLinkPickerPreview() {
    MiraPreviewSurface {
        MiraListRow(
            title = EnglishNoteDetailWording.none,
            onClick = {},
            trailing = { NoteSelectedMark() },
        )
        MiraDivider()
        MiraListRow(title = "Rayluxeo", onClick = {})
    }
}
