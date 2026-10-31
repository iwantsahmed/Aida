package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.SavedPlaceEntity
import kotlinx.coroutines.flow.Flow

/**
 * The place directory.
 *
 * Ordered by [SavedPlaceEntity.label] everywhere rather than by id, so the Places list and the
 * names Mira reads back when she cannot find one are in the same order.
 *
 * The insert deliberately ABORTs instead of replacing. A second place under a name already taken
 * is Abu making a mistake, not an update: replacing would silently move the first place's
 * coordinate and every future reminder with it.
 */
@Dao
interface SavedPlaceDao {

    @Query("SELECT * FROM saved_places ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places ORDER BY label COLLATE NOCASE ASC")
    suspend fun all(): List<SavedPlaceEntity>

    @Query("SELECT * FROM saved_places WHERE labelKey = :labelKey LIMIT 1")
    suspend fun byLabelKey(labelKey: String): SavedPlaceEntity?

    @Query("SELECT * FROM saved_places WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): SavedPlaceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(place: SavedPlaceEntity): Long

    @Update
    suspend fun update(place: SavedPlaceEntity)

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: Long)
}
