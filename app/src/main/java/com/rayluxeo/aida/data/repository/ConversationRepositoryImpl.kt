package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.data.db.dao.ConversationDao
import com.rayluxeo.aida.data.db.entity.ConversationEntity
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.repository.ConversationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The transcript, over the two-table conversation schema.
 *
 * The screens still ask for one flat list of recent turns, which is all a single running
 * conversation needs. Underneath, every turn belongs to a thread, so the history can later be
 * broken into named conversations without the reading side changing at all.
 */
@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val dao: ConversationDao,
    private val dispatchers: DispatcherProvider,
) : ConversationRepository {

    /** SQL takes the newest [limit] rows; the list is reversed so the transcript reads downward. */
    override fun observeRecent(limit: Int): Flow<List<ConversationTurn>> =
        dao.observeRecentMessages(limit.coerceAtLeast(1))
            .map { entities -> entities.asReversed().map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun append(turn: ConversationTurn): AppResult<ConversationTurn> =
        storageCall(dispatchers, "append conversation turn") {
            val id = dao.appendToCurrent(
                message = turn.copy(id = 0L).toEntity(conversationId = 0L),
                newConversation = { startedAt -> newThread(startedAt) },
            )
            turn.copy(id = id)
        }

    override suspend fun clear(): AppResult<Unit> =
        storageCall(dispatchers, "clear conversation") { dao.deleteAllConversations() }

    /**
     * A thread starts unnamed. Naming it needs something to have been said, and nothing has been
     * said yet at the moment it is created.
     */
    private fun newThread(startedAtEpochMillis: Long) = ConversationEntity(
        title = null,
        startedAtEpochMillis = startedAtEpochMillis,
        lastMessageAtEpochMillis = startedAtEpochMillis,
        messageCount = 0,
        isArchived = false,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = startedAtEpochMillis,
        updatedAtEpochMillis = startedAtEpochMillis,
    )
}
