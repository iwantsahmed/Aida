package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.automation.AutomationEngine
import com.rayluxeo.aida.domain.automation.RecordTriggerRules
import com.rayluxeo.aida.domain.automation.RecordTriggerSource
import com.rayluxeo.aida.domain.brief.AttentionRules
import com.rayluxeo.aida.domain.model.AutomationTrigger
import com.rayluxeo.aida.domain.model.FiredOccurrence
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.repository.AutomationFireLedgerRepository
import com.rayluxeo.aida.domain.repository.AutomationRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * The daily check behind record-driven automations: fires each enabled rule whose record condition
 * has newly been met — an invoice gone overdue, a project deadline arriving, a subscription about to
 * renew — exactly once per occurrence.
 *
 * It is the single state-change entry point for this feature (§4): the worker calls only this. It
 * reads the records once, asks [RecordTriggerRules] which occurrences qualify, and for each automation
 * fires through [AutomationEngine.executeAutomation] (which validates, rate-limits and logs like any
 * other run) only when a genuinely new occurrence has appeared — then records it in the ledger so the
 * same fact never fires it again, while a new one still does. Running it twice in a day is harmless:
 * the second pass finds every occurrence already in the ledger and fires nothing.
 *
 * A read it cannot do, or a ledger it cannot write, costs Abu nothing but a missed check it retries;
 * it never throws. [MiraClock] is injected — never `Instant.now()`.
 */
class FireRecordTriggeredAutomationsUseCase @Inject constructor(
    private val automations: AutomationRepository,
    private val engine: AutomationEngine,
    private val ledger: AutomationFireLedgerRepository,
    private val invoices: InvoiceRepository,
    private val tasks: TaskRepository,
    private val subscriptions: SubscriptionRepository,
    private val projects: ProjectRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(): AppResult<Int> {
        val enabled = runCatching { automations.observeEnabled().first() }.getOrDefault(emptyList())
            .filter { it.trigger.kind in RECORD_TRIGGER_KINDS }
        if (enabled.isEmpty()) return AppResult.Success(0)

        val now = clock.now()
        val today = clock.today()

        val occurrencesByKind = RecordTriggerRules.evaluate(
            now = now,
            zone = clock.zone(),
            unpaidInvoices = runCatching { invoices.observeUnpaid().first() }.getOrDefault(emptyList()),
            overdueTasks = tasks.list(TaskQuery(filter = TaskFilter.OVERDUE)).valueOrEmpty(),
            dueTodayTasks = tasks.list(TaskQuery(filter = TaskFilter.DUE_TODAY)).valueOrEmpty(),
            renewals = subscriptions.dueOnOrBefore(today.plusDays(AttentionRules.RENEWAL_HORIZON_DAYS)).valueOrEmpty(),
            openProjects = runCatching { projects.observe(ProjectFilter.OPEN).first() }.getOrDefault(emptyList()),
        )

        var fired = 0
        for (automation in enabled) {
            val occurrences = occurrencesByKind[automation.trigger.kind].orEmpty()
            if (occurrences.isEmpty()) continue

            // A ledger read that failed means we cannot tell what has already fired, so skip rather
            // than risk re-firing — it will be retried on the next pass.
            val already = when (val read = ledger.firedFor(automation.id)) {
                is AppResult.Failure -> continue
                is AppResult.Success -> read.value.mapTo(HashSet()) { it.key() }
            }
            val fresh = occurrences.filter { Occurrence(it.source, it.recordId, it.occurrenceFrom) !in already }
            if (fresh.isEmpty()) continue

            // One fire per automation per pass — the action runs once, and every fresh occurrence
            // that triggered it is recorded together, only if the run actually completed.
            if (engine.executeAutomation(automation) is AppResult.Success) {
                fired++
                ledger.recordFired(
                    fresh.map { FiredOccurrence(automation.id, it.source, it.recordId, it.occurrenceFrom, now) },
                )
            }
        }

        ledger.pruneFiredBefore(now.minus(LEDGER_RETENTION))
        return AppResult.Success(fired)
    }

    private fun <T> AppResult<List<T>>.valueOrEmpty(): List<T> =
        (this as? AppResult.Success)?.value ?: emptyList()

    private fun FiredOccurrence.key() = Occurrence(source, recordId, occurrenceFrom)

    /** The identity of one occurrence, so a fired row and a current occurrence compare by fact. */
    private data class Occurrence(val source: RecordTriggerSource, val recordId: Long, val occurrenceFrom: Instant)

    private companion object {
        val RECORD_TRIGGER_KINDS = setOf(
            AutomationTrigger.INVOICE_DUE,
            AutomationTrigger.INVOICE_OVERDUE,
            AutomationTrigger.TASK_DUE,
            AutomationTrigger.TASK_OVERDUE,
            AutomationTrigger.SUBSCRIPTION_RENEWAL,
            AutomationTrigger.PROJECT_DEADLINE,
        )

        /** Old ledger rows are pruned past this; comfortably longer than any trigger window. */
        val LEDGER_RETENTION: Duration = Duration.ofDays(90)
    }
}
