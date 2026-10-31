package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Budget
import com.rayluxeo.aida.domain.model.BudgetProgress
import kotlinx.coroutines.flow.Flow

/**
 * The ceilings Abu set, and what has been spent against them.
 *
 * Progress is a one-shot read rather than a Flow: it is a rollup over the whole expense table
 * for a window, and re-running it on every expense row that changes would cost more than it is
 * worth on a phone. A screen showing it refreshes when Abu looks at it.
 */
interface BudgetRepository {

    fun observeAll(): Flow<List<Budget>>

    /** Only the budgets whose period covers today. Both ends of a period are inclusive. */
    fun observeActive(): Flow<List<Budget>>
    /**
     * Reads a budget **whatever its state, including one in the bin**.
     *
     * That is deliberate: restoring a binned record has to be able to read it first. It does mean
     * a caller showing the result must check `deletedAt` — every listing, search and recall path
     * already filters, so this is the one accessor where a binned row can come back.
     */
    suspend fun byId(id: Long): AppResult<Budget>

    suspend fun create(budget: Budget): AppResult<Budget>

    suspend fun update(budget: Budget): AppResult<Budget>

    /**
     * What has been spent inside the budget's own window and category, summed over whole minor
     * units of the budget's currency. Binned expenses are excluded, and an expense in another
     * currency is left out rather than converted — Mira holds no exchange rates.
     */
    suspend fun progress(budgetId: Long): AppResult<BudgetProgress>

    suspend fun activeProgress(): AppResult<List<BudgetProgress>>

    suspend fun delete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Budget>
}
