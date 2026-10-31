package com.rayluxeo.aida.ui.notes.detail

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraErrorState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import com.rayluxeo.aida.ui.notes.NOTE_NOTICE_MILLIS
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

/** The body field is generous but bounded; past this the field scrolls rather than the screen. */
private const val BODY_MAX_LINES = 12

/**
 * One note, open.
 *
 * [onOpenInkPage] has no default: the canvas is in the navigation graph, so every caller can reach
 * it, and a default of null would be the "kept for later" parameter that makes a row silently
 * refuse the tap. A page whose files were reclaimed is still shown — Abu needs to know the writing
 * was there — but it is the one page that does not open.
 */
@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    onAskMira: () -> Unit,
    onOpenInkPage: (Long) -> Unit,
    onNewInkPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: NoteDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The view model decides what leaving means — straight out, or a question first.
    BackHandler { viewModel.onBackRequested() }

    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onBack()
    }

    NoteDetailContent(
        state = state,
        actions = NoteDetailActions(
            onBackRequested = viewModel::onBackRequested,
            onTitleChange = viewModel::onTitleChange,
            onBodyChange = viewModel::onBodyChange,
            onSave = viewModel::onSave,
            onOpenSheet = viewModel::onOpenSheet,
            onCloseSheet = viewModel::onCloseSheet,
            onTogglePinned = viewModel::onTogglePinned,
            onArchive = viewModel::onArchive,
            onRestore = viewModel::onRestore,
            onDeleteRequested = viewModel::onDeleteRequested,
            onCancelDelete = viewModel::onCancelDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onKeepEditing = viewModel::onKeepEditing,
            onConfirmDiscard = viewModel::onConfirmDiscard,
            onAddTag = viewModel::onAddTag,
            onRemoveTag = viewModel::onRemoveTag,
            onPickLink = viewModel::onPickLink,
            onToggleOrganizeTitle = viewModel::onToggleOrganizeTitle,
            onToggleOrganizeTag = viewModel::onToggleOrganizeTag,
            onToggleOrganizeClient = viewModel::onToggleOrganizeClient,
            onToggleOrganizeProject = viewModel::onToggleOrganizeProject,
            onApplyOrganize = viewModel::onApplyOrganize,
            onToggleCandidate = viewModel::onToggleCandidate,
            onCreateTasks = viewModel::onCreateTasks,
            onCreateReminder = viewModel::onCreateReminder,
            onCreateInvoiceDraft = viewModel::onCreateInvoiceDraft,
            onDismissNotice = viewModel::onDismissNotice,
        ),
        onAskMira = onAskMira,
        onOpenInkPage = onOpenInkPage,
        onNewInkPage = onNewInkPage,
        modifier = modifier,
    )
}

/**
 * What the editor can do, in one value.
 *
 * Twenty-odd callbacks passed one at a time would make every composable between here and the
 * field that uses them a parameter list nobody reads.
 */
internal data class NoteDetailActions(
    val onBackRequested: () -> Unit,
    val onTitleChange: (String) -> Unit,
    val onBodyChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onOpenSheet: (NoteDetailSheet) -> Unit,
    val onCloseSheet: () -> Unit,
    val onTogglePinned: () -> Unit,
    val onArchive: () -> Unit,
    val onRestore: () -> Unit,
    val onDeleteRequested: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onKeepEditing: () -> Unit,
    val onConfirmDiscard: () -> Unit,
    val onAddTag: (String) -> Unit,
    val onRemoveTag: (Long) -> Unit,
    val onPickLink: (Long?) -> Unit,
    val onToggleOrganizeTitle: () -> Unit,
    val onToggleOrganizeTag: (Long) -> Unit,
    val onToggleOrganizeClient: () -> Unit,
    val onToggleOrganizeProject: () -> Unit,
    val onApplyOrganize: () -> Unit,
    val onToggleCandidate: (Int) -> Unit,
    val onCreateTasks: () -> Unit,
    val onCreateReminder: (LocalDate, LocalTime) -> Unit,
    val onCreateInvoiceDraft: () -> Unit,
    val onDismissNotice: () -> Unit,
)

@Composable
internal fun NoteDetailContent(
    state: NoteDetailUiState,
    actions: NoteDetailActions,
    onAskMira: () -> Unit,
    onOpenInkPage: (Long) -> Unit,
    onNewInkPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val words = noteDetailWording(state.language)
    var isPickingReminder by remember { mutableStateOf(false) }

    // The hand is told before the eye, and the line takes itself away afterwards. The list does
    // both; the editor used to do neither, so a confirmation sat on the note until Abu closed it.
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        when (notice.tone) {
            MiraNoticeTone.CONFIRM -> haptics.confirm()
            MiraNoticeTone.DANGER -> haptics.error()
            MiraNoticeTone.INFO -> Unit
        }
        delay(NOTE_NOTICE_MILLIS)
        actions.onDismissNotice()
    }

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = if (state.isNew) words.newNoteTitle else words.noteTitle,
                onBack = actions.onBackRequested,
                backContentDescription = words.back,
                // A note that has not been saved has nothing to pin, file or delete — and neither
                // has one that would not load. Every verb in that sheet needs the stored note, so
                // over the error state the glyph opened a sheet whose three rows all did nothing
                // and did not even close it behind them (AGENTS.md §12).
                action = if (state.isNew || state.errorMessage != null) {
                    null
                } else {
                    {
                        MiraIconButton(
                            icon = MiraIcons.MoreVert,
                            contentDescription = words.moreActions,
                            onClick = { actions.onOpenSheet(NoteDetailSheet.ACTIONS) },
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when {
                state.isLoading -> MiraListSkeleton()
                // Centred for the same reason the loading arc is: a failure that owns the whole
                // screen should sit in the middle of the screen it owns, not in the top third of
                // it with everything below left blank.
                state.errorMessage != null -> MiraErrorState(
                    message = state.errorMessage,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> NoteForm(
                    state = state,
                    words = words,
                    actions = actions,
                    onAskMira = onAskMira,
                    onOpenInkPage = onOpenInkPage,
                    onNewInkPage = onNewInkPage,
                    onPickReminder = { isPickingReminder = true },
                )
            }

            // Floated rather than pushed in at the top of the form, which is where it used to sit:
            // inside the scrolling column a line raised while Abu was reading the bottom of a long
            // note appeared above the viewport and he never saw it.
            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = notice.message,
                    tone = notice.tone,
                    dismissContentDescription = words.dismiss,
                    onDismiss = actions.onDismissNotice,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = spacing.md),
                )
            }
        }
    }

    NoteDetailSheets(state = state, words = words, actions = actions)
    NoteDetailDialogs(state = state, words = words, actions = actions)

    if (isPickingReminder && state.reminderDate != null && state.reminderTime != null) {
        NoteReminderDialogs(
            initialDate = state.reminderDate,
            initialTime = state.reminderTime,
            words = words,
            onPick = { date, time ->
                actions.onCreateReminder(date, time)
                isPickingReminder = false
            },
            onDismiss = { isPickingReminder = false },
        )
    }
}

@Composable
private fun NoteForm(
    state: NoteDetailUiState,
    words: NoteDetailWording,
    actions: NoteDetailActions,
    onAskMira: () -> Unit,
    onOpenInkPage: (Long) -> Unit,
    onNewInkPage: () -> Unit,
    onPickReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // A binned note says so before anything else, and offers the one verb that undoes it. It
        // belongs in the flow rather than floating like a notice, because it is not transient:
        // it is a standing fact about the note, and it goes only when the note comes back.
        if (state.isDeleted) {
            MiraInlineNotice(
                text = words.inBin,
                tone = MiraNoticeTone.DANGER,
                actionText = words.restore,
                onAction = actions.onRestore,
            )
            Spacer(Modifier.height(spacing.sm))
        }

        MiraTextField(
            value = state.title,
            onValueChange = actions.onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = words.titlePlaceholder,
            enabled = state.canEdit,
            singleLine = false,
        )
        Spacer(Modifier.height(spacing.xs))
        MiraTextField(
            value = state.body,
            onValueChange = actions.onBodyChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = words.bodyPlaceholder,
            enabled = state.canEdit,
            singleLine = false,
            maxLines = BODY_MAX_LINES,
        )

        // Only while there is something to save. See `NoteDetailUiState.isSaveOffered`.
        AnimatedVisibility(
            visible = state.isSaveOffered,
            // The height is movement, so it springs; the opacity is not, so it curves. Both at
            // once is the rule in MiraMotion, and a spring on alpha only makes light flicker.
            enter = fadeIn(MiraMotion.fade()) + expandVertically(MiraMotion.move()),
            exit = fadeOut(MiraMotion.fade(MiraMotion.QUICK_MS)) +
                shrinkVertically(MiraMotion.move()),
            label = "NoteSave",
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(spacing.md))
                MiraButton(
                    text = words.save,
                    onClick = actions.onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSave,
                )
            }
        }

        // Everything below belongs to a note that exists: a label or a link needs a row to hang on.
        if (!state.isNew) {
            NoteLabelsSection(state = state, words = words, actions = actions)
            NoteBelongsSection(state = state, words = words, actions = actions)
            NoteCarriedSection(
                state = state,
                words = words,
                onOpenInkPage = onOpenInkPage,
                onNewInkPage = onNewInkPage,
            )
            NoteActionSection(
                state = state,
                words = words,
                actions = actions,
                onAskMira = onAskMira,
                onPickReminder = onPickReminder,
            )
            NoteHistorySection(state = state, words = words)
        }
        Spacer(Modifier.height(spacing.xxl))
    }
}

@Composable
private fun NoteLabelsSection(
    state: NoteDetailUiState,
    words: NoteDetailWording,
    actions: NoteDetailActions,
) {
    Spacer(Modifier.height(MiraTheme.spacing.sectionGap))
    MiraSectionHeader(text = words.sectionLabels)
    MiraListRow(
        title = state.tags.takeIf { it.isNotEmpty() }?.joinToString { it.name } ?: words.noTags,
        onClick = if (state.canEdit) {
            { actions.onOpenSheet(NoteDetailSheet.TAGS) }
        } else {
            null
        },
        enabled = state.canEdit,
    )
}

/**
 * A hairline goes BETWEEN two rows and never under the last of them.
 *
 * Every section on this page used to close with one, which drew a line to nothing across the
 * thirty-two points of air that already separate one section from the next — the same fault, and
 * the same fix, as the list of notes.
 */
@Composable
private fun NoteBelongsSection(
    state: NoteDetailUiState,
    words: NoteDetailWording,
    actions: NoteDetailActions,
) {
    Spacer(Modifier.height(MiraTheme.spacing.sectionGap))
    MiraSectionHeader(text = words.sectionBelongs)
    LinkRow(
        label = words.clientLabel,
        value = state.clientLabel ?: words.none,
        enabled = state.canEdit,
        onClick = { actions.onOpenSheet(NoteDetailSheet.CLIENT) },
    )
    MiraDivider()
    LinkRow(
        label = words.projectLabel,
        value = state.projectLabel ?: words.none,
        enabled = state.canEdit,
        onClick = { actions.onOpenSheet(NoteDetailSheet.PROJECT) },
    )
    MiraDivider()
    LinkRow(
        label = words.taskLabel,
        value = state.taskLabel ?: words.none,
        enabled = state.canEdit,
        onClick = { actions.onOpenSheet(NoteDetailSheet.TASK) },
    )
}

@Composable
private fun LinkRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MiraListRow(
        title = label,
        supporting = value,
        onClick = onClick.takeIf { enabled },
        enabled = enabled,
    )
}

/**
 * What the note carries besides its words: pages of handwriting, files, and the recording it was
 * written up from. Each section is left out entirely when there is nothing in it — a heading over
 * nothing is noise, not information.
 *
 * The line under the pages is where Abu is told that handwriting is read as he writes it. It sits
 * here, beside the pages it is about, rather than in the action row as a control that could only
 * ever be dimmed.
 *
 * None of these rows leads with a glyph. The heading above each one already says what the rows
 * are, so a pen beside every page and a paperclip beside every file repeated it — and because the
 * labels, the links and the history above and below carry no glyph, those rows were the only ones
 * on the screen whose titles started forty pixels in. One left edge, all the way down, is worth
 * more than four decorative marks.
 */
@Composable
private fun NoteCarriedSection(
    state: NoteDetailUiState,
    words: NoteDetailWording,
    onOpenInkPage: (Long) -> Unit,
    onNewInkPage: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    // Offered on any saved note Abu can still edit, not only one that already has pages: a note
    // with no handwriting is exactly the note that needs a way to start some. A note he has not
    // saved yet has no id for a page to hang on, so it waits.
    val canAddInk = !state.isNew && state.canEdit
    if (state.inkPages.isNotEmpty() || canAddInk) {
        Spacer(Modifier.height(spacing.sectionGap))
        MiraSectionHeader(text = words.sectionHandwriting)
        state.inkPages.forEachIndexed { index, page ->
            if (index > 0) MiraDivider()
            MiraListRow(
                title = if (page.isLost) words.inkLost else page.label,
                supporting = if (page.isLost) page.label else null,
                // The one page that does not open is the one whose ink is gone.
                onClick = if (page.isLost) null else ({ onOpenInkPage(page.id) }),
            )
        }
        if (canAddInk) {
            if (state.inkPages.isNotEmpty()) MiraDivider()
            MiraListRow(title = words.addHandwriting, onClick = onNewInkPage)
        }
        // No spacer: the row above already carries twelve points of padding under its words, so
        // the optical gap is a step on the scale. Adding eight more made it twenty, which is not.
        Text(
            text = words.inkReadWhileWriting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (state.attachments.isNotEmpty()) {
        Spacer(Modifier.height(spacing.sectionGap))
        MiraSectionHeader(text = words.sectionAttachments)
        state.attachments.forEachIndexed { index, attachment ->
            if (index > 0) MiraDivider()
            MiraListRow(title = attachment.name, supporting = attachment.detail)
        }
    }

    state.voiceNote?.let { voice ->
        Spacer(Modifier.height(spacing.sectionGap))
        MiraSectionHeader(text = words.sectionVoice)
        MiraListRow(title = voice.label)
    }
}

@Composable
private fun NoteActionSection(
    state: NoteDetailUiState,
    words: NoteDetailWording,
    actions: NoteDetailActions,
    onAskMira: () -> Unit,
    onPickReminder: () -> Unit,
) {
    Spacer(Modifier.height(MiraTheme.spacing.sectionGap))
    MiraSectionHeader(text = words.sectionActions)
    NoteActionRow(
        actions = state.actions,
        words = words,
        onAction = { action ->
            when (action) {
                NoteAction.ORGANIZE -> actions.onOpenSheet(NoteDetailSheet.ORGANIZE)
                NoteAction.TURN_INTO_TASKS -> actions.onOpenSheet(NoteDetailSheet.TASKS)
                NoteAction.CREATE_REMINDER -> onPickReminder()
                NoteAction.LINK_CLIENT -> actions.onOpenSheet(NoteDetailSheet.CLIENT)
                NoteAction.CREATE_INVOICE_DRAFT -> actions.onCreateInvoiceDraft()
                NoteAction.ASK_MIRA -> onAskMira()
            }
        },
    )
}

@Composable
private fun NoteHistorySection(state: NoteDetailUiState, words: NoteDetailWording) {
    val spacing = MiraTheme.spacing
    val lines = listOfNotNull(state.createdLabel, state.updatedLabel)
    if (lines.isEmpty()) return
    Spacer(Modifier.height(spacing.sectionGap))
    MiraSectionHeader(text = words.sectionHistory)
    // Spaced between the lines rather than after each of them: a gap below the last one is a hole
    // added to the gap the page already ends with.
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteDetailSheets(
    state: NoteDetailUiState,
    words: NoteDetailWording,
    actions: NoteDetailActions,
) {
    when (state.sheet) {
        null -> Unit
        NoteDetailSheet.ACTIONS -> NoteDetailActionsSheet(
            state = state,
            words = words,
            onTogglePinned = actions.onTogglePinned,
            onArchive = actions.onArchive,
            onRestore = actions.onRestore,
            onDelete = actions.onDeleteRequested,
            onDismiss = actions.onCloseSheet,
        )
        NoteDetailSheet.TAGS -> NoteTagPicker(
            selected = state.tags,
            suggestions = state.tagSuggestions,
            words = words,
            onAdd = actions.onAddTag,
            onRemove = actions.onRemoveTag,
            onDismiss = actions.onCloseSheet,
        )
        NoteDetailSheet.CLIENT,
        NoteDetailSheet.PROJECT,
        NoteDetailSheet.TASK,
        -> state.linkPicker?.let { picker ->
            NoteLinkPicker(
                picker = picker,
                words = words,
                onPick = actions.onPickLink,
                onDismiss = actions.onCloseSheet,
            )
        }
        NoteDetailSheet.ORGANIZE -> state.organize?.let { organize ->
            NoteOrganizeSheet(
                state = organize,
                words = words,
                onToggleTitle = actions.onToggleOrganizeTitle,
                onToggleTag = actions.onToggleOrganizeTag,
                onToggleClient = actions.onToggleOrganizeClient,
                onToggleProject = actions.onToggleOrganizeProject,
                onApply = actions.onApplyOrganize,
                onDismiss = actions.onCloseSheet,
            )
        }
        NoteDetailSheet.TASKS -> state.tasks?.let { tasks ->
            NoteTasksSheet(
                state = tasks,
                words = words,
                onToggle = actions.onToggleCandidate,
                onCreate = actions.onCreateTasks,
                onDismiss = actions.onCloseSheet,
            )
        }
    }
}

@Composable
private fun NoteDetailDialogs(
    state: NoteDetailUiState,
    words: NoteDetailWording,
    actions: NoteDetailActions,
) {
    if (state.isConfirmingDelete) {
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

    if (state.isConfirmingDiscard) {
        MiraDialog(
            title = words.discardTitle,
            onDismissRequest = actions.onKeepEditing,
            confirmText = words.discardConfirm,
            onConfirm = actions.onConfirmDiscard,
            message = words.discardMessage,
            dismissText = words.keepEditing,
            onDismiss = actions.onKeepEditing,
            destructive = true,
        )
    }
}

@Preview
@Composable
private fun NoteDetailPreview() {
    MiraPreviewSurface {
        MiraListRow(title = EnglishNoteDetailWording.clientLabel, supporting = "Rayluxeo")
        MiraDivider()
        MiraButton(
            text = EnglishNoteDetailWording.save,
            onClick = {},
            style = MiraButtonStyle.PRIMARY,
        )
    }
}
