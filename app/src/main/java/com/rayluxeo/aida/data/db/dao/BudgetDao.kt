package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * The ceilings Abu set.
 *
 * The period is compared in epoch days, which is the unit the column already holds: turning the
 * stored day back into a date to compare it would put a timezone in the middle of a question
 * that has none. Both ends are inclusive, so a budget ending on the 31st still covers the 31st.
 */
@Dao
interface BudgetDao {

    @Query(
        """
        SELECT * FROM budgets
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY periodStartEpochDay DESC, id DESC
        """
    )
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM budgets
        WHERE deletedAtEpochMillis IS NULL
          AND periodStartEpochDay <= :onEpochDay
          AND periodEndEpochDay >= :onEpochDay
        ORDER BY periodStartEpochDay ASC, id ASC
        """
    )
    fun observeCovering(onEpochDay: Long): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT * FROM budgets
        WHERE deletedAtEpochMillis IS NULL
          AND periodStartEpochDay <= :onEpochDay
          AND periodEndEpochDay >= :onEpochDay
        ORDER BY periodStartEpochDay ASC, id ASC
        """
    )
    suspend fun listCovering(onEpochDay: Long): List<BudgetEntity>

    /** Finds the row whatever its state, so a soft-deleted budget can still be restored. */
    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun findById(id: Long): BudgetEntity?

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity): Int

    @Query(
        """
        UPDATE budgets
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE budgets
        SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    suspend fun restore(id: Long, atEpochMillis: Long): Int
}
