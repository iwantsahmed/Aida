package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Something Abu said and kept.
 *
 * The audio itself is a [FileRecordEntity]; this row holds what the recording means — how long
 * it runs, what language it is in, and the transcript if one was produced. The file link is
 * `SET NULL` so a transcript survives its audio being cleared to reclaim space.
 */
@Entity(
    tableName = "voice_notes",
    foreignKeys = [
        ForeignKey(
            entity = FileRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["audioFileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["audioFileId"]),
        Index(value = ["noteId"]),
        Index(value = ["recordedAtEpochMillis"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val audioFileId: Long?,
    /** Set when the voice note was turned into a written note. */
    val noteId: Long?,
    val transcript: String?,
    val durationMillis: Long,
    val languageTag: String,
    val recordedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
