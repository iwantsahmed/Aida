package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationRun
import com.rayluxeo.aida.domain.repository.AutomationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRunHistory @Inject constructor(
    private val automationRepository: AutomationRepository,
) {
    fun observeHistory(automationId: Long): Flow<List<AutomationRun>> =
        automationRepository.observeRuns(automationId)

    suspend fun markInFlightRunsAsFailed(): AppResult<Int> {
        val inFlight = (automationRepository.inFlightRuns() as? AppResult.Success)?.value ?: return AppResult.Success(0)
        var count = 0
        for (run in inFlight) {
            automationRepository.finishRun(run.id, AutomationOutcome.FAILED, detail = "Process terminated mid-flight")
            count++
        }
        return AppResult.Success(count)
    }
}
