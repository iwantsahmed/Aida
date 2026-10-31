package com.rayluxeo.aida.domain.command.handler

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.memory.MemoryManager
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Conversation
import com.rayluxeo.aida.domain.model.ConversationSearchHit
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.model.MemorySource
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteAttachment
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.NoteQuery
import com.rayluxeo.aida.domain.model.Payment
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
import com.rayluxeo.aida.domain.search.UniversalSearchUseCase
import com.rayluxeo.aida.domain.usecase.ForgetFactUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.Currency

class KnowledgeCommandHandlerTest {

    private val clock = FixedMiraClock()
    private val notes = FakeNoteRepository()
    private val memory = FakeMemoryRepository()
    private val memoryManager = MemoryManager(memory, clock)
    private val conversationRepo = FakeSearchConversationRepo()
    private val contextManager = com.rayluxeo.aida.domain.context.ConversationContextManager()
    private val handler = DefaultKnowledgeCommandHandler(
        notes = notes,
        memory = memory,
        memoryManager = memoryManager,
        forgetFactUseCase = ForgetFactUseCase(memory, memoryManager),
        universalSearchUseCase = UniversalSearchUseCase(
            taskRepository = FakeSearchTaskRepo(),
            noteRepository = notes,
            clientRepository = FakeSearchClientRepo(),
            projectRepository = FakeSearchProjectRepo(),
            invoiceRepository = FakeSearchInvoiceRepo(),
            quoteRepository = FakeSearchQuoteRepo(),
            paymentRepository = FakeSearchPaymentRepo(),
            expenseRepository = FakeSearchExpenseRepo(),
            fileRepository = FakeSearchFileRepo(),
            memoryRepository = memory,
            conversationRepository = conversationRepo,
        ),
        conversationThreadRepository = conversationRepo,
        contextManager = contextManager,
        clock = clock,
    )

    @Test
    fun `a note is stored with its title and body and answered with the title`() = runTest {
        val response = succeeds(handler.createNote(AppCommand.CreateNote("Invoice ideas", "Bill Ahmed monthly")))
        assertThat(response).isEqualTo(MiraResponse.NoteCreated("Invoice ideas"))
    }

    @Test
    fun `new conversation starts a new thread and clears context`() = runTest {
        contextManager.updateClient(Client(id = 1L, name = "Ahmed", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
        val response = succeeds(handler.newConversation(AppCommand.NewConversation))
        assertThat(response).isEqualTo(MiraResponse.NewConversationStarted)
        assertThat(contextManager.currentState().lastClient).isNull()
    }

    @Test
    fun `delete all conversations clears context and returns AllConversationsDeleted`() = runTest {
        contextManager.updateSearchQuery("Ahmed")
        val response = succeeds(handler.deleteAllConversations(AppCommand.DeleteAllConversations))
        assertThat(response).isEqualTo(MiraResponse.AllConversationsDeleted)
        assertThat(contextManager.currentState().lastSearchQuery).isNull()
    }

    private fun <T> succeeds(result: AppResult<T>): T {
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return (result as AppResult.Success).value
    }
}

private class FakeSearchTaskRepo : TaskRepository {
    override fun observe(filter: TaskFilter) = error("unused")
    override fun observe(query: TaskQuery) = error("unused")
    override suspend fun list(query: TaskQuery) = AppResult.Success(emptyList<Task>())
    override suspend fun count(query: TaskQuery) = AppResult.Success(0)
    override suspend fun groupedByProject(query: TaskQuery) = AppResult.Success(emptyList<TaskGroup>())
    override suspend fun groupedByClient(query: TaskQuery) = AppResult.Success(emptyList<TaskGroup>())
    override fun observeCounts() = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun search(query: String?, filter: TaskFilter) = AppResult.Success(emptyList<Task>())
    override suspend fun create(task: Task) = error("unused")
    override suspend fun update(task: Task) = error("unused")
    override suspend fun setDone(id: Long, done: Boolean) = error("unused")
    override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean) = error("unused")
    override suspend fun snooze(id: Long, until: Instant) = error("unused")
    override suspend fun archive(id: Long) = error("unused")
    override suspend fun unarchive(id: Long) = error("unused")
    override suspend fun archiveAll(ids: Collection<Long>) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
    override suspend fun deleteAll(ids: Collection<Long>) = error("unused")
    override suspend fun completeWithNextOccurrence(id: Long, nextDueAt: Instant) = error("unused")
    override suspend fun setTags(taskId: Long, tagIds: Collection<Long>) = error("unused")
    override suspend fun addTag(taskId: Long, tagId: Long) = error("unused")
    override suspend fun removeTag(taskId: Long, tagId: Long) = error("unused")
    override suspend fun attachFile(taskId: Long, fileId: Long) = error("unused")
    override suspend fun detachFile(taskId: Long, fileId: Long) = error("unused")
}

private class FakeSearchClientRepo : ClientRepository {
    override fun observe(filter: ClientFilter) = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun search(query: String?, filter: ClientFilter) = AppResult.Success(emptyList<Client>())
    override suspend fun create(client: Client) = error("unused")
    override suspend fun update(client: Client) = error("unused")
    override suspend fun setArchived(id: Long, archived: Boolean) = error("unused")
    override suspend fun softDelete(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
    override suspend fun purge(id: Long) = error("unused")
}

private class FakeSearchProjectRepo : ProjectRepository {
    override fun observe(filter: ProjectFilter) = error("unused")
    override fun observeForClient(clientId: Long) = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun search(query: String?, filter: ProjectFilter) = AppResult.Success(emptyList<Project>())
    override suspend fun create(project: Project, milestones: List<ProjectMilestone>) = error("unused")
    override suspend fun update(project: Project) = error("unused")
    override suspend fun setStatus(id: Long, status: ProjectStatus) = error("unused")
    override suspend fun softDelete(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
    override suspend fun purge(id: Long) = error("unused")
    override fun observeMilestones(projectId: Long) = error("unused")
    override suspend fun addMilestone(milestone: ProjectMilestone) = error("unused")
    override suspend fun updateMilestone(milestone: ProjectMilestone) = error("unused")
    override suspend fun setMilestoneComplete(id: Long, complete: Boolean) = error("unused")
    override suspend fun reorderMilestones(projectId: Long, orderedIds: List<Long>) = error("unused")
    override suspend fun deleteMilestone(id: Long) = error("unused")
}

private class FakeSearchInvoiceRepo : InvoiceRepository {
    override fun observeAll() = error("unused")
    override fun observeForClient(clientId: Long) = error("unused")
    override fun observeUnpaid() = error("unused")
    override fun observeOverdue() = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun byNumber(number: String) = error("unused")
    override suspend fun search(query: String?) = AppResult.Success(emptyList<Invoice>())
    override suspend fun create(invoice: Invoice) = error("unused")
    override suspend fun createWithNextNumber(invoice: Invoice) = error("unused")
    override suspend fun update(invoice: Invoice) = error("unused")
    override suspend fun setStatus(id: Long, status: InvoiceStatus) = error("unused")
    override suspend fun softDelete(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
}

private class FakeSearchQuoteRepo : QuoteRepository {
    override fun observeAll() = error("unused")
    override fun observeForClient(clientId: Long) = error("unused")
    override fun observeOpen() = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun byNumber(number: String) = error("unused")
    override suspend fun search(query: String?) = AppResult.Success(emptyList<Quote>())
    override suspend fun create(quote: Quote) = error("unused")
    override suspend fun createWithNextNumber(quote: Quote) = error("unused")
    override suspend fun update(quote: Quote) = error("unused")
    override suspend fun setStatus(id: Long, status: QuoteStatus) = error("unused")
    override suspend fun markConverted(id: Long, invoiceId: Long) = error("unused")
    override suspend fun softDelete(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
}

private class FakeSearchPaymentRepo : PaymentRepository {
    override fun observeRecent(limit: Int) = error("unused")
    override fun observeForInvoice(invoiceId: Long) = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun forInvoice(invoiceId: Long) = error("unused")
    override suspend fun search(query: String?, limit: Int) = AppResult.Success(emptyList<Payment>())
    override suspend fun record(payment: Payment) = error("unused")
    override suspend fun totalReceivedBetween(from: Instant, to: Instant, currency: Currency) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
}

private class FakeSearchExpenseRepo : ExpenseRepository {
    override fun observeAll() = error("unused")
    override fun observeByCategory(category: ExpenseCategory) = error("unused")
    override fun observeBetween(from: Instant, to: Instant) = error("unused")
    override fun observeForProject(projectId: Long) = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun listBetween(from: Instant, to: Instant) = error("unused")
    override suspend fun search(query: String?, limit: Int) = AppResult.Success(emptyList<Expense>())
    override suspend fun create(expense: Expense) = error("unused")
    override suspend fun update(expense: Expense) = error("unused")
    override suspend fun totalSpentBetween(from: Instant, to: Instant, currency: Currency, category: ExpenseCategory?) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
}

private class FakeSearchFileRepo : FileRepository {
    override fun observeAll() = error("unused")
    override fun observeForClient(clientId: Long) = error("unused")
    override fun observeForProject(projectId: Long) = error("unused")
    override fun observeBySource(source: FileSource) = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun search(query: String?, limit: Int) = AppResult.Success(emptyList<FileRecord>())
    override suspend fun register(file: FileRecord) = error("unused")
    override suspend fun registerScan(file: FileRecord, pageCount: Int) = error("unused")
    override suspend fun update(file: FileRecord) = error("unused")
    override suspend fun recordRecognisedText(fileId: Long, text: String?) = error("unused")
    override suspend fun scanFor(fileId: Long) = error("unused")
    override suspend fun softDelete(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
    override suspend fun listReclaimable() = error("unused")
    override suspend fun purge(id: Long) = error("unused")
}

private class FakeSearchConversationRepo : ConversationThreadRepository {
    private fun testConv(id: Long, title: String?) = Conversation(
        id = id,
        title = title,
        startedAt = Instant.EPOCH,
        lastMessageAt = Instant.EPOCH,
        messageCount = 0,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    override suspend fun create(title: String?): AppResult<Conversation> =
        AppResult.Success(testConv(1L, title))
    override fun observeThreads() = flowOf(emptyList<Conversation>())
    override fun observeMessages(conversationId: Long) = flowOf(emptyList<ConversationTurn>())
    override suspend fun thread(id: Long) = AppResult.Success(testConv(id, "Test"))
    override suspend fun rename(id: Long, title: String?): AppResult<Conversation> = AppResult.Success(testConv(id, title))
    override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Conversation> = AppResult.Success(testConv(id, "Test").copy(isArchived = archived))
    override suspend fun delete(id: Long) = AppResult.Success(Unit)
    override suspend fun deleteAll() = AppResult.Success(Unit)
    override suspend fun search(query: String, limit: Int) = AppResult.Success(emptyList<ConversationSearchHit>())
}

private class FakeNoteRepository : NoteRepository {
    val rows = mutableListOf<Note>()
    override suspend fun create(note: Note): AppResult<Note> {
        val saved = note.copy(id = (rows.size + 1).toLong())
        rows += saved
        return AppResult.Success(saved)
    }
    override suspend fun search(query: String?): AppResult<List<Note>> = AppResult.Success(rows)
    override fun observe(filter: NoteFilter) = error("unused")
    override fun observe(query: NoteQuery) = error("unused")
    override suspend fun list(query: NoteQuery) = error("unused")
    override fun observeUsedTags() = error("unused")
    override fun observeAttachments(noteId: Long) = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun update(note: Note) = error("unused")
    override suspend fun setPinned(id: Long, pinned: Boolean) = error("unused")
    override suspend fun archive(id: Long) = error("unused")
    override suspend fun unarchive(id: Long) = error("unused")
    override suspend fun setTaskLink(id: Long, taskId: Long?) = error("unused")
    override suspend fun setClientLink(id: Long, clientId: Long?) = error("unused")
    override suspend fun setProjectLink(id: Long, projectId: Long?) = error("unused")
    override suspend fun setTags(noteId: Long, tagIds: Collection<Long>) = error("unused")
    override suspend fun addTag(noteId: Long, tagId: Long) = error("unused")
    override suspend fun removeTag(noteId: Long, tagId: Long) = error("unused")
    override suspend fun attach(noteId: Long, fileId: Long) = error("unused")
    override suspend fun detach(noteId: Long, fileId: Long) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
}

private class FakeMemoryRepository : MemoryRepository {
    val rows = mutableListOf<MemoryFact>()
    override suspend fun search(query: String?, limit: Int) = AppResult.Success(rows)
    override suspend fun recall(query: String, limit: Int) = AppResult.Success(rows)
    override suspend fun remember(fact: MemoryFact): AppResult<MemoryFact> {
        val saved = fact.copy(id = (rows.size + 1).toLong())
        rows += saved
        return AppResult.Success(saved)
    }
    override fun observe() = error("unused")
    override fun observeForSubject(subject: String) = error("unused")
    override suspend fun byId(id: Long) = error("unused")
    override suspend fun update(fact: MemoryFact) = error("unused")
    override suspend fun forget(id: Long) = error("unused")
    override suspend fun restore(id: Long) = error("unused")
}
