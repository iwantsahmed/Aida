package com.rayluxeo.aida.domain.memory

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MemoryManagerTest {

    private class FakeMemoryRepository : MemoryRepository {
        private val facts = mutableListOf<MemoryFact>()
        private var idCounter = 1L

        override fun observe(): Flow<List<MemoryFact>> = flowOf(facts)

        override fun observeByCategory(category: MemoryCategory): Flow<List<MemoryFact>> =
            flowOf(facts.filter { it.category == category })

        override fun observeForSubject(subject: String): Flow<List<MemoryFact>> =
            flowOf(facts.filter { it.subject.equals(subject, ignoreCase = true) })

        override suspend fun byId(id: Long): AppResult<MemoryFact> {
            val fact = facts.firstOrNull { it.id == id } ?: return AppResult.Failure(com.rayluxeo.aida.core.common.MiraError(com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND))
            return fact.asSuccess()
        }

        override suspend fun search(query: String?, limit: Int): AppResult<List<MemoryFact>> {
            if (query.isNullOrBlank()) return facts.asSuccess()
            return facts.filter { it.subject.contains(query, ignoreCase = true) || it.fact.contains(query, ignoreCase = true) }.asSuccess()
        }

        override suspend fun recall(query: String, limit: Int): AppResult<List<MemoryFact>> = search(query, limit)

        override suspend fun remember(fact: MemoryFact): AppResult<MemoryFact> {
            val stored = fact.copy(id = idCounter++)
            facts.add(stored)
            return stored.asSuccess()
        }

        override suspend fun update(fact: MemoryFact): AppResult<MemoryFact> {
            val index = facts.indexOfFirst { it.id == fact.id }
            if (index >= 0) facts[index] = fact
            return fact.asSuccess()
        }

        override suspend fun setPinned(id: Long, isPinned: Boolean): AppResult<MemoryFact> {
            val index = facts.indexOfFirst { it.id == id }
            val updated = facts[index].copy(isPinned = isPinned)
            facts[index] = updated
            return updated.asSuccess()
        }

        override suspend fun updateCategory(id: Long, category: MemoryCategory): AppResult<MemoryFact> {
            val index = facts.indexOfFirst { it.id == id }
            val updated = facts[index].copy(category = category)
            facts[index] = updated
            return updated.asSuccess()
        }

        override suspend fun forget(id: Long): AppResult<Unit> {
            facts.removeIf { it.id == id }
            return Unit.asSuccess()
        }

        override suspend fun restore(id: Long): AppResult<Unit> = Unit.asSuccess()
    }

    private class FakeClock : MiraClock {
        override fun now(): Instant = Instant.ofEpochMilli(1700000000000L)
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    @Test
    fun `remember fact infers category and stores in repository`() = runTest {
        val repo = FakeMemoryRepository()
        val manager = MemoryManager(repo, FakeClock())

        val result = manager.remember("Ahmed", "Ahmed prefers WhatsApp")
        assertThat(result).isInstanceOf(AppResult.Success::class.java)

        val stored = (result as AppResult.Success).value
        assertThat(stored.subject).isEqualTo("Ahmed")
        assertThat(stored.category).isEqualTo(MemoryCategory.PERSONAL_PREFERENCE)
    }

    @Test
    fun `remember fact resolves conflict when same subject exists`() = runTest {
        val repo = FakeMemoryRepository()
        val manager = MemoryManager(repo, FakeClock())

        manager.remember("Ahmed", "Ahmed prefers Email")
        val updatedResult = manager.remember("Ahmed", "Ahmed prefers WhatsApp")

        val stored = (updatedResult as AppResult.Success).value
        assertThat(stored.id).isEqualTo(1L)
        assertThat(stored.fact).isEqualTo("Ahmed prefers WhatsApp")
    }
}
