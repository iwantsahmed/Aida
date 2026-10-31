package com.rayluxeo.aida.domain.command.handler

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraError
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.finance.Calculator
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.PaymentPolicy
import com.rayluxeo.aida.domain.model.CalculationRecord
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.repository.CalculationRepository
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.IncomeRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.usecase.RecordPaymentUseCase
import com.rayluxeo.aida.finance.MiraCalculator
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * What Mira does with money once the sentence has been understood.
 *
 * Two rules are worth more than the rest here and each has its own tests: a payment in a currency
 * the invoice is not in is refused rather than converted, and a name matching several people is
 * put back to Abu rather than guessed at. Everything asserts on [Money] and never on a Double.
 *
 * No Hilt: the handler is built by hand from the fakes below, on a clock that never moves.
 */
class MoneyCommandHandlerTest {

    private val clock: MiraClock = FixedMiraClock()
    private val invoices = FakeMoneyInvoices(clock)
    private val payments = FakeMoneyPayments(invoices)
    private val expenses = FakeMoneyExpenses()
    private val income = FakeMoneyIncome()
    private val clients = FakeMoneyClients()
    private val subscriptions = FakeMoneySubscriptions()
    private val calculations = FakeMoneyCalculations()
    private val calculator = RecordingMoneyCalculator()

    private fun handler(withCalculator: Calculator = calculator) = DefaultMoneyCommandHandler(
        recordReceivedPayment = RecordPaymentUseCase(payments, FakeTestActivityLog(), clock),
        expenses = expenses,
        income = income,
        clients = clients,
        invoices = invoices,
        subscriptions = subscriptions,
        calculations = calculations,
        calculator = withCalculator,
        clock = clock,
    )

    // ------------------------------------------------------------------ payments

    @Test
    fun `a payment naming nobody and nothing is still recorded`() = runTest {
        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("5000")),
        ).response()

        assertThat(response).isEqualTo(
            MiraResponse.PaymentRecorded(Money.of("5000"), clientName = null, invoiceNumber = null),
        )
        assertThat(payments.recorded).hasSize(1)
        assertThat(payments.recorded.single().amount).isEqualTo(Money.of("5000"))
        assertThat(payments.recorded.single().invoiceId).isNull()
        assertThat(payments.recorded.single().clientId).isNull()
    }

    @Test
    fun `a payment settling an invoice moves the invoice in the same step`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        invoices.add(invoice(id = 3L, number = "INV-2026-014", clientId = 7L, total = Money.of("50000")))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(
                amount = Money.of("50000"),
                invoiceNumber = "INV-2026-014",
                method = PaymentMethod.BANK_TRANSFER,
            ),
        ).response()

        assertThat(response).isEqualTo(
            MiraResponse.PaymentRecorded(Money.of("50000"), "Ahmed Traders", "INV-2026-014"),
        )
        val settled = invoices.find(3L)!!
        assertThat(settled.amountPaid).isEqualTo(Money.of("50000"))
        assertThat(settled.status).isEqualTo(InvoiceStatus.PAID)
        assertThat(payments.recorded.single().invoiceId).isEqualTo(3L)
        assertThat(payments.recorded.single().clientId).isEqualTo(7L)
        assertThat(payments.recorded.single().method).isEqualTo(PaymentMethod.BANK_TRANSFER)
    }

    @Test
    fun `part of an invoice leaves it partially paid`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        invoices.add(invoice(id = 3L, number = "INV-2026-014", clientId = 7L, total = Money.of("50000")))

        handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("20000"), invoiceNumber = "INV-2026-014"),
        ).response()

        val part = invoices.find(3L)!!
        assertThat(part.amountPaid).isEqualTo(Money.of("20000"))
        assertThat(part.outstanding).isEqualTo(Money.of("30000"))
        assertThat(part.status).isEqualTo(InvoiceStatus.PARTIALLY_PAID)
    }

    @Test
    fun `a payment in another currency than the invoice is refused, never converted`() = runTest {
        val usd = Currency.getInstance("USD")
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        invoices.add(invoice(id = 3L, number = "INV-2026-014", clientId = 7L, total = Money.of("50000")))

        val error = handler().recordPayment(
            AppCommand.RecordPayment(
                amount = Money.of("500", usd),
                invoiceNumber = "INV-2026-014",
            ),
        ).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(payments.recorded).isEmpty()
        assertThat(invoices.find(3L)!!.amountPaid).isEqualTo(Money.zero())
        assertThat(invoices.find(3L)!!.status).isEqualTo(InvoiceStatus.SENT)
    }

    @Test
    fun `more than is outstanding is refused`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        invoices.add(invoice(id = 3L, number = "INV-2026-014", clientId = 7L, total = Money.of("50000")))

        val error = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("60000"), invoiceNumber = "INV-2026-014"),
        ).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(payments.recorded).isEmpty()
        assertThat(invoices.find(3L)!!.amountPaid).isEqualTo(Money.zero())
    }

    @Test
    fun `an invoice number that matches nothing is refused, not filed unattached`() = runTest {
        val error = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("5000"), invoiceNumber = "INV-2026-099"),
        ).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(payments.recorded).isEmpty()
    }

    @Test
    fun `an invoice belonging to somebody else is refused rather than misfiled`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        clients.add(client(id = 9L, name = "Karim Enterprise"))
        invoices.add(invoice(id = 3L, number = "INV-2026-014", clientId = 7L, total = Money.of("50000")))

        val error = handler().recordPayment(
            AppCommand.RecordPayment(
                amount = Money.of("50000"),
                client = ClientTarget.ByName("Karim Enterprise"),
                invoiceNumber = "INV-2026-014",
            ),
        ).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(payments.recorded).isEmpty()
        assertThat(invoices.find(3L)!!.amountPaid).isEqualTo(Money.zero())
    }

    @Test
    fun `a payment naming only a client is filed against him`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(
                amount = Money.of("25000"),
                client = ClientTarget.ByName("Ahmed Traders"),
                method = PaymentMethod.MOBILE_WALLET,
            ),
        ).response()

        assertThat(response).isEqualTo(
            MiraResponse.PaymentRecorded(Money.of("25000"), "Ahmed Traders", invoiceNumber = null),
        )
        assertThat(payments.recorded.single().clientId).isEqualTo(7L)
        assertThat(payments.recorded.single().invoiceId).isNull()
        assertThat(payments.recorded.single().method).isEqualTo(PaymentMethod.MOBILE_WALLET)
    }

    @Test
    fun `a client named by id is used as given`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("1000"), client = ClientTarget.ById(7L)),
        ).response()

        assertThat((response as MiraResponse.PaymentRecorded).clientName).isEqualTo("Ahmed Traders")
        assertThat(payments.recorded.single().clientId).isEqualTo(7L)
    }

    @Test
    fun `a name matching several clients is a question, not a guess`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        clients.add(client(id = 8L, name = "Ahmed Textiles"))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("25000"), client = ClientTarget.ByName("Ahmed")),
        ).response()

        assertThat(response).isInstanceOf(MiraResponse.AmbiguousClient::class.java)
        val ambiguous = response as MiraResponse.AmbiguousClient
        assertThat(ambiguous.query).isEqualTo("Ahmed")
        assertThat(ambiguous.candidates).containsExactly("Ahmed Traders", "Ahmed Textiles")
        assertThat(payments.recorded).isEmpty()
    }

    @Test
    fun `an exact name settles what a partial one would not`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed"))
        clients.add(client(id = 8L, name = "Ahmed Textiles"))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("25000"), client = ClientTarget.ByName("ahmed")),
        ).response()

        assertThat((response as MiraResponse.PaymentRecorded).clientName).isEqualTo("Ahmed")
        assertThat(payments.recorded.single().clientId).isEqualTo(7L)
    }

    @Test
    fun `a client mentioned in another client's notes does not become a candidate`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        clients.add(client(id = 9L, name = "Karim Enterprise", notes = "introduced by Ahmed"))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("25000"), client = ClientTarget.ByName("Ahmed")),
        ).response()

        assertThat((response as MiraResponse.PaymentRecorded).clientName).isEqualTo("Ahmed Traders")
    }

    @Test
    fun `a name that only turns up in somebody's notes is asked about, never acted on`() = runTest {
        clients.add(client(id = 9L, name = "Karim Enterprise", notes = "introduced by Ahmed"))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("25000"), client = ClientTarget.ByName("Ahmed")),
        ).response()

        val ambiguous = response as MiraResponse.AmbiguousClient
        assertThat(ambiguous.query).isEqualTo("Ahmed")
        assertThat(ambiguous.candidates).containsExactly("Karim Enterprise")
        assertThat(payments.recorded).isEmpty()
    }

    @Test
    fun `a company name is enough to find the client`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed", company = "Rayluxeo Limited"))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("25000"), client = ClientTarget.ByName("Rayluxeo")),
        ).response()

        assertThat((response as MiraResponse.PaymentRecorded).clientName).isEqualTo("Ahmed")
    }

    @Test
    fun `a name nobody carries is a not found failure`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))

        val error = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("25000"), client = ClientTarget.ByName("Rahim")),
        ).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(payments.recorded).isEmpty()
    }

    @Test
    fun `a blank invoice number means no invoice rather than a missing one`() = runTest {
        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("5000"), invoiceNumber = "   "),
        ).response()

        assertThat((response as MiraResponse.PaymentRecorded).invoiceNumber).isNull()
        assertThat(payments.recorded.single().invoiceId).isNull()
    }

    @Test
    fun `an invoice number arrives with the spaces dictation left on it`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        invoices.add(invoice(id = 3L, number = "INV-2026-014", clientId = 7L, total = Money.of("50000")))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("1000"), invoiceNumber = " INV-2026-014 "),
        ).response()

        assertThat((response as MiraResponse.PaymentRecorded).invoiceNumber).isEqualTo("INV-2026-014")
    }

    @Test
    fun `a settled invoice whose client is in the bin still records the payment`() = runTest {
        invoices.add(invoice(id = 3L, number = "INV-2026-014", clientId = 7L, total = Money.of("50000")))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("50000"), invoiceNumber = "INV-2026-014"),
        ).response()

        assertThat(response).isEqualTo(
            MiraResponse.PaymentRecorded(Money.of("50000"), clientName = null, invoiceNumber = "INV-2026-014"),
        )
        assertThat(payments.recorded.single().clientId).isEqualTo(7L)
    }

    @Test
    fun `paisa survive the round trip exactly`() = runTest {
        val response = handler().recordPayment(
            AppCommand.RecordPayment(amount = Money.of("1234.56")),
        ).response()

        val amount = (response as MiraResponse.PaymentRecorded).amount
        assertThat(amount).isEqualTo(Money.of("1234.56"))
        assertThat(amount.amount.scale()).isEqualTo(2)
        assertThat(amount.amount.unscaledValue().toLong()).isEqualTo(123_456L)
    }

    @Test
    fun `a payment can be recorded as a percentage of an invoice total`() = runTest {
        invoices.add(invoice(id = 41L, number = "INV-2026-041", clientId = 7L, total = Money.of("10000")))

        val response = handler().recordPayment(
            AppCommand.RecordPayment(percentage = java.math.BigDecimal(50), invoiceNumber = "41"),
        ).response()

        assertThat(response).isEqualTo(
            MiraResponse.PaymentRecorded(Money.of("5000.00"), clientName = null, invoiceNumber = "INV-2026-041"),
        )
        assertThat(payments.recorded.single().amount).isEqualTo(Money.of("5000.00"))
        assertThat(invoices.find(41L)?.amountPaid).isEqualTo(Money.of("5000.00"))
    }

    @Test
    fun `a storage failure recording a payment reaches Abu as a failure`() = runTest {
        payments.failWith = MiraErrorCode.STORAGE

        val error = handler().recordPayment(AppCommand.RecordPayment(amount = Money.of("5000"))).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.STORAGE)
    }

    // ------------------------------------------------------------------ expenses

    @Test
    fun `an expense is recorded with the words Abu used`() = runTest {
        val response = handler().recordExpense(
            AppCommand.RecordExpense(
                amount = Money.of("450"),
                description = "Uber to the client's office",
                category = ExpenseCategory.TRAVEL,
            ),
        ).response()

        assertThat(response).isEqualTo(MiraResponse.ExpenseRecorded(Money.of("450"), "Uber to the client's office"))
        val stored = expenses.rows.single()
        assertThat(stored.description).isEqualTo("Uber to the client's office")
        assertThat(stored.category).isEqualTo(ExpenseCategory.TRAVEL)
        assertThat(stored.amount).isEqualTo(Money.of("450"))
        assertThat(stored.spentAt).isEqualTo(clock.now())
        assertThat(stored.deletedAt).isNull()
    }

    @Test
    fun `a Bengali description is stored exactly as it was said`() = runTest {
        val response = handler().recordExpense(
            AppCommand.RecordExpense(amount = Money.of("120"), description = "দুপুরের খাবার"),
        ).response()

        assertThat(response).isEqualTo(MiraResponse.ExpenseRecorded(Money.of("120"), "দুপুরের খাবার"))
        assertThat(expenses.rows.single().description).isEqualTo("দুপুরের খাবার")
        assertThat(expenses.rows.single().category).isEqualTo(ExpenseCategory.OTHER)
    }

    @Test
    fun `a storage failure recording an expense reaches Abu as a failure`() = runTest {
        expenses.failWith = MiraErrorCode.STORAGE

        val error = handler().recordExpense(
            AppCommand.RecordExpense(amount = Money.of("450"), description = "Fuel"),
        ).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.STORAGE)
        assertThat(expenses.rows).isEmpty()
    }

    // ------------------------------------------------------------------ income

    @Test
    fun `income with no client named is still recorded`() = runTest {
        val response = handler().recordIncome(
            AppCommand.RecordIncome(amount = Money.of("15000"), source = "Logo design"),
        ).response()

        assertThat(response).isEqualTo(MiraResponse.IncomeRecorded(Money.of("15000"), "Logo design"))
        val stored = income.rows.single()
        assertThat(stored.source).isEqualTo("Logo design")
        assertThat(stored.clientId).isNull()
        assertThat(stored.receivedAt).isEqualTo(clock.now())
        assertThat(stored.paymentId).isNull()
    }

    @Test
    fun `income against a named client carries his id`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))

        val response = handler().recordIncome(
            AppCommand.RecordIncome(
                amount = Money.of("15000"),
                source = "Retainer",
                client = ClientTarget.ByName("Ahmed Traders"),
            ),
        ).response()

        assertThat(response).isEqualTo(MiraResponse.IncomeRecorded(Money.of("15000"), "Retainer"))
        assertThat(income.rows.single().clientId).isEqualTo(7L)
    }

    @Test
    fun `an ambiguous client stops the income too`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        clients.add(client(id = 8L, name = "Ahmed Textiles"))

        val response = handler().recordIncome(
            AppCommand.RecordIncome(
                amount = Money.of("15000"),
                source = "Retainer",
                client = ClientTarget.ByName("Ahmed"),
            ),
        ).response()

        assertThat(response).isInstanceOf(MiraResponse.AmbiguousClient::class.java)
        assertThat(income.rows).isEmpty()
    }

    @Test
    fun `an unknown client stops the income before anything is written`() = runTest {
        val error = handler().recordIncome(
            AppCommand.RecordIncome(
                amount = Money.of("15000"),
                source = "Retainer",
                client = ClientTarget.ByName("Rahim"),
            ),
        ).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(income.rows).isEmpty()
    }

    @Test
    fun `a storage failure recording income reaches Abu as a failure`() = runTest {
        income.failWith = MiraErrorCode.STORAGE

        val error = handler().recordIncome(
            AppCommand.RecordIncome(amount = Money.of("15000"), source = "Retainer"),
        ).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.STORAGE)
    }

    // ------------------------------------------------------------------ arithmetic

    @Test
    fun `the expression reaches the calculator exactly as it was given`() = runTest {
        handler().performCalculation(AppCommand.PerformCalculation("12450 * 3 + 7.5% of 2400")).response()

        assertThat(calculator.seen).containsExactly("12450 * 3 + 7.5% of 2400")
    }

    @Test
    fun `the answer is the calculator's, reported beside the question`() = runTest {
        val response = handler(RecordingMoneyCalculator(AppResult.Success("37530"))).performCalculation(
            AppCommand.PerformCalculation("12450 * 3"),
        ).response()

        assertThat(response).isEqualTo(MiraResponse.CalculationResult("12450 * 3", "37530"))
    }

    @Test
    fun `an expression the calculator cannot parse is a validation failure`() = runTest {
        val broken = RecordingMoneyCalculator(failure(MiraErrorCode.VALIDATION, "No numbers to work with"))

        val error = handler(broken).performCalculation(AppCommand.PerformCalculation("times by")).error()

        assertThat(error.code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `the real evaluator answers through the same seam`() = runTest {
        val real = handler(MiraCalculator())

        assertThat(real.performCalculation(AppCommand.PerformCalculation("2 + 3 * 4")).response())
            .isEqualTo(MiraResponse.CalculationResult("2 + 3 * 4", "14"))
        assertThat(real.performCalculation(AppCommand.PerformCalculation("15% of 2400")).response())
            .isEqualTo(MiraResponse.CalculationResult("15% of 2400", "360"))
    }

    @Test
    fun `dividing by zero comes back as a refusal rather than an exception`() = runTest {
        val error = handler(MiraCalculator())
            .performCalculation(AppCommand.PerformCalculation("5 / 0"))
            .error()

        assertThat(error.code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    // ------------------------------------------------------------------ fixtures

    private fun client(
        id: Long,
        name: String,
        company: String? = null,
        notes: String? = null,
    ): Client = Client(
        id = id,
        uuid = "client-$id",
        name = name,
        companyName = company,
        notes = notes,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun invoice(
        id: Long,
        number: String,
        clientId: Long,
        total: Money,
        status: InvoiceStatus = InvoiceStatus.SENT,
    ): Invoice = Invoice(
        id = id,
        uuid = "invoice-$id",
        number = number,
        clientId = clientId,
        status = status,
        issuedOn = LocalDate.of(2026, 3, 1),
        dueOn = LocalDate.of(2026, 3, 31),
        subtotal = total,
        discount = Money.zero(total.currency),
        tax = Money.zero(total.currency),
        total = total,
        amountPaid = Money.zero(total.currency),
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun AppResult<MiraResponse>.response(): MiraResponse = (this as AppResult.Success).value

    private fun AppResult<MiraResponse>.error(): MiraError = (this as AppResult.Failure).error
}

/** Records what it was asked and answers with whatever the test put in it — it never does sums. */
private class RecordingMoneyCalculator(
    private val answer: AppResult<String> = AppResult.Success("14"),
) : Calculator {

    val seen = mutableListOf<String>()

    override fun evaluate(expression: String): AppResult<String> {
        seen += expression
        return answer
    }
}

/**
 * Expenses in memory.
 *
 * Soft deletion is modelled rather than skipped: every read here hides a binned row exactly as
 * the Room implementation does, so a test cannot pass against behaviour the real repository
 * does not have.
 */
private class FakeMoneyExpenses : ExpenseRepository {

    private val store = MutableStateFlow<List<Expense>>(emptyList())
    private var nextId = 1L

    /** Set before a call to make the next write fail, so the error paths are real paths. */
    var failWith: MiraErrorCode? = null

    val rows: List<Expense> get() = store.value.filter { it.deletedAt == null }

    override fun observeAll(): Flow<List<Expense>> = store.map { all -> all.live() }

    override fun observeByCategory(category: ExpenseCategory): Flow<List<Expense>> =
        store.map { all -> all.live().filter { it.category == category } }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<Expense>> =
        store.map { all -> all.live().filter { it.spentAt >= from && it.spentAt < to } }

    override fun observeForProject(projectId: Long): Flow<List<Expense>> =
        store.map { all -> all.live().filter { it.projectId == projectId } }

    override suspend fun byId(id: Long): AppResult<Expense> =
        store.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No expense with id $id")

    override suspend fun listBetween(from: Instant, to: Instant): AppResult<List<Expense>> =
        AppResult.Success(store.value.live().filter { it.spentAt >= from && it.spentAt < to })

    override suspend fun create(expense: Expense): AppResult<Expense> {
        failWith?.let { return failure(it, "Fake expense store was told to fail") }
        val saved = expense.copy(id = nextId++)
        store.value = store.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun update(expense: Expense): AppResult<Expense> {
        failWith?.let { return failure(it, "Fake expense store was told to fail") }
        if (store.value.none { it.id == expense.id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No expense with id ${expense.id}")
        }
        store.value = store.value.map { if (it.id == expense.id) expense else it }
        return AppResult.Success(expense)
    }

    override suspend fun totalSpentBetween(
        from: Instant,
        to: Instant,
        currency: Currency,
        category: ExpenseCategory?,
    ): AppResult<Money> = AppResult.Success(
        store.value.live()
            .filter { it.spentAt >= from && it.spentAt < to }
            .filter { it.amount.currency == currency }
            .filter { category == null || it.category == category }
            .fold(Money.zero(currency)) { running, expense -> running + expense.amount },
    )

    override suspend fun delete(id: Long): AppResult<Unit> {
        val found = store.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No expense with id $id")
        store.value = store.value.map { if (it.id == id) found.copy(deletedAt = MONEY_BINNED_AT) else it }
        return AppResult.Success(Unit)
    }

    override suspend fun restore(id: Long): AppResult<Expense> {
        val found = store.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No expense with id $id")
        val restored = found.copy(deletedAt = null)
        store.value = store.value.map { if (it.id == id) restored else it }
        return AppResult.Success(restored)
    }

    private fun List<Expense>.live(): List<Expense> = filter { it.deletedAt == null }
}

/** Income in memory, with the same soft-delete behaviour as the stored one. */
private class FakeMoneyIncome : IncomeRepository {

    private val store = MutableStateFlow<List<IncomeEntry>>(emptyList())
    private var nextId = 1L

    var failWith: MiraErrorCode? = null

    val rows: List<IncomeEntry> get() = store.value.filter { it.deletedAt == null }

    override fun observeRecent(limit: Int): Flow<List<IncomeEntry>> =
        store.map { all -> all.live().sortedByDescending { it.receivedAt }.take(limit) }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<IncomeEntry>> =
        store.map { all -> all.live().filter { it.receivedAt >= from && it.receivedAt < to } }

    override fun observeForClient(clientId: Long): Flow<List<IncomeEntry>> =
        store.map { all -> all.live().filter { it.clientId == clientId } }

    override suspend fun byId(id: Long): AppResult<IncomeEntry> =
        store.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No income entry with id $id")

    override suspend fun create(entry: IncomeEntry): AppResult<IncomeEntry> {
        failWith?.let { return failure(it, "Fake income store was told to fail") }
        val saved = entry.copy(id = nextId++)
        store.value = store.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun update(entry: IncomeEntry): AppResult<IncomeEntry> {
        failWith?.let { return failure(it, "Fake income store was told to fail") }
        if (store.value.none { it.id == entry.id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No income entry with id ${entry.id}")
        }
        store.value = store.value.map { if (it.id == entry.id) entry else it }
        return AppResult.Success(entry)
    }

    override suspend fun totalReceivedBetween(
        from: Instant,
        to: Instant,
        currency: Currency,
        excludeEntriesFromPayments: Boolean,
    ): AppResult<Money> = AppResult.Success(
        store.value.live()
            .filter { it.receivedAt >= from && it.receivedAt < to }
            .filter { it.amount.currency == currency }
            .filter { !excludeEntriesFromPayments || it.paymentId == null }
            .fold(Money.zero(currency)) { running, entry -> running + entry.amount },
    )

    override suspend fun delete(id: Long): AppResult<Unit> {
        val found = store.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No income entry with id $id")
        store.value = store.value.map { if (it.id == id) found.copy(deletedAt = MONEY_BINNED_AT) else it }
        return AppResult.Success(Unit)
    }

    override suspend fun restore(id: Long): AppResult<IncomeEntry> {
        val found = store.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No income entry with id $id")
        val restored = found.copy(deletedAt = null)
        store.value = store.value.map { if (it.id == id) restored else it }
        return AppResult.Success(restored)
    }

    private fun List<IncomeEntry>.live(): List<IncomeEntry> = filter { it.deletedAt == null }
}

/**
 * Clients in memory.
 *
 * [search] matches name, company and notes, because the stored one is full-text over all three —
 * that is exactly why the handler narrows what comes back instead of taking the first row.
 */
private class FakeMoneyClients : ClientRepository {

    private val store = MutableStateFlow<List<Client>>(emptyList())

    fun add(client: Client) {
        store.value = store.value + client
    }

    override fun observe(filter: ClientFilter): Flow<List<Client>> =
        store.map { all -> all.live().filter { it.matches(filter) } }

    override suspend fun byId(id: Long): AppResult<Client> =
        store.value.live().firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No client with id $id")

    override suspend fun search(query: String?, filter: ClientFilter): AppResult<List<Client>> {
        val needle = query?.trim()?.takeIf { it.isNotEmpty() }
        return AppResult.Success(
            store.value.live()
                .filter { it.matches(filter) }
                .filter { needle == null || it.carries(needle) },
        )
    }

    override suspend fun create(client: Client): AppResult<Client> {
        val saved = client.copy(id = store.value.size + 1L, uuid = "client-${store.value.size + 1}")
        store.value = store.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun update(client: Client): AppResult<Client> {
        if (store.value.none { it.id == client.id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No client with id ${client.id}")
        }
        store.value = store.value.map { if (it.id == client.id) client else it }
        return AppResult.Success(client)
    }

    override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client> {
        val found = store.value.live().firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No client with id $id")
        val changed = found.copy(isArchived = archived)
        store.value = store.value.map { if (it.id == id) changed else it }
        return AppResult.Success(changed)
    }

    override suspend fun softDelete(id: Long): AppResult<Unit> {
        val found = store.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No client with id $id")
        store.value = store.value.map { if (it.id == id) found.copy(deletedAt = MONEY_BINNED_AT) else it }
        return AppResult.Success(Unit)
    }

    override suspend fun restore(id: Long): AppResult<Client> {
        val found = store.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No client with id $id")
        val restored = found.copy(deletedAt = null)
        store.value = store.value.map { if (it.id == id) restored else it }
        return AppResult.Success(restored)
    }

    override suspend fun purge(id: Long): AppResult<Unit> {
        if (store.value.none { it.id == id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No client with id $id")
        }
        store.value = store.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    private fun Client.carries(needle: String): Boolean =
        name.contains(needle, ignoreCase = true) ||
            companyName?.contains(needle, ignoreCase = true) == true ||
            notes?.contains(needle, ignoreCase = true) == true

    private fun Client.matches(filter: ClientFilter): Boolean = when (filter) {
        ClientFilter.ALL -> true
        ClientFilter.ACTIVE -> !isArchived
        ClientFilter.ARCHIVED -> isArchived
    }

    private fun List<Client>.live(): List<Client> = filter { it.deletedAt == null }
}

/** Invoices in memory. [find] is the test's own window onto a row after a payment has moved it. */
private class FakeMoneyInvoices(private val clock: MiraClock) : InvoiceRepository {

    private val store = MutableStateFlow<List<Invoice>>(emptyList())
    private var nextId = 100L

    fun add(invoice: Invoice) {
        store.value = store.value + invoice
    }

    fun find(id: Long): Invoice? = store.value.firstOrNull { it.id == id }

    /** How the payments cluster writes back: the header only, exactly as the real transaction does. */
    fun replace(invoice: Invoice) {
        store.value = store.value.map { if (it.id == invoice.id) invoice else it }
    }

    override fun observeAll(): Flow<List<Invoice>> = store.map { all -> all.live() }

    override fun observeForClient(clientId: Long): Flow<List<Invoice>> =
        store.map { all -> all.live().filter { it.clientId == clientId } }

    override fun observeUnpaid(): Flow<List<Invoice>> =
        store.map { all -> all.live().filter { it.isOwed() }.sortedBy { it.dueOn } }

    override fun observeOverdue(): Flow<List<Invoice>> =
        store.map { all ->
            all.live().filter { it.isOwed() && it.dueOn.isBefore(clock.today()) }.sortedBy { it.dueOn }
        }

    override suspend fun byId(id: Long): AppResult<Invoice> =
        store.value.live().firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No invoice with id $id")

    override suspend fun byNumber(number: String): AppResult<Invoice> =
        store.value.live().firstOrNull {
            it.number == number || it.number.endsWith("-$number") ||
                (number.all { c -> c.isDigit() } && it.number.endsWith("-${number.padStart(3, '0')}"))
        }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No invoice numbered $number")

    override suspend fun search(query: String?): AppResult<List<Invoice>> {
        val needle = query?.trim()?.takeIf { it.isNotEmpty() }
        return AppResult.Success(
            store.value.live().filter {
                needle == null ||
                    it.number.contains(needle, ignoreCase = true) ||
                    it.notes?.contains(needle, ignoreCase = true) == true
            },
        )
    }

    override suspend fun create(invoice: Invoice): AppResult<Invoice> {
        if (store.value.any { it.number == invoice.number }) {
            return failure(MiraErrorCode.VALIDATION, "Invoice ${invoice.number} already exists")
        }
        val saved = invoice.copy(id = nextId++, uuid = "invoice-${invoice.number}")
        store.value = store.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> {
        val year = invoice.issuedOn.year
        val taken = store.value.count { it.number.startsWith("INV-$year-") }
        return create(invoice.copy(number = "INV-$year-${(taken + 1).toString().padStart(3, '0')}"))
    }

    override suspend fun update(invoice: Invoice): AppResult<Invoice> {
        if (store.value.none { it.id == invoice.id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No invoice with id ${invoice.id}")
        }
        replace(invoice)
        return AppResult.Success(invoice)
    }

    override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.PARTIALLY_PAID) {
            return failure(MiraErrorCode.VALIDATION, "Only a payment moves an invoice to $status")
        }
        val found = store.value.live().firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No invoice with id $id")
        val changed = found.copy(status = status)
        replace(changed)
        return AppResult.Success(changed)
    }

    override suspend fun softDelete(id: Long): AppResult<Unit> {
        val found = store.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No invoice with id $id")
        replace(found.copy(deletedAt = MONEY_BINNED_AT))
        return AppResult.Success(Unit)
    }

    override suspend fun restore(id: Long): AppResult<Invoice> {
        val found = store.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No invoice with id $id")
        val restored = found.copy(deletedAt = null)
        replace(restored)
        return AppResult.Success(restored)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        if (store.value.none { it.id == id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No invoice with id $id")
        }
        store.value = store.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    private fun Invoice.isOwed(): Boolean =
        status != InvoiceStatus.VOID && outstanding.amount.signum() > 0

    private fun List<Invoice>.live(): List<Invoice> = filter { it.deletedAt == null }
}

/**
 * Payments in memory, over the same invoice store.
 *
 * [record] reproduces the four refusals the Room transaction makes — gone, void, another
 * currency, more than is outstanding — and applies the paid figure and status through the domain's
 * own [PaymentPolicy]. Without that, a test asserting Mira refuses a dollar payment against a taka
 * invoice would be asserting against a fake that never refuses anything.
 */
private class FakeMoneyPayments(private val invoices: FakeMoneyInvoices) : PaymentRepository {

    private val store = MutableStateFlow<List<Payment>>(emptyList())
    private var nextId = 1L

    var failWith: MiraErrorCode? = null

    val recorded: List<Payment> get() = store.value

    override fun observeRecent(limit: Int): Flow<List<Payment>> =
        store.map { all -> all.sortedByDescending { it.receivedAt }.take(limit) }

    override fun observeForInvoice(invoiceId: Long): Flow<List<Payment>> =
        store.map { all -> all.filter { it.invoiceId == invoiceId } }

    override suspend fun byId(id: Long): AppResult<Payment> =
        store.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No payment with id $id")

    override suspend fun forInvoice(invoiceId: Long): AppResult<List<Payment>> =
        AppResult.Success(store.value.filter { it.invoiceId == invoiceId })

    override suspend fun record(payment: Payment): AppResult<Payment> {
        failWith?.let { return failure(it, "Fake payment store was told to fail") }
        val invoiceId = payment.invoiceId ?: return AppResult.Success(save(payment))

        val invoice = invoices.find(invoiceId)?.takeIf { it.deletedAt == null }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No live invoice with id $invoiceId")
        if (invoice.status == InvoiceStatus.VOID) {
            return failure(MiraErrorCode.VALIDATION, "Invoice $invoiceId is void and cannot take a payment")
        }
        if (invoice.currency != payment.amount.currency) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Invoice $invoiceId is in ${invoice.currency.currencyCode}, " +
                    "payment in ${payment.amount.currency.currencyCode}",
            )
        }
        val paidMinor = MoneyAmount.toMinorUnits(invoice.amountPaid) + MoneyAmount.toMinorUnits(payment.amount)
        val totalMinor = MoneyAmount.toMinorUnits(invoice.total)
        if (paidMinor > totalMinor) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Invoice $invoiceId has only ${totalMinor - MoneyAmount.toMinorUnits(invoice.amountPaid)} " +
                    "minor units outstanding",
            )
        }
        val saved = save(payment)
        invoices.replace(
            invoice.copy(
                amountPaid = MoneyAmount.fromMinorUnits(paidMinor, invoice.currency.currencyCode),
                status = PaymentPolicy.statusAfter(paidMinor, totalMinor, invoice.status),
            ),
        )
        return AppResult.Success(saved)
    }

    override suspend fun totalReceivedBetween(
        from: Instant,
        to: Instant,
        currency: Currency,
    ): AppResult<Money> = AppResult.Success(
        store.value
            .filter { it.receivedAt >= from && it.receivedAt < to }
            .filter { it.amount.currency == currency }
            .fold(Money.zero(currency)) { running, payment -> running + payment.amount },
    )

    override suspend fun delete(id: Long): AppResult<Unit> {
        if (store.value.none { it.id == id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No payment with id $id")
        }
        store.value = store.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    private fun save(payment: Payment): Payment {
        val saved = payment.copy(id = nextId++)
        store.value = store.value + saved
        return saved
    }
}

/** Any instant will do for a bin marker; the tests assert on the flag, never on when it happened. */
private val MONEY_BINNED_AT: Instant = Instant.parse("2026-03-09T00:00:00Z")

private class FakeMoneySubscriptions : SubscriptionRepository {
    private val store = MutableStateFlow<List<Subscription>>(emptyList())
    override fun observeActive(): Flow<List<Subscription>> = store.map { list -> list.filter { it.isActive && it.deletedAt == null } }
    override fun observeAll(): Flow<List<Subscription>> = store.map { list -> list.filter { it.deletedAt == null } }
    override suspend fun byId(id: Long): AppResult<Subscription> =
        store.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "No sub with id $id")
    override suspend fun dueOnOrBefore(date: LocalDate): AppResult<List<Subscription>> =
        AppResult.Success(store.value.filter { it.isActive && it.deletedAt == null && !it.nextChargeOn.isAfter(date) })
    override suspend fun create(subscription: Subscription): AppResult<Subscription> {
        val s = subscription.copy(id = (store.value.size + 1).toLong())
        store.value = store.value + s
        return AppResult.Success(s)
    }
    override suspend fun update(subscription: Subscription): AppResult<Subscription> {
        store.value = store.value.map { if (it.id == subscription.id) subscription else it }
        return AppResult.Success(subscription)
    }
    override suspend fun setActive(id: Long, active: Boolean): AppResult<Subscription> {
        val sub = store.value.firstOrNull { it.id == id } ?: return failure(MiraErrorCode.NOT_FOUND, "No sub")
        val updated = sub.copy(isActive = active)
        store.value = store.value.map { if (it.id == id) updated else it }
        return AppResult.Success(updated)
    }
    override suspend fun advanceOverdueCharges(): AppResult<Int> = AppResult.Success(0)
    override suspend fun delete(id: Long): AppResult<Unit> {
        store.value = store.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }
    override suspend fun restore(id: Long): AppResult<Subscription> = failure(MiraErrorCode.NOT_FOUND, "Not found")
}

private class FakeMoneyCalculations : CalculationRepository {
    private val store = MutableStateFlow<List<CalculationRecord>>(emptyList())
    override fun observeRecent(limit: Int): Flow<List<CalculationRecord>> =
        store.map { it.takeLast(limit).reversed() }
    override suspend fun mostRecent(): AppResult<CalculationRecord?> = AppResult.Success(store.value.lastOrNull())
    override suspend fun record(record: CalculationRecord): AppResult<CalculationRecord> {
        store.value = store.value + record
        return AppResult.Success(record)
    }
    override suspend fun clear(): AppResult<Unit> {
        store.value = emptyList()
        return AppResult.Success(Unit)
    }
}

