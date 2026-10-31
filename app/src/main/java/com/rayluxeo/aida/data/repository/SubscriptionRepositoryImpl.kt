package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.SubscriptionDao
import com.rayluxeo.aida.data.db.entity.SubscriptionEntity
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The calendar arithmetic that advances a charge date belongs to [Subscription] and is applied
 * here, inside the DAO's transaction, by handing it in as a function. A month is not thirty
 * days: a subscription billing on the 31st of January must land on the last day of February,
 * which only `java.time` gets right, and SQLite never sees the question.
 */
@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val dao: SubscriptionDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : SubscriptionRepository {

    override fun observeActive(): Flow<List<Subscription>> = dao.observeActive().toDomainFlow()

    override fun observeAll(): Flow<List<Subscription>> = dao.observeAll().toDomainFlow()

    override suspend fun byId(id: Long): AppResult<Subscription> {
        val found = storageCall(dispatchers, "load subscription $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No subscription with id $id")
        }
    }

    override suspend fun dueOnOrBefore(date: LocalDate): AppResult<List<Subscription>> =
        storageCall(dispatchers, "list subscriptions due on or before $date") {
            dao.listDueOnOrBefore(date.toEpochDay()).map { it.toDomain() }
        }

    override suspend fun create(subscription: Subscription): AppResult<Subscription> =
        storageCall(dispatchers, "create subscription") {
            val id = dao.insert(subscription.copy(id = 0L).toEntity())
            subscription.copy(id = id)
        }

    override suspend fun update(subscription: Subscription): AppResult<Subscription> {
        val updated = storageCall(dispatchers, "update subscription ${subscription.id}") {
            dao.update(subscription.copy(updatedAt = clock.now()).toEntity())
        }
        return when (updated) {
            is AppResult.Failure -> updated
            is AppResult.Success ->
                if (updated.value > 0) byId(subscription.id)
                else failure(MiraErrorCode.NOT_FOUND, "No subscription with id ${subscription.id}")
        }
    }

    override suspend fun setActive(id: Long, active: Boolean): AppResult<Subscription> {
        val changed = storageCall(dispatchers, "set subscription $id active=$active") {
            dao.setActive(id, active, clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No subscription with id $id")
        }
    }

    override suspend fun advanceOverdueCharges(): AppResult<Int> =
        storageCall(dispatchers, "advance overdue subscription charges") {
            val today = clock.today()
            dao.advanceOverdue(
                todayEpochDay = today.toEpochDay(),
                atEpochMillis = clock.now().toEpochMilli(),
            ) { row -> row.toDomain().nextChargeOnOrAfter(today).toEpochDay() }
        }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val binned = storageCall(dispatchers, "delete subscription $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }
        return when (binned) {
            is AppResult.Failure -> binned
            is AppResult.Success ->
                if (binned.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No live subscription with id $id")
        }
    }

    override suspend fun restore(id: Long): AppResult<Subscription> {
        val restored = storageCall(dispatchers, "restore subscription $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }
        return when (restored) {
            is AppResult.Failure -> restored
            is AppResult.Success ->
                if (restored.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No binned subscription with id $id")
        }
    }

    private fun Flow<List<SubscriptionEntity>>.toDomainFlow(): Flow<List<Subscription>> =
        map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())
}
