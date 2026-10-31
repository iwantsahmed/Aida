package com.rayluxeo.aida.domain.search

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Conversation
import com.rayluxeo.aida.domain.model.ConversationSearchHit
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteAttachment
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.NoteQuery
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectMilestone
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ConversationThreadRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.FileRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.MemoryRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.QuoteRepository
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.repository.TaskOccurrence
import com.rayluxeo.aida.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

class UniversalSearchUseCaseTest {

    private val now = Instant.ofEpochMilli(1700000000000L)

    private inner class FakeTaskRepo(val result: List<Task>) : TaskRepository {
        override fun observe(filter: TaskFilter): Flow<List<Task>> = flowOf(result)
        override fun observe(query: TaskQuery): Flow<List<Task>> = flowOf(result)
        override suspend fun list(query: TaskQuery): AppResult<List<Task>> = result.asSuccess()
        override suspend fun count(query: TaskQuery): AppResult<Int> = result.size.asSuccess()
        override suspend fun groupedByProject(query: TaskQuery): AppResult<List<TaskGroup>> = emptyList<TaskGroup>().asSuccess()
        override suspend fun groupedByClient(query: TaskQuery): AppResult<List<TaskGroup>> = emptyList<TaskGroup>().asSuccess()
        override fun observeCounts(): Flow<TaskCounts> = flowOf(TaskCounts(0, 0, 0, 0))
        override suspend fun byId(id: Long): AppResult<Task> = result.first().asSuccess()
        override suspend fun search(query: String?, filter: TaskFilter): AppResult<List<Task>> = result.asSuccess()
        override suspend fun create(task: Task): AppResult<Task> = task.asSuccess()
        override suspend fun update(task: Task): AppResult<Task> = task.asSuccess()
        override suspend fun setDone(id: Long, isDone: Boolean): AppResult<Task> = result.first().asSuccess()
        override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean): AppResult<Int> = ids.size.asSuccess()
        override suspend fun snooze(id: Long, until: Instant): AppResult<Task> = result.first().asSuccess()
        override suspend fun archive(id: Long): AppResult<Task> = result.first().asSuccess()
        override suspend fun unarchive(id: Long): AppResult<Task> = result.first().asSuccess()
        override suspend fun archiveAll(ids: Collection<Long>): AppResult<Int> = ids.size.asSuccess()
        override suspend fun delete(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun restore(id: Long): AppResult<Task> = result.first().asSuccess()
        override suspend fun deleteAll(ids: Collection<Long>): AppResult<Int> = ids.size.asSuccess()
        override suspend fun completeWithNextOccurrence(id: Long, nextDueAt: Instant): AppResult<TaskOccurrence> =
            TaskOccurrence(result.first(), result.first()).asSuccess()
        override suspend fun setTags(taskId: Long, tagIds: Collection<Long>): AppResult<Task> = result.first().asSuccess()
        override suspend fun addTag(taskId: Long, tagId: Long): AppResult<Task> = result.first().asSuccess()
        override suspend fun removeTag(taskId: Long, tagId: Long): AppResult<Task> = result.first().asSuccess()
        override suspend fun attachFile(taskId: Long, fileId: Long): AppResult<Task> = result.first().asSuccess()
        override suspend fun detachFile(taskId: Long, fileId: Long): AppResult<Task> = result.first().asSuccess()
    }

    private inner class FakeNoteRepo(val result: List<Note>) : NoteRepository {
        override fun observe(filter: NoteFilter): Flow<List<Note>> = flowOf(result)
        override fun observe(query: NoteQuery): Flow<List<Note>> = flowOf(result)
        override suspend fun list(query: NoteQuery): AppResult<List<Note>> = result.asSuccess()
        override fun observeUsedTags(): Flow<List<Tag>> = flowOf(emptyList())
        override fun observeAttachments(noteId: Long): Flow<List<NoteAttachment>> = flowOf(emptyList())
        override suspend fun byId(id: Long): AppResult<Note> = result.first().asSuccess()
        override suspend fun search(query: String?): AppResult<List<Note>> = result.asSuccess()
        override suspend fun create(note: Note): AppResult<Note> = note.asSuccess()
        override suspend fun update(note: Note): AppResult<Note> = note.asSuccess()
        override suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Note> = result.first().asSuccess()
        override suspend fun archive(id: Long): AppResult<Note> = result.first().asSuccess()
        override suspend fun unarchive(id: Long): AppResult<Note> = result.first().asSuccess()
        override suspend fun setTaskLink(id: Long, taskId: Long?): AppResult<Note> = result.first().asSuccess()
        override suspend fun setClientLink(id: Long, clientId: Long?): AppResult<Note> = result.first().asSuccess()
        override suspend fun setProjectLink(id: Long, projectId: Long?): AppResult<Note> = result.first().asSuccess()
        override suspend fun setTags(noteId: Long, tagIds: Collection<Long>): AppResult<Note> = result.first().asSuccess()
        override suspend fun addTag(noteId: Long, tagId: Long): AppResult<Note> = result.first().asSuccess()
        override suspend fun removeTag(noteId: Long, tagId: Long): AppResult<Note> = result.first().asSuccess()
        override suspend fun attach(noteId: Long, fileId: Long): AppResult<NoteAttachment> =
            NoteAttachment(
                id = 1L,
                noteId = noteId,
                fileId = fileId,
                position = 0,
                displayName = "file",
                mimeType = "image/jpeg",
                sizeBytes = 10L,
                relativePath = "file",
                createdAt = now,
            ).asSuccess()
        override suspend fun detach(noteId: Long, fileId: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun delete(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun restore(id: Long): AppResult<Unit> = Unit.asSuccess()
    }

    private inner class FakeClientRepo(val result: List<Client>) : ClientRepository {
        override fun observe(filter: ClientFilter): Flow<List<Client>> = flowOf(result)
        override suspend fun byId(id: Long): AppResult<Client> = result.first().asSuccess()
        override suspend fun search(query: String?, filter: ClientFilter): AppResult<List<Client>> = result.asSuccess()
        override suspend fun create(client: Client): AppResult<Client> = client.asSuccess()
        override suspend fun update(client: Client): AppResult<Client> = client.asSuccess()
        override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client> = result.first().asSuccess()
        override suspend fun softDelete(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun restore(id: Long): AppResult<Client> = result.first().asSuccess()
        override suspend fun purge(id: Long): AppResult<Unit> = Unit.asSuccess()
    }

    private inner class FakeProjectRepo(val result: List<Project>) : ProjectRepository {
        override fun observe(filter: ProjectFilter): Flow<List<Project>> = flowOf(result)
        override fun observeForClient(clientId: Long): Flow<List<Project>> = flowOf(result)
        override suspend fun byId(id: Long): AppResult<Project> = result.first().asSuccess()
        override suspend fun search(query: String?, filter: ProjectFilter): AppResult<List<Project>> = result.asSuccess()
        override suspend fun create(project: Project, milestones: List<ProjectMilestone>): AppResult<Project> = project.asSuccess()
        override suspend fun update(project: Project): AppResult<Project> = project.asSuccess()
        override suspend fun setStatus(id: Long, status: ProjectStatus): AppResult<Project> = result.first().asSuccess()
        override suspend fun softDelete(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun restore(id: Long): AppResult<Project> = result.first().asSuccess()
        override suspend fun purge(id: Long): AppResult<Unit> = Unit.asSuccess()
        override fun observeMilestones(projectId: Long): Flow<List<ProjectMilestone>> = flowOf(emptyList())
        override suspend fun addMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> = milestone.asSuccess()
        override suspend fun updateMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> = milestone.asSuccess()
        override suspend fun setMilestoneComplete(id: Long, complete: Boolean): AppResult<ProjectMilestone> =
            ProjectMilestone(id = id, projectId = 1L, title = "m", dueOn = null, isComplete = complete, completedAt = null, position = 0, createdAt = now, updatedAt = now).asSuccess()
        override suspend fun reorderMilestones(projectId: Long, orderedIds: List<Long>): AppResult<List<ProjectMilestone>> = emptyList<ProjectMilestone>().asSuccess()
        override suspend fun deleteMilestone(id: Long): AppResult<Unit> = Unit.asSuccess()
    }

    private inner class FakeInvoiceRepo(val result: List<Invoice>) : InvoiceRepository {
        override fun observeAll(): Flow<List<Invoice>> = flowOf(result)
        override fun observeForClient(clientId: Long): Flow<List<Invoice>> = flowOf(result)
        override fun observeUnpaid(): Flow<List<Invoice>> = flowOf(result)
        override fun observeOverdue(): Flow<List<Invoice>> = flowOf(result)
        override suspend fun byId(id: Long): AppResult<Invoice> = result.first().asSuccess()
        override suspend fun byNumber(number: String): AppResult<Invoice> = result.first().asSuccess()
        override suspend fun search(query: String?): AppResult<List<Invoice>> = result.asSuccess()
        override suspend fun create(invoice: Invoice): AppResult<Invoice> = invoice.asSuccess()
        override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> = invoice.asSuccess()
        override suspend fun update(invoice: Invoice): AppResult<Invoice> = invoice.asSuccess()
        override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> = result.first().asSuccess()
        override suspend fun softDelete(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun restore(id: Long): AppResult<Invoice> = result.first().asSuccess()
        override suspend fun delete(id: Long): AppResult<Unit> = Unit.asSuccess()
    }

    private inner class FakeQuoteRepo(val result: List<Quote>) : QuoteRepository {
        override fun observeAll(): Flow<List<Quote>> = flowOf(result)
        override fun observeForClient(clientId: Long): Flow<List<Quote>> = flowOf(result)
        override fun observeOpen(): Flow<List<Quote>> = flowOf(result)
        override suspend fun byId(id: Long): AppResult<Quote> = result.first().asSuccess()
        override suspend fun byNumber(number: String): AppResult<Quote> = result.first().asSuccess()
        override suspend fun search(query: String?): AppResult<List<Quote>> = result.asSuccess()
        override suspend fun create(quote: Quote): AppResult<Quote> = quote.asSuccess()
        override suspend fun createWithNextNumber(quote: Quote): AppResult<Quote> = quote.asSuccess()
        override suspend fun update(quote: Quote): AppResult<Quote> = quote.asSuccess()
        override suspend fun setStatus(id: Long, status: QuoteStatus): AppResult<Quote> = result.first().asSuccess()
        override suspend fun markConverted(id: Long, invoiceId: Long): AppResult<Quote> = result.first().asSuccess()
        override suspend fun softDelete(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun restore(id: Long): AppResult<Quote> = result.first().asSuccess()
        override suspend fun delete(id: Long): AppResult<Unit> = Unit.asSuccess()
    }

    private inner class FakePaymentRepo(val result: List<Payment>) : PaymentRepository {
        override fun observeRecent(limit: Int): Flow<List<Payment>> = flowOf(result)
        override fun observeForInvoice(invoiceId: Long): Flow<List<Payment>> = flowOf(result)
        override suspend fun byId(id: Long): AppResult<Payment> = result.first().asSuccess()
        override suspend fun forInvoice(invoiceId: Long): AppResult<List<Payment>> = result.asSuccess()
        override suspend fun search(query: String?, limit: Int): AppResult<List<Payment>> = result.asSuccess()
        override suspend fun record(payment: Payment): AppResult<Payment> = payment.asSuccess()
        override suspend fun totalReceivedBetween(from: Instant, to: Instant, currency: Currency): AppResult<Money> = Money.zero().asSuccess()
        override suspend fun delete(id: Long): AppResult<Unit> = Unit.asSuccess()
    }

    private inner class FakeExpenseRepo(val result: List<Expense>) : ExpenseRepository {
        override fun observeAll(): Flow<List<Expense>> = flowOf(result)
        override fun observeByCategory(category: ExpenseCategory): Flow<List<Expense>> = flowOf(result)
        override fun observeBetween(from: Instant, to: Instant): Flow<List<Expense>> = flowOf(result)
        override fun observeForProject(projectId: Long): Flow<List<Expense>> = flowOf(result)
        override suspend fun byId(id: Long): AppResult<Expense> = result.first().asSuccess()
        override suspend fun listBetween(from: Instant, to: Instant): AppResult<List<Expense>> = result.asSuccess()
        override suspend fun search(query: String?, limit: Int): AppResult<List<Expense>> = result.asSuccess()
        override suspend fun create(expense: Expense): AppResult<Expense> = expense.asSuccess()
        override suspend fun update(expense: Expense): AppResult<Expense> = expense.asSuccess()
        override suspend fun totalSpentBetween(from: Instant, to: Instant, currency: Currency, category: ExpenseCategory?): AppResult<Money> = Money.zero().asSuccess()
        override suspend fun delete(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun restore(id: Long): AppResult<Expense> = result.first().asSuccess()
    }

    private inner class FakeFileRepo(val result: List<FileRecord>) : FileRepository {
        override fun observeAll(): Flow<List<FileRecord>> = flowOf(result)
        override fun observeForClient(clientId: Long): Flow<List<FileRecord>> = flowOf(result)
        override fun observeForProject(projectId: Long): Flow<List<FileRecord>> = flowOf(result)
        override fun observeBySource(source: FileSource): Flow<List<FileRecord>> = flowOf(result)
        override suspend fun byId(id: Long): AppResult<FileRecord> = result.first().asSuccess()
        override suspend fun search(query: String?, limit: Int): AppResult<List<FileRecord>> = result.asSuccess()
        override suspend fun register(file: FileRecord): AppResult<FileRecord> = file.asSuccess()
        override suspend fun registerScan(file: FileRecord, pageCount: Int): AppResult<FileRecord> = file.asSuccess()
        override suspend fun update(file: FileRecord): AppResult<FileRecord> = file.asSuccess()
        override suspend fun recordRecognisedText(fileId: Long, text: String?): AppResult<FileRecord> = result.first().asSuccess()
        override suspend fun scanFor(fileId: Long): AppResult<com.rayluxeo.aida.domain.model.ScanRecord?> = AppResult.Success(null)
        override suspend fun softDelete(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun restore(id: Long): AppResult<FileRecord> = result.first().asSuccess()
        override suspend fun listReclaimable(): AppResult<List<FileRecord>> = emptyList<FileRecord>().asSuccess()
        override suspend fun purge(id: Long): AppResult<Unit> = Unit.asSuccess()
    }

    private inner class FakeMemoryRepo(val result: List<MemoryFact>) : MemoryRepository {
        override fun observe(): Flow<List<MemoryFact>> = flowOf(result)
        override fun observeByCategory(category: MemoryCategory): Flow<List<MemoryFact>> = flowOf(result)
        override fun observeForSubject(subject: String): Flow<List<MemoryFact>> = flowOf(result)
        override suspend fun byId(id: Long): AppResult<MemoryFact> = result.first().asSuccess()
        override suspend fun search(query: String?, limit: Int): AppResult<List<MemoryFact>> = result.asSuccess()
        override suspend fun recall(query: String, limit: Int): AppResult<List<MemoryFact>> = result.asSuccess()
        override suspend fun remember(fact: MemoryFact): AppResult<MemoryFact> = fact.asSuccess()
        override suspend fun update(fact: MemoryFact): AppResult<MemoryFact> = fact.asSuccess()
        override suspend fun setPinned(id: Long, isPinned: Boolean): AppResult<MemoryFact> = result.first().asSuccess()
        override suspend fun updateCategory(id: Long, category: MemoryCategory): AppResult<MemoryFact> = result.first().asSuccess()
        override suspend fun forget(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun restore(id: Long): AppResult<Unit> = Unit.asSuccess()
    }

    private inner class FakeConversationRepo(val result: List<ConversationTurn>) : ConversationThreadRepository {
        override suspend fun create(title: String?): AppResult<Conversation> =
            AppResult.Success(
                Conversation(
                    id = 1L,
                    title = title,
                    startedAt = now,
                    lastMessageAt = now,
                    messageCount = 0,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        override fun observeThreads(): Flow<List<Conversation>> = flowOf(emptyList())
        override fun observeMessages(conversationId: Long): Flow<List<ConversationTurn>> = flowOf(result)
        override suspend fun thread(id: Long): AppResult<Conversation> = AppResult.Failure(com.rayluxeo.aida.core.common.MiraError(com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND))
        override suspend fun rename(id: Long, title: String?): AppResult<Conversation> = AppResult.Failure(com.rayluxeo.aida.core.common.MiraError(com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND))
        override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Conversation> = AppResult.Failure(com.rayluxeo.aida.core.common.MiraError(com.rayluxeo.aida.core.common.MiraErrorCode.NOT_FOUND))
        override suspend fun delete(id: Long): AppResult<Unit> = Unit.asSuccess()
        override suspend fun deleteAll(): AppResult<Unit> = Unit.asSuccess()
        override suspend fun search(query: String, limit: Int): AppResult<List<ConversationSearchHit>> = result.map { ConversationSearchHit(1L, it) }.asSuccess()
    }

    @Test
    fun `universal search aggregates results from all 11 entities`() = runTest {
        val task = Task(id = 1, title = "Website task", createdAt = now, updatedAt = now)
        val note = Note(id = 1, title = "Website note", body = "details", createdAt = now, updatedAt = now)
        val client = Client(id = 1, name = "Ahmed", createdAt = now, updatedAt = now)
        val project = Project(id = 1, name = "Ahmed Website", createdAt = now, updatedAt = now)
        val invoice = Invoice(id = 1, number = "INV-001", clientId = 1, issuedOn = LocalDate.now(), dueOn = LocalDate.now(), subtotal = Money.zero(), discount = Money.zero(), tax = Money.zero(), total = Money.zero(), amountPaid = Money.zero(), createdAt = now, updatedAt = now)
        val quote = Quote(id = 1, number = "Q-001", clientId = 1, issuedOn = LocalDate.now(), subtotal = Money.zero(), discount = Money.zero(), tax = Money.zero(), total = Money.zero(), createdAt = now, updatedAt = now)
        val payment = Payment(id = 1, invoiceId = 1, clientId = 1, amount = Money.zero(), method = PaymentMethod.BANK_TRANSFER, receivedAt = now, createdAt = now, updatedAt = now)
        val expense = Expense(id = 1, description = "Website hosting", amount = Money.zero(), spentAt = now, createdAt = now, updatedAt = now)
        val file = FileRecord(id = 1, displayName = "Website Brief.pdf", relativePath = "scans/brief.pdf", mimeType = "application/pdf", sizeBytes = 100, source = FileSource.IMPORT, createdAt = now, updatedAt = now)
        val memory = MemoryFact(id = 1, subject = "Ahmed", fact = "Ahmed prefers WhatsApp", createdAt = now, updatedAt = now)
        val turn = ConversationTurn(id = 1, role = TurnRole.ABU, text = "Website details", language = SpokenLanguage.ENGLISH, source = InputSource.TEXT, createdAt = now)

        val useCase = UniversalSearchUseCase(
            taskRepository = FakeTaskRepo(listOf(task)),
            noteRepository = FakeNoteRepo(listOf(note)),
            clientRepository = FakeClientRepo(listOf(client)),
            projectRepository = FakeProjectRepo(listOf(project)),
            invoiceRepository = FakeInvoiceRepo(listOf(invoice)),
            quoteRepository = FakeQuoteRepo(listOf(quote)),
            paymentRepository = FakePaymentRepo(listOf(payment)),
            expenseRepository = FakeExpenseRepo(listOf(expense)),
            fileRepository = FakeFileRepo(listOf(file)),
            memoryRepository = FakeMemoryRepo(listOf(memory)),
            conversationRepository = FakeConversationRepo(listOf(turn)),
        )

        val results = useCase("Website")

        assertThat(results.tasks).hasSize(1)
        assertThat(results.notes).hasSize(1)
        assertThat(results.clients).hasSize(1)
        assertThat(results.projects).hasSize(1)
        assertThat(results.invoices).hasSize(1)
        assertThat(results.quotes).hasSize(1)
        assertThat(results.payments).hasSize(1)
        assertThat(results.expenses).hasSize(1)
        assertThat(results.files).hasSize(1)
        assertThat(results.memories).hasSize(1)
        assertThat(results.conversations).hasSize(1)
        assertThat(results.totalCount).isEqualTo(11)
    }
}
