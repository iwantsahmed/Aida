package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.ReminderPlaceDao
import com.rayluxeo.aida.data.db.entity.ReminderPlaceEntity
import com.rayluxeo.aida.domain.model.ReminderPlace
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.repository.ReminderPlaceRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Storage only. It deliberately knows nothing about [com.rayluxeo.aida.domain.scheduler.PlaceReminderScheduler]:
 * the scheduler reads and writes through this port, and a dependency back the other way would be
 * a Dagger cycle as well as a muddle about which of the two owns the truth.
 *
 * Nothing in here reaches the network. A latitude and a longitude are written to Room and read
 * back, and that is the whole of their journey.
 */
@Singleton
class ReminderPlaceRepositoryImpl @Inject constructor(
    private val dao: ReminderPlaceDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : ReminderPlaceRepository {

    override fun observeAll(): Flow<List<ReminderPlace>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byReminderId(reminderId: Long): AppResult<ReminderPlace?> =
        storageCall(dispatchers, "load place for reminder $reminderId") {
            dao.findByReminderId(reminderId)?.toDomain()
        }

    override suspend fun save(place: ReminderPlace): AppResult<ReminderPlace> {
        val now = clock.now()
        val written = storageCall(dispatchers, "save place for reminder ${place.reminderId}") {
            val id = dao.upsertForReminder(place.toEntity(now))
            dao.findById(id)
        }
        return when (written) {
            is AppResult.Failure -> written
            is AppResult.Success -> written.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.STORAGE, "Place for reminder ${place.reminderId} was not readable after writing")
        }
    }

    override suspend fun setRegistered(id: Long, isRegistered: Boolean): AppResult<Unit> {
        val changed = storageCall(dispatchers, "mark place $id registered=$isRegistered") {
            dao.setRegistered(id, isRegistered, clock.now().toEpochMilli())
        }
        return changed.requireOneRow(id)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val deleted = storageCall(dispatchers, "delete place $id") { dao.deleteById(id) }
        return deleted.requireOneRow(id)
    }

    override suspend fun pending(): AppResult<List<ReminderPlace>> =
        storageCall(dispatchers, "list places awaiting arrival") {
            dao.listForReminderState(ReminderState.SCHEDULED).map { it.toDomain() }
        }

    override suspend fun countRegisteredOtherThan(placeId: Long): AppResult<Int> =
        storageCall(dispatchers, "count registered places") { dao.countRegisteredExcluding(placeId) }

    private fun AppResult<Int>.requireOneRow(id: Long): AppResult<Unit> = when (this) {
        is AppResult.Failure -> this
        is AppResult.Success ->
            if (value > 0) AppResult.Success(Unit)
            else failure(MiraErrorCode.NOT_FOUND, "No reminder place with id $id")
    }
}

private fun ReminderPlaceEntity.toDomain(): ReminderPlace = ReminderPlace(
    id = id,
    reminderId = reminderId,
    label = label,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    trigger = trigger,
    isRegistered = isRegistered,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

/**
 * [now] stamps the edit rather than trusting the caller's copy of it, so two writes in the same
 * session cannot appear to have happened in the wrong order. `isRegistered` is written as false
 * because a row arriving here has not been offered to the platform yet; the upsert preserves the
 * stored flag for a place that already exists.
 */
private fun ReminderPlace.toEntity(now: Instant): ReminderPlaceEntity = ReminderPlaceEntity(
    id = id,
    reminderId = reminderId,
    label = label,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    trigger = trigger,
    isRegistered = false,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = now.toEpochMilli(),
)
