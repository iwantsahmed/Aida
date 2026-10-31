package com.rayluxeo.aida.data.platform

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.FileRepositoryImpl
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.repository.FileRepository
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
class FileRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))
    private val createdAt: Instant = clock.now()

    @Before
    fun openDatabase() {
        database = openTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a registered file round trips through the database unchanged`() = runTest {
        val repository = repository()

        val registered = repository.register(
            record(name = "Contract", path = "docs/contract.pdf", text = "signed on the third"),
        ).value()

        val loaded = repository.byId(registered.id).value()
        assertThat(loaded.displayName).isEqualTo("Contract")
        assertThat(loaded.relativePath).isEqualTo("docs/contract.pdf")
        assertThat(loaded.mimeType).isEqualTo("application/pdf")
        assertThat(loaded.sizeBytes).isEqualTo(2_048L)
        assertThat(loaded.source).isEqualTo(FileSource.IMPORT)
        assertThat(loaded.extractedText).isEqualTo("signed on the third")
        assertThat(loaded.createdAt).isEqualTo(createdAt)
        assertThat(loaded.deletedAt).isNull()
    }

    @Test
    fun `registration mints a uuid and keeps one supplied by a restore`() = runTest {
        val repository = repository()

        val minted = repository.register(record(name = "Deed", path = "docs/deed.pdf")).value()
        val restored = repository.register(
            record(name = "Older deed", path = "docs/older-deed.pdf").copy(uuid = "from-backup"),
        ).value()

        assertThat(minted.uuid).isNotEmpty()
        assertThat(minted.uuid).isNotEqualTo(restored.uuid)
        assertThat(restored.uuid).isEqualTo("from-backup")
        assertThat(repository.byId(minted.id).value().uuid).isEqualTo(minted.uuid)
    }

    @Test
    fun `registering the same path twice is refused as a validation failure`() = runTest {
        val repository = repository()
        repository.register(record(name = "Contract", path = "docs/contract.pdf"))

        val second = repository.register(record(name = "Contract again", path = "docs/contract.pdf"))

        assertThat(second).isInstanceOf(AppResult.Failure::class.java)
        assertThat((second as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeAll().first()).hasSize(1)
    }

    @Test
    fun `deleting hides the file from reads but keeps its bytes accountable`() = runTest {
        val repository = repository()
        val file = repository.register(record(name = "Receipt", path = "docs/receipt.pdf")).value()

        assertThat(repository.softDelete(file.id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.observeAll().first()).isEmpty()
        assertThat(repository.search(query = null).value()).isEmpty()
        assertThat((repository.byId(file.id) as AppResult.Failure).error.code)
            .isEqualTo(MiraErrorCode.NOT_FOUND)

        val reclaimable = repository.listReclaimable().value()
        assertThat(reclaimable.map { it.id }).containsExactly(file.id)
        assertThat(reclaimable.single().relativePath).isEqualTo("docs/receipt.pdf")
        assertThat(reclaimable.single().deletedAt).isEqualTo(clock.now())
    }

    @Test
    fun `purging is refused until the file is in the bin`() = runTest {
        val repository = repository()
        val file = repository.register(record(name = "Receipt", path = "docs/receipt.pdf")).value()

        val tooEarly = repository.purge(file.id)
        assertThat((tooEarly as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(database.countRows("files")).isEqualTo(1)

        repository.softDelete(file.id)
        assertThat(repository.purge(file.id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(database.countRows("files")).isEqualTo(0)
        assertThat(repository.listReclaimable().value()).isEmpty()
    }

    @Test
    fun `restoring returns the file to the list`() = runTest {
        val repository = repository()
        val file = repository.register(record(name = "Receipt", path = "docs/receipt.pdf")).value()
        repository.softDelete(file.id)

        val restored = repository.restore(file.id).value()

        assertThat(restored.deletedAt).isNull()
        assertThat(repository.observeAll().first().map { it.id }).containsExactly(file.id)
        assertThat(repository.listReclaimable().value()).isEmpty()
    }

    @Test
    fun `a scan is registered with its file or not at all`() = runTest {
        val repository = repository()

        val scanned = repository.registerScan(
            record(name = "Passport", path = "docs/passport.pdf", source = FileSource.SCAN),
            pageCount = 2,
        ).value()

        val scan = repository.scanFor(scanned.id).value()
        assertThat(scan?.pageCount).isEqualTo(2)
        assertThat(scan?.fileId).isEqualTo(scanned.id)
        assertThat(scan?.recognisedAt).isNull()
        assertThat(scan?.createdAt).isEqualTo(clock.now())
    }

    @Test
    fun `a scan of no pages is refused before anything is written`() = runTest {
        val repository = repository()

        val refused = repository.registerScan(
            record(name = "Passport", path = "docs/passport.pdf", source = FileSource.SCAN),
            pageCount = 0,
        )

        assertThat((refused as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(database.countRows("files")).isEqualTo(0)
        assertThat(database.countRows("scan_records")).isEqualTo(0)
    }

    @Test
    fun `recognised text is searchable and marks the scan as read`() = runTest {
        val repository = repository()
        val scanned = repository.registerScan(
            record(name = "Bill", path = "docs/bill.pdf", source = FileSource.SCAN),
            pageCount = 1,
        ).value()

        val updated = repository.recordRecognisedText(scanned.id, "Kamal paid 1234.56 in March").value()

        assertThat(updated.extractedText).isEqualTo("Kamal paid 1234.56 in March")
        assertThat(repository.scanFor(scanned.id).value()?.recognisedAt).isEqualTo(clock.now())
        assertThat(repository.search("kamal").value().map { it.id }).containsExactly(scanned.id)
    }

    @Test
    fun `search ignores punctuation that SQLite would read as an operator`() = runTest {
        val repository = repository()
        repository.register(record(name = "Abu's rent receipt", path = "docs/rent.pdf"))

        assertThat(repository.search("abu's").value().map { it.displayName })
            .containsExactly("Abu's rent receipt")
        assertThat(repository.search("   ").value()).hasSize(1)
    }

    @Test
    fun `updating cannot move a file to a path another file holds`() = runTest {
        val repository = repository()
        repository.register(record(name = "First", path = "docs/first.pdf"))
        val second = repository.register(record(name = "Second", path = "docs/second.pdf")).value()

        val refused = repository.update(second.copy(relativePath = "docs/first.pdf"))

        assertThat((refused as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.byId(second.id).value().relativePath).isEqualTo("docs/second.pdf")
    }

    @Test
    fun `updating stamps the change and leaves identity alone`() = runTest {
        val repository = repository()
        val file = repository.register(record(name = "Deed", path = "docs/deed.pdf")).value()
        clock.moveTo(LocalDateTime.of(2026, 3, 6, 9, 0))

        val updated = repository.update(
            file.copy(displayName = "Deed of sale", uuid = "rewritten", createdAt = Instant.EPOCH),
        ).value()

        assertThat(updated.displayName).isEqualTo("Deed of sale")
        assertThat(updated.uuid).isEqualTo(file.uuid)
        assertThat(updated.createdAt).isEqualTo(createdAt)
        assertThat(updated.updatedAt).isEqualTo(clock.now())
    }

    @Test
    fun `files are listed for the client and project they belong to`() = runTest {
        val repository = repository()
        database.insertClient(id = 7L)
        database.insertProject(id = 9L, clientId = 7L)
        val linked = repository.register(
            record(name = "Brief", path = "docs/brief.pdf").copy(clientId = 7L, projectId = 9L),
        ).value()
        repository.register(record(name = "Loose", path = "docs/loose.pdf"))

        assertThat(repository.observeForClient(7L).first().map { it.id }).containsExactly(linked.id)
        assertThat(repository.observeForProject(9L).first().map { it.id }).containsExactly(linked.id)
        assertThat(repository.observeAll().first()).hasSize(2)
    }

    @Test
    fun `deleting a client orphans its files rather than deleting them`() = runTest {
        val repository = repository()
        database.insertClient(id = 7L)
        val file = repository.register(
            record(name = "Brief", path = "docs/brief.pdf").copy(clientId = 7L),
        ).value()

        database.openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = 7")

        val loaded = repository.byId(file.id).value()
        assertThat(loaded.clientId).isNull()
        assertThat(loaded.displayName).isEqualTo("Brief")
    }

    private fun repository(): FileRepository = FileRepositoryImpl(
        dao = database.fileDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun record(
        name: String,
        path: String,
        source: FileSource = FileSource.IMPORT,
        text: String? = null,
    ) = FileRecord(
        displayName = name,
        relativePath = path,
        mimeType = "application/pdf",
        sizeBytes = 2_048L,
        source = source,
        extractedText = text,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success<T>).value
}
