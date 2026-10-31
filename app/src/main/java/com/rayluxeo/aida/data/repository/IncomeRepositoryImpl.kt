package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.IncomeDao
import com.rayluxeo.aida.data.db.entity.IncomeEntryEntity
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.repository.IncomeRepository
import java.time.Instant
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class IncomeRepositoryImpl @Inject constructor(
    private val dao: IncomeDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : IncomeRepository {

    override fun observeRecent(limit: Int): Flow<List<IncomeEntry>> =
        dao.observeRecent(limit).toDomainFlow()

    override fun observeBetween(from: Instant, to: Instant): Flow<List<IncomeEntry>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).toDomainFlow()

    override fun observeForClient(clientId: Long): Flow<List<IncomeEntry>> =
        dao.observeForClient(clientId).toDomainFlow()

    override suspend fun byId(id: Long): AppResult<IncomeEntry> {
        val found = storageCall(dispatchers, "load income entry $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No income entry with id $id")
        }
    }

    override suspend fun create(entry: IncomeEntry): AppResult<IncomeEntry> =
        storageCall(dispatchers, "create income entry") {
            val id = dao.insert(entry.copy(id = 0L).toEntity())
            entry.copy(id = id)
        }

    override suspend fun update(entry: IncomeEntry): AppResult<IncomeEntry> {
        val updated = storageCall(dispatchers, "update income entry ${entry.id}") {
            dao.update(entry.copy(updatedAt = clock.now()).toEntity())
        }
        return when (updated) {
            is AppResult.Failure -> updated
            is AppResult.Success ->
                if (updated.value > 0) byId(entry.id)
                else failure(MiraErrorCode.NOT_FOUND, "No income entry with id ${entry.id}")
        }
    }

    override suspend fun totalReceivedBetween(
        from: Instant,
        to: Instant,
        currency: Currency,
        excludeEntriesFromPayments: Boolean,
    ): AppResult<Money> = storageCall(dispatchers, "total income received") {
        val fromEpochMillis = from.toEpochMilli()
        val toEpochMillis = to.toEpochMilli()
        val minor = if (excludeEntriesFromPayments) {
            dao.sumReceivedMinorUninvoiced(fromEpochMillis, toEpochMillis, currency.currencyCode)
        } else {
            dao.sumReceivedMinor(fromEpochMillis, toEpochMillis, currency.currencyCode)
        }
        MoneyAmount.fromMinorUnits(minor, currency.currencyCode)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val binned = storageCall(dispatchers, "delete income entry $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }
        return when (binned) {
            is AppResult.Failure -> binned
            is AppResult.Success ->
                if (binned.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No live income entry with id $id")
        }
    }

    override suspend fun restore(id: Long): AppResult<IncomeEntry> {
        val restored = storageCall(dispatchers, "restore income entry $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }
        return when (restored) {
            is AppResult.Failure -> restored
            is AppResult.Success ->
                if (restored.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No binned income entry with id $id")
        }
    }

    private fun Flow<List<IncomeEntryEntity>>.toDomainFlow(): Flow<List<IncomeEntry>> =
        map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())
}
