package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rayluxeo.aida.data.db.entity.ActivityLogEntity
import com.rayluxeo.aida.domain.model.ActivityCategory
import kotlinx.coroutines.flow.Flow

/**
 * The record of what Mira did.
 *
 * Insert and read only, on purpose. There is no update and no delete — not even a soft one —
 * because a log that can be rewritten afterwards is no longer evidence of anything.
 *
 * The subject reference is a plain (table, id) pair with no foreign key behind it, so entries
 * outlive the rows they describe. That is what makes "what happened to that invoice" answerable
 * after the invoice is gone.
 */
@Dao
interface ActivityLogDao {

    @Query("SELECT * FROM activity_log ORDER BY occurredAtEpochMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityLogEntity>>

    @Query(
        """
        SELECT * FROM activity_log
        WHERE category = :category
        ORDER BY occurredAtEpochMillis DESC, id DESC
        LIMIT :limit
        """
    )
    fun observeByCategory(category: ActivityCategory, limit: Int): Flow<List<ActivityLogEntity>>

    /** The history of one row, which stays readable after that row has been deleted. */
    @Query(
        """
        SELECT * FROM activity_log
        WHERE subjectTable = :table AND subjectId = :id
        ORDER BY occurredAtEpochMillis ASC, id ASC
        """
    )
    fun observeForSubject(table: String, id: Long): Flow<List<ActivityLogEntity>>

    @Query(
        """
        SELECT * FROM activity_log
        WHERE occurredAtEpochMillis >= :fromEpochMillis AND occurredAtEpochMillis < :toEpochMillis
        ORDER BY occurredAtEpochMillis ASC, id ASC
        """
    )
    suspend fun listBetween(fromEpochMillis: Long, toEpochMillis: Long): List<ActivityLogEntity>

    @Insert
    suspend fun insert(entry: ActivityLogEntity): Long
}
