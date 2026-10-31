package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.TimerDao
import com.rayluxeo.aida.data.db.entity.TimerRecordEntity
import com.rayluxeo.aida.domain.model.TimerRecord
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.repository.TimerRepository
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The end instant is worked out here, once, from the injected clock: a countdown that resolved
 * "ten minutes from now" at each call site would drift between the row and the notification.
 *
 * Each transition asks the DAO for a guarded transaction and only reads the row back when the
 * guard refused, so the ordinary path is one write and the refusal path can still say which
 * state the timer was actually in.
 */
@Singleton
class TimerRepositoryImpl @Inject constructor(
    private val dao: TimerDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : TimerRepository {

    override fun observeActive(): Flow<List<TimerRecord>> =
        dao.observeInStates(ACTIVE_STATES)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun active(): AppResult<List<TimerRecord>> =
        storageCall(dispatchers, "list active timers") {
            dao.findInStates(ACTIVE_STATES).map { it.toDomain() }
        }

    override suspend fun byId(id: Long): AppResult<TimerRecord> {
        val found = storageCall(dispatchers, "load timer $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No timer with id $id")
        }
    }

    override suspend fun start(label: String, duration: Duration): AppResult<TimerRecord> {
        if (duration.isZero || duration.isNegative) {
            return failure(MiraErrorCode.VALIDATION, "Timer duration was $duration")
        }
        val startedAt = clock.now().toEpochMilli()
        val inserted = storageCall(dispatchers, "start timer") {
            dao.insert(
                TimerRecordEntity(
                    label = label.trim(),
                    durationMillis = duration.toMillis(),
                    startedAtEpochMillis = startedAt,
                    endsAtEpochMillis = startedAt + duration.toMillis(),
                    state = TimerState.RUNNING,
                    remainingMillisWhenPaused = null,
                    createdAtEpochMillis = startedAt,
                    updatedAtEpochMillis = startedAt,
                )
            )
        }
        return when (inserted) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> byId(inserted.value)
        }
    }

    override suspend fun pause(id: Long): AppResult<TimerRecord> =
        transition(id, "pause timer $id", TimerState.RUNNING) { nowMillis ->
            dao.pause(id, nowMillis)
        }

    override suspend fun resume(id: Long): AppResult<TimerRecord> =
        transition(id, "resume timer $id", TimerState.PAUSED) { nowMillis ->
            dao.resume(id, nowMillis)
        }

    override suspend fun finish(id: Long): AppResult<TimerRecord> =
        transition(id, "finish timer $id", null) { nowMillis ->
            dao.settle(id, TimerState.FINISHED, nowMillis)
        }

    override suspend fun cancel(id: Long): AppResult<TimerRecord> =
        transition(id, "cancel timer $id", null) { nowMillis ->
            dao.settle(id, TimerState.CANCELLED, nowMillis)
        }

    /**
     * [required] is the state the transaction insists on, or null when it only insists that the
     * timer is not already over. It is used for the wording of the refusal, never for the guard
     * itself — the guard lives inside the transaction, where the row cannot change underneath it.
     */
    private suspend fun transition(
        id: Long,
        detail: String,
        required: TimerState?,
        block: suspend (Long) -> TimerRecordEntity?,
    ): AppResult<TimerRecord> {
        val moved = storageCall(dispatchers, detail) { block(clock.now().toEpochMilli()) }
        return when (moved) {
            is AppResult.Failure -> moved
            is AppResult.Success -> moved.value?.toDomain()?.let { AppResult.Success(it) }
                ?: refused(id, required)
        }
    }

    private suspend fun refused(id: Long, required: TimerState?): AppResult<TimerRecord> =
        when (val existing = byId(id)) {
            is AppResult.Failure -> existing
            is AppResult.Success -> failure(
                MiraErrorCode.VALIDATION,
                "Timer $id is ${existing.value.state}" +
                    (required?.let { ", not $it" } ?: " and is already over"),
            )
        }

    private companion object {
        /** A timer Abu can still do something with. Finished and cancelled ones are history. */
        val ACTIVE_STATES = listOf(TimerState.RUNNING, TimerState.PAUSED)
    }
}
