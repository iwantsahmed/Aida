package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.AlarmState

/**
 * The ledger of what Mira has actually asked the operating system to do.
 *
 * Distinct from [ReminderEntity], which is Abu's intent. This row is the OS-side fact: which
 * request code was used, whether the alarm was allowed to be exact, and whether it fired or was
 * missed. Keeping them apart is what makes it possible to answer "the reminder exists, so why
 * did nothing happen" after a reboot or a revoked exact-alarm permission.
 *
 * `SET NULL` on the reminder: a fired alarm is a historical fact and must outlive its reminder.
 */
@Entity(
    tableName = "alarm_records",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["reminderId"]),
        Index(value = ["requestCode"]),
        Index(value = ["state"]),
        Index(value = ["triggerAtEpochMillis"]),
    ],
)
data class AlarmRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val reminderId: Long?,
    val label: String,
    val triggerAtEpochMillis: Long,
    /** False when the OS refused an exact alarm and Mira fell back to a window. */
    val isExact: Boolean,
    val state: AlarmState,
    val firedAtEpochMillis: Long?,
    /** The PendingIntent request code, so a scheduled alarm can be found and cancelled again. */
    val requestCode: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
