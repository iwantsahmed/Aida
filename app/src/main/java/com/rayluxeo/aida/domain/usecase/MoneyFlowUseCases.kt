package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Budget
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.ExchangeRate
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.repository.BudgetRepository
import com.rayluxeo.aida.domain.repository.CalculationRepository
import com.rayluxeo.aida.domain.repository.ExchangeRateRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.IncomeRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

class RecordExpenseUseCase @Inject constructor(
    private val expenses: ExpenseRepository,
    private val activityLog: com.rayluxeo.aida.domain.repository.ActivityLogRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        amount: Money,
        description: String,
        category: ExpenseCategory = ExpenseCategory.OTHER,
        clientId: Long? = null,
        projectId: Long? = null,
        receiptFileId: Long? = null,
        isBillable: Boolean = false,
        spentAt: Instant? = null,
    ): AppResult<Expense> {
        if (amount.amount.signum() <= 0) {
            return failure(MiraErrorCode.VALIDATION, "An expense amount must be greater than zero")
        }
        val desc = description.trim()
        if (desc.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "An expense description or merchant is required")
        }
        val now = clock.now()
        val expense = Expense(
            amount = amount,
            description = desc,
            category = category,
            clientId = clientId,
            projectId = projectId,
            receiptFileId = receiptFileId,
            isBillable = isBillable,
            spentAt = spentAt ?: now,
            createdAt = now,
            updatedAt = now,
        )
        val result = expenses.create(expense)
        if (result is AppResult.Success) {
            activityLog.record(
                com.rayluxeo.aida.domain.model.ActivityEntry(
                    category = com.rayluxeo.aida.domain.model.ActivityCategory.MONEY,
                    action = "expense.recorded",
                    summary = "Expense recorded: ${amount.amount} ${amount.currency.currencyCode} - $desc",
                    subject = com.rayluxeo.aida.domain.model.ActivitySubject("expenses", result.value.id),
                    occurredAt = now,
                )
            )
        }
        return result
    }
}

class RecordIncomeUseCase @Inject constructor(
    private val income: IncomeRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        amount: Money,
        source: String,
        description: String? = null,
        clientId: Long? = null,
        projectId: Long? = null,
        paymentId: Long? = null,
        receivedAt: Instant? = null,
    ): AppResult<IncomeEntry> {
        if (amount.amount.signum() <= 0) {
            return failure(MiraErrorCode.VALIDATION, "Income amount must be greater than zero")
        }
        val src = source.trim()
        if (src.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "Income source is required")
        }
        val now = clock.now()
        val entry = IncomeEntry(
            source = src,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            amount = amount,
            receivedAt = receivedAt ?: now,
            clientId = clientId,
            projectId = projectId,
            paymentId = paymentId,
            createdAt = now,
            updatedAt = now,
        )
        return income.create(entry)
    }
}

class CreateBudgetUseCase @Inject constructor(
    private val budgets: BudgetRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        name: String,
        limit: Money,
        category: ExpenseCategory? = null,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): AppResult<Budget> {
        if (limit.amount.signum() <= 0) {
            return failure(MiraErrorCode.VALIDATION, "Budget limit must be greater than zero")
        }
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "Budget name is required")
        }
        if (periodEnd.isBefore(periodStart)) {
            return failure(MiraErrorCode.VALIDATION, "Budget period end must not precede period start")
        }
        val now = clock.now()
        val budget = Budget(
            name = trimmedName,
            category = category,
            periodStart = periodStart,
            periodEnd = periodEnd,
            limit = limit,
            createdAt = now,
            updatedAt = now,
        )
        return budgets.create(budget)
    }
}

class CreateSubscriptionUseCase @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        name: String,
        amount: Money,
        vendor: String? = null,
        cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
        cycleDays: Int? = null,
        nextChargeOn: LocalDate,
        reminderId: Long? = null,
    ): AppResult<Subscription> {
        if (amount.amount.signum() <= 0) {
            return failure(MiraErrorCode.VALIDATION, "Subscription amount must be greater than zero")
        }
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "Subscription name is required")
        }
        val now = clock.now()
        val sub = Subscription(
            name = trimmedName,
            vendor = vendor?.trim()?.takeIf { it.isNotEmpty() },
            amount = amount,
            cycle = cycle,
            cycleDays = cycleDays,
            nextChargeOn = nextChargeOn,
            reminderId = reminderId,
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        return subscriptions.create(sub)
    }
}

class ManageExchangeRateUseCase @Inject constructor(
    private val exchangeRates: ExchangeRateRepository,
) {
    suspend fun setRate(baseCurrency: String, targetCurrency: String, rate: BigDecimal): AppResult<ExchangeRate> {
        if (rate.signum() <= 0) {
            return failure(MiraErrorCode.VALIDATION, "Exchange rate must be positive")
        }
        return exchangeRates.setManualRate(baseCurrency, targetCurrency, rate)
    }

    suspend fun removeRate(baseCurrency: String, targetCurrency: String): AppResult<Unit> =
        exchangeRates.removeManualRate(baseCurrency, targetCurrency)

    suspend fun convert(amount: Money, targetCurrency: Currency): AppResult<Money> =
        exchangeRates.convert(amount, targetCurrency)
}

class ClearCalculationHistoryUseCase @Inject constructor(
    private val calculations: CalculationRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = calculations.clear()
}
