package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskSort

/**
 * How the applied filters read as one line. The same separator the rows themselves use, so a
 * summary above the list and the detail under a title are plainly the same kind of sentence.
 */
private const val APPLIED_SEPARATOR = " · "

/**
 * The seven views, and the controls that narrow and order whichever one is showing.
 *
 * What is currently applied is written out under the controls rather than left inside the sheet
 * that set it: a filter Abu cannot see is a list he thinks is broken, and the one that catches
 * people out is the one set days ago and forgotten. It is a line of words rather than a row of
 * chips, because it is something to read and not something to press.
 */
@Composable
internal fun TaskFilterBar(
    view: TasksView,
    filters: TaskFiltersUiState,
    sort: TaskSortUiState,
    words: TasksWording,
    onSelectView: (TasksView) -> Unit,
    onSelectProject: (Long?) -> Unit,
    onSelectClient: (Long?) -> Unit,
    onToggleTag: (Long) -> Unit,
    onTogglePriority: (TaskPriority) -> Unit,
    onSelectSort: (TaskSort) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }

    val viewScroll = rememberScrollState()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fadingHorizontalEdges(viewScroll)
                .horizontalScroll(viewScroll),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            TasksView.entries.forEach { option ->
                MiraChip(
                    text = words.viewLabel(option),
                    selected = option == view,
                    onClick = { onSelectView(option) },
                )
            }
        }
        Spacer(Modifier.height(spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MiraChip(
                text = words.filtersTitle,
                selected = filters.isActive,
                leadingIcon = MiraIcons.FilterList,
                onClick = { showFilters = true },
            )
            MiraChip(
                text = words.sortLabel(sort.sort),
                leadingIcon = MiraIcons.SwapVert,
                onClick = { showSort = true },
            )
        }

        // What is applied is said in words on a line of its own rather than as a row of filled
        // chips beside the two that open sheets. Those chips were the same champagne as the
        // controls next to them and did nothing when pressed, which is both a dead control and a
        // fourth place for an accent that is only meant to be in one. Clear keeps its own target.
        if (filters.isActive) {
            Spacer(Modifier.height(spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = filters.appliedLabels.joinToString(APPLIED_SEPARATOR),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Ivory at label weight against muted body text beside it. Dropping the dead
                // champagne chip was right; rendering its replacement in exactly the colour of
                // the static line next to it was not — a control has to read as one, and this
                // is the only thing on the line that can be pressed. It takes weight and size
                // rather than the accent, which the chips above it are already spending.
                MiraButton(
                    text = words.clearFilters,
                    onClick = onClearFilters,
                    style = MiraButtonStyle.TEXT,
                    accent = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }

    if (showFilters) {
        TaskFiltersSheet(
            filters = filters,
            words = words,
            onSelectProject = onSelectProject,
            onSelectClient = onSelectClient,
            onToggleTag = onToggleTag,
            onTogglePriority = onTogglePriority,
            onClearFilters = onClearFilters,
            onDismiss = { showFilters = false },
        )
    }

    if (showSort) {
        TaskSortSheet(
            sort = sort,
            words = words,
            onSelectSort = onSelectSort,
            onDismiss = { showSort = false },
        )
    }
}

/**
 * Project and client are one choice each because a task has one of each; tags and priority are
 * several, because a task can wear several tags and Abu often wants two of the three priorities.
 * Sections with nothing to offer are left out rather than shown empty.
 */
@Composable
private fun TaskFiltersSheet(
    filters: TaskFiltersUiState,
    words: TasksWording,
    onSelectProject: (Long?) -> Unit,
    onSelectClient: (Long?) -> Unit,
    onToggleTag: (Long) -> Unit,
    onTogglePriority: (TaskPriority) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraBottomSheet(onDismissRequest = onDismiss, title = words.filtersTitle) {
        if (filters.projects.isNotEmpty()) {
            MiraSectionHeader(text = words.projectLabel)
            FacetChips(
                anyLabel = words.anyProject,
                facets = filters.projects,
                selectedId = filters.projectId,
                onSelect = onSelectProject,
            )
            Spacer(Modifier.height(spacing.md))
        }

        if (filters.clients.isNotEmpty()) {
            MiraSectionHeader(text = words.clientLabel)
            FacetChips(
                anyLabel = words.anyClient,
                facets = filters.clients,
                selectedId = filters.clientId,
                onSelect = onSelectClient,
            )
            Spacer(Modifier.height(spacing.md))
        }

        if (filters.tags.isNotEmpty()) {
            MiraSectionHeader(text = words.tagsLabel)
            ChipRow {
                filters.tags.forEach { tag ->
                    MiraChip(
                        text = tag.label,
                        selected = tag.id in filters.tagIds,
                        onClick = { onToggleTag(tag.id) },
                    )
                }
            }
            Spacer(Modifier.height(spacing.md))
        }

        MiraSectionHeader(text = words.priorityLabel)
        ChipRow {
            TaskPriority.entries.forEach { priority ->
                MiraChip(
                    text = words.priorityOptions[priority.ordinal],
                    selected = priority in filters.priorities,
                    onClick = { onTogglePriority(priority) },
                )
            }
        }
        Spacer(Modifier.height(spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (filters.isActive) {
                MiraButton(
                    text = words.clearFilters,
                    onClick = onClearFilters,
                    style = MiraButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
            }
            MiraButton(
                text = words.done,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A block of chips inside a sheet, wrapped onto as many lines as it takes.
 *
 * It used to scroll sideways, on the stated grounds that wrapping "would need `FlowRow`, which is
 * still opt-in layout API". That was simply untrue — `-opt-in=…ExperimentalLayoutApi` is set once
 * in `app/build.gradle.kts` and [FlowRow] is already used twice in this very feature, in the tag
 * picker — and it cost Abu something real: a sheet has no competing width, so a tag past the
 * right edge is a tag he has no reason to believe exists. Wrapping shows every one of them.
 *
 * The seven view chips on the list itself stay on one scrolling line deliberately, and not for
 * this reason: they are fixed chrome above every list, and a second permanent row of them would
 * push the rows down on every screen to save a swipe on one.
 */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    val spacing = MiraTheme.spacing
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        content()
    }
}

@Composable
private fun FacetChips(
    anyLabel: String,
    facets: List<TaskFacet>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    ChipRow {
        MiraChip(text = anyLabel, selected = selectedId == null, onClick = { onSelect(null) })
        facets.forEach { facet ->
            MiraChip(
                text = facet.label,
                selected = facet.id == selectedId,
                onClick = { onSelect(facet.id) },
            )
        }
    }
}

/** Choosing the sort already applied reverses it, so every order is reachable in one more tap. */
@Composable
private fun TaskSortSheet(
    sort: TaskSortUiState,
    words: TasksWording,
    onSelectSort: (TaskSort) -> Unit,
    onDismiss: () -> Unit,
) {
    MiraBottomSheet(onDismissRequest = onDismiss, title = words.sortTitle) {
        TaskSort.entries.forEach { option ->
            val selected = option == sort.sort
            MiraListRow(
                title = words.sortLabel(option),
                supporting = words.sortDirection(option, if (selected) sort.ascending else true),
                onClick = { onSelectSort(option) },
                trailing = if (selected) {
                    {
                        Icon(
                            imageVector = MiraIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(MiraTheme.spacing.md))
        MiraButton(text = words.done, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Preview
@Composable
private fun TaskFilterBarPreview() {
    MiraPreviewSurface {
        TaskFilterBar(
            view = TasksView.TODAY,
            filters = TaskFiltersUiState(
                projects = listOf(TaskFacet(1L, "Rayluxeo")),
                appliedLabels = listOf("Rayluxeo", "High"),
            ),
            sort = TaskSortUiState(),
            words = EnglishTasksWording,
            onSelectView = {},
            onSelectProject = {},
            onSelectClient = {},
            onToggleTag = {},
            onTogglePriority = {},
            onSelectSort = {},
            onClearFilters = {},
        )
    }
}
