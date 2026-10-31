package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.domain.brief.AttentionRules
import com.rayluxeo.aida.domain.model.AutomationTrigger
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Which records currently meet which record-driven automation trigger, as a set of occurrences.
 *
 * Pure — the clock arrives as [now]/[zone], the records as lists already read by the use case — so
 * the windows and the anchors are checkable without Room or a wall clock. It reuses the same
 * [AttentionRules] horizons the brief's [com.rayluxeo.aida.domain.reminder.DerivedReminderRules]
 * uses, so Home's Needs Attention card and a record-triggered automation agree about the same record
 * on the same day.
 *
 * The anchor ([RecordOccurrence.occurrenceFrom]) is tied to the fact, not to "now": an invoice's due
 * date for the due window, the day after for the overdue window, a task's own due day. It changes
 * only when the fact genuinely changes (a later due date, a task snoozed to a new day), which is what
 * lets the fire ledger fire once and then stay quiet while the condition merely persists.
 */
object RecordTriggerRules {

    fun evaluate(
        now: Instant,
        zone: ZoneId,
        unpaidInvoices: List<Invoice>,
        overdueTasks: List<Task>,
        dueTodayTasks: List<Task>,
        renewals: List<Subscription>,
        openProjects: List<Project>,
    ): Map<AutomationTrigger, List<RecordOccurrence>> {
        val today = now.atZone(zone).toLocalDate()
        fun anchor(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

        val invoiceDue = mutableListOf<RecordOccurrence>()
        val invoiceOverdue = mutableListOf<RecordOccurrence>()
        for (invoice in unpaidInvoices) {
            // Draft is not billed yet and void is cancelled — neither is a bill Abu is waiting on.
            if (invoice.status == InvoiceStatus.DRAFT || invoice.status == InvoiceStatus.VOID) continue
            val daysAway = ChronoUnit.DAYS.between(today, invoice.dueOn)
            when {
                invoice.dueOn.isBefore(today) -> invoiceOverdue += RecordOccurrence(
                    source = RecordTriggerSource.INVOICE,
                    recordId = invoice.id,
                    occurrenceFrom = anchor(invoice.dueOn.plusDays(1)),
                )
                daysAway in 0L..AttentionRules.INVOICE_DUE_SOON_DAYS -> invoiceDue += RecordOccurrence(
                    source = RecordTriggerSource.INVOICE,
                    recordId = invoice.id,
                    occurrenceFrom = anchor(invoice.dueOn.minusDays(AttentionRules.INVOICE_DUE_SOON_DAYS)),
                )
            }
        }

        val projectDeadline = openProjects.mapNotNull { project ->
            val dueOn = project.dueOn ?: return@mapNotNull null
            val daysAway = ChronoUnit.DAYS.between(today, dueOn)
            if (daysAway in 0L..AttentionRules.DEADLINE_HORIZON_DAYS) {
                RecordOccurrence(
                    source = RecordTriggerSource.PROJECT,
                    recordId = project.id,
                    occurrenceFrom = anchor(dueOn.minusDays(AttentionRules.DEADLINE_HORIZON_DAYS)),
                )
            } else {
                null
            }
        }

        val subscriptionRenewal = renewals.mapNotNull { subscription ->
            if (!subscription.isActive || subscription.deletedAt != null) return@mapNotNull null
            val charge = subscription.nextChargeOnOrAfter(today)
            val daysAway = ChronoUnit.DAYS.between(today, charge)
            if (daysAway in 0L..AttentionRules.RENEWAL_HORIZON_DAYS) {
                RecordOccurrence(
                    source = RecordTriggerSource.SUBSCRIPTION,
                    recordId = subscription.id,
                    occurrenceFrom = anchor(charge.minusDays(AttentionRules.RENEWAL_HORIZON_DAYS)),
                )
            } else {
                null
            }
        }

        val taskDue = dueTodayTasks.mapNotNull { task ->
            val dueDate = task.dueAt?.atZone(zone)?.toLocalDate() ?: return@mapNotNull null
            RecordOccurrence(RecordTriggerSource.TASK, task.id, anchor(dueDate))
        }

        val taskOverdue = overdueTasks.mapNotNull { task ->
            val dueDate = task.dueAt?.atZone(zone)?.toLocalDate() ?: return@mapNotNull null
            // The day AFTER the due day, so a task snoozed to a new date re-anchors as a new fact.
            RecordOccurrence(RecordTriggerSource.TASK, task.id, anchor(dueDate.plusDays(1)))
        }

        return buildMap {
            if (invoiceDue.isNotEmpty()) put(AutomationTrigger.INVOICE_DUE, invoiceDue)
            if (invoiceOverdue.isNotEmpty()) put(AutomationTrigger.INVOICE_OVERDUE, invoiceOverdue)
            if (projectDeadline.isNotEmpty()) put(AutomationTrigger.PROJECT_DEADLINE, projectDeadline)
            if (subscriptionRenewal.isNotEmpty()) put(AutomationTrigger.SUBSCRIPTION_RENEWAL, subscriptionRenewal)
            if (taskDue.isNotEmpty()) put(AutomationTrigger.TASK_DUE, taskDue)
            if (taskOverdue.isNotEmpty()) put(AutomationTrigger.TASK_OVERDUE, taskOverdue)
        }
    }
}
