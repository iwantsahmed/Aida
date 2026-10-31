package com.rayluxeo.aida.ui.notes.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.InkPage
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteAttachment
import com.rayluxeo.aida.domain.model.NoteOrganization
import com.rayluxeo.aida.domain.model.NoteTaskCandidate
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.VoiceNote
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InkPageRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TagRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.repository.VoiceNoteRepository
import com.rayluxeo.aida.domain.usecase.ArchiveNoteUseCase
import com.rayluxeo.aida.domain.usecase.CreateInvoiceDraftUseCase
import com.rayluxeo.aida.domain.usecase.CreateNoteUseCase
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import com.rayluxeo.aida.domain.usecase.DeleteNoteUseCase
import com.rayluxeo.aida.domain.usecase.LinkNoteUseCase
import com.rayluxeo.aida.domain.usecase.OrganizeNoteUseCase
import com.rayluxeo.aida.domain.usecase.PinNoteUseCase
import com.rayluxeo.aida.domain.usecase.RestoreNoteUseCase
import com.rayluxeo.aida.domain.usecase.SetNoteTagsUseCase
import com.rayluxeo.aida.domain.usecase.TurnNoteIntoTasksUseCase
import com.rayluxeo.aida.domain.usecase.UpdateNoteUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One note: its words, what it is tied to, what it carries, and the six things Mira can do with it.
 *
 * Three rules shape the class.
 *
 * **The words are a draft until Abu saves.** Typing a title or a line of body touches nothing,
 * which is what makes leaving without saving mean something and why the screen asks before
 * discarding. Everything else — pinning, filing, a label, a link, a page of handwriting — belongs
 * to the note rather than to the form, has its own port, and is written the moment he asks for it.
 * Nothing that writes ever reaches back into the form and replaces a word he has typed: [settle]
 * is the single place a written note is read back, and it leaves a changed field alone.
 *
 * **A note in the bin is read-only.** It can be read and restored and nothing else: an edit saved
 * onto a binned note would be work Abu cannot find again, because no list but the bin shows it.
 *
 * **An action that cannot run is never drawn as though it could.** Each of the six says whether it
 * is available and, when it is not, why — an invoice needs a client, a note with no lines has
 * nothing to turn into tasks, and neither of those is Abu's mistake. An action that could *never*
 * run is not offered at all: reading handwriting is something the keyboard does on the canvas while
 * Abu writes, so the handwriting section says so and the row carries no control that would fail
 * every single time.
 */
@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val notes: NoteRepository,
    private val inkPages: InkPageRepository,
    private val tags: TagRepository,
    private val clients: ClientRepository,
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
    voiceNotes: VoiceNoteRepository,
    private val createNote: CreateNoteUseCase,
    private val updateNote: UpdateNoteUseCase,
    private val pinNote: PinNoteUseCase,
    private val archiveNote: ArchiveNoteUseCase,
    private val restoreNote: RestoreNoteUseCase,
    private val deleteNote: DeleteNoteUseCase,
    private val setNoteTags: SetNoteTagsUseCase,
    private val linkNote: LinkNoteUseCase,
    private val organizeNote: OrganizeNoteUseCase,
    private val turnIntoTasks: TurnNoteIntoTasksUseCase,
    private val createReminder: CreateReminderUseCase,
    private val createInvoiceDraft: CreateInvoiceDraftUseCase,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
    private val times: TaskTimeFormatter,
    private val renderer: ResponseRenderer,
) : ViewModel() {

    private val openedWith: Long = savedState.get<Long>(ARG_NOTE_ID) ?: NEW_NOTE_ID

    private val local = MutableStateFlow(LocalState(isLoading = openedWith != NEW_NOTE_ID))

    /**
     * The id the note actually has, which is not the one the screen opened with once a blank note
     * has been saved. Everything keyed on it — attachments, ink, the recording — follows the note
     * rather than watching note zero for the rest of the session.
     */
    private val savedId: Flow<Long> = local.map { it.stored?.id ?: NEW_NOTE_ID }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val carried: Flow<Carried> = savedId.flatMapLatest { id ->
        if (id == NEW_NOTE_ID) {
            flowOf(Carried(emptyList(), emptyList(), null))
        } else {
            combine(
                notes.observeAttachments(id),
                inkPages.observeForNote(id),
                voiceNotes.observe().map { all ->
                    all.firstOrNull { it.noteId == id && it.deletedAt == null }
                },
            ) { attachments, pages, voice -> Carried(attachments, pages, voice) }
        }
    }

    private val catalogue: Flow<Catalogue> = combine(
        clients.observe(ClientFilter.ACTIVE),
        projects.observe(ProjectFilter.ALL),
        tasks.observe(TaskFilter.OPEN),
        tags.observeAll(),
    ) { allClients, allProjects, allTasks, allTags ->
        Catalogue(allClients, allProjects, allTasks, allTags)
    }

    val uiState: StateFlow<NoteDetailUiState> = combine(
        local,
        carried,
        catalogue,
        settings.settings,
    ) { state, held, available, preferences ->
        buildState(state, held, available, preferences.languageMode.displayLanguage())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = NoteDetailUiState(isNew = openedWith == NEW_NOTE_ID),
    )

    init {
        if (openedWith != NEW_NOTE_ID) {
            viewModelScope.launch { reload(openedWith) }
        }
    }

    // --- The words ---------------------------------------------------------------------------

    fun onTitleChange(value: String) = local.update { it.copy(title = value) }

    fun onBodyChange(value: String) = local.update { it.copy(body = value) }

    /**
     * Saves the words, and only the words.
     *
     * A blank note is refused by the use case rather than here, because a note holding a page of
     * handwriting and no words at all is a complete note and only the domain knows that.
     *
     * What is written is the snapshot taken here, so a keystroke made while the write is in flight
     * is not part of it — and [settle] leaves that keystroke on screen rather than replacing it
     * with what went to disk, which leaves the note dirty and the save button live for it.
     */
    fun onSave() {
        val state = local.value
        if (!state.isDirty || state.isSaving) return
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(isSaving = true) }
            val stored = state.stored
            val saved = if (stored == null) {
                createNote(title = state.title, body = state.body, language = language)
            } else {
                updateNote(stored.copy(title = state.title, body = state.body))
            }
            local.update { it.copy(isSaving = false) }
            when (saved) {
                is AppResult.Success -> {
                    settle(saved.value, sentTitle = state.title, sentBody = state.body)
                    confirm(noteDetailWording(language).noteSaved)
                }
                is AppResult.Failure -> showProblem(saved.error.code, language)
            }
        }
    }

    // --- Leaving -----------------------------------------------------------------------------

    fun onBackRequested() {
        val state = local.value
        if (state.isDirty) {
            local.update { it.copy(isConfirmingDiscard = true) }
        } else {
            local.update { it.copy(isFinished = true) }
        }
    }

    fun onKeepEditing() = local.update { it.copy(isConfirmingDiscard = false) }

    fun onConfirmDiscard() = local.update { it.copy(isConfirmingDiscard = false, isFinished = true) }

    // --- Sheets ------------------------------------------------------------------------------

    fun onOpenSheet(sheet: NoteDetailSheet) {
        when (sheet) {
            NoteDetailSheet.CLIENT -> openLink(NoteLinkKind.CLIENT)
            NoteDetailSheet.PROJECT -> openLink(NoteLinkKind.PROJECT)
            NoteDetailSheet.TASK -> openLink(NoteLinkKind.TASK)
            NoteDetailSheet.ORGANIZE -> onOrganize()
            NoteDetailSheet.TASKS -> onTurnIntoTasks()
            NoteDetailSheet.ACTIONS, NoteDetailSheet.TAGS -> local.update { it.copy(sheet = sheet) }
        }
    }

    fun onCloseSheet() = local.update {
        it.copy(sheet = null, linkKind = null, proposal = null, kept = null, candidates = null)
    }

    // --- Putting the note away, and taking it back ---------------------------------------------

    fun onTogglePinned() {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            local.update { it.copy(sheet = null) }
            when (val saved = pinNote(stored.id, !stored.isPinned)) {
                is AppResult.Success -> settle(saved.value)
                is AppResult.Failure -> showProblem(saved.error.code, language())
            }
        }
    }

    fun onArchive() {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(sheet = null) }
            when (val saved = archiveNote(stored.id)) {
                is AppResult.Success -> {
                    settle(saved.value)
                    confirm(noteDetailWording(language).noteArchived)
                }
                is AppResult.Failure -> showProblem(saved.error.code, language)
            }
        }
    }

    /**
     * Takes the note out of the archive, or out of the bin.
     *
     * One handler because [RestoreNoteUseCase] is one verb: it reads the note and undoes whichever
     * way it was put away. Which line Abu reads is decided from what it was before the call.
     */
    fun onRestore() {
        val stored = local.value.stored ?: return
        val wasDeleted = stored.deletedAt != null
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(sheet = null) }
            when (val saved = restoreNote(stored.id)) {
                is AppResult.Success -> {
                    settle(saved.value)
                    val words = noteDetailWording(language)
                    confirm(if (wasDeleted) words.noteRestored else words.noteUnarchived)
                }
                is AppResult.Failure -> showProblem(saved.error.code, language)
            }
        }
    }

    fun onDeleteRequested() = local.update { it.copy(sheet = null, isConfirmingDelete = true) }

    fun onCancelDelete() = local.update { it.copy(isConfirmingDelete = false) }

    /**
     * Bins the note and stays on it.
     *
     * Leaving would take the way back with it: the bin is soft, so the honest thing is to keep
     * the note on screen, read-only, with the one verb that undoes it beside the line saying
     * where it went.
     */
    fun onConfirmDelete() {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(isConfirmingDelete = false) }
            when (val removed = deleteNote(stored.id)) {
                is AppResult.Success -> {
                    reload(stored.id)
                    confirm(noteDetailWording(language).noteDeleted)
                }
                is AppResult.Failure -> showProblem(removed.error.code, language)
            }
        }
    }

    // --- Labels ------------------------------------------------------------------------------

    /** Mints the label if it is new, then wears it. `ensure` is the only thing that creates one. */
    fun onAddTag(name: String) {
        val stored = local.value.stored ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val language = language()
            when (val tag = tags.ensure(clean)) {
                is AppResult.Failure -> showProblem(tag.error.code, language)
                is AppResult.Success -> {
                    val wanted = stored.tags.map { it.id } + tag.value.id
                    applyTags(stored.id, wanted.distinct(), language)
                }
            }
        }
    }

    fun onRemoveTag(tagId: Long) {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            applyTags(stored.id, stored.tags.map { it.id } - tagId, language())
        }
    }

    // --- Links -------------------------------------------------------------------------------

    fun onPickLink(id: Long?) {
        val stored = local.value.stored ?: return
        val kind = local.value.linkKind ?: return
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(sheet = null, linkKind = null) }
            val saved = when (kind) {
                NoteLinkKind.CLIENT -> linkNote.toClient(stored.id, id)
                NoteLinkKind.PROJECT -> linkNote.toProject(stored.id, id)
                NoteLinkKind.TASK -> linkNote.toTask(stored.id, id)
            }
            when (saved) {
                is AppResult.Success -> settle(saved.value)
                is AppResult.Failure -> showProblem(saved.error.code, language)
            }
        }
    }

    // --- The six actions -------------------------------------------------------------------------

    /**
     * Reads the note and proposes; nothing is written until Abu agrees, part by part.
     *
     * A proposal with nothing in it is never put in front of him — it would be a sheet whose only
     * honest answer is "never mind" — so it is said in a line instead.
     *
     * What is read is the note as SAVED, because that is what the use case is given. Applying the
     * proposal therefore leaves a draft alone: a line typed and not yet saved is still on screen
     * afterwards, and still unsaved.
     */
    fun onOrganize() {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            val language = language()
            when (val proposed = organizeNote(stored.id)) {
                is AppResult.Failure -> showProblem(proposed.error.code, language)
                is AppResult.Success -> {
                    val organization = proposed.value
                    if (!organization.hasSuggestions) {
                        local.update {
                            it.copy(
                                sheet = null,
                                notice = NoteDetailNotice(
                                    message = noteDetailWording(language).organizeNothing,
                                    tone = MiraNoticeTone.INFO,
                                ),
                            )
                        }
                    } else {
                        local.update {
                            it.copy(
                                sheet = NoteDetailSheet.ORGANIZE,
                                proposal = organization,
                                kept = organization,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Turning a part off empties it; turning it back on puts back what Mira proposed.
     *
     * What is kept is held as a [NoteOrganization] rather than as four flags, because that is
     * exactly what is handed back to [OrganizeNoteUseCase.applyProposal] — a second shape would be
     * a second chance to apply something Abu turned off.
     */
    fun onToggleOrganizeTitle() = updateOrganize { kept, proposal ->
        kept.copy(title = if (kept.title == null) proposal.title else null)
    }

    fun onToggleOrganizeTag(tagId: Long) = updateOrganize { kept, _ ->
        kept.copy(
            tagIds = if (tagId in kept.tagIds) kept.tagIds - tagId else kept.tagIds + tagId,
        )
    }

    fun onToggleOrganizeClient() = updateOrganize { kept, proposal ->
        kept.copy(clientId = if (kept.clientId == null) proposal.clientId else null)
    }

    fun onToggleOrganizeProject() = updateOrganize { kept, proposal ->
        kept.copy(projectId = if (kept.projectId == null) proposal.projectId else null)
    }

    fun onApplyOrganize() {
        val stored = local.value.stored ?: return
        val state = local.value
        val proposal = state.proposal ?: return
        val kept = state.keptOrganization(proposal) ?: return
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(isApplyingOrganize = true) }
            val applied = organizeNote.applyProposal(stored.id, kept)
            local.update { it.copy(isApplyingOrganize = false) }
            when (applied) {
                is AppResult.Success -> {
                    closeOrganize()
                    settle(applied.value)
                    confirm(noteDetailWording(language).organizeApplied)
                }
                is AppResult.Failure -> {
                    closeOrganize()
                    showProblem(applied.error.code, language)
                }
            }
        }
    }

    /**
     * Shows every line of the note with its tick, before anything is created.
     *
     * The lines are the saved ones, like everything else that reads the note rather than the form.
     */
    fun onTurnIntoTasks() {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            val language = language()
            when (val found = turnIntoTasks.candidates(stored.id)) {
                is AppResult.Failure -> showProblem(found.error.code, language)
                is AppResult.Success -> {
                    if (found.value.isEmpty()) {
                        local.update {
                            it.copy(
                                sheet = null,
                                notice = NoteDetailNotice(
                                    message = noteDetailWording(language).tasksEmpty,
                                    tone = MiraNoticeTone.INFO,
                                ),
                            )
                        }
                    } else {
                        local.update {
                            it.copy(sheet = NoteDetailSheet.TASKS, candidates = found.value)
                        }
                    }
                }
            }
        }
    }

    fun onToggleCandidate(index: Int) = local.update { state ->
        val candidates = state.candidates ?: return@update state
        if (index !in candidates.indices) return@update state
        state.copy(
            candidates = candidates.mapIndexed { at, candidate ->
                if (at == index) candidate.copy(selected = !candidate.selected) else candidate
            },
        )
    }

    fun onCreateTasks() {
        val stored = local.value.stored ?: return
        val chosen = local.value.candidates?.filter { it.selected }.orEmpty()
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            val language = language()
            local.update { it.copy(isCreatingTasks = true) }
            val created = turnIntoTasks.create(stored.id, chosen)
            local.update { it.copy(isCreatingTasks = false) }
            when (created) {
                is AppResult.Success -> {
                    local.update { it.copy(sheet = null, candidates = null) }
                    reload(stored.id)
                    confirm(noteDetailWording(language).tasksCreated(created.value.size))
                }
                is AppResult.Failure -> {
                    local.update { it.copy(sheet = null, candidates = null) }
                    showProblem(created.error.code, language)
                }
            }
        }
    }

    /**
     * Sets a reminder about this note at the moment Abu picked.
     *
     * A time already gone is refused by [CreateReminderUseCase] and comes back as a worded
     * problem rather than a reminder that would fire at the next reboot.
     *
     * It carries **no link**, deliberately. `Reminder` has no note link, and `linkedTaskId` does
     * not mean "near this task" — it means "this is that task's own reminder", which is what the
     * task editor reads back and cancels on the next save of the task. Filing a note's reminder
     * under the note's task would have it quietly cancelled the next time Abu changed that task's
     * due date, and nothing would tell him it had gone.
     */
    fun onCreateReminder(date: LocalDate, time: LocalTime) {
        val stored = local.value.stored ?: return
        viewModelScope.launch {
            val language = language()
            val words = noteDetailWording(language)
            val at = times.toInstant(date, time)
            val created = createReminder(
                title = stored.reminderTitle(words),
                triggerAt = at,
            )
            when (created) {
                is AppResult.Success ->
                    confirm(words.reminderCreated(times.momentLabel(created.value.triggerAt, language)))
                is AppResult.Failure -> showProblem(created.error.code, language)
            }
        }
    }

    /**
     * Raises a draft invoice for the note's client, with the note's own words on its one line.
     *
     * No amount is passed and none is invented: the draft sits at zero until Abu prices it, which
     * is the part he does not need Mira for.
     */
    fun onCreateInvoiceDraft() {
        val stored = local.value.stored ?: return
        val clientId = stored.clientId ?: return
        viewModelScope.launch {
            val language = language()
            val words = noteDetailWording(language)
            val client = when (val found = clients.byId(clientId)) {
                is AppResult.Failure -> return@launch showProblem(found.error.code, language)
                is AppResult.Success -> found.value
            }
            val draft = createInvoiceDraft(
                client = client,
                amount = null,
                description = stored.title ?: stored.firstLine(),
            )
            when (draft) {
                is AppResult.Success ->
                    confirm(words.invoiceDraftCreated(draft.value.number, client.name))
                is AppResult.Failure -> showProblem(draft.error.code, language)
            }
        }
    }

    fun onDismissNotice() = local.update { it.copy(notice = null) }

    // --- Putting the state together ------------------------------------------------------------

    private fun openLink(kind: NoteLinkKind) = local.update {
        it.copy(sheet = kindToSheet(kind), linkKind = kind)
    }

    private fun kindToSheet(kind: NoteLinkKind): NoteDetailSheet = when (kind) {
        NoteLinkKind.CLIENT -> NoteDetailSheet.CLIENT
        NoteLinkKind.PROJECT -> NoteDetailSheet.PROJECT
        NoteLinkKind.TASK -> NoteDetailSheet.TASK
    }

    private fun updateOrganize(
        transform: (kept: NoteOrganization, proposal: NoteOrganization) -> NoteOrganization,
    ) = local.update { state ->
        val proposal = state.proposal ?: return@update state
        state.copy(kept = transform(state.kept ?: proposal, proposal))
    }

    private fun closeOrganize() =
        local.update { it.copy(sheet = null, proposal = null, kept = null) }

    private suspend fun applyTags(noteId: Long, tagIds: List<Long>, language: SpokenLanguage) {
        when (val saved = setNoteTags(noteId, tagIds)) {
            is AppResult.Success -> settle(saved.value)
            is AppResult.Failure -> showProblem(saved.error.code, language)
        }
    }

    /** Reads the note back after a write, through the one rule [settle] holds about the words. */
    private suspend fun reload(id: Long) {
        when (val found = notes.byId(id)) {
            is AppResult.Success -> settle(found.value)
            is AppResult.Failure -> {
                // Worded here rather than in the state, so a note that will not load says so in
                // Abu's own language from the first frame.
                val message = renderer.display(MiraResponse.Problem(found.error.code), language())
                local.update { it.copy(isLoading = false, errorMessage = message) }
            }
        }
    }

    /**
     * Puts a written note back into the form without stepping on words Abu has since typed.
     *
     * A field is taken from storage only while what is on screen still matches what was stored.
     * That one rule covers every write: a label added over a half-typed line leaves the line
     * alone, Organize may rewrite the title without taking the body with it, and a keystroke made
     * while a save was in flight survives the save coming back. A flag at each call site cannot do
     * that — it can only be right about the field the caller was thinking of, and Organize is the
     * caller that was wrong.
     */
    private fun settle(
        note: Note,
        sentTitle: String? = null,
        sentBody: String? = null,
    ) = local.update { state ->
        val stored = state.stored
        // A field is adopted when what is on screen still matches what this write SENT — or, for
        // a write that sent nothing of its own, what was last stored.
        //
        // Comparing against what was sent is what makes a trim settle. The domain trims a title
        // on the way in, so after saving " ink " the stored title is "ink" while the field still
        // holds the spaces; measured against the stored text those two can never agree again, and
        // the note stayed dirty for ever — Save never went quiet and every exit asked Abu to
        // discard work he had already saved.
        val titleBase = sentTitle ?: stored?.title.orEmpty()
        val bodyBase = sentBody ?: stored?.body.orEmpty()
        state.copy(
            isLoading = false,
            stored = note,
            title = if (state.title == titleBase) note.title.orEmpty() else state.title,
            body = if (state.body == bodyBase) note.body else state.body,
            errorMessage = null,
        )
    }

    private suspend fun language(): SpokenLanguage =
        settings.current().languageMode.displayLanguage()

    private fun confirm(message: String) = local.update {
        it.copy(notice = NoteDetailNotice(message = message, tone = MiraNoticeTone.CONFIRM))
    }

    private fun showProblem(code: MiraErrorCode, language: SpokenLanguage) = local.update {
        it.copy(
            notice = NoteDetailNotice(
                message = renderer.display(MiraResponse.Problem(code), language),
                tone = MiraNoticeTone.DANGER,
            ),
        )
    }

    private fun buildState(
        state: LocalState,
        held: Carried,
        available: Catalogue,
        language: SpokenLanguage,
    ): NoteDetailUiState {
        val words = noteDetailWording(language)
        val stored = state.stored
        val isDeleted = stored?.deletedAt != null
        val remindAt = clock.localNow().plusHours(REMINDER_LEAD_HOURS)
        return NoteDetailUiState(
            isLoading = state.isLoading,
            isNew = openedWith == NEW_NOTE_ID && stored == null,
            language = language,
            title = state.title,
            body = state.body,
            isPinned = stored?.isPinned == true,
            isArchived = stored?.archivedAt != null,
            isDeleted = isDeleted,
            isSaving = state.isSaving,
            isDirty = state.isDirty,
            tags = stored?.tags.orEmpty().map { NoteTagUi(id = it.id, name = it.name) },
            tagSuggestions = available.tags
                .filterNot { tag -> stored?.tags.orEmpty().any { it.id == tag.id } }
                .map { NoteTagUi(id = it.id, name = it.name) },
            clientLabel = stored?.clientId?.let { id -> available.clientName(id) },
            projectLabel = stored?.projectId?.let { id -> available.projectName(id) },
            taskLabel = stored?.taskId?.let { id -> available.taskName(id) },
            attachments = held.attachments.map { it.toUi(words) },
            inkPages = held.inkPages.map { it.toUi(words, language) },
            voiceNote = held.voiceNote?.toUi(words, language),
            createdLabel = stored?.let { words.created(times.momentLabel(it.createdAt, language)) },
            updatedLabel = stored?.let { words.updated(times.momentLabel(it.updatedAt, language)) },
            actions = actionsFor(stored, isDeleted, words),
            sheet = state.sheet,
            linkPicker = state.linkKind?.let { kind -> available.pickerFor(kind, stored, words) },
            organize = state.proposal?.let { proposal ->
                (state.kept ?: proposal).toUi(proposal, state.isApplyingOrganize, available)
            },
            tasks = state.candidates?.let { candidates ->
                NoteTasksUiState(
                    candidates = candidates.map {
                        NoteTaskCandidateUi(line = it.line, isSelected = it.selected)
                    },
                    isCreating = state.isCreatingTasks,
                )
            },
            reminderDate = remindAt.toLocalDate(),
            reminderTime = remindAt.toLocalTime(),
            notice = state.notice,
            errorMessage = state.errorMessage,
            isConfirmingDiscard = state.isConfirmingDiscard,
            isConfirmingDelete = state.isConfirmingDelete,
            isFinished = state.isFinished,
        )
    }

    /**
     * Which of the six can run, and what to say about the ones that cannot.
     *
     * Every reason here is one that can change: save the note, take it out of the bin, write a
     * line, link a client. Reading a saved page of handwriting is not on that list, because it is
     * not something that becomes possible later — the keyboard reads handwriting on the canvas as
     * Abu writes and no call anywhere in Android takes a finished page and returns its text. That
     * belongs in the handwriting section as a sentence, not here as a control that would be dimmed
     * for the life of the app.
     */
    private fun actionsFor(
        stored: Note?,
        isDeleted: Boolean,
        words: NoteDetailWording,
    ): List<NoteActionUi> = NoteAction.entries.map { action ->
        val reason = when {
            action == NoteAction.ASK_MIRA -> null
            stored == null -> words.saveFirst
            isDeleted -> words.restoreFirst
            action == NoteAction.TURN_INTO_TASKS && stored.body.isBlank() -> words.needsWords
            action == NoteAction.CREATE_INVOICE_DRAFT && stored.clientId == null ->
                words.linkClientFirst
            else -> null
        }
        NoteActionUi(
            action = action,
            label = words.actionLabel(action),
            isEnabled = reason == null,
            reason = reason,
        )
    }

    private fun NoteAttachment.toUi(words: NoteDetailWording): NoteAttachmentUi = NoteAttachmentUi(
        name = displayName,
        detail = words.fileSize(sizeBytes),
    )

    private fun InkPage.toUi(words: NoteDetailWording, language: SpokenLanguage): InkPageUi =
        InkPageUi(
            id = id,
            label = words.inkPageLabel(strokeCount, times.momentLabel(updatedAt, language)),
            isLost = isLost,
        )

    /**
     * The length is handed over as two numbers rather than as a formatted string, because the
     * digits themselves are wording: Bengali writes them in Bengali numerals, and a "2:45" built
     * here would sit in Latin digits beside a moment that had already been written as "৯টা".
     */
    private fun VoiceNote.toUi(words: NoteDetailWording, language: SpokenLanguage): NoteVoiceUi {
        val total = duration.seconds.coerceAtLeast(0L)
        return NoteVoiceUi(
            label = words.voiceNoteLabel(
                minutes = total / SECONDS_PER_MINUTE,
                seconds = total % SECONDS_PER_MINUTE,
                moment = times.momentLabel(recordedAt, language),
            ),
        )
    }

    private fun Note.firstLine(): String? = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()

    /**
     * What a reminder about this note is called.
     *
     * A blank title would be refused, so the note's own opening line stands in, and the word
     * "Note" only when it has neither — worded per language, because it is a sentence Abu will
     * read on a notification months from now.
     */
    private fun Note.reminderTitle(words: NoteDetailWording): String =
        title?.takeIf { it.isNotBlank() } ?: firstLine() ?: words.noteTitle

    private fun NoteOrganization.toUi(
        proposal: NoteOrganization,
        isApplying: Boolean,
        available: Catalogue,
    ): NoteOrganizeUiState = NoteOrganizeUiState(
        title = proposal.title,
        acceptTitle = title != null,
        tags = proposal.tagIds.mapNotNull { id ->
            available.tags.firstOrNull { it.id == id }?.let { tag ->
                NoteOrganizeTagUi(id = tag.id, name = tag.name, isAccepted = tag.id in tagIds)
            }
        },
        client = proposal.clientId?.let { id ->
            available.clientName(id)?.let { NoteLinkOption(id = id, label = it) }
        },
        acceptClient = clientId != null,
        project = proposal.projectId?.let { id ->
            available.projectName(id)?.let { NoteLinkOption(id = id, label = it) }
        },
        acceptProject = projectId != null,
        isApplying = isApplying,
    )

    /** Everything the screen owns rather than the database: the draft, the sheets, the proposal. */
    private data class LocalState(
        val isLoading: Boolean = false,
        val stored: Note? = null,
        val title: String = "",
        val body: String = "",
        val isSaving: Boolean = false,
        val sheet: NoteDetailSheet? = null,
        val linkKind: NoteLinkKind? = null,
        /** What Mira proposed, kept whole so a part turned off can be turned back on. */
        val proposal: NoteOrganization? = null,
        /** The parts Abu has kept: the whole proposal until he turns one of them off. */
        val kept: NoteOrganization? = null,
        val isApplyingOrganize: Boolean = false,
        val candidates: List<NoteTaskCandidate>? = null,
        val isCreatingTasks: Boolean = false,
        val notice: NoteDetailNotice? = null,
        val errorMessage: String? = null,
        val isConfirmingDiscard: Boolean = false,
        val isConfirmingDelete: Boolean = false,
        val isFinished: Boolean = false,
    ) {
        /**
         * A binned note is never dirty: its editor is read-only, so there is nothing to save and
         * nothing to ask about on the way out.
         */
        val isDirty: Boolean
            get() = when {
                stored?.deletedAt != null -> false
                stored == null -> title.isNotBlank() || body.isNotBlank()
                else -> title != stored.title.orEmpty() || body != stored.body
            }

        fun keptOrganization(proposal: NoteOrganization): NoteOrganization? =
            (kept ?: proposal).takeIf { it.hasSuggestions }
    }

    private data class Carried(
        val attachments: List<NoteAttachment>,
        val inkPages: List<InkPage>,
        val voiceNote: VoiceNote?,
    )

    /** The names the links need, which the note carries only as ids. */
    private data class Catalogue(
        val clients: List<Client>,
        val projects: List<Project>,
        val tasks: List<Task>,
        val tags: List<Tag>,
    ) {
        fun clientName(id: Long): String? = clients.firstOrNull { it.id == id }?.name

        fun projectName(id: Long): String? = projects.firstOrNull { it.id == id }?.name

        fun taskName(id: Long): String? = tasks.firstOrNull { it.id == id }?.title

        fun pickerFor(
            kind: NoteLinkKind,
            stored: Note?,
            words: NoteDetailWording,
        ): NoteLinkPickerUiState = when (kind) {
            NoteLinkKind.CLIENT -> NoteLinkPickerUiState(
                kind = kind,
                options = clients.map { NoteLinkOption(it.id, it.name) },
                selectedId = stored?.clientId,
            )
            NoteLinkKind.PROJECT -> NoteLinkPickerUiState(
                kind = kind,
                options = projects.map { NoteLinkOption(it.id, it.name) },
                selectedId = stored?.projectId,
            )
            NoteLinkKind.TASK -> NoteLinkPickerUiState(
                kind = kind,
                // A task the note is already tied to may have been completed since, so it is
                // added back rather than disappearing out of its own picker.
                options = (tasks.map { NoteLinkOption(it.id, it.title) } + linkedTask(stored, words))
                    .distinctBy { it.id },
                selectedId = stored?.taskId,
            )
        }

        private fun linkedTask(stored: Note?, words: NoteDetailWording): List<NoteLinkOption> {
            val id = stored?.taskId ?: return emptyList()
            return listOf(NoteLinkOption(id = id, label = taskName(id) ?: words.taskLabel))
        }
    }

    internal companion object {
        /** The nav argument. Zero is the note that does not exist yet. */
        const val ARG_NOTE_ID = "noteId"

        const val NEW_NOTE_ID = 0L

        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        const val SECONDS_PER_MINUTE = 60L

        /**
         * Where the reminder pickers open. An hour from now is the nearest moment that is
         * certainly still ahead, which is the one thing `CreateReminderUseCase` insists on.
         */
        const val REMINDER_LEAD_HOURS = 1L
    }
}
