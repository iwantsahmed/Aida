package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.AutomationEntity
import com.rayluxeo.aida.data.db.entity.AutomationRunEntity
import com.rayluxeo.aida.domain.model.AutomationOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Automations and their run history.
 *
 * An abstract class rather than an interface because opening a run writes two tables — the run
 * itself and the automation's `lastRunAtEpochMillis` — and those have to move together, or the
 * list shows an automation that has never run sitting on top of its own history.
 *
 * Every read excludes binned automations, and the run queries reach across to the automations
 * table to do it: a run belonging to something Abu threw away is not part of any list he asks for.
 * The two exceptions, [runById] and [stampLastRun], address one row by id on purpose.
 */
@Dao
abstract class AutomationDao {

    @Query(
        """
        SELECT * FROM automations
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY name ASC, id ASC
        """
    )
    abstract fun observeAll(): Flow<List<AutomationEntity>>

    @Query(
        """
        SELECT * FROM automations
        WHERE deletedAtEpochMillis IS NULL AND isEnabled = 1
        ORDER BY name ASC, id ASC
        """
    )
    abstract fun observeEnabled(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE id = :id AND deletedAtEpochMillis IS NULL")
    abstract suspend fun findById(id: Long): AutomationEntity?

    @Insert
    abstract suspend fun insert(automation: AutomationEntity): Long

    @Update
    abstract suspend fun update(automation: AutomationEntity): Int

    @Query(
        """
        UPDATE automations
        SET isEnabled = :enabled, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long): Int

    @Query(
        """
        UPDATE automations
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE automations
        SET lastRunAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id
        """
    )
    abstract suspend fun stampLastRun(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        SELECT r.* FROM automation_runs AS r
        JOIN automations AS a ON a.id = r.automationId
        WHERE r.automationId = :automationId AND a.deletedAtEpochMillis IS NULL
        ORDER BY r.startedAtEpochMillis DESC, r.id DESC
        """
    )
    abstract fun observeRuns(automationId: Long): Flow<List<AutomationRunEntity>>

    /** Runs nothing ever closed — the shape a run is left in when the process dies mid-flight. */
    @Query(
        """
        SELECT r.* FROM automation_runs AS r
        JOIN automations AS a ON a.id = r.automationId
        WHERE r.finishedAtEpochMillis IS NULL AND a.deletedAtEpochMillis IS NULL
        ORDER BY r.startedAtEpochMillis ASC, r.id ASC
        """
    )
    abstract suspend fun listInFlight(): List<AutomationRunEntity>

    @Query("SELECT * FROM automation_runs WHERE id = :id")
    abstract suspend fun runById(id: Long): AutomationRunEntity?

    @Insert
    abstract suspend fun insertRun(run: AutomationRunEntity): Long

    /**
     * Closes a run. The null check on the finish time is what makes a second call report zero rows
     * instead of quietly overwriting how the run actually ended.
     */
    @Query(
        """
        UPDATE automation_runs
        SET finishedAtEpochMillis = :finishedAt, outcome = :outcome, detail = :detail
        WHERE id = :id AND finishedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun finishRun(
        id: Long,
        finishedAt: Long,
        outcome: AutomationOutcome,
        detail: String?,
    ): Int

    /** The run row and the automation's last-run stamp, written together or not at all. */
    @Transaction
    open suspend fun startRun(run: AutomationRunEntity): Long {
        val id = insertRun(run)
        stampLastRun(run.automationId, run.startedAtEpochMillis)
        return id
    }
}
