package com.rayluxeo.aida.ui.notes.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.InkPage
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.VoiceNote
import com.rayluxeo.aida.domain.usecase.ArchiveNoteUseCase
import com.rayluxeo.aida.domain.usecase.BuildInvoiceTotalsUseCase
import com.rayluxeo.aida.domain.usecase.CreateInvoiceDraftUseCase
import com.rayluxeo.aida.domain.usecase.CreateNoteUseCase
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import com.rayluxeo.aida.domain.usecase.CreateTaskUseCase
import com.rayluxeo.aida.domain.usecase.DeleteNoteUseCase
import com.rayluxeo.aida.domain.usecase.LinkNoteUseCase
import com.rayluxeo.aida.domain.usecase.OrganizeNoteUseCase
import com.rayluxeo.aida.domain.usecase.PinNoteUseCase
import com.rayluxeo.aida.domain.usecase.RestoreNoteUseCase
import com.rayluxeo.aida.domain.usecase.SetNoteTagsUseCase
import com.rayluxeo.aida.domain.usecase.TurnNoteIntoTasksUseCase
import com.rayluxeo.aida.domain.usecase.UpdateNoteUseCase
import com.rayluxeo.aida.ui.notes.FakeInkPageRepository
import com.rayluxeo.aida.ui.notes.FakeInvoiceRepository
import com.rayluxeo.aida.ui.notes.FakeNoteRepository
import com.rayluxeo.aida.ui.notes.FakeNoteTagRepository
import com.rayluxeo.aida.ui.notes.FakeVoiceNoteRepository
import com.rayluxeo.aida.ui.notes.testNote
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import com.rayluxeo.aida.ui.tasks.FakeClientRepository
import com.rayluxeo.aida.ui.tasks.FakeProjectRepository
import com.rayluxeo.aida.ui.tasks.FakeReminderRepository
import com.rayluxeo.aida.ui.tasks.FakeReminderScheduler
import com.rayluxeo.aida.ui.tasks.FakeSettingsRepository
import com.rayluxeo.aida.ui.tasks.FakeSubtaskRepository
import com.rayluxeo.aida.ui.tasks.FakeTaskRepository
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The note editor built out of hand-written fakes, with the real use cases behind it.
 *
 * The use cases are the real ones on purpose: what these tests are about is that the editor
 * proposes rather than applies, and that it says what it cannot do — neither of which means
 * anything if the rules underneath are stubbed out.
 */
class NoteDetailViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 9, 0))
    private val tags = FakeNoteTagRepository()
    private val notes = FakeNoteRepository(clock, tagSource = tags)
    private val inkPages = FakeInkPageRepository()
    private val clients = FakeClientRepository()
    private val projects = FakeProjectRepository()
    private val tasks = FakeTaskRepository(clock)
    private val subtasks = FakeSubtaskRepository()
    private val voiceNotes = FakeVoiceNoteRepository()
    private val reminders = FakeReminderRepository()
    private val scheduler = FakeReminderScheduler()
    private val invoices = FakeInvoiceRepository()
    private val settings = FakeSettingsRepository()
    private val renderer = MiraResponseRenderer(clock)
    private val times = TaskTimeFormatter(clock)

    private val yesterday = LocalDateTime.of(2026, 3, 4, 9, 0).toInstant()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    // --- The words ------------------------------------------------------------------------------

    @Test
    fun `an existing note opens with its own words`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Lease renewal", body = "Ask about the deposit", createdAt = yesterday),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.isNew).isFalse()
        assertThat(state.title).isEqualTo("Lease renewal")
        assertThat(state.body).isEqualTo("Ask about the deposit")
        assertThat(state.isDirty).isFalse()
    }

    @Test
    fun `typing changes nothing until it is saved`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onBodyChange("Ask about the deposit")
        runCurrent()

        assertThat(viewModel.uiState.value.isDirty).isTrue()
        assertThat(viewModel.uiState.value.canSave).isTrue()
        assertThat(notes.snapshot().single().body).isEmpty()

        viewModel.onSave()
        runCurrent()

        assertThat(notes.snapshot().single().body).isEqualTo("Ask about the deposit")
        assertThat(viewModel.uiState.value.isDirty).isFalse()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Note saved.")
    }

    @Test
    fun `leaving with unsaved words asks first, and leaving without them does not`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onBackRequested()
        runCurrent()
        assertThat(viewModel.uiState.value.isFinished).isTrue()

        val second = createViewModel(noteId = 1L)
        observe(second.uiState)
        second.onTitleChange("Lease renewal 2027")
        second.onBackRequested()
        runCurrent()

        assertThat(second.uiState.value.isConfirmingDiscard).isTrue()
        assertThat(second.uiState.value.isFinished).isFalse()

        second.onConfirmDiscard()
        runCurrent()
        assertThat(second.uiState.value.isFinished).isTrue()
        assertThat(notes.snapshot().single().title).isEqualTo("Lease renewal")
    }

    @Test
    fun `a note emptied of everything is refused in Abu's own language`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onTitleChange("")
        viewModel.onSave()
        runCurrent()

        val notice = viewModel.uiState.value.notice
        assertThat(notice?.tone).isEqualTo(MiraNoticeTone.DANGER)
        assertThat(notice?.message).isNotEmpty()
        assertThat(notes.snapshot().single().title).isEqualTo("Lease renewal")
    }

    // --- A note in the bin -------------------------------------------------------------------------

    @Test
    fun `a note in the bin is read-only and every action says why`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Binned", body = "one line", deletedAt = yesterday, createdAt = yesterday),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.isDeleted).isTrue()
        assertThat(state.canEdit).isFalse()
        assertThat(state.canSave).isFalse()

        // Every one of them, not just Organize: the name of this test says "every action".
        val blocked = state.actions.filter { !it.isEnabled }
        assertThat(blocked.map { it.action })
            .containsExactlyElementsIn(NoteAction.entries - NoteAction.ASK_MIRA)
        blocked.forEach { action ->
            assertThat(action.reason).isEqualTo(EnglishNoteDetailWording.restoreFirst)
        }

        viewModel.onRestore()
        runCurrent()

        assertThat(notes.snapshot().single().deletedAt).isNull()
        assertThat(viewModel.uiState.value.canEdit).isTrue()
    }

    // --- The six actions ----------------------------------------------------------------------------

    /**
     * Every action in the row is one some state enables. "Extract text" was not: it returned its
     * reason before any state was read, so no note Abu could ever open would light it up, and the
     * screen's handler for it did nothing. Reading handwriting happens on the canvas while he
     * writes, and the handwriting section says so in a sentence instead.
     */
    @Test
    fun `no action is offered that no note could ever enable`() = runTest {
        clients.seed(Client(id = 5L, name = "Rayluxeo", createdAt = yesterday, updatedAt = yesterday))
        notes.seed(
            testNote(id = 1L, title = "Lease renewal", body = "one line", clientId = 5L, createdAt = yesterday),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        val actions = viewModel.uiState.value.actions
        assertThat(actions.map { it.action }).containsExactlyElementsIn(NoteAction.entries)
        assertThat(actions.filter { !it.isEnabled }).isEmpty()
    }

    @Test
    fun `an invoice draft says it needs a client, and raises one once there is`() = runTest {
        clients.seed(Client(id = 5L, name = "Rayluxeo", createdAt = yesterday, updatedAt = yesterday))
        notes.seed(testNote(id = 1L, title = "Lease renewal", body = "one line", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        val blocked = viewModel.uiState.value.actions
            .first { it.action == NoteAction.CREATE_INVOICE_DRAFT }
        assertThat(blocked.isEnabled).isFalse()
        assertThat(blocked.reason).isEqualTo(EnglishNoteDetailWording.linkClientFirst)

        viewModel.onOpenSheet(NoteDetailSheet.CLIENT)
        runCurrent()
        assertThat(viewModel.uiState.value.linkPicker?.options?.map { it.label })
            .containsExactly("Rayluxeo")

        viewModel.onPickLink(5L)
        runCurrent()
        assertThat(notes.snapshot().single().clientId).isEqualTo(5L)

        val allowed = viewModel.uiState.value.actions
            .first { it.action == NoteAction.CREATE_INVOICE_DRAFT }
        assertThat(allowed.isEnabled).isTrue()
        assertThat(allowed.reason).isNull()

        viewModel.onCreateInvoiceDraft()
        runCurrent()

        assertThat(invoices.snapshot()).hasSize(1)
        assertThat(viewModel.uiState.value.notice?.tone).isEqualTo(MiraNoticeTone.CONFIRM)
        assertThat(viewModel.uiState.value.notice?.message).contains("Rayluxeo")
    }

    // --- Organize: a proposal, never an action -------------------------------------------------------

    /**
     * The domain trims a title on the way in. Measured against the STORED text, the field and the
     * row could then never agree again: Save stayed lit, and leaving always asked Abu to discard
     * work he had in fact already saved.
     */
    @Test
    fun `a save whose text the domain trims leaves the note clean`() = runTest {
        notes.seed(testNote(id = 1L, title = null, body = "", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onTitleChange("  Lease renewal  ")
        viewModel.onBodyChange("  rent is due  ")
        viewModel.onSave()
        runCurrent()

        assertThat(notes.snapshot().single().title).isEqualTo("Lease renewal")
        assertThat(viewModel.uiState.value.title).isEqualTo("Lease renewal")
        assertThat(viewModel.uiState.value.isDirty).isFalse()
        assertThat(viewModel.uiState.value.canSave).isFalse()
    }

    @Test
    fun `organize shows what would change and writes none of it until it is applied`() = runTest {
        tags.seed(Tag(id = 7L, name = "rent"))
        clients.seed(Client(id = 5L, name = "Rayluxeo", createdAt = yesterday, updatedAt = yesterday))
        notes.seed(
            testNote(
                id = 1L,
                body = "Rayluxeo wants the lease renewed\nrent is due on the fifth",
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onOrganize()
        runCurrent()

        val proposal = viewModel.uiState.value.organize
        assertThat(viewModel.uiState.value.sheet).isEqualTo(NoteDetailSheet.ORGANIZE)
        assertThat(proposal?.title).isEqualTo("Rayluxeo wants the lease renewed")
        assertThat(proposal?.tags?.map { it.name }).containsExactly("rent")
        assertThat(proposal?.client?.label).isEqualTo("Rayluxeo")

        // Nothing has been written: the sheet is a question, not an action.
        val untouched = notes.snapshot().single()
        assertThat(untouched.title).isNull()
        assertThat(untouched.clientId).isNull()
        assertThat(untouched.tags).isEmpty()

        viewModel.onApplyOrganize()
        runCurrent()

        val filed = notes.snapshot().single()
        assertThat(filed.title).isEqualTo("Rayluxeo wants the lease renewed")
        assertThat(filed.clientId).isEqualTo(5L)
        assertThat(filed.tags.map { it.name }).containsExactly("rent")
        assertThat(viewModel.uiState.value.sheet).isNull()
        assertThat(viewModel.uiState.value.notice?.message)
            .isEqualTo(EnglishNoteDetailWording.organizeApplied)
    }

    @Test
    fun `a part of the proposal Abu turns off is not applied`() = runTest {
        tags.seed(Tag(id = 7L, name = "rent"))
        clients.seed(Client(id = 5L, name = "Rayluxeo", createdAt = yesterday, updatedAt = yesterday))
        notes.seed(
            testNote(
                id = 1L,
                body = "Rayluxeo wants the lease renewed\nrent is due on the fifth",
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onOrganize()
        runCurrent()
        viewModel.onToggleOrganizeClient()
        runCurrent()

        assertThat(viewModel.uiState.value.organize?.acceptClient).isFalse()

        viewModel.onApplyOrganize()
        runCurrent()

        val filed = notes.snapshot().single()
        assertThat(filed.clientId).isNull()
        assertThat(filed.title).isEqualTo("Rayluxeo wants the lease renewed")
    }

    @Test
    fun `a note with nothing worth proposing is told so in a line rather than a sheet`() = runTest {
        notes.seed(testNote(id = 1L, title = "Already filed", body = "nothing to match", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onOrganize()
        runCurrent()

        assertThat(viewModel.uiState.value.sheet).isNull()
        assertThat(viewModel.uiState.value.organize).isNull()
        assertThat(viewModel.uiState.value.notice?.message)
            .isEqualTo(EnglishNoteDetailWording.organizeNothing)
    }

    // --- Turning lines into tasks ----------------------------------------------------------------------

    @Test
    fun `the lines are shown with their ticks before a single task is created`() = runTest {
        notes.seed(
            testNote(
                id = 1L,
                title = "Lease renewal",
                body = "- call the printer\nrent went up last year too",
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onTurnIntoTasks()
        runCurrent()

        val candidates = viewModel.uiState.value.tasks?.candidates.orEmpty()
        assertThat(candidates.map { it.line })
            .containsExactly("call the printer", "rent went up last year too")
        assertThat(candidates.first().isSelected).isTrue()
        assertThat(candidates.last().isSelected).isFalse()
        assertThat(tasks.snapshot()).isEmpty()

        viewModel.onCreateTasks()
        runCurrent()

        assertThat(tasks.snapshot().map { it.title }).containsExactly("call the printer")
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("1 task created.")
        assertThat(viewModel.uiState.value.sheet).isNull()
    }

    @Test
    fun `a line Abu ticks is created and one he unticks is not`() = runTest {
        notes.seed(
            testNote(
                id = 1L,
                title = "Lease renewal",
                body = "- call the printer\n- send the deposit receipt",
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onTurnIntoTasks()
        runCurrent()
        viewModel.onToggleCandidate(1)
        runCurrent()

        assertThat(viewModel.uiState.value.tasks?.candidates?.last()?.isSelected).isFalse()

        viewModel.onCreateTasks()
        runCurrent()

        assertThat(tasks.snapshot().map { it.title }).containsExactly("call the printer")
    }

    // --- Labels, links and reminders ------------------------------------------------------------------

    @Test
    fun `a label is minted once and then worn`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onAddTag("  rent  ")
        runCurrent()

        assertThat(notes.snapshot().single().tags.map { it.name }).containsExactly("rent")
        assertThat(viewModel.uiState.value.tags.map { it.name }).containsExactly("rent")

        viewModel.onRemoveTag(viewModel.uiState.value.tags.single().id)
        runCurrent()

        assertThat(notes.snapshot().single().tags).isEmpty()
    }

    @Test
    fun `a reminder about the note is set for the moment Abu picked`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onCreateReminder(LocalDate.of(2026, 3, 6), LocalTime.of(9, 0))
        runCurrent()

        val reminder = reminders.snapshot().single()
        assertThat(reminder.title).isEqualTo("Lease renewal")
        assertThat(scheduler.scheduled).containsExactly(reminder.id)
        assertThat(viewModel.uiState.value.notice?.tone).isEqualTo(MiraNoticeTone.CONFIRM)
    }

    /**
     * `linkedTaskId` means "this is that task's own reminder". The task editor reads the first
     * upcoming reminder carrying the id as the task's, and cancels it on the next save of that
     * task — so a reminder about a NOTE filed under the note's task was cancelled the next time
     * Abu touched the task's due date, silently, with nothing on screen to tell him.
     */
    @Test
    fun `a reminder about a note is not filed as its task's reminder`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Lease renewal", taskId = 12L, createdAt = yesterday),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onCreateReminder(LocalDate.of(2026, 3, 6), LocalTime.of(9, 0))
        runCurrent()

        val reminder = reminders.snapshot().single()
        assertThat(reminder.title).isEqualTo("Lease renewal")
        assertThat(reminder.linkedTaskId).isNull()
    }

    @Test
    fun `a reminder in the past is refused rather than set`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onCreateReminder(LocalDate.of(2026, 3, 4), LocalTime.of(9, 0))
        runCurrent()

        assertThat(reminders.snapshot()).isEmpty()
        assertThat(viewModel.uiState.value.notice?.tone).isEqualTo(MiraNoticeTone.DANGER)
    }

    /**
     * Organize writes to the note and the editor reads it back. It used to read the WORDS back
     * too, so three lines typed and not yet saved were gone the moment Abu tapped Apply — no
     * question asked, and a line saying the note had been filed.
     */
    @Test
    fun `organize leaves words Abu has not saved alone`() = runTest {
        tags.seed(Tag(id = 7L, name = "rent"))
        clients.seed(Client(id = 5L, name = "Rayluxeo", createdAt = yesterday, updatedAt = yesterday))
        notes.seed(
            testNote(
                id = 1L,
                body = "Rayluxeo wants the lease renewed\nrent is due on the fifth",
                createdAt = yesterday,
            ),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        val typed = "Rayluxeo wants the lease renewed\nrent is due on the fifth\nask about the deposit"
        viewModel.onBodyChange(typed)
        runCurrent()

        viewModel.onOrganize()
        runCurrent()
        viewModel.onApplyOrganize()
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.body).isEqualTo(typed)
        assertThat(state.isDirty).isTrue()
        // The title was Mira's to set, and nothing on screen disagreed with it, so it is taken.
        assertThat(state.title).isEqualTo("Rayluxeo wants the lease renewed")
        assertThat(notes.snapshot().single().title).isEqualTo("Rayluxeo wants the lease renewed")

        viewModel.onSave()
        runCurrent()
        assertThat(notes.snapshot().single().body).isEqualTo(typed)
    }

    /**
     * What is written is the snapshot the save took. A keystroke made while it was in flight is
     * not in it, so the note stays dirty and the words stay on screen — rather than the screen
     * quietly replacing them with what went to disk.
     */
    @Test
    fun `a keystroke made while a save is in flight is not thrown away`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        val gate = CompletableDeferred<Unit>()
        notes.holdWrites = gate
        viewModel.onBodyChange("ask about the deposit")
        viewModel.onSave()
        runCurrent()
        assertThat(viewModel.uiState.value.isSaving).isTrue()

        viewModel.onBodyChange("ask about the deposit\nand the rent")
        gate.complete(Unit)
        notes.holdWrites = null
        runCurrent()

        assertThat(viewModel.uiState.value.body).isEqualTo("ask about the deposit\nand the rent")
        assertThat(viewModel.uiState.value.isDirty).isTrue()
        assertThat(notes.snapshot().single().body).isEqualTo("ask about the deposit")
    }

    // --- A note that does not exist yet ----------------------------------------------------------

    /** The path Quick Capture lands on: note zero, typed into, and saved into a real row. */
    @Test
    fun `a blank note is created by the first save and then edited in place`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.isNew).isTrue()
        assertThat(viewModel.uiState.value.canSave).isFalse()

        viewModel.onTitleChange("Lease renewal")
        viewModel.onBodyChange("Ask about the deposit")
        runCurrent()
        assertThat(viewModel.uiState.value.canSave).isTrue()

        viewModel.onSave()
        runCurrent()

        val created = notes.snapshot().single()
        assertThat(created.title).isEqualTo("Lease renewal")
        assertThat(created.body).isEqualTo("Ask about the deposit")
        assertThat(viewModel.uiState.value.isNew).isFalse()
        assertThat(viewModel.uiState.value.isDirty).isFalse()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Note saved.")

        // The second save edits the row that now exists rather than writing a second one.
        viewModel.onBodyChange("Ask about the deposit and the rent")
        viewModel.onSave()
        runCurrent()

        assertThat(notes.snapshot()).hasSize(1)
        assertThat(notes.snapshot().single().body).isEqualTo("Ask about the deposit and the rent")
    }

    // --- Putting the note away from the editor -----------------------------------------------------

    @Test
    fun `pinning from the editor is written and needs no line of its own`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onOpenSheet(NoteDetailSheet.ACTIONS)
        viewModel.onTogglePinned()
        runCurrent()

        assertThat(notes.snapshot().single().isPinned).isTrue()
        assertThat(viewModel.uiState.value.isPinned).isTrue()
        assertThat(viewModel.uiState.value.sheet).isNull()

        viewModel.onTogglePinned()
        runCurrent()
        assertThat(notes.snapshot().single().isPinned).isFalse()
    }

    @Test
    fun `filing a note from the editor says so, and taking it back says something else`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onArchive()
        runCurrent()

        assertThat(notes.snapshot().single().archivedAt).isNotNull()
        assertThat(viewModel.uiState.value.isArchived).isTrue()
        assertThat(viewModel.uiState.value.notice?.message)
            .isEqualTo(EnglishNoteDetailWording.noteArchived)

        viewModel.onRestore()
        runCurrent()

        assertThat(notes.snapshot().single().archivedAt).isNull()
        assertThat(viewModel.uiState.value.notice?.message)
            .isEqualTo(EnglishNoteDetailWording.noteUnarchived)
    }

    /**
     * Deleting from the editor stays on the note: the bin is soft, and leaving would take the way
     * back with it. The note is read-only afterwards and Restore is the one verb it offers.
     */
    @Test
    fun `deleting from the editor keeps the note on screen, read-only, with a way back`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Lease renewal", body = "one line", createdAt = yesterday),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        viewModel.onDeleteRequested()
        runCurrent()
        assertThat(viewModel.uiState.value.isConfirmingDelete).isTrue()
        assertThat(notes.snapshot().single().deletedAt).isNull()

        viewModel.onCancelDelete()
        runCurrent()
        assertThat(viewModel.uiState.value.isConfirmingDelete).isFalse()

        viewModel.onDeleteRequested()
        viewModel.onConfirmDelete()
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(notes.snapshot().single().deletedAt).isNotNull()
        assertThat(state.isDeleted).isTrue()
        assertThat(state.isFinished).isFalse()
        assertThat(state.canEdit).isFalse()
        assertThat(state.title).isEqualTo("Lease renewal")
        assertThat(state.notice?.message).isEqualTo(EnglishNoteDetailWording.noteDeleted)
    }

    @Test
    fun `picking None in a link picker cuts the note loose`() = runTest {
        clients.seed(Client(id = 5L, name = "Rayluxeo", createdAt = yesterday, updatedAt = yesterday))
        notes.seed(
            testNote(id = 1L, title = "Lease renewal", clientId = 5L, createdAt = yesterday),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.clientLabel).isEqualTo("Rayluxeo")

        viewModel.onOpenSheet(NoteDetailSheet.CLIENT)
        runCurrent()
        assertThat(viewModel.uiState.value.linkPicker?.selectedId).isEqualTo(5L)

        viewModel.onPickLink(null)
        runCurrent()

        assertThat(notes.snapshot().single().clientId).isNull()
        assertThat(viewModel.uiState.value.clientLabel).isNull()
        assertThat(viewModel.uiState.value.sheet).isNull()
    }

    // --- What the note carries ------------------------------------------------------------------------

    @Test
    fun `a page of handwriting is listed by how much is on it and when it was written`() = runTest {
        notes.seed(testNote(id = 1L, title = "Handwritten", createdAt = yesterday))
        inkPages.seed(
            InkPage(
                id = 3L,
                noteId = 1L,
                strokeFileId = 9L,
                imageFileId = 10L,
                widthPx = 1_440,
                heightPx = 2_400,
                strokeCount = 1,
                createdAt = yesterday,
                updatedAt = yesterday,
            ),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        val page = viewModel.uiState.value.inkPages.single()
        assertThat(page.id).isEqualTo(3L)
        assertThat(page.isLost).isFalse()
        assertThat(page.label).startsWith("1 stroke")
    }

    /**
     * Files are reclaimed, so a page can outlive its own ink. Abu is told the writing is gone
     * rather than shown a blank page that looks like he never wrote anything.
     */
    @Test
    fun `a page whose ink was reclaimed is reported as lost rather than shown blank`() = runTest {
        notes.seed(testNote(id = 1L, title = "Handwritten", createdAt = yesterday))
        inkPages.seed(
            InkPage(
                id = 3L,
                noteId = 1L,
                strokeFileId = null,
                imageFileId = null,
                widthPx = 1_440,
                heightPx = 2_400,
                strokeCount = 4,
                createdAt = yesterday,
                updatedAt = yesterday,
            ),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.inkPages.single().isLost).isTrue()
    }

    @Test
    fun `an attachment is listed by name and size`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        notes.attach(noteId = 1L, fileId = 4L)
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        val attachment = viewModel.uiState.value.attachments.single()
        assertThat(attachment.name).isEqualTo("file-4")
        assertThat(attachment.detail).isEqualTo(EnglishNoteDetailWording.fileSize(2_048L))
    }

    /** The recording's length and the moment it was spoken, both worded in Abu's language. */
    @Test
    fun `the recording a note was written up from is listed with its length`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        voiceNotes.seed(
            VoiceNote(
                id = 2L,
                noteId = 1L,
                duration = Duration.ofSeconds(125L),
                recordedAt = yesterday,
                createdAt = yesterday,
                updatedAt = yesterday,
            ),
        )
        val viewModel = createViewModel(noteId = 1L)
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.voiceNote?.label).startsWith("2:05")
    }

    // --- Plumbing ---------------------------------------------------------------------------------------

    private fun createViewModel(noteId: Long = 0L): NoteDetailViewModel {
        val update = UpdateNoteUseCase(notes, inkPages, clock)
        val setTags = SetNoteTagsUseCase(notes)
        val link = LinkNoteUseCase(notes, clients, projects, tasks)
        return NoteDetailViewModel(
            savedState = SavedStateHandle(mapOf("noteId" to noteId)),
            notes = notes,
            inkPages = inkPages,
            tags = tags,
            clients = clients,
            projects = projects,
            tasks = tasks,
            voiceNotes = voiceNotes,
            createNote = CreateNoteUseCase(notes, clock, link),
            updateNote = update,
            pinNote = PinNoteUseCase(notes),
            archiveNote = ArchiveNoteUseCase(notes),
            restoreNote = RestoreNoteUseCase(notes),
            deleteNote = DeleteNoteUseCase(notes),
            setNoteTags = setTags,
            linkNote = link,
            organizeNote = OrganizeNoteUseCase(
                notes = notes,
                tags = tags,
                clients = clients,
                projects = projects,
                update = update,
                setTags = setTags,
                link = link,
            ),
            turnIntoTasks = TurnNoteIntoTasksUseCase(
                notes = notes,
                createTask = CreateTaskUseCase(tasks, subtasks, clock),
                link = link,
            ),
            createReminder = CreateReminderUseCase(reminders, scheduler, clock),
            createInvoiceDraft = CreateInvoiceDraftUseCase(
                invoices = invoices,
                totals = BuildInvoiceTotalsUseCase(),
                activityLog = FakeNoteDetailActivityLog(),
                profile = object : com.rayluxeo.aida.domain.repository.UserProfileRepository {
                    override fun observe() =
                        kotlinx.coroutines.flow.flowOf<com.rayluxeo.aida.domain.model.UserProfile?>(null)

                    override suspend fun current() =
                        com.rayluxeo.aida.core.common.AppResult.Success<com.rayluxeo.aida.domain.model.UserProfile?>(null)

                    override suspend fun save(profile: com.rayluxeo.aida.domain.model.UserProfile) =
                        com.rayluxeo.aida.core.common.AppResult.Success(profile)
                },
                clock = clock,
            ),
            settings = settings,
            clock = clock,
            times = times,
            renderer = renderer,
        )
    }

    /** Keeps the `WhileSubscribed` state flow hot for the length of the test. */
    private fun TestScope.observe(state: StateFlow<NoteDetailUiState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }

    private fun LocalDateTime.toInstant() = atZone(FakeMiraClock.DHAKA).toInstant()
}

private class FakeNoteDetailActivityLog : com.rayluxeo.aida.domain.repository.ActivityLogRepository {
    override fun observeRecent(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeByCategory(category: com.rayluxeo.aida.domain.model.ActivityCategory, limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeForSubject(subject: com.rayluxeo.aida.domain.model.ActivitySubject) = kotlinx.coroutines.flow.flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun between(from: java.time.Instant, to: java.time.Instant) = com.rayluxeo.aida.core.common.AppResult.Success(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun record(entry: com.rayluxeo.aida.domain.model.ActivityEntry) = com.rayluxeo.aida.core.common.AppResult.Success(entry)
}
