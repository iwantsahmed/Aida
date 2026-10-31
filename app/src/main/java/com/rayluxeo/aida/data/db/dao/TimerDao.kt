package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.rayluxeo.aida.data.db.entity.TimerRecordEntity
import com.rayluxeo.aida.domain.model.TimerState
import kotlinx.coroutines.flow.Flow

/**
 * Countdowns.
 *
 * Every state change here reads the row and writes it back inside one transaction, because each
 * one is derived from the row it replaces: a pause that landed between the read and the write
 * would store a remaining time measured against an end instant that had already moved.
 */
@Dao
abstract class TimerDao {

    @Query("SELECT * FROM timer_records WHERE state IN (:states) ORDER BY endsAtEpochMillis ASC, id ASC")
    abstract fun observeInStates(states: List<TimerState>): Flow<List<TimerRecordEntity>>

    /**
     * The same ordering as [observeInStates], read once. The reboot restore runs inside a
     * broadcast and has no business holding a subscription open for a single value.
     */
    @Query("SELECT * FROM timer_records WHERE state IN (:states) ORDER BY endsAtEpochMillis ASC, id ASC")
    abstract suspend fun findInStates(states: List<TimerState>): List<TimerRecordEntity>

    @Query("SELECT * FROM timer_records WHERE id = :id")
    abstract suspend fun findById(id: Long): TimerRecordEntity?

    @Insert
    abstract suspend fun insert(timer: TimerRecordEntity): Long

    @Query(
        """
        UPDATE timer_records
        SET state = :state,
            endsAtEpochMillis = :endsAt,
            remainingMillisWhenPaused = :remainingWhenPaused,
            updatedAtEpochMillis = :updatedAt
        WHERE id = :id
        """
    )
    abstract suspend fun applyState(
        id: Long,
        state: TimerState,
        endsAt: Long,
        remainingWhenPaused: Long?,
        updatedAt: Long,
    )

    /**
     * Freezes a running countdown at [nowMillis]. The end instant is kept as it was so that a
     * timer which is paused and never resumed still says when it would have ended.
     *
     * Returns the paused row, or null when the timer is gone or was not running.
     */
    @Transaction
    open suspend fun pause(id: Long, nowMillis: Long): TimerRecordEntity? {
        val current = findById(id) ?: return null
        if (current.state != TimerState.RUNNING) return null
        val remaining = (current.endsAtEpochMillis - nowMillis).coerceAtLeast(0L)
        applyState(
            id = id,
            state = TimerState.PAUSED,
            endsAt = current.endsAtEpochMillis,
            remainingWhenPaused = remaining,
            updatedAt = nowMillis,
        )
        return findById(id)
    }

    /**
     * Restarts a paused countdown from what was left, so the end instant moves forward by however
     * long the pause lasted. `startedAtEpochMillis` deliberately does not move: it records when
     * Abu started the timer, not when it last resumed.
     *
     * Returns the running row, or null when the timer is gone or was not paused.
     */
    @Transaction
    open suspend fun resume(id: Long, nowMillis: Long): TimerRecordEntity? {
        val current = findById(id) ?: return null
        if (current.state != TimerState.PAUSED) return null
        val remaining = current.remainingMillisWhenPaused ?: return null
        applyState(
            id = id,
            state = TimerState.RUNNING,
            endsAt = nowMillis + remaining,
            remainingWhenPaused = null,
            updatedAt = nowMillis,
        )
        return findById(id)
    }

    /**
     * Ends a timer for good. A timer that has already finished or been cancelled is left alone,
     * so a late "cancel" cannot relabel a countdown Abu actually saw through.
     *
     * Returns the settled row, or null when the timer is gone or was already over.
     */
    @Transaction
    open suspend fun settle(id: Long, state: TimerState, nowMillis: Long): TimerRecordEntity? {
        val current = findById(id) ?: return null
        if (current.state == TimerState.FINISHED || current.state == TimerState.CANCELLED) return null
        applyState(
            id = id,
            state = state,
            endsAt = current.endsAtEpochMillis,
            remainingWhenPaused = null,
            updatedAt = nowMillis,
        )
        return findById(id)
    }
}
