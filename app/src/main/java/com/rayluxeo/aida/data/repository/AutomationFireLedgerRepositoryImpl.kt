package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.data.db.dao.AutomationFireDao
import com.rayluxeo.aida.data.db.entity.AutomationFireEntity
import com.rayluxeo.aida.domain.automation.RecordTriggerSource
import com.rayluxeo.aida.domain.model.FiredOccurrence
import com.rayluxeo.aida.domain.repository.AutomationFireLedgerRepository
import java.time.Instant
import javax.inject.Inject

/**
 * Room-backed fire ledger. Every call funnels through [storageCall], so a SQLite throw surfaces as
 * a STORAGE failure value rather than an exception — the use case then leaves the automation
 * un-fired this pass rather than crashing the worker.
 */
class AutomationFireLedgerRepositoryImpl @Inject constructor(
    private val dao: AutomationFireDao,
    private val dispatchers: DispatcherProvider,
) : AutomationFireLedgerRepository {

    override suspend fun firedFor(automationId: Long): AppResult<List<FiredOccurrence>> =
        storageCall(dispatchers, "load fire ledger for automation $automationId") {
            dao.selectForAutomation(automationId).map { it.toDomain() }
        }

    override suspend fun recordFired(entries: List<FiredOccurrence>): AppResult<Unit> =
        storageCall(dispatchers, "record ${entries.size} automation fires") {
            dao.insertAll(entries.map { it.toEntity() })
        }

    override suspend fun pruneFiredBefore(instant: Instant): AppResult<Int> =
        storageCall(dispatchers, "prune automation fire ledger") {
            dao.deleteFiredBefore(instant.toEpochMilli())
        }

    private fun AutomationFireEntity.toDomain(): FiredOccurrence = FiredOccurrence(
        automationId = automationId,
        source = RecordTriggerSource.valueOf(source),
        recordId = recordId,
        occurrenceFrom = Instant.ofEpochMilli(occurrenceFromEpochMillis),
        firedAt = Instant.ofEpochMilli(firedAtEpochMillis),
    )

    private fun FiredOccurrence.toEntity(): AutomationFireEntity = AutomationFireEntity(
        automationId = automationId,
        source = source.name,
        recordId = recordId,
        occurrenceFromEpochMillis = occurrenceFrom.toEpochMilli(),
        firedAtEpochMillis = firedAt.toEpochMilli(),
    )
}
