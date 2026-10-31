package com.rayluxeo.aida.data.work

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.TaskPriority

/**
 * Scaffolding shared by the subtask, alarm-record and timer tests.
 *
 * Foreign keys are off by default in an in-memory Room database and `MiraDatabaseCallback` is not
 * attached to one, so the pragma is set here explicitly. Without it the cascade and SET NULL
 * tests would pass because nothing was being enforced, which is worse than not having them.
 */
internal fun openTestDatabase(): MiraDatabase {
    val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        MiraDatabase::class.java,
    )
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .allowMainThreadQueries()
        .build()
    database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
    return database
}

/** Removes a row the way nothing in the app does, so a cascade rule can actually be observed. */
internal fun MiraDatabase.hardDelete(table: String, id: Long) {
    openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE id = $id")
}

internal fun <T> AppResult<T>.successValue(): T {
    assertThat(this).isInstanceOf(AppResult.Success::class.java)
    return (this as AppResult.Success).value
}

internal fun AppResult<*>.failureCode(): MiraErrorCode {
    assertThat(this).isInstanceOf(AppResult.Failure::class.java)
    return (this as AppResult.Failure).error.code
}

internal fun taskRow(title: String, atEpochMillis: Long): TaskEntity = TaskEntity(
    title = title,
    notes = null,
    dueAtEpochMillis = null,
    priority = TaskPriority.NORMAL,
    isDone = false,
    completedAtEpochMillis = null,
    origin = RecordOrigin.MANUAL,
    createdAtEpochMillis = atEpochMillis,
    updatedAtEpochMillis = atEpochMillis,
)
