package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rayluxeo.aida.data.db.entity.PrayerSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerSettingsDao {

    @Query("SELECT * FROM prayer_settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<PrayerSettingsEntity?>

    @Query("SELECT * FROM prayer_settings WHERE id = 1 LIMIT 1")
    suspend fun find(): PrayerSettingsEntity?

    @Upsert
    suspend fun upsert(entity: PrayerSettingsEntity)
}
