package com.rayluxeo.aida.data.moneyflow

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.ExpenseDao
import com.rayluxeo.aida.data.db.entity.ExpenseEntity
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import java.time.LocalDate
import java.util.Currency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The budget rollup is the reason most of this exists. A binned expense that still counts
 * against a ceiling is the quietest way for a budget to be wrong, so every read and every sum is
 * checked against a row that has been sent to the bin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExpenseDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: ExpenseDao

    @Before
    fun openDatabase() {
        database = openMoneyFlowDatabase()
        dao = database.expenseDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an expense round trips through the database as whole minor units`() = runTest {
        val id = dao.insert(expense(description = "Figma", amount = Money.of("1234.56")))

        val stored = dao.findById(id)!!

        assertThat(stored.amountMinor).isEqualTo(123_456L)
        assertThat(MoneyAmount.fromMinorUnits(stored.amountMinor, stored.currencyCode))
            .isEqualTo(Money.of("1234.56"))
    }

    @Test
    fun `an update replaces the row rather than adding one`() = runTest {
        val id = dao.insert(expense(description = "Figma", amount = Money.of("1200.00")))
        val stored = dao.findById(id)!!

        val changed = dao.update(stored.copy(description = "Figma annual", amountMinor = 130_000L))

        assertThat(changed).isEqualTo(1)
        assertThat(dao.observeAll().first()).hasSize(1)
        assertThat(dao.findById(id)!!.description).isEqualTo("Figma annual")
    }

    @Test
    fun `a binned expense disappears from every read`() = runTest {
        val kept = dao.insert(expense(description = "Hosting", amount = Money.of("500.00")))
        val binned = dao.insert(expense(description = "Mistake", amount = Money.of("900.00")))

        assertThat(dao.softDelete(binned, atEpochMillis = NOW)).isEqualTo(1)

        assertThat(dao.observeAll().first().map { it.id }).containsExactly(kept)
        assertThat(dao.observeByCategory(ExpenseCategory.SOFTWARE).first().map { it.id })
            .containsExactly(kept)
        assertThat(dao.observeBetween(WINDOW_START, WINDOW_END).first().map { it.id })
            .containsExactly(kept)
        assertThat(dao.listBetween(WINDOW_START, WINDOW_END).map { it.id }).containsExactly(kept)
    }

    @Test
    fun `binning an expense takes it out of the rollup`() = runTest {
        dao.insert(expense(description = "Hosting", amount = Money.of("500.00")))
        val binned = dao.insert(expense(description = "Mistake", amount = Money.of("900.00")))

        assertThat(dao.sumSpentMinor(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(140_000L)

        dao.softDelete(binned, atEpochMillis = NOW)

        assertThat(dao.sumSpentMinor(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(50_000L)
    }

    @Test
    fun `binning the same expense twice reports nothing changed the second time`() = runTest {
        val id = dao.insert(expense(description = "Hosting", amount = Money.of("500.00")))

        assertThat(dao.softDelete(id, atEpochMillis = NOW)).isEqualTo(1)
        assertThat(dao.softDelete(id, atEpochMillis = NOW)).isEqualTo(0)
    }

    @Test
    fun `restoring brings back the same row`() = runTest {
        val id = dao.insert(expense(description = "Hosting", amount = Money.of("500.00")))
        dao.softDelete(id, atEpochMillis = NOW)

        assertThat(dao.restore(id, atEpochMillis = NOW)).isEqualTo(1)

        assertThat(dao.observeAll().first().map { it.id }).containsExactly(id)
        assertThat(dao.findById(id)!!.deletedAtEpochMillis).isNull()
    }

    @Test
    fun `the rollup sums many rows without drifting by a paisa`() = runTest {
        dao.insert(expense(description = "One", amount = Money.of("1234.56")))
        dao.insert(expense(description = "Two", amount = Money.of("0.01")))
        dao.insert(expense(description = "Three", amount = Money.of("99.99")))
        dao.insert(expense(description = "Four", amount = Money.of("0.44")))

        val total = dao.sumSpentMinor(WINDOW_START, WINDOW_END, "BDT")

        assertThat(total).isEqualTo(133_500L)
        assertThat(MoneyAmount.fromMinorUnits(total, "BDT")).isEqualTo(Money.of("1335.00"))
    }

    @Test
    fun `the rollup counts only the category it was asked about`() = runTest {
        dao.insert(expense(description = "Figma", amount = Money.of("500.00"), category = ExpenseCategory.SOFTWARE))
        dao.insert(expense(description = "Bus", amount = Money.of("40.00"), category = ExpenseCategory.TRAVEL))

        assertThat(dao.sumSpentMinorInCategory(WINDOW_START, WINDOW_END, "BDT", ExpenseCategory.SOFTWARE))
            .isEqualTo(50_000L)
        assertThat(dao.sumSpentMinorInCategory(WINDOW_START, WINDOW_END, "BDT", ExpenseCategory.TRAVEL))
            .isEqualTo(4_000L)
        assertThat(dao.sumSpentMinor(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(54_000L)
    }

    @Test
    fun `the rollup never adds one currency to another`() = runTest {
        dao.insert(expense(description = "Local", amount = Money.of("500.00")))
        dao.insert(
            expense(description = "Abroad", amount = Money.of("500.00", Currency.getInstance("USD"))),
        )

        assertThat(dao.sumSpentMinor(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(50_000L)
        assertThat(dao.sumSpentMinor(WINDOW_START, WINDOW_END, "USD")).isEqualTo(50_000L)
    }

    @Test
    fun `the rollup leaves out anything spent outside the window`() = runTest {
        dao.insert(expense(description = "Inside", amount = Money.of("500.00")))
        dao.insert(expense(description = "Outside", amount = Money.of("900.00"), spentAt = OUTSIDE_WINDOW))

        assertThat(dao.sumSpentMinor(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(50_000L)
    }

    @Test
    fun `an empty window rolls up to zero rather than to nothing`() = runTest {
        assertThat(dao.sumSpentMinor(WINDOW_START, WINDOW_END, "BDT")).isEqualTo(0L)
    }

    @Test
    fun `deleting a client leaves the expense behind with no client`() = runTest {
        val clientId = database.insertClient()
        val id = dao.insert(expense(description = "Site hosting", amount = Money.of("500.00"), clientId = clientId))

        database.deleteClient(clientId)

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored!!.clientId).isNull()
        assertThat(stored.amountMinor).isEqualTo(50_000L)
    }

    @Test
    fun `deleting a receipt leaves the expense behind with no receipt`() = runTest {
        val fileId = database.insertFile()
        val id = dao.insert(
            expense(description = "Taxi", amount = Money.of("250.00"), receiptFileId = fileId),
        )

        database.deleteFile(fileId)

        assertThat(dao.findById(id)!!.receiptFileId).isNull()
    }

    @Test
    fun `deleting a project leaves the expense behind with no project`() = runTest {
        val projectId = database.insertProject()
        val id = dao.insert(
            expense(description = "Stock photo", amount = Money.of("120.00"), projectId = projectId),
        )

        assertThat(dao.observeForProject(projectId).first().map { it.id }).containsExactly(id)

        database.openHelper.writableDatabase.execSQL("DELETE FROM projects WHERE id = ?", arrayOf<Any?>(projectId))

        assertThat(dao.findById(id)!!.projectId).isNull()
    }

    private fun expense(
        description: String,
        amount: Money,
        category: ExpenseCategory = ExpenseCategory.SOFTWARE,
        spentAt: Long = NOW,
        clientId: Long? = null,
        projectId: Long? = null,
        receiptFileId: Long? = null,
    ): ExpenseEntity = ExpenseEntity(
        description = description,
        category = category,
        amountMinor = MoneyAmount.toMinorUnits(amount),
        currencyCode = amount.currency.currencyCode,
        spentAtEpochMillis = spentAt,
        clientId = clientId,
        projectId = projectId,
        receiptFileId = receiptFileId,
        isBillable = false,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = spentAt,
        updatedAtEpochMillis = spentAt,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 5)
        val NOW: Long = TODAY.atMillis(hour = 11)
        val WINDOW_START: Long = TODAY.atStartOfDayMillis()
        val WINDOW_END: Long = TODAY.plusDays(1).atStartOfDayMillis()
        val OUTSIDE_WINDOW: Long = TODAY.plusDays(3).atMillis(hour = 11)
    }
}
