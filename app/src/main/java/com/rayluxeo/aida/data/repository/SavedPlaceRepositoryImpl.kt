package com.rayluxeo.aida.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.SavedPlaceDao
import com.rayluxeo.aida.data.db.entity.SavedPlaceEntity
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.SavedPlaceRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Abu's place directory, on disk.
 *
 * Two invariants are held here rather than trusted to callers. The label key is derived from the
 * label on the way in, every time, so the unique index and [byLabelKey] cannot disagree about what
 * counts as the same name — a caller that set one and not the other would leave a place findable
 * under a name nobody would ever say. And a name already taken comes back as `VALIDATION`, not as
 * `STORAGE`: SQLite refusing the insert is Abu making a mistake he can correct, not the phone
 * failing, and the two read very differently to him.
 *
 * Nothing in here reaches the network. A latitude and a longitude are written to Room and read
 * back, and that is the whole of their journey.
 */
@Singleton
class SavedPlaceRepositoryImpl @Inject constructor(
    private val dao: SavedPlaceDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : SavedPlaceRepository {

    override fun observeAll(): Flow<List<SavedPlace>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun all(): AppResult<List<SavedPlace>> =
        storageCall(dispatchers, "list saved places") { dao.all().map { it.toDomain() } }

    override suspend fun byLabelKey(labelKey: String): AppResult<SavedPlace?> =
        storageCall(dispatchers, "find the place saved as $labelKey") {
            dao.byLabelKey(labelKey)?.toDomain()
        }

    override suspend fun byId(id: Long): AppResult<SavedPlace> {
        val found = storageCall(dispatchers, "load saved place $id") { dao.byId(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No saved place with id $id")
        }
    }

    override suspend fun create(place: SavedPlace): AppResult<SavedPlace> {
        val now = clock.now()
        val stored = place.copy(
            id = 0L,
            labelKey = SavedPlace.keyOf(place.label),
            createdAt = now,
            updatedAt = now,
        )
        val outcome = storageCall(dispatchers, "save the place ${stored.labelKey}") {
            try {
                Written.Row(dao.insert(stored.toEntity()))
            } catch (refused: Exception) {
                if (refused.isNameTaken()) Written.NameTaken else throw refused
            }
        }
        return outcome.resolve(stored) { id -> AppResult.Success(stored.copy(id = id)) }
    }

    /**
     * Reads the row back rather than trusting the write, because `@Update` reports no row count:
     * an id that is not there updates nothing at all and would otherwise look like a success.
     */
    override suspend fun update(place: SavedPlace): AppResult<SavedPlace> {
        val stored = place.copy(
            labelKey = SavedPlace.keyOf(place.label),
            updatedAt = clock.now(),
        )
        val outcome = storageCall(dispatchers, "update saved place ${place.id}") {
            try {
                dao.update(stored.toEntity())
                Written.Row(place.id)
            } catch (refused: Exception) {
                if (refused.isNameTaken()) Written.NameTaken else throw refused
            }
        }
        return outcome.resolve(stored) { id -> byId(id) }
    }

    /**
     * Idempotent, and the DAO gives no row count to make it anything else. A place that is already
     * gone is the state Abu asked for, and there is nothing downstream to break: a reminder holds
     * its own copy of the coordinate, so deleting the directory entry moves nothing that is armed.
     */
    override suspend fun delete(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "delete saved place $id") { dao.delete(id) }

    /** Turns what came of a write into the one answer the port promises. */
    private suspend fun AppResult<Written>.resolve(
        stored: SavedPlace,
        onRow: suspend (Long) -> AppResult<SavedPlace>,
    ): AppResult<SavedPlace> = when (this) {
        is AppResult.Failure -> this
        is AppResult.Success -> when (val written = value) {
            Written.NameTaken -> failure(
                MiraErrorCode.VALIDATION,
                "Another place is already saved as ${stored.labelKey}",
            )
            is Written.Row -> onRow(written.id)
        }
    }

    /**
     * What came of a write, so the taken-name case can be answered rather than thrown.
     * `storageCall` turns every escaping exception into `STORAGE`, which is the wrong word for a
     * name Abu has already used.
     */
    private sealed interface Written {
        data class Row(val id: Long) : Written
        data object NameTaken : Written
    }
}

private fun SavedPlaceEntity.toDomain(): SavedPlace = SavedPlace(
    id = id,
    label = label,
    labelKey = labelKey,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

private fun SavedPlace.toEntity(): SavedPlaceEntity = SavedPlaceEntity(
    id = id,
    label = label,
    labelKey = labelKey,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

/**
 * True when SQLite refused the write because the name is already taken.
 *
 * The type is read as well as the message, for the reason [isForeignKeyFailure] gives: Room may
 * surface a refusal as the framework exception or as a wrapped driver one. `saved_places` carries
 * exactly one constraint that a caller can trip — the unique index on `labelKey` — and no foreign
 * keys at all, so on this table a constraint refusal can only be a name already in use.
 */
private fun Throwable.isNameTaken(): Boolean =
    generateSequence(this) { it.cause }.take(CONSTRAINT_CAUSE_DEPTH).any { error ->
        error is SQLiteConstraintException ||
            error.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true
    }

private const val CONSTRAINT_CAUSE_DEPTH = 5
