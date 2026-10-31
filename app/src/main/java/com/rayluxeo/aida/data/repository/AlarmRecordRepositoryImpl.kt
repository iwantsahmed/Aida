package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.AlarmRecordDao
import com.rayluxeo.aida.data.db.entity.AlarmRecordEntity
import com.rayluxeo.aida.domain.model.AlarmRecord
import com.rayluxeo.aida.domain.model.AlarmState
import com.rayluxeo.aida.domain.repository.AlarmRecordRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Every state change here is a guarded update that only moves a still-scheduled record, so a
 * cancel that arrives after the alarm has already gone off changes nothing and is reported as a
 * failure. Overwriting a fired row would destroy the single fact the ledger exists to hold.
 */
@Singleton
class AlarmRecordRepositoryImpl @Inject constructor(
    private val dao: AlarmRecordDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : AlarmRecordRepository {

    override fun observeScheduled(): Flow<List<AlarmRecord>> =
        dao.observeInState(AlarmState.SCHEDULED)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<AlarmRecord> {
        val found = storageCall(dispatchers, "load alarm record $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No alarm record with id $id")
        }
    }

    override suspend fun scheduledByRequestCode(requestCode: Int): AppResult<AlarmRecord?> =
        storageCall(dispatchers, "find alarm for request code $requestCode") {
            dao.findByRequestCode(requestCode, AlarmState.SCHEDULED)?.toDomain()
        }

    override suspend fun forReminder(reminderId: Long): AppResult<List<AlarmRecord>> =
        storageCall(dispatchers, "list alarms for reminder $reminderId") {
            dao.listForReminder(reminderId).map { it.toDomain() }
        }

    override suspend fun record(
        reminderId: Long?,
        label: String,
        triggerAt: Instant,
        isExact: Boolean,
        requestCode: Int,
    ): AppResult<AlarmRecord> {
        val now = clock.now().toEpochMilli()
        val inserted = storageCall(dispatchers, "record alarm for request code $requestCode") {
            dao.insert(
                AlarmRecordEntity(
                    reminderId = reminderId,
                    label = label,
                    triggerAtEpochMillis = triggerAt.toEpochMilli(),
                    isExact = isExact,
                    state = AlarmState.SCHEDULED,
                    firedAtEpochMillis = null,
                    requestCode = requestCode,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            )
        }
        return when (inserted) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> byId(inserted.value)
        }
    }

    override suspend fun markFired(id: Long, firedAt: Instant): AppResult<AlarmRecord> =
        close(id, AlarmState.FIRED, firedAt)

    override suspend fun markMissed(id: Long): AppResult<AlarmRecord> =
        close(id, AlarmState.MISSED, firedAt = null)

    override suspend fun cancel(id: Long): AppResult<AlarmRecord> =
        close(id, AlarmState.CANCELLED, firedAt = null)

    private suspend fun close(
        id: Long,
        to: AlarmState,
        firedAt: Instant?,
    ): AppResult<AlarmRecord> {
        val changed = storageCall(dispatchers, "move alarm record $id to $to") {
            dao.moveState(
                id = id,
                from = AlarmState.SCHEDULED,
                to = to,
                firedAt = firedAt?.toEpochMilli(),
                updatedAt = clock.now().toEpochMilli(),
            )
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success -> if (changed.value > 0) byId(id) else alreadyClosed(id, to)
        }
    }

    /**
     * Nothing moved, so either the record is not there or it had already left [AlarmState.SCHEDULED].
     * The extra read costs one query on the failure path and buys an error Abu can be told about.
     */
    private suspend fun alreadyClosed(id: Long, to: AlarmState): AppResult<AlarmRecord> =
        when (val existing = byId(id)) {
            is AppResult.Failure -> existing
            is AppResult.Success -> failure(
                MiraErrorCode.VALIDATION,
                "Alarm record $id is ${existing.value.state} and cannot be moved to $to",
            )
        }
}
