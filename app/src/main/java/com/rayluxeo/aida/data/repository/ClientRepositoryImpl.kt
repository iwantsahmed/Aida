package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.data.db.dao.ClientDao
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.repository.ClientRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The clients table, seen as domain records.
 *
 * Two invariants are held here rather than trusted to callers. The uuid is minted once, at the
 * moment of the first insert, and every later write goes through the DAO transaction that carries
 * the stored one forward — an identity that could be edited would be worthless on an exported
 * invoice. And a permanent delete that SQLite refuses is reported as a decision Abu has to make,
 * not as a storage fault: he still has bills against this client.
 */
@Singleton
class ClientRepositoryImpl @Inject constructor(
    private val dao: ClientDao,
    private val activityLog: com.rayluxeo.aida.domain.repository.ActivityLogRepository,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : ClientRepository {

    override fun observe(filter: ClientFilter): Flow<List<Client>> =
        when (filter) {
            ClientFilter.ALL -> dao.observeAll()
            ClientFilter.ACTIVE -> dao.observeActive()
            ClientFilter.ARCHIVED -> dao.observeArchived()
        }
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Client> {
        val found = storageCall(dispatchers, "load client $id") { dao.findLiveById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No client with id $id")
        }
    }

    override suspend fun search(query: String?, filter: ClientFilter): AppResult<List<Client>> =
        storageCall(dispatchers, "search clients") {
            val match = query?.let(::ftsMatchOrNull)
            val rows = if (match == null) dao.listAll() else dao.search(match)
            rows.map { it.toDomain() }.filter { it.matches(filter) }
        }

    override suspend fun create(client: Client): AppResult<Client> {
        unstorableRate(client)?.let { return it }
        val uuid = UUID.randomUUID().toString()
        val stored = storageCall(dispatchers, "create client") {
            dao.insert(client.copy(id = 0L).toEntity(uuid))
        }
        return stored.map { id -> client.copy(id = id, uuid = uuid) }
    }

    override suspend fun update(client: Client): AppResult<Client> {
        unstorableRate(client)?.let { return it }
        val changed = storageCall(dispatchers, "update client ${client.id}") {
            dao.updateDetails(client.copy(updatedAt = clock.now()).toEntity(client.uuid))
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(client.id)
                else failure(MiraErrorCode.NOT_FOUND, "No client with id ${client.id}")
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client> {
        val changed = storageCall(dispatchers, "archive client $id") {
            dao.setArchived(id = id, archived = archived, updatedAt = clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No client with id $id")
        }
    }

    override suspend fun softDelete(id: Long): AppResult<Unit> {
        val now = clock.now()
        val changed = storageCall(dispatchers, "bin client $id") {
            dao.softDelete(id = id, atEpochMillis = now.toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) {
                    activityLog.record(
                        com.rayluxeo.aida.domain.model.ActivityEntry(
                            category = com.rayluxeo.aida.domain.model.ActivityCategory.CLIENT,
                            action = "client.deleted",
                            summary = "Client soft-deleted (ID: $id)",
                            subject = com.rayluxeo.aida.domain.model.ActivitySubject("clients", id),
                            occurredAt = now,
                        )
                    )
                    AppResult.Success(Unit)
                } else {
                    failure(MiraErrorCode.NOT_FOUND, "No client with id $id")
                }
        }
    }

    override suspend fun restore(id: Long): AppResult<Client> {
        val changed = storageCall(dispatchers, "restore client $id") {
            dao.restore(id = id, updatedAt = clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No client with id $id in the bin")
        }
    }

    override suspend fun purge(id: Long): AppResult<Unit> {
        val outcome = storageCall(dispatchers, "delete client $id") {
            val invoices = dao.countInvoices(id)
            val quotes = dao.countQuotes(id)
            if (invoices + quotes > 0) {
                Purge.Blocked(invoices, quotes)
            } else {
                try {
                    Purge.Removed(dao.deleteById(id))
                } catch (constraint: Exception) {
                    if (constraint.isForeignKeyFailure()) Purge.Blocked(invoices, quotes) else throw constraint
                }
            }
        }
        return when (outcome) {
            is AppResult.Failure -> outcome
            is AppResult.Success -> when (val value = outcome.value) {
                is Purge.Blocked -> failure(
                    MiraErrorCode.VALIDATION,
                    "Client $id still has ${value.invoices} invoices and ${value.quotes} quotes",
                )
                is Purge.Removed ->
                    if (value.rows > 0) AppResult.Success(Unit)
                    else failure(MiraErrorCode.NOT_FOUND, "No client with id $id")
            }
        }
    }

    private fun Client.matches(filter: ClientFilter): Boolean = when (filter) {
        ClientFilter.ALL -> true
        ClientFilter.ACTIVE -> !isArchived
        ClientFilter.ARCHIVED -> isArchived
    }

    private fun unstorableRate(client: Client): AppResult.Failure? {
        val rate = client.billing?.defaultRate ?: return null
        if (isStorable(rate)) return null
        return failure(
            MiraErrorCode.VALIDATION,
            "Default rate ${rate.amount} is finer than ${rate.currency.currencyCode} can hold exactly",
        )
    }

    /** What came of a permanent delete, so the constraint case can be answered rather than thrown. */
    private sealed interface Purge {
        data class Removed(val rows: Int) : Purge
        data class Blocked(val invoices: Int, val quotes: Int) : Purge
    }
}
