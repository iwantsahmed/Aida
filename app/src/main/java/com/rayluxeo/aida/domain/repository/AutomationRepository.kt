package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationRun
import kotlinx.coroutines.flow.Flow

/**
 * Automations and the history of what they did.
 *
 * A trigger's settings are stored as JSON, which the database cannot check, so this port is the
 * boundary where that text becomes a value or becomes a failure. An automation whose configuration
 * will not parse is reported as a VALIDATION failure by [byId] and by everything that has to read
 * it before acting; the list flows leave it out instead, because one unreadable row must not empty
 * a screen.
 */
interface AutomationRepository {

    /** Every live automation, enabled or not, ordered by name. */
    fun observeAll(): Flow<List<Automation>>

    /** The ones that can currently fire. */
    fun observeEnabled(): Flow<List<Automation>>

    /** One automation's run history, newest first. A binned automation observes as empty. */
    fun observeRuns(automationId: Long): Flow<List<AutomationRun>>

    suspend fun byId(id: Long): AppResult<Automation>

    suspend fun create(automation: Automation): AppResult<Automation>

    suspend fun update(automation: Automation): AppResult<Automation>

    suspend fun setEnabled(id: Long, enabled: Boolean): AppResult<Automation>

    /** Soft: the run history stays on disk with it. */
    suspend fun delete(id: Long): AppResult<Unit>

    /**
     * Opens a run and stamps the automation's last-run time in one transaction, so the history and
     * the time the list shows can never disagree about whether it ran.
     *
     * Refused for an automation that is binned, missing, or whose configuration cannot be read —
     * none of those can actually be carried out.
     */
    suspend fun startRun(automationId: Long): AppResult<AutomationRun>

    /** Closes a run exactly once. Closing one that is already closed is a VALIDATION failure. */
    suspend fun finishRun(
        runId: Long,
        outcome: AutomationOutcome,
        detail: String? = null,
    ): AppResult<AutomationRun>

    /**
     * Runs still open, oldest first — including the ones abandoned when the process died, which is
     * what a caller sweeps on the next start.
     */
    suspend fun inFlightRuns(): AppResult<List<AutomationRun>>
}
