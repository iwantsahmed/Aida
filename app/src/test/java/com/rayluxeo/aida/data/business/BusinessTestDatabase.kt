package com.rayluxeo.aida.data.business

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraError
import com.rayluxeo.aida.data.db.MiraDatabase

/**
 * The database these tests run against.
 *
 * Two settings are not optional here. An in-memory builder does not run Mira's own open callback,
 * so foreign keys start **off** — without the pragma below, every cascade and every RESTRICT test
 * would pass by doing nothing. And Robolectric's SQLite does not support write-ahead logging, so
 * the journal mode is pinned to TRUNCATE rather than left to Room's default.
 */
internal fun openBusinessTestDatabase(): MiraDatabase {
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

/**
 * Writes an invoice straight to SQLite.
 *
 * The invoice cluster belongs to another part of the app; what matters here is only that a row
 * exists pointing at the client, so that the RESTRICT the clients table lives under is real.
 */
internal fun MiraDatabase.insertInvoiceRow(clientId: Long, number: String = "INV-2026-001") {
    openHelper.writableDatabase.execSQL(
        """
        INSERT INTO invoices (
            uuid, number, clientId, projectId, status, issuedOnEpochDay, dueOnEpochDay, currencyCode,
            subtotalMinor, discountMinor, taxMinor, totalMinor, amountPaidMinor, notes,
            deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis
        ) VALUES (
            '11111111-1111-1111-1111-111111111111', '$number', $clientId, NULL, 'SENT', 20517, 20547, 'BDT',
            100000, 0, 0, 100000, 0, NULL, NULL, 1772000000000, 1772000000000
        )
        """.trimIndent()
    )
}

/** Removes the invoice rows again, so a purge that should now succeed is not blocked by leftovers. */
internal fun MiraDatabase.deleteInvoiceRows() {
    openHelper.writableDatabase.execSQL("DELETE FROM invoices")
}

/**
 * The value of a call that was meant to work.
 *
 * A failure is reported as the error Mira actually returned rather than as a cast that blew up,
 * because "expected success but got VALIDATION: rate is finer than BDT" is the sentence that
 * finds the bug.
 */
internal fun <T> AppResult<T>.orFail(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw AssertionError("Expected success, got ${error.code}: ${error.detail}")
}

/** The error of a call that was meant to be refused, for the same reason. */
internal fun AppResult<*>.errorOrFail(): MiraError = when (this) {
    is AppResult.Failure -> error
    is AppResult.Success -> throw AssertionError("Expected a failure, got $value")
}
