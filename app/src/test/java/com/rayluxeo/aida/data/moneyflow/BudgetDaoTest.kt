package com.rayluxeo.aida.data.moneyflow

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.BudgetDao
import com.rayluxeo.aida.data.db.entity.BudgetEntity
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Both ends of a budget's period are inclusive. Getting that wrong loses a day's spending at one
 * end of every month, which is exactly the sort of error nobody notices until the total is used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BudgetDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: BudgetDao

    @Before
    fun openDatabase() {
        database = openMoneyFlowDatabase()
        dao = database.budgetDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a limit round trips through the database as whole minor units`() = runTest {
        val id = dao.insert(budget(name = "March software", limit = Money.of("1234.56")))

        val stored = dao.findById(id)!!

        assertThat(stored.limitMinor).isEqualTo(123_456L)
        assertThat(MoneyAmount.fromMinorUnits(stored.limitMinor, stored.currencyCode))
            .isEqualTo(Money.of("1234.56"))
    }

    @Test
    fun `a budget with no category is stored as covering every category`() = runTest {
        val id = dao.insert(budget(name = "Everything", category = null))

        assertThat(dao.findById(id)!!.category).isNull()
    }

    @Test
    fun `a period covers both of its own end days`() = runTest {
        val id = dao.insert(
            budget(
                name = "March",
                periodStart = LocalDate.of(2026, 3, 1),
                periodEnd = LocalDate.of(2026, 3, 31),
            ),
        )

        assertThat(dao.listCovering(LocalDate.of(2026, 3, 1).toEpochDay()).map { it.id })
            .containsExactly(id)
        assertThat(dao.listCovering(LocalDate.of(2026, 3, 31).toEpochDay()).map { it.id })
            .containsExactly(id)
        assertThat(dao.listCovering(LocalDate.of(2026, 2, 28).toEpochDay())).isEmpty()
        assertThat(dao.listCovering(LocalDate.of(2026, 4, 1).toEpochDay())).isEmpty()
    }

    @Test
    fun `a binned budget disappears from every read`() = runTest {
        val kept = dao.insert(budget(name = "March"))
        val binned = dao.insert(budget(name = "Mistake"))

        assertThat(dao.softDelete(binned, atEpochMillis = NOW)).isEqualTo(1)

        assertThat(dao.observeAll().first().map { it.id }).containsExactly(kept)
        assertThat(dao.observeCovering(TODAY.toEpochDay()).first().map { it.id }).containsExactly(kept)
        assertThat(dao.listCovering(TODAY.toEpochDay()).map { it.id }).containsExactly(kept)
    }

    @Test
    fun `restoring brings back the same row`() = runTest {
        val id = dao.insert(budget(name = "March"))
        dao.softDelete(id, atEpochMillis = NOW)

        assertThat(dao.restore(id, atEpochMillis = NOW)).isEqualTo(1)
        assertThat(dao.observeAll().first().map { it.id }).containsExactly(id)
    }

    @Test
    fun `an update replaces the row rather than adding one`() = runTest {
        val id = dao.insert(budget(name = "March", limit = Money.of("5000.00")))
        val stored = dao.findById(id)!!

        assertThat(dao.update(stored.copy(limitMinor = 600_000L))).isEqualTo(1)

        assertThat(dao.observeAll().first()).hasSize(1)
        assertThat(dao.findById(id)!!.limitMinor).isEqualTo(600_000L)
    }

    private fun budget(
        name: String,
        category: ExpenseCategory? = ExpenseCategory.SOFTWARE,
        limit: Money = Money.of("5000.00"),
        periodStart: LocalDate = LocalDate.of(2026, 3, 1),
        periodEnd: LocalDate = LocalDate.of(2026, 3, 31),
    ): BudgetEntity = BudgetEntity(
        name = name,
        category = category,
        periodStartEpochDay = periodStart.toEpochDay(),
        periodEndEpochDay = periodEnd.toEpochDay(),
        limitMinor = MoneyAmount.toMinorUnits(limit),
        currencyCode = limit.currency.currencyCode,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = NOW,
        updatedAtEpochMillis = NOW,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 5)
        val NOW: Long = TODAY.atMillis(hour = 11)
    }
}
