package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.ClientEntity
import kotlinx.coroutines.flow.Flow

/**
 * The clients table and the FTS index over it.
 *
 * Every read here filters `deletedAtEpochMillis IS NULL`. A client in the bin keeps his row so
 * that his invoices still join to something and an undo restores the same id — he is simply not
 * a client any screen should offer.
 *
 * Ordering is the same in every query, so a client never changes position when a screen switches
 * between "active" and "all": by name, then by id for the two clients who share one.
 */
@Dao
abstract class ClientDao {

    @Query(
        """
        SELECT * FROM clients
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeAll(): Flow<List<ClientEntity>>

    @Query(
        """
        SELECT * FROM clients
        WHERE deletedAtEpochMillis IS NULL AND isArchived = 0
        ORDER BY name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeActive(): Flow<List<ClientEntity>>

    @Query(
        """
        SELECT * FROM clients
        WHERE deletedAtEpochMillis IS NULL AND isArchived = 1
        ORDER BY name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeArchived(): Flow<List<ClientEntity>>

    @Query(
        """
        SELECT * FROM clients
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract suspend fun listAll(): List<ClientEntity>

    /**
     * [match] is FTS4 MATCH syntax, not a literal — callers build it with a sanitiser, because an
     * apostrophe or a hyphen out of dictation is an operator to SQLite.
     */
    @Query(
        """
        SELECT clients.* FROM clients
        JOIN client_fts ON client_fts.rowid = clients.id
        WHERE client_fts MATCH :match AND clients.deletedAtEpochMillis IS NULL
        ORDER BY clients.name COLLATE NOCASE ASC, clients.id ASC
        """
    )
    abstract suspend fun search(match: String): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :id AND deletedAtEpochMillis IS NULL")
    abstract suspend fun findLiveById(id: Long): ClientEntity?

    /** Reads the row whatever state it is in, which is what restoring from the bin needs. */
    @Query("SELECT * FROM clients WHERE id = :id")
    abstract suspend fun findById(id: Long): ClientEntity?

    /**
     * Counts EVERY invoice for the client, binned ones included — deliberately not filtered on
     * `deletedAtEpochMillis`. This guards deletion: a client with any paperwork at all, even
     * soft-deleted, must not be erased, because the invoice is a fact that has to outlive the
     * contact (DATA_MODEL RESTRICT). Filtering out binned rows here would let a client with only
     * deleted invoices be hard-deleted and orphan them. One of the stated soft-delete exceptions.
     */
    @Query("SELECT COUNT(*) FROM invoices WHERE clientId = :id")
    abstract suspend fun countInvoices(id: Long): Int

    /** Counts every quote for the client, binned included, for the same deletion guard as
     *  [countInvoices]; intentionally not filtered on `deletedAtEpochMillis`. */
    @Query("SELECT COUNT(*) FROM quotes WHERE clientId = :id")
    abstract suspend fun countQuotes(id: Long): Int

    @Insert
    abstract suspend fun insert(client: ClientEntity): Long

    @Update
    protected abstract suspend fun update(client: ClientEntity): Int

    /**
     * The only way details are written back.
     *
     * The uuid and the creation time are read from the stored row and carried forward inside the
     * transaction, so a caller holding a stale or hand-built copy cannot rewrite the identity this
     * client is known by outside the database. A client in the bin is not editable: restore first.
     */
    @Transaction
    open suspend fun updateDetails(client: ClientEntity): Int {
        val existing = findById(client.id) ?: return 0
        if (existing.deletedAtEpochMillis != null) return 0
        return update(
            client.copy(
                uuid = existing.uuid,
                createdAtEpochMillis = existing.createdAtEpochMillis,
                deletedAtEpochMillis = null,
            )
        )
    }

    @Query(
        """
        UPDATE clients SET isArchived = :archived, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long): Int

    @Query(
        """
        UPDATE clients SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE clients SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, updatedAt: Long): Int

    /**
     * Permanent. SQLite refuses this while an invoice or a quote still points at the client, and
     * the repository turns that refusal into an answer Abu can act on rather than a storage error.
     */
    @Query("DELETE FROM clients WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int
}
