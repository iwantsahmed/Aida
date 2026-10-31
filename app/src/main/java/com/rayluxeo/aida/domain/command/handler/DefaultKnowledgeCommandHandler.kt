package com.rayluxeo.aida.domain.command.handler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.usecase.ForgetFactUseCase
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.context.ConversationContextManager
import com.rayluxeo.aida.domain.memory.MemoryConflictResolver
import com.rayluxeo.aida.domain.memory.MemoryManager
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.repository.ConversationThreadRepository
import com.rayluxeo.aida.domain.repository.MemoryRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.search.UniversalSearchUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Notes, the facts Mira has been told to hold on to, and conversation history.
 *
 * Both searches read through the repositories' own full-text queries, which already exclude
 * anything Abu has binned — a note he deleted must not come back when he asks what he wrote.
 *
 * Every reply carries a handful of results rather than all of them. Mira reads these out loud,
 * and a spoken list longer than three is a list nobody follows; the count beside it says how many
 * she found, so the reply never implies there were only three. Memory is the one place that count
 * is itself bounded — [MemoryRepository.recall] fetches no more than it is worth saying.
 */
class DefaultKnowledgeCommandHandler @Inject constructor(
    private val notes: NoteRepository,
    private val memory: MemoryRepository,
    private val memoryManager: MemoryManager,
    private val forgetFactUseCase: ForgetFactUseCase,
    private val universalSearchUseCase: UniversalSearchUseCase,
    private val conversationThreadRepository: ConversationThreadRepository,
    private val contextManager: ConversationContextManager,
    private val clock: MiraClock,
) : KnowledgeCommandHandler {

    override suspend fun createNote(command: AppCommand.CreateNote): AppResult<MiraResponse> {
        val now = clock.now()
        val note = Note(
            title = command.title,
            body = command.body,
            language = languageOf(command.title, command.body),
            createdAt = now,
            updatedAt = now,
        )
        return notes.create(note).map {
            contextManager.updateNote(it)
            MiraResponse.NoteCreated(it.title)
        }
    }

    override suspend fun searchNotes(command: AppCommand.SearchNotes): AppResult<MiraResponse> =
        notes.search(command.query).map { found ->
            contextManager.updateSearchQuery(command.query)
            MiraResponse.NotesFound(
                count = found.size,
                titles = found.take(SPOKEN_RESULTS).map { it.spokenTitle() },
            )
        }

    /**
     * The fact keeps the weakest provenance on purpose. This seam carries no input source, so
     * Mira cannot tell a fact Abu spoke from one he typed, and stamping either would put a claim
     * on a record she later quotes back to him as her own.
     */
    override suspend fun storeMemory(command: AppCommand.StoreMemory): AppResult<MiraResponse> {
        val category = MemoryConflictResolver.inferCategory(command.subject, command.fact)
        return memoryManager.remember(
            subject = command.subject,
            fact = command.fact,
            category = category,
        ).map { MiraResponse.MemoryStored(it.subject, it.fact) }
    }

    override suspend fun forgetMemory(command: AppCommand.ForgetMemory): AppResult<MiraResponse> =
        memoryManager.forget(command.memoryId).map { MiraResponse.MemoryForgotten(command.memoryId) }

    /**
     * Forgetting a fact Abu names in words. Resolves first: one match forgets, several ask which,
     * none is a NOT_FOUND. The forget is soft, so a wrong one is restorable from the Memory screen.
     */
    override suspend fun forgetFact(command: AppCommand.ForgetFact): AppResult<MiraResponse> =
        forgetFactUseCase(command.query).flatMap { resolution ->
            when (resolution) {
                is ForgetFactUseCase.Resolution.Exact ->
                    AppResult.Success(MiraResponse.FactForgotten(resolution.subject))
                is ForgetFactUseCase.Resolution.Ambiguous ->
                    AppResult.Success(MiraResponse.AmbiguousFact(command.query, resolution.subjects))
                ForgetFactUseCase.Resolution.None ->
                    failure(MiraErrorCode.NOT_FOUND, "No stored fact matches '${command.query}'")
            }
        }

    /**
     * Recall rather than search, because this is Mira about to use what she knows: the facts she
     * returns here are the ones she is about to say. The side-effect-free [MemoryRepository.search]
     * belongs to the memory screen, where Abu browsing his own list must not inflate the counts
     * that tell him which facts she actually leans on.
     */
    override suspend fun searchMemory(command: AppCommand.SearchMemory): AppResult<MiraResponse> =
        memory.recall(command.query).map { found ->
            contextManager.updateSearchQuery(command.query)
            MiraResponse.MemoriesFound(
                count = found.size,
                facts = found.take(SPOKEN_RESULTS).map { it.fact.trim() },
            )
        }

    override suspend fun universalSearch(command: AppCommand.UniversalSearch): AppResult<MiraResponse> {
        contextManager.updateSearchQuery(command.query)
        val results = universalSearchUseCase(command.query)
        val topSnippets = mutableListOf<String>()
        if (results.tasks.isNotEmpty()) topSnippets.add("Tasks: ${results.tasks.size}")
        if (results.notes.isNotEmpty()) topSnippets.add("Notes: ${results.notes.size}")
        if (results.clients.isNotEmpty()) topSnippets.add("Clients: ${results.clients.size}")
        if (results.projects.isNotEmpty()) topSnippets.add("Projects: ${results.projects.size}")
        if (results.invoices.isNotEmpty()) topSnippets.add("Invoices: ${results.invoices.size}")
        if (results.quotes.isNotEmpty()) topSnippets.add("Quotes: ${results.quotes.size}")
        if (results.payments.isNotEmpty()) topSnippets.add("Payments: ${results.payments.size}")
        if (results.expenses.isNotEmpty()) topSnippets.add("Expenses: ${results.expenses.size}")
        if (results.files.isNotEmpty()) topSnippets.add("Files: ${results.files.size}")
        if (results.memories.isNotEmpty()) topSnippets.add("Memories: ${results.memories.size}")
        if (results.conversations.isNotEmpty()) topSnippets.add("Conversations: ${results.conversations.size}")

        return AppResult.Success(
            MiraResponse.UniversalSearchResultsFound(
                query = results.query,
                totalCount = results.totalCount,
                topSnippets = topSnippets,
            )
        )
    }

    override suspend fun newConversation(command: AppCommand.NewConversation): AppResult<MiraResponse> {
        contextManager.clear()
        return conversationThreadRepository.create(null).map {
            MiraResponse.NewConversationStarted
        }
    }

    override suspend fun deleteConversation(command: AppCommand.DeleteConversation): AppResult<MiraResponse> {
        val targetId = command.id
        contextManager.clear()
        return if (targetId != null) {
            conversationThreadRepository.delete(targetId).map {
                MiraResponse.ConversationDeleted(targetId)
            }
        } else {
            val threads = conversationThreadRepository.observeThreads().firstOrNull()
            val recentId = threads?.firstOrNull()?.id
            if (recentId != null) {
                conversationThreadRepository.delete(recentId).map {
                    MiraResponse.ConversationDeleted(recentId)
                }
            } else {
                AppResult.Success(MiraResponse.ConversationDeleted(null))
            }
        }
    }

    override suspend fun deleteAllConversations(command: AppCommand.DeleteAllConversations): AppResult<MiraResponse> {
        contextManager.clear()
        return conversationThreadRepository.deleteAll().map {
            MiraResponse.AllConversationsDeleted
        }
    }

    override suspend fun searchConversations(command: AppCommand.SearchConversations): AppResult<MiraResponse> {
        contextManager.updateSearchQuery(command.query)
        return conversationThreadRepository.search(command.query).map { hits ->
            MiraResponse.ConversationsFound(
                count = hits.size,
                snippets = hits.take(SPOKEN_RESULTS).map { it.turn.text },
            )
        }
    }

    /** A note Abu never titled is read back by its opening words, since Mira has to say something. */
    private fun Note.spokenTitle(): String {
        val given = title?.trim()
        if (!given.isNullOrEmpty()) return given
        val opening = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (opening.length <= SNIPPET_CHARS) {
            opening
        } else {
            opening.take(SNIPPET_CHARS).trimEnd() + ELLIPSIS
        }
    }

    /**
     * Which language a record was written in, read off the script Abu actually used.
     *
     * A note keeps its language forever and Mira reads it back in that voice, so taking it from
     * Settings would give a Bengali note an English voice on any day he had the app in English.
     * Banglish is Latin script and lands on [SpokenLanguage.ENGLISH]; that is the right answer for
     * a voice, which has to pronounce the letters that are there.
     */
    private fun languageOf(vararg parts: String?): SpokenLanguage {
        val bengali = parts.any { part -> part?.any { it in BENGALI_BLOCK } == true }
        return if (bengali) SpokenLanguage.BENGALI else SpokenLanguage.ENGLISH
    }

    private companion object {
        /** Three is what Mira can say in one breath. The count carries the rest. */
        const val SPOKEN_RESULTS = 3
        const val SNIPPET_CHARS = 60
        const val ELLIPSIS = "\u2026"

        /** The Bengali Unicode block, which is what "written in Bengali" comes down to. */
        val BENGALI_BLOCK = '\u0980'..'\u09FF'
    }
}
