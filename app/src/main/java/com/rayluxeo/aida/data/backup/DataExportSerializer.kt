package com.rayluxeo.aida.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Turns a [DataExport] into the two human-readable formats Abu can ask for, deterministically and
 * without touching a database — so the exact bytes produced can be asserted in a plain unit test.
 *
 * JSON is the whole document in one object. CSV is one entity at a time, because a CSV file is a
 * single flat table and cannot hold Mira's seven record kinds at once; [CsvEntity] names the ones
 * that can be exported and [csvEntityOf] resolves the identifier the UI passes, returning null for
 * anything unknown so the caller can refuse it with a clear error rather than writing an empty file.
 *
 * CSV follows RFC 4180: records end with CRLF, and a field is wrapped in double quotes when it
 * contains a comma, a quote or a line break, with embedded quotes doubled. That is what keeps a
 * client note that itself contains a comma from silently becoming two columns.
 */
object DataExportSerializer {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(export: DataExport): String = json.encodeToString(export)

    /** The record kinds that can be written to a single CSV file, by their stable identifier. */
    enum class CsvEntity(val id: String) {
        TASKS("tasks"),
        NOTES("notes"),
        CLIENTS("clients"),
        INVOICES("invoices"),
        EXPENSES("expenses"),
        INCOME("income"),
        PAYMENTS("payments"),
    }

    /** The [CsvEntity] for [name], case-insensitively, or null when no entity matches it. */
    fun csvEntityOf(name: String): CsvEntity? =
        CsvEntity.entries.firstOrNull { it.id.equals(name, ignoreCase = true) }

    fun toCsv(entity: CsvEntity, export: DataExport): String = when (entity) {
        CsvEntity.TASKS -> tasksCsv(export.tasks)
        CsvEntity.NOTES -> notesCsv(export.notes)
        CsvEntity.CLIENTS -> clientsCsv(export.clients)
        CsvEntity.INVOICES -> invoicesCsv(export.invoices)
        CsvEntity.EXPENSES -> expensesCsv(export.expenses)
        CsvEntity.INCOME -> incomeCsv(export.income)
        CsvEntity.PAYMENTS -> paymentsCsv(export.payments)
    }

    private fun tasksCsv(rows: List<TaskExport>): String = buildCsv(
        header = listOf(
            "id", "title", "description", "notes", "dueAtEpochMillis", "dueAllDay",
            "estimatedMinutes", "priority", "isDone", "completedAtEpochMillis", "origin",
            "projectId", "clientId", "archivedAtEpochMillis", "createdAtEpochMillis",
            "updatedAtEpochMillis",
        ),
        rows = rows,
    ) {
        listOf(
            it.id, it.title, it.description, it.notes, it.dueAtEpochMillis, it.dueAllDay,
            it.estimatedMinutes, it.priority, it.isDone, it.completedAtEpochMillis, it.origin,
            it.projectId, it.clientId, it.archivedAtEpochMillis, it.createdAtEpochMillis,
            it.updatedAtEpochMillis,
        )
    }

    private fun notesCsv(rows: List<NoteExport>): String = buildCsv(
        header = listOf(
            "id", "title", "body", "isPinned", "clientId", "projectId", "taskId", "languageTag",
            "archivedAtEpochMillis", "createdAtEpochMillis", "updatedAtEpochMillis",
        ),
        rows = rows,
    ) {
        listOf(
            it.id, it.title, it.body, it.isPinned, it.clientId, it.projectId, it.taskId,
            it.languageTag, it.archivedAtEpochMillis, it.createdAtEpochMillis, it.updatedAtEpochMillis,
        )
    }

    private fun clientsCsv(rows: List<ClientExport>): String = buildCsv(
        header = listOf(
            "id", "uuid", "name", "companyName", "email", "phone", "addressLine", "notes",
            "defaultCurrencyCode", "defaultRateMinor", "isArchived", "createdAtEpochMillis",
            "updatedAtEpochMillis",
        ),
        rows = rows,
    ) {
        listOf(
            it.id, it.uuid, it.name, it.companyName, it.email, it.phone, it.addressLine, it.notes,
            it.defaultCurrencyCode, it.defaultRateMinor, it.isArchived, it.createdAtEpochMillis,
            it.updatedAtEpochMillis,
        )
    }

    /**
     * Invoice headers, one row each. The priced lines are nested data that a flat CSV cannot carry
     * without repeating the header on every line; the full lines are in the JSON export, and the
     * stored totals that a reader needs are on the header row here.
     */
    private fun invoicesCsv(rows: List<InvoiceExport>): String = buildCsv(
        header = listOf(
            "id", "uuid", "number", "clientId", "projectId", "status", "issuedOnEpochDay",
            "dueOnEpochDay", "currencyCode", "subtotalMinor", "discountMinor", "taxMinor",
            "totalMinor", "amountPaidMinor", "lineCount", "notes", "createdAtEpochMillis",
            "updatedAtEpochMillis",
        ),
        rows = rows,
    ) {
        listOf(
            it.id, it.uuid, it.number, it.clientId, it.projectId, it.status, it.issuedOnEpochDay,
            it.dueOnEpochDay, it.currencyCode, it.subtotalMinor, it.discountMinor, it.taxMinor,
            it.totalMinor, it.amountPaidMinor, it.lines.size, it.notes, it.createdAtEpochMillis,
            it.updatedAtEpochMillis,
        )
    }

    private fun expensesCsv(rows: List<ExpenseExport>): String = buildCsv(
        header = listOf(
            "id", "description", "category", "amountMinor", "currencyCode", "spentAtEpochMillis",
            "clientId", "projectId", "receiptFileId", "isBillable", "createdAtEpochMillis",
            "updatedAtEpochMillis",
        ),
        rows = rows,
    ) {
        listOf(
            it.id, it.description, it.category, it.amountMinor, it.currencyCode, it.spentAtEpochMillis,
            it.clientId, it.projectId, it.receiptFileId, it.isBillable, it.createdAtEpochMillis,
            it.updatedAtEpochMillis,
        )
    }

    private fun incomeCsv(rows: List<IncomeExport>): String = buildCsv(
        header = listOf(
            "id", "source", "description", "amountMinor", "currencyCode", "receivedAtEpochMillis",
            "clientId", "projectId", "paymentId", "createdAtEpochMillis", "updatedAtEpochMillis",
        ),
        rows = rows,
    ) {
        listOf(
            it.id, it.source, it.description, it.amountMinor, it.currencyCode, it.receivedAtEpochMillis,
            it.clientId, it.projectId, it.paymentId, it.createdAtEpochMillis, it.updatedAtEpochMillis,
        )
    }

    private fun paymentsCsv(rows: List<PaymentExport>): String = buildCsv(
        header = listOf(
            "id", "invoiceId", "clientId", "amountMinor", "currencyCode", "method",
            "receivedAtEpochMillis", "reference", "note", "createdAtEpochMillis", "updatedAtEpochMillis",
        ),
        rows = rows,
    ) {
        listOf(
            it.id, it.invoiceId, it.clientId, it.amountMinor, it.currencyCode, it.method,
            it.receivedAtEpochMillis, it.reference, it.note, it.createdAtEpochMillis,
            it.updatedAtEpochMillis,
        )
    }

    private fun <T> buildCsv(
        header: List<String>,
        rows: List<T>,
        cells: (T) -> List<Any?>,
    ): String = buildString {
        append(header.joinToString(separator = ",") { escape(it) })
        append(LINE_BREAK)
        for (row in rows) {
            append(cells(row).joinToString(separator = ",") { escape(it) })
            append(LINE_BREAK)
        }
    }

    /**
     * One CSV field. Null is an empty field; everything else is its own `toString`, wrapped in
     * quotes with internal quotes doubled when it carries a delimiter, a quote or a line break.
     */
    private fun escape(value: Any?): String {
        val text = value?.toString() ?: return ""
        val needsQuoting = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    private const val LINE_BREAK = "\r\n"
}
