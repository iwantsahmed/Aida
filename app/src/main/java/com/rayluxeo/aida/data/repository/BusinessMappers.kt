package com.rayluxeo.aida.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.rayluxeo.aida.data.db.entity.ClientEntity
import com.rayluxeo.aida.data.db.entity.ProjectEntity
import com.rayluxeo.aida.data.db.entity.ProjectMilestoneEntity
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientBilling
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectMilestone
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

/**
 * Where the storage shapes of the business records meet the domain ones.
 *
 * Three conversions live here and nowhere else, so a query never has to do them:
 * epoch millis to [Instant], epoch days to [LocalDate], and minor units to [Money]. Money crosses
 * this boundary as a whole number of paisa beside its currency code and comes back as a
 * [java.math.BigDecimal]; there is no point in between where it is a `Double`.
 */

internal fun ClientEntity.toDomain(): Client = Client(
    id = id,
    uuid = uuid,
    name = name,
    companyName = companyName,
    email = email,
    phone = phone,
    addressLine = addressLine,
    notes = notes,
    billing = billingOf(defaultCurrencyCode, defaultRateMinor),
    isArchived = isArchived,
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

/**
 * [uuid] is passed in rather than read off the client, because the stable identity is the
 * repository's to assign and the DAO's to preserve — never a caller's to state.
 */
internal fun Client.toEntity(uuid: String): ClientEntity = ClientEntity(
    id = id,
    uuid = uuid,
    name = name,
    companyName = companyName,
    email = email,
    phone = phone,
    addressLine = addressLine,
    notes = notes,
    defaultCurrencyCode = billing?.currency?.currencyCode,
    defaultRateMinor = billing?.defaultRate?.let(MoneyAmount::toMinorUnits),
    isArchived = isArchived,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ProjectEntity.toDomain(): Project {
    val currency = currencyOrNull(currencyCode)
    return Project(
        id = id,
        uuid = uuid,
        clientId = clientId,
        name = name,
        description = description,
        status = status,
        startedOn = startedOnEpochDay?.let(LocalDate::ofEpochDay),
        dueOn = dueOnEpochDay?.let(LocalDate::ofEpochDay),
        // A budget whose currency cannot be named is not shown as a number in some other one.
        budget = currency?.let { named -> budgetMinor?.let { MoneyAmount.fromMinorUnits(it, named.currencyCode) } },
        currency = currency ?: Money.BDT,
        deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun Project.toEntity(uuid: String): ProjectEntity = ProjectEntity(
    id = id,
    uuid = uuid,
    clientId = clientId,
    name = name,
    description = description,
    status = status,
    startedOnEpochDay = startedOn?.toEpochDay(),
    dueOnEpochDay = dueOn?.toEpochDay(),
    budgetMinor = budget?.let(MoneyAmount::toMinorUnits),
    currencyCode = currency.currencyCode,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ProjectMilestoneEntity.toDomain(): ProjectMilestone = ProjectMilestone(
    id = id,
    projectId = projectId,
    title = title,
    dueOn = dueOnEpochDay?.let(LocalDate::ofEpochDay),
    isComplete = isComplete,
    completedAt = completedAtEpochMillis?.let(Instant::ofEpochMilli),
    position = position,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun ProjectMilestone.toEntity(): ProjectMilestoneEntity = ProjectMilestoneEntity(
    id = id,
    projectId = projectId,
    title = title,
    dueOnEpochDay = dueOn?.toEpochDay(),
    isComplete = isComplete,
    completedAtEpochMillis = completedAt?.toEpochMilli(),
    position = position,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

/**
 * A stored rate with no currency beside it is read as no rate at all. The pair is what carries
 * meaning, so half of it is dropped rather than completed with a guess at taka.
 */
private fun billingOf(currencyCode: String?, rateMinor: Long?): ClientBilling? {
    val currency = currencyOrNull(currencyCode) ?: return null
    return ClientBilling(
        currency = currency,
        defaultRate = rateMinor?.let { MoneyAmount.fromMinorUnits(it, currency.currencyCode) },
    )
}

/**
 * One row carrying a currency code this device's JVM does not know must not take a whole list
 * down with it, so the code decays to "no currency" the way an unrecognised enum name decays to
 * its default.
 */
private fun currencyOrNull(code: String?): Currency? =
    code?.let { runCatching { Currency.getInstance(it) }.getOrNull() }

/**
 * True when [money] can be stated exactly in its own currency's minor units. An amount finer than
 * that — 12.345 taka — is a mistake made upstream, and the repository refuses it as a validation
 * failure rather than letting it round on the way to disk.
 */
internal fun isStorable(money: Money): Boolean =
    runCatching { MoneyAmount.toMinorUnits(money) }.isSuccess

/**
 * Room may surface a foreign-key refusal as the framework exception or as a wrapped driver one,
 * so the message is read as well as the type. A repository uses this to tell the two apart: a
 * refusal is a decision for Abu, anything else is a storage fault and stays one.
 */
internal fun Throwable.isForeignKeyFailure(): Boolean =
    generateSequence(this) { it.cause }.take(CAUSE_DEPTH).any { error ->
        error is SQLiteConstraintException ||
            error.message?.contains("FOREIGN KEY constraint failed", ignoreCase = true) == true
    }

private const val CAUSE_DEPTH = 5
