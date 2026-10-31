package com.rayluxeo.aida.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * How many whole rows a sheet's list may take before it starts scrolling inside itself.
 *
 * A slice of the next one is left showing beyond these, because a list that ends cleanly at its
 * own edge looks finished, while one with a row cut in half says there is more below it.
 */
private const val WHOLE_ROWS_SHOWN = 5

/**
 * The rows of a sheet that lists things, capped so that everything below them stays reachable.
 *
 * A sheet's body is a plain column with no scrolling of its own, so a list as long as the note or
 * as long as Abu's client book used to run past the bottom of the sheet — and with it went the
 * Create button, the Cancel button, and the last two thirds of the options. The sheet's only exit
 * was the scrim and the thing it exists to do could not be done (AGENTS.md §12).
 *
 * The cap is what fixes that: the list scrolls within its own bounds and whatever the sheet puts
 * under it keeps its place. It is declared once, so the editor's two long sheets cannot drift into
 * two different answers to the same problem.
 */
@Composable
internal fun NoteSheetList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = (spacing.xxl + spacing.md) * WHOLE_ROWS_SHOWN + spacing.lg)
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

@Preview
@Composable
private fun NoteSheetListPreview() {
    MiraPreviewSurface {
        NoteSheetList {
            MiraListRow(title = "Rayluxeo", onClick = {})
            MiraDivider()
            MiraListRow(title = "The landlord", onClick = {})
        }
    }
}
