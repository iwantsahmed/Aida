package com.rayluxeo.aida.ui.notes.detail

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.ui.notes.NoteSheetActionRow

/**
 * What can be done to the note itself, as opposed to its words.
 *
 * A note in the bin offers one verb and no others: pinning or filing it would be asking storage
 * for something it refuses, for a reason Abu could not see from here.
 *
 * The rows are [NoteSheetActionRow], the same ones the list's sheet draws. Deleting from here is
 * asked about in a dialog of its own, so the row only opens the question — that timing is the one
 * thing this sheet decides for itself.
 */
@Composable
internal fun NoteDetailActionsSheet(
    state: NoteDetailUiState,
    words: NoteDetailWording,
    onTogglePinned: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraBottomSheet(onDismissRequest = onDismiss, modifier = modifier, title = words.noteTitle) {
        if (state.isDeleted) {
            NoteSheetActionRow(
                title = words.restore,
                icon = MiraIcons.Undo,
                onClick = onRestore,
            )
        } else {
            // A hairline goes BETWEEN two rows and never under the last of them.
            NoteSheetActionRow(
                title = if (state.isPinned) words.unpin else words.pin,
                icon = MiraIcons.PushPin,
                onClick = onTogglePinned,
            )
            MiraDivider()
            NoteSheetActionRow(
                title = if (state.isArchived) words.unarchive else words.archive,
                icon = if (state.isArchived) MiraIcons.Unarchive else MiraIcons.Inventory2,
                onClick = if (state.isArchived) onRestore else onArchive,
            )
            MiraDivider()
            NoteSheetActionRow(
                title = words.delete,
                icon = MiraIcons.DeleteOutline,
                destructive = true,
                onClick = onDelete,
            )
        }
    }
}

@Preview
@Composable
private fun NoteDetailActionsSheetPreview() {
    MiraPreviewSurface {
        NoteSheetActionRow(
            title = EnglishNoteDetailWording.pin,
            icon = MiraIcons.PushPin,
            onClick = {},
        )
        MiraDivider()
        NoteSheetActionRow(
            title = EnglishNoteDetailWording.archive,
            icon = MiraIcons.Inventory2,
            onClick = {},
        )
        MiraDivider()
        NoteSheetActionRow(
            title = EnglishNoteDetailWording.delete,
            icon = MiraIcons.DeleteOutline,
            destructive = true,
            onClick = {},
        )
    }
}
