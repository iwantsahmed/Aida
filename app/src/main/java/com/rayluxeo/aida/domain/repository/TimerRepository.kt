package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.TimerRecord
import java.time.Duration
import kotlinx.coroutines.flow.Flow

/**
 * Countdowns.
 *
 * The repository owns the arithmetic between a duration and an end instant, so that "ten minutes
 * from now" is resolved once, against the injected clock, rather than by each caller. What is
 * left of a running timer is never stored — read it from [TimerRecord.remainingAt].
 */
interface TimerRepository {

    /** Running and paused timers, the one ending soonest first. */
    fun observeActive(): Flow<List<TimerRecord>>

    /**
     * The same set, read once.
     *
     * A one-shot read rather than a first collection of [observeActive], because the caller is a
     * broadcast receiver with a finite slice of process time: keeping a Flow hot long enough to
     * take one value from it is a subscription that has to be torn down correctly at exactly the
     * moment the process is most likely to be killed.
     */
    suspend fun active(): AppResult<List<TimerRecord>>

    suspend fun byId(id: Long): AppResult<TimerRecord>

    /**
     * Starts a countdown of [duration] from now. [label] may be empty — an unlabelled timer is
     * normal, and naming it is the presentation layer's job, in Abu's language.
     */
    suspend fun start(label: String, duration: Duration): AppResult<TimerRecord>

    /** Freezes a running timer. Refused for a timer that is not running. */
    suspend fun pause(id: Long): AppResult<TimerRecord>

    /** Restarts a paused timer from what was left of it. Refused for a timer that is not paused. */
    suspend fun resume(id: Long): AppResult<TimerRecord>

    /** Marks a timer as having run out. Refused for one that is already over. */
    suspend fun finish(id: Long): AppResult<TimerRecord>

    /** Stops a timer before its moment. Refused for one that is already over. */
    suspend fun cancel(id: Long): AppResult<TimerRecord>
}
