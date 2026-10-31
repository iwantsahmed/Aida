package com.rayluxeo.aida.data.moneyflow

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.BudgetRepositoryImpl
import com.rayluxeo.aida.data.repository.CalculationRepositoryImpl
import com.rayluxeo.aida.data.repository.ExpenseRepositoryImpl
import com.rayluxeo.aida.data.repository.IncomeRepositoryImpl
import com.rayluxeo.aida.data.repository.PaymentRepositoryImpl
import com.rayluxeo.aida.data.repository.SubscriptionRepositoryImpl
import com.rayluxeo.aida.domain.model.Budget
import com.rayluxeo.aida.domain.model.CalculationRecord
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.usecase.RecordPaymentUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The layer Abu's screens actually talk to: mappers, repositories and the one use case with real
 * rules in it. Everything monetary here is asserted as a [Money], not as a count of paisa, so a
 * mapper that lost a decimal place on the way out would fail even though the row was correct.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MoneyFlowRepositoryTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))
    private val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())

    @Before
    fun openDatabase() {
        database = openMoneyFlowDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an amount round trips through the repository exactly`() = runTest {
        val payments = paymentRepository()

        val recorded = payments.record(payment(amount = Money.of("1234.56")))
        val id = (recorded as AppResult.Success).value.id

        val loaded = payments.byId(id)
        assertThat((loaded as AppResult.Success).value.amount).isEqualTo(Money.of("1234.56"))
        assertThat(loaded.value.amount.amount.toPlainString()).isEqualTo("1234.56")
    }

    @Test
    fun `an amount with a trailing zero keeps its scale through the repository`() = runTest {
        val payments = paymentRepository()

        val recorded = payments.record(payment(amount = Money.of("1200.00")))
        val id = (recorded as AppResult.Success).value.id

        val loaded = payments.byId(id) as AppResult.Success
        assertThat(loaded.value.amount).isEqualTo(Money.of("1200.00"))
        assertThat(loaded.value.amount.amount.toPlainString()).isEqualTo("1200.00")
    }

    @Test
    fun `a partial payment leaves the invoice partially paid`() = runTest {
        val clientId = database.insertClient()
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L)

        val result = recordPayment()(amount = Money.of("400.00"), invoiceId = invoiceId)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(database.invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.PARTIALLY_PAID.name)
        assertThat(database.invoiceAmountPaidMinor(invoiceId)).isEqualTo(40_000L)
    }

    @Test
    fun `a payment for the exact total leaves the invoice paid`() = runTest {
        val clientId = database.insertClient()
        val invoiceId = database.insertInvoice(clientId, totalMinor = 123_456L)

        val result = recordPayment()(amount = Money.of("1234.56"), invoiceId = invoiceId)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat(database.invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.PAID.name)
        assertThat(database.invoiceAmountPaidMinor(invoiceId)).isEqualTo(123_456L)
    }

    @Test
    fun `a payment in another currency is refused rather than converted`() = runTest {
        val clientId = database.insertClient()
        val invoiceId = database.insertInvoice(clientId, currencyCode = "BDT", totalMinor = 100_000L)

        val result = recordPayment()(
            amount = Money.of("500.00", Currency.getInstance("USD")),
            invoiceId = invoiceId,
        )

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(database.paymentCount()).isEqualTo(0L)
        assertThat(database.invoiceStatus(invoiceId)).isEqualTo(InvoiceStatus.SENT.name)
    }

    @Test
    fun `a payment larger than what is outstanding is refused`() = runTest {
        val clientId = database.insertClient()
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L)

        val result = recordPayment()(amount = Money.of("1500.00"), invoiceId = invoiceId)

        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(database.paymentCount()).isEqualTo(0L)
    }

    @Test
    fun `a payment of nothing is refused before it reaches storage`() = runTest {
        val result = recordPayment()(amount = Money.zero())

        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(database.paymentCount()).isEqualTo(0L)
    }

    @Test
    fun `a payment against an invoice that is not there reports not found`() = runTest {
        val result = recordPayment()(amount = Money.of("100.00"), invoiceId = 404L)

        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a payment with no invoice is recorded on its own`() = runTest {
        val result = recordPayment()(amount = Money.of("250.00"), method = PaymentMethod.CASH)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).value.id).isGreaterThan(0L)
        assertThat(result.value.receivedAt).isEqualTo(clock.now())
        assertThat(database.paymentCount()).isEqualTo(1L)
    }

    @Test
    fun `deleting an invoice orphans the payment rather than losing the money`() = runTest {
        val payments = paymentRepository()
        val clientId = database.insertClient()
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L)
        val recorded = recordPayment()(amount = Money.of("400.00"), invoiceId = invoiceId)
        val paymentId = (recorded as AppResult.Success).value.id

        database.deleteInvoice(invoiceId)

        val loaded = payments.byId(paymentId) as AppResult.Success
        assertThat(loaded.value.invoiceId).isNull()
        assertThat(loaded.value.amount).isEqualTo(Money.of("400.00"))
    }

    @Test
    fun `an invoice reads back every payment against it, oldest first`() = runTest {
        val payments = paymentRepository()
        val clientId = database.insertClient()
        val invoiceId = database.insertInvoice(clientId, totalMinor = 100_000L)
        recordPayment()(amount = Money.of("600.00"), invoiceId = invoiceId)
        recordPayment()(amount = Money.of("400.00"), invoiceId = invoiceId)

        val listed = payments.forInvoice(invoiceId) as AppResult.Success
        val observed = payments.observeForInvoice(invoiceId).first()

        assertThat(listed.value.map { it.amount })
            .containsExactly(Money.of("600.00"), Money.of("400.00"))
            .inOrder()
        assertThat(observed.map { it.amount }).isEqualTo(listed.value.map { it.amount })
    }

    @Test
    fun `the totals for a day add up exactly on both sides of the ledger`() = runTest {
        val payments = paymentRepository()
        val expenses = expenseRepository()
        recordPayment()(amount = Money.of("1234.56"))
        recordPayment()(amount = Money.of("0.44"))
        expenses.create(expense("Figma", Money.of("99.99")))
        expenses.create(expense("Bus fare", Money.of("0.01"), category = ExpenseCategory.TRAVEL))

        val received = payments.totalReceivedBetween(WINDOW_START, WINDOW_END) as AppResult.Success
        val spent = expenses.totalSpentBetween(WINDOW_START, WINDOW_END) as AppResult.Success
        val spentOnTravel = expenses.totalSpentBetween(
            from = WINDOW_START,
            to = WINDOW_END,
            category = ExpenseCategory.TRAVEL,
        ) as AppResult.Success

        assertThat(received.value).isEqualTo(Money.of("1235.00"))
        assertThat(spent.value).isEqualTo(Money.of("100.00"))
        assertThat(spentOnTravel.value).isEqualTo(Money.of("0.01"))
    }

    @Test
    fun `budget progress sums the window and ignores binned expenses`() = runTest {
        val expenses = expenseRepository()
        val budgets = budgetRepository()
        expenses.create(expense("Figma", Money.of("1234.56")))
        val mistake = expenses.create(expense("Duplicate", Money.of("900.00")))
        expenses.create(expense("Bus fare", Money.of("40.00"), category = ExpenseCategory.TRAVEL))
        expenses.delete((mistake as AppResult.Success).value.id)

        val created = budgets.create(budget(limit = Money.of("5000.00"))) as AppResult.Success
        val progress = budgets.progress(created.value.id) as AppResult.Success

        assertThat(progress.value.spent).isEqualTo(Money.of("1234.56"))
        assertThat(progress.value.remaining).isEqualTo(Money.of("3765.44"))
        assertThat(progress.value.isOverspent).isFalse()
    }

    @Test
    fun `budget progress reports an overspend rather than clamping at zero`() = runTest {
        val expenses = expenseRepository()
        val budgets = budgetRepository()
        expenses.create(expense("Camera", Money.of("6500.00")))

        val created = budgets.create(budget(limit = Money.of("5000.00"))) as AppResult.Success
        val progress = budgets.progress(created.value.id) as AppResult.Success

        assertThat(progress.value.spent).isEqualTo(Money.of("6500.00"))
        assertThat(progress.value.remaining).isEqualTo(Money.of("-1500.00"))
        assertThat(progress.value.isOverspent).isTrue()
    }

    @Test
    fun `budget progress leaves out spending on either side of the period`() = runTest {
        val expenses = expenseRepository()
        val budgets = budgetRepository()
        expenses.create(expense("Before", Money.of("100.00"), spentOn = LocalDate.of(2026, 2, 28)))
        expenses.create(expense("First day", Money.of("200.00"), spentOn = LocalDate.of(2026, 3, 1)))
        expenses.create(expense("Last day", Money.of("300.00"), spentOn = LocalDate.of(2026, 3, 31)))
        expenses.create(expense("After", Money.of("400.00"), spentOn = LocalDate.of(2026, 4, 1)))

        val created = budgets.create(budget(limit = Money.of("5000.00"))) as AppResult.Success
        val progress = budgets.progress(created.value.id) as AppResult.Success

        assertThat(progress.value.spent).isEqualTo(Money.of("500.00"))
    }

    @Test
    fun `a binned budget is no longer reported on`() = runTest {
        val budgets = budgetRepository()
        val created = budgets.create(budget(limit = Money.of("5000.00"))) as AppResult.Success

        budgets.delete(created.value.id)

        val progress = budgets.progress(created.value.id)
        assertThat((progress as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)

        val active = budgets.activeProgress()
        assertThat((active as AppResult.Success).value).isEmpty()
    }

    @Test
    fun `income totals add up exactly and skip what a payment already covers`() = runTest {
        val income = incomeRepository()
        val recorded = recordPayment()(amount = Money.of("700.00")) as AppResult.Success
        income.create(entry("Invoice INV-001", Money.of("700.00"), paymentId = recorded.value.id))
        income.create(entry("Cash job", Money.of("1234.56")))

        val all = income.totalReceivedBetween(WINDOW_START, WINDOW_END) as AppResult.Success
        val uninvoiced = income.totalReceivedBetween(
            from = WINDOW_START,
            to = WINDOW_END,
            excludeEntriesFromPayments = true,
        ) as AppResult.Success

        assertThat(all.value).isEqualTo(Money.of("1934.56"))
        assertThat(uninvoiced.value).isEqualTo(Money.of("1234.56"))
    }

    @Test
    fun `a monthly subscription charging on the 31st of January lands on the last day of February`() =
        runTest {
            clock.moveTo(LocalDateTime.of(2026, 2, 5, 9, 0))
            val subscriptions = subscriptionRepository()
            val created = subscriptions.create(
                subscription(nextChargeOn = LocalDate.of(2026, 1, 31)),
            ) as AppResult.Success

            val advanced = subscriptions.advanceOverdueCharges()

            assertThat((advanced as AppResult.Success).value).isEqualTo(1)
            val reloaded = subscriptions.byId(created.value.id) as AppResult.Success
            assertThat(reloaded.value.nextChargeOn).isEqualTo(LocalDate.of(2026, 2, 28))
        }

    @Test
    fun `a subscription charging today is not swept past the day it is due`() = runTest {
        clock.moveTo(LocalDateTime.of(2026, 2, 28, 9, 0))
        val subscriptions = subscriptionRepository()
        val created = subscriptions.create(
            subscription(nextChargeOn = LocalDate.of(2026, 2, 28)),
        ) as AppResult.Success

        assertThat((subscriptions.advanceOverdueCharges() as AppResult.Success).value).isEqualTo(0)
        val reloaded = subscriptions.byId(created.value.id) as AppResult.Success
        assertThat(reloaded.value.nextChargeOn).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(reloaded.value.amount).isEqualTo(Money.of("1200.00"))
    }

    @Test
    fun `a calculation is kept exactly as Abu was told it`() = runTest {
        val calculations = calculationRepository()

        val recorded = calculations.record(
            CalculationRecord(
                expression = "12450 * 3 + 7.5% of 2400",
                resultText = "37,530.00",
                language = SpokenLanguage.BENGALI,
                createdAt = clock.now(),
            ),
        )

        assertThat(recorded).isInstanceOf(AppResult.Success::class.java)
        val latest = calculations.mostRecent() as AppResult.Success
        assertThat(latest.value?.resultText).isEqualTo("37,530.00")
        assertThat(latest.value?.language).isEqualTo(SpokenLanguage.BENGALI)
    }

    @Test
    fun `an empty calculation history answers with nothing rather than failing`() = runTest {
        val latest = calculationRepository().mostRecent()

        assertThat(latest).isInstanceOf(AppResult.Success::class.java)
        assertThat((latest as AppResult.Success).value).isNull()
    }

    private fun paymentRepository() = PaymentRepositoryImpl(database.paymentDao(), clock, dispatchers)

    private fun expenseRepository() = ExpenseRepositoryImpl(database.expenseDao(), clock, dispatchers)

    private fun incomeRepository() = IncomeRepositoryImpl(database.incomeDao(), clock, dispatchers)

    private fun budgetRepository() =
        BudgetRepositoryImpl(database.budgetDao(), database.expenseDao(), clock, dispatchers)

    private fun subscriptionRepository() =
        SubscriptionRepositoryImpl(database.subscriptionDao(), clock, dispatchers)

    private fun calculationRepository() =
        CalculationRepositoryImpl(database.calculationDao(), dispatchers)

    private fun recordPayment() = RecordPaymentUseCase(paymentRepository(), FakeTestActivityLog(), clock)

    private fun payment(amount: Money, invoiceId: Long? = null): Payment = Payment(
        invoiceId = invoiceId,
        amount = amount,
        method = PaymentMethod.BANK_TRANSFER,
        receivedAt = clock.now(),
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun expense(
        description: String,
        amount: Money,
        category: ExpenseCategory = ExpenseCategory.SOFTWARE,
        spentOn: LocalDate = LocalDate.of(2026, 3, 5),
    ): Expense = Expense(
        description = description,
        category = category,
        amount = amount,
        spentAt = Instant.ofEpochMilli(spentOn.atMillis(hour = 11)),
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun budget(limit: Money): Budget = Budget(
        name = "March software",
        category = ExpenseCategory.SOFTWARE,
        periodStart = LocalDate.of(2026, 3, 1),
        periodEnd = LocalDate.of(2026, 3, 31),
        limit = limit,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun entry(source: String, amount: Money, paymentId: Long? = null): IncomeEntry =
        IncomeEntry(
            source = source,
            amount = amount,
            receivedAt = clock.now(),
            paymentId = paymentId,
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )

    private fun subscription(nextChargeOn: LocalDate): Subscription = Subscription(
        name = "Hosting",
        amount = Money.of("1200.00"),
        cycle = SubscriptionCycle.MONTHLY,
        nextChargeOn = nextChargeOn,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private companion object {
        val WINDOW_START: Instant =
            Instant.ofEpochMilli(LocalDate.of(2026, 3, 5).atStartOfDayMillis())
        val WINDOW_END: Instant =
            Instant.ofEpochMilli(LocalDate.of(2026, 3, 6).atStartOfDayMillis())
    }
}

private class FakeTestActivityLog : com.rayluxeo.aida.domain.repository.ActivityLogRepository {
    override fun observeRecent(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeByCategory(category: com.rayluxeo.aida.domain.model.ActivityCategory, limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeForSubject(subject: com.rayluxeo.aida.domain.model.ActivitySubject) = kotlinx.coroutines.flow.flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun between(from: Instant, to: Instant) = AppResult.Success(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun record(entry: com.rayluxeo.aida.domain.model.ActivityEntry) = AppResult.Success(entry)
}
