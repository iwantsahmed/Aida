package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSearchField
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import com.rayluxeo.aida.domain.model.NoteSort
import kotlinx.coroutines.delay

/**
 * Everything Abu has written down.
 *
 * [onNewNote] is separate from [onOpenNote] rather than opening note zero, so the caller decides
 * how a blank note is reached and this screen never has to know that zero means "not saved yet".
 */
@Composable
fun NotesScreen(
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenVoiceNotes: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: NotesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = remember(viewModel) { NotesCallbacks(viewModel) }

    NotesContent(
        state = state,
        callbacks = callbacks,
        onOpenNote = onOpenNote,
        onNewNote = onNewNote,
        onOpenVoiceNotes = onOpenVoiceNotes,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Everything the screen can ask the view model to do, as one value.
 *
 * A dozen lambdas threaded one at a time through three levels of composable is where a handler
 * last went missing, and the compiler had nothing to say about it.
 */
@Immutable
internal class NotesCallbacks(
    val onSelectView: (NotesView) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onToggleTag: (Long) -> Unit,
    val onClearTags: () -> Unit,
    val onSelectSort: (NoteSort) -> Unit,
    val onOpenActions: (Long) -> Unit,
    val onCloseActions: () -> Unit,
    val onTogglePinned: (Long, Boolean) -> Unit,
    val onArchive: (Long) -> Unit,
    val onRestore: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val onUndo: () -> Unit,
    val onDismissNotice: () -> Unit,
) {
    constructor(viewModel: NotesViewModel) : this(
        onSelectView = viewModel::onSelectView,
        onQueryChange = viewModel::onQueryChange,
        onToggleTag = viewModel::onToggleTag,
        onClearTags = viewModel::onClearTags,
        onSelectSort = viewModel::onSelectSort,
        onOpenActions = viewModel::onOpenActions,
        onCloseActions = viewModel::onCloseActions,
        onTogglePinned = viewModel::onTogglePinned,
        onArchive = viewModel::onArchive,
        onRestore = viewModel::onRestore,
        onDelete = viewModel::onDelete,
        onUndo = viewModel::onUndo,
        onDismissNotice = viewModel::onDismissNotice,
    )
}

@Composable
private fun NotesContent(
    state: NotesUiState,
    callbacks: NotesCallbacks,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenVoiceNotes: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val words = notesWording(state.language)

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        when (notice.tone) {
            NoteNoticeTone.CONFIRM -> haptics.confirm()
            NoteNoticeTone.PROBLEM -> haptics.error()
        }
        delay(NOTE_NOTICE_MILLIS)
        callbacks.onDismissNotice()
    }

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = words.screenTitle,
                onBack = onBack,
                backContentDescription = words.back,
                // The one thing Abu comes here to do besides read, so it carries the screen's
                // single champagne. Everything else on the list is ivory or muted, which is what
                // lets one accent still mean something — and when the empty list is already
                // drawing a champagne button for the very same action, this one goes quiet rather
                // than making two of it. See `NotesUiState.isOfferingWayForward`.
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiraIconButton(
                            icon = MiraIcons.KeyboardVoice,
                            contentDescription = words.voiceNotes,
                            onClick = onOpenVoiceNotes,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MiraIconButton(
                            icon = MiraIcons.Add,
                            contentDescription = words.newNote,
                            onClick = onNewNote,
                            tint = if (state.isOfferingWayForward) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            if (state.isLoading) {
                MiraListSkeleton()
            } else {
                // Two intervals, not three sizes of gap: the search field and both rows of chips
                // are one group — everything that narrows the list — so every gap inside the
                // group is the same step, and the list itself sits a whole step further away.
                // Related closer than unrelated, at one interval each, is the whole of the rhythm
                // on this screen.
                Column(modifier = Modifier.fillMaxSize()) {
                    MiraSearchField(
                        value = state.query,
                        onValueChange = callbacks.onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = words.searchPlaceholder,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    NoteFilterBar(
                        view = state.view,
                        tags = state.tags,
                        sort = state.sort,
                        words = words,
                        onSelectView = callbacks.onSelectView,
                        onToggleTag = callbacks.onToggleTag,
                        onClearTags = callbacks.onClearTags,
                        onSelectSort = callbacks.onSelectSort,
                    )
                    Spacer(Modifier.height(spacing.lg))
                    NotesList(
                        state = state,
                        words = words,
                        callbacks = callbacks,
                        onOpenNote = onOpenNote,
                        onNewNote = onNewNote,
                    )
                }
            }

            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = notice.message,
                    tone = when (notice.tone) {
                        NoteNoticeTone.CONFIRM -> MiraNoticeTone.CONFIRM
                        NoteNoticeTone.PROBLEM -> MiraNoticeTone.DANGER
                    },
                    dismissContentDescription = words.dismiss,
                    onDismiss = callbacks.onDismissNotice,
                    actionText = notice.undoLabel,
                    onAction = callbacks.onUndo.takeIf { notice.undoLabel != null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = spacing.md),
                )
            }
        }
    }

    state.actions?.let { actions ->
        NoteActionsSheet(
            state = actions,
            words = words,
            onTogglePinned = { callbacks.onTogglePinned(actions.noteId, !actions.isPinned) },
            onArchive = { callbacks.onArchive(actions.noteId) },
            onRestore = { callbacks.onRestore(actions.noteId) },
            onDelete = { callbacks.onDelete(actions.noteId) },
            onDismiss = callbacks.onCloseActions,
        )
    }
}

/**
 * An empty list is a normal state, not a fault.
 *
 * What it says is why it is empty, and what it offers is the one control that undoes that: a
 * search that matched nothing offers to clear the search, and a label filter that matched nothing
 * says so and offers to clear the labels. Neither is answered with "write a note" — that is not an
 * answer to "nothing matches" — and the bin and the archive offer nothing at all, because there is
 * no way forward from them and inventing one would be a button that does not belong.
 *
 * With both a search and a label set, the search is cleared first and the list then says what the
 * labels are still doing. One tap, one thing undone, and the words never promise more than the tap
 * delivers.
 *
 * It is centred in the whole of the space the list would have filled rather than left sitting
 * under the filters. Most of Mira's screens are empty most of the time — that is the truth of a
 * private assistant — so an empty one has to look like a decision, and a line stranded in the top
 * third of a black screen looks like a screen that gave up halfway through drawing itself.
 */
@Composable
private fun ColumnScope.NotesList(
    state: NotesUiState,
    words: NotesWording,
    callbacks: NotesCallbacks,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    if (state.isEmpty) {
        // These four cases are the ones `NotesUiState.isOfferingWayForward` counts, and the top
        // bar hands its champagne over on the strength of that count. A case added here without
        // being added there puts two accented primaries back on the screen.
        val searching = state.query.isNotBlank()
        val canWrite = state.view == NotesView.ALL || state.view == NotesView.PINNED
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            MiraEmptyState(
                icon = MiraIcons.Description,
                message = when {
                    searching -> words.noMatches(state.query.trim())
                    state.hasTagFilter -> words.noTagMatches
                    else -> words.emptyMessage(state.view)
                },
                actionText = when {
                    searching -> words.clearSearch
                    state.hasTagFilter -> words.clearTags
                    canWrite -> words.writeFirstNote
                    else -> null
                },
                onAction = when {
                    searching -> {
                        { callbacks.onQueryChange("") }
                    }
                    state.hasTagFilter -> callbacks.onClearTags
                    canWrite -> onNewNote
                    else -> null
                },
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.weight(1f)) {
        state.sections.forEachIndexed { index, section ->
            section.title?.let { title ->
                item(key = "heading-${section.key}") {
                    Column {
                        // The first heading already has the air the filter bar leaves above it.
                        // The ones after it have to make their own, or a hairline is all that
                        // separates two groups and the second heading reads as a stray label in
                        // the middle of one list.
                        if (index > 0) Spacer(Modifier.height(spacing.lg))
                        MiraSectionHeader(text = title)
                    }
                }
            }
            itemsIndexed(
                items = section.items,
                key = { _, note -> "${section.key}-${note.id}" },
            ) { row, note ->
                NoteRow(
                    note = note,
                    words = words,
                    onOpen = { onOpenNote(note.id) },
                    onOpenActions = { callbacks.onOpenActions(note.id) },
                )
                // A hairline separates two rows; it does not close a group. Under the last row of
                // a section it would be a line drawn to nothing, and the gap before the next
                // heading is already doing that job.
                if (row < section.items.lastIndex) MiraDivider()
            }
        }
        item(key = "tail") { Spacer(Modifier.height(spacing.xxl)) }
    }
}
