package com.rayluxeo.aida.data.money

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.ClientEntity
import com.rayluxeo.aida.data.db.entity.InvoiceEntity
import com.rayluxeo.aida.data.db.entity.InvoiceItemEntity
import com.rayluxeo.aida.data.db.entity.ProjectEntity
import com.rayluxeo.aida.data.db.entity.QuoteEntity
import com.rayluxeo.aida.data.db.entity.QuoteItemEntity
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.QuoteStatus
import java.time.LocalDate
import java.util.UUID

/**
 * Rows for the invoice and quote tests.
 *
 * The database is built with foreign keys explicitly on. An in-memory builder leaves them off,
 * and a constraint test against a database that is not enforcing constraints passes for entirely
 * the wrong reason.
 */
object InvoiceFixtures {

    val ISSUED: LocalDate = LocalDate.of(2026, 3, 5)
    val DUE: LocalDate = LocalDate.of(2026, 3, 19)
    const val NOW_MILLIS = 1_772_000_000_000L
    const val BDT = "BDT"

    fun openDatabase(): MiraDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MiraDatabase::class.java,
        )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON") }

    fun client(name: String = "Rayluxeo"): ClientEntity = ClientEntity(
        uuid = UUID.randomUUID().toString(),
        name = name,
        companyName = null,
        email = null,
        phone = null,
        addressLine = null,
        notes = null,
        defaultCurrencyCode = BDT,
        defaultRateMinor = null,
        isArchived = false,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = NOW_MILLIS,
        updatedAtEpochMillis = NOW_MILLIS,
    )

    fun project(clientId: Long?, name: String = "Website refresh"): ProjectEntity = ProjectEntity(
        uuid = UUID.randomUUID().toString(),
        clientId = clientId,
        name = name,
        description = null,
        status = ProjectStatus.ACTIVE,
        startedOnEpochDay = ISSUED.toEpochDay(),
        dueOnEpochDay = DUE.toEpochDay(),
        budgetMinor = null,
        currencyCode = BDT,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = NOW_MILLIS,
        updatedAtEpochMillis = NOW_MILLIS,
    )

    fun invoice(
        number: String,
        clientId: Long,
        projectId: Long? = null,
        status: InvoiceStatus = InvoiceStatus.DRAFT,
        issuedOn: LocalDate = ISSUED,
        dueOn: LocalDate = DUE,
        subtotalMinor: Long = 0L,
        discountMinor: Long = 0L,
        taxMinor: Long = 0L,
        totalMinor: Long = 0L,
        amountPaidMinor: Long = 0L,
        notes: String? = null,
        deletedAtEpochMillis: Long? = null,
    ): InvoiceEntity = InvoiceEntity(
        uuid = UUID.randomUUID().toString(),
        number = number,
        clientId = clientId,
        projectId = projectId,
        status = status,
        issuedOnEpochDay = issuedOn.toEpochDay(),
        dueOnEpochDay = dueOn.toEpochDay(),
        currencyCode = BDT,
        subtotalMinor = subtotalMinor,
        discountMinor = discountMinor,
        taxMinor = taxMinor,
        totalMinor = totalMinor,
        amountPaidMinor = amountPaidMinor,
        notes = notes,
        deletedAtEpochMillis = deletedAtEpochMillis,
        createdAtEpochMillis = NOW_MILLIS,
        updatedAtEpochMillis = NOW_MILLIS,
    )

    fun item(
        description: String,
        quantityMilli: Long = 1_000L,
        unitPriceMinor: Long = 0L,
        taxRateBasisPoints: Int = 0,
        lineTotalMinor: Long = 0L,
        position: Int = 0,
    ): InvoiceItemEntity = InvoiceItemEntity(
        invoiceId = 0L,
        description = description,
        quantityMilli = quantityMilli,
        unitPriceMinor = unitPriceMinor,
        taxRateBasisPoints = taxRateBasisPoints,
        lineTotalMinor = lineTotalMinor,
        position = position,
    )

    fun quote(
        number: String,
        clientId: Long,
        projectId: Long? = null,
        status: QuoteStatus = QuoteStatus.DRAFT,
        issuedOn: LocalDate = ISSUED,
        validUntil: LocalDate? = DUE,
        subtotalMinor: Long = 0L,
        discountMinor: Long = 0L,
        taxMinor: Long = 0L,
        totalMinor: Long = 0L,
        convertedInvoiceId: Long? = null,
        notes: String? = null,
        deletedAtEpochMillis: Long? = null,
    ): QuoteEntity = QuoteEntity(
        uuid = UUID.randomUUID().toString(),
        number = number,
        clientId = clientId,
        projectId = projectId,
        status = status,
        issuedOnEpochDay = issuedOn.toEpochDay(),
        validUntilEpochDay = validUntil?.toEpochDay(),
        currencyCode = BDT,
        subtotalMinor = subtotalMinor,
        discountMinor = discountMinor,
        taxMinor = taxMinor,
        totalMinor = totalMinor,
        convertedInvoiceId = convertedInvoiceId,
        notes = notes,
        deletedAtEpochMillis = deletedAtEpochMillis,
        createdAtEpochMillis = NOW_MILLIS,
        updatedAtEpochMillis = NOW_MILLIS,
    )

    fun quoteItem(
        description: String,
        quantityMilli: Long = 1_000L,
        unitPriceMinor: Long = 0L,
        taxRateBasisPoints: Int = 0,
        lineTotalMinor: Long = 0L,
        position: Int = 0,
    ): QuoteItemEntity = QuoteItemEntity(
        quoteId = 0L,
        description = description,
        quantityMilli = quantityMilli,
        unitPriceMinor = unitPriceMinor,
        taxRateBasisPoints = taxRateBasisPoints,
        lineTotalMinor = lineTotalMinor,
        position = position,
    )

    /**
     * Clients, projects and payments are written with SQL rather than through a DAO: they belong
     * to other clusters, and a test for invoices should not wait on somebody else's repository to
     * be able to insert the parent row an invoice needs.
     */
    fun insertClient(database: MiraDatabase, name: String = "Rayluxeo"): Long {
        val row = client(name)
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO clients
                (uuid, name, companyName, email, phone, addressLine, notes, defaultCurrencyCode,
                 defaultRateMinor, isArchived, deletedAtEpochMillis, createdAtEpochMillis,
                 updatedAtEpochMillis)
            VALUES (?, ?, NULL, NULL, NULL, NULL, NULL, ?, NULL, 0, NULL, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(row.uuid, row.name, row.defaultCurrencyCode, row.createdAtEpochMillis, row.updatedAtEpochMillis),
        )
        return lastRowId(database)
    }

    fun insertProject(database: MiraDatabase, clientId: Long?, name: String = "Website refresh"): Long {
        val row = project(clientId, name)
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO projects
                (uuid, clientId, name, description, status, startedOnEpochDay, dueOnEpochDay,
                 budgetMinor, currencyCode, deletedAtEpochMillis, createdAtEpochMillis,
                 updatedAtEpochMillis)
            VALUES (?, ?, ?, NULL, ?, ?, ?, NULL, ?, NULL, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                row.uuid,
                row.clientId,
                row.name,
                row.status.name,
                row.startedOnEpochDay,
                row.dueOnEpochDay,
                row.currencyCode,
                row.createdAtEpochMillis,
                row.updatedAtEpochMillis,
            ),
        )
        return lastRowId(database)
    }

    fun insertPayment(database: MiraDatabase, invoiceId: Long, clientId: Long, amountMinor: Long): Long {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO payments
                (invoiceId, clientId, amountMinor, currencyCode, method, receivedAtEpochMillis,
                 reference, note, createdAtEpochMillis, updatedAtEpochMillis)
            VALUES (?, ?, ?, ?, 'CASH', ?, NULL, NULL, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(invoiceId, clientId, amountMinor, BDT, NOW_MILLIS, NOW_MILLIS, NOW_MILLIS),
        )
        return lastRowId(database)
    }

    fun longOrNull(database: MiraDatabase, sql: String): Long? =
        database.openHelper.writableDatabase.query(sql).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
        }

    fun count(database: MiraDatabase, sql: String): Int =
        database.openHelper.writableDatabase.query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun lastRowId(database: MiraDatabase): Long =
        longOrNull(database, "SELECT last_insert_rowid()") ?: 0L
}
