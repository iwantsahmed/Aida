package com.rayluxeo.aida.ui.notes

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.model.NoteSort
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.usecase.ArchiveNoteUseCase
import com.rayluxeo.aida.domain.usecase.DeleteNoteUseCase
import com.rayluxeo.aida.domain.usecase.PinNoteUseCase
import com.rayluxeo.aida.domain.usecase.RestoreNoteUseCase
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import com.rayluxeo.aida.ui.tasks.FakeSettingsRepository
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import java.time.LocalDateTime
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
 * The notes list built out of hand-written fakes. Hilt's test transform does not run under AGP 9,
 * so the view model is assembled here exactly as Dagger would assemble it.
 */
class NotesViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 9, 0))
    private val notes = FakeNoteRepository(clock)
    private val settings = FakeSettingsRepository()
    private val renderer = MiraResponseRenderer(clock)
    private val times = TaskTimeFormatter(clock)

    private val lastWeek = LocalDateTime.of(2026, 2, 26, 9, 0).toInstant()
    private val yesterday = LocalDateTime.of(2026, 3, 4, 9, 0).toInstant()
    private val today = LocalDateTime.of(2026, 3, 5, 8, 0).toInstant()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    // --- The five views ----------------------------------------------------------------------

    @Test
    fun `the list opens on every live note and each view asks for its own`() = runTest {
        notes.inkNoteIds += 4L
        notes.seed(
            testNote(id = 1L, title = "Lease renewal", createdAt = yesterday),
            testNote(id = 2L, title = "Pinned thought", isPinned = true, createdAt = yesterday),
            testNote(id = 3L, title = "Filed away", archivedAt = today, createdAt = lastWeek),
            testNote(id = 4L, title = "Handwritten", createdAt = today),
            testNote(id = 5L, title = "Binned", deletedAt = today, createdAt = lastWeek),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.view).isEqualTo(NotesView.ALL)
        assertThat(titles(viewModel))
            .containsExactly("Pinned thought", "Handwritten", "Lease renewal")

        viewModel.onSelectView(NotesView.PINNED)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Pinned thought")

        viewModel.onSelectView(NotesView.INK)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Handwritten")

        viewModel.onSelectView(NotesView.ARCHIVED)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Filed away")

        viewModel.onSelectView(NotesView.BIN)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Binned")
    }

    @Test
    fun `pinned notes come back in their own section above the rest`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Ordinary", createdAt = yesterday),
            testNote(id = 2L, title = "Pinned", isPinned = true, createdAt = lastWeek),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val sections = viewModel.uiState.value.sections
        assertThat(sections).hasSize(2)
        assertThat(sections.first().items.map { it.title }).containsExactly("Pinned")
        assertThat(sections.last().items.map { it.title }).containsExactly("Ordinary")
    }

    @Test
    fun `nothing pinned means one section with no heading`() = runTest {
        notes.seed(testNote(id = 1L, title = "Ordinary", createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.sections.single().title).isNull()
    }

    // --- Searching, narrowing, ordering ---------------------------------------------------------

    @Test
    fun `what is typed reaches the query and clearing it brings the rest back`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Lease renewal", createdAt = yesterday),
            testNote(id = 2L, title = "Flight booking", body = "Dhaka to Dubai", createdAt = today),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onQueryChange("dubai")
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Flight booking")

        viewModel.onQueryChange("")
        runCurrent()
        assertThat(titles(viewModel)).hasSize(2)
    }

    @Test
    fun `an empty list is reported as empty rather than as a failure`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.isEmpty).isTrue()
        assertThat(viewModel.uiState.value.notice).isNull()
    }

    @Test
    fun `choosing the sort already applied reverses it`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Older", createdAt = lastWeek, updatedAt = lastWeek),
            testNote(id = 2L, title = "Newer", createdAt = today, updatedAt = today),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(titles(viewModel)).containsExactly("Newer", "Older").inOrder()

        viewModel.onSelectSort(NoteSort.UPDATED)
        runCurrent()
        assertThat(viewModel.uiState.value.sort.ascending).isTrue()
        assertThat(titles(viewModel)).containsExactly("Older", "Newer").inOrder()
    }

    @Test
    fun `sorting by title starts at A and leaves pinned notes on top`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Zebra", createdAt = yesterday),
            testNote(id = 2L, title = "Apple", createdAt = today),
            testNote(id = 3L, title = "Mango", isPinned = true, createdAt = lastWeek),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSelectSort(NoteSort.TITLE)
        runCurrent()

        assertThat(viewModel.uiState.value.sort.sort).isEqualTo(NoteSort.TITLE)
        assertThat(viewModel.uiState.value.sort.ascending).isTrue()
        assertThat(titles(viewModel)).containsExactly("Mango", "Apple", "Zebra").inOrder()
    }

    /**
     * A note with no title of its own sorts last whichever way a title sort runs, and its body is
     * never consulted for the order — storage writes `notes.title IS NULL ASC` outside the
     * direction. The row still READS as its first line, which is why sorting by the body would
     * look like a list that had simply got it wrong: "Apple" sitting under "Zebra".
     */
    @Test
    fun `an untitled note sorts last by title, both ways, whatever its first line says`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Zebra", createdAt = yesterday),
            testNote(id = 2L, body = "Apple, and what to ask about it", createdAt = today),
            testNote(id = 3L, title = "Mango", createdAt = lastWeek),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onSelectSort(NoteSort.TITLE)
        runCurrent()
        assertThat(titles(viewModel))
            .containsExactly("Mango", "Zebra", "Apple, and what to ask about it").inOrder()

        viewModel.onSelectSort(NoteSort.TITLE)
        runCurrent()
        assertThat(viewModel.uiState.value.sort.ascending).isFalse()
        assertThat(titles(viewModel))
            .containsExactly("Zebra", "Mango", "Apple, and what to ask about it").inOrder()
    }

    @Test
    fun `a tag chip narrows the list and says which label is chosen`() = runTest {
        val rent = Tag(id = 7L, name = "rent")
        notes.seed(
            testNote(id = 1L, title = "Lease renewal", tags = listOf(rent), createdAt = yesterday),
            testNote(id = 2L, title = "Flight booking", createdAt = today),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.tags.map { it.label }).containsExactly("rent")

        viewModel.onToggleTag(rent.id)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Lease renewal")
        assertThat(viewModel.uiState.value.tags.single().isSelected).isTrue()

        viewModel.onClearTags()
        runCurrent()
        assertThat(titles(viewModel)).hasSize(2)
        assertThat(viewModel.uiState.value.hasTagFilter).isFalse()
    }

    /**
     * A second chip narrows the list; it does not widen it. Storage counts the distinct tags a
     * note carries and demands all of them, and a screen that believed otherwise would show Abu
     * rows he had just filtered out.
     */
    @Test
    fun `a second tag chip narrows the list rather than widening it`() = runTest {
        val rent = Tag(id = 7L, name = "rent")
        val rayluxeo = Tag(id = 8L, name = "rayluxeo")
        notes.seed(
            testNote(id = 1L, title = "Both", tags = listOf(rent, rayluxeo), createdAt = yesterday),
            testNote(id = 2L, title = "Rent only", tags = listOf(rent), createdAt = today),
            testNote(id = 3L, title = "Client only", tags = listOf(rayluxeo), createdAt = today),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onToggleTag(rent.id)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Both", "Rent only")

        viewModel.onToggleTag(rayluxeo.id)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Both")

        viewModel.onToggleTag(rent.id)
        runCurrent()
        assertThat(titles(viewModel)).containsExactly("Both", "Client only")
    }

    // --- Acting on one note ---------------------------------------------------------------------

    @Test
    fun `filing a note offers an undo that puts it back`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onArchive(1L)
        runCurrent()

        assertThat(notes.snapshot().single().archivedAt).isNotNull()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Note filed.")
        assertThat(viewModel.uiState.value.notice?.undoLabel).isEqualTo("Undo")
        assertThat(titles(viewModel)).isEmpty()

        viewModel.onUndo()
        runCurrent()

        assertThat(notes.snapshot().single().archivedAt).isNull()
        assertThat(titles(viewModel)).containsExactly("Lease renewal")
    }

    @Test
    fun `deleting is soft and the undo restores the same note`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onDelete(1L)
        runCurrent()

        assertThat(notes.snapshot().single().deletedAt).isNotNull()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Note deleted.")

        viewModel.onUndo()
        runCurrent()

        assertThat(notes.snapshot().single().deletedAt).isNull()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Put back.")
    }

    @Test
    fun `restoring from the bin says so, and offers no undo of its own`() = runTest {
        notes.seed(testNote(id = 1L, title = "Binned", deletedAt = today, createdAt = lastWeek))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onRestore(1L)
        runCurrent()

        assertThat(notes.snapshot().single().deletedAt).isNull()
        assertThat(viewModel.uiState.value.notice?.message).isEqualTo("Note restored.")
        assertThat(viewModel.uiState.value.notice?.undoLabel).isNull()
    }

    @Test
    fun `the actions sheet reads the note rather than the row it was tapped from`() = runTest {
        notes.seed(
            testNote(id = 1L, body = "No title at all\nsecond line", isPinned = true, createdAt = today),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onOpenActions(1L)
        runCurrent()

        val actions = viewModel.uiState.value.actions
        assertThat(actions?.title).isEqualTo("No title at all")
        assertThat(actions?.isPinned).isTrue()
        assertThat(actions?.isDeleted).isFalse()
    }

    /**
     * Pinning moves the row to the top, which Abu can see, so it says nothing. What it must not do
     * is leave the last notice's Undo standing: that undo belongs to a note that was filed or
     * binned, and a stale one would put back something he had stopped thinking about.
     */
    @Test
    fun `pinning writes the pin and clears an undo that is no longer about it`() = runTest {
        notes.seed(
            testNote(id = 1L, title = "Lease renewal", createdAt = yesterday),
            testNote(id = 2L, title = "Flight booking", createdAt = today),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onArchive(2L)
        runCurrent()
        assertThat(viewModel.uiState.value.notice?.undoLabel).isEqualTo("Undo")

        viewModel.onTogglePinned(1L, pinned = true)
        runCurrent()

        assertThat(notes.snapshot().first { it.id == 1L }.isPinned).isTrue()
        assertThat(viewModel.uiState.value.notice).isNull()
        assertThat(viewModel.uiState.value.actions).isNull()

        viewModel.onUndo()
        runCurrent()

        // Nothing to undo: the filed note stays filed rather than coming back on a stale id.
        assertThat(notes.snapshot().first { it.id == 2L }.archivedAt).isNotNull()

        viewModel.onTogglePinned(1L, pinned = false)
        runCurrent()
        assertThat(notes.snapshot().first { it.id == 1L }.isPinned).isFalse()
    }

    @Test
    fun `a storage failure is worded by Mira rather than shown as a code`() = runTest {
        notes.seed(testNote(id = 1L, title = "Lease renewal", createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        notes.failWith = MiraErrorCode.STORAGE
        viewModel.onDelete(1L)
        runCurrent()

        val notice = viewModel.uiState.value.notice
        assertThat(notice?.tone).isEqualTo(NoteNoticeTone.PROBLEM)
        assertThat(notice?.message).isNotEmpty()
        assertThat(notice?.message).doesNotContain("STORAGE")
        assertThat(notice?.undoLabel).isNull()
    }

    @Test
    fun `the screen speaks Bengali when Abu has chosen it`() = runTest {
        settings.setLanguageMode(LanguageMode.BANGLA)
        notes.seed(testNote(id = 1L, title = "চুক্তি নবায়ন", createdAt = yesterday))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onArchive(1L)
        runCurrent()

        // Filing a note and saving one are different things, and used to be the same sentence.
        assertThat(viewModel.uiState.value.notice?.message)
            .isEqualTo("নোট সংরক্ষণাগারে রাখা হয়েছে।")
    }

    // --- Plumbing --------------------------------------------------------------------------------

    private fun createViewModel() = NotesViewModel(
        notes = notes,
        pinNote = PinNoteUseCase(notes),
        archiveNote = ArchiveNoteUseCase(notes),
        restoreNote = RestoreNoteUseCase(notes),
        deleteNote = DeleteNoteUseCase(notes),
        settings = settings,
        times = times,
        renderer = renderer,
    )

    private fun titles(viewModel: NotesViewModel): List<String> =
        viewModel.uiState.value.sections.flatMap { section -> section.items.map { it.title } }

    /** Keeps the `WhileSubscribed` state flow hot for the length of the test. */
    private fun TestScope.observe(state: StateFlow<NotesUiState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }

    private fun LocalDateTime.toInstant() = atZone(FakeMiraClock.DHAKA).toInstant()
}
