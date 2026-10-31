package com.rayluxeo.aida.domain.memory

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.model.MemorySource
import com.rayluxeo.aida.domain.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val clock: MiraClock,
) {
    suspend fun remember(
        subject: String,
        fact: String,
        source: MemorySource = MemorySource.INFERRED,
        category: MemoryCategory? = null,
        clientId: Long? = null,
        projectId: Long? = null,
    ): AppResult<MemoryFact> {
        val trimmedSubject = subject.trim()
        val trimmedFact = fact.trim()

        if (trimmedSubject.isBlank()) {
            return failure(MiraErrorCode.VALIDATION, "Memory subject cannot be blank")
        }
        if (trimmedFact.isBlank()) {
            return failure(MiraErrorCode.VALIDATION, "Memory fact cannot be blank")
        }

        val inferredCategory = category ?: MemoryConflictResolver.inferCategory(trimmedSubject, trimmedFact)
        val now = clock.now()

        // Check for conflicting facts for same subject
        val existingSubjectFacts = (memoryRepository.search(trimmedSubject) as? AppResult.Success)?.value ?: emptyList()
        val conflict = MemoryConflictResolver.findConflictingFact(
            MemoryFact(subject = trimmedSubject, fact = trimmedFact, createdAt = now, updatedAt = now),
            existingSubjectFacts,
        )

        val memoryFact = MemoryFact(
            id = conflict?.id ?: 0L,
            subject = trimmedSubject,
            fact = trimmedFact,
            category = inferredCategory,
            source = source,
            clientId = clientId,
            projectId = projectId,
            createdAt = conflict?.createdAt ?: now,
            updatedAt = now,
        )

        return if (conflict != null) {
            memoryRepository.update(memoryFact)
        } else {
            memoryRepository.remember(memoryFact)
        }
    }

    suspend fun search(query: String?, categoryFilter: MemoryCategory? = null): AppResult<List<MemoryFact>> {
        val result = memoryRepository.search(query)
        if (result !is AppResult.Success) return result

        val facts = result.value
        val filtered = if (categoryFilter != null) {
            facts.filter { it.category == categoryFilter }
        } else {
            facts
        }

        val sorted = filtered.sortedWith(
            compareByDescending<MemoryFact> { it.isPinned }
                .thenByDescending { it.recallCount }
                .thenByDescending { it.updatedAt }
        )

        return AppResult.Success(sorted)
    }

    suspend fun forget(id: Long): AppResult<Unit> = memoryRepository.forget(id)

    suspend fun togglePin(id: Long, isPinned: Boolean): AppResult<MemoryFact> =
        memoryRepository.setPinned(id, isPinned)

    suspend fun updateCategory(id: Long, category: MemoryCategory): AppResult<MemoryFact> =
        memoryRepository.updateCategory(id, category)
}
