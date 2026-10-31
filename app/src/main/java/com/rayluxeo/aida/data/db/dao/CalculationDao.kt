package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rayluxeo.aida.data.db.entity.CalculationRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Every sum Mira has worked out.
 *
 * Append-only: there is no update and no soft delete, because a record of what Abu was told is
 * only worth keeping if it cannot be quietly rewritten afterwards. Clearing the history is all
 * or nothing, and it is [deleteAll] — the same shape as clearing the transcript.
 */
@Dao
interface CalculationDao {

    @Query("SELECT * FROM calculation_records ORDER BY createdAtEpochMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CalculationRecordEntity>>

    @Query("SELECT * FROM calculation_records ORDER BY createdAtEpochMillis DESC, id DESC LIMIT 1")
    suspend fun mostRecent(): CalculationRecordEntity?

    @Insert
    suspend fun insert(record: CalculationRecordEntity): Long

    @Query("DELETE FROM calculation_records")
    suspend fun deleteAll()
}
