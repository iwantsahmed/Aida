package com.rayluxeo.aida.domain.automation

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationRun
import com.rayluxeo.aida.domain.repository.AutomationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class AutomationRunHistoryTest {

    private class FakeAutomationRepo : AutomationRepository {
        val runs = mutableListOf<AutomationRun>()

        override fun observeAll(): Flow<List<Automation>> = error("unused")
        override fun observeEnabled(): Flow<List<Automation>> = error("unused")
        override fun observeRuns(automationId: Long): Flow<List<AutomationRun>> =
            flowOf(runs.filter { it.automationId == automationId })

        override suspend fun byId(id: Long) = error("unused")
        override suspend fun create(automation: Automation) = error("unused")
        override suspend fun update(automation: Automation) = error("unused")
        override suspend fun setEnabled(id: Long, enabled: Boolean) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override suspend fun startRun(automationId: Long) = error("unused")

        override suspend fun finishRun(runId: Long, outcome: AutomationOutcome, detail: String?): AppResult<AutomationRun> {
            val idx = runs.indexOfFirst { it.id == runId }
            val updated = runs[idx].copy(finishedAt = Instant.now(), outcome = outcome, detail = detail)
            runs[idx] = updated
            return updated.asSuccess()
        }

        override suspend fun inFlightRuns(): AppResult<List<AutomationRun>> =
            runs.filter { it.isInFlight }.asSuccess()
    }

    @Test
    fun `observeHistory yields runs for given automation id`() = runTest {
        val repo = FakeAutomationRepo()
        val now = Instant.now()
        repo.runs.add(AutomationRun(id = 1, automationId = 10, startedAt = now))
        repo.runs.add(AutomationRun(id = 2, automationId = 20, startedAt = now))

        val history = AutomationRunHistory(repo)
        val result = history.observeHistory(10).first()

        assertThat(result).hasSize(1)
        assertThat(result.first().automationId).isEqualTo(10)
    }

    @Test
    fun `markInFlightRunsAsFailed closes open in-flight runs`() = runTest {
        val repo = FakeAutomationRepo()
        val now = Instant.now()
        repo.runs.add(AutomationRun(id = 1, automationId = 10, startedAt = now, finishedAt = null))

        val history = AutomationRunHistory(repo)
        val countRes = history.markInFlightRunsAsFailed()

        assertThat((countRes as AppResult.Success).value).isEqualTo(1)
        assertThat(repo.runs.first().outcome).isEqualTo(AutomationOutcome.FAILED)
        assertThat(repo.runs.first().finishedAt).isNotNull()
    }
}
