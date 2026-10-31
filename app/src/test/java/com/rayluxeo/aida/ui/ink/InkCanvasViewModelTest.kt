package com.rayluxeo.aida.ui.ink

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.ink.HandwritingResult
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkPoint
import com.rayluxeo.aida.domain.ink.InkStroke
import com.rayluxeo.aida.domain.model.InkPage
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.repository.InkPageRepository
import com.rayluxeo.aida.domain.usecase.CreateNoteUseCase
import com.rayluxeo.aida.domain.usecase.ExtractNoteTextUseCase
import com.rayluxeo.aida.domain.usecase.DeleteNoteUseCase
import com.rayluxeo.aida.domain.usecase.LinkNoteUseCase
import com.rayluxeo.aida.ui.tasks.FakeClientRepository
import com.rayluxeo.aida.ui.tasks.FakeProjectRepository
import com.rayluxeo.aida.ui.tasks.FakeTaskRepository
import com.rayluxeo.aida.domain.usecase.UpdateNoteUseCase
import com.rayluxeo.aida.ui.notes.FakeNoteRepository
import com.rayluxeo.aida.ui.tasks.FakeSettingsRepository
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val NOTE_ID = 4L
private const val PAGE_WIDTH_PX = 1_000
private const val PAGE_HEIGHT_PX = 2_000

/**
 * The canvas's screen logic, built out of hand-written collaborators (AGENTS.md §7.4).
 *
 * The note use case behind it is the real one on purpose: what these tests are about is that the
 * canvas obeys the rules the rest of Mira obeys, and that means nothing with the rules stubbed
 * out. The ink repository double refuses a page with no strokes exactly as storage does, because
 * that refusal is the whole reason the removal path exists.
 */
class InkCanvasViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 9, 0))
    private val notes = FakeNoteRepository(clock)
    private val inkPages = FakeInkPageStore(clock)
    private val settings = FakeSettingsRepository()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val links = LinkNoteUseCase(
        notes = notes,
        clients = FakeClientRepository(),
        projects = FakeProjectRepository(),
        tasks = FakeTaskRepository(clock),
    )

    private fun viewModel(): InkCanvasViewModel = InkCanvasViewModel(
        inkPages = inkPages,
        notes = notes,
        createNote = CreateNoteUseCase(notes, clock, links),
        extractNoteText = ExtractNoteTextUseCase(notes, UpdateNoteUseCase(notes, inkPages, clock)),
        deleteNote = DeleteNoteUseCase(notes),
        updateNote = UpdateNoteUseCase(notes, inkPages, clock),
        settings = settings,
    )

    private fun note(body: String = "original", binned: Boolean = false) = Note(
        id = NOTE_ID,
        body = body,
        deletedAt = if (binned) clock.now() else null,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun storedPage(): InkPage = inkPages.seed(
        noteId = NOTE_ID,
        drawing = InkDrawing(
            widthPx = PAGE_WIDTH_PX,
            heightPx = PAGE_HEIGHT_PX,
            strokes = listOf(
                InkStroke(widthPx = 4f, points = listOf(InkPoint(10f, 20f), InkPoint(30f, 40f))),
            ),
        ),
    )

    /** One finished stroke, put on the page the way the canvas view would put it there. */
    private fun InkCanvasViewModel.write(y: Float) {
        engine.onMotion(
            InkMotion(InkPhase.DOWN, InkToolType.STYLUS, listOf(InkSample(100f, y))),
        )
        engine.onMotion(
            InkMotion(InkPhase.UP, InkToolType.STYLUS, listOf(InkSample(200f, y))),
        )
    }

    private fun InkCanvasViewModel.openBlank(): InkCanvasViewModel = apply {
        openPage(noteId = NOTE_ID, pageId = null)
        engine.resize(PAGE_WIDTH_PX, PAGE_HEIGHT_PX)
    }

    /**
     * Quick Ink from the shortcut or the widget is a blank sheet with no note behind it. If the
     * canvas could not mint one, the fastest way into Mira would be the one that cannot save.
     */
    @Test
    fun `a page started with no note behind it mints one when Abu saves`() = runTest {
        val model = viewModel()
        model.openPage(noteId = null, pageId = null)
        model.engine.resize(PAGE_WIDTH_PX, PAGE_HEIGHT_PX)
        runCurrent()
        model.write(y = 100f)

        model.save()
        runCurrent()

        val minted = notes.snapshot().single()
        // Blank on purpose: Abu asked for a page, not a form. The handwriting IS the note until
        // he gives it words, which is why an empty row has to be allowed through here and only
        // here.
        assertThat(minted.title).isNull()
        assertThat(minted.body).isEmpty()
        assertThat(inkPages.stored).hasSize(1)
        assertThat(model.state.value.noteId).isEqualTo(minted.id)
        assertThat(model.state.value.notice).isEqualTo(InkNotice.SAVED)
    }

    /** The other half: a row minted only to hold a page must not outlive the page failing. */
    @Test
    fun `a note minted for a page is taken back out when the page will not save`() = runTest {
        val model = viewModel()
        model.openPage(noteId = null, pageId = null)
        model.engine.resize(PAGE_WIDTH_PX, PAGE_HEIGHT_PX)
        runCurrent()
        model.write(y = 100f)
        inkPages.failWith = MiraErrorCode.STORAGE

        model.save()
        runCurrent()

        // Out of his notes, not out of existence: notes only soft delete, which is what makes an
        // accidental delete undoable. A blank row in the bin is recoverable clutter; a blank row
        // in his list is one he cannot tell apart from writing that was lost.
        assertThat(notes.snapshot().single().deletedAt).isNotNull()
        assertThat(model.state.value.noteId).isNull()
        assertThat(model.state.value.notice).isEqualTo(InkNotice.COULD_NOT_SAVE)
    }

    @Test
    fun `a page Abu has rubbed clean is taken off the note rather than saved again`() = runTest {
        val page = storedPage()
        val model = viewModel()
        model.openPage(noteId = NOTE_ID, pageId = page.id)
        runCurrent()
        model.clear()

        model.save()
        runCurrent()

        // Storage will not hold a page with nothing on it, and says that erasing one down to
        // nothing is a delete the caller has to ask for. It was never asked, so Abu got a red
        // line telling him to try a save that could not succeed, for as long as he kept trying.
        assertThat(inkPages.deleted).containsExactly(page.id)
        assertThat(inkPages.stored).isEmpty()
        assertThat(model.state.value.notice).isEqualTo(InkNotice.PAGE_REMOVED)
        assertThat(model.state.value.pageId).isNull()
        assertThat(model.state.value.hasUnsavedWork).isFalse()
    }

    @Test
    fun `a removal that failed leaves the page on the note and says which way it went`() =
        runTest {
            val page = storedPage()
            val model = viewModel()
            model.openPage(noteId = NOTE_ID, pageId = page.id)
            runCurrent()
            model.clear()
            inkPages.failWith = MiraErrorCode.STORAGE

            model.save()
            runCurrent()

            assertThat(model.state.value.notice).isEqualTo(InkNotice.COULD_NOT_REMOVE)
            assertThat(model.state.value.pageId).isEqualTo(page.id)
            assertThat(model.state.value.hasUnsavedWork).isTrue()
        }

    @Test
    fun `a page with writing on it is written to disk`() = runTest {
        notes.seed(note())
        val model = viewModel().openBlank()
        model.write(y = 100f)

        model.save()
        runCurrent()

        assertThat(inkPages.stored).hasSize(1)
        assertThat(inkPages.deleted).isEmpty()
        assertThat(model.state.value.notice).isEqualTo(InkNotice.SAVED)
        assertThat(model.state.value.pageId).isNotNull()
        assertThat(model.state.value.hasUnsavedWork).isFalse()
    }

    @Test
    fun `the offer to undo a clear goes the moment Abu writes again`() = runTest {
        val model = viewModel().openBlank()
        model.write(y = 100f)
        model.clear()
        assertThat(model.state.value.notice).isEqualTo(InkNotice.PAGE_CLEARED)

        model.write(y = 300f)

        // The line still said "Page cleared" and its Undo was the canvas's plain undo, so
        // pressing it took back the stroke he had just written instead of restoring the page it
        // named — a control doing something other than what it says.
        assertThat(model.state.value.notice).isNull()
    }

    @Test
    fun `undoing from the notice puts the cleared page back and closes the offer`() = runTest {
        val model = viewModel().openBlank()
        model.write(y = 100f)
        model.write(y = 300f)
        model.clear()

        model.undo()

        assertThat(model.state.value.strokeCount).isEqualTo(2)
        assertThat(model.state.value.notice).isNull()
    }

    @Test
    fun `text the keyboard read reaches the note through the note's own rules`() = runTest {
        notes.seed(note(body = "  already written  "))
        val model = viewModel().openBlank()
        model.onHandwriting(HandwritingResult.Recognised("and this"))

        model.keepRecognisedText()
        runCurrent()

        // The blank line is the separator the domain use case uses, and the trimming is the
        // trimming every note verb shares — neither happens on a write that goes straight to the
        // repository, which is what this used to do.
        assertThat(notes.snapshot().single().body).isEqualTo("already written\n\nand this")
        assertThat(model.state.value.notice).isEqualTo(InkNotice.TEXT_KEPT)
        assertThat(model.state.value.recognised).isNull()
    }

    @Test
    fun `text is never filed onto a note that is in the bin`() = runTest {
        notes.seed(note(body = "original", binned = true))
        val model = viewModel().openBlank()
        model.onHandwriting(HandwritingResult.Recognised("and this"))

        model.keepRecognisedText()
        runCurrent()

        // `byId` is the one read that returns a binned note, so this is the only place that can
        // be asked to write into one. Text filed there is text no list but the bin would show
        // him again.
        assertThat(notes.snapshot().single().body).isEqualTo("original")
        assertThat(model.state.value.notice).isEqualTo(InkNotice.NOTE_IN_BIN)
        assertThat(model.state.value.recognised).isEqualTo("and this")
    }
}

/**
 * The handwriting pages, behaving the way storage behaves in the ways the canvas depends on.
 *
 * It refuses an empty drawing, because `InkPageRepositoryImpl` refuses one and the whole point of
 * the removal path is that refusal. A double that quietly accepted a blank page would make the
 * defect invisible from here.
 */
private class FakeInkPageStore(private val clock: MiraClock) : InkPageRepository {

    private val rows = MutableStateFlow<List<InkPage>>(emptyList())
    private val drawings = mutableMapOf<Long, InkDrawing>()
    private var nextId = 1L

    /** Consumed by the next call, so a single refusal can be aimed at one write. */
    var failWith: MiraErrorCode? = null

    val stored: MutableList<InkDrawing> = mutableListOf()
    val deleted: MutableList<Long> = mutableListOf()

    override fun observeForNote(noteId: Long): Flow<List<InkPage>> =
        rows.map { all -> all.filter { it.noteId == noteId } }

    override suspend fun byId(id: Long): AppResult<InkPage> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No ink page with id $id")

    override suspend fun forNote(noteId: Long): AppResult<List<InkPage>> =
        AppResult.Success(rows.value.filter { it.noteId == noteId })


    override suspend fun save(
        noteId: Long,
        drawing: InkDrawing,
        pageId: Long?,
    ): AppResult<InkPage> {
        consumeFailure()?.let { return it }
        if (drawing.isEmpty) {
            return failure(MiraErrorCode.VALIDATION, "A page with no strokes is not handwriting")
        }
        stored += drawing
        return AppResult.Success(put(noteId, drawing, pageId))
    }

    override suspend fun load(pageId: Long): AppResult<InkDrawing> =
        drawings[pageId]
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No strokes for ink page $pageId")

    override suspend fun delete(pageId: Long): AppResult<Unit> {
        consumeFailure()?.let { return it }
        deleted += pageId
        rows.value = rows.value.filterNot { it.id == pageId }
        drawings -= pageId
        return AppResult.Success(Unit)
    }

    fun seed(noteId: Long, drawing: InkDrawing): InkPage = put(noteId, drawing, pageId = null)

    private fun put(noteId: Long, drawing: InkDrawing, pageId: Long?): InkPage {
        val id = pageId ?: nextId++
        val page = InkPage(
            id = id,
            noteId = noteId,
            strokeFileId = id,
            imageFileId = id,
            widthPx = drawing.widthPx,
            heightPx = drawing.heightPx,
            strokeCount = drawing.strokes.size,
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        rows.value = rows.value.filterNot { it.id == id } + page
        drawings[id] = drawing
        return page
    }

    private fun consumeFailure(): AppResult.Failure? {
        val code = failWith ?: return null
        failWith = null
        return failure(code, "The test asked this one to fail")
    }
}
