package com.rayluxeo.aida.domain.command.handler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.finance.Calculator
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.IncomeEntry
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.IncomeRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.usecase.RecordPaymentUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Money in, money out, and arithmetic.
 *
 * Four things this handler deliberately does not do:
 *
 * - **It does not convert currencies.** A payment in a currency other than the invoice it names
 *   is refused inside [RecordPaymentUseCase]'s repository, in the same transaction that would
 *   have applied it, and that refusal is passed straight on. Mira holds no exchange rates with
 *   the radio off, and a rate invented at the moment of recording would be baked into Abu's
 *   books for good.
 * - **It does not do arithmetic.** Sums go to [Calculator], which is deterministic Kotlin over
 *   BigDecimal. Nothing here is a Double or a Float at any point, not even in an intermediate.
 * - **It does not re-apply a payment to its invoice.** [RecordPaymentUseCase] already moves the
 *   invoice's paid figure and status in one transaction with the insert; a second pass here
 *   would be a way for the two to disagree.
 * - **It does not guess who was meant.** A name matching several clients comes back as
 *   [MiraResponse.AmbiguousClient] for Abu to settle, rather than being filed against whichever
 *   row the search happened to return first.
 *
 * A payment naming no invoice is still recorded: money arriving is a fact about Abu's balance
 * first and a settlement second.
 */
class DefaultMoneyCommandHandler @Inject constructor(
    private val recordReceivedPayment: RecordPaymentUseCase,
    private val expenses: ExpenseRepository,
    private val income: IncomeRepository,
    private val clients: ClientRepository,
    private val invoices: InvoiceRepository,
    private val subscriptions: com.rayluxeo.aida.domain.repository.SubscriptionRepository,
    private val calculations: com.rayluxeo.aida.domain.repository.CalculationRepository,
    private val calculator: Calculator,
    private val clock: MiraClock,
) : MoneyCommandHandler {

    override suspend fun recordPayment(command: AppCommand.RecordPayment): AppResult<MiraResponse> {
        val number = command.invoiceNumber?.trim()?.takeIf { it.isNotEmpty() }
        val invoice: Invoice? = if (number == null) {
            null
        } else {
            when (val found = invoices.byNumber(number)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> found.value
            }
        }

        val client: Client? = when (val lookup = lookUp(command.client)) {
            is ClientLookup.Stop -> return lookup.result
            is ClientLookup.Resolved -> lookup.client
        }

        if (invoice != null && client != null && invoice.clientId != client.id) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Invoice ${invoice.number} is not ${client.name}'s to settle",
            )
        }

        val effectiveAmount: Money = if (command.amount != null) {
            command.amount
        } else if (command.percentage != null && invoice != null) {
            val factor = command.percentage.movePointLeft(2)
            val targetPaid = invoice.total * factor
            if (invoice.amountPaid.amount.compareTo(java.math.BigDecimal.ZERO) == 0) {
                targetPaid
            } else {
                val diff = targetPaid - invoice.amountPaid
                if (diff.amount <= java.math.BigDecimal.ZERO) {
                    return failure(
                        MiraErrorCode.VALIDATION,
                        "Invoice ${invoice.number} is already ${command.percentage}% or more paid",
                    )
                }
                diff
            }
        } else {
            return failure(
                MiraErrorCode.VALIDATION,
                "Payment requires either an explicit amount or an invoice reference for percentage calculation",
            )
        }

        val recorded = recordReceivedPayment(
            amount = effectiveAmount,
            method = command.method,
            invoiceId = invoice?.id,
            clientId = client?.id ?: invoice?.clientId,
        )
        return when (recorded) {
            is AppResult.Failure -> recorded
            is AppResult.Success -> AppResult.Success(
                MiraResponse.PaymentRecorded(
                    amount = recorded.value.amount,
                    clientName = client?.name ?: invoice?.let { nameOf(it.clientId) },
                    invoiceNumber = invoice?.number,
                ),
            )
        }
    }

    override suspend fun recordExpense(command: AppCommand.RecordExpense): AppResult<MiraResponse> {
        val now = clock.now()
        return expenses.create(
            Expense(
                description = command.description,
                category = command.category,
                amount = command.amount,
                spentAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        ).map { MiraResponse.ExpenseRecorded(it.amount, it.description) }
    }

    override suspend fun recordIncome(command: AppCommand.RecordIncome): AppResult<MiraResponse> {
        val client: Client? = when (val lookup = lookUp(command.client)) {
            is ClientLookup.Stop -> return lookup.result
            is ClientLookup.Resolved -> lookup.client
        }
        val now = clock.now()
        return income.create(
            IncomeEntry(
                source = command.source,
                amount = command.amount,
                receivedAt = now,
                clientId = client?.id,
                createdAt = now,
                updatedAt = now,
            ),
        ).map { MiraResponse.IncomeRecorded(it.amount, it.source) }
    }

    override suspend fun performCalculation(command: AppCommand.PerformCalculation): AppResult<MiraResponse> {
        val result = calculator.evaluate(command.expression)
        if (result is AppResult.Success) {
            calculations.record(
                com.rayluxeo.aida.domain.model.CalculationRecord(
                    expression = command.expression,
                    resultText = result.value,
                    createdAt = clock.now(),
                ),
            )
        }
        return result.map { MiraResponse.CalculationResult(command.expression, it) }
    }

    override suspend fun queryExpenses(command: AppCommand.QueryExpenses): AppResult<MiraResponse> {
        val now = clock.now()
        val zone = clock.zone()
        val today = now.atZone(zone).toLocalDate()
        val fromInstant = (command.from ?: today.withDayOfMonth(1)).atStartOfDay(zone).toInstant()
        val toInstant = (command.to ?: today.plusMonths(1).withDayOfMonth(1)).atStartOfDay(zone).toInstant()

        val list = when (val fetched = expenses.listBetween(fromInstant, toInstant)) {
            is AppResult.Failure -> return fetched
            is AppResult.Success -> fetched.value
        }
        val filtered = if (command.category != null) {
            list.filter { it.category == command.category }
        } else {
            list
        }
        var total = Money.zero()
        for (item in filtered) {
            total += item.amount
        }
        return AppResult.Success(
            MiraResponse.ExpenseSummaryReport(
                totalSpent = total,
                category = command.category,
                count = filtered.size,
            ),
        )
    }

    override suspend fun queryIncome(command: AppCommand.QueryIncome): AppResult<MiraResponse> {
        val now = clock.now()
        val zone = clock.zone()
        val today = now.atZone(zone).toLocalDate()
        val fromInstant = (command.from ?: today.withDayOfMonth(1)).atStartOfDay(zone).toInstant()
        val toInstant = (command.to ?: today.plusMonths(1).withDayOfMonth(1)).atStartOfDay(zone).toInstant()

        // The total is summed by the repository over BigDecimal, never here and never as a Double.
        val total = when (val fetched = income.totalReceivedBetween(fromInstant, toInstant)) {
            is AppResult.Failure -> return fetched
            is AppResult.Success -> fetched.value
        }
        val count = income.observeBetween(fromInstant, toInstant).first().size
        return AppResult.Success(
            MiraResponse.IncomeSummaryReport(
                totalReceived = total,
                count = count,
            ),
        )
    }

    override suspend fun querySubscription(command: AppCommand.QuerySubscription): AppResult<MiraResponse> {
        val needle = command.name?.trim() ?: "Subscription"
        val active = when (val fetched = subscriptions.dueOnOrBefore(java.time.LocalDate.MAX)) {
            is AppResult.Failure -> return fetched
            is AppResult.Success -> fetched.value
        }
        val match = active.firstOrNull { it.name.contains(needle, ignoreCase = true) || it.vendor?.contains(needle, ignoreCase = true) == true }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No subscription found matching \"$needle\"")

        return AppResult.Success(
            MiraResponse.SubscriptionRenewalInfo(
                name = match.name,
                nextChargeOn = match.nextChargeOn,
                amount = match.amount,
            ),
        )
    }

    /** A null [target] is "he named nobody", which is not the same as "nobody was found". */
    private suspend fun lookUp(target: ClientTarget?): ClientLookup = when (target) {
        null -> ClientLookup.Resolved(null)

        is ClientTarget.ById -> when (val found = clients.byId(target.id)) {
            is AppResult.Failure -> ClientLookup.Stop(found)
            is AppResult.Success -> ClientLookup.Resolved(found.value)
        }

        is ClientTarget.ByName -> when (val found = clients.search(target.name)) {
            is AppResult.Failure -> ClientLookup.Stop(found)
            is AppResult.Success -> narrow(target.name.trim(), found.value)
        }
    }

    /**
     * The search is full-text over name, company **and notes**, so asking for "Ahmed" also
     * returns the client whose notes say "introduced by Ahmed". Only a name or a company resolves
     * a client outright; a hit that landed somewhere else is put back to Abu as a question, never
     * acted on, because filing his money against the wrong person is the one outcome here that
     * cannot be noticed later.
     *
     * An exact name beats a partial one, which is what lets "Ahmed" mean Ahmed while Ahmed
     * Textiles is also on the books.
     */
    private fun narrow(needle: String, matches: List<Client>): ClientLookup {
        val named = matches.filter { it.name.equals(needle, ignoreCase = true) }
            .ifEmpty { matches.filter { it.mentions(needle) } }
        return when {
            named.size == 1 -> ClientLookup.Resolved(named.first())

            named.size > 1 -> ClientLookup.Stop(ambiguous(needle, named))

            matches.isEmpty() ->
                ClientLookup.Stop(failure(MiraErrorCode.NOT_FOUND, "No client matching \"$needle\""))

            else -> ClientLookup.Stop(ambiguous(needle, matches))
        }
    }

    private fun ambiguous(needle: String, candidates: List<Client>): AppResult<MiraResponse> =
        AppResult.Success(MiraResponse.AmbiguousClient(needle, candidates.take(MAX_CANDIDATES).map { it.name }))

    private fun Client.mentions(needle: String): Boolean =
        name.contains(needle, ignoreCase = true) || companyName?.contains(needle, ignoreCase = true) == true

    /** A binned client still owns his invoices, so a name Mira cannot read costs the reply a word, not the payment. */
    private suspend fun nameOf(clientId: Long): String? = clients.byId(clientId).getOrNull()?.name

    private companion object {
        /** Enough for Abu to pick from out loud. A longer list read back is not a question. */
        const val MAX_CANDIDATES = 5
    }
}

/**
 * What looking a client up came to.
 *
 * [Stop] carries the finished reply rather than a reason, because the two ways a lookup ends the
 * command are not the same kind of thing: several people of that name is a question for Abu and
 * a success, nobody of that name is a failure. Deciding that here keeps both call sites to one
 * branch each.
 */
private sealed interface ClientLookup {
    data class Resolved(val client: Client?) : ClientLookup
    data class Stop(val result: AppResult<MiraResponse>) : ClientLookup
}
