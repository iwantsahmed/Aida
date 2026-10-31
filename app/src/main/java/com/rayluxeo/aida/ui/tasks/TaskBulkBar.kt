package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * What replaces the filter bar once Abu is choosing rows.
 *
 * It replaces rather than stacks on top, so the list never carries two bars at once and the
 * screen says plainly which mode it is in. The way out is the first control on the line and it
 * performs nothing: leaving a selection is not a decision about the tasks in it.
 *
 * Every action is disabled with nothing chosen rather than hidden, because a bar whose contents
 * change shape as rows are ticked is harder to aim at than one that greys out.
 */
@Composable
internal fun TaskBulkBar(
    selection: TaskSelectionUiState,
    words: TasksWording,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onLeaveSelection: () -> Unit,
    onComplete: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val hasSelection = selection.count > 0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiraIconButton(
                icon = MiraIcons.Close,
                contentDescription = words.leaveSelection,
                onClick = onLeaveSelection,
            )
            Spacer(Modifier.width(spacing.xs))
            Text(
                text = words.selectedCount(selection.count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            MiraButton(
                text = if (selection.allSelected) words.clearSelection else words.selectAll,
                onClick = if (selection.allSelected) onClearSelection else onSelectAll,
                style = MiraButtonStyle.TEXT,
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            // Delete is a text button and the other two are filled, so they are not the same
            // height. Left to the default the short one hangs off the top of the line.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiraButton(
                text = words.complete,
                onClick = onComplete,
                style = MiraButtonStyle.SECONDARY,
                enabled = hasSelection,
                leadingIcon = MiraIcons.DoneAll,
            )
            MiraButton(
                text = words.archive,
                onClick = onArchive,
                style = MiraButtonStyle.SECONDARY,
                enabled = hasSelection,
                leadingIcon = MiraIcons.Inventory2,
            )
            // TEXT is the only style that takes the danger accent, and it carries no glyph, so
            // delete is worded rather than drawn. Its tone is what sets it apart from the rest.
            MiraButton(
                text = words.delete,
                onClick = onDelete,
                style = MiraButtonStyle.TEXT,
                enabled = hasSelection,
                accent = MiraTheme.colors.danger,
            )
        }
    }
}

@Preview
@Composable
private fun TaskBulkBarPreview() {
    MiraPreviewSurface {
        TaskBulkBar(
            selection = TaskSelectionUiState(isActive = true, ids = setOf(1L, 2L, 3L)),
            words = EnglishTasksWording,
            onSelectAll = {},
            onClearSelection = {},
            onLeaveSelection = {},
            onComplete = {},
            onArchive = {},
            onDelete = {},
        )
    }
}
