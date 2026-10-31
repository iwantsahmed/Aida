package com.rayluxeo.aida.domain.attention

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.MeetingRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Pure Kotlin implementation of the proactive attention engine.
 *
 * Implements local, private detection for:
 * 1. Overdue invoices
 * 2. Overdue tasks
 * 3. Deadlines within 48 hours
 * 4. Scheduled client follow-ups
 * 5. Subscriptions renewing soon
 * 6. Partially paid invoices
 * 7. High-priority unfinished tasks
 * 8. Projects with upcoming deadlines
 * 9. Projects with no recent activity
 * 10. Possible duplicate expenses
 *
 * Guaranteed zero monetary cost, offline-first, and never initiates mutating side-effects
 * (no sending messages, no sending invoices, no charges, no deletions).
 */
class DefaultProactiveAttentionEngine @Inject constructor(
    private val invoices: InvoiceRepository,
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val clients: ClientRepository,
    private val subscriptions: SubscriptionRepository,
    private val expenses: ExpenseRepository,
    private val meetings: MeetingRepository,
    private val dismissalRepository: AttentionDismissalRepository,
    private val clock: MiraClock,
) : ProactiveAttentionEngine {

    override suspend fun detectItems(): AppResult<List<ProactiveAttentionItem>> {
        val now = clock.now()
        val zone = clock.zone()
        val today = clock.today()

        val dismissed = dismissalRepository.getDismissedIds()

        val allInvoices = invoices.observeAll().first()
            .filter { it.deletedAt == null && it.status != InvoiceStatus.VOID && it.status != InvoiceStatus.DRAFT }
        val openTasks = tasks.observe(TaskFilter.OPEN).first()
            .filter { it.deletedAt == null && !it.isDone }
        val openProjects = projects.observe(ProjectFilter.OPEN).first()
            .filter { it.deletedAt == null && it.isOpen }
        val activeClients = clients.observe(ClientFilter.ACTIVE).first()
            .filter { it.deletedAt == null && !it.isArchived }
        val clientNames = activeClients.associate { it.id to it.name }
        val activeSubscriptions = subscriptions.observeActive().first()
            .filter { it.deletedAt == null && it.isActive }
        val recentExpenses = runCatching {
            expenses.observeBetween(now.minus(Duration.ofDays(7)), now).first()
        }.getOrDefault(emptyList()).filter { it.deletedAt == null }

        val meetingHistory = runCatching {
            val until = today.plusDays(15).atStartOfDay(zone).toInstant()
            meetings.observeBetween(Instant.EPOCH, until).first().filter { it.deletedAt == null }
        }.getOrDefault(emptyList())

        val detected = mutableListOf<ProactiveAttentionItem>()

        // 1. Overdue invoices
        detected += detectOverdueInvoices(allInvoices, clientNames, today, zone)

        // 2. Overdue tasks
        detected += detectOverdueTasks(openTasks, today, now, zone)

        // 3. Deadlines within 48 hours
        detected += detectDeadlinesWithin48Hours(openProjects, openTasks, today)

        // 4. Scheduled client follow-ups
        detected += detectScheduledClientFollowUps(activeClients, meetingHistory, today, now, zone)

        // 5. Subscriptions renewing soon
        detected += detectSubscriptionsRenewingSoon(activeSubscriptions, today, zone)

        // 6. Partially paid invoices (not already overdue)
        detected += detectPartiallyPaidInvoices(allInvoices, clientNames, today)

        // 7. High-priority unfinished tasks (not already overdue)
        detected += detectHighPriorityTasks(openTasks, now, zone)

        // 8. Projects with upcoming deadlines (3 to 14 days out)
        detected += detectProjectsUpcomingDeadlines(openProjects, today)

        // 9. Projects with no recent activity
        detected += detectProjectsNoRecentActivity(openProjects, openTasks, today, zone)

        // 10. Possible duplicate expenses
        detected += detectDuplicateExpenses(recentExpenses)

        val filtered = detected
            .filter { it.id !in dismissed }
            .sortedWith(
                compareBy<ProactiveAttentionItem> { it.severity }
                    .thenBy { it.category }
                    .thenBy { it.id },
            )

        return AppResult.Success(filtered)
    }

    override suspend fun dismiss(id: String) {
        dismissalRepository.dismiss(id)
    }

    private fun detectOverdueInvoices(
        invoices: List<Invoice>,
        clientNames: Map<Long, String>,
        today: LocalDate,
        zone: ZoneId,
    ): List<ProactiveAttentionItem> = invoices
        .filter { it.dueOn.isBefore(today) && it.outstanding.amount > BigDecimal.ZERO }
        .map { invoice ->
            val daysOverdue = ChronoUnit.DAYS.between(invoice.dueOn, today)
            val clientName = clientNames[invoice.clientId]
            ProactiveAttentionItem(
                id = "overdue-invoice-${invoice.id}",
                category = AttentionCategory.OVERDUE_INVOICE,
                severity = AttentionSeverity.CRITICAL,
                days = daysOverdue,
                relatedRecord = AttentionRecordRef(
                    type = AttentionRecordType.INVOICE,
                    id = invoice.id,
                    title = invoice.number,
                    subtitle = clientName,
                ),
                availableActions = listOf(
                    AttentionAction.OPEN,
                    AttentionAction.PREPARE_FOLLOW_UP,
                    AttentionAction.REMIND_ME,
                    AttentionAction.DISMISS,
                ),
                amount = invoice.outstanding,
                timestamp = invoice.dueOn.atStartOfDay(zone).toInstant(),
            )
        }

    private fun detectOverdueTasks(
        tasks: List<Task>,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
    ): List<ProactiveAttentionItem> = tasks
        .filter { it.dueAt != null && it.dueAt.isBefore(now) }
        .map { task ->
            val taskDueDate = task.dueAt!!.atZone(zone).toLocalDate()
            val daysOverdue = ChronoUnit.DAYS.between(taskDueDate, today)
            ProactiveAttentionItem(
                id = "overdue-task-${task.id}",
                category = AttentionCategory.OVERDUE_TASK,
                severity = AttentionSeverity.CRITICAL,
                days = daysOverdue,
                dueOn = taskDueDate,
                relatedRecord = AttentionRecordRef(
                    type = AttentionRecordType.TASK,
                    id = task.id,
                    title = task.title,
                ),
                availableActions = listOf(
                    AttentionAction.OPEN,
                    AttentionAction.MARK_DONE,
                    AttentionAction.REMIND_ME,
                    AttentionAction.DISMISS,
                ),
                timestamp = task.dueAt,
            )
        }

    private fun detectDeadlinesWithin48Hours(
        projects: List<Project>,
        openTasks: List<Task>,
        today: LocalDate,
    ): List<ProactiveAttentionItem> = projects
        .filter { it.dueOn != null }
        .mapNotNull { project ->
            val dueOn = project.dueOn ?: return@mapNotNull null
            val daysAway = ChronoUnit.DAYS.between(today, dueOn)
            if (daysAway !in 0..2) return@mapNotNull null

            val unfinishedCount = openTasks.count { it.projectId == project.id }
            ProactiveAttentionItem(
                id = "deadline-48h-project-${project.id}",
                category = AttentionCategory.DEADLINE_WITHIN_48_HOURS,
                severity = if (daysAway <= 1L) AttentionSeverity.CRITICAL else AttentionSeverity.WARNING,
                days = daysAway,
                count = unfinishedCount,
                relatedRecord = AttentionRecordRef(
                    type = AttentionRecordType.PROJECT,
                    id = project.id,
                    title = project.name,
                ),
                availableActions = listOf(
                    AttentionAction.OPEN,
                    AttentionAction.REMIND_ME,
                    AttentionAction.DISMISS,
                ),
            )
        }

    private fun detectScheduledClientFollowUps(
        clients: List<com.rayluxeo.aida.domain.model.Client>,
        meetings: List<Meeting>,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
    ): List<ProactiveAttentionItem> {
        val lastContact = mutableMapOf<Long, Instant>()
        val booked = mutableSetOf<Long>()
        for (meeting in meetings) {
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

        return clients
            .filter { it.id !in booked }
            .mapNotNull { client ->
                val contactedAt = lastContact[client.id] ?: client.createdAt
                val contactDate = contactedAt.atZone(zone).toLocalDate()
                val daysSince = ChronoUnit.DAYS.between(contactDate, today)
                if (daysSince < 21) return@mapNotNull null

                ProactiveAttentionItem(
                    id = "followup-client-${client.id}",
                    category = AttentionCategory.SCHEDULED_CLIENT_FOLLOW_UP,
                    severity = AttentionSeverity.NOTICE,
                    days = daysSince,
                    relatedRecord = AttentionRecordRef(
                        type = AttentionRecordType.CLIENT,
                        id = client.id,
                        title = client.name,
                    ),
                    availableActions = listOf(
                        AttentionAction.OPEN,
                        AttentionAction.PREPARE_FOLLOW_UP,
                        AttentionAction.REMIND_ME,
                        AttentionAction.DISMISS,
                    ),
                    timestamp = contactedAt,
                )
            }
    }

    private fun detectSubscriptionsRenewingSoon(
        subscriptions: List<Subscription>,
        today: LocalDate,
        zone: ZoneId,
    ): List<ProactiveAttentionItem> = subscriptions.mapNotNull { sub ->
        val chargeDate = sub.nextChargeOnOrAfter(today)
        val daysAway = ChronoUnit.DAYS.between(today, chargeDate)
        if (daysAway !in 0..7) return@mapNotNull null

        ProactiveAttentionItem(
            id = "renewing-sub-${sub.id}",
            category = AttentionCategory.SUBSCRIPTION_RENEWING_SOON,
            severity = if (daysAway <= 2) AttentionSeverity.WARNING else AttentionSeverity.NOTICE,
            days = daysAway,
            relatedRecord = AttentionRecordRef(
                type = AttentionRecordType.SUBSCRIPTION,
                id = sub.id,
                title = sub.name,
            ),
            availableActions = listOf(
                AttentionAction.OPEN,
                AttentionAction.REMIND_ME,
                AttentionAction.DISMISS,
            ),
            amount = sub.amount,
            timestamp = chargeDate.atStartOfDay(zone).toInstant(),
        )
    }

    private fun detectPartiallyPaidInvoices(
        invoices: List<Invoice>,
        clientNames: Map<Long, String>,
        today: LocalDate,
    ): List<ProactiveAttentionItem> = invoices
        .filter { !it.dueOn.isBefore(today) && it.amountPaid.amount > BigDecimal.ZERO && it.outstanding.amount > BigDecimal.ZERO }
        .map { invoice ->
            val clientName = clientNames[invoice.clientId]
            ProactiveAttentionItem(
                id = "partial-invoice-${invoice.id}",
                category = AttentionCategory.PARTIALLY_PAID_INVOICE,
                severity = AttentionSeverity.WARNING,
                secondaryAmount = invoice.amountPaid,
                relatedRecord = AttentionRecordRef(
                    type = AttentionRecordType.INVOICE,
                    id = invoice.id,
                    title = invoice.number,
                    subtitle = clientName,
                ),
                availableActions = listOf(
                    AttentionAction.OPEN,
                    AttentionAction.PREPARE_FOLLOW_UP,
                    AttentionAction.REMIND_ME,
                    AttentionAction.DISMISS,
                ),
                amount = invoice.outstanding,
            )
        }

    private fun detectHighPriorityTasks(
        tasks: List<Task>,
        now: Instant,
        zone: ZoneId,
    ): List<ProactiveAttentionItem> = tasks
        .filter { it.priority == TaskPriority.HIGH && (it.dueAt == null || !it.dueAt.isBefore(now)) }
        .map { task ->
            ProactiveAttentionItem(
                id = "high-priority-task-${task.id}",
                category = AttentionCategory.HIGH_PRIORITY_UNFINISHED_TASK,
                severity = AttentionSeverity.WARNING,
                dueOn = task.dueAt?.atZone(zone)?.toLocalDate(),
                relatedRecord = AttentionRecordRef(
                    type = AttentionRecordType.TASK,
                    id = task.id,
                    title = task.title,
                ),
                availableActions = listOf(
                    AttentionAction.OPEN,
                    AttentionAction.MARK_DONE,
                    AttentionAction.REMIND_ME,
                    AttentionAction.DISMISS,
                ),
                timestamp = task.dueAt,
            )
        }

    private fun detectProjectsUpcomingDeadlines(
        projects: List<Project>,
        today: LocalDate,
    ): List<ProactiveAttentionItem> = projects
        .filter { it.dueOn != null }
        .mapNotNull { project ->
            val dueOn = project.dueOn ?: return@mapNotNull null
            val daysAway = ChronoUnit.DAYS.between(today, dueOn)
            if (daysAway !in 3..14) return@mapNotNull null

            ProactiveAttentionItem(
                id = "upcoming-deadline-project-${project.id}",
                category = AttentionCategory.PROJECT_UPCOMING_DEADLINE,
                severity = AttentionSeverity.NOTICE,
                days = daysAway,
                dueOn = dueOn,
                relatedRecord = AttentionRecordRef(
                    type = AttentionRecordType.PROJECT,
                    id = project.id,
                    title = project.name,
                ),
                availableActions = listOf(
                    AttentionAction.OPEN,
                    AttentionAction.REMIND_ME,
                    AttentionAction.DISMISS,
                ),
            )
        }

    private fun detectProjectsNoRecentActivity(
        projects: List<Project>,
        openTasks: List<Task>,
        today: LocalDate,
        zone: ZoneId,
    ): List<ProactiveAttentionItem> = projects.mapNotNull { project ->
        val updatedDate = project.updatedAt.atZone(zone).toLocalDate()
        val daysSinceUpdate = ChronoUnit.DAYS.between(updatedDate, today)
        val hasOpenTasks = openTasks.any { it.projectId == project.id }
        if (daysSinceUpdate < 14 || !hasOpenTasks) return@mapNotNull null

        ProactiveAttentionItem(
            id = "stalled-project-${project.id}",
            category = AttentionCategory.PROJECT_NO_RECENT_ACTIVITY,
            severity = AttentionSeverity.NOTICE,
            days = daysSinceUpdate,
            relatedRecord = AttentionRecordRef(
                type = AttentionRecordType.PROJECT,
                id = project.id,
                title = project.name,
            ),
            availableActions = listOf(
                AttentionAction.OPEN,
                AttentionAction.DISMISS,
            ),
            timestamp = project.updatedAt,
        )
    }

    private fun detectDuplicateExpenses(
        expenses: List<Expense>,
    ): List<ProactiveAttentionItem> {
        val duplicates = mutableListOf<ProactiveAttentionItem>()
        val seenPairs = mutableSetOf<Pair<Long, Long>>()

        for (i in expenses.indices) {
            for (j in i + 1 until expenses.size) {
                val e1 = expenses[i]
                val e2 = expenses[j]
                val pairKey = minOf(e1.id, e2.id) to maxOf(e1.id, e2.id)
                if (pairKey in seenPairs) continue

                val sameAmount = e1.amount == e2.amount
                val sameDesc = e1.description.trim().equals(e2.description.trim(), ignoreCase = true)
                val sameCategory = e1.category == e2.category
                val hoursApart = Duration.between(e1.spentAt, e2.spentAt).abs().toHours()

                if (sameAmount && (sameDesc || sameCategory) && hoursApart <= 48) {
                    seenPairs += pairKey
                    val desc = e1.description.trim().ifEmpty { e1.category.name }
                    duplicates += ProactiveAttentionItem(
                        id = "duplicate-expense-${e1.id}-${e2.id}",
                        category = AttentionCategory.POSSIBLE_DUPLICATE_EXPENSE,
                        severity = AttentionSeverity.WARNING,
                        hours = hoursApart,
                        relatedRecord = AttentionRecordRef(
                            type = AttentionRecordType.EXPENSE,
                            id = e2.id,
                            title = desc,
                        ),
                        availableActions = listOf(
                            AttentionAction.OPEN,
                            AttentionAction.DISMISS,
                        ),
                        amount = e1.amount,
                        timestamp = e2.spentAt,
                    )
                }
            }
        }
        return duplicates
    }

}
