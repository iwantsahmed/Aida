package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.ConversationDao
import com.rayluxeo.aida.domain.model.Conversation
import com.rayluxeo.aida.domain.model.ConversationSearchHit
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.repository.ConversationThreadRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * History over the same two tables the live transcript writes to.
 *
 * Every mutation reads the thread before it writes, because the DAO's update statements report
 * nothing back: a rename aimed at a thread that is already in the bin has to come back as
 * NOT_FOUND rather than appearing to succeed and changing nothing.
 */
@Singleton
class ConversationThreadRepositoryImpl @Inject constructor(
    private val dao: ConversationDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : ConversationThreadRepository {

    override fun observeThreads(): Flow<List<Conversation>> =
        dao.observeActive()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    /**
     * The messages table carries no deleted flag of its own — the thread holds it — so the state
     * of the thread is checked once, as collection starts, and a binned thread yields nothing.
     */
    override fun observeMessages(conversationId: Long): Flow<List<ConversationTurn>> = flow {
        val thread = dao.conversationById(conversationId)
        if (thread == null || thread.deletedAtEpochMillis != null) {
            emit(emptyList())
        } else {
            emitAll(dao.observeMessages(conversationId).map { rows -> rows.map { it.toDomain() } })
        }
    }.flowOn(dispatchers.io).orOnFailure(emptyList())

    override suspend fun create(title: String?): AppResult<Conversation> =
        storageCall(dispatchers, "create conversation") {
            val now = clock.now().toEpochMilli()
            val current = dao.mostRecentConversation()
            if (current != null && current.messageCount > 0) {
                dao.setArchived(current.id, true, now)
            }
            val entity = com.rayluxeo.aida.data.db.entity.ConversationEntity(
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                startedAtEpochMillis = now,
                lastMessageAtEpochMillis = now,
                messageCount = 0,
                isArchived = false,
                deletedAtEpochMillis = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
            val id = dao.insertConversation(entity)
            dao.conversationById(id)?.toDomain() ?: entity.copy(id = id).toDomain()
        }

    override suspend fun thread(id: Long): AppResult<Conversation> {
        val found = storageCall(dispatchers, "load conversation $id") { dao.conversationById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value
                ?.takeIf { it.deletedAtEpochMillis == null }
                ?.toDomain()
                ?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No conversation with id $id")
        }
    }

    override suspend fun rename(id: Long, title: String?): AppResult<Conversation> {
        val named = title?.trim()?.takeIf { it.isNotEmpty() }
        return mutate(id, "rename conversation $id") { now -> dao.renameConversation(id, named, now) }
    }

    override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Conversation> =
        mutate(id, "archive conversation $id") { now -> dao.setArchived(id, archived, now) }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val deleted = mutate(id, "delete conversation $id") { now -> dao.softDelete(id, now) }
        return when (deleted) {
            is AppResult.Failure -> deleted
            is AppResult.Success -> AppResult.Success(Unit)
        }
    }

    override suspend fun deleteAll(): AppResult<Unit> =
        storageCall(dispatchers, "delete all conversations") {
            dao.deleteAllConversations()
        }

    override suspend fun search(query: String, limit: Int): AppResult<List<ConversationSearchHit>> =
        storageCall(dispatchers, "search conversation messages") {
            val match = ftsMatchOrNull(query)
            if (match == null) {
                emptyList()
            } else {
                dao.searchMessages(match, limit.coerceAtLeast(1)).map { it.toSearchHit() }
            }
        }

    /**
     * Reads the thread, applies [block], and reads it back so the caller gets the row as stored
     * rather than the row it hoped for. A thread already in the bin is refused: undeleting is a
     * separate decision, not a side effect of renaming.
     */
    private suspend fun mutate(
        id: Long,
        detail: String,
        block: suspend (nowEpochMillis: Long) -> Unit,
    ): AppResult<Conversation> {
        val updated = storageCall(dispatchers, detail) {
            val existing = dao.conversationById(id)
            if (existing == null || existing.deletedAtEpochMillis != null) {
                null
            } else {
                block(clock.now().toEpochMilli())
                dao.conversationById(id)?.toDomain()
            }
        }
        return when (updated) {
            is AppResult.Failure -> updated
            is AppResult.Success -> updated.value?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No conversation with id $id")
        }
    }
}
