package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.BudgetDao
import com.rayluxeo.aida.data.db.dao.ExpenseDao
import com.rayluxeo.aida.data.db.entity.BudgetEntity
import com.rayluxeo.aida.domain.model.Budget
import com.rayluxeo.aida.domain.model.BudgetProgress
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.repository.BudgetRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Holds the expense DAO as well as its own, because a budget on its own says nothing: the only
 * question worth asking about one is how much of it is left, and the answer lives in the
 * expenses table.
 *
 * A budget's period is a run of days; an expense happened at a moment. The two are reconciled
 * here, once, by turning the period into a half-open millisecond window in Abu's own zone — a
 * conversion the queries must never do for themselves, because SQLite would resolve "the 3rd"
 * in UTC and quietly move the boundary six hours.
 */
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao,
    private val expenses: ExpenseDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : BudgetRepository {

    override fun observeAll(): Flow<List<Budget>> = dao.observeAll().toDomainFlow()

    override fun observeActive(): Flow<List<Budget>> = flow<List<Budget>> {
        emitAll(dao.observeCovering(clock.today().toEpochDay()).map { rows -> rows.map { it.toDomain() } })
    }.flowOn(dispatchers.io).orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Budget> {
        val found = storageCall(dispatchers, "load budget $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No budget with id $id")
        }
    }

    override suspend fun create(budget: Budget): AppResult<Budget> =
        storageCall(dispatchers, "create budget") {
            val id = dao.insert(budget.copy(id = 0L).toEntity())
            budget.copy(id = id)
        }

    override suspend fun update(budget: Budget): AppResult<Budget> {
        val updated = storageCall(dispatchers, "update budget ${budget.id}") {
            dao.update(budget.copy(updatedAt = clock.now()).toEntity())
        }
        return when (updated) {
            is AppResult.Failure -> updated
            is AppResult.Success ->
                if (updated.value > 0) byId(budget.id)
                else failure(MiraErrorCode.NOT_FOUND, "No budget with id ${budget.id}")
        }
    }

    override suspend fun progress(budgetId: Long): AppResult<BudgetProgress> {
        val loaded = storageCall(dispatchers, "load budget $budgetId") { dao.findById(budgetId) }
        return when (loaded) {
            is AppResult.Failure -> loaded
            is AppResult.Success -> {
                val row = loaded.value
                if (row == null || row.deletedAtEpochMillis != null) {
                    failure(MiraErrorCode.NOT_FOUND, "No live budget with id $budgetId")
                } else {
                    storageCall(dispatchers, "roll up budget $budgetId") {
                        row.toDomain().withSpend()
                    }
                }
            }
        }
    }

    override suspend fun activeProgress(): AppResult<List<BudgetProgress>> =
        storageCall(dispatchers, "roll up active budgets") {
            dao.listCovering(clock.today().toEpochDay()).map { it.toDomain().withSpend() }
        }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val binned = storageCall(dispatchers, "delete budget $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }
        return when (binned) {
            is AppResult.Failure -> binned
            is AppResult.Success ->
                if (binned.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No live budget with id $id")
        }
    }

    override suspend fun restore(id: Long): AppResult<Budget> {
        val restored = storageCall(dispatchers, "restore budget $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }
        return when (restored) {
            is AppResult.Failure -> restored
            is AppResult.Success ->
                if (restored.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No binned budget with id $id")
        }
    }

    /**
     * The end of the period is inclusive, so the window runs to the start of the following day.
     * The currency is part of the filter: an expense in another currency is left out of the
     * rollup rather than converted, because Mira holds no exchange rates.
     */
    private suspend fun Budget.withSpend(): BudgetProgress {
        val zone = clock.zone()
        val fromEpochMillis = periodStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val toEpochMillis = periodEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val currencyCode = limit.currency.currencyCode
        val budgetCategory = category
        val minor = if (budgetCategory == null) {
            expenses.sumSpentMinor(fromEpochMillis, toEpochMillis, currencyCode)
        } else {
            expenses.sumSpentMinorInCategory(
                fromEpochMillis = fromEpochMillis,
                toEpochMillis = toEpochMillis,
                currencyCode = currencyCode,
                category = budgetCategory,
            )
        }
        return BudgetProgress(
            budget = this,
            spent = MoneyAmount.fromMinorUnits(minor, currencyCode),
        )
    }

    private fun Flow<List<BudgetEntity>>.toDomainFlow(): Flow<List<Budget>> =
        map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())
}
