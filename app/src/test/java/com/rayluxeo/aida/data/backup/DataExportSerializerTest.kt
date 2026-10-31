package com.rayluxeo.aida.data.backup

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.dao.InvoiceWithItems
import com.rayluxeo.aida.data.db.entity.InvoiceEntity
import com.rayluxeo.aida.data.db.entity.InvoiceItemEntity
import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.TaskPriority
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The export shaping is deterministic and database-free, so the exact bytes it produces are
 * asserted here directly — plain JUnit and Truth, no Hilt, no Room (AGENTS.md §7.4).
 */
class DataExportSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun exportOf(
        tasks: List<TaskExport> = emptyList(),
        invoices: List<InvoiceExport> = emptyList(),
        expenses: List<ExpenseExport> = emptyList(),
    ): DataExport = DataExport(
        exportedAtEpochMillis = 1_760_000_000_000L,
        tasks = tasks,
        notes = emptyList(),
        clients = emptyList(),
        invoices = invoices,
        expenses = expenses,
        income = emptyList(),
        payments = emptyList(),
    )

    @Test
    fun `json round trips the whole document`() {
        val export = exportOf(
            tasks = listOf(
                TaskExport(
                    id = 1,
                    title = "Call Ahmed",
                    dueAllDay = false,
                    priority = "HIGH",
                    isDone = false,
                    origin = "VOICE",
                    createdAtEpochMillis = 100,
                    updatedAtEpochMillis = 100,
                ),
            ),
            invoices = listOf(
                InvoiceExport(
                    id = 7,
                    uuid = "inv-uuid",
                    number = "INV-2026-014",
                    clientId = 3,
                    status = "SENT",
                    issuedOnEpochDay = 20_000,
                    dueOnEpochDay = 20_030,
                    currencyCode = "BDT",
                    subtotalMinor = 125_000,
                    discountMinor = 0,
                    taxMinor = 18_750,
                    totalMinor = 143_750,
                    amountPaidMinor = 0,
                    lines = listOf(
                        InvoiceLineExport(
                            id = 1,
                            description = "Design",
                            quantityMilli = 2_500,
                            unitPriceMinor = 50_000,
                            taxRateBasisPoints = 1_500,
                            lineTotalMinor = 125_000,
                            position = 0,
                        ),
                    ),
                    createdAtEpochMillis = 100,
                    updatedAtEpochMillis = 100,
                ),
            ),
        )

        val text = DataExportSerializer.toJson(export)
        val decoded = json.decodeFromString(DataExport.serializer(), text)

        assertThat(decoded).isEqualTo(export)
    }

    @Test
    fun `money is written as integer minor units, never a float`() {
        val export = exportOf(
            expenses = listOf(
                ExpenseExport(
                    id = 1,
                    description = "Server",
                    category = "SOFTWARE",
                    amountMinor = 125_000,
                    currencyCode = "BDT",
                    spentAtEpochMillis = 100,
                    isBillable = false,
                    createdAtEpochMillis = 100,
                    updatedAtEpochMillis = 100,
                ),
            ),
        )

        val text = DataExportSerializer.toJson(export)

        assertThat(text).contains("125000")
        // Minor units are an exact integer; a decimal point anywhere would mean money became a float.
        assertThat(text).doesNotContain("1250.00")
        assertThat(text).doesNotContain("1250.0")
    }

    @Test
    fun `csv quotes fields with commas, quotes and newlines per rfc 4180`() {
        val export = exportOf(
            tasks = listOf(
                TaskExport(
                    id = 1,
                    title = "Buy milk, eggs",
                    description = "He said \"hi\"",
                    notes = "line1\nline2",
                    dueAllDay = false,
                    priority = "NORMAL",
                    isDone = false,
                    origin = "MANUAL",
                    createdAtEpochMillis = 100,
                    updatedAtEpochMillis = 100,
                ),
            ),
        )

        val csv = DataExportSerializer.toCsv(DataExportSerializer.CsvEntity.TASKS, export)
        val lines = csv.split("\r\n")

        assertThat(csv).contains("\r\n")
        assertThat(lines.first()).startsWith("id,title,description,notes,")
        // Comma-bearing field wrapped; embedded quotes doubled; newline field wrapped.
        assertThat(csv).contains("\"Buy milk, eggs\"")
        assertThat(csv).contains("\"He said \"\"hi\"\"\"")
        assertThat(csv).contains("\"line1\nline2\"")
    }

    @Test
    fun `csv leaves null fields empty and renders plain values unquoted`() {
        val export = exportOf(
            tasks = listOf(
                TaskExport(
                    id = 1,
                    title = "Minimal",
                    dueAllDay = false,
                    priority = "LOW",
                    isDone = false,
                    origin = "MANUAL",
                    createdAtEpochMillis = 100,
                    updatedAtEpochMillis = 100,
                ),
            ),
        )

        val csv = DataExportSerializer.toCsv(DataExportSerializer.CsvEntity.TASKS, export)
        val dataRow = csv.split("\r\n")[1]

        // id, title, then description(empty), notes(empty), dueAtEpochMillis(empty), dueAllDay=false.
        assertThat(dataRow).startsWith("1,Minimal,,,,false,")
    }

    @Test
    fun `csv entity name resolves case-insensitively and rejects the unknown`() {
        assertThat(DataExportSerializer.csvEntityOf("tasks"))
            .isEqualTo(DataExportSerializer.CsvEntity.TASKS)
        assertThat(DataExportSerializer.csvEntityOf("Payments"))
            .isEqualTo(DataExportSerializer.CsvEntity.PAYMENTS)
        assertThat(DataExportSerializer.csvEntityOf("subscriptions")).isNull()
        assertThat(DataExportSerializer.csvEntityOf("")).isNull()
    }

    @Test
    fun `task entity maps to its export with enums as names`() {
        val entity = TaskEntity(
            id = 42,
            title = "Ship build",
            description = "release",
            notes = null,
            dueAtEpochMillis = 1_760_000_000_000L,
            dueAllDay = true,
            estimatedMinutes = 30,
            priority = TaskPriority.HIGH,
            isDone = false,
            completedAtEpochMillis = null,
            origin = RecordOrigin.VOICE,
            archivedAtEpochMillis = 123,
            createdAtEpochMillis = 10,
            updatedAtEpochMillis = 20,
        )

        val export = entity.toExport()

        assertThat(export.id).isEqualTo(42)
        assertThat(export.priority).isEqualTo("HIGH")
        assertThat(export.origin).isEqualTo("VOICE")
        assertThat(export.dueAllDay).isTrue()
        assertThat(export.archivedAtEpochMillis).isEqualTo(123)
    }

    @Test
    fun `invoice with items maps and sorts its lines by position`() {
        val invoice = InvoiceEntity(
            id = 7,
            uuid = "inv-uuid",
            number = "INV-2026-014",
            clientId = 3,
            projectId = null,
            status = InvoiceStatus.SENT,
            issuedOnEpochDay = 20_000,
            dueOnEpochDay = 20_030,
            currencyCode = "BDT",
            subtotalMinor = 125_000,
            discountMinor = 0,
            taxMinor = 18_750,
            totalMinor = 143_750,
            amountPaidMinor = 0,
            notes = null,
            deletedAtEpochMillis = null,
            createdAtEpochMillis = 10,
            updatedAtEpochMillis = 20,
        )
        val outOfOrder = InvoiceWithItems(
            invoice = invoice,
            items = listOf(
                InvoiceItemEntity(id = 2, invoiceId = 7, description = "Second", quantityMilli = 1_000, unitPriceMinor = 1, taxRateBasisPoints = 0, lineTotalMinor = 1, position = 1),
                InvoiceItemEntity(id = 1, invoiceId = 7, description = "First", quantityMilli = 1_000, unitPriceMinor = 1, taxRateBasisPoints = 0, lineTotalMinor = 1, position = 0),
            ),
        )

        val export = outOfOrder.toExport()

        assertThat(export.status).isEqualTo("SENT")
        assertThat(export.totalMinor).isEqualTo(143_750)
        assertThat(export.lines.map { it.description }).containsExactly("First", "Second").inOrder()
    }
}
