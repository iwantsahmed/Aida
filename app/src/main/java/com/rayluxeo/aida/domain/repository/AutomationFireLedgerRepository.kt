package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.FiredOccurrence
import java.time.Instant

/**
 * The dedup ledger behind record-driven automations: what each automation has already fired for, so
 * a rule does not fire again for the same record occurrence.
 *
 * Internal bookkeeping, not an Abu-facing list — there is no observe/soft-delete here, by design,
 * the same way [com.rayluxeo.aida.domain.model.AutomationRun] history is append-only. A row's life is
 * tied to its automation: the CASCADE from `automations` removes it when the rule is binned.
 */
interface AutomationFireLedgerRepository {

    /** Everything [automationId] has already fired for, so the use case can diff the current set against it. */
    suspend fun firedFor(automationId: Long): AppResult<List<FiredOccurrence>>

    /**
     * Records the occurrences an automation has now fired for. Idempotent: a batch that repeats a
     * row already present is ignored on the unique index rather than failing, so a worker retry
     * cannot double-write.
     */
    suspend fun recordFired(entries: List<FiredOccurrence>): AppResult<Unit>

    /** Drops ledger rows older than [instant], so the table does not grow without bound. */
    suspend fun pruneFiredBefore(instant: Instant): AppResult<Int>
}
