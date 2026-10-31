package com.rayluxeo.aida.data.knowledge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.MemoryEntity
import com.rayluxeo.aida.data.db.entity.NoteEntity
import com.rayluxeo.aida.data.db.entity.VoiceNoteEntity
import com.rayluxeo.aida.domain.model.MemorySource

/**
 * The database these tests run against.
 *
 * Three things here are load-bearing. Hilt is absent, because its local-test transform does not
 * run under AGP 9. The journal mode is TRUNCATE, so there is a single connection and the foreign
 * key pragma below actually applies to the one the queries use. And that pragma is set by hand:
 * an in-memory builder does not run Mira's own open callback, and foreign keys default to OFF, so
 * without it every cascade and SET NULL test would pass for the wrong reason.
 */
internal fun openKnowledgeDatabase(): MiraDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        MiraDatabase::class.java,
    )
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .allowMainThreadQueries()
        .build()
        .also { it.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON") }

/**
 * Clients, projects and files are seeded with raw SQL rather than through their own DAOs.
 *
 * Those DAOs belong to other parts of the data layer; these tests need the parent rows to exist
 * so the foreign keys can be exercised, and nothing more. Writing them directly keeps the notes
 * tests independent of how another cluster chose to shape its inserts.
 */
internal fun MiraDatabase.seedClient(name: String = "Rahman Textiles"): Long =
    openHelper.writableDatabase.insert(
        "clients",
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            put("uuid", "client-$name")
            put("name", name)
            put("isArchived", 0)
            put("createdAtEpochMillis", SEED_MILLIS)
            put("updatedAtEpochMillis", SEED_MILLIS)
        },
    )

internal fun MiraDatabase.seedProject(clientId: Long? = null, name: String = "Spring campaign"): Long =
    openHelper.writableDatabase.insert(
        "projects",
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            put("uuid", "project-$name")
            if (clientId == null) putNull("clientId") else put("clientId", clientId)
            put("name", name)
            put("status", "ACTIVE")
            put("currencyCode", "BDT")
            put("createdAtEpochMillis", SEED_MILLIS)
            put("updatedAtEpochMillis", SEED_MILLIS)
        },
    )

internal fun MiraDatabase.seedFile(displayName: String = "contract.pdf"): Long =
    openHelper.writableDatabase.insert(
        "files",
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            put("uuid", "file-$displayName")
            put("displayName", displayName)
            put("relativePath", "documents/$displayName")
            put("mimeType", "application/pdf")
            put("sizeBytes", 2_048L)
            put("source", "IMPORT")
            put("createdAtEpochMillis", SEED_MILLIS)
            put("updatedAtEpochMillis", SEED_MILLIS)
        },
    )

/** Hard-deletes a file row, which is what the note_attachments cascade is declared against. */
internal fun MiraDatabase.hardDeleteFile(fileId: Long) {
    openHelper.writableDatabase.execSQL("DELETE FROM files WHERE id = ?", arrayOf<Any>(fileId))
}

/** Hard-deletes a note row. Mira never does this; the cascade rule is still real and tested. */
internal fun MiraDatabase.hardDeleteNote(noteId: Long) {
    openHelper.writableDatabase.execSQL("DELETE FROM notes WHERE id = ?", arrayOf<Any>(noteId))
}

internal fun MiraDatabase.fileExists(fileId: Long): Boolean =
    openHelper.writableDatabase.query("SELECT COUNT(*) FROM files WHERE id = $fileId").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0) > 0
    }

internal fun MiraDatabase.countAttachments(): Int =
    openHelper.writableDatabase.query("SELECT COUNT(*) FROM note_attachments").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

internal fun MiraDatabase.countNotes(): Int =
    openHelper.writableDatabase.query("SELECT COUNT(*) FROM notes").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

internal fun MiraDatabase.softDeleteFile(fileId: Long, atEpochMillis: Long = SEED_MILLIS) {
    openHelper.writableDatabase.execSQL(
        "UPDATE files SET deletedAtEpochMillis = ? WHERE id = ?",
        arrayOf<Any>(atEpochMillis, fileId),
    )
}

internal fun noteEntity(
    title: String? = null,
    body: String,
    isPinned: Boolean = false,
    clientId: Long? = null,
    projectId: Long? = null,
    taskId: Long? = null,
    languageTag: String = "en-US",
    archivedAtEpochMillis: Long? = null,
    deletedAtEpochMillis: Long? = null,
    createdAtEpochMillis: Long = SEED_MILLIS,
    updatedAtEpochMillis: Long = SEED_MILLIS,
) = NoteEntity(
    title = title,
    body = body,
    isPinned = isPinned,
    clientId = clientId,
    projectId = projectId,
    taskId = taskId,
    languageTag = languageTag,
    archivedAtEpochMillis = archivedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun memoryEntity(
    subject: String,
    fact: String,
    source: MemorySource = MemorySource.SPOKEN,
    languageTag: String = "en-US",
    clientId: Long? = null,
    projectId: Long? = null,
    recallCount: Int = 0,
    lastRecalledAtEpochMillis: Long? = null,
    deletedAtEpochMillis: Long? = null,
    createdAtEpochMillis: Long = SEED_MILLIS,
    updatedAtEpochMillis: Long = SEED_MILLIS,
) = MemoryEntity(
    subject = subject,
    fact = fact,
    source = source,
    languageTag = languageTag,
    clientId = clientId,
    projectId = projectId,
    recallCount = recallCount,
    lastRecalledAtEpochMillis = lastRecalledAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun voiceNoteEntity(
    audioFileId: Long? = null,
    noteId: Long? = null,
    transcript: String? = null,
    durationMillis: Long = 12_000L,
    languageTag: String = "en-US",
    recordedAtEpochMillis: Long = SEED_MILLIS,
    deletedAtEpochMillis: Long? = null,
    createdAtEpochMillis: Long = SEED_MILLIS,
    updatedAtEpochMillis: Long = SEED_MILLIS,
) = VoiceNoteEntity(
    audioFileId = audioFileId,
    noteId = noteId,
    transcript = transcript,
    durationMillis = durationMillis,
    languageTag = languageTag,
    recordedAtEpochMillis = recordedAtEpochMillis,
    deletedAtEpochMillis = deletedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

/** 2026-03-05T14:00 in Abu's zone, matching the clock the repository tests run on. */
internal const val SEED_MILLIS: Long = 1_772_697_600_000L

/**
 * Unwrapping an [AppResult] in a test.
 *
 * Written as assertions rather than casts so a failure reads as "expected Success, was Failure"
 * with the error in the message, instead of a ClassCastException with nothing in it.
 */
internal fun <T> AppResult<T>.expectSuccess(): T {
    assertThat(this).isInstanceOf(AppResult.Success::class.java)
    return (this as AppResult.Success).value
}

internal fun <T> AppResult<T>.expectFailure(): MiraErrorCode {
    assertThat(this).isInstanceOf(AppResult.Failure::class.java)
    return (this as AppResult.Failure).error.code
}
