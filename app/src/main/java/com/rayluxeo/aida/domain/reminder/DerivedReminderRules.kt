package com.rayluxeo.aida.domain.reminder

import com.rayluxeo.aida.domain.brief.AttentionRules
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.Subscription
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Which record a derived reminder grew out of, and therefore which link identifies it. */
enum class DerivedReminderTarget { INVOICE, CLIENT, PROJECT, SUBSCRIPTION }

/**
 * A reminder Mira has decided should exist, before anything has been written.
 *
 * [subject] is a name out of Abu's own records — an invoice number, a client, a project, a
 * subscription — and never a sentence. The wording is [kind] plus [subject] rendered in his
 * language at the moment it is shown; a title written here in English could not be read out in
 * Bengali, which is the same reason `AttentionItem` carries a subject rather than prose.
 *
 * [occurrenceFrom] is the anchor that stops the daily sweep nagging: see
 * [DerivedReminderRules.decide].
 */
data class DerivedReminder(
    val kind: ReminderKind,
    val subject: String,
    val target: DerivedReminderTarget,
    val targetId: Long,
    /** The client this concerns, when there is one, so a notification can name who it is about. */
    val clientId: Long? = null,
    val triggerAt: Instant,
    /** The instant this fact's notice window opened. Stable: it never depends on when Mira looked. */
    val occurrenceFrom: Instant,
)

/**
 * Which reminders Abu's own records say should exist today, and which he has already been told.
 *
 * Pure: no repository, no clock, no Android. `DerivedReminderWorker` reads the records and writes
 * the results; everything that decides *what any of it means* is here, so every window and every
 * suppression can be pinned down in a test rather than through a database and a scheduler.
 *
 * **The windows are not this file's to invent.** How soon an invoice is worth mentioning, how far
 * out a deadline matters, how long a client has to be silent — `AttentionRules` already answers
 * all of that for the Needs Attention card, and the constants are read from there. A second set of
 * numbers would mean Home and the notification disagreeing about the same invoice.
 *
 * ## The rule that matters most: Mira raises a fact once
 *
 * The sweep runs every morning. Without care it would raise the same overdue-invoice reminder
 * every day until the invoice was paid, and Abu would turn notifications off for good.
 *
 * So every candidate carries [DerivedReminder.occurrenceFrom]: the instant its notice window
 * opened, worked out from the record alone and never from the day Mira happens to be looking. A
 * candidate is dropped when a reminder of the same kind, pointing at the same record, already has
 * a [Reminder.triggerAt] at or after that instant — because such a reminder can only have been
 * raised for this same occurrence.
 *
 * Three things follow, all of them deliberate:
 *
 * - **State is not consulted.** A reminder that has already fired, and one Abu cancelled, both
 *   count. A cancelled reminder is him saying he does not want to hear about it; raising it again
 *   tomorrow would be Mira overruling him.
 * - **A reminder he set himself counts too.** If Abu asked to be reminded about that invoice, he
 *   does not also need Mira's own.
 * - **A new occurrence is a new fact.** A subscription's next charge, a client who went quiet
 *   again after a meeting, a deadline that moved: each opens a later window, so each is raised
 *   once more. [Reminder.triggerAt] is read rather than [Reminder.armedAt] for exactly this — a
 *   snooze moves when a reminder goes off, not which fact it was about.
 */
object DerivedReminderRules {

    /**
     * The kinds this sweep owns. Nothing else may raise them from a record, and nothing here
     * touches any other kind: a reminder Abu set by voice is his, whatever it is about.
     */
    val KINDS: Set<ReminderKind> = setOf(
        ReminderKind.INVOICE,
        ReminderKind.PROJECT_DEADLINE,
        ReminderKind.SUBSCRIPTION,
        ReminderKind.CLIENT_FOLLOW_UP,
    )

    /**
     * The hours Mira may speak up in when the reminder was her idea rather than his.
     *
     * A reminder Abu set for three in the morning is his business. One she worked out from a
     * record is not, and the sweep behind it is deferrable work that the OS may run at any hour —
     * so a fact found at 03:00 waits for the morning rather than waking him with it.
     */
    val WAKING_FROM: LocalTime = LocalTime.of(8, 0)

    /** The far edge of that window. After it, a derived reminder waits for the next morning. */
    val WAKING_UNTIL: LocalTime = LocalTime.of(21, 0)

    /**
     * The most reminders of one kind a single sweep raises.
     *
     * A first run on a phone with two years of records behind it would otherwise raise forty at
     * once, which is not a morning briefing but a wall Abu swipes away. What is left is not lost:
     * it has not been raised, so tomorrow's sweep sees it again, and the ones already raised are
     * suppressed — the backlog drains a few a day in the order they matter.
     */
    const val MAX_PER_KIND: Int = 5

    /**
     * How far ahead of now a reminder for a moment that has already passed is placed.
     *
     * An overdue invoice's moment is behind us, and a reminder cannot be scheduled for the past.
     * A minute reads as "now" to Abu and is comfortably in the future by the time the write
     * reaches the scheduler.
     */
    private val SHORTLY: Duration = Duration.ofMinutes(1)

    /**
     * Every reminder that should exist and does not yet, most urgent kind first.
     *
     * [existing] must be every reminder of [KINDS] in any state, not only the scheduled ones — the
     * fired and cancelled ones are precisely the evidence that a fact has already been raised.
     *
     * [lastContact] is the last meeting that has already happened, by client id, and
     * [meetingBooked] names the clients with one in the diary; both are read the way the brief
     * reads them, so the card and the reminder cannot disagree about who has gone quiet.
     *
     * Nothing here is invented: every result points at a record that exists, and a day with
     * nothing on it returns an empty list.
     */
    fun decide(
        now: Instant,
        zone: ZoneId,
        unpaidInvoices: List<Invoice> = emptyList(),
        openProjects: List<Project> = emptyList(),
        renewals: List<Subscription> = emptyList(),
        clients: List<Client> = emptyList(),
        lastContact: Map<Long, Instant> = emptyMap(),
        meetingBooked: Set<Long> = emptySet(),
        existing: List<Reminder> = emptyList(),
    ): List<DerivedReminder> {
        val today = now.atZone(zone).toLocalDate()
        val triggerAt = nextWakingMoment(now, zone)
        val candidates = buildList {
            addAll(invoiceCandidates(today, zone, unpaidInvoices, triggerAt))
            addAll(deadlineCandidates(today, zone, openProjects, triggerAt))
            addAll(renewalCandidates(today, zone, renewals, triggerAt))
            addAll(followUpCandidates(today, zone, clients, lastContact, meetingBooked, triggerAt))
        }
        return candidates
            .filterNot { it.alreadyRaisedIn(existing) }
            .groupBy { it.kind }
            .flatMap { (_, ofKind) -> ofKind.take(MAX_PER_KIND) }
    }

    /**
     * An invoice is worth one notice as it comes due and one more once it is late.
     *
     * The two are told apart by the window they open in — the due-soon notice opens
     * [AttentionRules.INVOICE_DUE_SOON_DAYS] before the due date, the overdue one on the morning
     * after it — so the second is not swallowed by the first, and neither comes back.
     *
     * A draft is skipped for the same reason the card skips it: nobody is late paying a bill Abu
     * never sent, and telling him otherwise would send him chasing his own paperwork.
     */
    private fun invoiceCandidates(
        today: LocalDate,
        zone: ZoneId,
        invoices: List<Invoice>,
        triggerAt: Instant,
    ): List<DerivedReminder> = invoices
        .asSequence()
        .filter { it.status != InvoiceStatus.DRAFT }
        .sortedWith(compareBy<Invoice> { it.dueOn }.thenBy { it.id })
        .mapNotNull { invoice ->
            val opensOn = when {
                invoice.dueOn.isBefore(today) -> invoice.dueOn.plusDays(1)
                ChronoUnit.DAYS.between(today, invoice.dueOn) <= AttentionRules.INVOICE_DUE_SOON_DAYS ->
                    invoice.dueOn.minusDays(AttentionRules.INVOICE_DUE_SOON_DAYS)
                else -> return@mapNotNull null
            }
            DerivedReminder(
                kind = ReminderKind.INVOICE,
                // The number rather than the client: it names one document, and the client link
                // beside it lets the notification say whose bill it is.
                subject = invoice.number,
                target = DerivedReminderTarget.INVOICE,
                targetId = invoice.id,
                clientId = invoice.clientId,
                triggerAt = triggerAt,
                occurrenceFrom = opensOn.atStartOfDay(zone).toInstant(),
            )
        }
        .toList()

    /**
     * A project deadline inside the horizon the card already uses.
     *
     * The project's own due date only. A milestone deadline has nowhere to be recorded — the
     * reminder row links to a project and not to a milestone — so two milestones of one project
     * could not be told apart, and the second would be suppressed as a repeat of the first. The
     * Needs Attention card reads milestones and says so; this does not pretend to.
     */
    private fun deadlineCandidates(
        today: LocalDate,
        zone: ZoneId,
        projects: List<Project>,
        triggerAt: Instant,
    ): List<DerivedReminder> = projects
        .asSequence()
        .filter { it.isOpen }
        .mapNotNull { project -> project.dueOn?.let { project to it } }
        .filter { (_, dueOn) -> AttentionRules.isDeadlineInWindow(today, dueOn) }
        .sortedWith(compareBy<Pair<Project, LocalDate>> { it.second }.thenBy { it.first.id })
        .map { (project, dueOn) ->
            DerivedReminder(
                kind = ReminderKind.PROJECT_DEADLINE,
                subject = project.name,
                target = DerivedReminderTarget.PROJECT,
                targetId = project.id,
                clientId = project.clientId,
                triggerAt = triggerAt,
                occurrenceFrom = dueOn.minusDays(AttentionRules.DEADLINE_HORIZON_DAYS)
                    .atStartOfDay(zone)
                    .toInstant(),
            )
        }
        .toList()

    /**
     * A renewal close enough that Abu could still cancel it before the card is charged, which is
     * the only reason to mention one at all.
     *
     * The charge date is taken from the subscription rather than from its stored column: a row
     * whose date has not been swept forward yet would otherwise read as months overdue and open a
     * window that closed long ago.
     */
    private fun renewalCandidates(
        today: LocalDate,
        zone: ZoneId,
        subscriptions: List<Subscription>,
        triggerAt: Instant,
    ): List<DerivedReminder> = subscriptions
        .asSequence()
        .filter { it.isActive && it.deletedAt == null }
        .map { it to it.nextChargeOnOrAfter(today) }
        .filter { (_, charge) ->
            ChronoUnit.DAYS.between(today, charge) <= AttentionRules.RENEWAL_HORIZON_DAYS
        }
        .sortedWith(compareBy<Pair<Subscription, LocalDate>> { it.second }.thenBy { it.first.id })
        .map { (subscription, charge) ->
            DerivedReminder(
                kind = ReminderKind.SUBSCRIPTION,
                subject = subscription.name,
                target = DerivedReminderTarget.SUBSCRIPTION,
                targetId = subscription.id,
                triggerAt = triggerAt,
                occurrenceFrom = charge.minusDays(AttentionRules.RENEWAL_HORIZON_DAYS)
                    .atStartOfDay(zone)
                    .toInstant(),
            )
        }
        .toList()

    /**
     * A client Abu has not sat down with in [AttentionRules.QUIET_AFTER_DAYS].
     *
     * The window opens on the day the silence reached that length, counted from the last meeting
     * that has already happened — or, for a client he has never met, from the day he added them,
     * so a client taken on last week is not immediately a reproach. That anchor is what makes the
     * follow-up renew itself honestly: after a meeting the silence restarts, the window opens
     * later, and one more reminder becomes due. Until then there is nothing new to say.
     *
     * A client with a meeting already in the diary is not quiet, whatever the gap behind him says.
     */
    private fun followUpCandidates(
        today: LocalDate,
        zone: ZoneId,
        clients: List<Client>,
        lastContact: Map<Long, Instant>,
        meetingBooked: Set<Long>,
        triggerAt: Instant,
    ): List<DerivedReminder> = clients
        .asSequence()
        .filter { !it.isArchived && it.deletedAt == null && it.id !in meetingBooked }
        .map { client ->
            val since = (lastContact[client.id] ?: client.createdAt).atZone(zone).toLocalDate()
            Triple(client, since, ChronoUnit.DAYS.between(since, today))
        }
        .filter { (_, _, silentFor) -> silentFor >= AttentionRules.QUIET_AFTER_DAYS }
        .sortedWith(
            compareByDescending<Triple<Client, LocalDate, Long>> { it.third }
                .thenBy { it.first.name }
                .thenBy { it.first.id },
        )
        .map { (client, since, _) ->
            DerivedReminder(
                kind = ReminderKind.CLIENT_FOLLOW_UP,
                subject = client.name,
                target = DerivedReminderTarget.CLIENT,
                targetId = client.id,
                clientId = client.id,
                triggerAt = triggerAt,
                occurrenceFrom = since.plusDays(AttentionRules.QUIET_AFTER_DAYS)
                    .atStartOfDay(zone)
                    .toInstant(),
            )
        }
        .toList()

    private fun DerivedReminder.alreadyRaisedIn(existing: List<Reminder>): Boolean =
        existing.any { reminder ->
            reminder.kind == kind &&
                reminder.linkTo(target) == targetId &&
                !reminder.triggerAt.isBefore(occurrenceFrom)
        }

    private fun Reminder.linkTo(target: DerivedReminderTarget): Long? = when (target) {
        DerivedReminderTarget.INVOICE -> linkedInvoiceId
        DerivedReminderTarget.CLIENT -> linkedClientId
        DerivedReminderTarget.PROJECT -> linkedProjectId
        DerivedReminderTarget.SUBSCRIPTION -> linkedSubscriptionId
    }

    /**
     * The first moment from [now] at which Mira may speak, in Abu's zone.
     *
     * Both candidates are resolved through the zone, so a time that does not exist on a
     * spring-forward morning moves to the far side of the gap rather than being invented.
     */
    private fun nextWakingMoment(now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        val time = local.toLocalTime()
        return when {
            time.isBefore(WAKING_FROM) -> local.toLocalDate().atTime(WAKING_FROM).atZone(zone).toInstant()
            time.isBefore(WAKING_UNTIL) -> now.plus(SHORTLY)
            else -> local.toLocalDate().plusDays(1).atTime(WAKING_FROM).atZone(zone).toInstant()
        }
    }
}
