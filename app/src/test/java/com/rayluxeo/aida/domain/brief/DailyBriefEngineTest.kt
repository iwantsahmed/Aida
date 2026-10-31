package com.rayluxeo.aida.domain.brief

import com.rayluxeo.aida.domain.repository.TaskOccurrence
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.model.TaskQuery
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.CalendarEvent
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.model.MeetingNote
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectMilestone
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.model.WritableCalendar
import com.rayluxeo.aida.domain.repository.BriefRepository
import com.rayluxeo.aida.domain.repository.CalendarRepository
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.MeetingRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The brief is the one place Mira states facts about Abu's whole day at once, so the failures
 * worth catching here are the ones he could not notice: a day reported as clear when the calendar
 * was never readable, a total that quietly added taka to dollars, a storage failure flattened into
 * an empty list.
 *
 * Nothing is injected — Hilt's local-test transform does not run under AGP 9 (google/dagger#5261)
 * — and the clock never moves, so every window lands on the same instant on every run.
 */
class DailyBriefEngineTest {

    private val clock = FixedMiraClock()
    private val today: LocalDate = LocalDate.of(2026, 3, 10)

    private val briefs = FakeBriefRepository(AppResult.Success(brief()))
    private val tasks = FakeTaskRepository()
    private val reminders = FakeReminderRepository()
    private val calendar = FakeCalendarRepository()
    private val invoices = FakeInvoiceRepository()
    private val payments = FakePaymentRepository()
    private val projects = FakeProjectRepository()
    private val subscriptions = FakeSubscriptionRepository()
    private val clients = FakeClientRepository()
    private val meetings = FakeMeetingRepository()
    private val profiles = FakeUserProfileRepository()

    private val engine = DefaultDailyBriefEngine(
        briefs = briefs,
        tasks = tasks,
        reminders = reminders,
        calendar = calendar,
        invoices = invoices,
        payments = payments,
        projects = projects,
        subscriptions = subscriptions,
        clients = clients,
        meetings = meetings,
        profiles = profiles,
        clock = clock,
    )

    // ------------------------------------------------------------------ the calendar rule

    @Test
    fun `a calendar Mira may not read is never reported as a clear day`() = runTest {
        calendar.readable = false

        val report = succeeded(engine.today())

        assertThat(report.calendarReadable).isFalse()
        assertThat(report.nextEvent).isNull()
        assertThat(report.todaysEvents).isEmpty()
        assertThat(calendar.reads).isEqualTo(0)
    }

    @Test
    fun `a calendar Mira read and found empty is a clear day`() = runTest {
        calendar.readable = true
        calendar.events = AppResult.Success(emptyList())

        val report = succeeded(engine.today())

        assertThat(report.calendarReadable).isTrue()
        assertThat(report.nextEvent).isNull()
    }

    @Test
    fun `a calendar that refused the read is not a clear day either`() = runTest {
        calendar.readable = true
        calendar.events = failure(MiraErrorCode.PERMISSION_REQUIRED, "revoked mid session")

        val report = succeeded(engine.today())

        assertThat(report.calendarReadable).isFalse()
        assertThat(report.nextEvent).isNull()
    }

    @Test
    fun `the next event is the first one that has not already finished`() = runTest {
        calendar.readable = true
        calendar.events = AppResult.Success(
            listOf(
                event("Standup", at(15), at(16)),
                event("Ahmed call", at(9, 30), at(11)),
                event("Breakfast", at(8), at(9)),
            )
        )

        val report = succeeded(engine.today())

        assertThat(report.todaysEvents.map { it.title })
            .containsExactly("Breakfast", "Ahmed call", "Standup").inOrder()
        assertThat(report.nextEvent?.title).isEqualTo("Ahmed call")
    }

    @Test
    fun `the calendar is read across Abu's own day rather than UTC's`() = runTest {
        calendar.readable = true
        calendar.events = AppResult.Success(emptyList())

        succeeded(engine.today())

        assertThat(calendar.from).isEqualTo(at(0))
        assertThat(calendar.to).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 0))
    }

    // ------------------------------------------------------------------ counts and failure

    @Test
    fun `the counts come from the repository rather than from a second count in Kotlin`() = runTest {
        briefs.result = AppResult.Success(
            brief(open = 11, dueToday = 4, overdue = 3, completedToday = 2)
        )
        tasks.byFilter[TaskFilter.DUE_TODAY] = listOf(task(1, "Only one row"))

        val report = succeeded(engine.today())

        assertThat(report.openTaskCount).isEqualTo(11)
        assertThat(report.dueTodayCount).isEqualTo(4)
        assertThat(report.overdueCount).isEqualTo(3)
        assertThat(report.completedTodayCount).isEqualTo(2)
        assertThat(report.dueToday).hasSize(1)
    }

    @Test
    fun `a brief that could not be read is a failure rather than an empty day`() = runTest {
        briefs.result = failure(MiraErrorCode.STORAGE, "database locked")

        assertThat(failed(engine.today())).isEqualTo(MiraErrorCode.STORAGE)
    }

    @Test
    fun `a subscription table that could not be read fails rather than going quiet`() = runTest {
        subscriptions.result = failure(MiraErrorCode.STORAGE, "database locked")

        assertThat(failed(engine.today())).isEqualTo(MiraErrorCode.STORAGE)
        assertThat(failed(engine.attention())).isEqualTo(MiraErrorCode.STORAGE)
    }

    @Test
    fun `the task lists are capped while the counts behind them stay exact`() = runTest {
        briefs.result = AppResult.Success(brief(overdue = 9))
        tasks.byFilter[TaskFilter.OVERDUE] = (1..9).map { task(it.toLong(), "Task $it", daysAgo(it.toLong())) }

        val report = succeeded(engine.today())

        assertThat(report.overdueCount).isEqualTo(9)
        assertThat(report.overdue).hasSize(5)
        assertThat(report.overdue.all { it.isOverdue }).isTrue()
    }

    // ------------------------------------------------------------------ money

    @Test
    fun `outstanding is the sum of what is still owed and overdue is the part already late`() = runTest {
        invoices.unpaid = listOf(
            invoice(id = 1, clientId = 7, total = "12000", dueOn = today.minusDays(4)),
            invoice(id = 2, clientId = 7, total = "8000", paid = "3000", dueOn = today.plusDays(3)),
        )
        clients.rows = listOf(client(7, "Ahmed"))

        val money = succeeded(engine.today()).money

        assertThat(money.outstandingTotal).isEqualTo(Money.of("17000"))
        assertThat(money.overdueTotal).isEqualTo(Money.of("12000"))
        assertThat(money.invoiceCount).isEqualTo(2)
        assertThat(money.overdueCount).isEqualTo(1)
    }

    @Test
    fun `a ledger in two currencies refuses to state one total rather than inventing a rate`() = runTest {
        invoices.unpaid = listOf(
            invoice(id = 1, clientId = 7, total = "12000", dueOn = today.minusDays(4)),
            invoice(id = 2, clientId = 8, total = "400", dueOn = today.minusDays(2), currency = USD),
        )

        val money = succeeded(engine.today()).money

        assertThat(money.outstandingTotal).isNull()
        assertThat(money.overdueTotal).isNull()
        assertThat(money.invoiceCount).isEqualTo(2)
        assertThat(money.overdueCount).isEqualTo(2)
    }

    @Test
    fun `nothing owed is zero in Abu's own currency rather than an unknown`() = runTest {
        profiles.result = AppResult.Success(profile(currency = USD))

        val money = succeeded(engine.today()).money

        assertThat(money.outstandingTotal).isEqualTo(Money.zero(USD))
        assertThat(money.invoiceCount).isEqualTo(0)
    }

    @Test
    fun `the invoices due soon are the ones still to fall due, carrying what is left on them`() = runTest {
        invoices.unpaid = listOf(
            invoice(id = 1, clientId = 7, number = "INV-A", dueOn = today.minusDays(4)),
            invoice(id = 2, clientId = 7, number = "INV-B", total = "8000", paid = "3000", dueOn = today.plusDays(3)),
            invoice(id = 3, clientId = 7, number = "INV-C", dueOn = today.plusDays(30)),
        )
        clients.rows = listOf(client(7, "Ahmed"))

        val dueSoon = succeeded(engine.today()).money.dueSoon

        assertThat(dueSoon.map { it.number }).containsExactly("INV-B")
        assertThat(dueSoon.single().clientName).isEqualTo("Ahmed")
        assertThat(dueSoon.single().amount).isEqualTo(Money.of("5000"))
        assertThat(dueSoon.single().isOverdue).isFalse()
    }

    @Test
    fun `recent payments are named by their client and only a handful are read`() = runTest {
        payments.rows = listOf(payment(id = 1, clientId = 7, amount = "5000"))
        clients.rows = listOf(client(7, "Ahmed"))

        val recent = succeeded(engine.today()).money.recentPayments

        assertThat(recent.single().clientName).isEqualTo("Ahmed")
        assertThat(recent.single().amount).isEqualTo(Money.of("5000"))
        assertThat(payments.lastLimit).isEqualTo(5)
    }

    // ------------------------------------------------------------------ the owner's name

    @Test
    fun `Abu is greeted by the name on his profile`() = runTest {
        profiles.result = AppResult.Success(profile(name = "  Abu  "))

        assertThat(succeeded(engine.today()).ownerName).isEqualTo("Abu")
    }

    @Test
    fun `a profile that could not be read still names its owner and breaks nothing else`() = runTest {
        profiles.result = failure(MiraErrorCode.STORAGE, "database locked")

        val report = succeeded(engine.today())

        // A storage hiccup does not change whose phone this is: the greeting falls back to Abu
        // (AGENTS.md §1) rather than trailing off unnamed, and the rest of the brief is unharmed.
        assertThat(report.ownerName).isEqualTo("Abu")
        assertThat(report.money.outstandingTotal).isEqualTo(Money.zero())
    }

    // ------------------------------------------------------------------ reminders

    @Test
    fun `a reminder whose moment has gone by is not upcoming`() = runTest {
        reminders.rows = listOf(
            reminder(id = 1, title = "Missed", at = daysAgo(1)),
            reminder(id = 2, title = "Tonight", at = at(21)),
            reminder(id = 3, title = "Noon", at = at(12)),
        )

        val upcoming = succeeded(engine.today()).upcomingReminders

        assertThat(upcoming.map { it.title }).containsExactly("Noon", "Tonight").inOrder()
    }

    // ------------------------------------------------------------------ deadlines

    @Test
    fun `a project's own date and its nearest unmet milestone are both deadlines`() = runTest {
        projects.rows = listOf(project(id = 9, name = "Rayluxeo website", dueOn = today.plusDays(9)))
        projects.milestones[9L] = listOf(
            milestone(id = 1, projectId = 9, title = "Copy signed off", dueOn = today.minusDays(2), complete = true),
            milestone(id = 2, projectId = 9, title = "Handover build", dueOn = today.plusDays(2)),
            milestone(id = 3, projectId = 9, title = "Retainer starts", dueOn = today.plusDays(40)),
        )

        val deadlines = succeeded(engine.today()).deadlines

        assertThat(deadlines.map { it.milestone }).containsExactly("Handover build", null).inOrder()
        assertThat(deadlines.first().dueOn).isEqualTo(today.plusDays(2))
        assertThat(deadlines.first().projectName).isEqualTo("Rayluxeo website")
    }

    @Test
    fun `a deadline beyond the horizon is not carried at all`() = runTest {
        projects.rows = listOf(project(id = 9, name = "Rayluxeo website", dueOn = today.plusDays(90)))

        assertThat(succeeded(engine.today()).deadlines).isEmpty()
    }

    // ------------------------------------------------------------------ follow-ups

    @Test
    fun `a client Abu has not sat down with in weeks needs a follow-up`() = runTest {
        clients.rows = listOf(client(7, "Ahmed"), client(8, "Zara"))
        meetings.rows = listOf(
            meeting(id = 1, clientId = 7, startsAt = daysAgo(40)),
            meeting(id = 2, clientId = 7, startsAt = daysAgo(30)),
            meeting(id = 3, clientId = 8, startsAt = daysAgo(2)),
        )

        val followUps = succeeded(engine.today()).followUps

        assertThat(followUps.map { it.clientName }).containsExactly("Ahmed")
        assertThat(followUps.single().lastContactAt).isEqualTo(daysAgo(30))
        assertThat(followUps.single().daysSince).isEqualTo(30L)
    }

    @Test
    fun `a meeting already in the diary settles the follow-up`() = runTest {
        clients.rows = listOf(client(7, "Ahmed"))
        meetings.rows = listOf(
            meeting(id = 1, clientId = 7, startsAt = daysAgo(40)),
            meeting(id = 2, clientId = 7, startsAt = daysAhead(3)),
        )

        assertThat(succeeded(engine.today()).followUps).isEmpty()
    }

    // ------------------------------------------------------------------ the card

    @Test
    fun `the card carries the overdue invoice, the deadline and the renewal`() = runTest {
        invoices.unpaid = listOf(invoice(id = 1, clientId = 7, total = "12000", dueOn = today.minusDays(4)))
        clients.rows = listOf(client(7, "Ahmed"))
        meetings.rows = listOf(meeting(id = 1, clientId = 7, startsAt = daysAgo(2)))
        projects.rows = listOf(project(id = 9, name = "Rayluxeo website", dueOn = today.plusDays(1)))
        subscriptions.result = AppResult.Success(
            listOf(subscription(id = 3, name = "Adobe", nextChargeOn = today.plusDays(2)))
        )

        val items = succeeded(engine.attention())

        assertThat(items.map { it.kind }).containsExactly(
            AttentionKind.INVOICE_OVERDUE,
            AttentionKind.PROJECT_DEADLINE,
            AttentionKind.SUBSCRIPTION_RENEWING,
        ).inOrder()
        assertThat(items.map { it.subject })
            .containsExactly("Ahmed", "Rayluxeo website", "Adobe").inOrder()
        assertThat(subscriptions.askedFor)
            .isEqualTo(today.plusDays(AttentionRules.RENEWAL_HORIZON_DAYS))
    }

    @Test
    fun `a quiet day produces an empty card rather than an invented one`() = runTest {
        assertThat(succeeded(engine.attention())).isEmpty()
        assertThat(succeeded(engine.today()).attention).isEmpty()
    }

    @Test
    fun `the card and the brief agree, because both rank the same reading`() = runTest {
        invoices.unpaid = listOf(invoice(id = 1, clientId = 7, dueOn = today.minusDays(4)))
        clients.rows = listOf(client(7, "Ahmed"))

        assertThat(succeeded(engine.today()).attention).isEqualTo(succeeded(engine.attention()))
    }

    @Test
    fun `the card does not touch the calendar, the reminders or the payments`() = runTest {
        invoices.unpaid = listOf(invoice(id = 1, clientId = 7, dueOn = today.minusDays(4)))

        succeeded(engine.attention())

        assertThat(calendar.permissionChecks).isEqualTo(0)
        assertThat(calendar.reads).isEqualTo(0)
        assertThat(reminders.reads).isEqualTo(0)
        assertThat(payments.reads).isEqualTo(0)
        assertThat(briefs.reads).isEqualTo(0)
    }

    // ------------------------------------------------------------------ helpers

    private fun <T> succeeded(result: AppResult<T>): T {
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return (result as AppResult.Success).value
    }

    private fun <T> failed(result: AppResult<T>): MiraErrorCode {
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        return (result as AppResult.Failure).error.code
    }

    private fun at(hour: Int, minute: Int = 0): Instant = FixedMiraClock.dhaka(2026, 3, 10, hour, minute)

    private fun daysAgo(days: Long): Instant = onDayAt(today.minusDays(days))

    private fun daysAhead(days: Long): Instant = onDayAt(today.plusDays(days))

    private fun onDayAt(date: LocalDate): Instant =
        date.atTime(9, 0).atZone(FixedMiraClock.DHAKA).toInstant()

    private fun brief(
        open: Int = 0,
        dueToday: Int = 0,
        overdue: Int = 0,
        completedToday: Int = 0,
    ) = DailyBrief(
        date = today,
        dayPart = DayPart.MORNING,
        openTaskCount = open,
        dueTodayCount = dueToday,
        overdueCount = overdue,
        completedTodayCount = completedToday,
        nextReminderTitle = null,
        nextReminderAt = null,
    )

    private fun task(id: Long, title: String, dueAt: Instant? = null) =
        Task(id = id, title = title, dueAt = dueAt, createdAt = EPOCH_ISH, updatedAt = EPOCH_ISH)

    private fun reminder(id: Long, title: String, at: Instant) =
        Reminder(id = id, title = title, triggerAt = at, createdAt = EPOCH_ISH)

    private fun event(title: String, startsAt: Instant, endsAt: Instant?) =
        CalendarEvent(systemId = 1L, calendarId = 1L, title = title, startsAt = startsAt, endsAt = endsAt)

    private fun invoice(
        id: Long,
        clientId: Long,
        number: String = "INV-2026-014",
        total: String = "1000",
        paid: String = "0",
        dueOn: LocalDate,
        currency: Currency = Money.BDT,
    ): Invoice {
        val amount = Money.of(total, currency)
        return Invoice(
            id = id,
            number = number,
            clientId = clientId,
            status = InvoiceStatus.SENT,
            issuedOn = dueOn.minusDays(30),
            dueOn = dueOn,
            subtotal = amount,
            discount = Money.zero(currency),
            tax = Money.zero(currency),
            total = amount,
            amountPaid = Money.of(paid, currency),
            createdAt = EPOCH_ISH,
            updatedAt = EPOCH_ISH,
        )
    }

    private fun payment(id: Long, clientId: Long, amount: String) = Payment(
        id = id,
        clientId = clientId,
        amount = Money.of(amount),
        receivedAt = daysAgo(1),
        createdAt = EPOCH_ISH,
        updatedAt = EPOCH_ISH,
    )

    private fun client(id: Long, name: String) =
        Client(id = id, name = name, createdAt = EPOCH_ISH, updatedAt = EPOCH_ISH)

    private fun project(id: Long, name: String, dueOn: LocalDate?) = Project(
        id = id,
        name = name,
        status = ProjectStatus.ACTIVE,
        dueOn = dueOn,
        createdAt = EPOCH_ISH,
        updatedAt = EPOCH_ISH,
    )

    private fun milestone(
        id: Long,
        projectId: Long,
        title: String,
        dueOn: LocalDate?,
        complete: Boolean = false,
    ) = ProjectMilestone(
        id = id,
        projectId = projectId,
        title = title,
        dueOn = dueOn,
        isComplete = complete,
        createdAt = EPOCH_ISH,
        updatedAt = EPOCH_ISH,
    )

    private fun subscription(id: Long, name: String, nextChargeOn: LocalDate) = Subscription(
        id = id,
        name = name,
        amount = Money.of("2400"),
        nextChargeOn = nextChargeOn,
        createdAt = EPOCH_ISH,
        updatedAt = EPOCH_ISH,
    )

    private fun meeting(id: Long, clientId: Long, startsAt: Instant) = Meeting(
        id = id,
        title = "Review",
        clientId = clientId,
        startsAt = startsAt,
        createdAt = EPOCH_ISH,
        updatedAt = EPOCH_ISH,
    )

    private fun profile(name: String = "Abu", currency: Currency = Money.BDT) = UserProfile(
        displayName = name,
        defaultCurrency = currency,
        createdAt = EPOCH_ISH,
        updatedAt = EPOCH_ISH,
    )

    private companion object {
        val EPOCH_ISH: Instant = Instant.parse("2020-01-01T00:00:00Z")
        val USD: Currency = Currency.getInstance("USD")
    }
}

/** A port the brief must not reach for. Failing loudly is the point. */
private fun unused(): Nothing = error("The daily brief does not use this")

private class FakeBriefRepository(var result: AppResult<DailyBrief>) : BriefRepository {
    var reads = 0
    override suspend fun todayBrief(): AppResult<DailyBrief> {
        reads++
        return result
    }
}

private class FakeTaskRepository : TaskRepository {
    val byFilter = mutableMapOf<TaskFilter, List<Task>>()
    override fun observe(filter: TaskFilter): Flow<List<Task>> = flow { emit(byFilter[filter].orEmpty()) }
    override fun observeCounts(): Flow<TaskCounts> = unused()
    override suspend fun byId(id: Long): AppResult<Task> = unused()
    override suspend fun search(query: String?, filter: TaskFilter): AppResult<List<Task>> = unused()
    override suspend fun create(task: Task): AppResult<Task> = unused()
    override suspend fun update(task: Task): AppResult<Task> = unused()
    override suspend fun setDone(id: Long, done: Boolean): AppResult<Task> = unused()
    override suspend fun delete(id: Long): AppResult<Unit> = unused()
    override suspend fun restore(id: Long): AppResult<Task> = unused()

        // Added when TaskRepository grew for the task system. These are not part of what this
        // test exercises, and a fake that answered them quietly would let a future change reach
        // for them unnoticed — so they fail loudly instead.
        override fun observe(query: TaskQuery): Flow<List<Task>> = unsupported("observe(query)")
        override suspend fun list(query: TaskQuery): AppResult<List<Task>> = unsupported("list")
        override suspend fun count(query: TaskQuery): AppResult<Int> = unsupported("count")
        override suspend fun groupedByProject(query: TaskQuery): AppResult<List<TaskGroup>> =
            unsupported("groupedByProject")
        override suspend fun groupedByClient(query: TaskQuery): AppResult<List<TaskGroup>> =
            unsupported("groupedByClient")
        override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean): AppResult<Int> =
            unsupported("setDoneAll")
        override suspend fun snooze(id: Long, until: Instant): AppResult<Task> = unsupported("snooze")
        override suspend fun archive(id: Long): AppResult<Task> = unsupported("archive")
        override suspend fun unarchive(id: Long): AppResult<Task> = unsupported("unarchive")
        override suspend fun archiveAll(ids: Collection<Long>): AppResult<Int> = unsupported("archiveAll")
        override suspend fun deleteAll(ids: Collection<Long>): AppResult<Int> = unsupported("deleteAll")
        override suspend fun completeWithNextOccurrence(
            id: Long,
            nextDueAt: Instant,
        ): AppResult<TaskOccurrence> = unsupported("completeWithNextOccurrence")
        override suspend fun setTags(taskId: Long, tagIds: Collection<Long>): AppResult<Task> =
            unsupported("setTags")
        override suspend fun addTag(taskId: Long, tagId: Long): AppResult<Task> = unsupported("addTag")
        override suspend fun removeTag(taskId: Long, tagId: Long): AppResult<Task> = unsupported("removeTag")

        override suspend fun attachFile(taskId: Long, fileId: Long): AppResult<Task> =
            unsupported("attachFile")
        override suspend fun detachFile(taskId: Long, fileId: Long): AppResult<Task> =
            unsupported("detachFile")

        private fun unsupported(name: String): Nothing =
            throw UnsupportedOperationException("This test's task fake was asked for $name")
}

private class FakeReminderRepository : ReminderRepository {
    var rows: List<Reminder> = emptyList()
    var reads = 0
    override fun observeUpcoming(): Flow<List<Reminder>> = flow {
        reads++
        emit(rows)
    }

    override fun observeByKind(kind: ReminderKind): Flow<List<Reminder>> = unused()
    override suspend fun byId(id: Long): AppResult<Reminder> = unused()
    override suspend fun create(reminder: Reminder): AppResult<Reminder> = unused()
    override suspend fun update(reminder: Reminder): AppResult<Reminder> = unused()
    override suspend fun markFired(id: Long): AppResult<Unit> = unused()
    override suspend fun cancel(id: Long): AppResult<Unit> = unused()
    override suspend fun nextScheduled(): AppResult<Reminder?> = unused()
    override suspend fun rescheduleAll(): AppResult<Int> = unused()
        override suspend fun listOfKinds(
            kinds: Collection<ReminderKind>,
        ): AppResult<List<Reminder>> = AppResult.Success(rows.filter { it.kind in kinds })

}

private class FakeCalendarRepository : CalendarRepository {
    var readable = false
    var events: AppResult<List<CalendarEvent>> = AppResult.Success(emptyList())
    var permissionChecks = 0
    var reads = 0
    var from: Instant? = null
    var to: Instant? = null

    override fun canRead(): Boolean {
        permissionChecks++
        return readable
    }

    override fun canWrite(): Boolean = unused()
    override suspend fun writableCalendars(): AppResult<List<WritableCalendar>> = unused()
    override suspend fun defaultCalendarId(): AppResult<Long?> = unused()

    override suspend fun eventsBetween(from: Instant, to: Instant): AppResult<List<CalendarEvent>> {
        reads++
        this.from = from
        this.to = to
        return events
    }

    override suspend fun createEvent(event: CalendarEvent): AppResult<CalendarEvent> = unused()
    override suspend fun updateEvent(event: CalendarEvent): AppResult<CalendarEvent> = unused()
    override suspend fun deleteEvent(systemEventId: Long): AppResult<Unit> = unused()
    override suspend fun searchEvents(query: String, from: Instant?, to: Instant?): AppResult<List<CalendarEvent>> = unused()
}

private class FakeInvoiceRepository : InvoiceRepository {
    var unpaid: List<Invoice> = emptyList()
    override fun observeUnpaid(): Flow<List<Invoice>> = flow { emit(unpaid) }
    override fun observeAll(): Flow<List<Invoice>> = unused()
    override fun observeForClient(clientId: Long): Flow<List<Invoice>> = unused()
    override fun observeOverdue(): Flow<List<Invoice>> = unused()
    override suspend fun byId(id: Long): AppResult<Invoice> = unused()
    override suspend fun byNumber(number: String): AppResult<Invoice> = unused()
    override suspend fun search(query: String?): AppResult<List<Invoice>> = unused()
    override suspend fun create(invoice: Invoice): AppResult<Invoice> = unused()
    override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> = unused()
    override suspend fun update(invoice: Invoice): AppResult<Invoice> = unused()
    override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> = unused()
    override suspend fun softDelete(id: Long): AppResult<Unit> = unused()
    override suspend fun restore(id: Long): AppResult<Invoice> = unused()
    override suspend fun delete(id: Long): AppResult<Unit> = unused()
}

private class FakePaymentRepository : PaymentRepository {
    var rows: List<Payment> = emptyList()
    var reads = 0
    var lastLimit: Int? = null

    override fun observeRecent(limit: Int): Flow<List<Payment>> = flow {
        reads++
        lastLimit = limit
        emit(rows)
    }

    override fun observeForInvoice(invoiceId: Long): Flow<List<Payment>> = unused()
    override suspend fun byId(id: Long): AppResult<Payment> = unused()
    override suspend fun forInvoice(invoiceId: Long): AppResult<List<Payment>> = unused()
    override suspend fun record(payment: Payment): AppResult<Payment> = unused()
    override suspend fun totalReceivedBetween(from: Instant, to: Instant, currency: Currency): AppResult<Money> =
        unused()

    override suspend fun delete(id: Long): AppResult<Unit> = unused()
}

private class FakeProjectRepository : ProjectRepository {
    var rows: List<Project> = emptyList()
    val milestones = mutableMapOf<Long, List<ProjectMilestone>>()

    override fun observe(filter: ProjectFilter): Flow<List<Project>> = flow { emit(rows) }
    override fun observeMilestones(projectId: Long): Flow<List<ProjectMilestone>> =
        flow { emit(milestones[projectId].orEmpty()) }

    override fun observeForClient(clientId: Long): Flow<List<Project>> = unused()
    override suspend fun byId(id: Long): AppResult<Project> = unused()
    override suspend fun search(query: String?, filter: ProjectFilter): AppResult<List<Project>> = unused()
    override suspend fun create(project: Project, milestones: List<ProjectMilestone>): AppResult<Project> = unused()
    override suspend fun update(project: Project): AppResult<Project> = unused()
    override suspend fun setStatus(id: Long, status: ProjectStatus): AppResult<Project> = unused()
    override suspend fun softDelete(id: Long): AppResult<Unit> = unused()
    override suspend fun restore(id: Long): AppResult<Project> = unused()
    override suspend fun purge(id: Long): AppResult<Unit> = unused()
    override suspend fun addMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> = unused()
    override suspend fun updateMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> = unused()
    override suspend fun setMilestoneComplete(id: Long, complete: Boolean): AppResult<ProjectMilestone> = unused()
    override suspend fun deleteMilestone(id: Long): AppResult<Unit> = unused()
    override suspend fun reorderMilestones(projectId: Long, orderedIds: List<Long>): AppResult<List<ProjectMilestone>> =
        unused()
}

private class FakeSubscriptionRepository : SubscriptionRepository {
    var result: AppResult<List<Subscription>> = AppResult.Success(emptyList())
    var askedFor: LocalDate? = null

    override suspend fun dueOnOrBefore(date: LocalDate): AppResult<List<Subscription>> {
        askedFor = date
        return result
    }

    override fun observeActive(): Flow<List<Subscription>> = unused()
    override fun observeAll(): Flow<List<Subscription>> = unused()
    override suspend fun byId(id: Long): AppResult<Subscription> = unused()
    override suspend fun create(subscription: Subscription): AppResult<Subscription> = unused()
    override suspend fun update(subscription: Subscription): AppResult<Subscription> = unused()
    override suspend fun setActive(id: Long, active: Boolean): AppResult<Subscription> = unused()
    override suspend fun advanceOverdueCharges(): AppResult<Int> = unused()
    override suspend fun delete(id: Long): AppResult<Unit> = unused()
    override suspend fun restore(id: Long): AppResult<Subscription> = unused()
}

private class FakeClientRepository : ClientRepository {
    var rows: List<Client> = emptyList()
    var askedFor: ClientFilter? = null
    override fun observe(filter: ClientFilter): Flow<List<Client>> = flow {
        askedFor = filter
        emit(rows)
    }

    override suspend fun byId(id: Long): AppResult<Client> = unused()
    override suspend fun search(query: String?, filter: ClientFilter): AppResult<List<Client>> = unused()
    override suspend fun create(client: Client): AppResult<Client> = unused()
    override suspend fun update(client: Client): AppResult<Client> = unused()
    override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client> = unused()
    override suspend fun softDelete(id: Long): AppResult<Unit> = unused()
    override suspend fun restore(id: Long): AppResult<Client> = unused()
    override suspend fun purge(id: Long): AppResult<Unit> = unused()
}

/**
 * [observeBetween] honours the half-open window exactly as the DAO does, so what the test
 * exercises is the engine's own window rather than a fake that hands back everything.
 */
private class FakeMeetingRepository : MeetingRepository {
    var rows: List<Meeting> = emptyList()

    override fun observeBetween(from: Instant, to: Instant): Flow<List<Meeting>> = flow {
        emit(rows.filter { !it.startsAt.isBefore(from) && it.startsAt.isBefore(to) }.sortedBy { it.startsAt })
    }

    override fun observeUpcoming(): Flow<List<Meeting>> = unused()
    override fun observeForClient(clientId: Long): Flow<List<Meeting>> = unused()
    override fun observeForProject(projectId: Long): Flow<List<Meeting>> = unused()
    override fun observeNotes(meetingId: Long): Flow<List<MeetingNote>> = unused()
    override fun observeOpenActionItems(): Flow<List<MeetingNote>> = unused()
    override suspend fun byId(id: Long): AppResult<Meeting> = unused()
    override suspend fun notesFor(meetingId: Long): AppResult<List<MeetingNote>> = unused()
    override suspend fun create(meeting: Meeting, notes: List<MeetingNote>): AppResult<Meeting> = unused()
    override suspend fun update(meeting: Meeting): AppResult<Meeting> = unused()
    override suspend fun addNote(note: MeetingNote): AppResult<MeetingNote> = unused()
    override suspend fun updateNote(note: MeetingNote): AppResult<MeetingNote> = unused()
    override suspend fun replaceNotes(meetingId: Long, notes: List<MeetingNote>): AppResult<List<MeetingNote>> =
        unused()

    override suspend fun linkNoteToTask(noteId: Long, taskId: Long?): AppResult<MeetingNote> = unused()
    override suspend fun removeNote(noteId: Long): AppResult<Unit> = unused()
    override suspend fun moveToBin(id: Long): AppResult<Unit> = unused()
    override suspend fun restore(id: Long): AppResult<Meeting> = unused()
    override suspend fun purge(id: Long): AppResult<Unit> = unused()
}

private class FakeUserProfileRepository : UserProfileRepository {
    var result: AppResult<UserProfile?> = AppResult.Success(null)
    override fun observe(): Flow<UserProfile?> = unused()
    override suspend fun current(): AppResult<UserProfile?> = result
    override suspend fun save(profile: UserProfile): AppResult<UserProfile> = unused()
}
