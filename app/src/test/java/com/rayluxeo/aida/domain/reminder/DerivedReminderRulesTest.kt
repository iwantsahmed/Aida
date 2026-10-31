package com.rayluxeo.aida.domain.reminder

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.domain.brief.AttentionRules
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

/**
 * The sweep runs every morning for the rest of Abu's working life, so the test that matters most
 * here is the one that proves it stays quiet: a fact Mira has already raised must not come back
 * tomorrow, whatever happened to the reminder afterwards.
 *
 * Everything is pinned to Dhaka and to one fixed morning. The rules hold no clock, so a window
 * that only behaves between nine and five would be worse than no test at all.
 */
class DerivedReminderRulesTest {

    private val zone: ZoneId = FixedMiraClock.DHAKA
    private val today: LocalDate = LocalDate.of(2026, 3, 10)
    private val morning: Instant = FixedMiraClock.dhaka(2026, 3, 10, 9)

    // ------------------------------------------------------------------ nothing to say

    @Test
    fun `a day with nothing on it raises nothing`() {
        assertThat(DerivedReminderRules.decide(now = morning, zone = zone)).isEmpty()
    }

    // ------------------------------------------------------------------ the no-nag rule

    @Test
    fun `running the sweep twice on the same day raises nothing the second time`() {
        val invoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(3)))

        val first = DerivedReminderRules.decide(now = morning, zone = zone, unpaidInvoices = invoices)
        assertThat(first).hasSize(1)

        val second = DerivedReminderRules.decide(
            now = morning.plus(Duration.ofMinutes(5)),
            zone = zone,
            unpaidInvoices = invoices,
            existing = first.map { it.asStored() },
        )
        assertThat(second).isEmpty()
    }

    @Test
    fun `an invoice still unpaid days later is not raised again`() {
        val invoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(6)))
        val raised = DerivedReminderRules.decide(now = morning, zone = zone, unpaidInvoices = invoices)

        val nextWeek = DerivedReminderRules.decide(
            now = morning.plus(Duration.ofDays(5)),
            zone = zone,
            unpaidInvoices = invoices,
            existing = raised.map { it.asStored(state = ReminderState.FIRED) },
        )

        assertThat(nextWeek).isEmpty()
    }

    @Test
    fun `a reminder Abu cancelled is not raised again`() {
        val invoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(2)))
        val raised = DerivedReminderRules.decide(now = morning, zone = zone, unpaidInvoices = invoices)

        val tomorrow = DerivedReminderRules.decide(
            now = morning.plus(Duration.ofDays(1)),
            zone = zone,
            unpaidInvoices = invoices,
            existing = raised.map { it.asStored(state = ReminderState.CANCELLED) },
        )

        assertThat(tomorrow).isEmpty()
    }

    @Test
    fun `a snoozed reminder still counts as raised`() {
        val invoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(2)))
        val raised = DerivedReminderRules.decide(now = morning, zone = zone, unpaidInvoices = invoices)
        val snoozed = raised.map { it.asStored().copy(snoozedUntil = morning.plus(Duration.ofDays(2))) }

        val tomorrow = DerivedReminderRules.decide(
            now = morning.plus(Duration.ofDays(1)),
            zone = zone,
            unpaidInvoices = invoices,
            existing = snoozed,
        )

        assertThat(tomorrow).isEmpty()
    }

    @Test
    fun `a reminder Abu set himself about the same invoice stands in for Mira's`() {
        val his = Reminder(
            id = 9,
            title = "Chase Ahmed",
            triggerAt = morning.plus(Duration.ofDays(1)),
            kind = ReminderKind.INVOICE,
            linkedInvoiceId = 4,
            createdAt = morning,
        )

        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(3))),
            existing = listOf(his),
        )

        assertThat(decisions).isEmpty()
    }

    @Test
    fun `a reminder about a different invoice does not suppress this one`() {
        val other = Reminder(
            id = 9,
            title = "INV-2026-001",
            triggerAt = morning,
            kind = ReminderKind.INVOICE,
            linkedInvoiceId = 5,
            createdAt = morning,
        )

        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(3))),
            existing = listOf(other),
        )

        assertThat(decisions.single().targetId).isEqualTo(4L)
    }

    // ------------------------------------------------------------------ invoices

    @Test
    fun `an invoice due inside the week is raised once, by its number and both its links`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            unpaidInvoices = listOf(
                invoice(id = 4, clientId = 7, number = "INV-2026-014", dueOn = today.plusDays(3)),
            ),
        )

        val decision = decisions.single()
        assertThat(decision.kind).isEqualTo(ReminderKind.INVOICE)
        assertThat(decision.subject).isEqualTo("INV-2026-014")
        assertThat(decision.target).isEqualTo(DerivedReminderTarget.INVOICE)
        assertThat(decision.targetId).isEqualTo(4L)
        assertThat(decision.clientId).isEqualTo(7L)
    }

    @Test
    fun `an invoice due beyond the week is not mentioned yet`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            unpaidInvoices = listOf(
                invoice(
                    id = 4,
                    clientId = 7,
                    dueOn = today.plusDays(AttentionRules.INVOICE_DUE_SOON_DAYS + 1),
                ),
            ),
        )

        assertThat(decisions).isEmpty()
    }

    @Test
    fun `a draft invoice is never chased`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            unpaidInvoices = listOf(
                invoice(id = 4, clientId = 7, dueOn = today.plusDays(1), status = InvoiceStatus.DRAFT),
            ),
        )

        assertThat(decisions).isEmpty()
    }

    @Test
    fun `an invoice that has gone late earns one more notice, and only one`() {
        val dueOn = today.plusDays(1)
        val invoices = listOf(invoice(id = 4, clientId = 7, dueOn = dueOn))

        val dueSoon = DerivedReminderRules.decide(now = morning, zone = zone, unpaidInvoices = invoices)
            .map { it.asStored(state = ReminderState.FIRED) }
        assertThat(dueSoon).hasSize(1)

        val dayAfterDue = FixedMiraClock.dhaka(2026, 3, 12, 9)
        val late = DerivedReminderRules.decide(
            now = dayAfterDue,
            zone = zone,
            unpaidInvoices = invoices,
            existing = dueSoon,
        )
        assertThat(late).hasSize(1)

        val theDayAfterThat = DerivedReminderRules.decide(
            now = dayAfterDue.plus(Duration.ofDays(1)),
            zone = zone,
            unpaidInvoices = invoices,
            existing = dueSoon + late.map { it.asStored(state = ReminderState.FIRED) },
        )
        assertThat(theDayAfterThat).isEmpty()
    }

    // ------------------------------------------------------------------ deadlines

    @Test
    fun `a project deadline inside the horizon is raised against the project`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            openProjects = listOf(project(id = 3, name = "Rayluxeo site", clientId = 7, dueOn = today.plusDays(9))),
        )

        val decision = decisions.single()
        assertThat(decision.kind).isEqualTo(ReminderKind.PROJECT_DEADLINE)
        assertThat(decision.subject).isEqualTo("Rayluxeo site")
        assertThat(decision.target).isEqualTo(DerivedReminderTarget.PROJECT)
        assertThat(decision.targetId).isEqualTo(3L)
        assertThat(decision.clientId).isEqualTo(7L)
    }

    @Test
    fun `a project with no due date and one past the horizon are both left alone`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            openProjects = listOf(
                project(id = 3, name = "No date", dueOn = null),
                project(
                    id = 4,
                    name = "Months away",
                    dueOn = today.plusDays(AttentionRules.DEADLINE_HORIZON_DAYS + 1),
                ),
            ),
        )

        assertThat(decisions).isEmpty()
    }

    @Test
    fun `a finished project is not a deadline`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            openProjects = listOf(
                project(id = 3, name = "Done", dueOn = today.plusDays(2), status = ProjectStatus.COMPLETED),
            ),
        )

        assertThat(decisions).isEmpty()
    }

    // ------------------------------------------------------------------ renewals

    @Test
    fun `a renewal inside the week is raised against the subscription`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            renewals = listOf(subscription(id = 2, name = "Figma", nextChargeOn = today.plusDays(5))),
        )

        val decision = decisions.single()
        assertThat(decision.kind).isEqualTo(ReminderKind.SUBSCRIPTION)
        assertThat(decision.subject).isEqualTo("Figma")
        assertThat(decision.target).isEqualTo(DerivedReminderTarget.SUBSCRIPTION)
        assertThat(decision.targetId).isEqualTo(2L)
        assertThat(decision.clientId).isNull()
    }

    @Test
    fun `a paused subscription is not renewing`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            renewals = listOf(
                subscription(id = 2, name = "Figma", nextChargeOn = today.plusDays(2), active = false),
            ),
        )

        assertThat(decisions).isEmpty()
    }

    @Test
    fun `next month's charge is a new fact and is raised again`() {
        val subscriptions = listOf(subscription(id = 2, name = "Figma", nextChargeOn = today.plusDays(2)))
        val thisMonth = DerivedReminderRules.decide(now = morning, zone = zone, renewals = subscriptions)
            .map { it.asStored(state = ReminderState.FIRED) }
        assertThat(thisMonth).hasSize(1)

        // A month on, the stored charge date has been stepped forward by the subscription sweep.
        val nextMonth = FixedMiraClock.dhaka(2026, 4, 9, 9)
        val decisions = DerivedReminderRules.decide(
            now = nextMonth,
            zone = zone,
            renewals = listOf(subscription(id = 2, name = "Figma", nextChargeOn = today.plusMonths(1).plusDays(2))),
            existing = thisMonth,
        )

        assertThat(decisions).hasSize(1)
    }

    // ------------------------------------------------------------------ follow-ups

    @Test
    fun `a client who has gone quiet is raised once`() {
        val client = client(id = 7, name = "Ahmed", createdAt = FixedMiraClock.dhaka(2025, 1, 1, 9))
        val lastMet = FixedMiraClock.dhaka(2026, 2, 1, 11)

        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            clients = listOf(client),
            lastContact = mapOf(7L to lastMet),
        )

        val decision = decisions.single()
        assertThat(decision.kind).isEqualTo(ReminderKind.CLIENT_FOLLOW_UP)
        assertThat(decision.subject).isEqualTo("Ahmed")
        assertThat(decision.target).isEqualTo(DerivedReminderTarget.CLIENT)
        assertThat(decision.targetId).isEqualTo(7L)
        assertThat(decision.clientId).isEqualTo(7L)
    }

    @Test
    fun `a client met last week is not quiet`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            clients = listOf(client(id = 7, name = "Ahmed")),
            lastContact = mapOf(7L to morning.minus(Duration.ofDays(6))),
        )

        assertThat(decisions).isEmpty()
    }

    @Test
    fun `a client with a meeting in the diary is not chased`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            clients = listOf(client(id = 7, name = "Ahmed", createdAt = FixedMiraClock.dhaka(2025, 1, 1, 9))),
            meetingBooked = setOf(7L),
        )

        assertThat(decisions).isEmpty()
    }

    @Test
    fun `a client added last week has not had time to go quiet`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            clients = listOf(client(id = 7, name = "Ahmed", createdAt = morning.minus(Duration.ofDays(6)))),
        )

        assertThat(decisions).isEmpty()
    }

    @Test
    fun `an archived client is not chased`() {
        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            clients = listOf(
                client(id = 7, name = "Ahmed", archived = true, createdAt = FixedMiraClock.dhaka(2025, 1, 1, 9)),
            ),
        )

        assertThat(decisions).isEmpty()
    }

    @Test
    fun `silence that restarts after a meeting earns a second follow-up`() {
        val client = client(id = 7, name = "Ahmed", createdAt = FixedMiraClock.dhaka(2025, 1, 1, 9))
        val firstQuiet = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            clients = listOf(client),
            lastContact = mapOf(7L to FixedMiraClock.dhaka(2026, 2, 1, 11)),
        ).map { it.asStored(state = ReminderState.FIRED) }
        assertThat(firstQuiet).hasSize(1)

        // They meet on the 12th, and go quiet again for three more weeks.
        val met = FixedMiraClock.dhaka(2026, 3, 12, 15)
        val later = FixedMiraClock.dhaka(2026, 4, 6, 9)

        val stillQuiet = DerivedReminderRules.decide(
            now = later,
            zone = zone,
            clients = listOf(client),
            lastContact = mapOf(7L to met),
            existing = firstQuiet,
        )

        assertThat(stillQuiet).hasSize(1)
    }

    // ------------------------------------------------------------------ when Mira may speak

    @Test
    fun `a sweep in the small hours waits for the morning`() {
        val threeAm = FixedMiraClock.dhaka(2026, 3, 10, 3)

        val decision = DerivedReminderRules.decide(
            now = threeAm,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(2))),
        ).single()

        assertThat(decision.triggerAt)
            .isEqualTo(today.atTime(DerivedReminderRules.WAKING_FROM).atZone(zone).toInstant())
    }

    @Test
    fun `a sweep late at night waits for the next morning`() {
        val lateEvening = FixedMiraClock.dhaka(2026, 3, 10, 22)

        val decision = DerivedReminderRules.decide(
            now = lateEvening,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(2))),
        ).single()

        assertThat(decision.triggerAt)
            .isEqualTo(today.plusDays(1).atTime(DerivedReminderRules.WAKING_FROM).atZone(zone).toInstant())
    }

    @Test
    fun `a sweep during the day speaks straight away, and always in the future`() {
        val decision = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(2))),
        ).single()

        assertThat(decision.triggerAt).isGreaterThan(morning)
        assertThat(decision.triggerAt).isLessThan(morning.plus(Duration.ofMinutes(5)))
    }

    // ------------------------------------------------------------------ the cap

    @Test
    fun `one sweep raises at most a handful of a kind, most urgent first`() {
        val overdue = (1..8).map { day ->
            invoice(id = day.toLong(), clientId = 7, number = "INV-$day", dueOn = today.minusDays(day.toLong()))
        }

        val decisions = DerivedReminderRules.decide(now = morning, zone = zone, unpaidInvoices = overdue)

        assertThat(decisions).hasSize(DerivedReminderRules.MAX_PER_KIND)
        assertThat(decisions.first().subject).isEqualTo("INV-8")
    }

    @Test
    fun `the cap is per kind, so the money never crowds out the deadlines`() {
        val overdue = (1..8).map { day ->
            invoice(id = day.toLong(), clientId = 7, number = "INV-$day", dueOn = today.minusDays(day.toLong()))
        }

        val decisions = DerivedReminderRules.decide(
            now = morning,
            zone = zone,
            unpaidInvoices = overdue,
            openProjects = listOf(project(id = 3, name = "Rayluxeo site", dueOn = today.plusDays(4))),
        )

        assertThat(decisions.count { it.kind == ReminderKind.INVOICE })
            .isEqualTo(DerivedReminderRules.MAX_PER_KIND)
        assertThat(decisions.count { it.kind == ReminderKind.PROJECT_DEADLINE }).isEqualTo(1)
    }

    // ------------------------------------------------------------------ fixtures

    /** What the worker stores for a decision, so the next sweep sees exactly what it would see. */
    private fun DerivedReminder.asStored(
        state: ReminderState = ReminderState.SCHEDULED,
    ) = Reminder(
        id = targetId,
        title = subject,
        triggerAt = triggerAt,
        state = state,
        kind = kind,
        linkedInvoiceId = targetId.takeIf { target == DerivedReminderTarget.INVOICE },
        linkedClientId = clientId,
        linkedProjectId = targetId.takeIf { target == DerivedReminderTarget.PROJECT },
        linkedSubscriptionId = targetId.takeIf { target == DerivedReminderTarget.SUBSCRIPTION },
        createdAt = triggerAt,
    )

    private fun invoice(
        id: Long,
        clientId: Long,
        number: String = "INV-2026-014",
        dueOn: LocalDate,
        status: InvoiceStatus = InvoiceStatus.SENT,
    ): Invoice {
        val amount = Money.of("1000")
        return Invoice(
            id = id,
            number = number,
            clientId = clientId,
            status = status,
            issuedOn = dueOn.minusDays(30),
            dueOn = dueOn,
            subtotal = amount,
            discount = Money.zero(),
            tax = Money.zero(),
            total = amount,
            amountPaid = Money.zero(),
            createdAt = EPOCH_ISH,
            updatedAt = EPOCH_ISH,
        )
    }

    private fun project(
        id: Long,
        name: String,
        dueOn: LocalDate?,
        clientId: Long? = null,
        status: ProjectStatus = ProjectStatus.ACTIVE,
    ) = Project(
        id = id,
        clientId = clientId,
        name = name,
        status = status,
        dueOn = dueOn,
        createdAt = EPOCH_ISH,
        updatedAt = EPOCH_ISH,
    )

    private fun subscription(
        id: Long,
        name: String,
        nextChargeOn: LocalDate,
        active: Boolean = true,
    ) = Subscription(
        id = id,
        name = name,
        amount = Money.of("2400"),
        cycle = SubscriptionCycle.MONTHLY,
        nextChargeOn = nextChargeOn,
        isActive = active,
        createdAt = EPOCH_ISH,
        updatedAt = EPOCH_ISH,
    )

    private fun client(
        id: Long,
        name: String,
        archived: Boolean = false,
        createdAt: Instant = EPOCH_ISH,
    ) = Client(
        id = id,
        name = name,
        isArchived = archived,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private companion object {
        /** Long before any window here cares, so a record's own age never interferes. */
        val EPOCH_ISH: Instant = Instant.parse("2020-01-01T00:00:00Z")
    }
}
