package com.rayluxeo.aida.data.platform

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.rayluxeo.aida.data.db.MiraDatabase

/**
 * The database these tests run against.
 *
 * Two settings are not optional here. `TRUNCATE` keeps every statement on one connection, which
 * Robolectric's SQLite needs; and foreign keys are **off** by default in an in-memory builder,
 * because the callback that turns them on is only attached where the real database is built. A
 * cascade or a SET NULL asserted without the pragma passes for the wrong reason — the constraint
 * is simply not there — so it is turned on explicitly and the constraint tests mean what they say.
 */
internal fun openTestDatabase(): MiraDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        MiraDatabase::class.java,
    )
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .allowMainThreadQueries()
        .build()
        .also { it.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON") }

internal fun MiraDatabase.countRows(table: String): Int =
    openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

/**
 * Clients and projects belong to another part of the schema and have no DAO of their own here, so
 * the parents a file hangs off are written as plain SQL with explicit ids. The columns are spelled
 * out so a later column added to either table cannot silently shift these values sideways.
 */
internal fun MiraDatabase.insertClient(id: Long, name: String = "Ahmed", atEpochMillis: Long = 1L) {
    openHelper.writableDatabase.execSQL(
        """
        INSERT INTO clients (
            id, uuid, name, companyName, email, phone, addressLine, notes,
            defaultCurrencyCode, defaultRateMinor, isArchived, deletedAtEpochMillis,
            createdAtEpochMillis, updatedAtEpochMillis
        ) VALUES ($id, 'client-$id', '$name', NULL, NULL, NULL, NULL, NULL,
            'BDT', NULL, 0, NULL, $atEpochMillis, $atEpochMillis)
        """.trimIndent(),
    )
}

internal fun MiraDatabase.insertProject(
    id: Long,
    clientId: Long? = null,
    name: String = "Website",
    atEpochMillis: Long = 1L,
) {
    openHelper.writableDatabase.execSQL(
        """
        INSERT INTO projects (
            id, uuid, clientId, name, description, status, startedOnEpochDay, dueOnEpochDay,
            budgetMinor, currencyCode, deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis
        ) VALUES ($id, 'project-$id', ${clientId ?: "NULL"}, '$name', NULL, 'ACTIVE', NULL, NULL,
            NULL, 'BDT', NULL, $atEpochMillis, $atEpochMillis)
        """.trimIndent(),
    )
}
