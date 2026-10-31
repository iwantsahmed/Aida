package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Abu's one Salah configuration — a single row (id = 1). Enums are stored by name as `String` and
 * converted in the mapper, so this table needs no type converter. Location is nullable because there
 * is none until he sets one; that null is the first-run signal the UI reads.
 */
@Entity(tableName = "prayer_settings")
data class PrayerSettingsEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String?,
    val timezone: String?,
    val calculationMethod: String,
    val madhab: String,
    val highLatitudeRule: String,
    val customFajrAngle: Double?,
    val customIshaAngle: Double?,
    val fajrAdjustment: Int,
    val dhuhrAdjustment: Int,
    val asrAdjustment: Int,
    val maghribAdjustment: Int,
    val ishaAdjustment: Int,
    val prayerNotificationsEnabled: Boolean,
    val notifyFajr: Boolean,
    val notifyDhuhr: Boolean,
    val notifyAsr: Boolean,
    val notifyMaghrib: Boolean,
    val notifyIsha: Boolean,
    val checkInEnabled: Boolean,
    val checkInFajr: Boolean,
    val checkInDhuhr: Boolean,
    val checkInAsr: Boolean,
    val checkInMaghrib: Boolean,
    val checkInIsha: Boolean,
    val defaultAskDelayMinutes: Int,
    val tahajjudReminderEnabled: Boolean,
    val tahajjudReminderMode: String,
    val tahajjudCustomMinutes: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
