package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rayluxeo.aida.data.db.entity.AlarmRecordEntity
import com.rayluxeo.aida.domain.model.AlarmState
import kotlinx.coroutines.flow.Flow

/**
 * The ledger of alarms Mira has handed to the operating system.
 *
 * Nothing here is ever deleted and no row has a deleted column: the point of the table is to
 * still hold the fact that an alarm fired — or did not — long after the reminder behind it has
 * gone. Deleting that reminder only nulls [AlarmRecordEntity.reminderId].
 */
@Dao
interface AlarmRecordDao {

    @Query("SELECT * FROM alarm_records WHERE state = :state ORDER BY triggerAtEpochMillis ASC, id ASC")
    fun observeInState(state: AlarmState): Flow<List<AlarmRecordEntity>>

    @Query("SELECT * FROM alarm_records WHERE id = :id")
    suspend fun findById(id: Long): AlarmRecordEntity?

    /**
     * Finds the alarm behind a PendingIntent. Request codes are reused once an alarm is done
     * with, so the newest row still in [state] is the one the code currently belongs to; an
     * older fired row with the same code is history, not the live alarm.
     */
    @Query(
        """
        SELECT * FROM alarm_records
        WHERE requestCode = :requestCode AND state = :state
        ORDER BY createdAtEpochMillis DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun findByRequestCode(requestCode: Int, state: AlarmState): AlarmRecordEntity?

    @Query(
        """
        SELECT * FROM alarm_records WHERE reminderId = :reminderId
        ORDER BY triggerAtEpochMillis DESC, id DESC
        """
    )
    suspend fun listForReminder(reminderId: Long): List<AlarmRecordEntity>

    @Insert
    suspend fun insert(record: AlarmRecordEntity): Long

    /**
     * Moves an alarm out of [from] and out of nothing else. A record that has already fired is
     * the one fact this table exists to keep, so a cancel arriving after the alarm went off must
     * change no rows rather than rewrite what happened. The row count says which it was.
     */
    @Query(
        """
        UPDATE alarm_records
        SET state = :to, firedAtEpochMillis = :firedAt, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND state = :from
        """
    )
    suspend fun moveState(
        id: Long,
        from: AlarmState,
        to: AlarmState,
        firedAt: Long?,
        updatedAt: Long,
    ): Int
}
