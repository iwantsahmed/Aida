package com.rayluxeo.aida.data.platform

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.FileDao
import com.rayluxeo.aida.data.db.entity.FileRecordEntity
import com.rayluxeo.aida.data.db.entity.ScanRecordEntity
import com.rayluxeo.aida.domain.model.FileSource
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hilt is deliberately absent: its local-test bytecode transform does not run under AGP 9, so the
 * database is built by hand and the application is pinned to plain [Application].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FileDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: FileDao

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))
    private val now = clock.now().toEpochMilli()

    @Before
    fun openDatabase() {
        database = openTestDatabase()
        dao = database.fileDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `register returns the new row id and the file is observable`() = runTest {
        val id = dao.register(file(name = "contract"))

        assertThat(id).isGreaterThan(0L)
        assertThat(dao.observeAll().first().map { it.displayName }).containsExactly("contract")
        assertThat(dao.findById(id)?.relativePath).isEqualTo("docs/contract.pdf")
    }

    @Test
    fun `registering a path twice is refused and leaves one row`() = runTest {
        dao.register(file(name = "contract"))

        val second = dao.register(
            file(name = "contract copy", path = "docs/contract.pdf", uuid = "another-uuid"),
        )

        assertThat(second).isEqualTo(FileDao.PATH_TAKEN)
        assertThat(database.countRows("files")).isEqualTo(1)
        assertThat(dao.observeAll().first().single().displayName).isEqualTo("contract")
    }

    @Test
    fun `a soft deleted file is hidden from every read and listDeleted finds it`() = runTest {
        val id = dao.register(file(name = "receipt", clientId = null))

        assertThat(dao.softDelete(id, now)).isEqualTo(1)

        assertThat(dao.observeAll().first()).isEmpty()
        assertThat(dao.listAll(limit = 50)).isEmpty()
        assertThat(dao.search("receipt*", limit = 50)).isEmpty()
        val binned = dao.listDeleted()
        assertThat(binned.map { it.id }).containsExactly(id)
        assertThat(binned.single().deletedAtEpochMillis).isEqualTo(now)
        assertThat(database.countRows("files")).isEqualTo(1)
    }

    @Test
    fun `restoring brings a binned file back into the list`() = runTest {
        val id = dao.register(file(name = "receipt"))
        dao.softDelete(id, now)

        assertThat(dao.restore(id, now + 1)).isEqualTo(1)

        assertThat(dao.observeAll().first().map { it.id }).containsExactly(id)
        assertThat(dao.listDeleted()).isEmpty()
        assertThat(dao.findById(id)?.updatedAtEpochMillis).isEqualTo(now + 1)
    }

    @Test
    fun `soft deleting twice reports no change the second time`() = runTest {
        val id = dao.register(file(name = "receipt"))

        assertThat(dao.softDelete(id, now)).isEqualTo(1)
        assertThat(dao.softDelete(id, now + 1)).isEqualTo(0)
        assertThat(dao.findById(id)?.deletedAtEpochMillis).isEqualTo(now)
    }

    @Test
    fun `a live file cannot be purged and a binned one takes its scan with it`() = runTest {
        val id = dao.registerScan(file(name = "invoice scan", source = FileSource.SCAN)) { fileId ->
            scan(fileId, pages = 3)
        }

        assertThat(dao.purgeDeleted(id)).isEqualTo(0)
        assertThat(database.countRows("files")).isEqualTo(1)

        dao.softDelete(id, now)

        assertThat(dao.purgeDeleted(id)).isEqualTo(1)
        assertThat(database.countRows("files")).isEqualTo(0)
        assertThat(database.countRows("scan_records")).isEqualTo(0)
    }

    @Test
    fun `registerScan writes the file and its scan together`() = runTest {
        val id = dao.registerScan(file(name = "passport", source = FileSource.SCAN)) { fileId ->
            scan(fileId, pages = 2)
        }

        val stored = dao.scanForFile(id)
        assertThat(stored?.pageCount).isEqualTo(2)
        assertThat(stored?.recognisedAtEpochMillis).isNull()
        assertThat(database.countRows("files")).isEqualTo(1)
    }

    @Test
    fun `registerScan writes nothing at all when the path is already taken`() = runTest {
        dao.register(file(name = "passport"))

        val refused = dao.registerScan(
            file(name = "passport again", path = "docs/passport.pdf", uuid = "second"),
        ) { fileId -> scan(fileId, pages = 2) }

        assertThat(refused).isEqualTo(FileDao.PATH_TAKEN)
        assertThat(database.countRows("files")).isEqualTo(1)
        assertThat(database.countRows("scan_records")).isEqualTo(0)
    }

    @Test
    fun `a scan cannot point at a file that does not exist`() = runTest {
        var refused = false
        try {
            dao.insertScan(scan(fileId = 404L, pages = 1))
        } catch (expected: SQLiteConstraintException) {
            refused = true
        }

        assertThat(refused).isTrue()
        assertThat(database.countRows("scan_records")).isEqualTo(0)
    }

    @Test
    fun `deleting a client keeps its files and clears the link`() = runTest {
        database.insertClient(id = 7L)
        database.insertProject(id = 9L, clientId = 7L)
        val id = dao.register(file(name = "brief", clientId = 7L, projectId = 9L))

        database.openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = 7")

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored?.clientId).isNull()
        assertThat(stored?.projectId).isEqualTo(9L)
        assertThat(dao.observeForProject(9L).first().map { it.id }).containsExactly(id)
    }

    @Test
    fun `full text search matches the name and the extracted text`() = runTest {
        dao.register(file(name = "rent receipt", text = "Paid to Kamal for March"))
        dao.register(file(name = "invoice 004", text = "Rayluxeo web design"))

        assertThat(dao.search("kamal*", limit = 50).map { it.displayName })
            .containsExactly("rent receipt")
        assertThat(dao.search("invoice*", limit = 50).map { it.displayName })
            .containsExactly("invoice 004")
        assertThat(dao.search("kettle*", limit = 50)).isEmpty()
    }

    @Test
    fun `full text search follows a file through an update`() = runTest {
        val id = dao.register(file(name = "letter"))

        val stored = dao.findById(id)!!
        dao.updateMetadata(stored.copy(displayName = "notice"))

        assertThat(dao.search("notice*", limit = 50).map { it.id }).containsExactly(id)
        assertThat(dao.search("letter*", limit = 50)).isEmpty()
    }

    @Test
    fun `updateMetadata refuses a path that belongs to another file`() = runTest {
        dao.register(file(name = "first"))
        val secondId = dao.register(file(name = "second"))
        val second = dao.findById(secondId)!!

        val refused = dao.updateMetadata(second.copy(relativePath = "docs/first.pdf"))

        assertThat(refused).isEqualTo(FileDao.PATH_TAKEN)
        assertThat(dao.findById(secondId)?.relativePath).isEqualTo("docs/second.pdf")
    }

    @Test
    fun `updateMetadata keeps the uuid, the creation time and the bin state`() = runTest {
        val id = dao.register(file(name = "deed"))
        dao.softDelete(id, now)
        val stored = dao.findById(id)!!

        dao.updateMetadata(
            stored.copy(
                displayName = "deed of sale",
                uuid = "rewritten",
                createdAtEpochMillis = 0L,
                deletedAtEpochMillis = null,
                updatedAtEpochMillis = now + 5,
            ),
        )

        val after = dao.findById(id)!!
        assertThat(after.displayName).isEqualTo("deed of sale")
        assertThat(after.uuid).isEqualTo(stored.uuid)
        assertThat(after.createdAtEpochMillis).isEqualTo(stored.createdAtEpochMillis)
        assertThat(after.deletedAtEpochMillis).isEqualTo(now)
    }

    @Test
    fun `recognised text lands on the file and stamps the scan in one write`() = runTest {
        val id = dao.registerScan(file(name = "bill", source = FileSource.SCAN)) { fileId ->
            scan(fileId, pages = 1)
        }

        val changed = dao.recordRecognisedText(id, text = "Total 1234.56 BDT", atEpochMillis = now + 2)

        assertThat(changed).isEqualTo(1)
        assertThat(dao.findById(id)?.extractedText).isEqualTo("Total 1234.56 BDT")
        assertThat(dao.scanForFile(id)?.recognisedAtEpochMillis).isEqualTo(now + 2)
        assertThat(dao.search("1234*", limit = 50).map { it.id }).containsExactly(id)
    }

    @Test
    fun `recognised text leaves a binned file alone`() = runTest {
        val id = dao.registerScan(file(name = "bill", source = FileSource.SCAN)) { fileId ->
            scan(fileId, pages = 1)
        }
        dao.softDelete(id, now)

        val changed = dao.recordRecognisedText(id, text = "Total 1234.56 BDT", atEpochMillis = now + 2)

        assertThat(changed).isEqualTo(0)
        assertThat(dao.findById(id)?.extractedText).isNull()
        assertThat(dao.scanForFile(id)?.recognisedAtEpochMillis).isNull()
    }

    @Test
    fun `files are listed by source`() = runTest {
        dao.register(file(name = "scanned", source = FileSource.SCAN))
        dao.register(file(name = "imported", source = FileSource.IMPORT))

        assertThat(dao.observeBySource(FileSource.SCAN).first().map { it.displayName })
            .containsExactly("scanned")
    }

    private fun scan(fileId: Long, pages: Int) = ScanRecordEntity(
        fileId = fileId,
        pageCount = pages,
        recognisedAtEpochMillis = null,
        createdAtEpochMillis = now,
    )

    private fun file(
        name: String,
        path: String = "docs/${name.replace(' ', '-')}.pdf",
        uuid: String = "uuid-$path",
        source: FileSource = FileSource.IMPORT,
        text: String? = null,
        clientId: Long? = null,
        projectId: Long? = null,
    ) = FileRecordEntity(
        uuid = uuid,
        displayName = name,
        relativePath = path,
        mimeType = "application/pdf",
        sizeBytes = 2_048L,
        source = source,
        extractedText = text,
        clientId = clientId,
        projectId = projectId,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )
}
