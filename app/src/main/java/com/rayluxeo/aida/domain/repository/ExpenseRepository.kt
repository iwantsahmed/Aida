package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import java.time.Instant
import java.util.Currency
import kotlinx.coroutines.flow.Flow

/**
 * Money going out.
 *
 * Nothing here ever returns a binned expense. [delete] is soft and [restore] undoes it, so an
 * undo brings back the same row — a re-inserted copy would take a new id and the receipt file
 * pointing at the old one would stop resolving.
 */
interface ExpenseRepository {

    fun observeAll(): Flow<List<Expense>>

    fun observeByCategory(category: ExpenseCategory): Flow<List<Expense>>

    /** The window is half-open: [from] counts, [to] does not. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<Expense>>

    fun observeForProject(projectId: Long): Flow<List<Expense>>
    /**
     * Reads a expense **whatever its state, including one in the bin**.
     *
     * That is deliberate: restoring a binned record has to be able to read it first. It does mean
     * a caller showing the result must check `deletedAt` — every listing, search and recall path
     * already filters, so this is the one accessor where a binned row can come back.
     */
    suspend fun byId(id: Long): AppResult<Expense>

    suspend fun listBetween(from: Instant, to: Instant): AppResult<List<Expense>>

    suspend fun search(query: String?, limit: Int = 50): AppResult<List<Expense>> =
        AppResult.Success(emptyList())

    suspend fun create(expense: Expense): AppResult<Expense>

    suspend fun update(expense: Expense): AppResult<Expense>

    /** Summed in whole minor units, binned rows excluded. */
    suspend fun totalSpentBetween(
        from: Instant,
        to: Instant,
        currency: Currency = Money.BDT,
        category: ExpenseCategory? = null,
    ): AppResult<Money>

    suspend fun delete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Expense>
}
