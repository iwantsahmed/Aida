package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraErrorState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSearchField
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraSegmentedControl
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskSort
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

/** How long a notice stays up before it clears itself. Long enough to read and to undo. */
private const val NOTICE_MILLIS = 6_000L

/**
 * Holds the notice that is on its way out.
 *
 * A plain box rather than a `MutableState`, deliberately: the value is written and read inside one
 * composition, and making it observable would invalidate the very pass that just wrote it.
 */
private class NoticeHolder(var value: TasksNotice? = null)

/**
 * The task manager. Tasks and reminders share the screen because they are the same question asked
 * two ways — what is on, and what will interrupt me.
 */
@Composable
fun TasksScreen(
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TasksViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = remember(viewModel) { TasksCallbacks(viewModel) }

    TasksContent(state = state, callbacks = callbacks, onOpenTask = onOpenTask, modifier = modifier)
}

/**
 * Everything the screen can ask the view model to do, as one value.
 *
 * Twenty-odd lambdas threaded one at a time through three levels of composable was where a
 * handler last went missing, and the compiler had nothing to say about it.
 */
@Immutable
internal class TasksCallbacks(
    val onSelectTab: (TasksTab) -> Unit,
    val onSelectView: (TasksView) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSelectProject: (Long?) -> Unit,
    val onSelectClient: (Long?) -> Unit,
    val onToggleTag: (Long) -> Unit,
    val onTogglePriority: (TaskPriority) -> Unit,
    val onSelectSort: (TaskSort) -> Unit,
    val onClearFilters: () -> Unit,
    val onToggleTask: (Long, Boolean) -> Unit,
    val onOpenActions: (Long) -> Unit,
    val onCloseActions: () -> Unit,
    val onSnooze: (Long, SnoozeOption) -> Unit,
    val onSetArchived: (Long, Boolean) -> Unit,
    val onDuplicate: (Long) -> Unit,
    val onDeleteTask: (Long) -> Unit,
    val onStartSelection: (Long) -> Unit,
    val onToggleSelected: (Long) -> Unit,
    val onSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onLeaveSelection: () -> Unit,
    val onBulkComplete: () -> Unit,
    val onBulkArchive: () -> Unit,
    val onBulkDelete: () -> Unit,
    val onRequestCancelReminder: (Long) -> Unit,
    val onDismissCancelReminder: () -> Unit,
    val onCancelReminder: (Long) -> Unit,
    val onUnsnoozeReminder: (Long) -> Unit,
    val onWatchPlacesAgain: () -> Unit,
    val onDismissPlaceNotice: () -> Unit,
    val onOpenComposer: () -> Unit,
    val onCloseComposer: () -> Unit,
    val onComposerTitleChange: (String) -> Unit,
    val onComposerDateChange: (LocalDate?) -> Unit,
    val onComposerTimeChange: (LocalTime?) -> Unit,
    val onComposerPriorityChange: (TaskPriority) -> Unit,
    val onSaveTask: () -> Unit,
    val onOpenReminderComposer: () -> Unit,
    val onCloseReminderComposer: () -> Unit,
    val onSaveReminder: (String, LocalDate, LocalTime) -> Unit,
    val onUndo: () -> Unit,
    val onDismissNotice: () -> Unit,
) {
    constructor(viewModel: TasksViewModel) : this(
        onSelectTab = viewModel::onSelectTab,
        onSelectView = viewModel::onSelectView,
        onQueryChange = viewModel::onQueryChange,
        onSelectProject = viewModel::onSelectProject,
        onSelectClient = viewModel::onSelectClient,
        onToggleTag = viewModel::onToggleTag,
        onTogglePriority = viewModel::onTogglePriority,
        onSelectSort = viewModel::onSelectSort,
        onClearFilters = viewModel::onClearFilters,
        onToggleTask = viewModel::onToggleTask,
        onOpenActions = viewModel::onOpenActions,
        onCloseActions = viewModel::onCloseActions,
        onSnooze = viewModel::onSnoozeTask,
        onSetArchived = viewModel::onSetArchived,
        onDuplicate = viewModel::onDuplicateTask,
        onDeleteTask = viewModel::onDeleteTask,
        onStartSelection = viewModel::onStartSelection,
        onToggleSelected = viewModel::onToggleSelected,
        onSelectAll = viewModel::onSelectAllVisible,
        onClearSelection = viewModel::onClearSelection,
        onLeaveSelection = viewModel::onLeaveSelection,
        onBulkComplete = viewModel::onBulkComplete,
        onBulkArchive = viewModel::onBulkArchive,
        onBulkDelete = viewModel::onBulkDelete,
        onRequestCancelReminder = viewModel::onCancelReminderRequested,
        onDismissCancelReminder = viewModel::onDismissCancelReminder,
        onCancelReminder = viewModel::onCancelReminder,
        onUnsnoozeReminder = viewModel::onUnsnoozeReminder,
        onWatchPlacesAgain = viewModel::onWatchPlacesAgain,
        onDismissPlaceNotice = viewModel::onDismissPlaceNotice,
        onOpenComposer = viewModel::onOpenComposer,
        onCloseComposer = viewModel::onCloseComposer,
        onComposerTitleChange = viewModel::onComposerTitleChange,
        onComposerDateChange = viewModel::onComposerDateChange,
        onComposerTimeChange = viewModel::onComposerTimeChange,
        onComposerPriorityChange = viewModel::onComposerPriorityChange,
        onSaveTask = viewModel::onSaveTask,
        onOpenReminderComposer = viewModel::onOpenReminderComposer,
        onCloseReminderComposer = viewModel::onCloseReminderComposer,
        onSaveReminder = viewModel::onSaveReminder,
        onUndo = viewModel::onUndo,
        onDismissNotice = viewModel::onDismissNotice,
    )
}

@Composable
private fun TasksContent(
    state: TasksUiState,
    callbacks: TasksCallbacks,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val words = tasksWording(state.language)
    val reminderWords = remindersWording(state.language)
    val context = LocalContext.current

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        when (notice.tone) {
            NoticeTone.CONFIRM -> haptics.confirm()
            NoticeTone.PROBLEM -> haptics.error()
        }
        delay(NOTICE_MILLIS)
        callbacks.onDismissNotice()
    }

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = words.screenTitle,
                action = {
                    MiraIconButton(
                        icon = MiraIcons.Add,
                        contentDescription = if (state.tab == TasksTab.TASKS) words.addTask else words.addReminder,
                        onClick = if (state.tab == TasksTab.TASKS) callbacks.onOpenComposer else callbacks.onOpenReminderComposer,
                    )
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            if (state.isLoading) {
                MiraListSkeleton()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    MiraSegmentedControl(
                        options = words.tabLabels,
                        selectedIndex = state.tab.ordinal,
                        onSelect = { callbacks.onSelectTab(TasksTab.entries[it]) },
                    )
                    Spacer(Modifier.height(spacing.md))
                    when (state.tab) {
                        TasksTab.TASKS -> TasksPane(
                            state = state,
                            words = words,
                            callbacks = callbacks,
                            onOpenTask = onOpenTask,
                        )
                        TasksTab.REMINDERS -> RemindersPane(
                            reminders = state.reminders,
                            placeNotice = state.placeNotice,
                            words = words,
                            reminderWords = reminderWords,
                            onRequestCancelReminder = callbacks.onRequestCancelReminder,
                            onUnsnoozeReminder = callbacks.onUnsnoozeReminder,
                            onPlaceAction = { notice ->
                                // A refusal is answered by the Android screen that fixes it; the
                                // standing count is answered by trying again.
                                val refusal = notice.settingsFor
                                if (refusal != null) {
                                    context.openPlaceSettings(refusal)
                                } else {
                                    callbacks.onWatchPlacesAgain()
                                }
                            },
                            onDismissPlaceNotice = callbacks.onDismissPlaceNotice,
                        )
                    }
                }
            }

            // The notice rises into place and fades out again rather than blinking on and off.
            // The words it leaves with are the words it arrived with, so the holder keeps the
            // last one alive for exactly as long as the exit takes.
            val held = remember { NoticeHolder() }
            state.notice?.let { held.value = it }
            AnimatedVisibility(
                visible = state.notice != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = spacing.md),
                enter = fadeIn(MiraMotion.fade()) +
                    slideInVertically(MiraMotion.move()) { height -> height / 2 },
                exit = fadeOut(MiraMotion.exit()),
            ) {
                held.value?.let { notice ->
                    MiraInlineNotice(
                        text = notice.message,
                        tone = when (notice.tone) {
                            NoticeTone.CONFIRM -> MiraNoticeTone.CONFIRM
                            NoticeTone.PROBLEM -> MiraNoticeTone.DANGER
                        },
                        dismissContentDescription = words.dismiss,
                        onDismiss = callbacks.onDismissNotice,
                        actionText = notice.undoLabel,
                        onAction = callbacks.onUndo.takeIf { notice.undoLabel != null },
                    )
                }
            }
        }
    }

    state.composer?.let { composer ->
        TaskComposerSheet(
            state = composer,
            words = words,
            onTitleChange = callbacks.onComposerTitleChange,
            onDateChange = callbacks.onComposerDateChange,
            onTimeChange = callbacks.onComposerTimeChange,
            onPriorityChange = callbacks.onComposerPriorityChange,
            onSave = callbacks.onSaveTask,
            onDismiss = callbacks.onCloseComposer,
            onOpenFullEditor = {
                callbacks.onCloseComposer()
                onOpenTask(0L)
            },
            // The composer stays open on a failed save, so its reason is shown inside the sheet
            // rather than on the screen the sheet is covering.
            errorText = state.notice?.takeIf { it.tone == NoticeTone.PROBLEM }?.message,
        )
    }

    if (state.showReminderComposer) {
        ReminderComposerSheet(
            words = words,
            onDismiss = callbacks.onCloseReminderComposer,
            onSave = callbacks.onSaveReminder,
        )
    }

    // Cancelling a reminder is the one act on this screen with no undo behind it, so the second
    // tap comes before it rather than after. The danger tone sits here, on the control that
    // actually does it, and not on the row that opens the question.
    state.confirmCancelReminder?.let { pending ->
        MiraDialog(
            title = reminderWords.cancelTitle,
            onDismissRequest = callbacks.onDismissCancelReminder,
            confirmText = reminderWords.cancelConfirm,
            onConfirm = { callbacks.onCancelReminder(pending.id) },
            message = reminderWords.cancelMessage(pending.title),
            dismissText = reminderWords.keepReminder,
            onDismiss = callbacks.onDismissCancelReminder,
            destructive = true,
        )
    }

    state.actions?.let { actions ->
        TaskActionsSheet(
            state = actions,
            words = words,
            onSnooze = { option -> callbacks.onSnooze(actions.taskId, option) },
            onSetArchived = { archived -> callbacks.onSetArchived(actions.taskId, archived) },
            onDuplicate = { callbacks.onDuplicate(actions.taskId) },
            onDelete = { callbacks.onDeleteTask(actions.taskId) },
            onDismiss = callbacks.onCloseActions,
        )
    }
}

@Composable
private fun ColumnScope.TasksPane(
    state: TasksUiState,
    words: TasksWording,
    callbacks: TasksCallbacks,
    onOpenTask: (Long) -> Unit,
) {
    val spacing = MiraTheme.spacing

    // Nothing to search is not the same as nothing found. An empty view with no query typed has
    // no rows for the field to narrow, so it is left out rather than sitting over the empty
    // state governing nothing; a query that found nothing keeps its field, because that is the
    // one Abu has to be able to clear.
    if (!state.isEmpty || state.query.isNotBlank()) {
        MiraSearchField(
            value = state.query,
            onValueChange = callbacks.onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = words.searchPlaceholder,
        )
        Spacer(Modifier.height(spacing.sm))
    }

    // The bulk bar replaces the filter bar rather than stacking on it: one bar, one mode.
    if (state.selection.isActive) {
        TaskBulkBar(
            selection = state.selection,
            words = words,
            onSelectAll = callbacks.onSelectAll,
            onClearSelection = callbacks.onClearSelection,
            onLeaveSelection = callbacks.onLeaveSelection,
            onComplete = callbacks.onBulkComplete,
            onArchive = callbacks.onBulkArchive,
            onDelete = callbacks.onBulkDelete,
        )
    } else {
        TaskFilterBar(
            view = state.view,
            filters = state.filters,
            sort = state.sort,
            words = words,
            onSelectView = callbacks.onSelectView,
            onSelectProject = callbacks.onSelectProject,
            onSelectClient = callbacks.onSelectClient,
            onToggleTag = callbacks.onToggleTag,
            onTogglePriority = callbacks.onTogglePriority,
            onSelectSort = callbacks.onSelectSort,
            onClearFilters = callbacks.onClearFilters,
        )
    }
    // Twelve points hold the search field and the bar together as one piece of chrome; twenty-four
    // separate that chrome from the list. Both gaps were sixteen, which is a difference nobody
    // can feel and therefore no hierarchy at all.
    Spacer(Modifier.height(spacing.lg))

    // A list that failed to load is not an empty list, and it is not a line either: the view
    // model empties `sections` whenever it words an error, so there is no content underneath for
    // a notice to sit above. It owns the space the rows would have had, centred exactly like the
    // empty state one branch below and like the same failure on the detail screen — a red line
    // pinned under the filter bar with six hundred points of nothing beneath it reads as a
    // broken layout on top of a failed load.
    if (state.errorMessage != null) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            MiraErrorState(message = state.errorMessage)
        }
    } else if (state.isEmpty) {
        val searching = state.query.isNotBlank()
        // Most of Mira's lists are empty most of the time, so emptiness is centred in the space
        // it owns rather than left hanging under the filter bar with the screen blank beneath it.
        // It should read as a decision, not as a list that stopped loading.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            MiraEmptyState(
                icon = MiraIcons.TaskAlt,
                message = if (searching) {
                    words.noMatches(state.query.trim())
                } else {
                    words.emptyMessage(state.view)
                },
                actionText = if (searching) words.clearSearch else words.addFirstTask,
                onAction = if (searching) {
                    { callbacks.onQueryChange("") }
                } else {
                    callbacks.onOpenComposer
                },
            )
        }
    } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
            state.sections.forEachIndexed { index, section ->
                section.title?.let { title ->
                    item(key = "heading-${section.key}") {
                        // The gap belongs above the heading, not below the last row: a heading
                        // that sits closer to its own rows than to the ones before it is what
                        // makes a grouped list read as groups rather than as one long column.
                        if (index > 0) Spacer(Modifier.height(spacing.lg))
                        MiraSectionHeader(text = title)
                    }
                }
                itemsIndexed(
                    items = section.items,
                    key = { _, task -> "${section.key}-${task.id}" },
                ) { position, task ->
                    // A rule BETWEEN rows, never after the last one — a hairline under the final
                    // row of a section is a line with nothing on the other side of it. Inset to
                    // where the titles start, so the glyphs read as a column of their own.
                    if (position > 0) {
                        MiraDivider(
                            modifier = Modifier.padding(start = spacing.xxl + spacing.md),
                        )
                    }
                    TaskRow(
                        task = task,
                        words = words,
                        selecting = state.selection.isActive,
                        onToggleDone = { callbacks.onToggleTask(task.id, task.isDone) },
                        onOpen = { onOpenTask(task.id) },
                        onOpenActions = { callbacks.onOpenActions(task.id) },
                        onToggleSelected = { callbacks.onToggleSelected(task.id) },
                        onStartSelection = { callbacks.onStartSelection(task.id) },
                    )
                }
            }
            item(key = "tail") { Spacer(Modifier.height(spacing.xxl)) }
        }
    }
}

@Composable
private fun ReminderComposerSheet(
    words: TasksWording,
    onDismiss: () -> Unit,
    onSave: (title: String, date: LocalDate, time: LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().plusHours(1).withMinute(0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    MiraBottomSheet(onDismissRequest = onDismiss, modifier = modifier, title = words.newReminder) {
        MiraTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = words.reminderTitlePlaceholder,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MiraButton(
                style = MiraButtonStyle.SECONDARY,
                text = date.toString(),
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f),
                leadingIcon = MiraIcons.CalendarToday,
            )
            MiraButton(
                style = MiraButtonStyle.SECONDARY,
                text = "%02d:%02d".format(time.hour, time.minute),
                onClick = { showTimePicker = true },
                modifier = Modifier.weight(1f),
                leadingIcon = MiraIcons.Schedule,
            )
        }
        Spacer(Modifier.height(spacing.lg))

        MiraButton(
            text = words.addReminder,
            onClick = { onSave(title, date, time) },
            modifier = Modifier.fillMaxWidth(),
            enabled = title.isNotBlank(),
            leadingIcon = MiraIcons.Alarm,
        )
        Spacer(Modifier.height(spacing.xs))
    }

    if (showDatePicker) {
        TaskDatePickerDialog(
            initialDate = date,
            setText = words.set,
            cancelText = words.cancel,
            onPick = { picked ->
                date = picked
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTimePicker) {
        TaskTimePickerDialog(
            title = words.dueTimeTitle,
            initialTime = time,
            setText = words.set,
            cancelText = words.cancel,
            onPick = { picked ->
                time = picked
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}
