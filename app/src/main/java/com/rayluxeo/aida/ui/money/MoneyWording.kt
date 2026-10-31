package com.rayluxeo.aida.ui.money

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * Every word the Money screen says to Abu, in the language he chose. Same phrasebook shape as the
 * Salah and Clients screens: one interface, one object per language, one selector. Human wording
 * lives here and never in the view model or the domain (§4.6) — the view model only ever hands up a
 * language-free [MoneyNotice], which this phrasebook renders.
 *
 * Money figures and dates are formatted by the screen (through `formatMoney`/`formatDate`) and
 * passed in already shaped for the language, so a line that reads a Bengali count carries Bengali
 * digits. Where a label wraps a value whose Bengali ordering differs from English, the whole line
 * is composed here rather than glued together at the call site.
 */
internal interface MoneyWording {

    // Frame
    val title: String
    val subtitle: String
    val back: String
    fun tabLabel(tab: MoneyTab): String

    // Shared across dialogs
    val amount: String
    val currency: String
    val notesOptional: String
    val cancel: String

    // Enum labels
    fun categoryName(category: ExpenseCategory): String
    fun paymentMethodName(method: PaymentMethod): String
    fun cycleName(cycle: SubscriptionCycle): String
    fun invoiceStatusName(status: InvoiceStatus): String

    // DASHBOARD
    val dashboardLoading: String
    val incomeThisMonth: String
    val expensesThisMonth: String
    val businessCashflowSummary: String
    val businessCashflowDetail: String
    val businessIncome: String
    val businessExpenses: String
    val netBusinessProfit: String
    val outstandingReceivables: String
    val outstandingReceivablesSubtitle: String
    val paymentsReceived: String
    val paymentsReceivedSubtitle: String
    val expenseCategories: String
    val viewAll: String
    val upcomingSubscriptions: String
    fun monthlyRunRate(amount: String): String
    val manage: String
    val noActiveSubscriptions: String
    fun due(date: String): String

    // EXPENSES & INCOME
    fun expensesTab(count: Int): String
    fun incomeTab(count: Int): String
    val addExpense: String
    val addIncome: String
    val allCategories: String
    val noExpenses: String
    val businessBillable: String
    val noIncome: String

    // PAYMENTS & RECEIVABLES
    fun paymentsTab(count: Int): String
    fun receivablesTab(count: Int): String
    val recordPayment: String
    val noPayments: String
    val paymentFallback: String
    fun refInline(reference: String): String
    val noReceivables: String
    fun clientNumber(id: Long): String
    fun balanceLabel(amount: String): String
    fun totalLabel(amount: String): String
    fun dueStatus(date: String, status: String): String

    // BUDGETS & SUBSCRIPTIONS
    val spendingBudgets: String
    val spendingBudgetsDetail: String
    val addBudget: String
    val noBudgets: String
    fun limitLabel(amount: String): String
    fun periodLabel(start: String, end: String, category: String?): String
    fun spentLabel(amount: String): String
    fun leftLabel(amount: String): String
    fun overByLabel(amount: String): String
    val recurringSubscriptions: String
    val recurringSubscriptionsDetail: String
    val addSubscription: String
    val noSubscriptions: String
    fun nextRenewal(cycle: String, date: String): String

    // CALCULATOR
    val financialCalculator: String
    val financialCalculatorDetail: String
    val expressionLabel: String
    val resultLabel: String
    fun errorLabel(detail: String): String
    val chipPercentOf: String
    val chipDiscount: String
    val chipTax: String
    val chipMarkup: String
    val chipMargin: String
    val chipSplit: String
    val clearAction: String
    val calculateAction: String
    val calculationHistory: String
    val clearHistory: String
    val noCalculations: String

    // EXCHANGE RATES
    val currencyConverter: String
    val currencyConverterDetail: String
    val fromLabel: String
    val toLabel: String
    val exchangeRates: String
    val offlineRatesNote: String
    val setRate: String
    val badgeManual: String
    val badgeDefault: String
    val reset: String

    // DIALOGS — add expense
    val titleAddExpense: String
    val saveExpense: String
    val merchantDescription: String
    fun categoryLabel(category: String): String
    val businessBillableToClient: String

    // DIALOGS — add income
    val titleAddIncome: String
    val saveIncome: String
    val sourceLabel: String

    // DIALOGS — record payment
    val titleRecordPayment: String
    val recordPaymentConfirm: String
    val matchInvoiceOptional: String
    val none: String
    fun methodLabel(method: String): String
    val transactionReference: String

    // DIALOGS — add budget
    val titleAddBudget: String
    val createBudget: String
    val budgetName: String
    val limitLabelField: String
    val categoryOptional: String
    val all: String

    // DIALOGS — add subscription
    val titleAddSubscription: String
    val addSubscriptionConfirm: String
    val subscriptionName: String
    val vendorOptional: String
    val billingFrequency: String

    // DIALOGS — set rate
    val titleSetRate: String
    val saveRate: String
    val baseCurrency: String
    val targetCurrency: String
    fun rateHint(base: String, target: String): String

    // NOTICES
    fun notice(notice: MoneyNotice): String
}

internal object EnglishMoneyWording : MoneyWording {

    override val title = "Aida Money"
    override val subtitle = "Cashflow, budgets, rates and intelligence"
    override val back = "Back"

    override fun tabLabel(tab: MoneyTab) = when (tab) {
        MoneyTab.DASHBOARD -> "Dashboard"
        MoneyTab.EXPENSES_INCOME -> "Expenses & Income"
        MoneyTab.PAYMENTS_RECEIVABLES -> "Payments & Invoices"
        MoneyTab.BUDGETS_SUBSCRIPTIONS -> "Budgets & Subs"
        MoneyTab.CALCULATOR -> "Calculator"
        MoneyTab.RATES -> "Exchange Rates"
    }

    override val amount = "Amount"
    override val currency = "Currency"
    override val notesOptional = "Notes (optional)"
    override val cancel = "Cancel"

    override fun categoryName(category: ExpenseCategory) = when (category) {
        ExpenseCategory.SOFTWARE -> "Software"
        ExpenseCategory.HARDWARE -> "Hardware"
        ExpenseCategory.TRAVEL -> "Travel"
        ExpenseCategory.FOOD -> "Food"
        ExpenseCategory.RENT -> "Rent"
        ExpenseCategory.UTILITIES -> "Utilities"
        ExpenseCategory.MARKETING -> "Marketing"
        ExpenseCategory.PROFESSIONAL_FEES -> "Professional fees"
        ExpenseCategory.TAX -> "Tax"
        ExpenseCategory.OTHER -> "Other"
    }

    override fun paymentMethodName(method: PaymentMethod) = when (method) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.BANK_TRANSFER -> "Bank transfer"
        PaymentMethod.MOBILE_WALLET -> "Mobile wallet"
        PaymentMethod.CARD -> "Card"
        PaymentMethod.CHEQUE -> "Cheque"
        PaymentMethod.OTHER -> "Other"
    }

    override fun cycleName(cycle: SubscriptionCycle) = when (cycle) {
        SubscriptionCycle.WEEKLY -> "Weekly"
        SubscriptionCycle.MONTHLY -> "Monthly"
        SubscriptionCycle.QUARTERLY -> "Quarterly"
        SubscriptionCycle.YEARLY -> "Yearly"
        SubscriptionCycle.CUSTOM_DAYS -> "Custom"
    }

    override fun invoiceStatusName(status: InvoiceStatus) = when (status) {
        InvoiceStatus.DRAFT -> "Draft"
        InvoiceStatus.READY -> "Ready"
        InvoiceStatus.SENT -> "Sent"
        InvoiceStatus.PARTIALLY_PAID -> "Partially paid"
        InvoiceStatus.PAID -> "Paid"
        InvoiceStatus.OVERDUE -> "Overdue"
        InvoiceStatus.VOID -> "Void"
    }

    override val dashboardLoading = "Gathering cashflow, invoice receivables and subscription commitments…"
    override val incomeThisMonth = "Income This Month"
    override val expensesThisMonth = "Expenses This Month"
    override val businessCashflowSummary = "Business Cashflow Summary"
    override val businessCashflowDetail = "Dedicated business client income minus business-related operating expenses"
    override val businessIncome = "Business Income"
    override val businessExpenses = "Business Expenses"
    override val netBusinessProfit = "Net Business Profit"
    override val outstandingReceivables = "Outstanding Receivables"
    override val outstandingReceivablesSubtitle = "Unpaid customer invoices"
    override val paymentsReceived = "Payments Received"
    override val paymentsReceivedSubtitle = "Collected this month"
    override val expenseCategories = "Expense Categories"
    override val viewAll = "View All"
    override val upcomingSubscriptions = "Upcoming Subscriptions"
    override fun monthlyRunRate(amount: String) = "Monthly run-rate: $amount"
    override val manage = "Manage"
    override val noActiveSubscriptions = "No active recurring subscriptions"
    override fun due(date: String) = "Due $date"

    override fun expensesTab(count: Int) = "Expenses ($count)"
    override fun incomeTab(count: Int) = "Income ($count)"
    override val addExpense = "Add Expense"
    override val addIncome = "Add Income"
    override val allCategories = "All Categories"
    override val noExpenses = "No expenses recorded. Speak 'I spent 6500 taka on Adobe' or add manually."
    override val businessBillable = "• Business / Billable"
    override val noIncome = "No income entries recorded yet."

    override fun paymentsTab(count: Int) = "Payments ($count)"
    override fun receivablesTab(count: Int) = "Receivables ($count)"
    override val recordPayment = "Record Payment"
    override val noPayments = "No payments received yet."
    override val paymentFallback = "Payment"
    override fun refInline(reference: String) = "• Ref: $reference"
    override val noReceivables = "All issued invoices are settled. No outstanding receivables."
    override fun clientNumber(id: Long) = "Client #$id"
    override fun balanceLabel(amount: String) = "Balance: $amount"
    override fun totalLabel(amount: String) = "Total: $amount"
    override fun dueStatus(date: String, status: String) = "Due $date • $status"

    override val spendingBudgets = "Spending Budgets"
    override val spendingBudgetsDetail = "Monthly category limits to keep expenditure under control"
    override val addBudget = "Add Budget"
    override val noBudgets = "No budgets configured. Add monthly ceilings for Software, Office, or Personal spending."
    override fun limitLabel(amount: String) = "Limit: $amount"
    override fun periodLabel(start: String, end: String, category: String?) =
        "Period: $start to $end" + (category?.let { " • Category: $it" } ?: " • All Categories")
    override fun spentLabel(amount: String) = "Spent: $amount"
    override fun leftLabel(amount: String) = "Left: $amount"
    override fun overByLabel(amount: String) = "Over by $amount"
    override val recurringSubscriptions = "Recurring Subscriptions"
    override val recurringSubscriptionsDetail = "SaaS, hosting, domains and memberships"
    override val addSubscription = "Add Subscription"
    override val noSubscriptions = "No subscriptions added yet. Track Adobe, JetBrains, GitHub, or Figma renewals."
    override fun nextRenewal(cycle: String, date: String) = "$cycle • Next renewal: $date"

    override val financialCalculator = "Financial Calculator"
    override val financialCalculatorDetail =
        "Supports percentages, discounts, markup, margin and amount splitting with precise Kotlin arithmetic."
    override val expressionLabel = "Expression (e.g., 30% of 450000, 12000 with 15% discount, split 20000 by 4)"
    override val resultLabel = "Result:"
    override fun errorLabel(detail: String) = "Error: $detail"
    override val chipPercentOf = "% of"
    override val chipDiscount = "10% discount"
    override val chipTax = "15% tax"
    override val chipMarkup = "20% markup"
    override val chipMargin = "25% margin"
    override val chipSplit = "split by 2"
    override val clearAction = "Clear"
    override val calculateAction = "Calculate"
    override val calculationHistory = "Calculation History"
    override val clearHistory = "Clear History"
    override val noCalculations = "No calculations recorded yet."

    override val currencyConverter = "Currency Converter"
    override val currencyConverterDetail = "Offline-first conversions using deterministic manual or bundled rates"
    override val fromLabel = "From"
    override val toLabel = "To"
    override val exchangeRates = "Exchange Rates"
    override val offlineRatesNote = "Always retain offline rates • Free tier only"
    override val setRate = "Set Rate"
    override val badgeManual = "MANUAL"
    override val badgeDefault = "DEFAULT"
    override val reset = "Reset"

    override val titleAddExpense = "Add Expense"
    override val saveExpense = "Save Expense"
    override val merchantDescription = "Merchant / Description (e.g. Adobe)"
    override fun categoryLabel(category: String) = "Category: $category"
    override val businessBillableToClient = "Business / Billable to client"

    override val titleAddIncome = "Add Income Entry"
    override val saveIncome = "Save Income"
    override val sourceLabel = "Source (e.g. Design Consulting)"

    override val titleRecordPayment = "Record Received Payment"
    override val recordPaymentConfirm = "Record Payment"
    override val matchInvoiceOptional = "Match Invoice (Optional):"
    override val none = "None"
    override fun methodLabel(method: String) = "Method: $method"
    override val transactionReference = "Transaction Reference / bKash TrxID"

    override val titleAddBudget = "Add Spending Budget"
    override val createBudget = "Create Budget"
    override val budgetName = "Budget Name (e.g. Monthly Software)"
    override val limitLabelField = "Limit"
    override val categoryOptional = "Category (Optional):"
    override val all = "All"

    override val titleAddSubscription = "Add Subscription"
    override val addSubscriptionConfirm = "Add Subscription"
    override val subscriptionName = "Subscription Name (e.g. Adobe Creative Cloud)"
    override val vendorOptional = "Vendor (optional)"
    override val billingFrequency = "Billing Frequency:"

    override val titleSetRate = "Set Manual Exchange Rate"
    override val saveRate = "Save Rate"
    override val baseCurrency = "Base Currency"
    override val targetCurrency = "Target Currency"
    override fun rateHint(base: String, target: String) = "Rate (1 $base in $target)"

    override fun notice(notice: MoneyNotice): String = when (notice) {
        is MoneyNotice.ExpenseRecorded -> "Recorded expense: ${notice.description}"
        is MoneyNotice.ExpenseFailed -> "Failed to record expense: ${notice.detail}"
        is MoneyNotice.InvalidExpenseAmount -> "Invalid expense amount: ${notice.detail}"
        is MoneyNotice.IncomeRecorded -> "Recorded income from ${notice.source}"
        is MoneyNotice.IncomeFailed -> "Failed to record income: ${notice.detail}"
        is MoneyNotice.InvalidIncomeAmount -> "Invalid income amount: ${notice.detail}"
        MoneyNotice.PaymentRecorded -> "Payment recorded successfully"
        is MoneyNotice.PaymentFailed -> "Failed to record payment: ${notice.detail}"
        is MoneyNotice.InvalidPayment -> "Invalid payment: ${notice.detail}"
        is MoneyNotice.BudgetCreated -> "Budget '${notice.name}' created"
        is MoneyNotice.BudgetFailed -> "Failed to create budget: ${notice.detail}"
        is MoneyNotice.InvalidBudgetLimit -> "Invalid budget limit: ${notice.detail}"
        is MoneyNotice.SubscriptionAdded -> "Subscription '${notice.name}' added"
        is MoneyNotice.SubscriptionFailed -> "Failed to add subscription: ${notice.detail}"
        is MoneyNotice.InvalidSubscriptionAmount -> "Invalid subscription amount: ${notice.detail}"
        MoneyNotice.CalculationHistoryCleared -> "Calculation history cleared"
        is MoneyNotice.RateSaved -> "Saved manual rate for ${notice.pairKey}"
        is MoneyNotice.RateFailed -> "Failed to save rate: ${notice.detail}"
        is MoneyNotice.InvalidExchangeRate -> "Invalid exchange rate: ${notice.detail}"
        is MoneyNotice.RateReset -> "Reset rate for ${notice.base}/${notice.target} to system default"
    }
}

internal object BengaliMoneyWording : MoneyWording {

    override val title = "আইদা মানি"
    override val subtitle = "নগদ প্রবাহ, বাজেট, রেট আর বিশ্লেষণ"
    override val back = "পেছনে যান"

    override fun tabLabel(tab: MoneyTab) = when (tab) {
        MoneyTab.DASHBOARD -> "ড্যাশবোর্ড"
        MoneyTab.EXPENSES_INCOME -> "খরচ ও আয়"
        MoneyTab.PAYMENTS_RECEIVABLES -> "পেমেন্ট ও ইনভয়েস"
        MoneyTab.BUDGETS_SUBSCRIPTIONS -> "বাজেট ও সাবস্ক্রিপশন"
        MoneyTab.CALCULATOR -> "ক্যালকুলেটর"
        MoneyTab.RATES -> "বিনিময় হার"
    }

    override val amount = "পরিমাণ"
    override val currency = "মুদ্রা"
    override val notesOptional = "নোট (ঐচ্ছিক)"
    override val cancel = "বাতিল"

    override fun categoryName(category: ExpenseCategory) = when (category) {
        ExpenseCategory.SOFTWARE -> "সফটওয়্যার"
        ExpenseCategory.HARDWARE -> "হার্ডওয়্যার"
        ExpenseCategory.TRAVEL -> "ভ্রমণ"
        ExpenseCategory.FOOD -> "খাবার"
        ExpenseCategory.RENT -> "ভাড়া"
        ExpenseCategory.UTILITIES -> "ইউটিলিটি"
        ExpenseCategory.MARKETING -> "মার্কেটিং"
        ExpenseCategory.PROFESSIONAL_FEES -> "পেশাদার ফি"
        ExpenseCategory.TAX -> "ট্যাক্স"
        ExpenseCategory.OTHER -> "অন্যান্য"
    }

    override fun paymentMethodName(method: PaymentMethod) = when (method) {
        PaymentMethod.CASH -> "নগদ"
        PaymentMethod.BANK_TRANSFER -> "ব্যাংক ট্রান্সফার"
        PaymentMethod.MOBILE_WALLET -> "মোবাইল ওয়ালেট"
        PaymentMethod.CARD -> "কার্ড"
        PaymentMethod.CHEQUE -> "চেক"
        PaymentMethod.OTHER -> "অন্যান্য"
    }

    override fun cycleName(cycle: SubscriptionCycle) = when (cycle) {
        SubscriptionCycle.WEEKLY -> "সাপ্তাহিক"
        SubscriptionCycle.MONTHLY -> "মাসিক"
        SubscriptionCycle.QUARTERLY -> "ত্রৈমাসিক"
        SubscriptionCycle.YEARLY -> "বার্ষিক"
        SubscriptionCycle.CUSTOM_DAYS -> "নিজস্ব"
    }

    override fun invoiceStatusName(status: InvoiceStatus) = when (status) {
        InvoiceStatus.DRAFT -> "খসড়া"
        InvoiceStatus.READY -> "প্রস্তুত"
        InvoiceStatus.SENT -> "পাঠানো হয়েছে"
        InvoiceStatus.PARTIALLY_PAID -> "আংশিক পরিশোধিত"
        InvoiceStatus.PAID -> "পরিশোধিত"
        InvoiceStatus.OVERDUE -> "মেয়াদোত্তীর্ণ"
        InvoiceStatus.VOID -> "বাতিল"
    }

    override val dashboardLoading = "নগদ প্রবাহ, ইনভয়েসের পাওনা আর সাবস্ক্রিপশনের দায় একত্র করা হচ্ছে…"
    override val incomeThisMonth = "এ মাসের আয়"
    override val expensesThisMonth = "এ মাসের খরচ"
    override val businessCashflowSummary = "ব্যবসার নগদ প্রবাহের সারসংক্ষেপ"
    override val businessCashflowDetail = "ব্যবসার ক্লায়েন্ট আয় থেকে ব্যবসা-সম্পর্কিত পরিচালন খরচ বাদ দিয়ে"
    override val businessIncome = "ব্যবসার আয়"
    override val businessExpenses = "ব্যবসার খরচ"
    override val netBusinessProfit = "নিট ব্যবসায়িক মুনাফা"
    override val outstandingReceivables = "বকেয়া পাওনা"
    override val outstandingReceivablesSubtitle = "অপরিশোধিত ক্লায়েন্ট ইনভয়েস"
    override val paymentsReceived = "প্রাপ্ত পেমেন্ট"
    override val paymentsReceivedSubtitle = "এ মাসে আদায় হয়েছে"
    override val expenseCategories = "খরচের বিভাগ"
    override val viewAll = "সব দেখুন"
    override val upcomingSubscriptions = "আসন্ন সাবস্ক্রিপশন"
    override fun monthlyRunRate(amount: String) = "মাসিক গড়: $amount"
    override val manage = "ব্যবস্থাপনা"
    override val noActiveSubscriptions = "কোনো সক্রিয় নিয়মিত সাবস্ক্রিপশন নেই"
    override fun due(date: String) = "প্রদেয় $date"

    override fun expensesTab(count: Int) = "খরচ (${bengaliNumerals(count.toString())})"
    override fun incomeTab(count: Int) = "আয় (${bengaliNumerals(count.toString())})"
    override val addExpense = "খরচ যোগ করুন"
    override val addIncome = "আয় যোগ করুন"
    override val allCategories = "সব বিভাগ"
    override val noExpenses = "কোনো খরচ লেখা হয়নি। বলুন 'অ্যাডোবিতে ৬৫০০ টাকা খরচ করেছি', বা নিজে যোগ করুন।"
    override val businessBillable = "• ব্যবসা / বিলযোগ্য"
    override val noIncome = "এখনো কোনো আয়ের এন্ট্রি লেখা হয়নি।"

    override fun paymentsTab(count: Int) = "পেমেন্ট (${bengaliNumerals(count.toString())})"
    override fun receivablesTab(count: Int) = "পাওনা (${bengaliNumerals(count.toString())})"
    override val recordPayment = "পেমেন্ট লিখুন"
    override val noPayments = "এখনো কোনো পেমেন্ট পাওয়া যায়নি।"
    override val paymentFallback = "পেমেন্ট"
    override fun refInline(reference: String) = "• রেফ: $reference"
    override val noReceivables = "জারি করা সব ইনভয়েস পরিশোধিত। কোনো বকেয়া পাওনা নেই।"
    override fun clientNumber(id: Long) = "ক্লায়েন্ট #${bengaliNumerals(id.toString())}"
    override fun balanceLabel(amount: String) = "বাকি: $amount"
    override fun totalLabel(amount: String) = "মোট: $amount"
    override fun dueStatus(date: String, status: String) = "প্রদেয় $date • $status"

    override val spendingBudgets = "খরচের বাজেট"
    override val spendingBudgetsDetail = "খরচ নিয়ন্ত্রণে রাখতে মাসিক বিভাগভিত্তিক সীমা"
    override val addBudget = "বাজেট যোগ করুন"
    override val noBudgets = "কোনো বাজেট ঠিক করা নেই। সফটওয়্যার, অফিস বা ব্যক্তিগত খরচের মাসিক সীমা যোগ করুন।"
    override fun limitLabel(amount: String) = "সীমা: $amount"
    override fun periodLabel(start: String, end: String, category: String?) =
        "মেয়াদ: $start — $end" + (category?.let { " • বিভাগ: $it" } ?: " • সব বিভাগ")
    override fun spentLabel(amount: String) = "খরচ হয়েছে: $amount"
    override fun leftLabel(amount: String) = "বাকি: $amount"
    override fun overByLabel(amount: String) = "সীমা ছাড়িয়েছে: $amount"
    override val recurringSubscriptions = "নিয়মিত সাবস্ক্রিপশন"
    override val recurringSubscriptionsDetail = "SaaS, হোস্টিং, ডোমেইন আর সদস্যপদ"
    override val addSubscription = "সাবস্ক্রিপশন যোগ করুন"
    override val noSubscriptions = "এখনো কোনো সাবস্ক্রিপশন যোগ করা হয়নি। অ্যাডোবি, জেটব্রেইনস, গিটহাব বা ফিগমার নবায়ন রাখুন।"
    override fun nextRenewal(cycle: String, date: String) = "$cycle • পরবর্তী নবায়ন: $date"

    override val financialCalculator = "আর্থিক ক্যালকুলেটর"
    override val financialCalculatorDetail =
        "নিখুঁত কোটলিন হিসাবে শতাংশ, ছাড়, মার্কআপ, মার্জিন আর পরিমাণ ভাগ করা সমর্থন করে।"
    override val expressionLabel = "এক্সপ্রেশন (যেমন, 30% of 450000, 12000 with 15% discount, split 20000 by 4)"
    override val resultLabel = "ফল:"
    override fun errorLabel(detail: String) = "সমস্যা: $detail"
    override val chipPercentOf = "% of"
    override val chipDiscount = "১০% ছাড়"
    override val chipTax = "১৫% কর"
    override val chipMarkup = "২০% মার্কআপ"
    override val chipMargin = "২৫% মার্জিন"
    override val chipSplit = "২ ভাগ"
    override val clearAction = "মুছুন"
    override val calculateAction = "হিসাব করুন"
    override val calculationHistory = "হিসাবের ইতিহাস"
    override val clearHistory = "ইতিহাস মুছুন"
    override val noCalculations = "এখনো কোনো হিসাব করা হয়নি।"

    override val currencyConverter = "মুদ্রা রূপান্তর"
    override val currencyConverterDetail = "নির্ধারিত ম্যানুয়াল বা বিল্ট-ইন রেট দিয়ে অফলাইন-প্রথম রূপান্তর"
    override val fromLabel = "থেকে"
    override val toLabel = "তে"
    override val exchangeRates = "বিনিময় হার"
    override val offlineRatesNote = "সবসময় অফলাইন রেট রাখা হয় • শুধু ফ্রি টায়ার"
    override val setRate = "রেট দিন"
    override val badgeManual = "ম্যানুয়াল"
    override val badgeDefault = "ডিফল্ট"
    override val reset = "রিসেট"

    override val titleAddExpense = "খরচ যোগ করুন"
    override val saveExpense = "খরচ সেভ করুন"
    override val merchantDescription = "বিক্রেতা / বিবরণ (যেমন অ্যাডোবি)"
    override fun categoryLabel(category: String) = "বিভাগ: $category"
    override val businessBillableToClient = "ব্যবসা / ক্লায়েন্টের কাছে বিলযোগ্য"

    override val titleAddIncome = "আয়ের এন্ট্রি যোগ করুন"
    override val saveIncome = "আয় সেভ করুন"
    override val sourceLabel = "উৎস (যেমন ডিজাইন কনসালটিং)"

    override val titleRecordPayment = "প্রাপ্ত পেমেন্ট লিখুন"
    override val recordPaymentConfirm = "পেমেন্ট লিখুন"
    override val matchInvoiceOptional = "ইনভয়েস মেলান (ঐচ্ছিক):"
    override val none = "কোনোটি নয়"
    override fun methodLabel(method: String) = "পদ্ধতি: $method"
    override val transactionReference = "লেনদেন রেফারেন্স / বিকাশ TrxID"

    override val titleAddBudget = "খরচের বাজেট যোগ করুন"
    override val createBudget = "বাজেট তৈরি করুন"
    override val budgetName = "বাজেটের নাম (যেমন মাসিক সফটওয়্যার)"
    override val limitLabelField = "সীমা"
    override val categoryOptional = "বিভাগ (ঐচ্ছিক):"
    override val all = "সব"

    override val titleAddSubscription = "সাবস্ক্রিপশন যোগ করুন"
    override val addSubscriptionConfirm = "সাবস্ক্রিপশন যোগ করুন"
    override val subscriptionName = "সাবস্ক্রিপশনের নাম (যেমন অ্যাডোবি ক্রিয়েটিভ ক্লাউড)"
    override val vendorOptional = "বিক্রেতা (ঐচ্ছিক)"
    override val billingFrequency = "বিলিং ফ্রিকোয়েন্সি:"

    override val titleSetRate = "ম্যানুয়াল বিনিময় হার দিন"
    override val saveRate = "রেট সেভ করুন"
    override val baseCurrency = "মূল মুদ্রা"
    override val targetCurrency = "লক্ষ্য মুদ্রা"
    override fun rateHint(base: String, target: String) = "রেট (১ $base কত $target)"

    override fun notice(notice: MoneyNotice): String = when (notice) {
        is MoneyNotice.ExpenseRecorded -> "খরচ লিখে রেখেছি: ${notice.description}"
        is MoneyNotice.ExpenseFailed -> "খরচ লিখে রাখা গেল না: ${notice.detail}"
        is MoneyNotice.InvalidExpenseAmount -> "খরচের পরিমাণ ঠিক নেই: ${notice.detail}"
        is MoneyNotice.IncomeRecorded -> "আয় লিখে রেখেছি — ${notice.source}"
        is MoneyNotice.IncomeFailed -> "আয় লিখে রাখা গেল না: ${notice.detail}"
        is MoneyNotice.InvalidIncomeAmount -> "আয়ের পরিমাণ ঠিক নেই: ${notice.detail}"
        MoneyNotice.PaymentRecorded -> "পেমেন্ট লিখে রাখা হয়েছে"
        is MoneyNotice.PaymentFailed -> "পেমেন্ট লিখে রাখা গেল না: ${notice.detail}"
        is MoneyNotice.InvalidPayment -> "পেমেন্ট ঠিক নেই: ${notice.detail}"
        is MoneyNotice.BudgetCreated -> "'${notice.name}' বাজেট তৈরি হয়েছে"
        is MoneyNotice.BudgetFailed -> "বাজেট তৈরি করা গেল না: ${notice.detail}"
        is MoneyNotice.InvalidBudgetLimit -> "বাজেটের সীমা ঠিক নেই: ${notice.detail}"
        is MoneyNotice.SubscriptionAdded -> "'${notice.name}' সাবস্ক্রিপশন যোগ হয়েছে"
        is MoneyNotice.SubscriptionFailed -> "সাবস্ক্রিপশন যোগ করা গেল না: ${notice.detail}"
        is MoneyNotice.InvalidSubscriptionAmount -> "সাবস্ক্রিপশনের পরিমাণ ঠিক নেই: ${notice.detail}"
        MoneyNotice.CalculationHistoryCleared -> "হিসাবের ইতিহাস মুছে ফেলা হয়েছে"
        is MoneyNotice.RateSaved -> "${notice.pairKey}-এর ম্যানুয়াল রেট সেভ হয়েছে"
        is MoneyNotice.RateFailed -> "রেট সেভ করা গেল না: ${notice.detail}"
        is MoneyNotice.InvalidExchangeRate -> "বিনিময় হার ঠিক নেই: ${notice.detail}"
        is MoneyNotice.RateReset -> "${notice.base}/${notice.target} রেট সিস্টেম ডিফল্টে ফেরানো হয়েছে"
    }
}

internal fun moneyWording(language: SpokenLanguage): MoneyWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishMoneyWording
    SpokenLanguage.BENGALI -> BengaliMoneyWording
}

/**
 * What the Money screen has to tell Abu after an action, as language-free data. The view model
 * builds one of these; [MoneyWording.notice] renders it in Abu's language. A technical [detail]
 * (an error code or a parse message) rides through untranslated — it is a diagnostic, not prose.
 */
sealed interface MoneyNotice {
    data class ExpenseRecorded(val description: String) : MoneyNotice
    data class ExpenseFailed(val detail: String) : MoneyNotice
    data class InvalidExpenseAmount(val detail: String) : MoneyNotice
    data class IncomeRecorded(val source: String) : MoneyNotice
    data class IncomeFailed(val detail: String) : MoneyNotice
    data class InvalidIncomeAmount(val detail: String) : MoneyNotice
    data object PaymentRecorded : MoneyNotice
    data class PaymentFailed(val detail: String) : MoneyNotice
    data class InvalidPayment(val detail: String) : MoneyNotice
    data class BudgetCreated(val name: String) : MoneyNotice
    data class BudgetFailed(val detail: String) : MoneyNotice
    data class InvalidBudgetLimit(val detail: String) : MoneyNotice
    data class SubscriptionAdded(val name: String) : MoneyNotice
    data class SubscriptionFailed(val detail: String) : MoneyNotice
    data class InvalidSubscriptionAmount(val detail: String) : MoneyNotice
    data object CalculationHistoryCleared : MoneyNotice
    data class RateSaved(val pairKey: String) : MoneyNotice
    data class RateFailed(val detail: String) : MoneyNotice
    data class InvalidExchangeRate(val detail: String) : MoneyNotice
    data class RateReset(val base: String, val target: String) : MoneyNotice
}
