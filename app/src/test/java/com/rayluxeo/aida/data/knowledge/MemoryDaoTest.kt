package com.rayluxeo.aida.data.knowledge

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.MemoryDao
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
class MemoryDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: MemoryDao

    @Before
    fun openDatabase() {
        database = openKnowledgeDatabase()
        dao = database.memoryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `insert returns the new row id and the fact is observable`() = runTest {
        val id = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold over silver"))

        assertThat(id).isGreaterThan(0L)
        assertThat(dao.observeAll().first().single().fact).isEqualTo("prefers gold over silver")
    }

    @Test
    fun `facts are grouped by what they are about`() = runTest {
        dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold"))
        dao.insert(memoryEntity(subject = "Rahman", fact = "pays on the fifth"))
        dao.insert(memoryEntity(subject = "Nabila", fact = "reviews on Thursdays"))

        assertThat(dao.observeForSubject("Rahman").first()).hasSize(2)
        assertThat(dao.observeForSubject("Nabila").first().single().fact).isEqualTo("reviews on Thursdays")
    }

    @Test
    fun `forgetting hides the fact from every read but leaves it readable by id`() = runTest {
        val id = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold over silver"))

        assertThat(dao.softDelete(id, SEED_MILLIS + 1_000)).isEqualTo(1)

        assertThat(dao.observeAll().first()).isEmpty()
        assertThat(dao.listAll(limit = 10)).isEmpty()
        assertThat(dao.search("gold*", limit = 10)).isEmpty()
        assertThat(dao.findById(id)?.deletedAtEpochMillis).isEqualTo(SEED_MILLIS + 1_000)
    }

    @Test
    fun `restoring brings the fact back into search`() = runTest {
        val id = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold over silver"))
        dao.softDelete(id, SEED_MILLIS + 1_000)

        assertThat(dao.restore(id, SEED_MILLIS + 2_000)).isEqualTo(1)

        assertThat(dao.search("gold*", limit = 10).map { it.id }).containsExactly(id)
    }

    @Test
    fun `full text search matches the subject as well as the wording`() = runTest {
        val id = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold over silver"))
        dao.insert(memoryEntity(subject = "Nabila", fact = "reviews on Thursdays"))

        assertThat(dao.search("rahman*", limit = 10).map { it.id }).containsExactly(id)
        assertThat(dao.search("silver*", limit = 10).map { it.id }).containsExactly(id)
        assertThat(dao.search("kettle*", limit = 10)).isEmpty()
    }

    @Test
    fun `search ranks the facts Mira has leaned on first`() = runTest {
        dao.insert(memoryEntity(subject = "Rahman", fact = "gold, once", recallCount = 1))
        dao.insert(memoryEntity(subject = "Rahman", fact = "gold, often", recallCount = 9))

        assertThat(dao.search("gold*", limit = 10).map { it.fact })
            .containsExactly("gold, often", "gold, once").inOrder()
    }

    @Test
    fun `search honours its limit`() = runTest {
        repeat(5) { index -> dao.insert(memoryEntity(subject = "Rahman", fact = "gold note $index")) }

        assertThat(dao.search("gold*", limit = 2)).hasSize(2)
        assertThat(dao.listAll(limit = 3)).hasSize(3)
    }

    @Test
    fun `two recalls in a row add up to two`() = runTest {
        val id = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold"))

        dao.recordRecall(id, SEED_MILLIS + 1_000)
        dao.recordRecall(id, SEED_MILLIS + 2_000)

        val stored = dao.findById(id)!!
        assertThat(stored.recallCount).isEqualTo(2)
        assertThat(stored.lastRecalledAtEpochMillis).isEqualTo(SEED_MILLIS + 2_000)
    }

    @Test
    fun `a recall is not an edit and leaves the update time alone`() = runTest {
        val id = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold"))

        dao.recordRecall(id, SEED_MILLIS + 1_000)

        assertThat(dao.findById(id)?.updatedAtEpochMillis).isEqualTo(SEED_MILLIS)
    }

    @Test
    fun `a forgotten fact is never recalled`() = runTest {
        val id = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold"))
        dao.softDelete(id, SEED_MILLIS + 1_000)

        assertThat(dao.recordRecall(id, SEED_MILLIS + 2_000)).isEqualTo(0)
        assertThat(dao.findById(id)?.recallCount).isEqualTo(0)
    }

    @Test
    fun `recall returns the counts it has just written, not the ones it read`() = runTest {
        val first = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold", recallCount = 4))
        val second = dao.insert(memoryEntity(subject = "Rahman", fact = "pays in gold", recallCount = 0))

        val recalled = dao.searchAndRecordRecall("gold*", limit = 10, atEpochMillis = SEED_MILLIS + 1_000)

        assertThat(recalled.map { it.id }).containsExactly(first, second).inOrder()
        assertThat(recalled.map { it.recallCount }).containsExactly(5, 1).inOrder()
        assertThat(recalled.map { it.lastRecalledAtEpochMillis })
            .containsExactly(SEED_MILLIS + 1_000, SEED_MILLIS + 1_000)
        assertThat(dao.findById(first)?.recallCount).isEqualTo(5)
    }

    @Test
    fun `a recall that matches nothing writes nothing`() = runTest {
        val id = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold"))

        assertThat(dao.searchAndRecordRecall("kettle*", limit = 10, atEpochMillis = SEED_MILLIS + 1_000))
            .isEmpty()
        assertThat(dao.findById(id)?.recallCount).isEqualTo(0)
    }

    @Test
    fun `deleting a client orphans the facts about it rather than forgetting them`() = runTest {
        val clientId = database.seedClient()
        val id = dao.insert(memoryEntity(subject = "Rahman", fact = "prefers gold", clientId = clientId))

        database.openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = ?", arrayOf<Any>(clientId))

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored?.clientId).isNull()
        assertThat(stored?.fact).isEqualTo("prefers gold")
    }
}
