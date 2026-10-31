package com.rayluxeo.aida.data.knowledge

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.NoteRepositoryImpl
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.repository.NoteRepository
import java.time.Instant
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NoteRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    @Before
    fun openDatabase() {
        database = openKnowledgeDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a created note comes back with its id and reads the same way`() = runTest {
        val repository = repository()

        val created = repository.create(note(title = "Rahman brief", body = "gold logo, no purple"))

        val id = created.expectSuccess().id
        assertThat(id).isGreaterThan(0L)
        val loaded = repository.byId(id).expectSuccess()
        assertThat(loaded.title).isEqualTo("Rahman brief")
        assertThat(loaded.body).isEqualTo("gold logo, no purple")
        assertThat(loaded.language).isEqualTo(SpokenLanguage.ENGLISH)
        assertThat(loaded.createdAt).isEqualTo(clock.now())
    }

    @Test
    fun `a note written in Bengali keeps its language across the boundary`() = runTest {
        val repository = repository()

        val created = repository.create(
            note(body = "sonar logo", language = SpokenLanguage.BENGALI),
        ).expectSuccess()

        assertThat(repository.byId(created.id).expectSuccess().language)
            .isEqualTo(SpokenLanguage.BENGALI)
    }

    @Test
    fun `create never puts a note straight into the bin`() = runTest {
        val repository = repository()

        val created = repository.create(
            note(body = "live", deletedAt = Instant.ofEpochMilli(SEED_MILLIS)),
        ).expectSuccess()

        assertThat(created.deletedAt).isNull()
        assertThat(repository.observe(NoteFilter.All).first().map { it.id }).containsExactly(created.id)
    }

    @Test
    fun `deleting hides the note from every list and from search, and restore undoes it`() = runTest {
        val repository = repository()
        val id = repository.create(note(title = "Numbering", body = "invoice numbering scheme"))
            .expectSuccess().id

        assertThat(repository.delete(id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.observe(NoteFilter.All).first()).isEmpty()
        assertThat(repository.search("numbering").expectSuccess()).isEmpty()
        assertThat(repository.search(null).expectSuccess()).isEmpty()
        assertThat(repository.byId(id).expectSuccess().deletedAt).isEqualTo(clock.now())

        assertThat(repository.restore(id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.search("numbering").expectSuccess().map { it.id }).containsExactly(id)
    }

    @Test
    fun `deleting a note that is already in the bin reports NOT_FOUND`() = runTest {
        val repository = repository()
        val id = repository.create(note(body = "once")).expectSuccess().id
        repository.delete(id)

        assertThat(repository.delete(id).expectFailure()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.restore(999L).expectFailure()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a blank query returns every live note rather than nothing`() = runTest {
        val repository = repository()
        repository.create(note(body = "first"))
        repository.create(note(body = "second"))

        assertThat(repository.search(null).expectSuccess()).hasSize(2)
        assertThat(repository.search("   ").expectSuccess()).hasSize(2)
    }

    @Test
    fun `search survives dictated punctuation`() = runTest {
        val repository = repository()
        val id = repository.create(note(body = "Rahman's gold logo")).expectSuccess().id

        assertThat(repository.search("rahman's").expectSuccess().map { it.id }).containsExactly(id)
    }

    @Test
    fun `pinning moves the note to the top and is refused once it is binned`() = runTest {
        val repository = repository()
        repository.create(note(title = "Plain", body = "first"))
        val pinnedId = repository.create(note(title = "Important", body = "second")).expectSuccess().id

        assertThat(repository.setPinned(pinnedId, pinned = true).expectSuccess().isPinned).isTrue()
        assertThat(repository.observe(NoteFilter.All).first().first().id).isEqualTo(pinnedId)
        assertThat(repository.observe(NoteFilter.Pinned).first().map { it.id }).containsExactly(pinnedId)

        repository.delete(pinnedId)
        assertThat(repository.setPinned(pinnedId, pinned = false).expectFailure())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `an edit stamps the clock and is visible to search`() = runTest {
        val repository = repository()
        val created = repository.create(note(body = "gold logo")).expectSuccess()
        clock.moveTo(LocalDateTime.of(2026, 3, 6, 9, 30))

        val updated = repository.update(created.copy(body = "silver logo")).expectSuccess()

        assertThat(updated.updatedAt).isEqualTo(clock.now())
        assertThat(updated.createdAt).isEqualTo(created.createdAt)
        assertThat(repository.search("silver").expectSuccess().map { it.id }).containsExactly(created.id)
        assertThat(repository.search("gold").expectSuccess()).isEmpty()
    }

    @Test
    fun `attaching the same file twice is refused with a reason rather than a crash`() = runTest {
        val repository = repository()
        val noteId = repository.create(note(body = "see the contract")).expectSuccess().id
        val fileId = database.seedFile()

        val first = repository.attach(noteId, fileId).expectSuccess()
        val second = repository.attach(noteId, fileId)

        assertThat(first.displayName).isEqualTo("contract.pdf")
        assertThat(first.position).isEqualTo(0)
        assertThat(second.expectFailure()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(database.countAttachments()).isEqualTo(1)
    }

    @Test
    fun `attaching a file that does not exist fails as storage rather than throwing`() = runTest {
        val repository = repository()
        val noteId = repository.create(note(body = "points at nothing")).expectSuccess().id

        assertThat(repository.attach(noteId, fileId = 404L).expectFailure())
            .isEqualTo(MiraErrorCode.STORAGE)
    }

    @Test
    fun `detaching a file that is not attached reports NOT_FOUND`() = runTest {
        val repository = repository()
        val noteId = repository.create(note(body = "no documents")).expectSuccess().id
        val fileId = database.seedFile()

        assertThat(repository.detach(noteId, fileId).expectFailure()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `the attachment list carries what the file is and drops it when the file goes`() = runTest {
        val repository = repository()
        val noteId = repository.create(note(body = "two documents")).expectSuccess().id
        val contract = database.seedFile("contract.pdf")
        val receipt = database.seedFile("receipt.pdf")
        repository.attach(noteId, contract)
        repository.attach(noteId, receipt)

        assertThat(repository.observeAttachments(noteId).first().map { it.displayName })
            .containsExactly("contract.pdf", "receipt.pdf").inOrder()

        assertThat(repository.detach(noteId, contract)).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.observeAttachments(noteId).first().map { it.fileId }).containsExactly(receipt)
    }

    @Test
    fun `notes scoped to a client and a project read back under that scope`() = runTest {
        val repository = repository()
        val clientId = database.seedClient()
        val projectId = database.seedProject(clientId = clientId)
        val clientNote = repository.create(note(body = "call back", clientId = clientId)).expectSuccess()
        val projectNote = repository.create(note(body = "gold logo", projectId = projectId)).expectSuccess()

        assertThat(repository.observe(NoteFilter.ForClient(clientId)).first().map { it.id })
            .containsExactly(clientNote.id)
        assertThat(repository.observe(NoteFilter.ForProject(projectId)).first().map { it.id })
            .containsExactly(projectNote.id)
    }

    private fun repository(): NoteRepository = NoteRepositoryImpl(
        dao = database.noteDao(),
        inkPages = database.inkPageDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun note(
        title: String? = null,
        body: String,
        clientId: Long? = null,
        projectId: Long? = null,
        language: SpokenLanguage = SpokenLanguage.ENGLISH,
        deletedAt: Instant? = null,
    ) = Note(
        title = title,
        body = body,
        clientId = clientId,
        projectId = projectId,
        language = language,
        deletedAt = deletedAt,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )
}
