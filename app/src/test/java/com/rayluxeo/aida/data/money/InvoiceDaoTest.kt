package com.rayluxeo.aida.data.money

import android.app.Application
import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.InvoiceDao
import com.rayluxeo.aida.data.money.InvoiceFixtures.count
import com.rayluxeo.aida.data.money.InvoiceFixtures.insertClient
import com.rayluxeo.aida.data.money.InvoiceFixtures.insertPayment
import com.rayluxeo.aida.data.money.InvoiceFixtures.insertProject
import com.rayluxeo.aida.data.money.InvoiceFixtures.invoice
import com.rayluxeo.aida.data.money.InvoiceFixtures.item
import com.rayluxeo.aida.data.money.InvoiceFixtures.longOrNull
import com.rayluxeo.aida.domain.model.InvoiceStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hilt is deliberately absent (AGENTS §7.4). Foreign keys are switched on explicitly, because an
 * in-memory database leaves them off and the cascade tests below would then prove nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InvoiceDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: InvoiceDao
    private var clientId = 0L

    @Before
    fun openDatabase() {
        database = InvoiceFixtures.openDatabase()
        dao = database.invoiceDao()
        clientId = insertClient(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an invoice and its lines are written together`() = runTest {
        val id = dao.insertWithLines(
            invoice(
                number = "INV-2026-001",
                clientId = clientId,
                subtotalMinor = 308_640L,
                taxMinor = 46_296L,
                totalMinor = 354_936L,
            ),
            listOf(
                item(
                    description = "Design, 2.5 days",
                    quantityMilli = 2_500L,
                    unitPriceMinor = 123_456L,
                    taxRateBasisPoints = 1_500,
                    lineTotalMinor = 308_640L,
                ),
            ),
        )!!

        val stored = dao.findById(id)!!
        assertThat(stored.invoice.number).isEqualTo("INV-2026-001")
        assertThat(stored.items).hasSize(1)
        assertThat(stored.items.single().invoiceId).isEqualTo(id)
    }

    @Test
    fun `a line of two and a half units at 1234-56 with 15 percent tax round-trips exactly`() = runTest {
        val id = dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId, subtotalMinor = 308_640L),
            listOf(
                item(
                    description = "Design",
                    quantityMilli = 2_500L,
                    unitPriceMinor = 123_456L,
                    taxRateBasisPoints = 1_500,
                    lineTotalMinor = 308_640L,
                ),
            ),
        )!!

        val line = dao.findById(id)!!.items.single()
        assertThat(line.quantityMilli).isEqualTo(2_500L)
        assertThat(line.unitPriceMinor).isEqualTo(123_456L)
        assertThat(line.taxRateBasisPoints).isEqualTo(1_500)
        assertThat(line.lineTotalMinor).isEqualTo(308_640L)
    }

    @Test
    fun `the stored subtotal equals the sum of the stored lines`() = runTest {
        val lines = (1..9).map { index ->
            item(description = "Line $index", unitPriceMinor = 33_333L, lineTotalMinor = 33_333L, position = index - 1)
        }
        val id = dao.insertWithLines(
            invoice(
                number = "INV-2026-004",
                clientId = clientId,
                subtotalMinor = 299_997L,
                totalMinor = 299_997L,
            ),
            lines,
        )!!

        val summed = longOrNull(database, "SELECT SUM(lineTotalMinor) FROM invoice_items WHERE invoiceId = $id")
        assertThat(summed).isEqualTo(dao.findById(id)!!.invoice.subtotalMinor)
        assertThat(summed).isEqualTo(299_997L)
    }

    @Test
    fun `a duplicate number is refused and nothing is written`() = runTest {
        dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item(description = "First")),
        )

        val second = dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item(description = "Second")),
        )

        assertThat(second).isNull()
        assertThat(count(database, "SELECT COUNT(*) FROM invoices")).isEqualTo(1)
        assertThat(count(database, "SELECT COUNT(*) FROM invoice_items")).isEqualTo(1)
    }

    @Test
    fun `a binned invoice keeps its number reserved`() = runTest {
        val id = dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item(description = "First")),
        )!!
        dao.softDelete(id, InvoiceFixtures.NOW_MILLIS)

        val reissued = dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item(description = "Second")),
        )

        assertThat(reissued).isNull()
    }

    @Test
    fun `a generated number continues the year and restarts the next one`() = runTest {
        val first = dao.insertWithGeneratedNumber(
            invoice(number = "", clientId = clientId),
            listOf(item(description = "One")),
            "INV-2026-",
            nextNumber("INV-2026-"),
        )
        val second = dao.insertWithGeneratedNumber(
            invoice(number = "", clientId = clientId),
            listOf(item(description = "Two")),
            "INV-2026-",
            nextNumber("INV-2026-"),
        )
        val nextYear = dao.insertWithGeneratedNumber(
            invoice(number = "", clientId = clientId, issuedOn = LocalDate.of(2027, 1, 4)),
            listOf(item(description = "Three")),
            "INV-2027-",
            nextNumber("INV-2027-"),
        )

        assertThat(dao.findById(first)!!.invoice.number).isEqualTo("INV-2026-001")
        assertThat(dao.findById(second)!!.invoice.number).isEqualTo("INV-2026-002")
        assertThat(dao.findById(nextYear)!!.invoice.number).isEqualTo("INV-2027-001")
    }

    @Test
    fun `the highest number is read numerically, not alphabetically`() = runTest {
        listOf("INV-2026-008", "INV-2026-009", "INV-2026-010").forEach { number ->
            dao.insertWithLines(invoice(number = number, clientId = clientId), listOf(item(description = number)))
        }

        assertThat(dao.highestNumberWithPrefix("INV-2026-")).isEqualTo("INV-2026-010")
    }

    @Test
    fun `deleting an invoice takes its lines and nothing else`() = runTest {
        val projectId = insertProject(database, clientId)
        val id = dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId, projectId = projectId),
            listOf(item(description = "One"), item(description = "Two", position = 1)),
        )!!
        insertPayment(database, invoiceId = id, clientId = clientId, amountMinor = 50_000L)

        assertThat(dao.deleteById(id)).isEqualTo(1)

        assertThat(count(database, "SELECT COUNT(*) FROM invoice_items")).isEqualTo(0)
        assertThat(count(database, "SELECT COUNT(*) FROM payments")).isEqualTo(1)
        assertThat(longOrNull(database, "SELECT invoiceId FROM payments")).isNull()
        assertThat(count(database, "SELECT COUNT(*) FROM clients")).isEqualTo(1)
        assertThat(count(database, "SELECT COUNT(*) FROM projects")).isEqualTo(1)
    }

    @Test
    fun `a client cannot be deleted while an invoice points at it`() = runTest {
        dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item(description = "One")),
        )

        assertThrows(SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = $clientId")
        }

        assertThat(count(database, "SELECT COUNT(*) FROM clients")).isEqualTo(1)
        assertThat(count(database, "SELECT COUNT(*) FROM invoices")).isEqualTo(1)
    }

    @Test
    fun `deleting a project orphans the invoice rather than deleting it`() = runTest {
        val projectId = insertProject(database, clientId)
        val id = dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId, projectId = projectId),
            listOf(item(description = "One")),
        )!!

        database.openHelper.writableDatabase.execSQL("DELETE FROM projects WHERE id = $projectId")

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored!!.invoice.projectId).isNull()
    }

    @Test
    fun `a binned invoice is invisible to every read`() = runTest {
        val id = dao.insertWithLines(
            invoice(
                number = "INV-2026-001",
                clientId = clientId,
                notes = "balcony railing",
                totalMinor = 100_000L,
            ),
            listOf(item(description = "One")),
        )!!

        assertThat(dao.softDelete(id, InvoiceFixtures.NOW_MILLIS)).isEqualTo(1)

        assertThat(dao.observeAll().first()).isEmpty()
        assertThat(dao.observeForClient(clientId).first()).isEmpty()
        assertThat(dao.observeUnpaid(InvoiceStatus.VOID).first()).isEmpty()
        assertThat(dao.observeOverdue(InvoiceFixtures.DUE.toEpochDay() + 1, InvoiceStatus.VOID).first()).isEmpty()
        assertThat(dao.findById(id)).isNull()
        assertThat(dao.findByNumber("INV-2026-001")).isNull()
        assertThat(dao.search("%balcony%")).isEmpty()
    }

    @Test
    fun `restore brings a binned invoice back`() = runTest {
        val id = dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item(description = "One")),
        )!!
        dao.softDelete(id, InvoiceFixtures.NOW_MILLIS)

        assertThat(dao.restore(id, InvoiceFixtures.NOW_MILLIS + 1)).isEqualTo(1)

        assertThat(dao.findById(id)).isNotNull()
        assertThat(dao.observeAll().first()).hasSize(1)
    }

    @Test
    fun `unpaid lists what is still owed and leaves out voided and settled bills`() = runTest {
        dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId, totalMinor = 100_000L),
            listOf(item(description = "Owed")),
        )
        dao.insertWithLines(
            invoice(number = "INV-2026-002", clientId = clientId, totalMinor = 100_000L, amountPaidMinor = 100_000L),
            listOf(item(description = "Settled")),
        )
        dao.insertWithLines(
            invoice(
                number = "INV-2026-003",
                clientId = clientId,
                status = InvoiceStatus.VOID,
                totalMinor = 100_000L,
            ),
            listOf(item(description = "Voided")),
        )

        val unpaid = dao.observeUnpaid(InvoiceStatus.VOID).first()

        assertThat(unpaid.map { it.invoice.number }).containsExactly("INV-2026-001")
    }

    @Test
    fun `overdue is judged against the day the caller passes in`() = runTest {
        dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId, totalMinor = 100_000L),
            listOf(item(description = "Owed")),
        )

        val dueDay = InvoiceFixtures.DUE.toEpochDay()
        assertThat(dao.observeOverdue(dueDay, InvoiceStatus.VOID).first()).isEmpty()
        assertThat(dao.observeOverdue(dueDay + 1, InvoiceStatus.VOID).first()).hasSize(1)
    }

    @Test
    fun `an update replaces the lines and leaves the paid amount alone`() = runTest {
        val id = dao.insertWithLines(
            invoice(
                number = "INV-2026-001",
                clientId = clientId,
                subtotalMinor = 100_000L,
                totalMinor = 100_000L,
                amountPaidMinor = 40_000L,
            ),
            listOf(item(description = "Original", unitPriceMinor = 100_000L, lineTotalMinor = 100_000L)),
        )!!
        val stored = dao.findById(id)!!.invoice

        val changed = dao.updateWithLines(
            stored.copy(subtotalMinor = 250_000L, totalMinor = 250_000L, amountPaidMinor = 0L),
            listOf(
                item(description = "First", unitPriceMinor = 150_000L, lineTotalMinor = 150_000L),
                item(description = "Second", unitPriceMinor = 100_000L, lineTotalMinor = 100_000L),
            ),
        )

        assertThat(changed).isEqualTo(1)
        val after = dao.findById(id)!!
        assertThat(after.items.map { it.description }).containsExactly("First", "Second")
        assertThat(after.items.map { it.position }).containsExactly(0, 1).inOrder()
        assertThat(after.invoice.amountPaidMinor).isEqualTo(40_000L)
        assertThat(count(database, "SELECT COUNT(*) FROM invoice_items")).isEqualTo(2)
    }

    @Test
    fun `an update cannot take a number another invoice is using`() = runTest {
        dao.insertWithLines(invoice(number = "INV-2026-001", clientId = clientId), listOf(item("One")))
        val secondId = dao.insertWithLines(
            invoice(number = "INV-2026-002", clientId = clientId),
            listOf(item("Two")),
        )!!
        val second = dao.findById(secondId)!!.invoice

        val refused = dao.updateWithLines(second.copy(number = "INV-2026-001"), listOf(item("Two")))

        assertThat(refused).isNull()
        assertThat(dao.findById(secondId)!!.invoice.number).isEqualTo("INV-2026-002")
    }

    @Test
    fun `a binned invoice cannot be edited back into life`() = runTest {
        val id = dao.insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item("One")),
        )!!
        dao.softDelete(id, InvoiceFixtures.NOW_MILLIS)
        val binned = InvoiceFixtures.invoice(number = "INV-2026-001", clientId = clientId).copy(id = id)

        assertThat(dao.updateWithLines(binned, listOf(item("One")))).isEqualTo(0)
        assertThat(dao.findById(id)).isNull()
    }

    @Test
    fun `search matches the number and the notes`() = runTest {
        dao.insertWithLines(
            invoice(number = "INV-2026-014", clientId = clientId, notes = "balcony railing"),
            listOf(item("One")),
        )
        dao.insertWithLines(
            invoice(number = "INV-2026-015", clientId = clientId, notes = "kitchen"),
            listOf(item("Two")),
        )

        assertThat(dao.search("%014%").map { it.invoice.number }).containsExactly("INV-2026-014")
        assertThat(dao.search("%railing%").map { it.invoice.number }).containsExactly("INV-2026-014")
        assertThat(dao.search("%").map { it.invoice.number })
            .containsExactly("INV-2026-014", "INV-2026-015")
    }

    private fun nextNumber(prefix: String): (String?) -> String = { highest ->
        val previous = highest?.substringAfterLast('-')?.toIntOrNull() ?: 0
        prefix + (previous + 1).toString().padStart(3, '0')
    }
}
