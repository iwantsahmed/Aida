package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.PrayerTrackingEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Abstract rather than an interface so the (day, prayer) upsert is one transaction: it finds the
 * existing row and updates it in place — keeping its id and its original createdAt — instead of a
 * REPLACE that would mint a new row and lose when the prayer was first logged.
 */
@Dao
abstract class PrayerTrackingDao {

    @Query("SELECT * FROM prayer_tracking_entry WHERE localDateEpochDay = :day ORDER BY prayer")
    abstract fun observeDay(day: Long): Flow<List<PrayerTrackingEntryEntity>>

    @Query("SELECT * FROM prayer_tracking_entry WHERE localDateEpochDay BETWEEN :from AND :to ORDER BY localDateEpochDay")
    abstract fun observeBetween(from: Long, to: Long): Flow<List<PrayerTrackingEntryEntity>>

    @Query("SELECT * FROM prayer_tracking_entry WHERE localDateEpochDay BETWEEN :from AND :to ORDER BY localDateEpochDay")
    abstract suspend fun listBetween(from: Long, to: Long): List<PrayerTrackingEntryEntity>

    @Query("SELECT * FROM prayer_tracking_entry WHERE localDateEpochDay = :day AND prayer = :prayer LIMIT 1")
    abstract suspend fun find(day: Long, prayer: String): PrayerTrackingEntryEntity?

    @Insert
    abstract suspend fun insert(entity: PrayerTrackingEntryEntity): Long

    @Update
    abstract suspend fun update(entity: PrayerTrackingEntryEntity)

    @Query("DELETE FROM prayer_tracking_entry")
    abstract suspend fun clearAll()

    @Transaction
    open suspend fun upsert(entity: PrayerTrackingEntryEntity) {
        val existing = find(entity.localDateEpochDay, entity.prayer)
        if (existing == null) {
            insert(entity)
        } else {
            update(entity.copy(id = existing.id, createdAtEpochMillis = existing.createdAtEpochMillis))
        }
    }
}
