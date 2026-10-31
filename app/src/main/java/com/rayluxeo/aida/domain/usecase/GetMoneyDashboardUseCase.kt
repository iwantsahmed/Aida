package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.IncomeRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class MoneyDashboardSummary(
    val incomeThisMonth: Money,
    val expensesThisMonth: Money,
    val netCashFlow: Money,
    val businessIncomeThisMonth: Money,
    val businessExpensesThisMonth: Money,
    val netBusinessSummary: Money,
    val outstandingReceivables: Money,
    val outstandingInvoices: List<Invoice>,
    val paymentsReceivedThisMonth: Money,
    val recentPayments: List<Payment>,
    val expenseCategories: Map<ExpenseCategory, Money>,
    val upcomingSubscriptions: List<Subscription>,
    val activeSubscriptionsMonthlyTotal: Money,
)

/**
 * Builds the live, reactive money dashboard summary for Abu.
 *
 * All figures are calculated with deterministic Kotlin BigDecimal arithmetic,
 * never by an external model.
 */
class GetMoneyDashboardUseCase @Inject constructor(
    private val income: IncomeRepository,
    private val expenses: ExpenseRepository,
    private val payments: PaymentRepository,
    private val invoices: InvoiceRepository,
    private val subscriptions: SubscriptionRepository,
    private val clock: MiraClock,
) {
    operator fun invoke(): Flow<MoneyDashboardSummary> {
        val now = clock.now()
        val zone = clock.zone()
        val today = now.atZone(zone).toLocalDate()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
        val nextMonthStart = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant()

        return combine(
            income.observeBetween(monthStart, nextMonthStart),
            expenses.observeBetween(monthStart, nextMonthStart),
            payments.observeRecent(50),
            invoices.observeUnpaid(),
            subscriptions.observeAll(),
        ) { incomeList, expenseList, paymentList, unpaidInvoices, allSubs ->
            var totalIncome = Money.zero()
            var businessIncome = Money.zero()
            for (item in incomeList) {
                totalIncome += item.amount
                if (item.projectId != null || item.clientId != null || item.paymentId != null) {
                    businessIncome += item.amount
                }
            }

            var totalExpense = Money.zero()
            var businessExpense = Money.zero()
            val categoryBreakdown = mutableMapOf<ExpenseCategory, Money>()
            for (item in expenseList) {
                totalExpense += item.amount
                if (item.isBillable || item.projectId != null || item.clientId != null) {
                    businessExpense += item.amount
                }
                val currentInCat = categoryBreakdown[item.category] ?: Money.zero(item.amount.currency)
                categoryBreakdown[item.category] = currentInCat + item.amount
            }

            var paymentsThisMonth = Money.zero()
            for (payment in paymentList) {
                if (!payment.receivedAt.isBefore(monthStart) && payment.receivedAt.isBefore(nextMonthStart)) {
                    paymentsThisMonth += payment.amount
                }
            }

            var totalReceivables = Money.zero()
            for (invoice in unpaidInvoices) {
                totalReceivables += invoice.outstanding
            }

            val activeSubs = allSubs.filter { it.isActive }
            val upcomingSubs = activeSubs.sortedBy { it.nextChargeOn }.take(10)

            var monthlySubsTotal = Money.zero()
            for (sub in activeSubs) {
                val monthlyEquivalent = when (sub.cycle) {
                    SubscriptionCycle.MONTHLY -> sub.amount
                    SubscriptionCycle.YEARLY -> {
                        val monthlyAmt = sub.amount.amount.divide(BigDecimal("12"), 2, RoundingMode.HALF_UP)
                        Money(monthlyAmt, sub.amount.currency)
                    }
                    SubscriptionCycle.QUARTERLY -> {
                        val monthlyAmt = sub.amount.amount.divide(BigDecimal("3"), 2, RoundingMode.HALF_UP)
                        Money(monthlyAmt, sub.amount.currency)
                    }
                    SubscriptionCycle.WEEKLY -> {
                        val monthlyAmt = sub.amount.amount.multiply(BigDecimal("4.33")).setScale(2, RoundingMode.HALF_UP)
                        Money(monthlyAmt, sub.amount.currency)
                    }
                    SubscriptionCycle.CUSTOM_DAYS -> sub.amount
                }
                monthlySubsTotal += monthlyEquivalent
            }

            MoneyDashboardSummary(
                incomeThisMonth = totalIncome,
                expensesThisMonth = totalExpense,
                netCashFlow = totalIncome - totalExpense,
                businessIncomeThisMonth = businessIncome,
                businessExpensesThisMonth = businessExpense,
                netBusinessSummary = businessIncome - businessExpense,
                outstandingReceivables = totalReceivables,
                outstandingInvoices = unpaidInvoices,
                paymentsReceivedThisMonth = paymentsThisMonth,
                recentPayments = paymentList.take(10),
                expenseCategories = categoryBreakdown,
                upcomingSubscriptions = upcomingSubs,
                activeSubscriptionsMonthlyTotal = monthlySubsTotal,
            )
        }
    }
}
