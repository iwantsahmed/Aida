package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraSegmentedControl
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.domain.model.TaskPriority
import java.time.LocalDate
import java.time.LocalTime

/**
 * Adding a task by hand. Only the title is required: a due moment is two separate decisions and
 * Abu is allowed to make neither, one or both.
 */
@Composable
internal fun TaskComposerSheet(
    state: TaskComposerUiState,
    words: TasksWording,
    onTitleChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFullEditor: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    MiraBottomSheet(onDismissRequest = onDismiss, modifier = modifier, title = words.newTask) {
        ComposerBody(
            state = state,
            words = words,
            onTitleChange = onTitleChange,
            onPriorityChange = onPriorityChange,
            onPickDate = { showDatePicker = true },
            onPickTime = { showTimePicker = true },
            onClearDate = { onDateChange(null) },
            onSave = onSave,
            onOpenFullEditor = onOpenFullEditor,
            errorText = errorText,
        )
    }

    if (showDatePicker) {
        TaskDatePickerDialog(
            initialDate = state.pickerDate,
            setText = words.set,
            cancelText = words.cancel,
            onPick = onDateChange,
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        TaskTimePickerDialog(
            title = words.dueTimeTitle,
            initialTime = state.pickerTime,
            setText = words.set,
            cancelText = words.cancel,
            onPick = onTimeChange,
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun ColumnScope.ComposerBody(
    state: TaskComposerUiState,
    words: TasksWording,
    onTitleChange: (String) -> Unit,
    onPriorityChange: (TaskPriority) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onClearDate: () -> Unit,
    onSave: () -> Unit,
    onOpenFullEditor: () -> Unit,
    errorText: String? = null,
) {
    val spacing = MiraTheme.spacing

    MiraTextField(
        value = state.title,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = words.taskTitlePlaceholder,
        enabled = !state.isSaving,
    )
    Spacer(Modifier.height(spacing.md))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        MiraButton(
            style = MiraButtonStyle.SECONDARY,
            text = state.dateLabel ?: words.addDate,
            onClick = onPickDate,
            modifier = Modifier.weight(1f),
            enabled = !state.isSaving,
            leadingIcon = MiraIcons.CalendarToday,
        )
        MiraButton(
            style = MiraButtonStyle.SECONDARY,
            text = state.timeLabel ?: words.addTime,
            onClick = onPickTime,
            modifier = Modifier.weight(1f),
            // A time with no day cannot become a due moment, so the date comes first.
            enabled = state.dueDate != null && !state.isSaving,
            leadingIcon = MiraIcons.Schedule,
        )
    }

    if (state.dueDate != null) {
        MiraButton(
            style = MiraButtonStyle.TEXT,
            text = words.clearDueDate,
            onClick = onClearDate,
            enabled = !state.isSaving,
            accent = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(spacing.md))

    Text(
        text = words.priorityLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(spacing.xs))
    MiraSegmentedControl(
        options = words.priorityOptions,
        selectedIndex = state.priority.ordinal,
        onSelect = { onPriorityChange(TaskPriority.entries[it]) },
        enabled = !state.isSaving,
    )
    Spacer(Modifier.height(spacing.lg))

    // Inside the sheet, not behind it: a save failure's reason was being drawn on the screen
    // underneath the modal scrim, so Save re-enabled with nothing said (§12).
    if (errorText != null) {
        MiraInlineNotice(
            text = errorText,
            tone = MiraNoticeTone.DANGER,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(spacing.sm))
    }

    MiraButton(
        text = words.addTask,
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canSave,
    )
    Spacer(Modifier.height(spacing.sm))
    MiraButton(
        style = MiraButtonStyle.SECONDARY,
        text = words.fullDetails,
        onClick = onOpenFullEditor,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isSaving,
    )
    Spacer(Modifier.height(spacing.xs))
}
