package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The join between the phone's own calendar and Mira's records.
 *
 * Mira does not copy the calendar; the system provider stays the source of truth for what is
 * scheduled. This row only remembers that a particular system event corresponds to a task, a
 * project or a meeting here, so the connection survives across syncs.
 *
 * [systemEventId] is unique because one event maps to at most one Mira record, and the local
 * links are all `SET NULL` so unlinking one side never deletes the row that records the pairing.
 */
@Entity(
    tableName = "calendar_links",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["systemEventId"], unique = true),
        Index(value = ["taskId"]),
        Index(value = ["projectId"]),
        Index(value = ["meetingId"]),
        Index(value = ["startsAtEpochMillis"]),
    ],
)
data class CalendarLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** CalendarContract.Events._ID on this device. */
    val systemEventId: Long,
    val systemCalendarId: Long,
    /** A copy of the event title, so the link still reads sensibly without calendar permission. */
    val title: String,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long?,
    val taskId: Long?,
    val projectId: Long?,
    val meetingId: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
