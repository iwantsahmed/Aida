package com.rayluxeo.aida.domain.brief

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/**
 * The ranking holds no repository and no clock, so every window and every tie can be pinned down
 * here rather than through a database.
 *
 * Two things this file exists to stop coming back: an item Mira made up to fill the card, and an
 * order in which the loudest category quietly owns all five places.
 */
class AttentionRulesTest {

    private val zone = FixedMiraClock.DHAKA
    private val today: LocalDate = LocalDate.of(2026, 3, 10)

    // ------------------------------------------------------------------ nothing to say

    @Test
    fun `a day with nothing on it produces an empty card`() {
        assertThat(AttentionRules.rank(today = today, zone = zone)).isEmpty()
    }

    // ------------------------------------------------------------------ invoices

    @Test
    fun `an invoice past its due date is named by its client and what is still owed`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 4, clientId = 7, total = "12000", dueOn = today.minusDays(4))),
            clientNames = mapOf(7L to "Ahmed"),
        )

        val item = items.single()
        assertThat(item.kind).isEqualTo(AttentionKind.INVOICE_OVERDUE)
        assertThat(item.severity).isEqualTo(AttentionSeverity.NOW)
        assertThat(item.subject).isEqualTo("Ahmed")
        assertThat(item.amount).isEqualTo(Money.of("12000"))
        assertThat(item.daysAway).isEqualTo(-4L)
        assertThat(item.reference).isEqualTo(AttentionReference(AttentionTarget.INVOICE, 4L))
    }

    @Test
    fun `a part paid invoice reports what is left rather than what it was raised for`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(
                invoice(id = 4, clientId = 7, total = "12000", paid = "4500", dueOn = today.minusDays(1)),
            ),
            clientNames = mapOf(7L to "Ahmed"),
        )

        assertThat(items.single().amount).isEqualTo(Money.of("7500"))
    }

    @Test
    fun `an invoice due inside the week is a warning rather than an alarm`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.plusDays(3))),
            clientNames = mapOf(7L to "Ahmed"),
        )

        val item = items.single()
        assertThat(item.kind).isEqualTo(AttentionKind.INVOICE_DUE_SOON)
        assertThat(item.severity).isEqualTo(AttentionSeverity.SOON)
        assertThat(item.daysAway).isEqualTo(3L)
    }

    @Test
    fun `an invoice due today is not overdue`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 4, clientId = 7, dueOn = today)),
        )

        assertThat(items.single().kind).isEqualTo(AttentionKind.INVOICE_DUE_SOON)
        assertThat(items.single().daysAway).isEqualTo(0L)
    }

    @Test
    fun `an invoice due beyond the window is left off the card`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(
                invoice(id = 4, clientId = 7, dueOn = today.plusDays(AttentionRules.INVOICE_DUE_SOON_DAYS + 1)),
            ),
        )

        assertThat(items).isEmpty()
    }

    @Test
    fun `an invoice whose client Mira cannot name falls back to the number`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 4, clientId = 7, dueOn = today.minusDays(2))),
            clientNames = emptyMap(),
        )

        assertThat(items.single().subject).isEqualTo("INV-2026-014")
    }

    // ------------------------------------------------------------------ ordering

    @Test
    fun `a small old debt outranks a large one that is only due next week`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(
                invoice(id = 1, clientId = 1, number = "INV-A", total = "50000", dueOn = today.plusDays(6)),
                invoice(id = 2, clientId = 2, number = "INV-B", total = "500", dueOn = today.minusDays(90)),
            ),
            clientNames = mapOf(1L to "Big", 2L to "Small"),
        )

        assertThat(items.map { it.subject }).containsExactly("Small", "Big").inOrder()
    }

    @Test
    fun `the older of two overdue invoices comes first whatever the amounts are`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(
                invoice(id = 1, clientId = 1, number = "INV-A", total = "50000", dueOn = today.minusDays(2)),
                invoice(id = 2, clientId = 2, number = "INV-B", total = "500", dueOn = today.minusDays(90)),
            ),
            clientNames = mapOf(1L to "Recent", 2L to "Ancient"),
        )

        assertThat(items.map { it.subject }).containsExactly("Ancient", "Recent").inOrder()
    }

    @Test
    fun `money only decides between two things that are equally urgent`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(
                invoice(id = 1, clientId = 1, number = "INV-A", total = "500", dueOn = today.minusDays(3)),
                invoice(id = 2, clientId = 2, number = "INV-B", total = "50000", dueOn = today.minusDays(3)),
            ),
            clientNames = mapOf(1L to "Small", 2L to "Large"),
        )

        assertThat(items.map { it.subject }).containsExactly("Large", "Small").inOrder()
    }

    @Test
    fun `an alarm outranks a warning even when the warning is closer in time`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(invoice(id = 1, clientId = 1, dueOn = today.minusDays(40))),
            clientNames = mapOf(1L to "Ahmed"),
            deadlines = listOf(deadline(projectId = 9, name = "Rayluxeo website", dueOn = today.plusDays(1))),
        )

        assertThat(items.map { it.severity })
            .containsExactly(AttentionSeverity.NOW, AttentionSeverity.SOON)
            .inOrder()
    }

    @Test
    fun `no single kind may take more than its share of the card`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            overdueTasks = (1..5).map { task(id = it.toLong(), title = "Task $it", dueAt = daysAgo(it.toLong())) },
        )

        assertThat(items).hasSize(AttentionRules.MAX_PER_KIND)
        assertThat(items.map { it.subject }).containsExactly("Task 5", "Task 4").inOrder()
    }

    @Test
    fun `the card never grows past its cap`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = listOf(
                invoice(id = 1, clientId = 1, number = "INV-A", dueOn = today.minusDays(9)),
                invoice(id = 2, clientId = 2, number = "INV-B", dueOn = today.minusDays(8)),
                invoice(id = 3, clientId = 3, number = "INV-C", dueOn = today.plusDays(2)),
                invoice(id = 4, clientId = 4, number = "INV-D", dueOn = today.plusDays(3)),
            ),
            deadlines = listOf(
                deadline(projectId = 9, name = "Rayluxeo website", dueOn = today.plusDays(1)),
                deadline(projectId = 10, name = "Portfolio", dueOn = today.plusDays(2)),
            ),
            overdueTasks = listOf(task(id = 1, title = "Invoice Ahmed", dueAt = daysAgo(1))),
            renewals = listOf(subscription(id = 1, name = "Adobe", nextChargeOn = today.plusDays(2))),
        )

        assertThat(items).hasSize(AttentionRules.MAX_ITEMS)
    }

    @Test
    fun `the order is total so the card does not reshuffle between reads`() {
        val invoices = listOf(
            invoice(id = 1, clientId = 1, number = "INV-A", total = "1000", dueOn = today.minusDays(2)),
            invoice(id = 2, clientId = 2, number = "INV-B", total = "1000", dueOn = today.minusDays(2)),
        )
        val first = AttentionRules.rank(today, zone, invoices, mapOf(1L to "Zara", 2L to "Ahmed"))
        val again = AttentionRules.rank(today, zone, invoices.reversed(), mapOf(1L to "Zara", 2L to "Ahmed"))

        assertThat(first.map { it.subject }).containsExactly("Ahmed", "Zara").inOrder()
        assertThat(again.map { it.subject }).isEqualTo(first.map { it.subject })
    }

    // ------------------------------------------------------------------ deadlines

    @Test
    fun `a project due tomorrow is a warning named after the project`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            deadlines = listOf(deadline(projectId = 9, name = "Rayluxeo website", dueOn = today.plusDays(1))),
        )

        val item = items.single()
        assertThat(item.kind).isEqualTo(AttentionKind.PROJECT_DEADLINE)
        assertThat(item.severity).isEqualTo(AttentionSeverity.SOON)
        assertThat(item.subject).isEqualTo("Rayluxeo website")
        assertThat(item.daysAway).isEqualTo(1L)
        assertThat(item.reference).isEqualTo(AttentionReference(AttentionTarget.PROJECT, 9L))
    }

    @Test
    fun `a deadline that has already passed is an alarm`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            deadlines = listOf(deadline(projectId = 9, name = "Rayluxeo website", dueOn = today.minusDays(2))),
        )

        assertThat(items.single().severity).isEqualTo(AttentionSeverity.NOW)
    }

    @Test
    fun `a deadline still a fortnight out is only a note`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            deadlines = listOf(deadline(projectId = 9, name = "Rayluxeo website", dueOn = today.plusDays(10))),
        )

        assertThat(items.single().severity).isEqualTo(AttentionSeverity.WATCH)
    }

    @Test
    fun `a deadline beyond the horizon is not the card's business`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            deadlines = listOf(
                deadline(
                    projectId = 9,
                    name = "Rayluxeo website",
                    dueOn = today.plusDays(AttentionRules.DEADLINE_HORIZON_DAYS + 1),
                ),
            ),
        )

        assertThat(items).isEmpty()
    }

    @Test
    fun `a project says one thing even when its own date and a milestone both land`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            deadlines = listOf(
                deadline(projectId = 9, name = "Rayluxeo website", dueOn = today.plusDays(9)),
                deadline(
                    projectId = 9,
                    name = "Rayluxeo website",
                    dueOn = today.plusDays(2),
                    milestone = "Handover build",
                ),
            ),
        )

        val item = items.single()
        assertThat(item.subject).isEqualTo("Handover build")
        assertThat(item.daysAway).isEqualTo(2L)
    }

    // ------------------------------------------------------------------ renewals

    @Test
    fun `a renewal inside the week is a note with the amount on it`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            renewals = listOf(subscription(id = 3, name = "Adobe", amount = "2400", nextChargeOn = today.plusDays(2))),
        )

        val item = items.single()
        assertThat(item.kind).isEqualTo(AttentionKind.SUBSCRIPTION_RENEWING)
        assertThat(item.severity).isEqualTo(AttentionSeverity.WATCH)
        assertThat(item.subject).isEqualTo("Adobe")
        assertThat(item.amount).isEqualTo(Money.of("2400"))
        assertThat(item.reference).isEqualTo(AttentionReference(AttentionTarget.SUBSCRIPTION, 3L))
    }

    @Test
    fun `a renewal further out than the week is left alone`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            renewals = listOf(
                subscription(
                    id = 3,
                    name = "Adobe",
                    nextChargeOn = today.plusDays(AttentionRules.RENEWAL_HORIZON_DAYS + 1),
                ),
            ),
        )

        assertThat(items).isEmpty()
    }

    @Test
    fun `a charge date that was never swept forward is read as the charge that is actually next`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            renewals = listOf(
                subscription(id = 3, name = "Adobe", nextChargeOn = LocalDate.of(2026, 1, 12)),
            ),
        )

        assertThat(items.single().daysAway).isEqualTo(2L)
    }

    // ------------------------------------------------------------------ tasks and follow-ups

    @Test
    fun `an overdue task is an alarm pointing at the task itself`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            overdueTasks = listOf(task(id = 6, title = "Send Ahmed the quote", dueAt = daysAgo(3))),
        )

        val item = items.single()
        assertThat(item.kind).isEqualTo(AttentionKind.TASK_OVERDUE)
        assertThat(item.severity).isEqualTo(AttentionSeverity.NOW)
        assertThat(item.subject).isEqualTo("Send Ahmed the quote")
        assertThat(item.daysAway).isEqualTo(-3L)
        assertThat(item.reference).isEqualTo(AttentionReference(AttentionTarget.TASK, 6L))
    }

    @Test
    fun `a client gone quiet becomes a note carrying how long it has been`() {
        val items = AttentionRules.rank(
            today = today,
            zone = zone,
            followUps = listOf(
                BriefFollowUp(clientId = 2, clientName = "Ahmed", lastContactAt = daysAgo(30), daysSince = 30),
            ),
        )

        val item = items.single()
        assertThat(item.kind).isEqualTo(AttentionKind.CLIENT_FOLLOW_UP)
        assertThat(item.severity).isEqualTo(AttentionSeverity.WATCH)
        assertThat(item.subject).isEqualTo("Ahmed")
        assertThat(item.daysAway).isEqualTo(-30L)
        assertThat(item.reference).isEqualTo(AttentionReference(AttentionTarget.CLIENT, 2L))
    }

    @Test
    fun `a client silent past the window needs following up`() {
        val followUps = AttentionRules.followUpsNeeded(
            today = today,
            zone = zone,
            clients = listOf(client(id = 2, name = "Ahmed")),
            lastContact = mapOf(2L to daysAgo(30)),
        )

        val followUp = followUps.single()
        assertThat(followUp.clientName).isEqualTo("Ahmed")
        assertThat(followUp.lastContactAt).isEqualTo(daysAgo(30))
        assertThat(followUp.daysSince).isEqualTo(30L)
    }

    @Test
    fun `a client seen this month is not quiet`() {
        val followUps = AttentionRules.followUpsNeeded(
            today = today,
            zone = zone,
            clients = listOf(client(id = 2, name = "Ahmed")),
            lastContact = mapOf(2L to daysAgo(AttentionRules.QUIET_AFTER_DAYS - 1)),
        )

        assertThat(followUps).isEmpty()
    }

    @Test
    fun `a client never met is measured from the day he was added`() {
        val quiet = AttentionRules.followUpsNeeded(
            today = today,
            zone = zone,
            clients = listOf(client(id = 2, name = "Ahmed", createdAt = daysAgo(60))),
        )
        val fresh = AttentionRules.followUpsNeeded(
            today = today,
            zone = zone,
            clients = listOf(client(id = 3, name = "Zara", createdAt = daysAgo(3))),
        )

        assertThat(quiet.single().lastContactAt).isNull()
        assertThat(quiet.single().daysSince).isNull()
        assertThat(fresh).isEmpty()
    }

    @Test
    fun `a client with a meeting already in the diary is not chased`() {
        val followUps = AttentionRules.followUpsNeeded(
            today = today,
            zone = zone,
            clients = listOf(client(id = 2, name = "Ahmed")),
            lastContact = mapOf(2L to daysAgo(90)),
            meetingBooked = setOf(2L),
        )

        assertThat(followUps).isEmpty()
    }

    @Test
    fun `an archived client is not chased`() {
        val followUps = AttentionRules.followUpsNeeded(
            today = today,
            zone = zone,
            clients = listOf(client(id = 2, name = "Ahmed", archived = true)),
            lastContact = mapOf(2L to daysAgo(90)),
        )

        assertThat(followUps).isEmpty()
    }

    @Test
    fun `the quietest client is named first and the list is capped`() {
        val clients = (1..7).map { client(id = it.toLong(), name = "Client $it") }
        val lastContact = (1..7).associate { it.toLong() to daysAgo(30L + it) }

        val followUps = AttentionRules.followUpsNeeded(today, zone, clients, lastContact)

        assertThat(followUps).hasSize(AttentionRules.MAX_FOLLOW_UPS)
        assertThat(followUps.first().clientName).isEqualTo("Client 7")
    }

    // ------------------------------------------------------------------ builders

    private fun daysAgo(days: Long): Instant =
        today.minusDays(days).atTime(9, 0).atZone(zone).toInstant()

    private fun invoice(
        id: Long,
        clientId: Long,
        number: String = "INV-2026-014",
        total: String = "1000",
        paid: String = "0",
        dueOn: LocalDate,
    ): Invoice {
        val amount = Money.of(total)
        return Invoice(
            id = id,
            number = number,
            clientId = clientId,
            status = InvoiceStatus.SENT,
            issuedOn = dueOn.minusDays(30),
            dueOn = dueOn,
            subtotal = amount,
            discount = Money.zero(),
            tax = Money.zero(),
            total = amount,
            amountPaid = Money.of(paid),
            createdAt = EPOCH_ISH,
            updatedAt = EPOCH_ISH,
        )
    }

    private fun deadline(
        projectId: Long,
        name: String,
        dueOn: LocalDate,
        milestone: String? = null,
    ) = BriefDeadline(
        projectId = projectId,
        projectName = name,
        clientName = null,
        dueOn = dueOn,
        milestone = milestone,
    )

    private fun subscription(
        id: Long,
        name: String,
        amount: String = "2400",
        nextChargeOn: LocalDate,
    ) = Subscription(
        id = id,
        name = name,
        amount = Money.of(amount),
        cycle = SubscriptionCycle.MONTHLY,
        nextChargeOn = nextChargeOn,
        createdAt = EPOCH_ISH,
        updatedAt = EPOCH_ISH,
    )

    private fun task(id: Long, title: String, dueAt: Instant?) = Task(
        id = id,
        title = title,
        dueAt = dueAt,
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
        /** Long before anything the windows care about, so a record's own age never interferes. */
        val EPOCH_ISH: Instant = Instant.parse("2020-01-01T00:00:00Z")
    }
}
