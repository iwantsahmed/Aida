package com.rayluxeo.aida.data.moneyflow

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.SubscriptionDao
import com.rayluxeo.aida.data.db.entity.SubscriptionEntity
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.model.SubscriptionCycle
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
 * Which rows the sweep picks up, and that a sweep over several of them is all or nothing.
 * The calendar arithmetic itself is covered on its own in `SubscriptionCycleTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubscriptionDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: SubscriptionDao

    @Before
    fun openDatabase() {
        database = openMoneyFlowDatabase()
        dao = database.subscriptionDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a subscription round trips through the database as whole minor units`() = runTest {
        val id = dao.insert(subscription(name = "Hosting", amount = Money.of("1234.56")))

        val stored = dao.findById(id)!!

        assertThat(stored.amountMinor).isEqualTo(123_456L)
        assertThat(MoneyAmount.fromMinorUnits(stored.amountMinor, stored.currencyCode))
            .isEqualTo(Money.of("1234.56"))
    }

    @Test
    fun `a paused subscription stays out of the active list but keeps its place in the full one`() =
        runTest {
            val live = dao.insert(subscription(name = "Hosting"))
            val paused = dao.insert(subscription(name = "Old CDN", isActive = false))

            assertThat(dao.observeActive().first().map { it.id }).containsExactly(live)
            assertThat(dao.observeAll().first().map { it.id }).containsExactly(live, paused)
        }

    @Test
    fun `a binned subscription disappears from every read`() = runTest {
        val kept = dao.insert(subscription(name = "Hosting"))
        val binned = dao.insert(subscription(name = "Mistake"))

        assertThat(dao.softDelete(binned, atEpochMillis = NOW)).isEqualTo(1)

        assertThat(dao.observeActive().first().map { it.id }).containsExactly(kept)
        assertThat(dao.observeAll().first().map { it.id }).containsExactly(kept)
        assertThat(dao.listDueOnOrBefore(TODAY.toEpochDay()).map { it.id }).containsExactly(kept)
    }

    @Test
    fun `restoring brings back the same row`() = runTest {
        val id = dao.insert(subscription(name = "Hosting"))
        dao.softDelete(id, atEpochMillis = NOW)

        assertThat(dao.restore(id, atEpochMillis = NOW)).isEqualTo(1)
        assertThat(dao.observeAll().first().map { it.id }).containsExactly(id)
    }

    @Test
    fun `the sweep moves only the dates that have already gone by`() = runTest {
        val overdue = dao.insert(subscription(name = "Overdue", nextChargeOn = TODAY.minusDays(3)))
        val dueToday = dao.insert(subscription(name = "Due today", nextChargeOn = TODAY))
        val future = dao.insert(subscription(name = "Later", nextChargeOn = TODAY.plusDays(9)))

        val advanced = dao.advanceOverdue(TODAY.toEpochDay(), NOW) { row ->
            row.nextChargeOnEpochDay + CYCLE_DAYS
        }

        assertThat(advanced).isEqualTo(1)
        assertThat(dao.findById(overdue)!!.nextChargeOnEpochDay)
            .isEqualTo(TODAY.minusDays(3).toEpochDay() + CYCLE_DAYS)
        assertThat(dao.findById(dueToday)!!.nextChargeOnEpochDay).isEqualTo(TODAY.toEpochDay())
        assertThat(dao.findById(future)!!.nextChargeOnEpochDay)
            .isEqualTo(TODAY.plusDays(9).toEpochDay())
    }

    @Test
    fun `the sweep leaves paused and binned subscriptions where they are`() = runTest {
        val paused = dao.insert(
            subscription(name = "Paused", nextChargeOn = TODAY.minusDays(30), isActive = false),
        )
        val binned = dao.insert(subscription(name = "Binned", nextChargeOn = TODAY.minusDays(30)))
        dao.softDelete(binned, atEpochMillis = NOW)

        val advanced = dao.advanceOverdue(TODAY.toEpochDay(), NOW) { row ->
            row.nextChargeOnEpochDay + CYCLE_DAYS
        }

        assertThat(advanced).isEqualTo(0)
        assertThat(dao.findById(paused)!!.nextChargeOnEpochDay)
            .isEqualTo(TODAY.minusDays(30).toEpochDay())
        assertThat(dao.findById(binned)!!.nextChargeOnEpochDay)
            .isEqualTo(TODAY.minusDays(30).toEpochDay())
    }

    @Test
    fun `the sweep moves every overdue row in one pass and reports the count`() = runTest {
        dao.insert(subscription(name = "One", nextChargeOn = TODAY.minusDays(1)))
        dao.insert(subscription(name = "Two", nextChargeOn = TODAY.minusDays(2)))
        dao.insert(subscription(name = "Three", nextChargeOn = TODAY.minusDays(3)))

        val advanced = dao.advanceOverdue(TODAY.toEpochDay(), NOW) { row ->
            row.nextChargeOnEpochDay + CYCLE_DAYS
        }

        assertThat(advanced).isEqualTo(3)
        assertThat(dao.observeActive().first().map { it.nextChargeOnEpochDay })
            .containsNoneOf(
                TODAY.minusDays(1).toEpochDay(),
                TODAY.minusDays(2).toEpochDay(),
            )
    }

    @Test
    fun `a row the arithmetic cannot move is left alone rather than rewritten`() = runTest {
        val id = dao.insert(subscription(name = "Stuck", nextChargeOn = TODAY.minusDays(4)))

        val advanced = dao.advanceOverdue(TODAY.toEpochDay(), NOW) { row -> row.nextChargeOnEpochDay }

        assertThat(advanced).isEqualTo(0)
        assertThat(dao.findById(id)!!.nextChargeOnEpochDay)
            .isEqualTo(TODAY.minusDays(4).toEpochDay())
    }

    @Test
    fun `pausing keeps the next charge date so resuming does not lose it`() = runTest {
        val id = dao.insert(subscription(name = "Hosting", nextChargeOn = TODAY.plusDays(12)))

        assertThat(dao.setActive(id, active = false, atEpochMillis = NOW)).isEqualTo(1)

        val stored = dao.findById(id)!!
        assertThat(stored.isActive).isFalse()
        assertThat(stored.nextChargeOnEpochDay).isEqualTo(TODAY.plusDays(12).toEpochDay())
    }

    @Test
    fun `deleting the reminder leaves the subscription Abu is still paying for`() = runTest {
        val reminderId = database.insertReminder()
        val id = dao.insert(subscription(name = "Hosting", reminderId = reminderId))

        database.deleteReminder(reminderId)

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored!!.reminderId).isNull()
        assertThat(stored.name).isEqualTo("Hosting")
    }

    private fun subscription(
        name: String,
        amount: Money = Money.of("1200.00"),
        cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
        nextChargeOn: LocalDate = TODAY,
        isActive: Boolean = true,
        reminderId: Long? = null,
    ): SubscriptionEntity = SubscriptionEntity(
        name = name,
        vendor = null,
        amountMinor = MoneyAmount.toMinorUnits(amount),
        currencyCode = amount.currency.currencyCode,
        cycle = cycle,
        cycleDays = null,
        nextChargeOnEpochDay = nextChargeOn.toEpochDay(),
        isActive = isActive,
        reminderId = reminderId,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = NOW,
        updatedAtEpochMillis = NOW,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 5)
        val NOW: Long = TODAY.atMillis(hour = 11)
        const val CYCLE_DAYS = 30L
    }
}
