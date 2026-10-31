package com.rayluxeo.aida.data.note

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraError
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.knowledge.expectFailure
import com.rayluxeo.aida.data.knowledge.expectSuccess
import com.rayluxeo.aida.data.knowledge.hardDeleteFile
import com.rayluxeo.aida.data.knowledge.noteEntity
import com.rayluxeo.aida.data.knowledge.openKnowledgeDatabase
import com.rayluxeo.aida.data.repository.FileRepositoryImpl
import com.rayluxeo.aida.data.repository.InkPageRepositoryImpl
import com.rayluxeo.aida.data.repository.NoteRepositoryImpl
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.repository.FileRepository
import com.rayluxeo.aida.domain.repository.InkPageRepository
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A page of handwriting is a row and two files, and what these tests hold to account is the order
 * they are written in.
 *
 * The one that matters most is the export failing halfway: Abu's previous page has to still be
 * there afterwards, and nothing of the attempt may be left on the disk or in the file index. A
 * save that half-succeeded would show him a note whose writing had silently become blank.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InkPageRepositoryImplTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = TestDispatcherProvider(dispatcher)
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    private lateinit var context: Context
    private lateinit var rasterizer: FakeInkRasterizer
    private lateinit var database: MiraDatabase
    private lateinit var files: FileRepository
    private lateinit var repository: InkPageRepository

    private val drawing: InkDrawing = drawingOf(
        listOf(12f to 40f, 18f to 44f, 26f to 51f),
        listOf(80f to 120f, 92f to 131f),
    )

    @Before
    fun openDatabase() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, INK_DIRECTORY).deleteRecursively()
        rasterizer = FakeInkRasterizer(context.filesDir)
        database = openKnowledgeDatabase()
        files = FileRepositoryImpl(
            dao = database.fileDao(),
            clock = clock,
            dispatchers = dispatchers,
        )
        repository = InkPageRepositoryImpl(
            context = context,
            dao = database.inkPageDao(),
            notes = database.noteDao(),
            files = files,
            codec = RoundTripInkCodec(),
            rasterizer = rasterizer,
            clock = clock,
            dispatchers = dispatchers,
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a saved page reads back as the same strokes`() = runTest(dispatcher) {
        val noteId = seedNote()
        val page = repository.save(noteId, drawing).expectSuccess()

        assertThat(page.noteId).isEqualTo(noteId)
        assertThat(page.strokeCount).isEqualTo(2)
        assertThat(page.widthPx).isEqualTo(drawing.widthPx)
        assertThat(page.isLost).isFalse()
        // The page that comes back is the row that was written, and reading it again agrees.
        assertThat(repository.byId(page.id).expectSuccess()).isEqualTo(page)
        assertThat(repository.load(page.id).expectSuccess()).isEqualTo(drawing)
        assertThat(repository.observeForNote(noteId).first().map { it.id }).containsExactly(page.id)
    }

    @Test
    fun `both files are registered against the page, relative to Mira's own directory`() =
        runTest(dispatcher) {
            val noteId = seedNote()
            val page = repository.save(noteId, drawing).expectSuccess()

            val strokes = files.byId(page.strokeFileId!!).expectSuccess()
            val image = files.byId(page.imageFileId!!).expectSuccess()
            assertThat(strokes.relativePath).startsWith("ink/")
            assertThat(strokes.relativePath).endsWith(".ink")
            assertThat(image.mimeType).isEqualTo("image/png")
            assertThat(strokes.sizeBytes).isGreaterThan(0L)
            assertThat(File(context.filesDir, image.relativePath).exists()).isTrue()
            // One name for the pair, so the two files on disk are visibly one page.
            assertThat(strokes.relativePath.substringBeforeLast('.'))
                .isEqualTo(image.relativePath.substringBeforeLast('.'))
            // What the files list shows Abu is when he wrote the page, in digits that read the
            // same in Bengali and in English — not the UUID that keeps the files apart on disk.
            assertThat(strokes.displayName).isEqualTo("2026-03-05-140000.ink")
            assertThat(image.displayName).isEqualTo("2026-03-05-140000.png")
        }

    @Test
    fun `saving over a page keeps its id and bins the files it used to have`() =
        runTest(dispatcher) {
            val noteId = seedNote()
            val first = repository.save(noteId, drawing).expectSuccess()
            val edited = drawingOf(
                listOf(12f to 40f, 18f to 44f),
                listOf(0f to 1f),
                listOf(5f to 9f),
            )

            val second = repository.save(noteId, edited, pageId = first.id).expectSuccess()

            assertThat(second.id).isEqualTo(first.id)
            assertThat(second.strokeCount).isEqualTo(3)
            assertThat(second.strokeFileId).isNotEqualTo(first.strokeFileId)
            assertThat(repository.load(first.id).expectSuccess()).isEqualTo(edited)
            assertThat(repository.forNote(noteId).expectSuccess()).hasSize(1)

            assertThat(files.byId(first.strokeFileId!!).expectFailure())
                .isEqualTo(MiraErrorCode.NOT_FOUND)
            assertThat(files.listReclaimable().expectSuccess().map { it.id })
                .containsExactly(first.strokeFileId, first.imageFileId)
        }

    @Test
    fun `an export that fails leaves the page Abu already had, and nothing else`() =
        runTest(dispatcher) {
            val noteId = seedNote()
            val saved = repository.save(noteId, drawing).expectSuccess()
            val untouchedAt = database.noteUpdatedAt(noteId)
            rasterizer.failure = AppResult.Failure(MiraError(MiraErrorCode.STORAGE, "no bitmap"))
            clock.moveTo(LocalDateTime.of(2026, 3, 5, 16, 0))

            val refused = repository.save(noteId, drawingOf(listOf(1f to 2f)), pageId = saved.id)

            assertThat(refused.expectFailure()).isEqualTo(MiraErrorCode.STORAGE)
            assertThat(repository.load(saved.id).expectSuccess()).isEqualTo(drawing)
            assertThat(files.byId(saved.strokeFileId!!).expectSuccess().id)
                .isEqualTo(saved.strokeFileId)
            assertThat(files.listReclaimable().expectSuccess()).isEmpty()
            assertThat(files.observeAll().first()).hasSize(2)
            assertThat(File(context.filesDir, INK_DIRECTORY).listFiles()?.size).isEqualTo(2)
            // Nothing was written, so the note has not been touched either.
            assertThat(database.noteUpdatedAt(noteId)).isEqualTo(untouchedAt)
        }

    @Test
    fun `an empty page is refused rather than stored as blank handwriting`() = runTest(dispatcher) {
        val noteId = seedNote()
        val refused = repository.save(noteId, InkDrawing.blank(widthPx = 1_440, heightPx = 3_120))

        assertThat(refused.expectFailure()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.forNote(noteId).expectSuccess()).isEmpty()
        assertThat(files.observeAll().first()).isEmpty()
    }

    @Test
    fun `a page of another note is refused rather than quietly moved`() = runTest(dispatcher) {
        val noteId = seedNote()
        val other = database.noteDao().insert(noteEntity(body = "a different note"))
        val page = repository.save(noteId, drawing).expectSuccess()

        val refused = repository.save(other, drawing, pageId = page.id)

        assertThat(refused.expectFailure()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.byId(page.id).expectSuccess().noteId).isEqualTo(noteId)
        assertThat(repository.save(noteId, drawing, pageId = 404L).expectFailure())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `deleting a page takes the row and bins its files`() = runTest(dispatcher) {
        val noteId = seedNote()
        val page = repository.save(noteId, drawing).expectSuccess()

        assertThat(repository.delete(page.id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.byId(page.id).expectFailure()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.observeForNote(noteId).first()).isEmpty()
        assertThat(files.listReclaimable().expectSuccess()).hasSize(2)
    }

    @Test
    fun `writing a page moves the note it belongs to, and so does taking one away`() =
        runTest(dispatcher) {
            val noteId = database.noteDao()
                .insert(noteEntity(body = "sketched the layout", isPinned = true))
            clock.moveTo(LocalDateTime.of(2026, 3, 5, 15, 30))

            val page = repository.save(noteId, drawing).expectSuccess()

            // Without this the note sits exactly where it was in a list sorted by when it was
            // last worked on, and the handwriting filter — which is re-read whenever the notes
            // table changes — never hears that the page exists.
            assertThat(database.noteUpdatedAt(noteId)).isEqualTo(clock.now().toEpochMilli())
            // The timestamp is moved by re-writing the pin flag, so the pin has to come back
            // exactly as Abu left it.
            assertThat(database.noteDao().findById(noteId)?.isPinned).isTrue()

            clock.moveTo(LocalDateTime.of(2026, 3, 5, 16, 45))
            repository.delete(page.id)

            assertThat(database.noteUpdatedAt(noteId)).isEqualTo(clock.now().toEpochMilli())
        }

    @Test
    fun `a page written on a note puts it in the handwriting list`() = runTest(dispatcher) {
        val noteId = seedNote()
        val notes = NoteRepositoryImpl(
            dao = database.noteDao(),
            inkPages = database.inkPageDao(),
            clock = clock,
            dispatchers = dispatchers,
        )
        assertThat(notes.observe(NoteFilter.Ink).first()).isEmpty()

        repository.save(noteId, drawing).expectSuccess()

        assertThat(notes.observe(NoteFilter.Ink).first().map { it.id }).containsExactly(noteId)
    }

    @Test
    fun `a page whose strokes were reclaimed says so instead of opening blank`() =
        runTest(dispatcher) {
            val noteId = seedNote()
            val page = repository.save(noteId, drawing).expectSuccess()
            database.hardDeleteFile(page.strokeFileId!!)

            val reopened = repository.byId(page.id).expectSuccess()

            assertThat(reopened.strokeFileId).isNull()
            assertThat(reopened.imageFileId).isEqualTo(page.imageFileId)
            assertThat(reopened.isLost).isFalse()
            assertThat(repository.load(page.id).expectFailure()).isEqualTo(MiraErrorCode.NOT_FOUND)
        }

    @Test
    fun `deleting the note takes its pages with it`() = runTest(dispatcher) {
        val noteId = seedNote()
        val page = repository.save(noteId, drawing).expectSuccess()

        database.openHelper.writableDatabase
            .execSQL("DELETE FROM notes WHERE id = ?", arrayOf<Any>(noteId))

        assertThat(repository.byId(page.id).expectFailure()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    private suspend fun seedNote(body: String = "sketched the layout"): Long =
        database.noteDao().insert(noteEntity(body = body))

    private companion object {
        const val INK_DIRECTORY = "ink"
    }
}
