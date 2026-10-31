package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.memory.MemoryManager
import com.rayluxeo.aida.domain.repository.MemoryRepository
import javax.inject.Inject

/**
 * Forgetting a stored fact Abu names in words, the spoken counterpart to forgetting by id.
 *
 * It resolves first and forgets second — the same shape as [CompleteTaskUseCase] — so an ambiguous
 * "forget that" asks which fact rather than guessing, and a single match forgets exactly one. The
 * forget is soft and reversible ([MemoryManager.forget] → [MemoryRepository.restore]), which is why
 * this needs no force-confirmation beyond the pipeline's normal gate.
 */
class ForgetFactUseCase @Inject constructor(
    private val memory: MemoryRepository,
    private val manager: MemoryManager,
) {

    sealed interface Resolution {
        data class Exact(val subject: String) : Resolution
        data class Ambiguous(val subjects: List<String>) : Resolution
        data object None : Resolution
    }

    suspend operator fun invoke(query: String): AppResult<Resolution> =
        memory.search(query).flatMap { matches ->
            when {
                matches.isEmpty() -> AppResult.Success(Resolution.None)
                matches.size == 1 ->
                    manager.forget(matches.first().id).map { Resolution.Exact(matches.first().subject) }
                else -> {
                    val needle = query.trim().lowercase()
                    val exact = matches.filter { it.subject.trim().lowercase() == needle }
                    if (exact.size == 1) {
                        manager.forget(exact.first().id).map { Resolution.Exact(exact.first().subject) }
                    } else {
                        AppResult.Success(Resolution.Ambiguous(matches.take(5).map { it.subject }))
                    }
                }
            }
        }
}
