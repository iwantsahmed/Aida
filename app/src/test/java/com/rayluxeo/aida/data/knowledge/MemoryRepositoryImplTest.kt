package com.rayluxeo.aida.data.knowledge

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.MemoryRepositoryImpl
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.model.MemorySource
import com.rayluxeo.aida.domain.repository.MemoryRepository
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
class MemoryRepositoryImplTest {

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
    fun `a remembered fact comes back with its id and its source`() = runTest {
        val repository = repository()

        val stored = repository.remember(
            fact(subject = "Rahman", fact = "prefers gold over silver", source = MemorySource.SPOKEN),
        ).expectSuccess()

        assertThat(stored.id).isGreaterThan(0L)
        val loaded = repository.byId(stored.id).expectSuccess()
        assertThat(loaded.subject).isEqualTo("Rahman")
        assertThat(loaded.fact).isEqualTo("prefers gold over silver")
        assertThat(loaded.source).isEqualTo(MemorySource.SPOKEN)
    }

    @Test
    fun `a fact Mira has just been told has never been recalled`() = runTest {
        val repository = repository()

        val stored = repository.remember(
            fact(subject = "Rahman", fact = "prefers gold")
                .copy(recallCount = 12, lastRecalledAt = Instant.ofEpochMilli(SEED_MILLIS)),
        ).expectSuccess()

        assertThat(stored.recallCount).isEqualTo(0)
        assertThat(stored.lastRecalledAt).isNull()
        assertThat(repository.byId(stored.id).expectSuccess().recallCount).isEqualTo(0)
    }

    @Test
    fun `recall counts the use and search does not`() = runTest {
        val repository = repository()
        val id = repository.remember(fact(subject = "Rahman", fact = "prefers gold")).expectSuccess().id

        assertThat(repository.search("gold").expectSuccess().map { it.id }).containsExactly(id)
        assertThat(repository.byId(id).expectSuccess().recallCount).isEqualTo(0)

        val recalled = repository.recall("gold").expectSuccess()

        assertThat(recalled.map { it.id }).containsExactly(id)
        assertThat(recalled.single().recallCount).isEqualTo(1)
        assertThat(recalled.single().lastRecalledAt).isEqualTo(clock.now())
        assertThat(repository.byId(id).expectSuccess().recallCount).isEqualTo(1)
    }

    @Test
    fun `two recalls of the same fact add up`() = runTest {
        val repository = repository()
        val id = repository.remember(fact(subject = "Rahman", fact = "prefers gold")).expectSuccess().id

        repository.recall("gold")
        repository.recall("gold")

        assertThat(repository.byId(id).expectSuccess().recallCount).isEqualTo(2)
    }

    @Test
    fun `a query with nothing searchable in it recalls nothing and counts nothing`() = runTest {
        val repository = repository()
        val id = repository.remember(fact(subject = "Rahman", fact = "prefers gold")).expectSuccess().id

        assertThat(repository.recall("  ...  ").expectSuccess()).isEmpty()
        assertThat(repository.byId(id).expectSuccess().recallCount).isEqualTo(0)
    }

    @Test
    fun `forgetting hides the fact from every read and from recall, and restore undoes it`() = runTest {
        val repository = repository()
        val id = repository.remember(fact(subject = "Rahman", fact = "prefers gold")).expectSuccess().id

        assertThat(repository.forget(id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.observe().first()).isEmpty()
        assertThat(repository.search(null).expectSuccess()).isEmpty()
        assertThat(repository.search("gold").expectSuccess()).isEmpty()
        assertThat(repository.recall("gold").expectSuccess()).isEmpty()
        assertThat(repository.byId(id).expectSuccess().deletedAt).isEqualTo(clock.now())

        assertThat(repository.restore(id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.search("gold").expectSuccess().map { it.id }).containsExactly(id)
    }

    @Test
    fun `forgetting a fact twice reports NOT_FOUND`() = runTest {
        val repository = repository()
        val id = repository.remember(fact(subject = "Rahman", fact = "prefers gold")).expectSuccess().id
        repository.forget(id)

        assertThat(repository.forget(id).expectFailure()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `editing a fact keeps the recall history it has earned`() = runTest {
        val repository = repository()
        val stored = repository.remember(fact(subject = "Rahman", fact = "prefers gold")).expectSuccess()
        repository.recall("gold")
        val recalled = repository.byId(stored.id).expectSuccess()
        clock.moveTo(LocalDateTime.of(2026, 3, 6, 9, 30))

        val updated = repository.update(recalled.copy(fact = "prefers gold, not silver")).expectSuccess()

        assertThat(updated.fact).isEqualTo("prefers gold, not silver")
        assertThat(updated.recallCount).isEqualTo(1)
        assertThat(updated.lastRecalledAt).isEqualTo(recalled.lastRecalledAt)
        assertThat(updated.updatedAt).isEqualTo(clock.now())
    }

    @Test
    fun `facts are readable by subject and in Bengali`() = runTest {
        val repository = repository()
        repository.remember(
            fact(subject = "Rahman", fact = "sonar logo pochondo", language = SpokenLanguage.BENGALI),
        )
        repository.remember(fact(subject = "Nabila", fact = "reviews on Thursdays"))

        val forRahman = repository.observeForSubject("Rahman").first()
        assertThat(forRahman).hasSize(1)
        assertThat(forRahman.single().language).isEqualTo(SpokenLanguage.BENGALI)
    }

    @Test
    fun `recall never returns more than it was asked for`() = runTest {
        val repository = repository()
        repeat(5) { index ->
            repository.remember(fact(subject = "Rahman", fact = "gold detail $index"))
        }

        assertThat(repository.recall("gold", limit = 2).expectSuccess()).hasSize(2)
        assertThat(repository.search("gold", limit = 3).expectSuccess()).hasSize(3)
    }

    private fun repository(): MemoryRepository = MemoryRepositoryImpl(
        dao = database.memoryDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun fact(
        subject: String,
        fact: String,
        source: MemorySource = MemorySource.SPOKEN,
        language: SpokenLanguage = SpokenLanguage.ENGLISH,
    ) = MemoryFact(
        subject = subject,
        fact = fact,
        source = source,
        language = language,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )
}
