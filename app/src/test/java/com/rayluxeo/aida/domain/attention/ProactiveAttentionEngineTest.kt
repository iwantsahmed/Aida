package com.rayluxeo.aida.domain.attention

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.model.MeetingNote
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectMilestone
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.MeetingRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProactiveAttentionEngineTest {

    private val zone = ZoneId.of("Asia/Dhaka")
    private val today = LocalDate.of(2026, 3, 10)
    private val clock = object : MiraClock {
        override fun now(): Instant = today.atTime(10, 0).atZone(zone).toInstant()
        override fun today(): LocalDate = today
        override fun zone(): ZoneId = zone
    }

    private val invoices = FakeInvoiceRepo()
    private val tasks = FakeTaskRepo()
    private val projects = FakeProjectRepo()
    private val clients = FakeClientRepo()
    private val subscriptions = FakeSubscriptionRepo()
    private val expenses = FakeExpenseRepo()
    private val meetings = FakeMeetingRepo()
    private val dismissals = FakeDismissalRepo()

    private val engine = DefaultProactiveAttentionEngine(
        invoices = invoices,
        tasks = tasks,
        projects = projects,
        clients = clients,
        subscriptions = subscriptions,
        expenses = expenses,
        meetings = meetings,
        dismissalRepository = dismissals,
        clock = clock,
    )

    @Test
    fun `detects overdue invoices`() = runTest {
        clients.clients = listOf(client(id = 1L, name = "Ahmed"))
        invoices.all = listOf(
            invoice(
                id = 10L,
                number = "INV-2026-001",
                clientId = 1L,
                dueOn = today.minusDays(4),
                total = Money.of("45000"),
                amountPaid = Money.zero(),
            ),
        )

        val result = engine.detectItems()
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val items = (result as AppResult.Success).value

        val overdue = items.firstOrNull { it.category == AttentionCategory.OVERDUE_INVOICE }
        assertThat(overdue).isNotNull()
        assertThat(overdue!!.days).isEqualTo(4)
        assertThat(overdue.amount).isEqualTo(Money.of("45000"))
        assertThat(overdue.relatedRecord.subtitle).isEqualTo("Ahmed")
        assertThat(overdue.relatedRecord.title).isEqualTo("INV-2026-001")
        assertThat(overdue.availableActions).containsExactly(
            AttentionAction.OPEN,
            AttentionAction.PREPARE_FOLLOW_UP,
            AttentionAction.REMIND_ME,
            AttentionAction.DISMISS,
        )
    }

    @Test
    fun `detects overdue tasks`() = runTest {
        tasks.open = listOf(
            task(
                id = 20L,
                title = "Submit tax report",
                dueAt = today.minusDays(1).atTime(15, 0).atZone(zone).toInstant(),
            ),
        )

        val result = (engine.detectItems() as AppResult.Success).value
        val overdue = result.firstOrNull { it.category == AttentionCategory.OVERDUE_TASK }
        assertThat(overdue).isNotNull()
        assertThat(overdue!!.days).isEqualTo(1)
        assertThat(overdue.relatedRecord.title).isEqualTo("Submit tax report")
        assertThat(overdue.availableActions).contains(AttentionAction.MARK_DONE)
    }

    @Test
    fun `detects deadlines within 48 hours with open task counts`() = runTest {
        projects.open = listOf(
            project(
                id = 30L,
                name = "Rayluxeo homepage",
                dueOn = today.plusDays(1),
            ),
        )
        tasks.open = listOf(
            task(id = 31L, title = "Design hero", projectId = 30L),
            task(id = 32L, title = "Wire auth", projectId = 30L),
            task(id = 33L, title = "QA tests", projectId = 30L),
        )

        val result = (engine.detectItems() as AppResult.Success).value
        val deadline = result.firstOrNull { it.category == AttentionCategory.DEADLINE_WITHIN_48_HOURS }
        assertThat(deadline).isNotNull()
        assertThat(deadline!!.days).isEqualTo(1)
        assertThat(deadline.count).isEqualTo(3)
        assertThat(deadline.relatedRecord.title).isEqualTo("Rayluxeo homepage")
        assertThat(deadline.availableActions).contains(AttentionAction.REMIND_ME)
    }

    @Test
    fun `detects scheduled client follow-ups after 21 days of silence`() = runTest {
        clients.clients = listOf(
            client(
                id = 40L,
                name = "Ahmed",
                createdAt = today.minusDays(50).atStartOfDay(zone).toInstant(),
            ),
        )
        meetings.between = listOf(
            meeting(
                clientId = 40L,
                startsAt = today.minusDays(25).atTime(11, 0).atZone(zone).toInstant(),
            ),
        )

        val result = (engine.detectItems() as AppResult.Success).value
        val followup = result.firstOrNull { it.category == AttentionCategory.SCHEDULED_CLIENT_FOLLOW_UP }
        assertThat(followup).isNotNull()
        assertThat(followup!!.days).isEqualTo(25)
        assertThat(followup.relatedRecord.title).isEqualTo("Ahmed")
        assertThat(followup.availableActions).contains(AttentionAction.PREPARE_FOLLOW_UP)
    }

    @Test
    fun `detects subscriptions renewing soon within 7 days`() = runTest {
        subscriptions.active = listOf(
            subscription(
                id = 50L,
                name = "Adobe",
                amount = Money.of("3500"),
                nextChargeOn = today.plusDays(2),
            ),
        )

        val result = (engine.detectItems() as AppResult.Success).value
        val sub = result.firstOrNull { it.category == AttentionCategory.SUBSCRIPTION_RENEWING_SOON }
        assertThat(sub).isNotNull()
        assertThat(sub!!.days).isEqualTo(2)
        assertThat(sub.amount).isEqualTo(Money.of("3500"))
        assertThat(sub.relatedRecord.title).isEqualTo("Adobe")
    }

    @Test
    fun `detects partially paid invoices`() = runTest {
        clients.clients = listOf(client(id = 1L, name = "Ahmed"))
        invoices.all = listOf(
            invoice(
                id = 60L,
                number = "INV-2026-004",
                clientId = 1L,
                dueOn = today.plusDays(5),
                total = Money.of("25000"),
                amountPaid = Money.of("15000"),
            ),
        )

        val result = (engine.detectItems() as AppResult.Success).value
        val partial = result.firstOrNull { it.category == AttentionCategory.PARTIALLY_PAID_INVOICE }
        assertThat(partial).isNotNull()
        assertThat(partial!!.secondaryAmount).isEqualTo(Money.of("15000"))
        assertThat(partial.amount).isEqualTo(Money.of("10000"))
        assertThat(partial.relatedRecord.subtitle).isEqualTo("Ahmed")
    }

    @Test
    fun `detects high priority unfinished tasks`() = runTest {
        tasks.open = listOf(
            task(
                id = 70L,
                title = "Security audit",
                priority = TaskPriority.HIGH,
                dueAt = today.plusDays(3).atTime(10, 0).atZone(zone).toInstant(),
            ),
        )

        val result = (engine.detectItems() as AppResult.Success).value
        val highPri = result.firstOrNull { it.category == AttentionCategory.HIGH_PRIORITY_UNFINISHED_TASK }
        assertThat(highPri).isNotNull()
        assertThat(highPri!!.relatedRecord.title).isEqualTo("Security audit")
        assertThat(highPri.availableActions).contains(AttentionAction.MARK_DONE)
    }

    @Test
    fun `detects projects with upcoming deadlines between 3 and 14 days`() = runTest {
        projects.open = listOf(
            project(
                id = 80L,
                name = "Mobile App Launch",
                dueOn = today.plusDays(7),
            ),
        )

        val result = (engine.detectItems() as AppResult.Success).value
        val upcoming = result.firstOrNull { it.category == AttentionCategory.PROJECT_UPCOMING_DEADLINE }
        assertThat(upcoming).isNotNull()
        assertThat(upcoming!!.days).isEqualTo(7)
        assertThat(upcoming.relatedRecord.title).isEqualTo("Mobile App Launch")
    }

    @Test
    fun `detects projects with no recent activity for 14 days`() = runTest {
        projects.open = listOf(
            project(
                id = 90L,
                name = "Stalled Project",
                updatedAt = today.minusDays(16).atStartOfDay(zone).toInstant(),
            ),
        )
        tasks.open = listOf(
            task(id = 91L, title = "Pending task", projectId = 90L),
        )

        val result = (engine.detectItems() as AppResult.Success).value
        val stalled = result.firstOrNull { it.category == AttentionCategory.PROJECT_NO_RECENT_ACTIVITY }
        assertThat(stalled).isNotNull()
        assertThat(stalled!!.days).isEqualTo(16)
        assertThat(stalled.relatedRecord.title).isEqualTo("Stalled Project")
    }

    @Test
    fun `detects possible duplicate expenses within 48 hours`() = runTest {
        val expenseTime = today.minusDays(2).atTime(12, 0).atZone(zone).toInstant()
        expenses.between = listOf(
            expense(
                id = 101L,
                description = "Office Supplies",
                amount = Money.of("1200"),
                spentAt = expenseTime,
            ),
            expense(
                id = 102L,
                description = "Office Supplies",
                amount = Money.of("1200"),
                spentAt = expenseTime.plusSeconds(3600),
            ),
        )

        val result = (engine.detectItems() as AppResult.Success).value
        val dup = result.firstOrNull { it.category == AttentionCategory.POSSIBLE_DUPLICATE_EXPENSE }
        assertThat(dup).isNotNull()
        assertThat(dup!!.amount).isEqualTo(Money.of("1200"))
        assertThat(dup.hours).isEqualTo(1)
        assertThat(dup.relatedRecord.title).isEqualTo("Office Supplies")
    }

    @Test
    fun `dismissed items are excluded from detection`() = runTest {
        subscriptions.active = listOf(
            subscription(
                id = 50L,
                name = "Adobe",
                amount = Money.of("3500"),
                nextChargeOn = today.plusDays(2),
            ),
        )
        dismissals.dismiss("renewing-sub-50")

        val result = (engine.detectItems() as AppResult.Success).value
        assertThat(result.any { it.id == "renewing-sub-50" }).isFalse()
    }

    // Helpers
    private fun invoice(
        id: Long,
        number: String,
        clientId: Long,
        dueOn: LocalDate,
        total: Money,
        amountPaid: Money = Money.zero(),
    ) = Invoice(
        id = id,
        number = number,
        clientId = clientId,
        status = InvoiceStatus.SENT,
        issuedOn = today.minusDays(10),
        dueOn = dueOn,
        subtotal = total,
        discount = Money.zero(),
        tax = Money.zero(),
        total = total,
        amountPaid = amountPaid,
        createdAt = today.minusDays(10).atStartOfDay(zone).toInstant(),
        updatedAt = today.minusDays(10).atStartOfDay(zone).toInstant(),
    )

    private fun task(
        id: Long,
        title: String,
        dueAt: Instant? = null,
        projectId: Long? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
    ) = Task(
        id = id,
        title = title,
        dueAt = dueAt,
        projectId = projectId,
        priority = priority,
        createdAt = today.minusDays(5).atStartOfDay(zone).toInstant(),
        updatedAt = today.minusDays(5).atStartOfDay(zone).toInstant(),
    )

    private fun project(
        id: Long,
        name: String,
        dueOn: LocalDate? = null,
        updatedAt: Instant = today.atStartOfDay(zone).toInstant(),
    ) = Project(
        id = id,
        name = name,
        dueOn = dueOn,
        createdAt = today.minusDays(30).atStartOfDay(zone).toInstant(),
        updatedAt = updatedAt,
    )

    private fun client(id: Long, name: String, createdAt: Instant = today.minusDays(30).atStartOfDay(zone).toInstant()) = Client(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun subscription(id: Long, name: String, amount: Money, nextChargeOn: LocalDate) = Subscription(
        id = id,
        name = name,
        amount = amount,
        cycle = SubscriptionCycle.MONTHLY,
        nextChargeOn = nextChargeOn,
        createdAt = today.minusDays(60).atStartOfDay(zone).toInstant(),
        updatedAt = today.minusDays(60).atStartOfDay(zone).toInstant(),
    )

    private fun expense(id: Long, description: String, amount: Money, spentAt: Instant) = Expense(
        id = id,
        description = description,
        amount = amount,
        spentAt = spentAt,
        createdAt = spentAt,
        updatedAt = spentAt,
    )

    private fun meeting(clientId: Long, startsAt: Instant) = Meeting(
        clientId = clientId,
        title = "Catch up",
        startsAt = startsAt,
        endsAt = startsAt.plusSeconds(1800),
        createdAt = startsAt,
        updatedAt = startsAt,
    )
}

// Fakes
private class FakeInvoiceRepo : InvoiceRepository {
    var all: List<Invoice> = emptyList()
    override fun observeAll(): Flow<List<Invoice>> = flow { emit(all) }
    override fun observeForClient(clientId: Long): Flow<List<Invoice>> = flow { emit(all.filter { it.clientId == clientId }) }
    override fun observeUnpaid(): Flow<List<Invoice>> = flow { emit(all.filter { it.outstanding.amount > java.math.BigDecimal.ZERO }) }
    override fun observeOverdue(): Flow<List<Invoice>> = flow { emit(emptyList()) }
    override suspend fun byId(id: Long): AppResult<Invoice> = AppResult.Success(all.first { it.id == id })
    override suspend fun byNumber(number: String): AppResult<Invoice> = AppResult.Success(all.first { it.number == number })
    override suspend fun search(query: String?): AppResult<List<Invoice>> = AppResult.Success(all)
    override suspend fun create(invoice: Invoice): AppResult<Invoice> = AppResult.Success(invoice)
    override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> = AppResult.Success(invoice)
    override suspend fun update(invoice: Invoice): AppResult<Invoice> = AppResult.Success(invoice)
    override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> = AppResult.Success(all.first { it.id == id })
    override suspend fun softDelete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Invoice> = AppResult.Success(all.first { it.id == id })
    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeTaskRepo : TaskRepository {
    var open: List<Task> = emptyList()
    override fun observe(filter: TaskFilter): Flow<List<Task>> = flow { emit(open) }
    override fun observeCounts(): Flow<TaskCounts> = flow { emit(TaskCounts(open = open.size, dueToday = 0, overdue = 0, completedToday = 0)) }
    override suspend fun byId(id: Long): AppResult<Task> = AppResult.Success(open.first { it.id == id })
    override suspend fun search(query: String?, filter: TaskFilter): AppResult<List<Task>> = AppResult.Success(open)
    override suspend fun create(task: Task): AppResult<Task> = AppResult.Success(task)
    override suspend fun update(task: Task): AppResult<Task> = AppResult.Success(task)
    override suspend fun setDone(id: Long, done: Boolean): AppResult<Task> = AppResult.Success(open.first { it.id == id })
    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Task> = AppResult.Success(open.first { it.id == id })
    override fun observe(query: com.rayluxeo.aida.domain.model.TaskQuery): Flow<List<Task>> = flow { emit(open) }
    override suspend fun list(query: com.rayluxeo.aida.domain.model.TaskQuery): AppResult<List<Task>> = AppResult.Success(open)
    override suspend fun count(query: com.rayluxeo.aida.domain.model.TaskQuery): AppResult<Int> = AppResult.Success(open.size)
    override suspend fun groupedByProject(query: com.rayluxeo.aida.domain.model.TaskQuery): AppResult<List<com.rayluxeo.aida.domain.repository.TaskGroup>> = AppResult.Success(emptyList())
    override suspend fun groupedByClient(query: com.rayluxeo.aida.domain.model.TaskQuery): AppResult<List<com.rayluxeo.aida.domain.repository.TaskGroup>> = AppResult.Success(emptyList())
    override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean): AppResult<Int> = AppResult.Success(ids.size)
    override suspend fun snooze(id: Long, until: Instant): AppResult<Task> = AppResult.Success(open.first { it.id == id })
    override suspend fun archive(id: Long): AppResult<Task> = AppResult.Success(open.first { it.id == id })
    override suspend fun unarchive(id: Long): AppResult<Task> = AppResult.Success(open.first { it.id == id })
    override suspend fun archiveAll(ids: Collection<Long>): AppResult<Int> = AppResult.Success(ids.size)
    override suspend fun deleteAll(ids: Collection<Long>): AppResult<Int> = AppResult.Success(ids.size)
    override suspend fun completeWithNextOccurrence(id: Long, nextDueAt: Instant): AppResult<com.rayluxeo.aida.domain.repository.TaskOccurrence> =
        AppResult.Success(com.rayluxeo.aida.domain.repository.TaskOccurrence(completed = open.first { it.id == id }, next = open.first { it.id == id }))
    override suspend fun setTags(taskId: Long, tagIds: Collection<Long>): AppResult<Task> = AppResult.Success(open.first { it.id == taskId })
    override suspend fun addTag(taskId: Long, tagId: Long): AppResult<Task> = AppResult.Success(open.first { it.id == taskId })
    override suspend fun removeTag(taskId: Long, tagId: Long): AppResult<Task> = AppResult.Success(open.first { it.id == taskId })
    override suspend fun attachFile(taskId: Long, fileId: Long): AppResult<Task> = AppResult.Success(open.first { it.id == taskId })
    override suspend fun detachFile(taskId: Long, fileId: Long): AppResult<Task> = AppResult.Success(open.first { it.id == taskId })
}

private class FakeProjectRepo : ProjectRepository {
    var open: List<Project> = emptyList()
    override fun observe(filter: ProjectFilter): Flow<List<Project>> = flow { emit(open) }
    override fun observeForClient(clientId: Long): Flow<List<Project>> = flow { emit(open.filter { it.clientId == clientId }) }
    override fun observeMilestones(projectId: Long): Flow<List<ProjectMilestone>> = flow { emit(emptyList()) }
    override suspend fun byId(id: Long): AppResult<Project> = AppResult.Success(open.first { it.id == id })
    override suspend fun search(query: String?, filter: ProjectFilter): AppResult<List<Project>> = AppResult.Success(open)
    override suspend fun create(project: Project, milestones: List<ProjectMilestone>): AppResult<Project> = AppResult.Success(project)
    override suspend fun update(project: Project): AppResult<Project> = AppResult.Success(project)
    override suspend fun setStatus(id: Long, status: ProjectStatus): AppResult<Project> = AppResult.Success(open.first { it.id == id })
    override suspend fun softDelete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Project> = AppResult.Success(open.first { it.id == id })
    override suspend fun purge(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun addMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> = AppResult.Success(milestone)
    override suspend fun updateMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> = AppResult.Success(milestone)
    override suspend fun setMilestoneComplete(id: Long, complete: Boolean): AppResult<ProjectMilestone> =
        AppResult.Success(ProjectMilestone(id = id, projectId = 0L, title = "", position = 0, isComplete = complete, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
    override suspend fun deleteMilestone(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun reorderMilestones(projectId: Long, orderedIds: List<Long>): AppResult<List<ProjectMilestone>> =
        AppResult.Success(emptyList())
}

private class FakeClientRepo : ClientRepository {
    var clients: List<Client> = emptyList()
    override fun observe(filter: ClientFilter): Flow<List<Client>> = flow { emit(clients) }
    override suspend fun byId(id: Long): AppResult<Client> = AppResult.Success(clients.first { it.id == id })
    override suspend fun search(query: String?, filter: ClientFilter): AppResult<List<Client>> = AppResult.Success(clients)
    override suspend fun create(client: Client): AppResult<Client> = AppResult.Success(client)
    override suspend fun update(client: Client): AppResult<Client> = AppResult.Success(client)
    override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client> = AppResult.Success(clients.first { it.id == id })
    override suspend fun softDelete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Client> = AppResult.Success(clients.first { it.id == id })
    override suspend fun purge(id: Long): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeSubscriptionRepo : SubscriptionRepository {
    var active: List<Subscription> = emptyList()
    override fun observeActive(): Flow<List<Subscription>> = flow { emit(active) }
    override fun observeAll(): Flow<List<Subscription>> = flow { emit(active) }
    override suspend fun byId(id: Long): AppResult<Subscription> = AppResult.Success(active.first { it.id == id })
    override suspend fun dueOnOrBefore(date: LocalDate): AppResult<List<Subscription>> = AppResult.Success(active)
    override suspend fun create(subscription: Subscription): AppResult<Subscription> = AppResult.Success(subscription)
    override suspend fun update(subscription: Subscription): AppResult<Subscription> = AppResult.Success(subscription)
    override suspend fun setActive(id: Long, active: Boolean): AppResult<Subscription> = AppResult.Success(this.active.first { it.id == id })
    override suspend fun advanceOverdueCharges(): AppResult<Int> = AppResult.Success(0)
    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Subscription> = AppResult.Success(this.active.first { it.id == id })
}

private class FakeExpenseRepo : ExpenseRepository {
    var between: List<Expense> = emptyList()
    override fun observeAll(): Flow<List<Expense>> = flow { emit(between) }
    override fun observeByCategory(category: ExpenseCategory): Flow<List<Expense>> = flow { emit(between) }
    override fun observeBetween(from: Instant, to: Instant): Flow<List<Expense>> = flow { emit(between) }
    override fun observeForProject(projectId: Long): Flow<List<Expense>> = flow { emit(between) }
    override suspend fun byId(id: Long): AppResult<Expense> = AppResult.Success(between.first { it.id == id })
    override suspend fun listBetween(from: Instant, to: Instant): AppResult<List<Expense>> = AppResult.Success(between)
    override suspend fun create(expense: Expense): AppResult<Expense> = AppResult.Success(expense)
    override suspend fun update(expense: Expense): AppResult<Expense> = AppResult.Success(expense)
    override suspend fun totalSpentBetween(from: Instant, to: Instant, currency: Currency, category: ExpenseCategory?): AppResult<Money> = AppResult.Success(Money.zero())
    override suspend fun delete(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Expense> = AppResult.Success(between.first { it.id == id })
}

private class FakeMeetingRepo : MeetingRepository {
    var between: List<Meeting> = emptyList()
    override fun observeUpcoming(): Flow<List<Meeting>> = flow { emit(between) }
    override fun observeBetween(from: Instant, to: Instant): Flow<List<Meeting>> = flow { emit(between) }
    override fun observeForClient(clientId: Long): Flow<List<Meeting>> = flow { emit(between) }
    override fun observeForProject(projectId: Long): Flow<List<Meeting>> = flow { emit(between) }
    override fun observeNotes(meetingId: Long): Flow<List<MeetingNote>> = flow { emit(emptyList()) }
    override fun observeOpenActionItems(): Flow<List<MeetingNote>> = flow { emit(emptyList()) }
    override suspend fun byId(id: Long): AppResult<Meeting> = AppResult.Success(between.first { it.id == id })
    override suspend fun notesFor(meetingId: Long): AppResult<List<MeetingNote>> = AppResult.Success(emptyList())
    override suspend fun create(meeting: Meeting, notes: List<MeetingNote>): AppResult<Meeting> = AppResult.Success(meeting)
    override suspend fun update(meeting: Meeting): AppResult<Meeting> = AppResult.Success(meeting)
    override suspend fun addNote(note: MeetingNote): AppResult<MeetingNote> = AppResult.Success(note)
    override suspend fun updateNote(note: MeetingNote): AppResult<MeetingNote> = AppResult.Success(note)
    override suspend fun replaceNotes(meetingId: Long, notes: List<MeetingNote>): AppResult<List<MeetingNote>> = AppResult.Success(notes)
    override suspend fun linkNoteToTask(noteId: Long, taskId: Long?): AppResult<MeetingNote> =
        AppResult.Success(MeetingNote(id = noteId, meetingId = 0L, body = "", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
    override suspend fun removeNote(noteId: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun moveToBin(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun restore(id: Long): AppResult<Meeting> = AppResult.Success(between.first { it.id == id })
    override suspend fun purge(id: Long): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeDismissalRepo : AttentionDismissalRepository {
    private val dismissed = MutableStateFlow<Set<String>>(emptySet())
    override fun observeDismissedIds(): Flow<Set<String>> = dismissed
    override suspend fun getDismissedIds(): Set<String> = dismissed.value
    override suspend fun dismiss(id: String) {
        dismissed.update { it + id }
    }
    override suspend fun clearDismissed() {
        dismissed.value = emptySet()
    }
}
