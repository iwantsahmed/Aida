package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rayluxeo.aida.data.db.entity.AutomationFireEntity

/**
 * The record-driven automation fire ledger.
 *
 * Reads reach across to `automations` to exclude a rule Abu has binned — its ledger is kept (soft
 * delete leaves the rows, so a restore does not re-fire history) but is not handed back while the
 * rule is in the bin. Writes dedup on the unique index: [insertAll] ignores an occurrence already
 * recorded, which is what makes a worker retry safe.
 */
@Dao
abstract class AutomationFireDao {

    @Query(
        """
        SELECT f.* FROM automation_record_fires AS f
        INNER JOIN automations AS a ON a.id = f.automationId
        WHERE f.automationId = :automationId AND a.deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun selectForAutomation(automationId: Long): List<AutomationFireEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(rows: List<AutomationFireEntity>)

    @Query("DELETE FROM automation_record_fires WHERE firedAtEpochMillis < :before")
    abstract suspend fun deleteFiredBefore(before: Long): Int
}
