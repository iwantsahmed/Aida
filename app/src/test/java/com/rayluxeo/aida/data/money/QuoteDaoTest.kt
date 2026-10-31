package com.rayluxeo.aida.data.money

import android.app.Application
import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.QuoteDao
import com.rayluxeo.aida.data.money.InvoiceFixtures.count
import com.rayluxeo.aida.data.money.InvoiceFixtures.insertClient
import com.rayluxeo.aida.data.money.InvoiceFixtures.insertProject
import com.rayluxeo.aida.data.money.InvoiceFixtures.invoice
import com.rayluxeo.aida.data.money.InvoiceFixtures.item
import com.rayluxeo.aida.data.money.InvoiceFixtures.quote
import com.rayluxeo.aida.data.money.InvoiceFixtures.quoteItem
import com.rayluxeo.aida.domain.model.QuoteStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Foreign keys are on; see [InvoiceDaoTest]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuoteDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: QuoteDao
    private var clientId = 0L

    @Before
    fun openDatabase() {
        database = InvoiceFixtures.openDatabase()
        dao = database.quoteDao()
        clientId = insertClient(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a quote and its lines are written together`() = runTest {
        val id = dao.insertWithLines(
            quote(number = "QUO-2026-001", clientId = clientId, subtotalMinor = 308_640L),
            listOf(
                quoteItem(
                    description = "Design, 2.5 days",
                    quantityMilli = 2_500L,
                    unitPriceMinor = 123_456L,
                    taxRateBasisPoints = 1_500,
                    lineTotalMinor = 308_640L,
                ),
            ),
        )!!

        val stored = dao.findById(id)!!
        assertThat(stored.quote.number).isEqualTo("QUO-2026-001")
        assertThat(stored.items.single().quoteId).isEqualTo(id)
        assertThat(stored.items.single().lineTotalMinor).isEqualTo(308_640L)
    }

    @Test
    fun `a duplicate number is refused and nothing is written`() = runTest {
        dao.insertWithLines(quote(number = "QUO-2026-001", clientId = clientId), listOf(quoteItem("One")))

        val second = dao.insertWithLines(
            quote(number = "QUO-2026-001", clientId = clientId),
            listOf(quoteItem("Two")),
        )

        assertThat(second).isNull()
        assertThat(count(database, "SELECT COUNT(*) FROM quotes")).isEqualTo(1)
        assertThat(count(database, "SELECT COUNT(*) FROM quote_items")).isEqualTo(1)
    }

    @Test
    fun `deleting a quote takes its lines and leaves the client standing`() = runTest {
        val id = dao.insertWithLines(
            quote(number = "QUO-2026-001", clientId = clientId),
            listOf(quoteItem("One"), quoteItem("Two", position = 1)),
        )!!

        assertThat(dao.deleteById(id)).isEqualTo(1)

        assertThat(count(database, "SELECT COUNT(*) FROM quote_items")).isEqualTo(0)
        assertThat(count(database, "SELECT COUNT(*) FROM clients")).isEqualTo(1)
    }

    @Test
    fun `a client cannot be deleted while a quote points at it`() = runTest {
        dao.insertWithLines(quote(number = "QUO-2026-001", clientId = clientId), listOf(quoteItem("One")))

        assertThrows(SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = $clientId")
        }

        assertThat(count(database, "SELECT COUNT(*) FROM quotes")).isEqualTo(1)
    }

    @Test
    fun `deleting a project orphans the quote rather than deleting it`() = runTest {
        val projectId = insertProject(database, clientId)
        val id = dao.insertWithLines(
            quote(number = "QUO-2026-001", clientId = clientId, projectId = projectId),
            listOf(quoteItem("One")),
        )!!

        database.openHelper.writableDatabase.execSQL("DELETE FROM projects WHERE id = $projectId")

        assertThat(dao.findById(id)!!.quote.projectId).isNull()
    }

    @Test
    fun `voiding the invoice a quote became leaves the quote standing`() = runTest {
        val invoiceId = database.invoiceDao().insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item("One")),
        )!!
        val quoteId = dao.insertWithLines(
            quote(number = "QUO-2026-001", clientId = clientId),
            listOf(quoteItem("One")),
        )!!
        dao.recordConversion(
            id = quoteId,
            invoiceId = invoiceId,
            status = QuoteStatus.ACCEPTED,
            atEpochMillis = InvoiceFixtures.NOW_MILLIS,
        )

        database.invoiceDao().deleteById(invoiceId)

        val stored = dao.findById(quoteId)
        assertThat(stored).isNotNull()
        assertThat(stored!!.quote.convertedInvoiceId).isNull()
        assertThat(stored.quote.status).isEqualTo(QuoteStatus.ACCEPTED)
    }

    @Test
    fun `an accepted quote records which invoice it became`() = runTest {
        val invoiceId = database.invoiceDao().insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item("One")),
        )!!
        val quoteId = dao.insertWithLines(
            quote(number = "QUO-2026-001", clientId = clientId),
            listOf(quoteItem("One")),
        )!!

        val changed = dao.recordConversion(
            id = quoteId,
            invoiceId = invoiceId,
            status = QuoteStatus.ACCEPTED,
            atEpochMillis = InvoiceFixtures.NOW_MILLIS,
        )

        assertThat(changed).isEqualTo(1)
        val stored = dao.findById(quoteId)!!.quote
        assertThat(stored.convertedInvoiceId).isEqualTo(invoiceId)
        assertThat(stored.status).isEqualTo(QuoteStatus.ACCEPTED)
    }

    @Test
    fun `an update cannot undo a conversion`() = runTest {
        val invoiceId = database.invoiceDao().insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item("One")),
        )!!
        val quoteId = dao.insertWithLines(
            quote(number = "QUO-2026-001", clientId = clientId),
            listOf(quoteItem("One")),
        )!!
        dao.recordConversion(quoteId, invoiceId, QuoteStatus.ACCEPTED, InvoiceFixtures.NOW_MILLIS)
        val stored = dao.findById(quoteId)!!.quote

        dao.updateWithLines(stored.copy(convertedInvoiceId = null), listOf(quoteItem("Revised")))

        assertThat(dao.findById(quoteId)!!.quote.convertedInvoiceId).isEqualTo(invoiceId)
    }

    @Test
    fun `open quotes leave out the converted and the binned`() = runTest {
        val invoiceId = database.invoiceDao().insertWithLines(
            invoice(number = "INV-2026-001", clientId = clientId),
            listOf(item("One")),
        )!!
        dao.insertWithLines(quote(number = "QUO-2026-001", clientId = clientId), listOf(quoteItem("Draft")))
        dao.insertWithLines(
            quote(number = "QUO-2026-002", clientId = clientId, status = QuoteStatus.SENT),
            listOf(quoteItem("Sent")),
        )
        dao.insertWithLines(
            quote(number = "QUO-2026-003", clientId = clientId, status = QuoteStatus.DECLINED),
            listOf(quoteItem("Declined")),
        )
        val convertedId = dao.insertWithLines(
            quote(number = "QUO-2026-004", clientId = clientId, status = QuoteStatus.SENT),
            listOf(quoteItem("Converted")),
        )!!
        dao.recordConversion(convertedId, invoiceId, QuoteStatus.ACCEPTED, InvoiceFixtures.NOW_MILLIS)
        val binnedId = dao.insertWithLines(
            quote(number = "QUO-2026-005", clientId = clientId, status = QuoteStatus.SENT),
            listOf(quoteItem("Binned")),
        )!!
        dao.softDelete(binnedId, InvoiceFixtures.NOW_MILLIS)

        val open = dao.observeOpen(drafted = QuoteStatus.DRAFT, sent = QuoteStatus.SENT).first()

        assertThat(open.map { it.quote.number }).containsExactly("QUO-2026-001", "QUO-2026-002")
    }

    @Test
    fun `a binned quote is invisible to every read`() = runTest {
        val id = dao.insertWithLines(
            quote(number = "QUO-2026-001", clientId = clientId, notes = "balcony railing"),
            listOf(quoteItem("One")),
        )!!

        assertThat(dao.softDelete(id, InvoiceFixtures.NOW_MILLIS)).isEqualTo(1)

        assertThat(dao.observeAll().first()).isEmpty()
        assertThat(dao.observeForClient(clientId).first()).isEmpty()
        assertThat(dao.findById(id)).isNull()
        assertThat(dao.findByNumber("QUO-2026-001")).isNull()
        assertThat(dao.search("%railing%")).isEmpty()
        assertThat(dao.restore(id, InvoiceFixtures.NOW_MILLIS + 1)).isEqualTo(1)
        assertThat(dao.findById(id)).isNotNull()
    }
}
