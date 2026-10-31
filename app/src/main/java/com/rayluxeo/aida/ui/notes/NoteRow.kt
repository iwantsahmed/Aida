package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * One note in the list: its words, when it was last touched, and a way to act on it.
 *
 * Nothing leads the row. A glyph for pinned or filed would only repeat the heading the row
 * already sits under ([NoteListItem] says why), and because it would appear on some rows and not
 * others it would leave two different left edges running down the same list — which is the first
 * thing that reads as unfinished on a screen made almost entirely of rows. With the glyph gone,
 * every title, every section heading and the search field above them share one edge.
 *
 * The "more" glyph is the row's only control besides the row itself: pinning and filing are
 * decisions worth a moment's thought, so they live in the sheet it opens, where they are named in
 * words. A row where the difference between opening a note and filing it is eight millimetres is
 * a row that files notes by accident.
 */
@Composable
internal fun NoteRow(
    note: NoteListItem,
    words: NotesWording,
    onOpen: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraListRow(
        title = note.title,
        modifier = modifier,
        supporting = note.supporting,
        onClick = onOpen,
        trailing = {
            MiraIconButton(
                icon = MiraIcons.MoreHoriz,
                contentDescription = words.moreActionsFor(note.title),
                onClick = onOpenActions,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Preview
@Composable
private fun NoteRowPreview() {
    MiraPreviewSurface {
        NoteRow(
            note = NoteListItem(
                id = 1L,
                title = "Lease renewal — what to ask the landlord",
                supporting = "Updated today, 9 am · rent, flat",
            ),
            words = EnglishNotesWording,
            onOpen = {},
            onOpenActions = {},
        )
        MiraDivider()
        NoteRow(
            note = NoteListItem(
                id = 2L,
                title = "Untitled",
                supporting = "Updated yesterday, 11 pm",
            ),
            words = EnglishNotesWording,
            onOpen = {},
            onOpenActions = {},
        )
    }
}
