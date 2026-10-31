package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.AutomationDao
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationRun
import com.rayluxeo.aida.domain.repository.AutomationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Automations, and the only place their stored JSON becomes something typed.
 *
 * The list flows drop a row whose configuration will not parse, while [byId] reports it as
 * VALIDATION. That split is deliberate: a screen listing automations must survive one bad row,
 * but anything about to act on a specific automation has to be told why it cannot.
 */
@Singleton
class AutomationRepositoryImpl @Inject constructor(
    private val dao: AutomationDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : AutomationRepository {

    override fun observeAll(): Flow<List<Automation>> =
        dao.observeAll()
            .map { rows -> rows.mapNotNull { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeEnabled(): Flow<List<Automation>> =
        dao.observeEnabled()
            .map { rows -> rows.mapNotNull { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeRuns(automationId: Long): Flow<List<AutomationRun>> =
        dao.observeRuns(automationId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Automation> {
        val found = storageCall(dispatchers, "load automation $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> {
                val entity = found.value ?: return failure(MiraErrorCode.NOT_FOUND, "No automation with id $id")
                entity.toDomain()?.let { AppResult.Success(it) }
                    ?: failure(MiraErrorCode.VALIDATION, "Automation $id configuration is invalid")
            }
        }
    }

    override suspend fun create(automation: Automation): AppResult<Automation> =
        storageCall(dispatchers, "create automation") {
            val id = dao.insert(automation.copy(id = 0L).toEntity())
            automation.copy(id = id)
        }

    override suspend fun update(automation: Automation): AppResult<Automation> {
        val updated = storageCall(dispatchers, "update automation ${automation.id}") {
            dao.update(automation.copy(updatedAt = clock.now()).toEntity())
        }
        return when (updated) {
            is AppResult.Failure -> updated
            is AppResult.Success ->
                if (updated.value > 0) byId(automation.id)
                else failure(MiraErrorCode.NOT_FOUND, "No automation with id ${automation.id}")
        }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean): AppResult<Automation> {
        val changed = storageCall(dispatchers, "set automation $id enabled=$enabled") {
            dao.setEnabled(id, enabled, clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No automation with id $id")
        }
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "delete automation $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No automation with id $id")
        }
    }

    /**
     * The automation is read first so a run can never be opened against one that is in the bin, or
     * one whose configuration cannot be read and therefore cannot be carried out either.
     */
    override suspend fun startRun(automationId: Long): AppResult<AutomationRun> {
        val automation = byId(automationId)
        if (automation is AppResult.Failure) return automation
        return storageCall(dispatchers, "start run for automation $automationId") {
            val run = AutomationRun(automationId = automationId, startedAt = clock.now())
            run.copy(id = dao.startRun(run.toEntity()))
        }
    }

    override suspend fun finishRun(
        runId: Long,
        outcome: AutomationOutcome,
        detail: String?,
    ): AppResult<AutomationRun> {
        val changed = storageCall(dispatchers, "finish automation run $runId") {
            dao.finishRun(
                id = runId,
                finishedAt = clock.now().toEpochMilli(),
                outcome = outcome,
                detail = detail,
            )
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success -> if (changed.value > 0) runById(runId) else refuseSecondFinish(runId)
        }
    }

    override suspend fun inFlightRuns(): AppResult<List<AutomationRun>> =
        storageCall(dispatchers, "list in-flight automation runs") {
            dao.listInFlight().map { it.toDomain() }
        }

    private suspend fun runById(id: Long): AppResult<AutomationRun> {
        val found = storageCall(dispatchers, "load automation run $id") { dao.runById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No automation run with id $id")
        }
    }

    /** Nothing changed, so either the run was never there or it was already closed once. */
    private suspend fun refuseSecondFinish(id: Long): AppResult<AutomationRun> {
        val found = storageCall(dispatchers, "load automation run $id") { dao.runById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success ->
                if (found.value == null) failure(MiraErrorCode.NOT_FOUND, "No automation run with id $id")
                else failure(MiraErrorCode.VALIDATION, "Automation run $id is already finished")
        }
    }
}
