package com.rayluxeo.aida.domain.report

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.IncomeRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Works out a report on the device, deterministically, from the repository ports.
 *
 * Pure domain: it composes the same ports the rest of Mira writes through and never touches Room,
 * Android or a language model (§4). Every money figure is BigDecimal the whole way — totals go
 * through the repos' own SQL sums or [sumOrNull] over `Money`, never a float — and a breakdown that
 * spans more than one currency reports a null total rather than a wrong one, the convention the
 * daily brief established, because Mira holds no exchange rates.
 *
 * A report never fails for want of data: an empty ledger is a report of zeros, which is the honest
 * answer to "how did this month go" when nothing was recorded.
 */
@Singleton
class DefaultReportEngine @Inject constructor(
    private val income: IncomeRepository,
    private val expenses: ExpenseRepository,
    private val payments: PaymentRepository,
    private val invoices: InvoiceRepository,
    private val subscriptions: SubscriptionRepository,
    private val clients: ClientRepository,
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
) : ReportEngine {

    override suspend fun generate(type: ReportType, period: ReportPeriod): AppResult<BusinessReport> {
        // "This week" starts on the day Abu chose in Settings, so a week report lines up with how
        // he actually keeps his week rather than a hard-coded Monday.
        val range = period.resolve(clock, settings.current().firstDayOfWeek)
        val report = when (type) {
            ReportType.INCOME -> incomeReport(range)
            ReportType.EXPENSES -> expensesReport(range)
            ReportType.NET_SUMMARY -> netReport(range)
            ReportType.OUTSTANDING_INVOICES -> outstandingReport(range)
            ReportType.PAYMENTS_RECEIVED -> paymentsReport(range)
            ReportType.CLIENT_REVENUE -> clientRevenueReport(range)
            ReportType.PROJECT_REVENUE -> projectRevenueReport(range)
            ReportType.EXPENSE_CATEGORIES -> expenseCategoriesReport(range)
            ReportType.TASK_COMPLETION -> taskCompletionReport(range)
            ReportType.OVERDUE_TASKS -> overdueTasksReport(range)
            ReportType.SUBSCRIPTION_COSTS -> subscriptionReport(range)
            ReportType.PROJECT_STATUS -> projectStatusReport(range)
        }
        return AppResult.Success(report)
    }

    private suspend fun incomeReport(range: ReportRange): BusinessReport {
        val total = payments.totalReceivedBetween(range.start, range.endExclusive).orZero()
            .let { paid ->
                val extra = income.totalReceivedBetween(
                    range.start,
                    range.endExclusive,
                    excludeEntriesFromPayments = true,
                ).orZero()
                paid + extra
            }
        val count = income.observeBetween(range.start, range.endExclusive).first().size
        return BusinessReport(ReportType.INCOME, range, total = total, count = count)
    }

    private suspend fun expensesReport(range: ReportRange): BusinessReport {
        val total = expenses.totalSpentBetween(range.start, range.endExclusive).orZero()
        val count = expenses.observeBetween(range.start, range.endExclusive).first().size
        return BusinessReport(ReportType.EXPENSES, range, total = total, count = count)
    }

    private suspend fun netReport(range: ReportRange): BusinessReport {
        val paid = payments.totalReceivedBetween(range.start, range.endExclusive).orZero()
        val extraIncome = income.totalReceivedBetween(
            range.start,
            range.endExclusive,
            excludeEntriesFromPayments = true,
        ).orZero()
        val moneyIn = paid + extraIncome
        val spent = expenses.totalSpentBetween(range.start, range.endExclusive).orZero()
        val net = moneyIn - spent
        return BusinessReport(
            type = ReportType.NET_SUMMARY,
            range = range,
            total = net,
            lines = listOf(
                ReportLine(ReportKey.Metric(ReportMetric.INCOME), amount = moneyIn),
                ReportLine(ReportKey.Metric(ReportMetric.EXPENSES), amount = spent),
                ReportLine(ReportKey.Metric(ReportMetric.NET), amount = net),
            ),
        )
    }

    private suspend fun outstandingReport(range: ReportRange): BusinessReport {
        // Receivables as they stood at the window's end: an invoice issued after it had not yet been
        // raised, so it is not money that was owed then.
        val unpaid = invoices.observeUnpaid().first()
            .filter { !it.issuedOn.isAfter(range.toDate) }
        val names = clientNames()
        val (total, mixed) = sumOrNull(unpaid.map { it.outstanding })
        val lines = unpaid
            .sortedByDescending { it.outstanding.amount }
            .map { inv ->
                val who = names[inv.clientId]
                ReportLine(
                    key = ReportKey.Named(if (who != null) "$who · #${inv.number}" else "#${inv.number}"),
                    amount = inv.outstanding,
                )
            }
        return BusinessReport(
            type = ReportType.OUTSTANDING_INVOICES,
            range = range,
            total = total,
            count = unpaid.size,
            lines = lines,
            currencyMixed = mixed,
        )
    }

    private suspend fun paymentsReport(range: ReportRange): BusinessReport {
        val total = payments.totalReceivedBetween(range.start, range.endExclusive).orZero()
        val count = payments.observeRecent(RECENT_SCAN).first()
            .count { it.receivedAt.inRange(range) }
        return BusinessReport(ReportType.PAYMENTS_RECEIVED, range, total = total, count = count)
    }

    private suspend fun clientRevenueReport(range: ReportRange): BusinessReport {
        val entries = income.observeBetween(range.start, range.endExclusive).first()
            .filter { it.clientId != null }
        val names = clientNames()
        val lines = entries.groupBy { it.clientId!! }
            .mapNotNull { (clientId, rows) ->
                val (sum, _) = sumOrNull(rows.map { it.amount })
                sum?.let { ReportLine(ReportKey.Named(names[clientId] ?: "#$clientId"), amount = it) }
            }
            .sortedByDescending { it.amount?.amount }
        val (total, mixed) = sumOrNull(entries.map { it.amount })
        return BusinessReport(
            type = ReportType.CLIENT_REVENUE,
            range = range,
            total = total,
            count = lines.size,
            lines = lines,
            currencyMixed = mixed,
        )
    }

    private suspend fun projectRevenueReport(range: ReportRange): BusinessReport {
        val entries = income.observeBetween(range.start, range.endExclusive).first()
            .filter { it.projectId != null }
        val names = projects.observe(ProjectFilter.ALL).first().associate { it.id to it.name }
        val lines = entries.groupBy { it.projectId!! }
            .mapNotNull { (projectId, rows) ->
                val (sum, _) = sumOrNull(rows.map { it.amount })
                sum?.let { ReportLine(ReportKey.Named(names[projectId] ?: "#$projectId"), amount = it) }
            }
            .sortedByDescending { it.amount?.amount }
        val (total, mixed) = sumOrNull(entries.map { it.amount })
        return BusinessReport(
            type = ReportType.PROJECT_REVENUE,
            range = range,
            total = total,
            count = lines.size,
            lines = lines,
            currencyMixed = mixed,
        )
    }

    private suspend fun expenseCategoriesReport(range: ReportRange): BusinessReport {
        val rows = expenses.observeBetween(range.start, range.endExclusive).first()
        val lines = rows.groupBy { it.category }
            .mapNotNull { (category, es) ->
                val (sum, _) = sumOrNull(es.map { it.amount })
                sum?.let { ReportLine(ReportKey.Category(category), amount = it) }
            }
            .sortedByDescending { it.amount?.amount }
        val (total, mixed) = sumOrNull(rows.map { it.amount })
        return BusinessReport(
            type = ReportType.EXPENSE_CATEGORIES,
            range = range,
            total = total,
            count = rows.size,
            lines = lines,
            currencyMixed = mixed,
        )
    }

    private suspend fun taskCompletionReport(range: ReportRange): BusinessReport {
        val all = tasks.list(TaskQuery(filter = TaskFilter.ALL)).orEmptyList()
        val completed = all.count { it.completedAt?.inRange(range) == true }
        val opened = all.count { it.createdAt.inRange(range) }
        return BusinessReport(
            type = ReportType.TASK_COMPLETION,
            range = range,
            count = completed,
            lines = listOf(
                ReportLine(ReportKey.Metric(ReportMetric.TASKS_COMPLETED), count = completed),
                ReportLine(ReportKey.Metric(ReportMetric.TASKS_OPENED), count = opened),
            ),
        )
    }

    private suspend fun overdueTasksReport(range: ReportRange): BusinessReport {
        // Overdue is a standing state, not a windowed one: a task overdue today is overdue whatever
        // period Abu is looking at, so this reads "now" rather than the range.
        val overdue = tasks.list(TaskQuery(filter = TaskFilter.OVERDUE)).orEmptyList()
        return BusinessReport(
            type = ReportType.OVERDUE_TASKS,
            range = range,
            count = overdue.size,
            lines = overdue.map { ReportLine(ReportKey.Named(it.title)) },
        )
    }

    private suspend fun subscriptionReport(range: ReportRange): BusinessReport {
        val active = subscriptions.observeActive().first()
        val monthly = active.map { it to it.amount.times(monthlyFactor(it.cycle, it.cycleDays)) }
        val lines = monthly
            .sortedByDescending { it.second.amount }
            .map { (sub, monthlyAmount) -> ReportLine(ReportKey.Named(sub.name), amount = monthlyAmount) }
        val (total, mixed) = sumOrNull(monthly.map { it.second })
        return BusinessReport(
            type = ReportType.SUBSCRIPTION_COSTS,
            range = range,
            total = total,
            count = active.size,
            lines = lines,
            currencyMixed = mixed,
        )
    }

    private suspend fun projectStatusReport(range: ReportRange): BusinessReport {
        val projs = projects.observe(ProjectFilter.ALL).first()
        val byStatus = projs.groupBy { it.status }
        val lines = ProjectStatus.entries.mapNotNull { status ->
            byStatus[status]?.let { ReportLine(ReportKey.Status(status), count = it.size) }
        }
        return BusinessReport(
            type = ReportType.PROJECT_STATUS,
            range = range,
            count = projs.size,
            lines = lines,
        )
    }

    private suspend fun clientNames(): Map<Long, String> =
        clients.observe(ClientFilter.ALL).first().associate { it.id to it.name }

    /**
     * The monthly-equivalent of one subscription cycle, as a plain factor on its amount. Division
     * is done in BigDecimal at a working scale so nothing ever becomes a double; the product is
     * rounded back to the currency's minor units by [Money.times].
     */
    private fun monthlyFactor(cycle: SubscriptionCycle, cycleDays: Int?): BigDecimal = when (cycle) {
        SubscriptionCycle.MONTHLY -> BigDecimal.ONE
        SubscriptionCycle.YEARLY -> BigDecimal.ONE.divide(BigDecimal(12), FACTOR_SCALE, RoundingMode.HALF_UP)
        SubscriptionCycle.QUARTERLY -> BigDecimal.ONE.divide(BigDecimal(3), FACTOR_SCALE, RoundingMode.HALF_UP)
        SubscriptionCycle.WEEKLY -> BigDecimal(52).divide(BigDecimal(12), FACTOR_SCALE, RoundingMode.HALF_UP)
        SubscriptionCycle.CUSTOM_DAYS ->
            if (cycleDays != null && cycleDays > 0) {
                BigDecimal(AVG_MONTH_DAYS).divide(BigDecimal(cycleDays), FACTOR_SCALE, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ONE
            }
    }

    /**
     * Sum a list of amounts, or null when they span more than one currency — Mira has no rates to
     * cross them, so a mixed total would be a fiction. An empty list sums to zero, not null.
     */
    private fun sumOrNull(amounts: List<Money>): Pair<Money?, Boolean> {
        if (amounts.isEmpty()) return Money.zero() to false
        val currency = amounts.first().currency
        if (amounts.any { it.currency != currency }) return null to true
        return amounts.reduce(Money::plus) to false
    }

    private fun Instant.inRange(range: ReportRange): Boolean =
        !isBefore(range.start) && isBefore(range.endExclusive)

    private fun AppResult<Money>.orZero(): Money = (this as? AppResult.Success)?.value ?: Money.zero()

    private fun <T> AppResult<List<T>>.orEmptyList(): List<T> =
        (this as? AppResult.Success)?.value ?: emptyList()

    private companion object {
        const val RECENT_SCAN = 500
        const val FACTOR_SCALE = 6
        const val AVG_MONTH_DAYS = 30
    }
}
