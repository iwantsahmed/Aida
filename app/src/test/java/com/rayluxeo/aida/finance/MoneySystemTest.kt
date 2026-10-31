package com.rayluxeo.aida.finance

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.local.LocalCommandEngine
import com.rayluxeo.aida.ai.nlu.BanglaNumberParser
import com.rayluxeo.aida.ai.nlu.BanglishNormalizer
import com.rayluxeo.aida.ai.nlu.CommandClassifier
import com.rayluxeo.aida.ai.nlu.CommandSlotParser
import com.rayluxeo.aida.ai.nlu.CurrencyExpressionParser
import com.rayluxeo.aida.ai.nlu.EnglishNumberParser
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.ai.nlu.InputNormalizer
import com.rayluxeo.aida.ai.nlu.LanguageHintDetector
import com.rayluxeo.aida.ai.nlu.MiraDateTimeParser
import com.rayluxeo.aida.ai.nlu.TimeExpressionParser
import com.rayluxeo.aida.ai.nlu.TitleExtractor
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.repository.ExchangeRateRepositoryImpl
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.repository.AppSettingRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.IncomeRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.usecase.GetMoneyDashboardUseCase
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * End-to-end verification of Mira Money systems:
 * 1. Command parsing and interpretation of the 5 key prompt phrases.
 * 2. Deterministic Kotlin financial calculations in GetMoneyDashboardUseCase.
 * 3. Offline and manual exchange rate management and conversions.
 */
class MoneySystemTest {

    private val clock = FixedMiraClock(Instant.parse("2026-10-15T12:00:00Z"))
    private val normalizer = InputNormalizer(LanguageHintDetector(), BanglishNormalizer())
    private val calculator = MiraCalculator()
    private val engine = LocalCommandEngine(
        classifier = CommandClassifier(),
        slots = CommandSlotParser(
            dateTime = MiraDateTimeParser(clock),
            times = TimeExpressionParser(clock),
            currency = CurrencyExpressionParser(BanglaNumberParser(), EnglishNumberParser()),
            titles = TitleExtractor(),
        ),
    )

    private suspend fun interpretCommand(raw: String): AppCommand {
        val text = normalizer.normalize(raw, LanguageMode.AUTO)
        val result = engine.interpret(
            CommandRequest(
                raw = text.raw,
                normalized = text.normalized,
                language = text.language,
                script = text.script,
                source = InputSource.TEXT,
            ),
        )
        assertThat(result).isInstanceOf(EngineResult.Interpreted::class.java)
        return (result as EngineResult.Interpreted).command
    }

    // -------------------------------------------------------------------------
    // 1. THE FIVE PROMPT COMMANDS
    // -------------------------------------------------------------------------

    @Test
    fun `command 1 - I spent 6500 taka on Adobe`() = runTest {
        val command = interpretCommand("I spent 6500 taka on Adobe.")
        assertThat(command).isInstanceOf(AppCommand.RecordExpense::class.java)
        val expense = command as AppCommand.RecordExpense
        assertThat(expense.amount).isEqualTo(Money.of("6500"))
        assertThat(expense.description).isEqualTo("Adobe")
        assertThat(expense.category).isEqualTo(ExpenseCategory.SOFTWARE)
    }

    @Test
    fun `command 2 - Ahmed paid 20k`() = runTest {
        val command = interpretCommand("Ahmed paid 20k.")
        assertThat(command).isInstanceOf(AppCommand.RecordPayment::class.java)
        val payment = command as AppCommand.RecordPayment
        assertThat(payment.amount).isEqualTo(Money.of("20000"))
        assertThat(payment.client).isInstanceOf(ClientTarget.ByName::class.java)
        assertThat((payment.client as ClientTarget.ByName).name).isEqualTo("Ahmed")
    }

    @Test
    fun `command 3 - What's 30 percent of 450000`() = runTest {
        val command = interpretCommand("What's 30% of 450000?")
        assertThat(command).isInstanceOf(AppCommand.PerformCalculation::class.java)
        val calc = command as AppCommand.PerformCalculation
        assertThat(calc.expression).isEqualTo("30% of 450000")

        val evaluated = calculator.evaluate(calc.expression)
        assertThat(evaluated).isInstanceOf(AppResult.Success::class.java)
        assertThat((evaluated as AppResult.Success).value).isEqualTo("135000")
    }

    @Test
    fun `command 4 - ei mashe software e koto khoroch hoyeche`() = runTest {
        val command = interpretCommand("এই মাসে software এ কত খরচ হয়েছে?")
        assertThat(command).isInstanceOf(AppCommand.QueryExpenses::class.java)
        val query = command as AppCommand.QueryExpenses
        assertThat(query.category).isEqualTo(ExpenseCategory.SOFTWARE)
    }

    @Test
    fun `command 5 - Adobe subscription next kobe`() = runTest {
        val command = interpretCommand("Adobe subscription next কবে?")
        assertThat(command).isInstanceOf(AppCommand.QuerySubscription::class.java)
        val query = command as AppCommand.QuerySubscription
        assertThat(query.name?.lowercase()).contains("adobe")
    }

    // -------------------------------------------------------------------------
    // 2. DASHBOARD FINANCIAL ROLLUP AND DETERMINISTIC CALCULATIONS
    // -------------------------------------------------------------------------

    @Test
    fun `dashboard computes exact financial sums with zero float rounding`() = runTest {
        val expenseList = listOf(
            Expense(
                id = 1,
                description = "Adobe CC",
                category = ExpenseCategory.SOFTWARE,
                amount = Money.of("6500"),
                spentAt = Instant.parse("2026-10-05T10:00:00Z"),
                isBillable = true,
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
            Expense(
                id = 2,
                description = "Grocery",
                category = ExpenseCategory.FOOD,
                amount = Money.of("3500"),
                spentAt = Instant.parse("2026-10-10T10:00:00Z"),
                isBillable = false,
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
        )

        val incomeList = listOf(
            IncomeEntry(
                id = 1,
                source = "Rayluxeo Client Project",
                amount = Money.of("50000"),
                receivedAt = Instant.parse("2026-10-02T10:00:00Z"),
                clientId = 10L,
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
            IncomeEntry(
                id = 2,
                source = "Personal gift",
                amount = Money.of("5000"),
                receivedAt = Instant.parse("2026-10-08T10:00:00Z"),
                clientId = null,
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
        )

        val paymentList = listOf(
            Payment(
                id = 1,
                amount = Money.of("20000"),
                receivedAt = Instant.parse("2026-10-12T10:00:00Z"),
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
        )

        val invoiceList = listOf(
            Invoice(
                id = 1,
                number = "INV-2026-001",
                clientId = 10L,
                status = InvoiceStatus.PARTIALLY_PAID,
                issuedOn = LocalDate.of(2026, 10, 1),
                dueOn = LocalDate.of(2026, 10, 20),
                subtotal = Money.of("40000"),
                discount = Money.zero(),
                tax = Money.zero(),
                total = Money.of("40000"),
                amountPaid = Money.of("20000"), // 20,000 outstanding
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
            Invoice(
                id = 2,
                number = "INV-2026-002",
                clientId = 11L,
                status = InvoiceStatus.SENT,
                issuedOn = LocalDate.of(2026, 10, 5),
                dueOn = LocalDate.of(2026, 10, 25),
                subtotal = Money.of("25000"),
                discount = Money.zero(),
                tax = Money.zero(),
                total = Money.of("25000"),
                amountPaid = Money.zero(), // 25,000 outstanding
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
        )

        val subList = listOf(
            Subscription(
                id = 1,
                name = "Adobe",
                amount = Money.of("6500"),
                cycle = SubscriptionCycle.MONTHLY,
                nextChargeOn = LocalDate.of(2026, 10, 25),
                isActive = true,
                createdAt = clock.now(),
                updatedAt = clock.now(),
            ),
        )

        val useCase = GetMoneyDashboardUseCase(
            expenses = FakeTestExpenses(expenseList),
            income = FakeTestIncome(incomeList),
            invoices = FakeTestInvoices(invoiceList),
            payments = FakeTestPayments(paymentList),
            subscriptions = FakeTestSubscriptions(subList),
            clock = clock,
        )

        val summary = useCase().first()

        // Income: 50,000 + 5,000 = 55,000
        assertThat(summary.incomeThisMonth).isEqualTo(Money.of("55000"))
        // Expenses: 6,500 + 3,500 = 10,000
        assertThat(summary.expensesThisMonth).isEqualTo(Money.of("10000"))
        // Net Cashflow: 55,000 - 10,000 = 45,000
        assertThat(summary.netCashFlow).isEqualTo(Money.of("45000"))

        // Business Income: only clientId != null -> 50,000
        assertThat(summary.businessIncomeThisMonth).isEqualTo(Money.of("50000"))
        // Business Expense: isBillable = true -> 6,500
        assertThat(summary.businessExpensesThisMonth).isEqualTo(Money.of("6500"))
        // Net Business Profit: 50,000 - 6,500 = 43,500
        assertThat(summary.netBusinessSummary).isEqualTo(Money.of("43500"))

        // Outstanding Receivables: (40000 - 20000) + 25000 = 45,000
        assertThat(summary.outstandingReceivables).isEqualTo(Money.of("45000"))

        // Payments received: 20,000
        assertThat(summary.paymentsReceivedThisMonth).isEqualTo(Money.of("20000"))

        // Subscriptions
        assertThat(summary.upcomingSubscriptions).hasSize(1)
        assertThat(summary.activeSubscriptionsMonthlyTotal).isEqualTo(Money.of("6500"))
    }

    // -------------------------------------------------------------------------
    // 3. EXCHANGE RATE REPOSITORY & CONVERSIONS
    // -------------------------------------------------------------------------

    @Test
    fun `exchange rates convert offline with default and user-configured manual rates`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
        }
        val settings = FakeTestAppSettings()
        val repository = ExchangeRateRepositoryImpl(settings, clock)

        // 1. Initial offline default rate: 1 USD = 121.50 BDT
        val initialRateResult = repository.getRate("USD", "BDT")
        assertThat(initialRateResult).isInstanceOf(AppResult.Success::class.java)
        val initialRate = (initialRateResult as AppResult.Success).value!!
        assertThat(initialRate.rate).isEqualTo(BigDecimal("121.50"))
        assertThat(initialRate.isManual).isFalse()

        // Convert USD 100 -> 12,150 BDT
        val conv1 = repository.convert(Money(BigDecimal("100.00"), Currency.getInstance("USD")), Currency.getInstance("BDT"))
        assertThat(conv1).isInstanceOf(AppResult.Success::class.java)
        assertThat((conv1 as AppResult.Success).value.amount).isEqualTo(BigDecimal("12150.00"))

        // 2. Set manual rate: 1 USD = 125.50 BDT
        val setRes = repository.setManualRate("USD", "BDT", BigDecimal("125.50"))
        assertThat(setRes).isInstanceOf(AppResult.Success::class.java)
        assertThat((setRes as AppResult.Success).value.isManual).isTrue()

        // Convert USD 100 -> 12,550 BDT
        val conv2 = repository.convert(Money(BigDecimal("100.00"), Currency.getInstance("USD")), Currency.getInstance("BDT"))
        assertThat(conv2).isInstanceOf(AppResult.Success::class.java)
        assertThat((conv2 as AppResult.Success).value.amount).isEqualTo(BigDecimal("12550.00"))

        // 3. Remove manual rate -> reverts to system default 121.50 BDT
        val removeRes = repository.removeManualRate("USD", "BDT")
        assertThat(removeRes).isInstanceOf(AppResult.Success::class.java)

        val revertedRate = (repository.getRate("USD", "BDT") as AppResult.Success).value!!
        assertThat(revertedRate.isManual).isFalse()
        assertThat(revertedRate.rate).isEqualTo(BigDecimal("121.50"))

        // 4. Validation: zero or negative rate is refused
        val invalidSet = repository.setManualRate("USD", "BDT", BigDecimal("-10"))
        assertThat(invalidSet).isInstanceOf(AppResult.Failure::class.java)
        assertThat((invalidSet as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
    }
}

// -----------------------------------------------------------------------------
// FAKES FOR MONEY SYSTEM TEST
// -----------------------------------------------------------------------------

private class FakeTestExpenses(private val list: List<Expense>) : ExpenseRepository {
    override fun observeAll(): Flow<List<Expense>> = flowOf(list)
    override fun observeByCategory(category: ExpenseCategory): Flow<List<Expense>> = flowOf(list.filter { it.category == category })
    override fun observeBetween(from: Instant, to: Instant): Flow<List<Expense>> = flowOf(list)
    override fun observeForProject(projectId: Long): Flow<List<Expense>> = flowOf(list.filter { it.projectId == projectId })
    override suspend fun byId(id: Long): AppResult<Expense> = list.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "No")
    override suspend fun listBetween(from: Instant, to: Instant): AppResult<List<Expense>> = AppResult.Success(list)
    override suspend fun create(expense: Expense): AppResult<Expense> = AppResult.Success(expense)
    override suspend fun update(expense: Expense): AppResult<Expense> = AppResult.Success(expense)
    override suspend fun totalSpentBetween(from: Instant, to: Instant, currency: Currency, category: ExpenseCategory?): AppResult<Money> =
        AppResult.Success(list.filter { (category == null || it.category == category) && it.amount.currency == currency }.fold(Money.zero(currency)) { acc, e -> acc + e.amount })
    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Expense> = failure(MiraErrorCode.NOT_FOUND, "No")
}

private class FakeTestIncome(private val list: List<IncomeEntry>) : IncomeRepository {
    override fun observeRecent(limit: Int): Flow<List<IncomeEntry>> = flowOf(list)
    override fun observeBetween(from: Instant, to: Instant): Flow<List<IncomeEntry>> = flowOf(list)
    override fun observeForClient(clientId: Long): Flow<List<IncomeEntry>> = flowOf(list.filter { it.clientId == clientId })
    override suspend fun byId(id: Long): AppResult<IncomeEntry> = list.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "No")
    override suspend fun create(entry: IncomeEntry): AppResult<IncomeEntry> = AppResult.Success(entry)
    override suspend fun update(entry: IncomeEntry): AppResult<IncomeEntry> = AppResult.Success(entry)
    override suspend fun totalReceivedBetween(from: Instant, to: Instant, currency: Currency, excludeEntriesFromPayments: Boolean): AppResult<Money> =
        AppResult.Success(list.filter { it.amount.currency == currency }.fold(Money.zero(currency)) { acc, i -> acc + i.amount })
    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<IncomeEntry> = failure(MiraErrorCode.NOT_FOUND, "No")
}

private class FakeTestInvoices(private val list: List<Invoice>) : InvoiceRepository {
    override fun observeAll(): Flow<List<Invoice>> = flowOf(list)
    override fun observeForClient(clientId: Long): Flow<List<Invoice>> = flowOf(list.filter { it.clientId == clientId })
    override fun observeUnpaid(): Flow<List<Invoice>> = flowOf(list.filter { it.status != InvoiceStatus.PAID && it.status != InvoiceStatus.VOID })
    override fun observeOverdue(): Flow<List<Invoice>> = flowOf(list.filter { it.status == InvoiceStatus.OVERDUE })
    override suspend fun byId(id: Long): AppResult<Invoice> = list.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "No")
    override suspend fun byNumber(number: String): AppResult<Invoice> = list.firstOrNull { it.number == number }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "No")
    override suspend fun search(query: String?): AppResult<List<Invoice>> = AppResult.Success(list)
    override suspend fun create(invoice: Invoice): AppResult<Invoice> = AppResult.Success(invoice)
    override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> = AppResult.Success(invoice)
    override suspend fun update(invoice: Invoice): AppResult<Invoice> = AppResult.Success(invoice)
    override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> = failure(MiraErrorCode.NOT_FOUND, "No")
    override suspend fun softDelete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Invoice> = failure(MiraErrorCode.NOT_FOUND, "No")
    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeTestPayments(private val list: List<Payment>) : PaymentRepository {
    override fun observeRecent(limit: Int): Flow<List<Payment>> = flowOf(list)
    override fun observeForInvoice(invoiceId: Long): Flow<List<Payment>> = flowOf(list.filter { it.invoiceId == invoiceId })
    override suspend fun byId(id: Long): AppResult<Payment> = list.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "No")
    override suspend fun forInvoice(invoiceId: Long): AppResult<List<Payment>> = AppResult.Success(list.filter { it.invoiceId == invoiceId })
    override suspend fun record(payment: Payment): AppResult<Payment> = AppResult.Success(payment)
    override suspend fun totalReceivedBetween(from: Instant, to: Instant, currency: Currency): AppResult<Money> =
        AppResult.Success(list.fold(Money.zero(currency)) { acc, p -> acc + p.amount })
    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeTestSubscriptions(private val list: List<Subscription>) : SubscriptionRepository {
    override fun observeActive(): Flow<List<Subscription>> = flowOf(list.filter { it.isActive })
    override fun observeAll(): Flow<List<Subscription>> = flowOf(list)
    override suspend fun byId(id: Long): AppResult<Subscription> = list.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "No")
    override suspend fun dueOnOrBefore(date: LocalDate): AppResult<List<Subscription>> = AppResult.Success(list.filter { !it.nextChargeOn.isAfter(date) })
    override suspend fun create(subscription: Subscription): AppResult<Subscription> = AppResult.Success(subscription)
    override suspend fun update(subscription: Subscription): AppResult<Subscription> = AppResult.Success(subscription)
    override suspend fun setActive(id: Long, active: Boolean): AppResult<Subscription> = failure(MiraErrorCode.NOT_FOUND, "No")
    override suspend fun advanceOverdueCharges(): AppResult<Int> = AppResult.Success(0)
    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Subscription> = failure(MiraErrorCode.NOT_FOUND, "No")
}

private class FakeTestAppSettings : AppSettingRepository {
    private val data = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observe(key: String): Flow<String?> = data.map { it[key] }
    override suspend fun get(key: String): AppResult<String?> = AppResult.Success(data.value[key])
    override suspend fun put(key: String, value: String): AppResult<Unit> {
        data.value = data.value + (key to value)
        return AppResult.Success(Unit)
    }
    override suspend fun remove(key: String): AppResult<Unit> {
        data.value = data.value - key
        return AppResult.Success(Unit)
    }
    override suspend fun takeNextCounterValue(key: String, startAt: Long): AppResult<Long> = AppResult.Success(startAt)
}
