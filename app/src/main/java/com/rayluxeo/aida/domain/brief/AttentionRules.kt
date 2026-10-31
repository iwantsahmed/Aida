package com.rayluxeo.aida.domain.brief

import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What goes on the Needs Attention card, and in what order.
 *
 * Pure functions over records that have already been read, holding no repository and no clock, so
 * every window and every tie in the ordering can be tested on its own rather than through a
 * database. [DefaultDailyBriefEngine] does the reading; this decides what any of it means.
 *
 * Two promises the card depends on:
 *
 * - **Nothing here is invented.** Every item comes from a record that exists, and a day with
 *   nothing on it produces an empty list. Mira does not manufacture urgency to fill a card.
 * - **The order is total.** Two runs over the same records produce the same list in the same
 *   order, so the card does not reshuffle itself between emissions on Home.
 */
object AttentionRules {

    /**
     * How far ahead an unpaid invoice has to be before it stops being Abu's problem this week.
     *
     * Seven days is the horizon he can act inside: long enough to chase a client and still be
     * paid on time, short enough that a bill due next month does not sit on the card for weeks
     * until he stops reading it.
     */
    const val INVOICE_DUE_SOON_DAYS: Long = 7L

    /**
     * How far ahead a project or milestone deadline is worth naming.
     *
     * A fortnight is roughly the point at which a deadline changes what Abu does today. Anything
     * further out belongs on the Projects screen, where he is looking at the plan rather than at
     * the day.
     */
    const val DEADLINE_HORIZON_DAYS: Long = 14L

    /**
     * Inside this many days a deadline stops being a note and becomes a warning.
     *
     * Three days is a working weekend: past it there is no longer room to reschedule around the
     * deadline, only room to meet it or miss it.
     */
    const val DEADLINE_IMMINENT_DAYS: Long = 3L

    /**
     * How soon a renewal has to be before Mira mentions it.
     *
     * A week is enough notice to cancel a subscription Abu no longer wants before the card is
     * charged, which is the only reason to mention a renewal at all.
     */
    const val RENEWAL_HORIZON_DAYS: Long = 7L

    /**
     * How long a client has to have been silent before Mira suggests a follow-up.
     *
     * Three weeks is past the point where a gap is just a busy fortnight. Shorter than this and
     * the card would name every client Abu happens not to have met since the weekend.
     */
    const val QUIET_AFTER_DAYS: Long = 21L

    /**
     * A booked meeting inside this many days means the follow-up has already happened.
     *
     * Telling Abu to chase a client he is sitting down with on Thursday is the kind of advice
     * that teaches him to stop reading the card.
     */
    const val UPCOMING_CONTACT_DAYS: Long = 14L

    /**
     * The most items the card ever carries. A card with forty rows is not a card; it is the list
     * Abu was trying to avoid reading.
     */
    const val MAX_ITEMS: Int = 5

    /**
     * The most items any one kind may contribute.
     *
     * Without it a week of overdue tasks — all of them [AttentionSeverity.NOW], all of them older
     * than the overdue invoice — would fill the card by itself and hide the money. The cap is what
     * keeps the card a summary of several things rather than the top of one list.
     */
    const val MAX_PER_KIND: Int = 2

    /** The most clients the brief names as needing a follow-up in one go. */
    const val MAX_FOLLOW_UPS: Int = 5

    /**
     * Ranks everything already read into the card's order.
     *
     * [unpaidInvoices] is the repository's own set of what is still owed, split here into overdue
     * and due-soon by exactly the rule `InvoiceRepository.observeOverdue` uses — due date strictly
     * before today — so the card and the Invoices screen can never disagree about which is which.
     */
    fun rank(
        today: LocalDate,
        zone: ZoneId,
        unpaidInvoices: List<Invoice> = emptyList(),
        clientNames: Map<Long, String> = emptyMap(),
        deadlines: List<BriefDeadline> = emptyList(),
        renewals: List<Subscription> = emptyList(),
        overdueTasks: List<Task> = emptyList(),
        followUps: List<BriefFollowUp> = emptyList(),
    ): List<AttentionItem> {
        val items = buildList {
            addAll(invoiceItems(today, zone, unpaidInvoices, clientNames))
            addAll(deadlineItems(today, zone, deadlines))
            addAll(renewalItems(today, zone, renewals))
            addAll(overdueTaskItems(today, zone, overdueTasks))
            addAll(followUpItems(followUps))
        }
        return items.sortedWith(BY_URGENCY).cappedPerKind().take(MAX_ITEMS)
    }

    /**
     * Which clients have gone quiet, given when Abu last sat down with each of them.
     *
     * [lastContact] is the last meeting that has already happened, by client id; a client absent
     * from it has never been met. [meetingBooked] names the clients with one in the diary, who are
     * therefore not quiet whatever the gap behind them says.
     *
     * A client Abu added last week and has not met yet is NOT a follow-up: with no contact to
     * measure from, the silence is counted from when the client record was created, so a new
     * client has to go quiet on its own terms before it appears here.
     */
    fun followUpsNeeded(
        today: LocalDate,
        zone: ZoneId,
        clients: List<Client>,
        lastContact: Map<Long, Instant> = emptyMap(),
        meetingBooked: Set<Long> = emptySet(),
    ): List<BriefFollowUp> = clients
        .asSequence()
        .filter { !it.isArchived && it.id !in meetingBooked }
        .mapNotNull { client ->
            val contactedAt = lastContact[client.id]
            val daysSince = contactedAt?.let { daysUntil(today, zone, it) }
            val silentFor = daysSince ?: daysUntil(today, zone, client.createdAt)
            if (silentFor < QUIET_AFTER_DAYS) {
                null
            } else {
                silentFor to BriefFollowUp(
                    clientId = client.id,
                    clientName = client.name,
                    lastContactAt = contactedAt,
                    daysSince = daysSince,
                )
            }
        }
        .sortedWith(compareByDescending<Pair<Long, BriefFollowUp>> { it.first }.thenBy { it.second.clientName })
        .map { it.second }
        .take(MAX_FOLLOW_UPS)
        .toList()

    /** Whether a deadline is close enough to be worth naming today. Past ones always are. */
    fun isDeadlineInWindow(today: LocalDate, dueOn: LocalDate): Boolean =
        ChronoUnit.DAYS.between(today, dueOn) <= DEADLINE_HORIZON_DAYS

    private fun invoiceItems(
        today: LocalDate,
        zone: ZoneId,
        invoices: List<Invoice>,
        clientNames: Map<Long, String>,
    ): List<AttentionItem> = invoices.mapNotNull { invoice ->
        // A draft has not been sent, so nobody is late paying it. `observeUnpaid` treats it as
        // still owed, which is right for a ledger total and wrong for this card: "Ahmed's invoice
        // is overdue" about a bill Abu never sent would send him chasing his own paperwork.
        if (invoice.status == InvoiceStatus.DRAFT) return@mapNotNull null
        val daysAway = ChronoUnit.DAYS.between(today, invoice.dueOn)
        val kind = when {
            invoice.dueOn.isBefore(today) -> AttentionKind.INVOICE_OVERDUE
            daysAway <= INVOICE_DUE_SOON_DAYS -> AttentionKind.INVOICE_DUE_SOON
            else -> return@mapNotNull null
        }
        AttentionItem(
            kind = kind,
            // The client is who Abu chases; the number is only how he finds the document again.
            subject = clientNames[invoice.clientId] ?: invoice.number,
            severity = if (kind == AttentionKind.INVOICE_OVERDUE) {
                AttentionSeverity.NOW
            } else {
                AttentionSeverity.SOON
            },
            amount = invoice.outstanding,
            at = invoice.dueOn.atStartOfDay(zone).toInstant(),
            daysAway = daysAway,
            reference = AttentionReference(AttentionTarget.INVOICE, invoice.id),
        )
    }

    /**
     * One row per project. A project with both its own due date and a milestone inside the window
     * would otherwise take two of the five places to say one thing, so the nearer of the two wins.
     */
    private fun deadlineItems(
        today: LocalDate,
        zone: ZoneId,
        deadlines: List<BriefDeadline>,
    ): List<AttentionItem> = deadlines
        .filter { isDeadlineInWindow(today, it.dueOn) }
        .groupBy { it.projectId }
        .mapNotNull { (_, forProject) -> forProject.minByOrNull { it.dueOn } }
        .map { deadline ->
            val daysAway = ChronoUnit.DAYS.between(today, deadline.dueOn)
            AttentionItem(
                kind = AttentionKind.PROJECT_DEADLINE,
                subject = deadline.milestone ?: deadline.projectName,
                severity = when {
                    daysAway < 0L -> AttentionSeverity.NOW
                    daysAway <= DEADLINE_IMMINENT_DAYS -> AttentionSeverity.SOON
                    else -> AttentionSeverity.WATCH
                },
                at = deadline.dueOn.atStartOfDay(zone).toInstant(),
                daysAway = daysAway,
                reference = AttentionReference(AttentionTarget.PROJECT, deadline.projectId),
            )
        }

    private fun renewalItems(
        today: LocalDate,
        zone: ZoneId,
        renewals: List<Subscription>,
    ): List<AttentionItem> = renewals.mapNotNull { subscription ->
        // A row whose charge date has not been swept forward yet would otherwise read as months
        // overdue; the subscription itself knows where the charge actually lands.
        val charge = subscription.nextChargeOnOrAfter(today)
        val daysAway = ChronoUnit.DAYS.between(today, charge)
        if (daysAway > RENEWAL_HORIZON_DAYS) {
            null
        } else {
            AttentionItem(
                kind = AttentionKind.SUBSCRIPTION_RENEWING,
                subject = subscription.name,
                severity = AttentionSeverity.WATCH,
                amount = subscription.amount,
                at = charge.atStartOfDay(zone).toInstant(),
                daysAway = daysAway,
                reference = AttentionReference(AttentionTarget.SUBSCRIPTION, subscription.id),
            )
        }
    }

    private fun overdueTaskItems(
        today: LocalDate,
        zone: ZoneId,
        tasks: List<Task>,
    ): List<AttentionItem> = tasks.map { task ->
        AttentionItem(
            kind = AttentionKind.TASK_OVERDUE,
            subject = task.title,
            severity = AttentionSeverity.NOW,
            at = task.dueAt,
            daysAway = task.dueAt?.let { daysUntil(today, zone, it).unaryMinus() },
            reference = AttentionReference(AttentionTarget.TASK, task.id),
        )
    }

    private fun followUpItems(followUps: List<BriefFollowUp>): List<AttentionItem> =
        followUps.map { followUp ->
            AttentionItem(
                kind = AttentionKind.CLIENT_FOLLOW_UP,
                subject = followUp.clientName,
                severity = AttentionSeverity.WATCH,
                at = followUp.lastContactAt,
                daysAway = followUp.daysSince?.unaryMinus(),
                reference = AttentionReference(AttentionTarget.CLIENT, followUp.clientId),
            )
        }

    /** Whole days from [moment] to [today], in Abu's zone. Negative for a moment still to come. */
    private fun daysUntil(today: LocalDate, zone: ZoneId, moment: Instant): Long =
        ChronoUnit.DAYS.between(moment.atZone(zone).toLocalDate(), today)

    private fun List<AttentionItem>.cappedPerKind(): List<AttentionItem> {
        val taken = mutableMapOf<AttentionKind, Int>()
        return filter { item ->
            val already = taken[item.kind] ?: 0
            if (already >= MAX_PER_KIND) {
                false
            } else {
                taken[item.kind] = already + 1
                true
            }
        }
    }

    /**
     * Severity, then how close the moment is, then how much money is on it.
     *
     * Ascending [AttentionItem.daysAway] reads correctly in both directions at once: the most
     * overdue thing is the most negative, and the soonest thing to come is the smallest positive.
     * That is what makes a 500-taka invoice three months late outrank a 50,000-taka one due next
     * week — the money is only consulted once the severity and the moment have tied, and comparing
     * amounts in two currencies is meaningless anyway, so it can never decide anything larger.
     * Kind and subject close the order so it is total and the card does not reshuffle.
     */
    private val BY_URGENCY: Comparator<AttentionItem> = Comparator { first, second ->
        var order = first.severity.compareTo(second.severity)
        if (order == 0) order = compareDaysAway(first.daysAway, second.daysAway)
        if (order == 0) order = compareAmount(first.amount, second.amount)
        if (order == 0) order = first.kind.compareTo(second.kind)
        if (order == 0) order = first.subject.compareTo(second.subject)
        order
    }

    /** Sooner first; an item with no moment at all sorts after every item that has one. */
    private fun compareDaysAway(first: Long?, second: Long?): Int = when {
        first == null && second == null -> 0
        first == null -> 1
        second == null -> -1
        else -> first.compareTo(second)
    }

    /** Larger first; an item carrying no amount sorts after every item that does. */
    private fun compareAmount(first: Money?, second: Money?): Int = when {
        first == null && second == null -> 0
        first == null -> 1
        second == null -> -1
        else -> second.amount.compareTo(first.amount)
    }
}
