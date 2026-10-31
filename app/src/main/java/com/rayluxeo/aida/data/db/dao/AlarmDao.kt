package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.AlarmEntity
import kotlinx.coroutines.flow.Flow

/**
 * The alarm clock's rows.
 *
 * Ordering is by `minuteOfDay` rather than by the armed instant, because the list Abu reads is his
 * day: a 06:30 alarm belongs above a 22:00 one whether or not either is switched on, and sorting
 * by the next trigger would drop every disabled alarm to the bottom and shuffle the rest at
 * midnight.
 *
 * There is no soft delete on this table. An alarm Abu removes is gone; the fact that it once rang
 * lives in `alarm_records`, which is the table that exists to remember.
 */
@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY minuteOfDay ASC, id ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun findById(id: Long): AlarmEntity?

    /**
     * Everything that should have an OS alarm behind it at [nowMillis].
     *
     * The snooze arm of the condition catches the one case the `isEnabled` flag misses: a one-time
     * alarm switches itself off as it rings, so an alarm Abu has just pushed ten minutes away is
     * already disabled and would otherwise be skipped by the replay after a reboot.
     */
    @Query(
        """
        SELECT * FROM alarms
        WHERE isEnabled = 1
           OR (snoozedUntilEpochMillis IS NOT NULL AND snoozedUntilEpochMillis > :nowMillis)
        ORDER BY minuteOfDay ASC, id ASC
        """
    )
    suspend fun listEnabledOrSnoozed(nowMillis: Long): List<AlarmEntity>

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity): Int

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
