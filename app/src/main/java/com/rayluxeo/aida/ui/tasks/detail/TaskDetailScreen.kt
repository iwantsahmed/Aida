package com.rayluxeo.aida.ui.tasks.detail

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraErrorState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraSegmentedControl
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskStatus
import com.rayluxeo.aida.domain.task.RecurrenceRule
import com.rayluxeo.aida.ui.tasks.TaskDatePickerDialog
import com.rayluxeo.aida.ui.tasks.TaskTimePickerDialog
import java.time.LocalDate
import java.time.LocalTime

/**
 * The estimates offered, in minutes.
 *
 * Chips rather than a free number field: Abu is guessing how long something will take, and a
 * guess to the minute is a false precision that nothing downstream can use.
 */
private val ESTIMATE_OPTIONS = listOf(15, 30, 45, 60, 90, 120, 240)

/**
 * One task, every field.
 *
 * The form is long, so it is grouped the way Abu thinks about a task rather than the way the
 * table stores one: what it is, then when it is due, then where it belongs, then the detail. An
 * empty field is not on screen at all — it waits behind an "add" chip at the end of its section,
 * because sixteen empty rows is a form to fill in rather than a task to glance at.
 */
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TaskDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The view model decides what leaving means — straight out, or a question first.
    BackHandler { viewModel.onBackRequested() }

    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onBack()
    }

    TaskDetailContent(
        state = state,
        actions = TaskDetailActions(
            onBackRequested = viewModel::onBackRequested,
            onTitleChange = viewModel::onTitleChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onNotesChange = viewModel::onNotesChange,
            onPriorityChange = viewModel::onPriorityChange,
            onDueDateChange = viewModel::onDueDateChange,
            onDueTimeChange = viewModel::onDueTimeChange,
            onReminderChange = viewModel::onReminderChange,
            onRecurrenceChange = viewModel::onRecurrenceChange,
            onProjectChange = viewModel::onProjectChange,
            onClientChange = viewModel::onClientChange,
            onEstimateChange = viewModel::onEstimateChange,
            onAddTag = viewModel::onAddTag,
            onRemoveTag = viewModel::onRemoveTag,
            onAddSubtask = viewModel::onAddSubtask,
            onToggleSubtask = viewModel::onToggleSubtask,
            onMoveSubtask = viewModel::onMoveSubtask,
            onRemoveSubtask = viewModel::onRemoveSubtask,
            onUnlinkAttachment = viewModel::onUnlinkAttachment,
            onRevealField = viewModel::onRevealField,
            onClearField = viewModel::onClearField,
            onOpenSheet = viewModel::onOpenSheet,
            onCloseSheet = viewModel::onCloseSheet,
            onDismissNotice = viewModel::onDismissNotice,
            onSave = viewModel::onSave,
            onSetDone = viewModel::onSetDone,
            onSetArchived = viewModel::onSetArchived,
            onDeleteRequested = viewModel::onDeleteRequested,
            onCancelDelete = viewModel::onCancelDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onKeepEditing = viewModel::onKeepEditing,
            onConfirmDiscard = viewModel::onConfirmDiscard,
        ),
        modifier = modifier,
    )
}

/**
 * What the editor can do, in one value.
 *
 * Thirty-one callbacks passed one by one would make every composable between here and the field
 * that uses them a parameter list nobody reads. Grouping them keeps the tree readable and keeps
 * the screen a pure function of its state.
 */
internal data class TaskDetailActions(
    val onBackRequested: () -> Unit,
    val onTitleChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onPriorityChange: (TaskPriority) -> Unit,
    val onDueDateChange: (LocalDate?) -> Unit,
    val onDueTimeChange: (LocalTime?) -> Unit,
    val onReminderChange: (ReminderLead?) -> Unit,
    val onRecurrenceChange: (RecurrenceRule?) -> Unit,
    val onProjectChange: (Long?) -> Unit,
    val onClientChange: (Long?) -> Unit,
    val onEstimateChange: (Int?) -> Unit,
    val onAddTag: (String) -> Unit,
    val onRemoveTag: (String) -> Unit,
    val onAddSubtask: (String) -> Unit,
    val onToggleSubtask: (Int) -> Unit,
    val onMoveSubtask: (Int, Boolean) -> Unit,
    val onRemoveSubtask: (Int) -> Unit,
    val onUnlinkAttachment: (Long) -> Unit,
    val onRevealField: (TaskDetailField) -> Unit,
    val onClearField: (TaskDetailField) -> Unit,
    val onOpenSheet: (TaskDetailSheet) -> Unit,
    val onCloseSheet: () -> Unit,
    val onDismissNotice: () -> Unit,
    val onSave: () -> Unit,
    val onSetDone: (Boolean) -> Unit,
    val onSetArchived: (Boolean) -> Unit,
    val onDeleteRequested: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onKeepEditing: () -> Unit,
    val onConfirmDiscard: () -> Unit,
)

@Composable
internal fun TaskDetailContent(
    state: TaskDetailUiState,
    actions: TaskDetailActions,
    modifier: Modifier = Modifier,
) {
    val words = taskDetailWording(state.language)

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = if (state.isNew) words.newTaskTitle else words.taskTitle,
                subtitle = if (state.isNew) null else words.statusLabel(state.status),
                onBack = actions.onBackRequested,
                backContentDescription = words.back,
                // A task that has not been saved has nothing to complete, archive or delete.
                action = if (state.isNew) {
                    null
                } else {
                    {
                        MiraIconButton(
                            icon = MiraIcons.MoreVert,
                            contentDescription = words.moreActions,
                            onClick = { actions.onOpenSheet(TaskDetailSheet.ACTIONS) },
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when {
                state.isLoading -> MiraListSkeleton()
                // Centred like the loader it replaces: a failure that lands a third of the way
                // down the screen reads as a broken layout on top of a broken load.
                state.errorMessage != null -> MiraErrorState(
                    message = state.errorMessage,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> TaskForm(state = state, words = words, actions = actions)
            }
        }
    }

    TaskDetailSheets(state = state, words = words, actions = actions)
    TaskDetailDialogs(state = state, words = words, actions = actions)
}

@Composable
private fun TaskForm(
    state: TaskDetailUiState,
    words: TaskDetailWording,
    actions: TaskDetailActions,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        state.notice?.let { notice ->
            MiraInlineNotice(
                text = notice.message,
                tone = notice.tone,
                dismissContentDescription = words.dismiss,
                onDismiss = actions.onDismissNotice,
            )
            Spacer(Modifier.height(spacing.md))
        }

        MiraTextField(
            value = state.title,
            onValueChange = actions.onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = words.titlePlaceholder,
            enabled = !state.isSaving,
            singleLine = false,
            maxLines = TITLE_MAX_LINES,
        )

        if (TaskDetailField.DESCRIPTION in state.visibleFields) {
            Spacer(Modifier.height(spacing.xs))
            MiraTextField(
                value = state.description,
                onValueChange = actions.onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = words.descriptionPlaceholder,
                enabled = !state.isSaving,
                singleLine = false,
                trailing = {
                    ClearFieldButton(TaskDetailField.DESCRIPTION, words, actions.onClearField)
                },
            )
        }

        Spacer(Modifier.height(spacing.md))
        FieldLabel(words.priorityLabel)
        MiraSegmentedControl(
            options = words.priorityOptions,
            selectedIndex = state.priority.ordinal,
            onSelect = { actions.onPriorityChange(TaskPriority.entries[it]) },
            enabled = !state.isSaving,
        )

        WhenSection(state = state, words = words, actions = actions)
        BelongsSection(state = state, words = words, actions = actions)
        DetailSection(state = state, words = words, actions = actions)
        AddFieldRow(fields = state.addableFields, words = words, onReveal = actions.onRevealField)
        HistorySection(state = state, words = words)

        // The one filled thing on the screen, and the last: everything above it is a field or a
        // quiet secondary, so the eye has exactly one place to land when the editing is done.
        Spacer(Modifier.height(spacing.sectionGap))
        MiraButton(
            text = words.save,
            onClick = actions.onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave,
        )
        Spacer(Modifier.height(spacing.xxl))
    }
}

@Composable
private fun ColumnScope.WhenSection(
    state: TaskDetailUiState,
    words: TaskDetailWording,
    actions: TaskDetailActions,
) {
    val spacing = MiraTheme.spacing
    val fields = state.visibleFields
    if (TaskDetailField.DUE !in fields &&
        TaskDetailField.REMINDER !in fields &&
        TaskDetailField.RECURRENCE !in fields
    ) {
        return
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Spacer(Modifier.height(spacing.sectionGap))
    MiraSectionHeader(text = words.sectionWhen)

    if (TaskDetailField.DUE in fields) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MiraButton(
                style = MiraButtonStyle.SECONDARY,
                text = state.dueDateLabel ?: words.addDate,
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f),
                enabled = !state.isSaving,
                leadingIcon = MiraIcons.CalendarToday,
            )
            MiraButton(
                style = MiraButtonStyle.SECONDARY,
                // All-day is what a date with no time is called, and the button says so rather
                // than inviting Abu to add a time he has already decided against.
                text = state.dueTimeLabel ?: words.addTime,
                onClick = { showTimePicker = true },
                modifier = Modifier.weight(1f),
                enabled = state.hasDueDate && !state.isSaving,
                leadingIcon = MiraIcons.Schedule,
            )
        }
        if (state.hasDueDate) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (state.dueTime != null) {
                    MiraButton(
                        style = MiraButtonStyle.TEXT,
                        text = words.clearDueTime,
                        onClick = { actions.onDueTimeChange(null) },
                        enabled = !state.isSaving,
                        accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MiraButton(
                    style = MiraButtonStyle.TEXT,
                    text = words.clearDueDate,
                    onClick = { actions.onClearField(TaskDetailField.DUE) },
                    enabled = !state.isSaving,
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (TaskDetailField.REMINDER in fields) {
        Spacer(Modifier.height(spacing.md))
        FieldLabel(words.fieldName(TaskDetailField.REMINDER))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MiraChip(
                text = words.reminderNone,
                selected = state.reminderLabel == null,
                onClick = { actions.onReminderChange(null) },
            )
            ReminderLead.entries.forEach { lead ->
                MiraChip(
                    text = words.reminderLeadLabel(lead),
                    selected = state.reminderLead == lead,
                    onClick = { actions.onReminderChange(lead) },
                )
            }
        }
        if (state.reminderLead == null && state.reminderLabel != null) {
            // A reminder Mira set by voice can sit at any offset; it is read back as the moment
            // it will fire rather than snapped to the nearest chip.
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = state.reminderLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (TaskDetailField.RECURRENCE in fields) {
        Spacer(Modifier.height(spacing.md))
        FieldRow(
            field = TaskDetailField.RECURRENCE,
            value = state.recurrenceLabel ?: words.repeatNever,
            words = words,
            onClick = { actions.onOpenSheet(TaskDetailSheet.RECURRENCE) },
            onClear = actions.onClearField,
        )
        if (state.recurrencePreview.isNotEmpty()) {
            Text(
                text = state.recurrencePreview.joinToString(PREVIEW_SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDatePicker) {
        TaskDatePickerDialog(
            initialDate = state.pickerDate,
            setText = words.set,
            cancelText = words.cancel,
            onPick = actions.onDueDateChange,
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        TaskTimePickerDialog(
            title = words.dueTimeTitle,
            initialTime = state.pickerTime,
            setText = words.set,
            cancelText = words.cancel,
            onPick = actions.onDueTimeChange,
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun ColumnScope.BelongsSection(
    state: TaskDetailUiState,
    words: TaskDetailWording,
    actions: TaskDetailActions,
) {
    val spacing = MiraTheme.spacing
    val fields = state.visibleFields
    if (TaskDetailField.PROJECT !in fields &&
        TaskDetailField.CLIENT !in fields &&
        TaskDetailField.TAGS !in fields
    ) {
        return
    }

    Spacer(Modifier.height(spacing.sectionGap))
    MiraSectionHeader(text = words.sectionBelongs)

    if (TaskDetailField.PROJECT in fields) {
        FieldRow(
            field = TaskDetailField.PROJECT,
            value = state.projectName ?: words.none,
            words = words,
            onClick = { actions.onOpenSheet(TaskDetailSheet.PROJECT) },
            onClear = actions.onClearField,
        )
    }
    if (TaskDetailField.CLIENT in fields) {
        FieldRow(
            field = TaskDetailField.CLIENT,
            value = state.clientName ?: words.none,
            words = words,
            onClick = { actions.onOpenSheet(TaskDetailSheet.CLIENT) },
            onClear = actions.onClearField,
        )
    }
    if (TaskDetailField.TAGS in fields) {
        FieldRow(
            field = TaskDetailField.TAGS,
            // The chips underneath ARE the value, and each one is the control that takes itself
            // off again. Writing the same names into the row as well listed them twice, one line
            // apart, in two different weights.
            value = if (state.tags.isEmpty()) words.none else null,
            words = words,
            onClick = { actions.onOpenSheet(TaskDetailSheet.TAGS) },
            onClear = actions.onClearField,
        )
        if (state.tags.isNotEmpty()) {
            SelectedTags(selected = state.tags, words = words, onRemove = actions.onRemoveTag)
        }
    }
}

@Composable
private fun ColumnScope.DetailSection(
    state: TaskDetailUiState,
    words: TaskDetailWording,
    actions: TaskDetailActions,
) {
    val spacing = MiraTheme.spacing
    val fields = state.visibleFields
    val hasAttachments = state.attachments.isNotEmpty()
    if (TaskDetailField.ESTIMATE !in fields &&
        TaskDetailField.SUBTASKS !in fields &&
        TaskDetailField.NOTES !in fields &&
        !hasAttachments
    ) {
        return
    }

    Spacer(Modifier.height(spacing.sectionGap))
    MiraSectionHeader(text = words.sectionDetail)

    if (TaskDetailField.ESTIMATE in fields) {
        FieldLabel(words.fieldName(TaskDetailField.ESTIMATE))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MiraChip(
                text = words.none,
                selected = state.estimatedMinutes == null,
                onClick = { actions.onEstimateChange(null) },
            )
            ESTIMATE_OPTIONS.forEach { minutes ->
                MiraChip(
                    text = words.estimateLabel(minutes),
                    selected = state.estimatedMinutes == minutes,
                    onClick = { actions.onEstimateChange(minutes) },
                )
            }
        }
        Spacer(Modifier.height(spacing.md))
    }

    if (TaskDetailField.SUBTASKS in fields) {
        FieldLabel(words.fieldName(TaskDetailField.SUBTASKS))
        SubtaskList(
            steps = state.subtasks,
            words = words,
            onAdd = actions.onAddSubtask,
            onToggle = actions.onToggleSubtask,
            onMove = actions.onMoveSubtask,
            onRemove = actions.onRemoveSubtask,
        )
        Spacer(Modifier.height(spacing.md))
    }

    if (TaskDetailField.NOTES in fields) {
        FieldLabel(words.fieldName(TaskDetailField.NOTES))
        MiraTextField(
            value = state.notes,
            onValueChange = actions.onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = words.notesPlaceholder,
            enabled = !state.isSaving,
            singleLine = false,
            trailing = { ClearFieldButton(TaskDetailField.NOTES, words, actions.onClearField) },
        )
        Spacer(Modifier.height(spacing.md))
    }

    // Attachments are shown, never added: Mira has no file picker yet, and a button that cannot
    // pick a file is worse than no button (AGENTS.md §12).
    if (hasAttachments) {
        FieldLabel(words.attachmentsLabel)
        state.attachments.forEach { attachment ->
            MiraListRow(
                title = attachment.name,
                supporting = attachment.detail,
                leading = {
                    // The same 48dp column the steps above it use, so the two lists in this
                    // section share one left edge instead of starting 24 points apart.
                    Box(
                        modifier = Modifier.size(spacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiraIcons.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailing = {
                    MiraIconButton(
                        icon = MiraIcons.Close,
                        contentDescription = words.unlinkAttachment(attachment.name),
                        onClick = { actions.onUnlinkAttachment(attachment.id) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

/**
 * When the task was made, last touched and finished.
 *
 * Three quiet lines rather than three list rows with rules between them. Nothing here can be
 * pressed, and a row of [com.rayluxeo.aida.core.design.component.MiraListRow]'s generous height
 * with a hairline under it is indistinguishable from the project and client rows above, which
 * can — so the form ended in three controls that do nothing, at the point where it should be
 * ending in the one control that does.
 */
@Composable
private fun ColumnScope.HistorySection(state: TaskDetailUiState, words: TaskDetailWording) {
    val created = state.createdLabel ?: return
    val spacing = MiraTheme.spacing
    val lines = buildList {
        add(words.createdLabel to created)
        state.updatedLabel?.let { add(words.updatedLabel to it) }
        state.completedLabel?.let { add(words.completedLabel to it) }
    }

    Spacer(Modifier.height(spacing.sectionGap))
    MiraSectionHeader(text = words.sectionHistory)
    // The gap goes BETWEEN the lines, not after each of them. A trailing spacer on the last one
    // made the run-up to Save four points longer on a saved task than on a new one, and a spacer
    // under the heading made this the one heading in the form that did not sit straight on its
    // own content. The form is meant to have one interval; two near-misses are how it stops
    // having one.
    lines.forEachIndexed { index, (label, value) ->
        if (index > 0) Spacer(Modifier.height(spacing.xxs))
        HistoryLine(label = label, value = value)
    }
}

/** One fact about the task's life, worded as the same "label — value" the rows above it use. */
@Composable
private fun HistoryLine(label: String, value: String) {
    Text(
        text = label + HISTORY_SEPARATOR + value,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ColumnScope.AddFieldRow(
    fields: List<TaskDetailField>,
    words: TaskDetailWording,
    onReveal: (TaskDetailField) -> Unit,
) {
    if (fields.isEmpty()) return
    val spacing = MiraTheme.spacing
    // Its own interval, not the one between two fields: adding a field is a different act from
    // filling one in, and at the tail of a long form it needs the room to say so.
    Spacer(Modifier.height(spacing.sectionGap))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        fields.forEach { field ->
            MiraChip(text = words.addField(field), onClick = { onReveal(field) })
        }
    }
}

/**
 * The name above a control that is not a text field.
 *
 * The gap under it is the same one [com.rayluxeo.aida.core.design.component.MiraTextField] puts
 * under its own label, so a labelled chip row and a labelled field sit at the same interval and
 * the form reads as one rhythm rather than two.
 */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(MiraTheme.spacing.xs))
}

@Composable
private fun FieldRow(
    field: TaskDetailField,
    value: String?,
    words: TaskDetailWording,
    onClick: () -> Unit,
    onClear: (TaskDetailField) -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraListRow(
        title = words.fieldName(field),
        modifier = modifier,
        supporting = value,
        onClick = onClick,
        trailing = { ClearFieldButton(field, words, onClear) },
    )
}

@Composable
private fun ClearFieldButton(
    field: TaskDetailField,
    words: TaskDetailWording,
    onClear: (TaskDetailField) -> Unit,
) {
    MiraIconButton(
        icon = MiraIcons.Close,
        contentDescription = words.clearField(field),
        onClick = { onClear(field) },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TaskDetailSheets(
    state: TaskDetailUiState,
    words: TaskDetailWording,
    actions: TaskDetailActions,
) {
    when (state.sheet) {
        TaskDetailSheet.NONE -> Unit
        TaskDetailSheet.RECURRENCE -> RecurrencePicker(
            rule = state.recurrence,
            preview = state.recurrencePreview,
            anchorDate = state.pickerDate,
            words = words,
            onRuleChange = actions.onRecurrenceChange,
            onDismiss = actions.onCloseSheet,
        )
        TaskDetailSheet.PROJECT -> OptionSheet(
            title = words.projectSheetTitle,
            options = state.projectOptions,
            selectedId = state.projectId,
            emptyMessage = words.noProjects,
            noneLabel = words.none,
            onSelect = {
                actions.onProjectChange(it)
                actions.onCloseSheet()
            },
            onDismiss = actions.onCloseSheet,
        )
        TaskDetailSheet.CLIENT -> OptionSheet(
            title = words.clientSheetTitle,
            options = state.clientOptions,
            selectedId = state.clientId,
            emptyMessage = words.noClients,
            noneLabel = words.none,
            onSelect = {
                actions.onClientChange(it)
                actions.onCloseSheet()
            },
            onDismiss = actions.onCloseSheet,
        )
        TaskDetailSheet.TAGS -> TagPicker(
            selected = state.tags,
            suggestions = state.tagSuggestions,
            words = words,
            onAdd = actions.onAddTag,
            onRemove = actions.onRemoveTag,
            onDismiss = actions.onCloseSheet,
        )
        TaskDetailSheet.ACTIONS -> ActionsSheet(state = state, words = words, actions = actions)
    }
}

/**
 * The verbs. Status is derived from them rather than picked, so this sheet offers what Abu can
 * *do* — and only the half of each pair that applies: a task that is open cannot be reopened.
 */
@Composable
private fun ActionsSheet(
    state: TaskDetailUiState,
    words: TaskDetailWording,
    actions: TaskDetailActions,
) {
    val haptics = rememberMiraHaptics()
    MiraBottomSheet(onDismissRequest = actions.onCloseSheet, title = words.actionsTitle) {
        val isDone = state.status == TaskStatus.DONE
        MiraListRow(
            title = if (isDone) words.reopen else words.complete,
            onClick = {
                haptics.confirm()
                actions.onSetDone(!isDone)
            },
        )
        MiraDivider()
        val isArchived = state.status == TaskStatus.ARCHIVED
        MiraListRow(
            title = if (isArchived) words.unarchive else words.archive,
            onClick = { actions.onSetArchived(!isArchived) },
        )
        MiraDivider()
        MiraListRow(title = words.delete, onClick = actions.onDeleteRequested)
        Spacer(Modifier.height(MiraTheme.spacing.md))
    }
}

@Composable
private fun OptionSheet(
    title: String,
    options: List<PickerOption>,
    selectedId: Long?,
    emptyMessage: String,
    noneLabel: String,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    MiraBottomSheet(onDismissRequest = onDismiss, title = title) {
        if (options.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MiraListRow(
                title = noneLabel,
                onClick = { onSelect(null) },
                trailing = { if (selectedId == null) SelectedMark() },
            )
            options.forEach { option ->
                MiraDivider()
                MiraListRow(
                    title = option.label,
                    supporting = option.supporting,
                    onClick = { onSelect(option.id) },
                    trailing = { if (selectedId == option.id) SelectedMark() },
                )
            }
        }
        Spacer(Modifier.height(MiraTheme.spacing.md))
    }
}

@Composable
private fun SelectedMark() {
    Icon(
        imageVector = MiraIcons.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TaskDetailDialogs(
    state: TaskDetailUiState,
    words: TaskDetailWording,
    actions: TaskDetailActions,
) {
    if (state.confirmDiscard) {
        MiraDialog(
            title = words.discardTitle,
            onDismissRequest = actions.onKeepEditing,
            confirmText = words.discardConfirm,
            onConfirm = actions.onConfirmDiscard,
            message = words.discardMessage,
            dismissText = words.keepEditing,
            onDismiss = actions.onKeepEditing,
            destructive = true,
        ) {
            // Leaving with edits is no longer only Discard vs Keep editing: when the edits are
            // valid, Save them here instead of forcing a scroll back to the bottom Save button.
            if (state.canSave) {
                MiraButton(
                    text = words.save,
                    onClick = actions.onSave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (state.confirmDelete) {
        MiraDialog(
            title = words.deleteTitle,
            onDismissRequest = actions.onCancelDelete,
            confirmText = words.delete,
            onConfirm = actions.onConfirmDelete,
            message = words.deleteMessage,
            dismissText = words.cancel,
            onDismiss = actions.onCancelDelete,
            destructive = true,
        )
    }
}

/** Long titles wrap, but a title that fills the screen is a note rather than a task. */
private const val TITLE_MAX_LINES = 3

private const val PREVIEW_SEPARATOR = "  ·  "

/**
 * Between a fact's name and the fact. The same dash [com.rayluxeo.aida.ui.tasks.TaskTimeFormatter]
 * puts in front of an overdue moment, so "Created — Today, 9 am" and "Overdue — Yesterday, 9 am"
 * are plainly the same sentence in both languages.
 */
private const val HISTORY_SEPARATOR = " — "

@Preview
@Composable
private fun TaskDetailPreview() {
    MiraPreviewSurface {
        TaskDetailContent(
            state = TaskDetailUiState(
                isLoading = false,
                isNew = false,
                title = "Finish the Rayluxeo homepage",
                dueDateLabel = "16 March",
                dueTimeLabel = "10 am",
                pickerDate = LocalDate.of(2026, 3, 16),
                pickerTime = LocalTime.of(10, 0),
                visibleFields = setOf(TaskDetailField.DUE),
                addableFields = listOf(TaskDetailField.DESCRIPTION, TaskDetailField.TAGS),
                createdLabel = "Today, 9 am",
                updatedLabel = "Today, 9 am",
            ),
            actions = previewActions(),
        )
    }
}

private fun previewActions(): TaskDetailActions = TaskDetailActions(
    onBackRequested = {},
    onTitleChange = {},
    onDescriptionChange = {},
    onNotesChange = {},
    onPriorityChange = {},
    onDueDateChange = {},
    onDueTimeChange = {},
    onReminderChange = {},
    onRecurrenceChange = {},
    onProjectChange = {},
    onClientChange = {},
    onEstimateChange = {},
    onAddTag = {},
    onRemoveTag = {},
    onAddSubtask = {},
    onToggleSubtask = {},
    onMoveSubtask = { _, _ -> },
    onRemoveSubtask = {},
    onUnlinkAttachment = {},
    onRevealField = {},
    onClearField = {},
    onOpenSheet = {},
    onCloseSheet = {},
    onDismissNotice = {},
    onSave = {},
    onSetDone = {},
    onSetArchived = {},
    onDeleteRequested = {},
    onCancelDelete = {},
    onConfirmDelete = {},
    onKeepEditing = {},
    onConfirmDiscard = {},
)
