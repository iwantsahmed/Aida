package com.rayluxeo.aida.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteQuery
import com.rayluxeo.aida.domain.model.NoteSort
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.usecase.ArchiveNoteUseCase
import com.rayluxeo.aida.domain.usecase.DeleteNoteUseCase
import com.rayluxeo.aida.domain.usecase.PinNoteUseCase
import com.rayluxeo.aida.domain.usecase.RestoreNoteUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The notes list.
 *
 * Every list on this screen is one [NoteQuery]: the five views, the search field, the tag chips
 * and the sort all set fields on the same object, so a view and the rows in it cannot disagree
 * about what "archived" means. Nothing is filtered or re-ordered here — storage answers the
 * question it was asked, and this class only decides how the answer is cut into sections.
 *
 * Every change to a note goes through a use case, including the ones that look like one line:
 * archiving and deleting are two different columns on the row, and the one verb that puts either
 * of them back is [RestoreNoteUseCase], which reads the note to find out which it was.
 */
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val notes: NoteRepository,
    private val pinNote: PinNoteUseCase,
    private val archiveNote: ArchiveNoteUseCase,
    private val restoreNote: RestoreNoteUseCase,
    private val deleteNote: DeleteNoteUseCase,
    private val settings: SettingsRepository,
    private val times: TaskTimeFormatter,
    private val renderer: ResponseRenderer,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val listing: Flow<List<Note>> = local
        .map { it.toQuery() }
        .distinctUntilChanged()
        .flatMapLatest { query -> notes.observe(query) }

    val uiState: StateFlow<NotesUiState> = combine(
        local,
        listing,
        notes.observeUsedTags(),
        settings.settings,
    ) { state, rows, usedTags, preferences ->
        buildState(state, rows, usedTags, preferences.languageMode.displayLanguage())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = NotesUiState(),
    )

    /** Changing view closes the open sheet: it names a note the new list may not contain. */
    fun onSelectView(view: NotesView) = local.update { it.copy(view = view, actions = null) }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onToggleTag(tagId: Long) = local.update {
        it.copy(tagIds = if (tagId in it.tagIds) it.tagIds - tagId else it.tagIds + tagId)
    }

    fun onClearTags() = local.update { it.copy(tagIds = emptySet()) }

    /** Choosing the sort already applied reverses it, which is how every list Abu uses behaves. */
    fun onSelectSort(sort: NoteSort) = local.update {
        if (it.sort == sort) {
            it.copy(ascending = !it.ascending)
        } else {
            // Words read newest-first and titles read A to Z; each sort starts the way it is read.
            it.copy(sort = sort, ascending = sort == NoteSort.TITLE)
        }
    }

    fun onDismissNotice() = local.update { it.copy(notice = null, undoId = null) }

    fun onOpenActions(id: Long) {
        viewModelScope.launch {
            val words = notesWording(language())
            when (val found = notes.byId(id)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        actions = NoteActionsUiState(
                            noteId = found.value.id,
                            title = found.value.displayTitle(words),
                            isPinned = found.value.isPinned,
                            isArchived = found.value.archivedAt != null,
                            isDeleted = found.value.deletedAt != null,
                        ),
                    )
                }
                is AppResult.Failure -> showProblem(found.error.code, language())
            }
        }
    }

    fun onCloseActions() = local.update { it.copy(actions = null) }

    /** Pinning needs no line of its own: the row visibly moves to the top of the list. */
    fun onTogglePinned(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            local.update { it.copy(actions = null) }
            when (val saved = pinNote(id, pinned)) {
                is AppResult.Success -> local.update { it.copy(notice = null, undoId = null) }
                is AppResult.Failure -> showProblem(saved.error.code, language())
            }
        }
    }

    fun onArchive(id: Long) {
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(actions = null) }
            when (val saved = archiveNote(id)) {
                is AppResult.Success -> confirm(
                    message = notesWording(language).noteArchived,
                    undoId = id,
                    language = language,
                )
                is AppResult.Failure -> showProblem(saved.error.code, language)
            }
        }
    }

    /**
     * Takes a note out of the archive or out of the bin.
     *
     * One handler for both because [RestoreNoteUseCase] is one verb: it reads the note and puts
     * back whichever way it was put away. The line Abu reads is chosen from what the note was,
     * which is why the state is read before the call rather than after it.
     */
    fun onRestore(id: Long) {
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(actions = null) }
            val wasDeleted = when (val found = notes.byId(id)) {
                is AppResult.Failure -> return@launch showProblem(found.error.code, language)
                is AppResult.Success -> found.value.deletedAt != null
            }
            when (val saved = restoreNote(id)) {
                is AppResult.Success -> {
                    val words = notesWording(language)
                    confirm(
                        message = if (wasDeleted) words.noteRestored else words.noteUnarchived,
                        undoId = null,
                        language = language,
                    )
                }
                is AppResult.Failure -> showProblem(saved.error.code, language)
            }
        }
    }

    /** Soft, so the undo puts back the same row rather than a copy wearing the old title. */
    fun onDelete(id: Long) {
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(actions = null) }
            when (val removed = deleteNote(id)) {
                is AppResult.Success -> confirm(
                    message = notesWording(language).noteDeleted,
                    undoId = id,
                    language = language,
                )
                is AppResult.Failure -> showProblem(removed.error.code, language)
            }
        }
    }

    /** Puts back exactly what the last action changed, and nothing it did not. */
    fun onUndo() {
        val id = local.value.undoId ?: return
        viewModelScope.launch {
            val language = language()
            when (val saved = restoreNote(id)) {
                is AppResult.Success -> local.update {
                    it.copy(
                        notice = NotesNotice(
                            message = notesWording(language).undone,
                            tone = NoteNoticeTone.CONFIRM,
                        ),
                        undoId = null,
                    )
                }
                is AppResult.Failure -> showProblem(saved.error.code, language)
            }
        }
    }

    private suspend fun language(): SpokenLanguage = settings.current().languageMode.displayLanguage()

    private fun confirm(message: String, undoId: Long?, language: SpokenLanguage) {
        val words = notesWording(language)
        local.update {
            it.copy(
                notice = NotesNotice(
                    message = message,
                    tone = NoteNoticeTone.CONFIRM,
                    undoLabel = words.undo.takeIf { _ -> undoId != null },
                ),
                undoId = undoId,
            )
        }
    }

    private fun showProblem(code: MiraErrorCode, language: SpokenLanguage) = local.update {
        it.copy(
            notice = NotesNotice(
                message = renderer.display(MiraResponse.Problem(code), language),
                tone = NoteNoticeTone.PROBLEM,
            ),
            undoId = null,
        )
    }

    private fun buildState(
        state: LocalState,
        rows: List<Note>,
        usedTags: List<Tag>,
        language: SpokenLanguage,
    ): NotesUiState {
        val words = notesWording(language)
        return NotesUiState(
            isLoading = false,
            language = language,
            view = state.view,
            query = state.query,
            sections = rows.toSections(words, language),
            tags = usedTags.map {
                NoteTagChip(id = it.id, label = it.name, isSelected = it.id in state.tagIds)
            },
            sort = NoteSortUiState(sort = state.sort, ascending = state.ascending),
            actions = state.actions,
            notice = state.notice,
        )
    }

    /**
     * Splits the answer into what is pinned and what is not, in the order storage gave it.
     *
     * Two headings only when both halves exist: a heading over every row on the screen says
     * nothing, and one over nothing is noise. Nothing is re-ordered here — pinned notes already
     * arrive first, and sorting them again in the view model would be a second opinion about an
     * order storage has already decided.
     */
    private fun List<Note>.toSections(words: NotesWording, language: SpokenLanguage): List<NoteSection> {
        val pinned = filter { it.isPinned }
        val rest = filter { !it.isPinned }
        if (pinned.isEmpty() || rest.isEmpty()) {
            return listOf(
                NoteSection(key = "notes", title = null, items = map { it.toItem(words, language) }),
            )
        }
        return listOf(
            NoteSection(
                key = "pinned",
                title = words.pinnedSection,
                items = pinned.map { it.toItem(words, language) },
            ),
            NoteSection(
                key = "rest",
                title = words.otherNotesSection,
                items = rest.map { it.toItem(words, language) },
            ),
        )
    }

    /**
     * A note's title falls back to its first written line, and only then to a word.
     *
     * A note captured by voice or written by hand often has no title of its own, and a list of
     * rows all reading "Untitled" would be useless — the first line is what Abu would have called
     * it anyway. The sheet a row opens reads the same way, so the two never name it differently.
     */
    private fun Note.displayTitle(words: NotesWording): String =
        title?.takeIf { it.isNotBlank() }
            ?: body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: words.untitled

    private fun Note.toItem(words: NotesWording, language: SpokenLanguage): NoteListItem {
        val details = mutableListOf(words.updatedAt(times.momentLabel(updatedAt, language)))
        if (tags.isNotEmpty()) details += words.tagSummary(tags.map { it.name })
        return NoteListItem(
            id = id,
            title = displayTitle(words),
            supporting = details.joinToString(DETAIL_SEPARATOR),
        )
    }

    /** What the screen owns rather than the database: which view, what was typed, what is pending. */
    private data class LocalState(
        val view: NotesView = NotesView.ALL,
        val query: String = "",
        val tagIds: Set<Long> = emptySet(),
        val sort: NoteSort = NoteSort.UPDATED,
        val ascending: Boolean = false,
        val actions: NoteActionsUiState? = null,
        /** The note the last action put away, which the undo puts back. */
        val undoId: Long? = null,
        val notice: NotesNotice? = null,
    ) {
        fun toQuery(): NoteQuery = NoteQuery(
            filter = view.filter,
            sort = sort,
            ascending = ascending,
            text = query.trim().takeIf { it.isNotEmpty() },
            tagIds = tagIds,
        )
    }

    private companion object {
        /** Language-neutral, so the supporting line reads the same in Bengali and in English. */
        const val DETAIL_SEPARATOR = " · "

        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
