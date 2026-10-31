package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.AlarmDao
import com.rayluxeo.aida.data.db.entity.AlarmEntity
import com.rayluxeo.aida.domain.model.Alarm
import com.rayluxeo.aida.domain.repository.AlarmRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Storage only. Arming an alarm is the scheduler's job and deciding when it should ring is the use
 * cases' — this file is the seam where an [Alarm] becomes a row and back, and nothing else.
 *
 * The mapping is not symmetric with the entity by accident. A time of day is kept as minutes from
 * midnight and the repeat days as their own names, so that a row can be read by eye during a
 * migration and so that 07:00 survives Abu crossing a time zone.
 */
@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val dao: AlarmDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : AlarmRepository {

    override fun observeAll(): Flow<List<Alarm>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Alarm> {
        val found = storageCall(dispatchers, "load alarm $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No alarm with id $id")
        }
    }

    override suspend fun enabledOrSnoozed(): AppResult<List<Alarm>> =
        storageCall(dispatchers, "list armable alarms") {
            dao.listEnabledOrSnoozed(clock.now().toEpochMilli()).map { it.toDomain() }
        }

    override suspend fun create(alarm: Alarm): AppResult<Alarm> =
        storageCall(dispatchers, "create alarm") {
            val id = dao.insert(alarm.copy(id = 0L).toEntity())
            alarm.copy(id = id)
        }

    override suspend fun update(alarm: Alarm): AppResult<Alarm> {
        if (alarm.id <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "Cannot update an unsaved alarm")
        }
        val changed = storageCall(dispatchers, "update alarm ${alarm.id}") { dao.update(alarm.toEntity()) }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) AppResult.Success(alarm)
                else failure(MiraErrorCode.NOT_FOUND, "No alarm with id ${alarm.id}")
        }
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "delete alarm $id") { dao.deleteById(id) }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No alarm with id $id")
        }
    }
}

/**
 * A stored minute outside the day is a corrupt row, not a crash. It is clamped rather than thrown
 * on because the alternative is an alarm list that will not open at all, and one alarm at the wrong
 * minute is a thing Abu can see and fix.
 */
private fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    label = label,
    at = LocalTime.ofSecondOfDay(minuteOfDay.coerceIn(0, MINUTES_IN_DAY - 1).toLong() * SECONDS_IN_MINUTE),
    repeatDays = decodeRepeatDays(repeatDays),
    isEnabled = isEnabled,
    nextTriggerAt = nextTriggerAtEpochMillis?.let(Instant::ofEpochMilli),
    snoozedUntil = snoozedUntilEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

private fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    label = label,
    minuteOfDay = at.hour * MINUTES_IN_HOUR + at.minute,
    repeatDays = encodeRepeatDays(repeatDays),
    isEnabled = isEnabled,
    nextTriggerAtEpochMillis = nextTriggerAt?.toEpochMilli(),
    snoozedUntilEpochMillis = snoozedUntil?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

/** Week order, so two alarms on the same days always produce the same text and compare equal. */
private fun encodeRepeatDays(days: Set<DayOfWeek>): String =
    DayOfWeek.entries.filter { it in days }.joinToString(separator = ",") { it.name }

private fun decodeRepeatDays(stored: String): Set<DayOfWeek> =
    stored.split(',')
        .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name.trim() } }
        .toSet()

private const val MINUTES_IN_HOUR = 60
private const val MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR
private const val SECONDS_IN_MINUTE = 60L
