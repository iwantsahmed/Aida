package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.ReminderEntity
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderState
import kotlinx.coroutines.flow.Flow

/**
 * Every ordering and every window here is over `COALESCE(snoozedUntilEpochMillis,
 * triggerAtEpochMillis)` rather than the trigger alone.
 *
 * A snooze deliberately leaves the trigger untouched — that is what lets Abu call one off and get
 * his original time back — so ordering by the trigger would show a reminder he pushed to tonight
 * still sitting at this morning, and "what is next" would answer with something that is not next.
 * The cost is that the index on `triggerAtEpochMillis` cannot serve these, which on one person's
 * reminders is a scan of a very short table.
 */
@Dao
interface ReminderDao {

    @Query(
        """
        SELECT * FROM reminders
        WHERE state = :state
        ORDER BY COALESCE(snoozedUntilEpochMillis, triggerAtEpochMillis) ASC, id ASC
        """
    )
    fun observeInState(state: ReminderState): Flow<List<ReminderEntity>>

    @Query(
        """
        SELECT * FROM reminders
        WHERE state = :state AND kind = :kind
        ORDER BY COALESCE(snoozedUntilEpochMillis, triggerAtEpochMillis) ASC, id ASC
        """
    )
    fun observeInStateOfKind(state: ReminderState, kind: ReminderKind): Flow<List<ReminderEntity>>

    /**
     * Every reminder of these kinds, whatever state it is in.
     *
     * The state is deliberately not filtered: the daily sweep uses this to recognise work it has
     * already done, and a reminder Abu cancelled is as much proof that Mira has said this once as
     * one still waiting to fire.
     */
    @Query("SELECT * FROM reminders WHERE kind IN (:kinds)")
    suspend fun listOfKinds(kinds: Collection<ReminderKind>): List<ReminderEntity>

    /**
     * Used after boot and after an upgrade. Reminders whose moment already passed are included
     * deliberately: re-arming one fires it immediately, which is what Abu wants after the phone
     * was off through its trigger time.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE state = :state
        ORDER BY COALESCE(snoozedUntilEpochMillis, triggerAtEpochMillis) ASC, id ASC
        """
    )
    suspend fun listInState(state: ReminderState): List<ReminderEntity>

    @Query(
        """
        SELECT * FROM reminders
        WHERE state = :state
          AND COALESCE(snoozedUntilEpochMillis, triggerAtEpochMillis) >= :atOrAfter
        ORDER BY COALESCE(snoozedUntilEpochMillis, triggerAtEpochMillis) ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun nextInState(state: ReminderState, atOrAfter: Long): ReminderEntity?

    /** Reads a row whatever its state, so a notification action can still find a fired reminder. */
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun findById(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    /**
     * The whole row at once. A snooze, an advanced occurrence and a state change are all one
     * read-modify-write, so none of them can leave a reminder half-moved — the alternative was a
     * column-by-column update per verb, which is where the two halves drift apart.
     */
    @Update
    suspend fun update(reminder: ReminderEntity): Int

    @Query("UPDATE reminders SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: ReminderState): Int
}
