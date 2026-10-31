package com.rayluxeo.aida.data.backup

import com.rayluxeo.aida.data.db.dao.InvoiceWithItems
import com.rayluxeo.aida.data.db.entity.ClientEntity
import com.rayluxeo.aida.data.db.entity.ExpenseEntity
import com.rayluxeo.aida.data.db.entity.IncomeEntryEntity
import com.rayluxeo.aida.data.db.entity.InvoiceItemEntity
import com.rayluxeo.aida.data.db.entity.NoteEntity
import com.rayluxeo.aida.data.db.entity.PaymentEntity
import com.rayluxeo.aida.data.db.entity.TaskEntity
import kotlinx.serialization.Serializable

/**
 * The human-readable export document, distinct from the encrypted [BackupEnvelope].
 *
 * A backup is for Mira to read back and must be exact; an export is for Abu (or his accountant)
 * to read in another program and is deliberately plain. Every field is in its stored form so the
 * numbers cannot drift on the way out: **money is a whole number of minor units beside its
 * `currencyCode`** (never a float), quantities are thousandths, tax rates are basis points, and
 * every timestamp is **epoch milliseconds** while whole dates are **epoch days** — exactly as
 * [com.rayluxeo.aida.data.db] holds them, which is what the suffixes say. Nothing here is read
 * back on restore, so no precision is owed to a parser; it is owed to whoever reads the number.
 *
 * Soft-deleted rows never reach these DTOs: the repository reads them through the DAO queries
 * that already filter `deletedAtEpochMillis IS NULL`.
 *
 * The DTOs are their own types rather than the Room entities so that the export is a stable
 * contract that does not shift when a column is added, and so the mapping can be tested with
 * hand-built entities and no database.
 */
@Serializable
data class DataExport(
    val app: String = "Aida",
    val formatVersion: Int = EXPORT_FORMAT_VERSION,
    val exportedAtEpochMillis: Long,
    val tasks: List<TaskExport>,
    val notes: List<NoteExport>,
    val clients: List<ClientExport>,
    val invoices: List<InvoiceExport>,
    val expenses: List<ExpenseExport>,
    val income: List<IncomeExport>,
    val payments: List<PaymentExport>,
) {
    companion object {
        /** Bumped when the export shape changes, so a reader can tell two generations apart. */
        const val EXPORT_FORMAT_VERSION = 1
    }
}

@Serializable
data class TaskExport(
    val id: Long,
    val title: String,
    val description: String? = null,
    val notes: String? = null,
    val dueAtEpochMillis: Long? = null,
    val dueAllDay: Boolean,
    val estimatedMinutes: Int? = null,
    val priority: String,
    val isDone: Boolean,
    val completedAtEpochMillis: Long? = null,
    val origin: String,
    val projectId: Long? = null,
    val clientId: Long? = null,
    val archivedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class NoteExport(
    val id: Long,
    val title: String? = null,
    val body: String,
    val isPinned: Boolean,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val taskId: Long? = null,
    val languageTag: String,
    val archivedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class ClientExport(
    val id: Long,
    val uuid: String,
    val name: String,
    val companyName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val addressLine: String? = null,
    val notes: String? = null,
    val defaultCurrencyCode: String? = null,
    val defaultRateMinor: Long? = null,
    val isArchived: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class InvoiceExport(
    val id: Long,
    val uuid: String,
    val number: String,
    val clientId: Long,
    val projectId: Long? = null,
    val status: String,
    val issuedOnEpochDay: Long,
    val dueOnEpochDay: Long,
    val currencyCode: String,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val amountPaidMinor: Long,
    val notes: String? = null,
    val lines: List<InvoiceLineExport>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class InvoiceLineExport(
    val id: Long,
    val description: String,
    val quantityMilli: Long,
    val unitPriceMinor: Long,
    val taxRateBasisPoints: Int,
    val lineTotalMinor: Long,
    val position: Int,
)

@Serializable
data class ExpenseExport(
    val id: Long,
    val description: String,
    val category: String,
    val amountMinor: Long,
    val currencyCode: String,
    val spentAtEpochMillis: Long,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val receiptFileId: Long? = null,
    val isBillable: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class IncomeExport(
    val id: Long,
    val source: String,
    val description: String? = null,
    val amountMinor: Long,
    val currencyCode: String,
    val receivedAtEpochMillis: Long,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val paymentId: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class PaymentExport(
    val id: Long,
    val invoiceId: Long? = null,
    val clientId: Long? = null,
    val amountMinor: Long,
    val currencyCode: String,
    val method: String,
    val receivedAtEpochMillis: Long,
    val reference: String? = null,
    val note: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

// --- Entity → DTO mapping -----------------------------------------------------------------------
// Enums become their `name`, the same stable string the database stores, so the export reads the
// same word the schema does. These are pure functions over plain data classes and are tested
// directly, without a database.

internal fun TaskEntity.toExport(): TaskExport = TaskExport(
    id = id,
    title = title,
    description = description,
    notes = notes,
    dueAtEpochMillis = dueAtEpochMillis,
    dueAllDay = dueAllDay,
    estimatedMinutes = estimatedMinutes,
    priority = priority.name,
    isDone = isDone,
    completedAtEpochMillis = completedAtEpochMillis,
    origin = origin.name,
    projectId = projectId,
    clientId = clientId,
    archivedAtEpochMillis = archivedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun NoteEntity.toExport(): NoteExport = NoteExport(
    id = id,
    title = title,
    body = body,
    isPinned = isPinned,
    clientId = clientId,
    projectId = projectId,
    taskId = taskId,
    languageTag = languageTag,
    archivedAtEpochMillis = archivedAtEpochMillis,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun ClientEntity.toExport(): ClientExport = ClientExport(
    id = id,
    uuid = uuid,
    name = name,
    companyName = companyName,
    email = email,
    phone = phone,
    addressLine = addressLine,
    notes = notes,
    defaultCurrencyCode = defaultCurrencyCode,
    defaultRateMinor = defaultRateMinor,
    isArchived = isArchived,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun InvoiceWithItems.toExport(): InvoiceExport = InvoiceExport(
    id = invoice.id,
    uuid = invoice.uuid,
    number = invoice.number,
    clientId = invoice.clientId,
    projectId = invoice.projectId,
    status = invoice.status.name,
    issuedOnEpochDay = invoice.issuedOnEpochDay,
    dueOnEpochDay = invoice.dueOnEpochDay,
    currencyCode = invoice.currencyCode,
    subtotalMinor = invoice.subtotalMinor,
    discountMinor = invoice.discountMinor,
    taxMinor = invoice.taxMinor,
    totalMinor = invoice.totalMinor,
    amountPaidMinor = invoice.amountPaidMinor,
    notes = invoice.notes,
    lines = items.sortedBy { it.position }.map { it.toExport() },
    createdAtEpochMillis = invoice.createdAtEpochMillis,
    updatedAtEpochMillis = invoice.updatedAtEpochMillis,
)

internal fun InvoiceItemEntity.toExport(): InvoiceLineExport = InvoiceLineExport(
    id = id,
    description = description,
    quantityMilli = quantityMilli,
    unitPriceMinor = unitPriceMinor,
    taxRateBasisPoints = taxRateBasisPoints,
    lineTotalMinor = lineTotalMinor,
    position = position,
)

internal fun ExpenseEntity.toExport(): ExpenseExport = ExpenseExport(
    id = id,
    description = description,
    category = category.name,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    spentAtEpochMillis = spentAtEpochMillis,
    clientId = clientId,
    projectId = projectId,
    receiptFileId = receiptFileId,
    isBillable = isBillable,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun IncomeEntryEntity.toExport(): IncomeExport = IncomeExport(
    id = id,
    source = source,
    description = description,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    receivedAtEpochMillis = receivedAtEpochMillis,
    clientId = clientId,
    projectId = projectId,
    paymentId = paymentId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun PaymentEntity.toExport(): PaymentExport = PaymentExport(
    id = id,
    invoiceId = invoiceId,
    clientId = clientId,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    method = method.name,
    receivedAtEpochMillis = receivedAtEpochMillis,
    reference = reference,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
