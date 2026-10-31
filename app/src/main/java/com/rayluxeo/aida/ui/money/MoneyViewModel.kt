package com.rayluxeo.aida.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.finance.Calculator
import com.rayluxeo.aida.domain.model.Budget
import com.rayluxeo.aida.domain.model.BudgetProgress
import com.rayluxeo.aida.domain.model.CalculationRecord
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.ExchangeRate
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.repository.BudgetRepository
import com.rayluxeo.aida.domain.repository.CalculationRepository
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ExchangeRateRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.IncomeRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.usecase.ClearCalculationHistoryUseCase
import com.rayluxeo.aida.domain.usecase.CreateBudgetUseCase
import com.rayluxeo.aida.domain.usecase.CreateSubscriptionUseCase
import com.rayluxeo.aida.domain.usecase.GetMoneyDashboardUseCase
import com.rayluxeo.aida.domain.usecase.ManageExchangeRateUseCase
import com.rayluxeo.aida.domain.usecase.MoneyDashboardSummary
import com.rayluxeo.aida.domain.usecase.RecordExpenseUseCase
import com.rayluxeo.aida.domain.usecase.RecordIncomeUseCase
import com.rayluxeo.aida.domain.usecase.RecordPaymentUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MoneyTab {
    DASHBOARD,
    EXPENSES_INCOME,
    PAYMENTS_RECEIVABLES,
    BUDGETS_SUBSCRIPTIONS,
    CALCULATOR,
    RATES,
}

data class LocalUiState(
    val selectedTab: MoneyTab = MoneyTab.DASHBOARD,
    val showExpenses: Boolean = true,
    val showPayments: Boolean = true,
    val expenseCategoryFilter: ExpenseCategory? = null,
    val calcExpression: String = "",
    val calcResult: String? = null,
    val calcError: String? = null,
    val convertAmount: String = "100",
    val convertFrom: String = "USD",
    val convertTo: String = "BDT",
    val convertedResult: String? = null,
    val showAddExpenseDialog: Boolean = false,
    val showAddIncomeDialog: Boolean = false,
    val showRecordPaymentDialog: Boolean = false,
    val showAddBudgetDialog: Boolean = false,
    val showAddSubscriptionDialog: Boolean = false,
    val showAddRateDialog: Boolean = false,
    val notice: MoneyNotice? = null,
)

data class MoneyUiState(
    val selectedTab: MoneyTab = MoneyTab.DASHBOARD,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val dashboard: MoneyDashboardSummary? = null,
    val expenses: List<Expense> = emptyList(),
    val income: List<IncomeEntry> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val unpaidInvoices: List<Invoice> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val budgetProgress: List<BudgetProgress> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val calculations: List<CalculationRecord> = emptyList(),
    val exchangeRates: List<ExchangeRate> = emptyList(),
    val clients: Map<Long, Client> = emptyMap(),
    val showExpenses: Boolean = true,
    val showPayments: Boolean = true,
    val expenseCategoryFilter: ExpenseCategory? = null,
    val calcExpression: String = "",
    val calcResult: String? = null,
    val calcError: String? = null,
    val convertAmount: String = "100",
    val convertFrom: String = "USD",
    val convertTo: String = "BDT",
    val convertedResult: String? = null,
    val showAddExpenseDialog: Boolean = false,
    val showAddIncomeDialog: Boolean = false,
    val showRecordPaymentDialog: Boolean = false,
    val showAddBudgetDialog: Boolean = false,
    val showAddSubscriptionDialog: Boolean = false,
    val showAddRateDialog: Boolean = false,
    val notice: MoneyNotice? = null,
)

@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val getDashboardUseCase: GetMoneyDashboardUseCase,
    private val recordExpenseUseCase: RecordExpenseUseCase,
    private val recordIncomeUseCase: RecordIncomeUseCase,
    private val recordPaymentUseCase: RecordPaymentUseCase,
    private val createBudgetUseCase: CreateBudgetUseCase,
    private val createSubscriptionUseCase: CreateSubscriptionUseCase,
    private val manageExchangeRateUseCase: ManageExchangeRateUseCase,
    private val clearCalculationHistoryUseCase: ClearCalculationHistoryUseCase,
    private val expenseRepo: ExpenseRepository,
    private val incomeRepo: IncomeRepository,
    private val paymentRepo: PaymentRepository,
    private val invoiceRepo: InvoiceRepository,
    private val budgetRepo: BudgetRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val calculationRepo: CalculationRepository,
    private val exchangeRateRepo: ExchangeRateRepository,
    private val clientRepo: ClientRepository,
    private val calculator: Calculator,
    private val clock: MiraClock,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(LocalUiState())

    init {
        recomputeConversion()
    }

    val uiState: StateFlow<MoneyUiState> = combine(
        local,
        getDashboardUseCase(),
        expenseRepo.observeAll(),
        incomeRepo.observeRecent(100),
        paymentRepo.observeRecent(100),
        invoiceRepo.observeUnpaid(),
        budgetRepo.observeAll(),
        subscriptionRepo.observeAll(),
        calculationRepo.observeRecent(50),
        exchangeRateRepo.observeAll(),
        clientRepo.observe(ClientFilter.ALL),
        settings.settings,
    ) { args: Array<Any?> ->
        val loc = args[0] as LocalUiState
        val dash = args[1] as MoneyDashboardSummary
        @Suppress("UNCHECKED_CAST")
        val exp = args[2] as List<Expense>
        @Suppress("UNCHECKED_CAST")
        val inc = args[3] as List<IncomeEntry>
        @Suppress("UNCHECKED_CAST")
        val pay = args[4] as List<Payment>
        @Suppress("UNCHECKED_CAST")
        val inv = args[5] as List<Invoice>
        @Suppress("UNCHECKED_CAST")
        val bud = args[6] as List<Budget>
        @Suppress("UNCHECKED_CAST")
        val subs = args[7] as List<Subscription>
        @Suppress("UNCHECKED_CAST")
        val calcs = args[8] as List<CalculationRecord>
        @Suppress("UNCHECKED_CAST")
        val rates = args[9] as List<ExchangeRate>
        @Suppress("UNCHECKED_CAST")
        val clients = (args[10] as List<Client>).associateBy { it.id }
        val language = (args[11] as MiraSettings).languageMode.displayLanguage()
        // Spend-vs-limit, computed by the domain so the bar matches what Mira says aloud. Re-run
        // here on every budget or expense change, since both feed this combine.
        val progress = (budgetRepo.activeProgress() as? AppResult.Success)?.value ?: emptyList()

        MoneyUiState(
            selectedTab = loc.selectedTab,
            language = language,
            dashboard = dash,
            expenses = exp,
            income = inc,
            payments = pay,
            unpaidInvoices = inv,
            budgets = bud,
            budgetProgress = progress,
            subscriptions = subs,
            calculations = calcs,
            exchangeRates = rates,
            clients = clients,
            showExpenses = loc.showExpenses,
            showPayments = loc.showPayments,
            expenseCategoryFilter = loc.expenseCategoryFilter,
            calcExpression = loc.calcExpression,
            calcResult = loc.calcResult,
            calcError = loc.calcError,
            convertAmount = loc.convertAmount,
            convertFrom = loc.convertFrom,
            convertTo = loc.convertTo,
            convertedResult = loc.convertedResult,
            showAddExpenseDialog = loc.showAddExpenseDialog,
            showAddIncomeDialog = loc.showAddIncomeDialog,
            showRecordPaymentDialog = loc.showRecordPaymentDialog,
            showAddBudgetDialog = loc.showAddBudgetDialog,
            showAddSubscriptionDialog = loc.showAddSubscriptionDialog,
            showAddRateDialog = loc.showAddRateDialog,
            notice = loc.notice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MoneyUiState(),
    )

    fun selectTab(tab: MoneyTab) {
        local.update { it.copy(selectedTab = tab) }
    }

    fun toggleShowExpenses(show: Boolean) {
        local.update { it.copy(showExpenses = show) }
    }

    fun toggleShowPayments(show: Boolean) {
        local.update { it.copy(showPayments = show) }
    }

    fun setExpenseCategoryFilter(category: ExpenseCategory?) {
        local.update { it.copy(expenseCategoryFilter = category) }
    }

    fun clearNotice() {
        local.update { it.copy(notice = null) }
    }

    fun setAddExpenseDialogVisible(visible: Boolean) { local.update { it.copy(showAddExpenseDialog = visible) } }
    fun setAddIncomeDialogVisible(visible: Boolean) { local.update { it.copy(showAddIncomeDialog = visible) } }
    fun setRecordPaymentDialogVisible(visible: Boolean) { local.update { it.copy(showRecordPaymentDialog = visible) } }
    fun setAddBudgetDialogVisible(visible: Boolean) { local.update { it.copy(showAddBudgetDialog = visible) } }
    fun setAddSubscriptionDialogVisible(visible: Boolean) { local.update { it.copy(showAddSubscriptionDialog = visible) } }
    fun setAddRateDialogVisible(visible: Boolean) { local.update { it.copy(showAddRateDialog = visible) } }

    fun recordExpense(
        amount: String,
        currencyCode: String,
        description: String,
        category: ExpenseCategory,
        isBillable: Boolean,
        clientId: Long? = null,
    ) {
        viewModelScope.launch {
            try {
                val parsedAmount = BigDecimal(amount.trim())
                val curr = Currency.getInstance(currencyCode.trim().uppercase())
                val money = Money(parsedAmount, curr)
                when (val result = recordExpenseUseCase(
                    amount = money,
                    description = description,
                    category = category,
                    isBillable = isBillable,
                    clientId = clientId,
                )) {
                    is AppResult.Failure -> local.update { it.copy(notice = MoneyNotice.ExpenseFailed(result.error.detail ?: result.error.code.name)) }
                    is AppResult.Success -> {
                        local.update {
                            it.copy(
                                showAddExpenseDialog = false,
                                notice = MoneyNotice.ExpenseRecorded(result.value.description),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                local.update { it.copy(notice = MoneyNotice.InvalidExpenseAmount(e.message.orEmpty())) }
            }
        }
    }

    fun recordIncome(
        amount: String,
        currencyCode: String,
        source: String,
        description: String?,
        clientId: Long? = null,
    ) {
        viewModelScope.launch {
            try {
                val parsedAmount = BigDecimal(amount.trim())
                val curr = Currency.getInstance(currencyCode.trim().uppercase())
                val money = Money(parsedAmount, curr)
                when (val result = recordIncomeUseCase(
                    amount = money,
                    source = source,
                    description = description,
                    clientId = clientId,
                )) {
                    is AppResult.Failure -> local.update { it.copy(notice = MoneyNotice.IncomeFailed(result.error.detail ?: result.error.code.name)) }
                    is AppResult.Success -> {
                        local.update {
                            it.copy(
                                showAddIncomeDialog = false,
                                notice = MoneyNotice.IncomeRecorded(result.value.source),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                local.update { it.copy(notice = MoneyNotice.InvalidIncomeAmount(e.message.orEmpty())) }
            }
        }
    }

    fun recordPayment(
        amount: String,
        currencyCode: String,
        invoiceId: Long?,
        clientId: Long?,
        method: PaymentMethod,
        reference: String?,
        note: String?,
    ) {
        viewModelScope.launch {
            try {
                val parsedAmount = BigDecimal(amount.trim())
                val curr = Currency.getInstance(currencyCode.trim().uppercase())
                val money = Money(parsedAmount, curr)
                when (val result = recordPaymentUseCase(
                    amount = money,
                    method = method,
                    invoiceId = invoiceId,
                    clientId = clientId,
                    reference = reference,
                    note = note,
                )) {
                    is AppResult.Failure -> local.update { it.copy(notice = MoneyNotice.PaymentFailed(result.error.detail ?: result.error.code.name)) }
                    is AppResult.Success -> {
                        local.update {
                            it.copy(
                                showRecordPaymentDialog = false,
                                notice = MoneyNotice.PaymentRecorded,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                local.update { it.copy(notice = MoneyNotice.InvalidPayment(e.message.orEmpty())) }
            }
        }
    }

    fun createBudget(
        name: String,
        limit: String,
        currencyCode: String,
        category: ExpenseCategory?,
    ) {
        viewModelScope.launch {
            try {
                val parsedLimit = BigDecimal(limit.trim())
                val curr = Currency.getInstance(currencyCode.trim().uppercase())
                val money = Money(parsedLimit, curr)
                // The period runs the current calendar month. Today comes from the injected clock,
                // never LocalDate.now() at a call site (§4 rule 4), so the default is testable.
                val today = clock.today()
                val periodStart = today.withDayOfMonth(1)
                val periodEnd = today.plusMonths(1).withDayOfMonth(1).minusDays(1)
                when (val result = createBudgetUseCase(
                    name = name,
                    limit = money,
                    category = category,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                )) {
                    is AppResult.Failure -> local.update { it.copy(notice = MoneyNotice.BudgetFailed(result.error.detail ?: result.error.code.name)) }
                    is AppResult.Success -> {
                        local.update {
                            it.copy(
                                showAddBudgetDialog = false,
                                notice = MoneyNotice.BudgetCreated(result.value.name),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                local.update { it.copy(notice = MoneyNotice.InvalidBudgetLimit(e.message.orEmpty())) }
            }
        }
    }

    fun createSubscription(
        name: String,
        amount: String,
        currencyCode: String,
        vendor: String?,
        cycle: SubscriptionCycle,
    ) {
        viewModelScope.launch {
            try {
                val parsedAmount = BigDecimal(amount.trim())
                val curr = Currency.getInstance(currencyCode.trim().uppercase())
                val money = Money(parsedAmount, curr)
                // First renewal defaults to a month out, from the injected clock rather than
                // LocalDate.now() at the call site (§4 rule 4) so it stays testable.
                val nextChargeOn = clock.today().plusMonths(1)
                when (val result = createSubscriptionUseCase(
                    name = name,
                    amount = money,
                    vendor = vendor,
                    cycle = cycle,
                    nextChargeOn = nextChargeOn,
                )) {
                    is AppResult.Failure -> local.update { it.copy(notice = MoneyNotice.SubscriptionFailed(result.error.detail ?: result.error.code.name)) }
                    is AppResult.Success -> {
                        local.update {
                            it.copy(
                                showAddSubscriptionDialog = false,
                                notice = MoneyNotice.SubscriptionAdded(result.value.name),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                local.update { it.copy(notice = MoneyNotice.InvalidSubscriptionAmount(e.message.orEmpty())) }
            }
        }
    }

    fun toggleSubscriptionActive(id: Long, currentActive: Boolean) {
        viewModelScope.launch {
            subscriptionRepo.setActive(id, !currentActive)
        }
    }

    fun setCalcExpression(expr: String) {
        local.update { it.copy(calcExpression = expr) }
    }

    fun appendToCalcExpression(part: String) {
        local.update { it.copy(calcExpression = it.calcExpression + part) }
    }

    fun clearCalcExpression() {
        local.update { it.copy(calcExpression = "", calcResult = null, calcError = null) }
    }

    fun evaluateCalculation() {
        val expr = local.value.calcExpression.trim()
        if (expr.isEmpty()) return
        viewModelScope.launch {
            when (val res = calculator.evaluate(expr)) {
                is AppResult.Failure -> {
                    local.update { it.copy(calcError = res.error.detail ?: res.error.code.name, calcResult = null) }
                }
                is AppResult.Success -> {
                    local.update { it.copy(calcResult = res.value, calcError = null) }
                    calculationRepo.record(
                        CalculationRecord(
                            expression = expr,
                            resultText = res.value,
                            createdAt = clock.now(),
                        ),
                    )
                }
            }
        }
    }

    fun clearCalculationHistory() {
        viewModelScope.launch {
            clearCalculationHistoryUseCase()
            local.update { it.copy(notice = MoneyNotice.CalculationHistoryCleared) }
        }
    }

    fun setConvertAmount(amount: String) {
        local.update { it.copy(convertAmount = amount) }
        recomputeConversion()
    }

    fun setConvertFrom(currency: String) {
        local.update { it.copy(convertFrom = currency.uppercase()) }
        recomputeConversion()
    }

    fun setConvertTo(currency: String) {
        local.update { it.copy(convertTo = currency.uppercase()) }
        recomputeConversion()
    }

    fun setManualRate(baseCurrency: String, targetCurrency: String, rateString: String) {
        viewModelScope.launch {
            try {
                val rate = BigDecimal(rateString.trim())
                when (val result = manageExchangeRateUseCase.setRate(baseCurrency.uppercase(), targetCurrency.uppercase(), rate)) {
                    is AppResult.Failure -> local.update { it.copy(notice = MoneyNotice.RateFailed(result.error.detail ?: result.error.code.name)) }
                    is AppResult.Success -> {
                        local.update {
                            it.copy(
                                showAddRateDialog = false,
                                notice = MoneyNotice.RateSaved(result.value.pairKey),
                            )
                        }
                        recomputeConversion()
                    }
                }
            } catch (e: Exception) {
                local.update { it.copy(notice = MoneyNotice.InvalidExchangeRate(e.message.orEmpty())) }
            }
        }
    }

    fun removeManualRate(baseCurrency: String, targetCurrency: String) {
        viewModelScope.launch {
            manageExchangeRateUseCase.removeRate(baseCurrency, targetCurrency)
            local.update { it.copy(notice = MoneyNotice.RateReset(baseCurrency, targetCurrency)) }
            recomputeConversion()
        }
    }

    private fun recomputeConversion() {
        viewModelScope.launch {
            try {
                val amt = BigDecimal(local.value.convertAmount.trim())
                val fromCurr = Currency.getInstance(local.value.convertFrom.trim().uppercase())
                val toCurr = Currency.getInstance(local.value.convertTo.trim().uppercase())
                val money = Money(amt, fromCurr)
                when (val result = manageExchangeRateUseCase.convert(money, toCurr)) {
                    // No rate path for this pair: the converter degrades silently (§3), it does not
                    // shout a code at Abu. The success value is numerals plus a currency code, the
                    // one part that reads the same in either language, so it carries no wording.
                    is AppResult.Failure -> local.update { it.copy(convertedResult = null) }
                    is AppResult.Success -> local.update { it.copy(convertedResult = "${result.value.amount.toPlainString()} ${result.value.currency.currencyCode}") }
                }
            } catch (_: Exception) {
                local.update { it.copy(convertedResult = null) }
            }
        }
    }
}
