package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.ExpenseDao
import com.rayluxeo.aida.data.db.entity.ExpenseEntity
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import java.time.Instant
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The bin is enforced by the DAO's queries rather than filtered here, so a read that forgets it
 * cannot exist: every observation query already carries `deletedAtEpochMillis IS NULL`, and the
 * only call that reads past it is [byId], which restore needs.
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : ExpenseRepository {

    override fun observeAll(): Flow<List<Expense>> =
        dao.observeAll().toDomainFlow()

    override fun observeByCategory(category: ExpenseCategory): Flow<List<Expense>> =
        dao.observeByCategory(category).toDomainFlow()

    override fun observeBetween(from: Instant, to: Instant): Flow<List<Expense>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).toDomainFlow()

    override fun observeForProject(projectId: Long): Flow<List<Expense>> =
        dao.observeForProject(projectId).toDomainFlow()

    override suspend fun byId(id: Long): AppResult<Expense> {
        val found = storageCall(dispatchers, "load expense $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No expense with id $id")
        }
    }

    override suspend fun listBetween(from: Instant, to: Instant): AppResult<List<Expense>> =
        storageCall(dispatchers, "list expenses") {
            dao.listBetween(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain() }
        }

    override suspend fun create(expense: Expense): AppResult<Expense> =
        storageCall(dispatchers, "create expense") {
            val id = dao.insert(expense.copy(id = 0L).toEntity())
            expense.copy(id = id)
        }

    override suspend fun search(query: String?, limit: Int): AppResult<List<Expense>> =
        storageCall(dispatchers, "search expenses") {
            val pattern = "%${query?.trim() ?: ""}%"
            dao.search(pattern).map { it.toDomain() }
        }

    override suspend fun update(expense: Expense): AppResult<Expense> {
        val updated = storageCall(dispatchers, "update expense ${expense.id}") {
            dao.update(expense.copy(updatedAt = clock.now()).toEntity())
        }
        return when (updated) {
            is AppResult.Failure -> updated
            is AppResult.Success ->
                if (updated.value > 0) byId(expense.id)
                else failure(MiraErrorCode.NOT_FOUND, "No expense with id ${expense.id}")
        }
    }

    override suspend fun totalSpentBetween(
        from: Instant,
        to: Instant,
        currency: Currency,
        category: ExpenseCategory?,
    ): AppResult<Money> = storageCall(dispatchers, "total expenses") {
        val fromEpochMillis = from.toEpochMilli()
        val toEpochMillis = to.toEpochMilli()
        val minor = if (category == null) {
            dao.sumSpentMinor(fromEpochMillis, toEpochMillis, currency.currencyCode)
        } else {
            dao.sumSpentMinorInCategory(
                fromEpochMillis = fromEpochMillis,
                toEpochMillis = toEpochMillis,
                currencyCode = currency.currencyCode,
                category = category,
            )
        }
        MoneyAmount.fromMinorUnits(minor, currency.currencyCode)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val binned = storageCall(dispatchers, "delete expense $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }
        return when (binned) {
            is AppResult.Failure -> binned
            is AppResult.Success ->
                if (binned.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No live expense with id $id")
        }
    }

    override suspend fun restore(id: Long): AppResult<Expense> {
        val restored = storageCall(dispatchers, "restore expense $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }
        return when (restored) {
            is AppResult.Failure -> restored
            is AppResult.Success ->
                if (restored.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No binned expense with id $id")
        }
    }

    private fun Flow<List<ExpenseEntity>>.toDomainFlow(): Flow<List<Expense>> =
        map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())
}
