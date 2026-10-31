package com.rayluxeo.aida.domain.automation

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.AutomationTrigger
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.Task
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The windows and anchors behind record-driven automations, proven without Room or a wall clock.
 * The anchor is the load-bearing part: it must identify the *fact*, so the ledger can fire once and
 * then stay quiet while the condition merely persists — every case here pins the exact occurrenceFrom.
 */
class RecordTriggerRulesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val today: LocalDate = LocalDate.of(2026, 10, 16)
    private val now: Instant = LocalDateTime.of(today, java.time.LocalTime.of(9, 0)).atZone(zone).toInstant()

    private fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    private fun evaluate(
        invoices: List<Invoice> = emptyList(),
        overdueTasks: List<Task> = emptyList(),
        dueTodayTasks: List<Task> = emptyList(),
        renewals: List<Subscription> = emptyList(),
        openProjects: List<Project> = emptyList(),
    ) = RecordTriggerRules.evaluate(now, zone, invoices, overdueTasks, dueTodayTasks, renewals, openProjects)

    @Test
    fun `an overdue invoice anchors on the day after its due date`() {
        val result = evaluate(invoices = listOf(invoice(7, dueOn = today.minusDays(1))))
        val occ = result.getValue(AutomationTrigger.INVOICE_OVERDUE).single()
        assertThat(occ.source).isEqualTo(RecordTriggerSource.INVOICE)
        assertThat(occ.recordId).isEqualTo(7)
        assertThat(occ.occurrenceFrom).isEqualTo(startOfDay(today)) // (due - 1) + 1 = today
        assertThat(result).doesNotContainKey(AutomationTrigger.INVOICE_DUE)
    }

    @Test
    fun `an invoice within a week of its due date is due-soon, anchored on due minus seven`() {
        val due = today.plusDays(3)
        val occ = evaluate(invoices = listOf(invoice(7, dueOn = due)))
            .getValue(AutomationTrigger.INVOICE_DUE).single()
        assertThat(occ.occurrenceFrom).isEqualTo(startOfDay(due.minusDays(7)))
    }

    @Test
    fun `a draft invoice is never a record trigger`() {
        val result = evaluate(invoices = listOf(invoice(7, dueOn = today.minusDays(1), status = InvoiceStatus.DRAFT)))
        assertThat(result).isEmpty()
    }

    @Test
    fun `an invoice beyond the due-soon window is neither due nor overdue`() {
        val result = evaluate(invoices = listOf(invoice(7, dueOn = today.plusDays(14))))
        assertThat(result).isEmpty()
    }

    @Test
    fun `an open project within the deadline horizon anchors on due minus fourteen`() {
        val due = today.plusDays(9)
        val occ = evaluate(openProjects = listOf(project(3, dueOn = due)))
            .getValue(AutomationTrigger.PROJECT_DEADLINE).single()
        assertThat(occ.recordId).isEqualTo(3)
        assertThat(occ.occurrenceFrom).isEqualTo(startOfDay(due.minusDays(14)))
    }

    @Test
    fun `a project beyond the deadline horizon does not qualify`() {
        assertThat(evaluate(openProjects = listOf(project(3, dueOn = today.plusDays(20))))).isEmpty()
    }

    @Test
    fun `a subscription renewing within a week qualifies, an inactive or deleted one does not`() {
        val charge = today.plusDays(4)
        val active = evaluate(renewals = listOf(subscription(1, nextChargeOn = charge)))
            .getValue(AutomationTrigger.SUBSCRIPTION_RENEWAL).single()
        assertThat(active.occurrenceFrom).isEqualTo(startOfDay(charge.minusDays(7)))

        assertThat(evaluate(renewals = listOf(subscription(1, nextChargeOn = charge, isActive = false)))).isEmpty()
        assertThat(evaluate(renewals = listOf(subscription(1, nextChargeOn = charge, deletedAt = now)))).isEmpty()
    }

    @Test
    fun `a task due today anchors on today's start`() {
        val occ = evaluate(dueTodayTasks = listOf(task(5, dueAt = startOfDay(today).plusSeconds(50_000))))
            .getValue(AutomationTrigger.TASK_DUE).single()
        assertThat(occ.recordId).isEqualTo(5)
        assertThat(occ.occurrenceFrom).isEqualTo(startOfDay(today))
    }

    @Test
    fun `an overdue task anchors on the day after its due date, so a snooze re-anchors`() {
        val yesterday = evaluate(overdueTasks = listOf(task(5, dueAt = startOfDay(today.minusDays(2)))))
            .getValue(AutomationTrigger.TASK_OVERDUE).single()
        val earlier = evaluate(overdueTasks = listOf(task(5, dueAt = startOfDay(today.minusDays(5)))))
            .getValue(AutomationTrigger.TASK_OVERDUE).single()
        assertThat(yesterday.occurrenceFrom).isEqualTo(startOfDay(today.minusDays(1)))
        assertThat(earlier.occurrenceFrom).isEqualTo(startOfDay(today.minusDays(4)))
        // Same task, different due date → different anchor, so the rule fires again after a snooze.
        assertThat(yesterday.occurrenceFrom).isNotEqualTo(earlier.occurrenceFrom)
    }

    @Test
    fun `a day with nothing qualifying yields an empty map`() {
        assertThat(evaluate()).isEmpty()
    }

    @Test
    fun `the overdue anchor is stable across the days the condition persists`() {
        val due = today.minusDays(1)
        val onDay1 = RecordTriggerRules.evaluate(now, zone, listOf(invoice(7, dueOn = due)), emptyList(), emptyList(), emptyList(), emptyList())
        val later = LocalDateTime.of(today.plusDays(2), java.time.LocalTime.of(9, 0)).atZone(zone).toInstant()
        val onDay3 = RecordTriggerRules.evaluate(later, zone, listOf(invoice(7, dueOn = due)), emptyList(), emptyList(), emptyList(), emptyList())
        assertThat(onDay1.getValue(AutomationTrigger.INVOICE_OVERDUE).single().occurrenceFrom)
            .isEqualTo(onDay3.getValue(AutomationTrigger.INVOICE_OVERDUE).single().occurrenceFrom)
    }

    private val t0: Instant = Instant.ofEpochSecond(1_700_000_000)

    private fun invoice(id: Long, dueOn: LocalDate, status: InvoiceStatus = InvoiceStatus.SENT) = Invoice(
        id = id,
        number = "INV-$id",
        clientId = 1,
        status = status,
        issuedOn = dueOn.minusDays(30),
        dueOn = dueOn,
        subtotal = Money.of("100"),
        discount = Money.zero(),
        tax = Money.zero(),
        total = Money.of("100"),
        amountPaid = Money.zero(),
        createdAt = t0,
        updatedAt = t0,
    )

    private fun task(id: Long, dueAt: Instant?) =
        Task(id = id, title = "T$id", dueAt = dueAt, createdAt = t0, updatedAt = t0)

    private fun subscription(
        id: Long,
        nextChargeOn: LocalDate,
        isActive: Boolean = true,
        deletedAt: Instant? = null,
    ) = Subscription(
        id = id,
        name = "S$id",
        amount = Money.of("100"),
        nextChargeOn = nextChargeOn,
        isActive = isActive,
        deletedAt = deletedAt,
        createdAt = t0,
        updatedAt = t0,
    )

    private fun project(id: Long, dueOn: LocalDate) =
        Project(id = id, name = "P$id", dueOn = dueOn, createdAt = t0, updatedAt = t0)
}
