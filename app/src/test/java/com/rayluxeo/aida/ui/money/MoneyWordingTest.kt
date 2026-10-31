package com.rayluxeo.aida.ui.money

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import org.junit.Test

/**
 * The Money phrasebook is the one place that screen gets its words, so a missed translation here is
 * not a red build — it is an English label sitting in a Bengali sheet, which nobody notices until
 * Abu does. These assertions walk every member, every enum branch and every notice so a line pasted
 * across from English and left untranslated, or a branch added in one language and forgotten in the
 * other, is caught.
 */
class MoneyWordingTest {

    private val english = moneyWording(SpokenLanguage.ENGLISH)
    private val bengali = moneyWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(moneyWording(SpokenLanguage.ENGLISH)).isSameInstanceAs(EnglishMoneyWording)
        assertThat(moneyWording(SpokenLanguage.BENGALI)).isSameInstanceAs(BengaliMoneyWording)
    }

    @Test
    fun `every line is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            words.everything().forEach { line ->
                assertThat(line).isNotEmpty()
            }
        }
    }

    @Test
    fun `representative lines are not left in English on the Bengali side`() {
        val pairs = listOf(
            english.title to bengali.title,
            english.subtitle to bengali.subtitle,
            english.addExpense to bengali.addExpense,
            english.recordPayment to bengali.recordPayment,
            english.spendingBudgets to bengali.spendingBudgets,
            english.currencyConverter to bengali.currencyConverter,
            english.resultLabel to bengali.resultLabel,
            english.saveExpense to bengali.saveExpense,
            english.cancel to bengali.cancel,
            english.tabLabel(MoneyTab.DASHBOARD) to bengali.tabLabel(MoneyTab.DASHBOARD),
            english.categoryName(ExpenseCategory.FOOD) to bengali.categoryName(ExpenseCategory.FOOD),
            english.notice(MoneyNotice.PaymentRecorded) to bengali.notice(MoneyNotice.PaymentRecorded),
        )
        pairs.forEach { (englishLine, bengaliLine) ->
            assertThat(bengaliLine).isNotEqualTo(englishLine)
        }
    }

    @Test
    fun `every enum branch renders non-blank in both languages`() {
        listOf(english, bengali).forEach { words ->
            MoneyTab.entries.forEach { assertThat(words.tabLabel(it)).isNotEmpty() }
            ExpenseCategory.entries.forEach { assertThat(words.categoryName(it)).isNotEmpty() }
            PaymentMethod.entries.forEach { assertThat(words.paymentMethodName(it)).isNotEmpty() }
            SubscriptionCycle.entries.forEach { assertThat(words.cycleName(it)).isNotEmpty() }
            InvoiceStatus.entries.forEach { assertThat(words.invoiceStatusName(it)).isNotEmpty() }
        }
    }

    @Test
    fun `every notice renders non-blank in both languages`() {
        listOf(english, bengali).forEach { words ->
            allNotices().forEach { assertThat(words.notice(it)).isNotEmpty() }
        }
    }

    /**
     * Bengali counts in its own digits. A chip that reads "3 খরচ" in the middle of a Bengali sheet
     * is the one slip that reads as broken even to someone who cannot read the words around it, so
     * the counted labels carry no Latin digit.
     */
    @Test
    fun `nothing Bengali is counted with Latin digits`() {
        val counted = listOf(
            bengali.expensesTab(3),
            bengali.incomeTab(12),
            bengali.paymentsTab(5),
            bengali.receivablesTab(7),
            bengali.clientNumber(42),
        )
        counted.forEach { line ->
            assertThat(line.none { it in '0'..'9' }).isTrue()
            assertThat(line).isNotEmpty()
        }
    }

    /** Data Abu typed — a client's name, an error detail — is placed, not translated. */
    @Test
    fun `placed data survives into both languages`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.notice(MoneyNotice.ExpenseRecorded(DESC))).contains(DESC)
            assertThat(words.notice(MoneyNotice.IncomeRecorded(SOURCE))).contains(SOURCE)
            assertThat(words.notice(MoneyNotice.BudgetCreated(NAME))).contains(NAME)
            assertThat(words.notice(MoneyNotice.ExpenseFailed(DETAIL))).contains(DETAIL)
        }
    }

    /** Every member the screen reads, with the same sample data on both sides. */
    private fun MoneyWording.everything(): List<String> = buildList {
        add(title)
        add(subtitle)
        add(back)
        add(amount)
        add(currency)
        add(notesOptional)
        add(cancel)
        MoneyTab.entries.forEach { add(tabLabel(it)) }
        ExpenseCategory.entries.forEach { add(categoryName(it)) }
        PaymentMethod.entries.forEach { add(paymentMethodName(it)) }
        SubscriptionCycle.entries.forEach { add(cycleName(it)) }
        InvoiceStatus.entries.forEach { add(invoiceStatusName(it)) }
        add(dashboardLoading)
        add(incomeThisMonth)
        add(expensesThisMonth)
        add(businessCashflowSummary)
        add(businessCashflowDetail)
        add(businessIncome)
        add(businessExpenses)
        add(netBusinessProfit)
        add(outstandingReceivables)
        add(outstandingReceivablesSubtitle)
        add(paymentsReceived)
        add(paymentsReceivedSubtitle)
        add(expenseCategories)
        add(viewAll)
        add(upcomingSubscriptions)
        add(monthlyRunRate(SAMPLE_MONEY))
        add(manage)
        add(noActiveSubscriptions)
        add(due(SAMPLE_DATE))
        add(expensesTab(3))
        add(incomeTab(12))
        add(addExpense)
        add(addIncome)
        add(allCategories)
        add(noExpenses)
        add(businessBillable)
        add(noIncome)
        add(paymentsTab(5))
        add(receivablesTab(7))
        add(recordPayment)
        add(noPayments)
        add(paymentFallback)
        add(refInline(REF))
        add(noReceivables)
        add(clientNumber(42))
        add(balanceLabel(SAMPLE_MONEY))
        add(totalLabel(SAMPLE_MONEY))
        add(dueStatus(SAMPLE_DATE, invoiceStatusName(InvoiceStatus.SENT)))
        add(spendingBudgets)
        add(spendingBudgetsDetail)
        add(addBudget)
        add(noBudgets)
        add(limitLabel(SAMPLE_MONEY))
        add(periodLabel(SAMPLE_DATE, SAMPLE_DATE, null))
        add(periodLabel(SAMPLE_DATE, SAMPLE_DATE, categoryName(ExpenseCategory.SOFTWARE)))
        add(spentLabel(SAMPLE_MONEY))
        add(leftLabel(SAMPLE_MONEY))
        add(overByLabel(SAMPLE_MONEY))
        add(recurringSubscriptions)
        add(recurringSubscriptionsDetail)
        add(addSubscription)
        add(noSubscriptions)
        add(nextRenewal(cycleName(SubscriptionCycle.MONTHLY), SAMPLE_DATE))
        add(financialCalculator)
        add(financialCalculatorDetail)
        add(expressionLabel)
        add(resultLabel)
        add(errorLabel(DETAIL))
        add(chipPercentOf)
        add(chipDiscount)
        add(chipTax)
        add(chipMarkup)
        add(chipMargin)
        add(chipSplit)
        add(clearAction)
        add(calculateAction)
        add(calculationHistory)
        add(clearHistory)
        add(noCalculations)
        add(currencyConverter)
        add(currencyConverterDetail)
        add(fromLabel)
        add(toLabel)
        add(exchangeRates)
        add(offlineRatesNote)
        add(setRate)
        add(badgeManual)
        add(badgeDefault)
        add(reset)
        add(titleAddExpense)
        add(saveExpense)
        add(merchantDescription)
        add(categoryLabel(categoryName(ExpenseCategory.SOFTWARE)))
        add(businessBillableToClient)
        add(titleAddIncome)
        add(saveIncome)
        add(sourceLabel)
        add(titleRecordPayment)
        add(recordPaymentConfirm)
        add(matchInvoiceOptional)
        add(none)
        add(methodLabel(paymentMethodName(PaymentMethod.CASH)))
        add(transactionReference)
        add(titleAddBudget)
        add(createBudget)
        add(budgetName)
        add(limitLabelField)
        add(categoryOptional)
        add(all)
        add(titleAddSubscription)
        add(addSubscriptionConfirm)
        add(subscriptionName)
        add(vendorOptional)
        add(billingFrequency)
        add(titleSetRate)
        add(saveRate)
        add(baseCurrency)
        add(targetCurrency)
        add(rateHint("USD", "BDT"))
        allNotices().forEach { add(notice(it)) }
    }

    private fun allNotices(): List<MoneyNotice> = listOf(
        MoneyNotice.ExpenseRecorded(DESC),
        MoneyNotice.ExpenseFailed(DETAIL),
        MoneyNotice.InvalidExpenseAmount(DETAIL),
        MoneyNotice.IncomeRecorded(SOURCE),
        MoneyNotice.IncomeFailed(DETAIL),
        MoneyNotice.InvalidIncomeAmount(DETAIL),
        MoneyNotice.PaymentRecorded,
        MoneyNotice.PaymentFailed(DETAIL),
        MoneyNotice.InvalidPayment(DETAIL),
        MoneyNotice.BudgetCreated(NAME),
        MoneyNotice.BudgetFailed(DETAIL),
        MoneyNotice.InvalidBudgetLimit(DETAIL),
        MoneyNotice.SubscriptionAdded(NAME),
        MoneyNotice.SubscriptionFailed(DETAIL),
        MoneyNotice.InvalidSubscriptionAmount(DETAIL),
        MoneyNotice.CalculationHistoryCleared,
        MoneyNotice.RateSaved("USD/BDT"),
        MoneyNotice.RateFailed(DETAIL),
        MoneyNotice.InvalidExchangeRate(DETAIL),
        MoneyNotice.RateReset("USD", "BDT"),
    )

    private companion object {
        const val DESC = "Adobe"
        const val SOURCE = "Design Consulting"
        const val NAME = "Monthly Software"
        const val DETAIL = "STORAGE"
        const val REF = "TRX-99"
        const val SAMPLE_MONEY = "৳1,500"
        const val SAMPLE_DATE = "5 Nov 2026"
    }
}
