package com.rayluxeo.aida.data.knowledge

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.NoteDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NoteDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: NoteDao

    @Before
    fun openDatabase() {
        database = openKnowledgeDatabase()
        dao = database.noteDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `insert returns the new row id and the note is observable`() = runTest {
        val id = dao.insert(noteEntity(title = "Meeting", body = "Rahman wants the logo in gold"))

        assertThat(id).isGreaterThan(0L)
        assertThat(dao.observeAll().first().single().id).isEqualTo(id)
    }

    @Test
    fun `pinned notes sort above the rest`() = runTest {
        dao.insert(noteEntity(title = "Old", body = "first", updatedAtEpochMillis = SEED_MILLIS + 5_000))
        dao.insert(noteEntity(title = "Pinned", body = "second", isPinned = true, updatedAtEpochMillis = SEED_MILLIS))

        assertThat(dao.observeAll().first().map { it.title }).containsExactly("Pinned", "Old").inOrder()
        assertThat(dao.observePinned().first().map { it.title }).containsExactly("Pinned")
    }

    @Test
    fun `notes are scoped to a client and to a project`() = runTest {
        val clientId = database.seedClient()
        val projectId = database.seedProject(clientId = clientId)
        dao.insert(noteEntity(title = "For the client", body = "call back", clientId = clientId))
        dao.insert(noteEntity(title = "For the project", body = "gold logo", projectId = projectId))
        dao.insert(noteEntity(title = "Loose", body = "nothing attached"))

        assertThat(dao.observeForClient(clientId).first().map { it.title }).containsExactly("For the client")
        assertThat(dao.observeForProject(projectId).first().map { it.title }).containsExactly("For the project")
    }

    @Test
    fun `deleting a client orphans its notes rather than deleting them`() = runTest {
        val clientId = database.seedClient()
        val id = dao.insert(noteEntity(body = "worth keeping", clientId = clientId))

        database.openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = ?", arrayOf<Any>(clientId))

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored?.clientId).isNull()
        assertThat(stored?.body).isEqualTo("worth keeping")
    }

    @Test
    fun `soft delete hides the note from every read but leaves it readable by id`() = runTest {
        val id = dao.insert(noteEntity(title = "Gone", body = "invoice numbering scheme"))

        assertThat(dao.softDelete(id, SEED_MILLIS + 1_000)).isEqualTo(1)

        assertThat(dao.observeAll().first()).isEmpty()
        assertThat(dao.listAll()).isEmpty()
        assertThat(dao.search("invoice*")).isEmpty()
        assertThat(dao.findById(id)?.deletedAtEpochMillis).isEqualTo(SEED_MILLIS + 1_000)
    }

    @Test
    fun `a binned note stays out of search even though the index still holds its words`() = runTest {
        val id = dao.insert(noteEntity(title = "Numbering", body = "invoice numbering scheme"))
        assertThat(dao.search("numbering*").map { it.id }).containsExactly(id)

        dao.softDelete(id, SEED_MILLIS + 1_000)

        assertThat(dao.search("numbering*")).isEmpty()
    }

    @Test
    fun `restore brings the note back into every read`() = runTest {
        val id = dao.insert(noteEntity(title = "Back", body = "invoice numbering scheme"))
        dao.softDelete(id, SEED_MILLIS + 1_000)

        assertThat(dao.restore(id, SEED_MILLIS + 2_000)).isEqualTo(1)

        assertThat(dao.observeAll().first().map { it.id }).containsExactly(id)
        assertThat(dao.search("numbering*").map { it.id }).containsExactly(id)
        assertThat(dao.findById(id)?.deletedAtEpochMillis).isNull()
    }

    @Test
    fun `a second delete reports nothing changed and keeps the first time`() = runTest {
        val id = dao.insert(noteEntity(body = "once"))
        dao.softDelete(id, SEED_MILLIS + 1_000)

        assertThat(dao.softDelete(id, SEED_MILLIS + 9_000)).isEqualTo(0)
        assertThat(dao.findById(id)?.deletedAtEpochMillis).isEqualTo(SEED_MILLIS + 1_000)
    }

    @Test
    fun `full text search matches the title and the body and follows an edit`() = runTest {
        val id = dao.insert(noteEntity(title = "Rahman brief", body = "gold logo, no purple"))
        dao.insert(noteEntity(title = "Groceries", body = "rice, lentils"))

        assertThat(dao.search("rahman*").map { it.id }).containsExactly(id)
        assertThat(dao.search("purple*").map { it.id }).containsExactly(id)

        dao.update(dao.findById(id)!!.copy(body = "silver logo"))

        assertThat(dao.search("silver*").map { it.id }).containsExactly(id)
        assertThat(dao.search("purple*")).isEmpty()
    }

    @Test
    fun `attaching a file twice is refused rather than duplicated`() = runTest {
        val noteId = dao.insert(noteEntity(body = "see the contract"))
        val fileId = database.seedFile()

        val first = dao.attachFile(noteId, fileId, SEED_MILLIS)
        val second = dao.attachFile(noteId, fileId, SEED_MILLIS + 1_000)

        assertThat(first).isNotNull()
        assertThat(second).isNull()
        assertThat(database.countAttachments()).isEqualTo(1)
    }

    @Test
    fun `attachments are numbered densely from zero and carry the file details`() = runTest {
        val noteId = dao.insert(noteEntity(body = "two documents"))
        val contract = database.seedFile("contract.pdf")
        val receipt = database.seedFile("receipt.pdf")

        dao.attachFile(noteId, contract, SEED_MILLIS)
        dao.attachFile(noteId, receipt, SEED_MILLIS + 1_000)

        val attachments = dao.observeAttachments(noteId).first()
        assertThat(attachments.map { it.position }).containsExactly(0, 1).inOrder()
        assertThat(attachments.map { it.displayName }).containsExactly("contract.pdf", "receipt.pdf").inOrder()
        assertThat(attachments.first().sizeBytes).isEqualTo(2_048L)
        assertThat(attachments.first().relativePath).isEqualTo("documents/contract.pdf")
    }

    @Test
    fun `attaching a file that does not exist is refused by the foreign key`() = runTest {
        val noteId = dao.insert(noteEntity(body = "points at nothing"))

        val attached = runCatching { dao.attachFile(noteId, fileId = 404L, atEpochMillis = SEED_MILLIS) }

        assertThat(attached.isFailure).isTrue()
        assertThat(database.countAttachments()).isEqualTo(0)
    }

    @Test
    fun `deleting a note takes its attachments with it and leaves the file alone`() = runTest {
        val noteId = dao.insert(noteEntity(body = "see the contract"))
        val fileId = database.seedFile()
        dao.attachFile(noteId, fileId, SEED_MILLIS)

        database.hardDeleteNote(noteId)

        assertThat(database.countAttachments()).isEqualTo(0)
        assertThat(dao.observeAttachments(noteId).first()).isEmpty()
        assertThat(database.fileExists(fileId)).isTrue()
    }

    @Test
    fun `deleting a file drops the citation and leaves the note standing`() = runTest {
        val noteId = dao.insert(noteEntity(body = "see the contract"))
        val fileId = database.seedFile()
        dao.attachFile(noteId, fileId, SEED_MILLIS)

        database.hardDeleteFile(fileId)

        assertThat(database.countAttachments()).isEqualTo(0)
        assertThat(dao.findById(noteId)).isNotNull()
    }

    @Test
    fun `a file in the bin drops out of the attachment list without breaking it`() = runTest {
        val noteId = dao.insert(noteEntity(body = "two documents"))
        val contract = database.seedFile("contract.pdf")
        val receipt = database.seedFile("receipt.pdf")
        dao.attachFile(noteId, contract, SEED_MILLIS)
        dao.attachFile(noteId, receipt, SEED_MILLIS + 1_000)

        database.softDeleteFile(contract)

        assertThat(dao.observeAttachments(noteId).first().map { it.displayName })
            .containsExactly("receipt.pdf")
        assertThat(database.countAttachments()).isEqualTo(2)
    }

    @Test
    fun `detaching removes only that citation`() = runTest {
        val noteId = dao.insert(noteEntity(body = "two documents"))
        val contract = database.seedFile("contract.pdf")
        val receipt = database.seedFile("receipt.pdf")
        dao.attachFile(noteId, contract, SEED_MILLIS)
        dao.attachFile(noteId, receipt, SEED_MILLIS + 1_000)

        assertThat(dao.deleteAttachment(noteId, contract)).isEqualTo(1)
        assertThat(dao.deleteAttachment(noteId, contract)).isEqualTo(0)

        assertThat(dao.observeAttachments(noteId).first().map { it.fileId }).containsExactly(receipt)
    }

    @Test
    fun `setPinned refuses a note that is in the bin`() = runTest {
        val id = dao.insert(noteEntity(body = "binned"))
        dao.softDelete(id, SEED_MILLIS + 1_000)

        assertThat(dao.setPinned(id, pinned = true, atEpochMillis = SEED_MILLIS + 2_000)).isEqualTo(0)
        assertThat(dao.findById(id)?.isPinned).isFalse()
    }
}
