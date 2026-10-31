package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * The champagne tick that says "this is the one".
 *
 * Every sheet that is a list of choices marks the chosen row with it — the sort sheet, the link
 * pickers — so the mark is declared once. It carries no content description on purpose: the row
 * it sits in is a single tappable thing, and a second announcement after the row's own title is
 * noise rather than information.
 */
@Composable
internal fun NoteSelectedMark() {
    Icon(
        imageVector = MiraIcons.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Preview
@Composable
private fun NoteSelectedMarkPreview() {
    MiraPreviewSurface {
        MiraListRow(title = EnglishNotesWording.done, onClick = {}, trailing = { NoteSelectedMark() })
    }
}
