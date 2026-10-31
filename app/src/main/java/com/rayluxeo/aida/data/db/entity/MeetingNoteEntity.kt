package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What was said and agreed at a meeting.
 *
 * Separate from [NoteEntity] because these are bound to a moment rather than filed on their own,
 * and because a meeting usually accumulates several as it goes. [isActionItem] marks the ones
 * that should become tasks.
 */
@Entity(
    tableName = "meeting_notes",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["meetingId"]),
        Index(value = ["taskId"]),
    ],
)
data class MeetingNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val meetingId: Long,
    val body: String,
    val isActionItem: Boolean,
    /** Set once an action item has been turned into a real task. */
    val taskId: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
