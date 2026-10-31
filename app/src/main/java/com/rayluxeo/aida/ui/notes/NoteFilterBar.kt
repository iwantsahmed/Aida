package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.domain.model.NoteSort

/**
 * The five views, the labels in use, and the order the rows come back in.
 *
 * Chosen tags are written out as selected chips on the bar rather than hidden behind a sheet: a
 * filter Abu cannot see is a list he thinks is broken, and the one that catches people out is the
 * one set days ago and forgotten. Only tags that are actually on a note are offered, so a chip
 * can never lead to an empty list for a label nothing wears.
 */
@Composable
internal fun NoteFilterBar(
    view: NotesView,
    tags: List<NoteTagChip>,
    sort: NoteSortUiState,
    words: NotesWording,
    onSelectView: (NotesView) -> Unit,
    onToggleTag: (Long) -> Unit,
    onClearTags: () -> Unit,
    onSelectSort: (NoteSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    var showSort by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        ChipRow {
            NotesView.entries.forEach { option ->
                MiraChip(
                    text = words.viewLabel(option),
                    selected = option == view,
                    onClick = { onSelectView(option) },
                )
            }
        }
        Spacer(Modifier.height(spacing.xs))
        ChipRow {
            MiraChip(
                text = words.sortLabel(sort.sort),
                leadingIcon = MiraIcons.SwapVert,
                onClick = { showSort = true },
            )
            tags.forEach { tag ->
                MiraChip(
                    text = tag.label,
                    selected = tag.isSelected,
                    onClick = { onToggleTag(tag.id) },
                )
            }
            if (tags.any { it.isSelected }) {
                MiraChip(text = words.clearTags, onClick = onClearTags)
            }
        }
    }

    if (showSort) {
        NoteSortSheet(
            sort = sort,
            words = words,
            onSelectSort = onSelectSort,
            onDismiss = { showSort = false },
        )
    }
}

/**
 * A line of chips that scrolls sideways rather than wrapping, so the bar keeps its height however
 * many labels Abu has.
 */
@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
        content = content,
    )
}

/**
 * Choosing the sort already applied reverses it, so every order is one more tap away.
 *
 * Each row says the direction it would put the list in — the one that is applied for the chosen
 * sort, and the one it would start at for the others, which is newest-first for a moment and A to
 * Z for a title.
 *
 * It is the same object as a link picker — a list of names, one bearing a champagne tick, chosen
 * by tap — so it is ruled the same way and wears the same mark. It needs no cap of its own: the
 * orders are an enum, so this list cannot grow past the sheet the way a client book can.
 */
@Composable
private fun NoteSortSheet(
    sort: NoteSortUiState,
    words: NotesWording,
    onSelectSort: (NoteSort) -> Unit,
    onDismiss: () -> Unit,
) {
    MiraBottomSheet(onDismissRequest = onDismiss, title = words.sortTitle) {
        NoteSort.entries.forEachIndexed { index, option ->
            val selected = option == sort.sort
            // A hairline goes BETWEEN two rows and never under the last of them; the step of air
            // above the button is already separating it from the list.
            if (index > 0) MiraDivider()
            MiraListRow(
                title = words.sortLabel(option),
                supporting = words.sortDirection(
                    option,
                    if (selected) sort.ascending else option == NoteSort.TITLE,
                ),
                onClick = { onSelectSort(option) },
                trailing = if (selected) {
                    { NoteSelectedMark() }
                } else {
                    null
                },
            )
        }
        // The same step every sheet leaves above its buttons, so the four of them read as one
        // family rather than four near-misses.
        Spacer(Modifier.height(MiraTheme.spacing.lg))
        MiraButton(text = words.done, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Preview
@Composable
private fun NoteFilterBarPreview() {
    MiraPreviewSurface {
        NoteFilterBar(
            view = NotesView.ALL,
            tags = listOf(
                NoteTagChip(id = 1L, label = "rent", isSelected = true),
                NoteTagChip(id = 2L, label = "rayluxeo", isSelected = false),
            ),
            sort = NoteSortUiState(),
            words = EnglishNotesWording,
            onSelectView = {},
            onToggleTag = {},
            onClearTags = {},
            onSelectSort = {},
        )
    }
}
