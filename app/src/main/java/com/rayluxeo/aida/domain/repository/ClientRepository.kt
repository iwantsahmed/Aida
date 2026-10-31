package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import kotlinx.coroutines.flow.Flow

/**
 * The store of people Abu works for.
 *
 * Deleting is two steps on purpose. [softDelete] puts a client in the bin, where he is invisible
 * to every read here but still joined to his invoices, so [restore] brings back the same row and
 * the same id. [purge] is the irreversible one, and it is refused while any invoice or quote still
 * points at him — a bill with no client on it is not a record anyone can act on.
 */
interface ClientRepository {

    fun observe(filter: ClientFilter = ClientFilter.ACTIVE): Flow<List<Client>>

    /** Fails with NOT_FOUND for a client in the bin, exactly as for one that never existed. */
    suspend fun byId(id: Long): AppResult<Client>

    /** Full-text over name, company and notes. A blank query means the whole [filter] set. */
    suspend fun search(query: String?, filter: ClientFilter = ClientFilter.ACTIVE): AppResult<List<Client>>

    /** The uuid on [client] is ignored; the stored client gets a fresh one that never changes. */
    suspend fun create(client: Client): AppResult<Client>

    /** Changes the details only. The uuid, the creation time and the bin state are left alone. */
    suspend fun update(client: Client): AppResult<Client>

    suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client>

    suspend fun softDelete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Client>

    /**
     * Removes the client for good. Fails with VALIDATION — not STORAGE — while invoices or quotes
     * still reference him, because that is a decision Abu has to make, not a storage fault.
     */
    suspend fun purge(id: Long): AppResult<Unit>
}
