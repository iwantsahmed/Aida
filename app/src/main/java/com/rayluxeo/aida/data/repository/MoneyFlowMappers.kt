package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.db.entity.BudgetEntity
import com.rayluxeo.aida.data.db.entity.CalculationRecordEntity
import com.rayluxeo.aida.data.db.entity.ExpenseEntity
import com.rayluxeo.aida.data.db.entity.IncomeEntryEntity
import com.rayluxeo.aida.data.db.entity.PaymentEntity
import com.rayluxeo.aida.data.db.entity.SubscriptionEntity
import com.rayluxeo.aida.domain.model.Budget
import com.rayluxeo.aida.domain.model.CalculationRecord
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.Subscription
import java.time.Instant
import java.time.LocalDate

/**
 * The boundary where storage units stop.
 *
 * Three conversions happen here and nowhere else. Money crosses as a whole number of minor units
 * beside its currency code and comes back as a `Money`; nothing between a DAO and a screen ever
 * sees a `Double`. Instants cross as epoch millis, dates with no time of day as epoch days —
 * a budget's period is a run of days and must not acquire a timezone on the way through.
 *
 * Mapping is deliberately total in both directions: an entity maps to exactly one domain value
 * and back to the same row, so a read-modify-write cannot lose a field by omission.
 */
internal fun PaymentEntity.toDomain(): Payment = Payment(
    id = id,
    invoiceId = invoiceId,
    clientId = clientId,
    amount = MoneyAmount.fromMinorUnits(amountMinor, currencyCode),
    method = method,
    receivedAt = Instant.ofEpochMilli(receivedAtEpochMillis),
    reference = reference,
    note = note,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun Payment.toEntity(): PaymentEntity = PaymentEntity(
    id = id,
    invoiceId = invoiceId,
    clientId = clientId,
    amountMinor = MoneyAmount.toMinorUnits(amount),
    currencyCode = amount.currency.currencyCode,
    method = method,
    receivedAtEpochMillis = receivedAt.toEpochMilli(),
    reference = reference,
    note = note,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    description = description,
    category = category,
    amount = MoneyAmount.fromMinorUnits(amountMinor, currencyCode),
    spentAt = Instant.ofEpochMilli(spentAtEpochMillis),
    clientId = clientId,
    projectId = projectId,
    receiptFileId = receiptFileId,
    isBillable = isBillable,
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    description = description,
    category = category,
    amountMinor = MoneyAmount.toMinorUnits(amount),
    currencyCode = amount.currency.currencyCode,
    spentAtEpochMillis = spentAt.toEpochMilli(),
    clientId = clientId,
    projectId = projectId,
    receiptFileId = receiptFileId,
    isBillable = isBillable,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun IncomeEntryEntity.toDomain(): IncomeEntry = IncomeEntry(
    id = id,
    source = source,
    description = description,
    amount = MoneyAmount.fromMinorUnits(amountMinor, currencyCode),
    receivedAt = Instant.ofEpochMilli(receivedAtEpochMillis),
    clientId = clientId,
    projectId = projectId,
    paymentId = paymentId,
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun IncomeEntry.toEntity(): IncomeEntryEntity = IncomeEntryEntity(
    id = id,
    source = source,
    description = description,
    amountMinor = MoneyAmount.toMinorUnits(amount),
    currencyCode = amount.currency.currencyCode,
    receivedAtEpochMillis = receivedAt.toEpochMilli(),
    clientId = clientId,
    projectId = projectId,
    paymentId = paymentId,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    name = name,
    category = category,
    periodStart = LocalDate.ofEpochDay(periodStartEpochDay),
    periodEnd = LocalDate.ofEpochDay(periodEndEpochDay),
    limit = MoneyAmount.fromMinorUnits(limitMinor, currencyCode),
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    name = name,
    category = category,
    periodStartEpochDay = periodStart.toEpochDay(),
    periodEndEpochDay = periodEnd.toEpochDay(),
    limitMinor = MoneyAmount.toMinorUnits(limit),
    currencyCode = limit.currency.currencyCode,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    name = name,
    vendor = vendor,
    amount = MoneyAmount.fromMinorUnits(amountMinor, currencyCode),
    cycle = cycle,
    cycleDays = cycleDays,
    nextChargeOn = LocalDate.ofEpochDay(nextChargeOnEpochDay),
    isActive = isActive,
    reminderId = reminderId,
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id,
    name = name,
    vendor = vendor,
    amountMinor = MoneyAmount.toMinorUnits(amount),
    currencyCode = amount.currency.currencyCode,
    cycle = cycle,
    cycleDays = cycleDays,
    nextChargeOnEpochDay = nextChargeOn.toEpochDay(),
    isActive = isActive,
    reminderId = reminderId,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun CalculationRecordEntity.toDomain(): CalculationRecord = CalculationRecord(
    id = id,
    expression = expression,
    resultText = resultText,
    language = SpokenLanguage.fromTag(languageTag),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

internal fun CalculationRecord.toEntity(): CalculationRecordEntity = CalculationRecordEntity(
    id = id,
    expression = expression,
    resultText = resultText,
    languageTag = language.tag,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)
