package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** Anything worth writing down, searchable the moment it is saved. */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        // SET NULL like the other two, and for the reason §8 gives: a note is a fact that must
        // outlive the task it grew out of. Finishing and deleting a task must not take Abu's
        // notes about it with it.
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["projectId"]),
        Index(value = ["isPinned"]),
        Index(value = ["updatedAtEpochMillis"]),
        Index(value = ["deletedAtEpochMillis"]),
        Index(value = ["taskId"]),
        Index(value = ["archivedAtEpochMillis"]),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String?,
    val body: String,
    val isPinned: Boolean,
    val clientId: Long?,
    val projectId: Long?,
    val taskId: Long?,
    val languageTag: String,
    /**
     * Out of the way, but not gone — and a different thing from [deletedAtEpochMillis].
     *
     * Archiving is Abu saying he is finished with a note; deleting is him saying it was a mistake.
     * One column doing both would make "empty the bin" throw away everything he had filed.
     */
    val archivedAtEpochMillis: Long?,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/**
 * Which tags a note carries.
 *
 * Points at the SAME `tags` table tasks use. Abu's vocabulary for labelling his work is one
 * vocabulary — a second `note_tags` list of names would let "rayluxeo" and "Rayluxeo" become
 * different tags depending on which screen he typed it on, which is the drift §7.12 exists to
 * forbid.
 */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class NoteTagEntity(
    val noteId: Long,
    val tagId: Long,
)

/**
 * A page of handwriting belonging to a note.
 *
 * The strokes are NOT in this row. They are a file, like every other blob in Mira, and this row
 * points at it — the same rule `FileRecordEntity` states: the bytes are never in the database.
 *
 * Both file links are `SET NULL` rather than `CASCADE`. A page whose stroke file has gone can
 * still show its exported image, and a page with neither is shown to Abu as a page whose ink was
 * lost. Cascading would delete the row and he would never learn that anything had been there.
 */
@Entity(
    tableName = "ink_pages",
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
            childColumns = ["strokeFileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = FileRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["imageFileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["noteId"]),
        Index(value = ["strokeFileId"]),
        Index(value = ["imageFileId"]),
    ],
)
data class InkPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val noteId: Long,
    val strokeFileId: Long?,
    val imageFileId: Long?,
    /** The size the page was drawn at, so it replays in proportion on any screen. */
    val widthPx: Int,
    val heightPx: Int,
    /** Kept on the row so the notes list can say "34 strokes" without opening the stroke file. */
    val strokeCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "note_fts")
@Fts4(contentEntity = NoteEntity::class)
data class NoteFtsEntity(
    val title: String?,
    val body: String,
)
