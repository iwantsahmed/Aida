package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Conversation
import com.rayluxeo.aida.domain.model.ConversationSearchHit
import com.rayluxeo.aida.domain.model.ConversationTurn
import kotlinx.coroutines.flow.Flow

/**
 * Threads of talk as history: named, archived, binned and searchable.
 *
 * There are deliberately two ports over the same two tables. [ConversationRepository] is the live
 * transcript — one flat list of recent turns, appended to on every exchange — and the Mira screen
 * is built on it. This port is everything that flat list cannot express: which threads exist, what
 * one thread holds, renaming it, archiving it, putting it in the bin, and full-text search across
 * everything ever said.
 *
 * Tidying the two into one would push a thread id into the transcript, which has no use for one,
 * and would make every change to the history screens a change to the screen Abu talks to every
 * day. Leave them apart.
 */
interface ConversationThreadRepository {

    /** Live threads, most recently spoken in first. Archived and binned threads are not here. */
    fun observeThreads(): Flow<List<Conversation>>

    /** One thread's messages in reading order. A binned thread observes as empty. */
    fun observeMessages(conversationId: Long): Flow<List<ConversationTurn>>

    suspend fun create(title: String? = null): AppResult<Conversation>

    suspend fun thread(id: Long): AppResult<Conversation>

    /** A blank or null title clears the name and leaves the thread to be known by its content. */
    suspend fun rename(id: Long, title: String?): AppResult<Conversation>

    /** Archiving takes a thread out of the list without taking it out of search. */
    suspend fun setArchived(id: Long, archived: Boolean): AppResult<Conversation>

    /** Soft: the thread and its messages stay on disk, so the bin can give them back. */
    suspend fun delete(id: Long): AppResult<Unit>

    /** Clears all conversation history. */
    suspend fun deleteAll(): AppResult<Unit>

    /**
     * Full-text search over every message. Messages in a binned thread never match — a delete has
     * to take the thread out of search too, or the bin is decorative.
     *
     * A query with nothing searchable left in it returns no hits rather than every message.
     */
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): AppResult<List<ConversationSearchHit>>

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 50
    }
}
