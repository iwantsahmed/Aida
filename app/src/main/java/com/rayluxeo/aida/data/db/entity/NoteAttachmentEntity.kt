package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The join between a note and a file.
 *
 * This is the many-to-many table: one note can carry several documents, and one document can be
 * cited by several notes. The pair is unique so the same file cannot be attached twice.
 *
 * `CASCADE` on the note deletes the attachment, which is correct — the link has no meaning
 * without the note. `CASCADE` on the file does the same and is equally safe, because removing
 * the link never removes the file row itself.
 */
@Entity(
    tableName = "note_attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FileRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["noteId", "fileId"], unique = true),
        Index(value = ["fileId"]),
    ],
)
data class NoteAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val noteId: Long,
    val fileId: Long,
    val position: Int,
    val createdAtEpochMillis: Long,
)
