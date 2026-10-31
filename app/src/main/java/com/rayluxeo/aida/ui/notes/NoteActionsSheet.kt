package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * What can be done to one note without opening it.
 *
 * Which verbs appear is decided from the note rather than from the list it was tapped in: a note
 * in the bin can only come back, and offering to pin or file it would be offering something
 * storage refuses for a reason Abu cannot see from here.
 *
 * Deleting is coloured rather than confirmed: the bin is soft and the notice that follows carries
 * an undo, so a second dialog would be a question Abu has already been given the answer to.
 */
@Composable
internal fun NoteActionsSheet(
    state: NoteActionsUiState,
    words: NotesWording,
    onTogglePinned: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = state.title.takeIf { it.isNotBlank() } ?: words.actionsTitle,
    ) {
        if (state.isDeleted) {
            NoteSheetActionRow(
                title = words.restore,
                icon = MiraIcons.Undo,
                onClick = {
                    onRestore()
                    onDismiss()
                },
            )
        } else {
            // A hairline goes BETWEEN two rows and never under the last of them: at the foot of a
            // sheet it would be a line ruled across the gap and then the edge of the sheet itself.
            NoteSheetActionRow(
                title = if (state.isPinned) words.unpin else words.pin,
                icon = MiraIcons.PushPin,
                onClick = {
                    onTogglePinned()
                    onDismiss()
                },
            )
            MiraDivider()
            NoteSheetActionRow(
                title = if (state.isArchived) words.unarchive else words.archive,
                icon = if (state.isArchived) MiraIcons.Unarchive else MiraIcons.Inventory2,
                onClick = {
                    if (state.isArchived) onRestore() else onArchive()
                    onDismiss()
                },
            )
            MiraDivider()
            NoteSheetActionRow(
                title = words.delete,
                icon = MiraIcons.DeleteOutline,
                destructive = true,
                onClick = {
                    onDelete()
                    onDismiss()
                },
            )
        }
    }
}

@Preview
@Composable
private fun NoteActionsSheetPreview() {
    MiraPreviewSurface {
        NoteSheetActionRow(
            title = EnglishNotesWording.pin,
            icon = MiraIcons.PushPin,
            onClick = {},
        )
        MiraDivider()
        NoteSheetActionRow(
            title = EnglishNotesWording.archive,
            icon = MiraIcons.Inventory2,
            onClick = {},
        )
        MiraDivider()
        NoteSheetActionRow(
            title = EnglishNotesWording.delete,
            icon = MiraIcons.DeleteOutline,
            destructive = true,
            onClick = {},
        )
    }
}
