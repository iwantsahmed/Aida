package com.rayluxeo.aida.domain.search

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.Expense
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.Task
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
import com.rayluxeo.aida.domain.repository.TaskRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

data class UniversalSearchResults(
    val query: String,
    val tasks: List<Task> = emptyList(),
    val notes: List<Note> = emptyList(),
    val clients: List<Client> = emptyList(),
    val projects: List<Project> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val files: List<FileRecord> = emptyList(),
    val memories: List<MemoryFact> = emptyList(),
    val conversations: List<ConversationTurn> = emptyList(),
) {
    val totalCount: Int
        get() = tasks.size + notes.size + clients.size + projects.size +
            invoices.size + quotes.size + payments.size + expenses.size +
            files.size + memories.size + conversations.size
}

class UniversalSearchUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val noteRepository: NoteRepository,
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    private val invoiceRepository: InvoiceRepository,
    private val quoteRepository: QuoteRepository,
    private val paymentRepository: PaymentRepository,
    private val expenseRepository: ExpenseRepository,
    private val fileRepository: FileRepository,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationThreadRepository,
) {
    suspend operator fun invoke(query: String, limitPerEntity: Int = 10): UniversalSearchResults = coroutineScope {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return@coroutineScope UniversalSearchResults(query = trimmedQuery)
        }

        val tasksDeferred = async { (taskRepository.search(trimmedQuery, com.rayluxeo.aida.domain.model.TaskFilter.ALL) as? AppResult.Success)?.value ?: emptyList() }
        val notesDeferred = async { (noteRepository.search(trimmedQuery) as? AppResult.Success)?.value ?: emptyList() }
        val clientsDeferred = async { (clientRepository.search(trimmedQuery) as? AppResult.Success)?.value ?: emptyList() }
        val projectsDeferred = async { (projectRepository.search(trimmedQuery) as? AppResult.Success)?.value ?: emptyList() }
        val invoicesDeferred = async { (invoiceRepository.search(trimmedQuery) as? AppResult.Success)?.value ?: emptyList() }
        val quotesDeferred = async { (quoteRepository.search(trimmedQuery) as? AppResult.Success)?.value ?: emptyList() }
        val paymentsDeferred = async { (paymentRepository.search(trimmedQuery, limitPerEntity) as? AppResult.Success)?.value ?: emptyList() }
        val expensesDeferred = async { (expenseRepository.search(trimmedQuery, limitPerEntity) as? AppResult.Success)?.value ?: emptyList() }
        val filesDeferred = async { (fileRepository.search(trimmedQuery, limitPerEntity) as? AppResult.Success)?.value ?: emptyList() }
        val memoriesDeferred = async { (memoryRepository.search(trimmedQuery, limitPerEntity) as? AppResult.Success)?.value ?: emptyList() }
        val conversationsDeferred = async {
            val hits = (conversationRepository.search(trimmedQuery, limitPerEntity) as? AppResult.Success)?.value ?: emptyList()
            hits.map { it.turn }
        }

        UniversalSearchResults(
            query = trimmedQuery,
            tasks = tasksDeferred.await().take(limitPerEntity),
            notes = notesDeferred.await().take(limitPerEntity),
            clients = clientsDeferred.await().take(limitPerEntity),
            projects = projectsDeferred.await().take(limitPerEntity),
            invoices = invoicesDeferred.await().take(limitPerEntity),
            quotes = quotesDeferred.await().take(limitPerEntity),
            payments = paymentsDeferred.await().take(limitPerEntity),
            expenses = expensesDeferred.await().take(limitPerEntity),
            files = filesDeferred.await().take(limitPerEntity),
            memories = memoriesDeferred.await().take(limitPerEntity),
            conversations = conversationsDeferred.await().take(limitPerEntity),
        )
    }
}
