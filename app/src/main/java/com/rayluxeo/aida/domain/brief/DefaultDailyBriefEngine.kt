package com.rayluxeo.aida.domain.brief

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.CalendarEvent
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.repository.BriefRepository
import com.rayluxeo.aida.domain.repository.CalendarRepository
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.MeetingRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import com.rayluxeo.aida.domain.salah.SalahClock
import com.rayluxeo.aida.domain.usecase.GetSalahDayUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Mira's picture of today, assembled on the device from Abu's own records.
 *
 * Nothing here consults Gemini and nothing here needs the radio: it is Room and the system
 * calendar provider, which is why the brief still works on a phone in flight mode.
 *
 * Three decisions worth knowing before changing anything:
 *
 * - **The counts are not recounted.** `BriefRepository.todayBrief` already counts in SQL, in
 *   Abu's own zone, and doing it again in Kotlin would give a second answer that drifts from the
 *   first. The lists here are the rows behind those counts, capped for reading; the counts stay
 *   exact.
 * - **A failure is never flattened into an empty list.** If storage cannot answer, [today] and
 *   [attention] fail, because "nothing to report" and "could not look" are different sentences
 *   and Abu can only tell them apart if Mira does.
 * - **The calendar is the exception, and it has its own flag.** Permission withheld or a provider
 *   that refused leaves [DailyBriefReport.calendarReadable] false rather than failing the whole
 *   brief. Telling Abu his day is clear when Mira was simply not allowed to look is the worst
 *   thing this feature could do; leaving the rest of the brief unbuilt because of it is second.
 */
class DefaultDailyBriefEngine @Inject constructor(
    private val briefs: BriefRepository,
    private val tasks: TaskRepository,
    private val reminders: ReminderRepository,
    private val calendar: CalendarRepository,
    private val invoices: InvoiceRepository,
    private val payments: PaymentRepository,
    private val projects: ProjectRepository,
    private val subscriptions: SubscriptionRepository,
    private val clients: ClientRepository,
    private val meetings: MeetingRepository,
    private val profiles: UserProfileRepository,
    private val clock: MiraClock,
    /**
     * The next prayer, the one Salah fact the morning brief carries. Optional so the engine can be
     * built in a plain unit test without the Salah graph; absent, the brief simply says nothing
     * about prayers, and so does a brief for an Abu who has not set a prayer location.
     */
    private val salahDay: GetSalahDayUseCase? = null,
) : DailyBriefEngine {

    override suspend fun today(): AppResult<DailyBriefReport> {
        val now = clock.now()
        val zone = clock.zone()
        val today = clock.today()

        val core = when (val result = briefs.todayBrief()) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }
        val signals = when (val result = gather(today, now, zone)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }

        // A profile that cannot be read costs Mira a name to greet him by, which is decoration.
        // It is the one read here allowed to fail quietly, because a null name states nothing false.
        val profile = profiles.current().getOrNull()
        val schedule = readTodaysEvents(core.date, zone)
        val recentPayments = payments.observeRecent(MAX_RECENT_PAYMENTS).first()
        val dueToday = tasks.observe(TaskFilter.DUE_TODAY).first()

        return AppResult.Success(
            DailyBriefReport(
                date = core.date,
                dayPart = core.dayPart,
                // Absent a profile name, the owner still has one: Mira is Abu's and only
                // Abu's (AGENTS.md §1). A proper name reads the same in either language, so it
                // is a fact about the app rather than the per-language wording §4 keeps out of
                // here — the greeting says "Good morning, Abu" rather than trailing off unnamed.
                ownerName = profile?.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: OWNER_NAME,
                openTaskCount = core.openTaskCount,
                dueTodayCount = core.dueTodayCount,
                overdueCount = core.overdueCount,
                completedTodayCount = core.completedTodayCount,
                dueToday = dueToday.take(MAX_TASK_ROWS).map { it.toBriefTask(isOverdue = false) },
                overdue = signals.overdueTasks.take(MAX_TASK_ROWS).map { it.toBriefTask(isOverdue = true) },
                nextEvent = schedule.events.firstOrNull { !it.hasFinishedBy(now) },
                todaysEvents = schedule.events,
                calendarReadable = schedule.readable,
                upcomingReminders = upcomingReminders(now),
                money = moneyFrom(
                    unpaid = signals.unpaidInvoices,
                    payments = recentPayments,
                    clientNames = signals.clientNames,
                    today = today,
                    fallbackCurrency = profile?.defaultCurrency ?: Money.BDT,
                ),
                deadlines = signals.deadlines,
                followUps = signals.followUps,
                attention = rank(today, zone, signals),
                salah = salahSummary(now, zone, today),
            )
        )
    }

    /**
     * The next prayer for the morning brief, computed on the device from Abu's saved place.
     *
     * The use case fails with NOT_FOUND when no location is set, which is exactly when the brief
     * should stay silent about Salah — so a failure degrades to null rather than failing the whole
     * brief (§3: an offline-optional path never blocks). [SalahClock] handles the after-Isha case,
     * returning tomorrow's Fajr, and [BriefSalah.isTomorrow] records that so the line can say it.
     */
    private suspend fun salahSummary(now: Instant, zone: ZoneId, today: LocalDate): BriefSalah? {
        val useCase = salahDay ?: return null
        val day = useCase(today).getOrNull() ?: return null
        val info = SalahClock.nextPrayer(now, day.times, day.tomorrowFajr)
        val nextDate = LocalDateTime.ofInstant(info.nextTime.toInstant(), zone).toLocalDate()
        return BriefSalah(
            nextPrayer = info.next,
            nextTime = info.nextTime.toInstant(),
            isTomorrow = nextDate.isAfter(today),
        )
    }

    /**
     * Home rebuilds this on every repository emission, so it reads only what the rules weigh: the
     * unpaid invoices, the clients, the overdue tasks, the open projects and their milestones, the
     * near renewals and the meeting history. It does not touch the calendar provider, the
     * reminders or the payments, and it does not build a brief to throw most of it away.
     */
    override suspend fun attention(): AppResult<List<AttentionItem>> {
        val now = clock.now()
        val zone = clock.zone()
        val today = clock.today()
        return gather(today, now, zone).map { signals -> rank(today, zone, signals) }
    }

    private fun rank(today: LocalDate, zone: ZoneId, signals: Signals): List<AttentionItem> =
        AttentionRules.rank(
            today = today,
            zone = zone,
            unpaidInvoices = signals.unpaidInvoices,
            clientNames = signals.clientNames,
            deadlines = signals.deadlines,
            renewals = signals.renewals,
            overdueTasks = signals.overdueTasks,
            followUps = signals.followUps,
        )

    /** Everything the ranking weighs, read once so [today] and [attention] cannot disagree. */
    private data class Signals(
        val unpaidInvoices: List<Invoice>,
        val clientNames: Map<Long, String>,
        val deadlines: List<BriefDeadline>,
        val renewals: List<Subscription>,
        val overdueTasks: List<Task>,
        val followUps: List<BriefFollowUp>,
    )

    private suspend fun gather(today: LocalDate, now: Instant, zone: ZoneId): AppResult<Signals> {
        val renewals = when (
            val result = subscriptions.dueOnOrBefore(today.plusDays(AttentionRules.RENEWAL_HORIZON_DAYS))
        ) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }

        val clientRows = clients.observe(ClientFilter.ALL).first()
        // Every live client, so an invoice raised against an archived one is still named. Who Abu
        // should chase is a narrower question, and `followUpsNeeded` asks it separately.
        val clientNames = clientRows.associate { it.id to it.name }
        val openProjects = projects.observe(ProjectFilter.OPEN).first()
        val contact = contactFrom(meetingHistory(today, zone), now)

        return AppResult.Success(
            Signals(
                unpaidInvoices = invoices.observeUnpaid().first(),
                clientNames = clientNames,
                deadlines = deadlinesFor(openProjects, clientNames, today),
                renewals = renewals,
                overdueTasks = tasks.observe(TaskFilter.OVERDUE).first(),
                followUps = AttentionRules.followUpsNeeded(
                    today = today,
                    zone = zone,
                    clients = clientRows,
                    lastContact = contact.lastContact,
                    meetingBooked = contact.booked,
                ),
            )
        )
    }

    /**
     * A project's own due date and its nearest unmet milestone are both deadlines, and either can
     * be the one that matters — a milestone can fall tomorrow on a project due in three months.
     *
     * Milestones cost one query per open project, which is the price of that: they have no bulk
     * accessor, and the alternative is a card that quietly cannot see half the deadlines.
     */
    private suspend fun deadlinesFor(
        openProjects: List<Project>,
        clientNames: Map<Long, String>,
        today: LocalDate,
    ): List<BriefDeadline> = buildList {
        for (project in openProjects) {
            val clientName = project.clientId?.let { clientNames[it] }
            project.dueOn
                ?.takeIf { AttentionRules.isDeadlineInWindow(today, it) }
                ?.let { dueOn ->
                    add(
                        BriefDeadline(
                            projectId = project.id,
                            projectName = project.name,
                            clientName = clientName,
                            dueOn = dueOn,
                        )
                    )
                }
            projects.observeMilestones(project.id).first()
                .asSequence()
                .filter { !it.isComplete }
                .mapNotNull { milestone -> milestone.dueOn?.let { it to milestone.title } }
                .minByOrNull { it.first }
                ?.takeIf { AttentionRules.isDeadlineInWindow(today, it.first) }
                ?.let { (dueOn, title) ->
                    add(
                        BriefDeadline(
                            projectId = project.id,
                            projectName = project.name,
                            clientName = clientName,
                            dueOn = dueOn,
                            milestone = title,
                        )
                    )
                }
        }
    }.sortedWith(compareBy<BriefDeadline> { it.dueOn }.thenBy { it.projectName })

    /**
     * The whole meeting history plus the near diary, in one range query.
     *
     * A query per client would make the Needs Attention card cost a query for every client Abu has
     * ever had; the meetings table is small and reading it whole is the cheaper of the two.
     */
    private suspend fun meetingHistory(today: LocalDate, zone: ZoneId): List<Meeting> {
        val until = today.plusDays(AttentionRules.UPCOMING_CONTACT_DAYS + 1).atStartOfDay(zone).toInstant()
        return meetings.observeBetween(Instant.EPOCH, until).first()
    }

    private data class Contact(val lastContact: Map<Long, Instant>, val booked: Set<Long>)

    private fun contactFrom(history: List<Meeting>, now: Instant): Contact {
        val lastContact = mutableMapOf<Long, Instant>()
        val booked = mutableSetOf<Long>()
        for (meeting in history) {
            val clientId = meeting.clientId ?: continue
            if (meeting.startsAt.isAfter(now)) {
                booked += clientId
            } else {
                val previous = lastContact[clientId]
                if (previous == null || meeting.startsAt.isAfter(previous)) {
                    lastContact[clientId] = meeting.startsAt
                }
            }
        }
        return Contact(lastContact, booked)
    }

    private data class Schedule(val readable: Boolean, val events: List<BriefEvent>)

    /**
     * [Schedule.readable] is true only when a read actually came back. `canRead` is asked first to
     * avoid a provider call that is certain to be refused, but a read that fails afterwards — a
     * permission revoked mid-session, a provider that threw — lands in the same place, because in
     * both cases Mira did not see the day.
     */
    private suspend fun readTodaysEvents(date: LocalDate, zone: ZoneId): Schedule {
        if (!calendar.canRead()) return Schedule(readable = false, events = emptyList())
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        return when (val result = calendar.eventsBetween(from, to)) {
            is AppResult.Failure -> Schedule(readable = false, events = emptyList())
            is AppResult.Success -> Schedule(
                readable = true,
                events = result.value
                    .sortedWith(compareBy<CalendarEvent> { it.startsAt }.thenBy { it.title })
                    .map { it.toBriefEvent() },
            )
        }
    }

    private suspend fun upcomingReminders(now: Instant): List<BriefReminder> =
        reminders.observeUpcoming().first()
            .asSequence()
            // The port returns everything still scheduled, including alarms whose moment has gone
            // by without firing. Those are not upcoming and saying so would be wrong.
            .filter { !it.triggerAt.isBefore(now) }
            .sortedBy { it.triggerAt }
            .take(MAX_UPCOMING_REMINDERS)
            .map { BriefReminder(id = it.id, title = it.title, triggerAt = it.triggerAt) }
            .toList()

    /**
     * Mira holds no exchange rates, so a total is stated only when every amount behind it is in
     * one currency. Null means the figure cannot honestly be given as one number — an empty set
     * of invoices is zero, not unknown.
     */
    private fun moneyFrom(
        unpaid: List<Invoice>,
        payments: List<Payment>,
        clientNames: Map<Long, String>,
        today: LocalDate,
        fallbackCurrency: Currency,
    ): BriefMoney {
        val overdue = unpaid.filter { it.dueOn.isBefore(today) }
        val dueSoon = unpaid.filter {
            !it.dueOn.isBefore(today) &&
                ChronoUnit.DAYS.between(today, it.dueOn) <= AttentionRules.INVOICE_DUE_SOON_DAYS
        }
        return BriefMoney(
            outstandingTotal = totalOf(unpaid.map { it.outstanding }, fallbackCurrency),
            overdueTotal = totalOf(overdue.map { it.outstanding }, fallbackCurrency),
            invoiceCount = unpaid.size,
            overdueCount = overdue.size,
            dueSoon = dueSoon.map { it.toDueSoonInvoice(clientNames) },
            recentPayments = payments.map { payment ->
                BriefPayment(
                    id = payment.id,
                    clientName = payment.clientId?.let { clientNames[it] },
                    amount = payment.amount,
                    receivedAt = payment.receivedAt,
                )
            },
        )
    }

    private fun totalOf(amounts: List<Money>, fallbackCurrency: Currency): Money? = when {
        amounts.isEmpty() -> Money.zero(fallbackCurrency)
        amounts.any { it.currency != amounts.first().currency } -> null
        else -> amounts.reduce(Money::plus)
    }

    /** Only invoices that have NOT passed their due date: an overdue one is not due soon. */
    private fun Invoice.toDueSoonInvoice(clientNames: Map<Long, String>) =
        BriefInvoice(
            id = id,
            number = number,
            clientName = clientNames[clientId],
            // What is still owed, not what the document was raised for: a part-paid invoice that
            // reported its full total would overstate what Abu is waiting on.
            amount = outstanding,
            dueOn = dueOn,
            isOverdue = false,
        )

    private fun Task.toBriefTask(isOverdue: Boolean) =
        BriefTask(id = id, title = title, dueAt = dueAt, isOverdue = isOverdue)

    private fun CalendarEvent.toBriefEvent() =
        BriefEvent(title = title, startsAt = startsAt, endsAt = endsAt, location = location)

    private fun BriefEvent.hasFinishedBy(now: Instant): Boolean = !(endsAt ?: startsAt).isAfter(now)

    private companion object {
        /**
         * How many task rows the brief carries. The counts beside them are exact; these are the
         * few Mira can name in a sentence without turning the brief into the Tasks screen.
         */
        const val MAX_TASK_ROWS = 5

        /** Enough to say what is coming without reading out the week. */
        const val MAX_UPCOMING_REMINDERS = 3

        const val MAX_RECENT_PAYMENTS = 5
    }
}

/** The one person Mira is for. See AGENTS.md §1. */
private const val OWNER_NAME = "Abu"
