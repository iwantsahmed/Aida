package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.ExpenseEntity
import com.rayluxeo.aida.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

/**
 * Money going out.
 *
 * Every read filters `deletedAtEpochMillis IS NULL`. An expense Abu has binned must disappear
 * from the lists *and* from the budget rollup in the same breath — a deleted receipt that still
 * counts against a ceiling is the quietest way for a budget to be wrong.
 *
 * The rollup is two queries rather than one with a nullable category, because a budget with no
 * category covers every category, and folding that into `(:category IS NULL OR ...)` hides the
 * difference between "all categories" and "a category that happens to be null".
 */
@Dao
interface ExpenseDao {

    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY spentAtEpochMillis DESC, id DESC
        """
    )
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAtEpochMillis IS NULL AND category = :category
        ORDER BY spentAtEpochMillis DESC, id DESC
        """
    )
    fun observeByCategory(category: ExpenseCategory): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAtEpochMillis IS NULL
          AND spentAtEpochMillis >= :fromEpochMillis
          AND spentAtEpochMillis < :toEpochMillis
        ORDER BY spentAtEpochMillis DESC, id DESC
        """
    )
    fun observeBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAtEpochMillis IS NULL AND projectId = :projectId
        ORDER BY spentAtEpochMillis DESC, id DESC
        """
    )
    fun observeForProject(projectId: Long): Flow<List<ExpenseEntity>>

    /** Finds the row whatever its state, so a soft-deleted expense can still be restored. */
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun findById(id: Long): ExpenseEntity?

    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAtEpochMillis IS NULL
          AND spentAtEpochMillis >= :fromEpochMillis
          AND spentAtEpochMillis < :toEpochMillis
        ORDER BY spentAtEpochMillis DESC, id DESC
        """
    )
    suspend fun listBetween(fromEpochMillis: Long, toEpochMillis: Long): List<ExpenseEntity>

    /** Summed in SQL over whole minor units: no row is ever widened to a Double on the way. */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM expenses
        WHERE deletedAtEpochMillis IS NULL
          AND currencyCode = :currencyCode
          AND spentAtEpochMillis >= :fromEpochMillis
          AND spentAtEpochMillis < :toEpochMillis
        """
    )
    suspend fun sumSpentMinor(
        fromEpochMillis: Long,
        toEpochMillis: Long,
        currencyCode: String,
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM expenses
        WHERE deletedAtEpochMillis IS NULL
          AND currencyCode = :currencyCode
          AND category = :category
          AND spentAtEpochMillis >= :fromEpochMillis
          AND spentAtEpochMillis < :toEpochMillis
        """
    )
    suspend fun sumSpentMinorInCategory(
        fromEpochMillis: Long,
        toEpochMillis: Long,
        currencyCode: String,
        category: ExpenseCategory,
    ): Long

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity): Int

    /** Returns 0 for a row that was already in the bin, so a double delete is not reported twice. */
    @Query(
        """
        UPDATE expenses
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM expenses
        WHERE deletedAtEpochMillis IS NULL
          AND (description LIKE :pattern ESCAPE '\' OR category LIKE :pattern ESCAPE '\')
        ORDER BY spentAtEpochMillis DESC, id DESC
        """
    )
    suspend fun search(pattern: String): List<ExpenseEntity>

    @Query(
        """
        UPDATE expenses
        SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    suspend fun restore(id: Long, atEpochMillis: Long): Int
}
