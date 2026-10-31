package com.rayluxeo.aida.data.moneyflow

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.domain.model.InvoiceStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One in-memory database, built the way the money tests need it.
 *
 * Foreign keys are off by default in an in-memory builder. Every constraint test in this package
 * would pass for the wrong reason without the PRAGMA below — a SET NULL that never fires looks
 * exactly like a SET NULL that worked when the parent row was the only thing checked.
 *
 * The parent rows are seeded with raw SQL on purpose. Clients, invoices, projects, files and
 * reminders belong to other parts of the schema, and going through their DAOs would tie these
 * tests to files this cluster does not own.
 */
internal fun openMoneyFlowDatabase(): MiraDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        MiraDatabase::class.java,
    )
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .allowMainThreadQueries()
        .build()
        .also { it.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON") }

internal fun MiraDatabase.insertClient(name: String = "Rayluxeo", uuid: String = name): Long =
    insertRow(
        """
        INSERT INTO clients (uuid, name, companyName, email, phone, addressLine, notes,
            defaultCurrencyCode, defaultRateMinor, isArchived, deletedAtEpochMillis,
            createdAtEpochMillis, updatedAtEpochMillis)
        VALUES (?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, ?, ?)
        """,
        arrayOf<Any?>(uuid, name, SEEDED_AT, SEEDED_AT),
    )

internal fun MiraDatabase.insertProject(clientId: Long? = null, name: String = "Site rebuild"): Long =
    insertRow(
        """
        INSERT INTO projects (uuid, clientId, name, description, status, startedOnEpochDay,
            dueOnEpochDay, budgetMinor, currencyCode, deletedAtEpochMillis,
            createdAtEpochMillis, updatedAtEpochMillis)
        VALUES (?, ?, ?, NULL, 'ACTIVE', NULL, NULL, NULL, 'BDT', NULL, ?, ?)
        """,
        arrayOf<Any?>(name, clientId, name, SEEDED_AT, SEEDED_AT),
    )

internal fun MiraDatabase.insertFile(displayName: String = "receipt.jpg"): Long =
    insertRow(
        """
        INSERT INTO files (uuid, displayName, relativePath, mimeType, sizeBytes, source,
            extractedText, clientId, projectId, deletedAtEpochMillis,
            createdAtEpochMillis, updatedAtEpochMillis)
        VALUES (?, ?, ?, 'image/jpeg', 1024, 'SCAN', NULL, NULL, NULL, NULL, ?, ?)
        """,
        arrayOf<Any?>(displayName, displayName, "scans/$displayName", SEEDED_AT, SEEDED_AT),
    )

internal fun MiraDatabase.insertReminder(title: String = "Renew hosting"): Long =
    insertRow(
        """
        INSERT INTO reminders (title, triggerAtEpochMillis, state, kind, linkedTaskId, origin,
            createdAtEpochMillis)
        VALUES (?, ?, 'SCHEDULED', 'SUBSCRIPTION', NULL, 'MANUAL', ?)
        """,
        arrayOf<Any?>(title, SEEDED_AT, SEEDED_AT),
    )

internal fun MiraDatabase.insertInvoice(
    clientId: Long,
    number: String = "INV-2026-001",
    currencyCode: String = "BDT",
    totalMinor: Long = 100_000L,
    amountPaidMinor: Long = 0L,
    status: InvoiceStatus = InvoiceStatus.SENT,
): Long = insertRow(
    """
    INSERT INTO invoices (uuid, number, clientId, projectId, status, issuedOnEpochDay,
        dueOnEpochDay, currencyCode, subtotalMinor, discountMinor, taxMinor, totalMinor,
        amountPaidMinor, notes, deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis)
    VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, 0, 0, ?, ?, NULL, NULL, ?, ?)
    """,
    arrayOf<Any?>(
        number,
        number,
        clientId,
        status.name,
        ISSUED_ON.toEpochDay(),
        ISSUED_ON.plusDays(30).toEpochDay(),
        currencyCode,
        totalMinor,
        totalMinor,
        amountPaidMinor,
        SEEDED_AT,
        SEEDED_AT,
    ),
)

internal fun MiraDatabase.invoiceStatus(invoiceId: Long): String =
    queryString("SELECT status FROM invoices WHERE id = ?", invoiceId)

internal fun MiraDatabase.invoiceAmountPaidMinor(invoiceId: Long): Long =
    queryLong("SELECT amountPaidMinor FROM invoices WHERE id = ?", invoiceId)

internal fun MiraDatabase.paymentCount(): Long =
    queryLong("SELECT COUNT(*) FROM payments")

internal fun MiraDatabase.deleteInvoice(invoiceId: Long) {
    openHelper.writableDatabase.execSQL("DELETE FROM invoices WHERE id = ?", arrayOf<Any?>(invoiceId))
}

internal fun MiraDatabase.deleteClient(clientId: Long) {
    openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = ?", arrayOf<Any?>(clientId))
}

internal fun MiraDatabase.deleteFile(fileId: Long) {
    openHelper.writableDatabase.execSQL("DELETE FROM files WHERE id = ?", arrayOf<Any?>(fileId))
}

internal fun MiraDatabase.deleteReminder(reminderId: Long) {
    openHelper.writableDatabase.execSQL("DELETE FROM reminders WHERE id = ?", arrayOf<Any?>(reminderId))
}

/** Abu's own zone, so a date in a test means the same day it would mean on his phone. */
internal fun LocalDate.atStartOfDayMillis(): Long =
    atStartOfDay(FakeMiraClock.DHAKA).toInstant().toEpochMilli()

internal fun LocalDate.atMillis(hour: Int): Long =
    atTime(hour, 0).atZone(FakeMiraClock.DHAKA).toInstant().toEpochMilli()

private val SEEDED_LOCAL_TIME: LocalDateTime = LocalDateTime.of(2026, 3, 5, 14, 0)

private val ISSUED_ON: LocalDate = LocalDate.of(2026, 3, 1)

private val SEEDED_AT: Long = SEEDED_LOCAL_TIME.atZone(FakeMiraClock.DHAKA).toInstant().toEpochMilli()

private fun MiraDatabase.insertRow(sql: String, args: Array<out Any?>): Long {
    val writable = openHelper.writableDatabase
    writable.execSQL(sql, args)
    return queryLong("SELECT last_insert_rowid()")
}

private fun MiraDatabase.queryLong(sql: String, vararg args: Any?): Long =
    openHelper.writableDatabase.query(sql, args).use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

private fun MiraDatabase.queryString(sql: String, vararg args: Any?): String =
    openHelper.writableDatabase.query(sql, args).use { cursor ->
        cursor.moveToFirst()
        cursor.getString(0)
    }
