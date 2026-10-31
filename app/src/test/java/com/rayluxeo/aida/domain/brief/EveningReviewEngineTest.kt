package com.rayluxeo.aida.domain.brief

import com.rayluxeo.aida.domain.repository.TaskOccurrence
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.model.TaskQuery
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.CalendarEvent
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.WritableCalendar
import com.rayluxeo.aida.domain.repository.CalendarRepository
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * What Mira says a day came to.
 *
 * Two things are tested harder than the rest because they are where this goes wrong. The review
 * runs in the evening but is about the whole local day, so a task finished at nine in the morning
 * has to be in it; and "tomorrow" is the next LOCAL day, which is 23 hours long the night a clock
 * goes forward — a review built on "now plus twenty-four hours" would reach into the day after.
 *
 * Every money assertion is on [Money]. Nothing here is a Double, including the expected values.
 *
 * No Hilt (google/dagger#5261): the engine is built by hand from the fakes at the bottom of this
 * file, on a clock that never moves.
 */
class EveningReviewEngineTest {

    private val clock: MiraClock = FixedMiraClock.at(21)
    private val tasks = FakeReviewTasks()
    private val payments = FakeReviewPayments()
    private val expenses = FakeReviewExpenses()
    private val invoices = FakeReviewInvoices()
    private val clients = FakeReviewClients()
    private val calendar = FakeReviewCalendar()

    private fun engine(withClock: MiraClock = clock) = DefaultEveningReviewEngine(
        tasks = tasks,
        payments = payments,
        expenses = expenses,
        invoices = invoices,
        clients = clients,
        calendar = calendar,
        clock = withClock,
    )

    // ------------------------------------------------------------------ the day boundary

    @Test
    fun `a task finished in the morning is still in the evening's account of the day`() = runTest {
        tasks.add(
            task(id = 1L, title = "Send proposal", isDone = true, completedAt = dhaka(10, hour = 9)),
            task(id = 2L, title = "Late filing", isDone = true, completedAt = dhaka(10, hour = 23, minute = 59)),
            task(id = 3L, title = "Yesterday's call", isDone = true, completedAt = dhaka(9, hour = 23, minute = 30)),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.completedToday.map { it.title })
            .containsExactly("Send proposal", "Late filing").inOrder()
    }

    @Test
    fun `the day is half-open at both ends of local midnight`() = runTest {
        tasks.add(
            task(id = 1L, title = "First thing", isDone = true, completedAt = dhaka(10, hour = 0)),
            task(id = 2L, title = "Tomorrow already", isDone = true, completedAt = dhaka(11, hour = 0)),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.completedToday.map { it.title }).containsExactly("First thing")
    }

    @Test
    fun `the review is about the date it is given, not the day the clock is on`() = runTest {
        tasks.add(
            task(id = 1L, title = "Monday's work", isDone = true, completedAt = dhaka(9, hour = 14)),
            task(id = 2L, title = "Tuesday's work", isDone = true, completedAt = dhaka(10, hour = 14)),
            task(id = 3L, title = "Due Tuesday", dueAt = dhaka(10, hour = 11)),
        )

        val report = engine().review(TODAY.minusDays(1)).value()

        assertThat(report.date).isEqualTo(TODAY.minusDays(1))
        assertThat(report.completedToday.map { it.title }).containsExactly("Monday's work")
        assertThat(report.tomorrowsPriorities.map { it.title }).containsExactly("Due Tuesday")
    }

    @Test
    fun `tomorrow is the next local day, not twenty-four hours, across a clock change`() = runTest {
        // New York moves to summer time at 02:00 on Sunday 8 March 2026, so that local day is 23
        // hours long. An engine adding 24 hours would reach into the Monday.
        val newYork: MiraClock = FixedMiraClock(
            instant = LocalDateTime.of(2026, 3, 7, 21, 0).atZone(NEW_YORK).toInstant(),
            zone = NEW_YORK,
        )
        calendar.events += listOf(
            event(title = "Late Sunday", startsAt = newYork(2026, 3, 8, 23, 30)),
            event(title = "Early Monday", startsAt = newYork(2026, 3, 9, 0, 30)),
        )

        val report = engine(newYork).review(LocalDate.of(2026, 3, 7)).value()

        assertThat(report.tomorrowsEvents.map { it.title }).containsExactly("Late Sunday")
        assertThat(calendar.askedFrom).isEqualTo(Instant.parse("2026-03-08T05:00:00Z"))
        assertThat(calendar.askedTo).isEqualTo(Instant.parse("2026-03-09T04:00:00Z"))
    }

    // ------------------------------------------------------------------ tasks

    @Test
    fun `still open is what was due that day or earlier, most urgent first`() = runTest {
        tasks.add(
            task(id = 1L, title = "Due today", dueAt = dhaka(10, hour = 9)),
            task(id = 2L, title = "Due today, high", dueAt = dhaka(10, hour = 9), priority = TaskPriority.HIGH),
            task(id = 3L, title = "Owed since yesterday", dueAt = dhaka(9, hour = 10)),
            task(id = 4L, title = "Due tomorrow", dueAt = dhaka(11, hour = 10)),
            task(id = 5L, title = "No date"),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.stillOpen.map { it.title })
            .containsExactly("Owed since yesterday", "Due today, high", "Due today").inOrder()
        assertThat(report.stillOpen.map { it.isOverdue })
            .containsExactly(true, false, false).inOrder()
    }

    @Test
    fun `a finished task is never reported as overdue`() = runTest {
        tasks.add(
            task(
                id = 1L,
                title = "Owed since last week",
                dueAt = dhaka(3, hour = 10),
                isDone = true,
                completedAt = dhaka(10, hour = 17),
            ),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.completedToday.single().isOverdue).isFalse()
    }

    @Test
    fun `tomorrow's priorities are the open tasks actually due the next day`() = runTest {
        tasks.add(
            task(id = 1L, title = "Afternoon delivery", dueAt = dhaka(11, hour = 15)),
            task(id = 2L, title = "Morning call", dueAt = dhaka(11, hour = 9)),
            task(id = 3L, title = "The day after", dueAt = dhaka(12, hour = 9)),
            task(
                id = 4L,
                title = "Already done",
                dueAt = dhaka(11, hour = 8),
                isDone = true,
                completedAt = dhaka(10, hour = 20),
            ),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.tomorrowsPriorities.map { it.title })
            .containsExactly("Morning call", "Afternoon delivery").inOrder()
        assertThat(report.tomorrowsPriorities.map { it.isOverdue }).containsExactly(false, false)
    }

    @Test
    fun `a storage failure reading tasks fails the review rather than reporting an empty day`() = runTest {
        tasks.failWith = MiraErrorCode.STORAGE

        val result = engine().review(TODAY)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.STORAGE)
    }

    // ------------------------------------------------------------------ money

    @Test
    fun `payments received are listed in order and totalled over the whole day`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        payments.add(
            payment(id = 1L, amount = Money.of("2500"), receivedAt = dhaka(10, hour = 16)),
            payment(id = 2L, amount = Money.of("5000"), receivedAt = dhaka(10, hour = 10), clientId = 7L),
            payment(id = 3L, amount = Money.of("1000"), receivedAt = dhaka(9, hour = 10)),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.paymentsReceived.map { it.id }).containsExactly(2L, 1L).inOrder()
        assertThat(report.paymentsReceived.first().clientName).isEqualTo("Ahmed Traders")
        assertThat(report.paymentsReceived.last().clientName).isNull()
        assertThat(report.paymentsTotal).isEqualTo(Money.of("7500"))
        assertThat(payments.askedFrom).isEqualTo(dhaka(10, hour = 0))
        assertThat(payments.askedTo).isEqualTo(dhaka(11, hour = 0))
    }

    @Test
    fun `a day with no payments has no total rather than a zero`() = runTest {
        val report = engine().review(TODAY).value()

        assertThat(report.paymentsReceived).isEmpty()
        assertThat(report.paymentsTotal).isNull()
    }

    @Test
    fun `a day whose money arrived in two currencies is reported without a single total`() = runTest {
        payments.add(
            payment(id = 1L, amount = Money.of("5000"), receivedAt = dhaka(10, hour = 10)),
            payment(id = 2L, amount = Money.of("120", USD), receivedAt = dhaka(10, hour = 11)),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.paymentsReceived.map { it.amount })
            .containsExactly(Money.of("5000"), Money.of("120", USD)).inOrder()
        assertThat(report.paymentsTotal).isNull()
    }

    @Test
    fun `a storage failure totalling payments fails the review`() = runTest {
        payments.add(payment(id = 1L, amount = Money.of("5000"), receivedAt = dhaka(10, hour = 10)))
        payments.failWith = MiraErrorCode.STORAGE

        val result = engine().review(TODAY)

        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.STORAGE)
    }

    @Test
    fun `the expense total is the repository's own sum, not one added up in the engine`() = runTest {
        expenses.add(
            expense(id = 1L, amount = Money.of("900"), spentAt = dhaka(10, hour = 12)),
            expense(id = 2L, amount = Money.of("450"), spentAt = dhaka(10, hour = 13)),
            expense(id = 3L, amount = Money.of("100"), spentAt = dhaka(9, hour = 13)),
        )
        // Deliberately not what the two rows above add up to: the assertion can only pass if the
        // figure came back from the repository's own sum.
        expenses.stubTotal = Money.of("1000")

        val report = engine().review(TODAY).value()

        assertThat(report.expensesRecorded).isEqualTo(2)
        assertThat(report.expensesTotal).isEqualTo(Money.of("1000"))
        assertThat(expenses.askedFrom).isEqualTo(dhaka(10, hour = 0))
        assertThat(expenses.askedTo).isEqualTo(dhaka(11, hour = 0))
    }

    @Test
    fun `invoices raised during the day are listed with the client's name`() = runTest {
        clients.add(client(id = 7L, name = "Ahmed Traders"))
        invoices.add(
            invoice(
                id = 1L,
                number = "INV-2026-014",
                clientId = 7L,
                total = Money.of("50000"),
                createdAt = dhaka(10, hour = 11),
            ),
            invoice(
                id = 2L,
                number = "INV-2026-013",
                clientId = 7L,
                total = Money.of("12000"),
                createdAt = dhaka(9, hour = 11),
            ),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.invoicesCreated).hasSize(1)
        assertThat(report.invoicesCreated.single().number).isEqualTo("INV-2026-014")
        assertThat(report.invoicesCreated.single().clientName).isEqualTo("Ahmed Traders")
        assertThat(report.invoicesCreated.single().amount).isEqualTo(Money.of("50000"))
        assertThat(report.invoicesCreated.single().isOverdue).isFalse()
    }

    @Test
    fun `an invoice whose client is in the bin is still reported, without a name`() = runTest {
        invoices.add(
            invoice(
                id = 1L,
                number = "INV-2026-014",
                clientId = 7L,
                total = Money.of("50000"),
                createdAt = dhaka(10, hour = 11),
            ),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.invoicesCreated.single().clientName).isNull()
    }

    @Test
    fun `a draft raised against a date already past is not called overdue`() = runTest {
        invoices.add(
            invoice(
                id = 1L,
                number = "INV-2026-015",
                clientId = 7L,
                total = Money.of("8000"),
                createdAt = dhaka(10, hour = 11),
                dueOn = TODAY.minusDays(2),
                status = InvoiceStatus.DRAFT,
            ),
            invoice(
                id = 2L,
                number = "INV-2026-016",
                clientId = 7L,
                total = Money.of("8000"),
                createdAt = dhaka(10, hour = 12),
                dueOn = TODAY.minusDays(2),
                status = InvoiceStatus.SENT,
            ),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.invoicesCreated.map { it.isOverdue }).containsExactly(false, true).inOrder()
    }

    // ------------------------------------------------------------------ the calendar

    @Test
    fun `an empty calendar Mira could read is reported as readable`() = runTest {
        val report = engine().review(TODAY).value()

        assertThat(report.tomorrowsEvents).isEmpty()
        assertThat(report.calendarReadable).isTrue()
    }

    @Test
    fun `a calendar Mira may not read is reported as unreadable, not as a free day`() = runTest {
        calendar.readable = false
        calendar.events += event(title = "Client meeting", startsAt = dhaka(11, hour = 10))

        val report = engine().review(TODAY).value()

        assertThat(report.tomorrowsEvents).isEmpty()
        assertThat(report.calendarReadable).isFalse()
    }

    @Test
    fun `a calendar that fails to answer does not take the rest of the review down`() = runTest {
        calendar.failWith = MiraErrorCode.PERMISSION_REQUIRED
        payments.add(payment(id = 1L, amount = Money.of("5000"), receivedAt = dhaka(10, hour = 10)))

        val report = engine().review(TODAY).value()

        assertThat(report.calendarReadable).isFalse()
        assertThat(report.tomorrowsEvents).isEmpty()
        assertThat(report.paymentsTotal).isEqualTo(Money.of("5000"))
    }

    @Test
    fun `tomorrow's events are given in the order they happen`() = runTest {
        calendar.events += listOf(
            event(title = "Afternoon review", startsAt = dhaka(11, hour = 15)),
            event(title = "Morning standup", startsAt = dhaka(11, hour = 9)),
            event(title = "Tonight", startsAt = dhaka(10, hour = 22)),
        )

        val report = engine().review(TODAY).value()

        assertThat(report.tomorrowsEvents.map { it.title })
            .containsExactly("Morning standup", "Afternoon review").inOrder()
    }

    // ------------------------------------------------------------------ a quiet day

    @Test
    fun `a day with nothing recorded on it is quiet`() = runTest {
        val report = engine().review(TODAY).value()

        assertThat(report.isQuiet).isTrue()
    }

    @Test
    fun `one payment is enough to make the day not a quiet one`() = runTest {
        payments.add(payment(id = 1L, amount = Money.of("5000"), receivedAt = dhaka(10, hour = 10)))

        val report = engine().review(TODAY).value()

        assertThat(report.isQuiet).isFalse()
    }

    // ------------------------------------------------------------------ fixtures

    private fun AppResult<EveningReviewReport>.value(): EveningReviewReport = when (this) {
        is AppResult.Success -> this.value
        is AppResult.Failure -> throw AssertionError("Expected a review, got ${this.error.code}")
    }

    private fun task(
        id: Long,
        title: String,
        dueAt: Instant? = null,
        isDone: Boolean = false,
        completedAt: Instant? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
    ): Task = Task(
        id = id,
        title = title,
        dueAt = dueAt,
        priority = priority,
        isDone = isDone,
        completedAt = completedAt,
        createdAt = CREATED,
        updatedAt = CREATED,
    )

    private fun payment(
        id: Long,
        amount: Money,
        receivedAt: Instant,
        clientId: Long? = null,
    ): Payment = Payment(
        id = id,
        clientId = clientId,
        amount = amount,
        receivedAt = receivedAt,
        createdAt = receivedAt,
        updatedAt = receivedAt,
    )

    private fun expense(id: Long, amount: Money, spentAt: Instant): Expense = Expense(
        id = id,
        description = "Expense $id",
        category = ExpenseCategory.SOFTWARE,
        amount = amount,
        spentAt = spentAt,
        createdAt = spentAt,
        updatedAt = spentAt,
    )

    private fun invoice(
        id: Long,
        number: String,
        clientId: Long,
        total: Money,
        createdAt: Instant,
        dueOn: LocalDate = TODAY.plusDays(14),
        status: InvoiceStatus = InvoiceStatus.SENT,
    ): Invoice = Invoice(
        id = id,
        number = number,
        clientId = clientId,
        status = status,
        issuedOn = TODAY,
        dueOn = dueOn,
        subtotal = total,
        discount = Money.zero(total.currency),
        tax = Money.zero(total.currency),
        total = total,
        amountPaid = Money.zero(total.currency),
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun client(id: Long, name: String): Client =
        Client(id = id, name = name, createdAt = CREATED, updatedAt = CREATED)

    private fun event(title: String, startsAt: Instant): CalendarEvent = CalendarEvent(
        systemId = startsAt.toEpochMilli(),
        calendarId = 1L,
        title = title,
        startsAt = startsAt,
        endsAt = startsAt.plusSeconds(3600),
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 10)
        val CREATED: Instant = FixedMiraClock.dhaka(2026, 3, 1, 9)
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val USD: Currency = Currency.getInstance("USD")

        /** A Dhaka wall-clock reading in March 2026, which is how these tests think. */
        fun dhaka(day: Int, hour: Int, minute: Int = 0): Instant =
            FixedMiraClock.dhaka(2026, 3, day, hour, minute)

        fun newYork(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
            LocalDateTime.of(year, month, day, hour, minute).atZone(NEW_YORK).toInstant()
    }

    // ------------------------------------------------------------------ collaborators

    /**
     * Answers the two day-independent filters and refuses the rest: DUE_TODAY and OVERDUE are
     * resolved against the repository's own idea of today, so an engine reaching for them would
     * silently report the wrong day whenever a past date is reviewed.
     */
    private class FakeReviewTasks : TaskRepository {

        private val rows = mutableListOf<Task>()
        var failWith: MiraErrorCode? = null

        fun add(vararg tasks: Task) {
            rows.addAll(tasks)
        }

        override suspend fun search(query: String?, filter: TaskFilter): AppResult<List<Task>> {
            failWith?.let { return failure(it, "Injected by FakeReviewTasks") }
            val matched = rows.filter { it.deletedAt == null }.filter {
                when (filter) {
                    TaskFilter.ALL -> true
                    TaskFilter.OPEN -> !it.isDone
                    TaskFilter.DONE -> it.isDone
                    TaskFilter.DUE_TODAY, TaskFilter.OVERDUE, TaskFilter.UPCOMING,
                    TaskFilter.ARCHIVED, TaskFilter.NO_DATE,
                    -> throw UnsupportedOperationException("The review must not ask for $filter")
                }
            }
            return AppResult.Success(matched)
        }

        override fun observe(filter: TaskFilter): Flow<List<Task>> = outsideTheReview()
        override fun observeCounts(): Flow<TaskCounts> = outsideTheReview()
        override suspend fun byId(id: Long): AppResult<Task> = outsideTheReview()
        override suspend fun create(task: Task): AppResult<Task> = outsideTheReview()
        override suspend fun update(task: Task): AppResult<Task> = outsideTheReview()
        override suspend fun setDone(id: Long, done: Boolean): AppResult<Task> = outsideTheReview()
        override suspend fun delete(id: Long): AppResult<Unit> = outsideTheReview()
        override suspend fun restore(id: Long): AppResult<Task> = outsideTheReview()
    
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

    /** Sums the way the real one does — in SQL over a window, in [Money], never in a Double. */
    private class FakeReviewPayments : PaymentRepository {

        private val rows = mutableListOf<Payment>()
        var failWith: MiraErrorCode? = null
        var askedFrom: Instant? = null
        var askedTo: Instant? = null

        fun add(vararg payments: Payment) {
            rows.addAll(payments)
        }

        /** Newest first and capped, exactly as the real query is, so a limit that bites shows up here. */
        override fun observeRecent(limit: Int): Flow<List<Payment>> = flowOf(
            rows.sortedWith(compareByDescending<Payment> { it.receivedAt }.thenByDescending { it.id })
                .take(limit),
        )

        override suspend fun totalReceivedBetween(
            from: Instant,
            to: Instant,
            currency: Currency,
        ): AppResult<Money> {
            askedFrom = from
            askedTo = to
            failWith?.let { return failure(it, "Injected by FakeReviewPayments") }
            return AppResult.Success(
                rows.filter { it.receivedAt >= from && it.receivedAt < to && it.amount.currency == currency }
                    .fold(Money.zero(currency)) { running, payment -> running + payment.amount },
            )
        }

        override fun observeForInvoice(invoiceId: Long): Flow<List<Payment>> = outsideTheReview()
        override suspend fun byId(id: Long): AppResult<Payment> = outsideTheReview()
        override suspend fun forInvoice(invoiceId: Long): AppResult<List<Payment>> = outsideTheReview()
        override suspend fun record(payment: Payment): AppResult<Payment> = outsideTheReview()
        override suspend fun delete(id: Long): AppResult<Unit> = outsideTheReview()
    }

    private class FakeReviewExpenses : ExpenseRepository {

        private val rows = mutableListOf<Expense>()

        /** Set where a test needs to prove the figure came from here rather than from the engine. */
        var stubTotal: Money? = null
        var askedFrom: Instant? = null
        var askedTo: Instant? = null

        fun add(vararg expenses: Expense) {
            rows.addAll(expenses)
        }

        override suspend fun listBetween(from: Instant, to: Instant): AppResult<List<Expense>> =
            AppResult.Success(rows.filter { it.spentWithin(from, to) })

        override suspend fun totalSpentBetween(
            from: Instant,
            to: Instant,
            currency: Currency,
            category: ExpenseCategory?,
        ): AppResult<Money> {
            askedFrom = from
            askedTo = to
            return AppResult.Success(
                stubTotal ?: rows.filter { it.spentWithin(from, to) && it.amount.currency == currency }
                    .fold(Money.zero(currency)) { running, expense -> running + expense.amount },
            )
        }

        private fun Expense.spentWithin(from: Instant, to: Instant): Boolean =
            deletedAt == null && spentAt >= from && spentAt < to

        override fun observeAll(): Flow<List<Expense>> = outsideTheReview()
        override fun observeByCategory(category: ExpenseCategory): Flow<List<Expense>> = outsideTheReview()
        override fun observeBetween(from: Instant, to: Instant): Flow<List<Expense>> = outsideTheReview()
        override fun observeForProject(projectId: Long): Flow<List<Expense>> = outsideTheReview()
        override suspend fun byId(id: Long): AppResult<Expense> = outsideTheReview()
        override suspend fun create(expense: Expense): AppResult<Expense> = outsideTheReview()
        override suspend fun update(expense: Expense): AppResult<Expense> = outsideTheReview()
        override suspend fun delete(id: Long): AppResult<Unit> = outsideTheReview()
        override suspend fun restore(id: Long): AppResult<Expense> = outsideTheReview()
    }

    private class FakeReviewInvoices : InvoiceRepository {

        private val rows = mutableListOf<Invoice>()

        fun add(vararg invoices: Invoice) {
            rows.addAll(invoices)
        }

        /** A blank query means every live invoice, exactly as the real repository documents. */
        override suspend fun search(query: String?): AppResult<List<Invoice>> =
            AppResult.Success(rows.filter { it.deletedAt == null })

        override fun observeAll(): Flow<List<Invoice>> = outsideTheReview()
        override fun observeForClient(clientId: Long): Flow<List<Invoice>> = outsideTheReview()
        override fun observeUnpaid(): Flow<List<Invoice>> = outsideTheReview()
        override fun observeOverdue(): Flow<List<Invoice>> = outsideTheReview()
        override suspend fun byId(id: Long): AppResult<Invoice> = outsideTheReview()
        override suspend fun byNumber(number: String): AppResult<Invoice> = outsideTheReview()
        override suspend fun create(invoice: Invoice): AppResult<Invoice> = outsideTheReview()
        override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> = outsideTheReview()
        override suspend fun update(invoice: Invoice): AppResult<Invoice> = outsideTheReview()
        override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> = outsideTheReview()
        override suspend fun softDelete(id: Long): AppResult<Unit> = outsideTheReview()
        override suspend fun restore(id: Long): AppResult<Invoice> = outsideTheReview()
        override suspend fun delete(id: Long): AppResult<Unit> = outsideTheReview()
    }

    /** NOT_FOUND for anyone unknown, which is also what the real one says for a client in the bin. */
    private class FakeReviewClients : ClientRepository {

        private val rows = mutableListOf<Client>()

        fun add(vararg clients: Client) {
            rows.addAll(clients)
        }

        override suspend fun byId(id: Long): AppResult<Client> =
            rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No client with id $id")

        override fun observe(filter: ClientFilter): Flow<List<Client>> = outsideTheReview()
        override suspend fun search(query: String?, filter: ClientFilter): AppResult<List<Client>> = outsideTheReview()
        override suspend fun create(client: Client): AppResult<Client> = outsideTheReview()
        override suspend fun update(client: Client): AppResult<Client> = outsideTheReview()
        override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client> = outsideTheReview()
        override suspend fun softDelete(id: Long): AppResult<Unit> = outsideTheReview()
        override suspend fun restore(id: Long): AppResult<Client> = outsideTheReview()
        override suspend fun purge(id: Long): AppResult<Unit> = outsideTheReview()
    }

    private class FakeReviewCalendar : CalendarRepository {

        val events = mutableListOf<CalendarEvent>()
        var readable: Boolean = true
        var failWith: MiraErrorCode? = null
        var askedFrom: Instant? = null
        var askedTo: Instant? = null

        override fun canRead(): Boolean = readable

        override suspend fun eventsBetween(from: Instant, to: Instant): AppResult<List<CalendarEvent>> {
            askedFrom = from
            askedTo = to
            failWith?.let { return failure(it, "Injected by FakeReviewCalendar") }
            return AppResult.Success(events.filter { it.startsAt >= from && it.startsAt < to })
        }

        override fun canWrite(): Boolean = outsideTheReview()
        override suspend fun writableCalendars(): AppResult<List<WritableCalendar>> = outsideTheReview()
        override suspend fun defaultCalendarId(): AppResult<Long?> = outsideTheReview()
        override suspend fun createEvent(event: CalendarEvent): AppResult<CalendarEvent> = outsideTheReview()
        override suspend fun updateEvent(event: CalendarEvent): AppResult<CalendarEvent> = outsideTheReview()
        override suspend fun deleteEvent(systemEventId: Long): AppResult<Unit> = outsideTheReview()
        override suspend fun searchEvents(query: String, from: Instant?, to: Instant?): AppResult<List<CalendarEvent>> = outsideTheReview()
    }
}

/** Makes a fake fail loudly the day the review starts leaning on something it was never given. */
private fun outsideTheReview(): Nothing =
    throw UnsupportedOperationException("The evening review does not use this")
