package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.IncomeEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Money coming in.
 *
 * Every read filters `deletedAtEpochMillis IS NULL`, including the totals — a binned entry that
 * still counts towards what Abu earned this month would be a lie told by arithmetic.
 *
 * [sumReceivedMinorUninvoiced] exists because an entry created from a payment carries that
 * payment's id: adding both to one figure would count the same taka twice.
 */
@Dao
interface IncomeDao {

    @Query(
        """
        SELECT * FROM income_entries
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY receivedAtEpochMillis DESC, id DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<IncomeEntryEntity>>

    @Query(
        """
        SELECT * FROM income_entries
        WHERE deletedAtEpochMillis IS NULL
          AND receivedAtEpochMillis >= :fromEpochMillis
          AND receivedAtEpochMillis < :toEpochMillis
        ORDER BY receivedAtEpochMillis DESC, id DESC
        """
    )
    fun observeBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<IncomeEntryEntity>>

    @Query(
        """
        SELECT * FROM income_entries
        WHERE deletedAtEpochMillis IS NULL AND clientId = :clientId
        ORDER BY receivedAtEpochMillis DESC, id DESC
        """
    )
    fun observeForClient(clientId: Long): Flow<List<IncomeEntryEntity>>

    /** Finds the row whatever its state, so a soft-deleted entry can still be restored. */
    @Query("SELECT * FROM income_entries WHERE id = :id")
    suspend fun findById(id: Long): IncomeEntryEntity?

    @Query("SELECT * FROM income_entries WHERE paymentId = :paymentId AND deletedAtEpochMillis IS NULL")
    suspend fun findByPaymentId(paymentId: Long): IncomeEntryEntity?

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM income_entries
        WHERE deletedAtEpochMillis IS NULL
          AND currencyCode = :currencyCode
          AND receivedAtEpochMillis >= :fromEpochMillis
          AND receivedAtEpochMillis < :toEpochMillis
        """
    )
    suspend fun sumReceivedMinor(
        fromEpochMillis: Long,
        toEpochMillis: Long,
        currencyCode: String,
    ): Long

    /** The half of the total that no payment already accounts for. */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM income_entries
        WHERE deletedAtEpochMillis IS NULL
          AND paymentId IS NULL
          AND currencyCode = :currencyCode
          AND receivedAtEpochMillis >= :fromEpochMillis
          AND receivedAtEpochMillis < :toEpochMillis
        """
    )
    suspend fun sumReceivedMinorUninvoiced(
        fromEpochMillis: Long,
        toEpochMillis: Long,
        currencyCode: String,
    ): Long

    @Insert
    suspend fun insert(entry: IncomeEntryEntity): Long

    @Update
    suspend fun update(entry: IncomeEntryEntity): Int

    @Query(
        """
        UPDATE income_entries
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE income_entries
        SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    suspend fun restore(id: Long, atEpochMillis: Long): Int
}
