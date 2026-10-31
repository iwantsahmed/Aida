package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.rayluxeo.aida.data.db.entity.ConversationEntity
import com.rayluxeo.aida.data.db.entity.ConversationMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Conversations and the messages inside them.
 *
 * Appending is a transaction on purpose: a message and its conversation's running count and
 * last-message time have to move together, or the thread list starts lying about threads that
 * are perfectly intact.
 */
@Dao
abstract class ConversationDao {

    @Query(
        """
        SELECT * FROM conversations
        WHERE deletedAtEpochMillis IS NULL AND isArchived = 0
        ORDER BY lastMessageAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeActive(): Flow<List<ConversationEntity>>

    /** Newest first so the LIMIT keeps the most recent; the repository restores reading order. */
    @Query(
        """
        SELECT m.* FROM conversation_messages AS m
        JOIN conversations AS c ON c.id = m.conversationId
        WHERE c.deletedAtEpochMillis IS NULL
        ORDER BY m.createdAtEpochMillis DESC, m.id DESC
        LIMIT :limit
        """
    )
    abstract fun observeRecentMessages(limit: Int): Flow<List<ConversationMessageEntity>>

    @Query(
        """
        SELECT * FROM conversation_messages
        WHERE conversationId = :conversationId
        ORDER BY createdAtEpochMillis ASC, id ASC
        """
    )
    abstract fun observeMessages(conversationId: Long): Flow<List<ConversationMessageEntity>>

    /**
     * Full-text search over everything said, newest first. The FTS table is external-content, so
     * the join back to the messages table is what supplies the rest of each row.
     *
     * The FTS table is deliberately NOT aliased: FTS4's MATCH operand is a hidden column named
     * after the table, so an alias makes it unresolvable ("no such column").
     */
    @Query(
        """
        SELECT m.* FROM conversation_messages AS m
        JOIN conversation_message_fts ON conversation_message_fts.rowid = m.id
        JOIN conversations AS c ON c.id = m.conversationId
        WHERE conversation_message_fts MATCH :query AND c.deletedAtEpochMillis IS NULL
        ORDER BY m.createdAtEpochMillis DESC
        LIMIT :limit
        """
    )
    abstract suspend fun searchMessages(query: String, limit: Int): List<ConversationMessageEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    abstract suspend fun conversationById(id: Long): ConversationEntity?

    /** The thread a new message joins: the most recently spoken-in, still open, thread. */
    @Query(
        """
        SELECT * FROM conversations
        WHERE deletedAtEpochMillis IS NULL AND isArchived = 0
        ORDER BY lastMessageAtEpochMillis DESC, id DESC
        LIMIT 1
        """
    )
    abstract suspend fun mostRecentConversation(): ConversationEntity?

    @Insert
    abstract suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert
    abstract suspend fun insertMessage(message: ConversationMessageEntity): Long

    @Query(
        """
        UPDATE conversations
        SET messageCount = messageCount + 1,
            lastMessageAtEpochMillis = :atEpochMillis,
            updatedAtEpochMillis = :atEpochMillis
        WHERE id = :conversationId
        """
    )
    abstract suspend fun recordMessageAdded(conversationId: Long, atEpochMillis: Long)

    @Query("UPDATE conversations SET title = :title, updatedAtEpochMillis = :atEpochMillis WHERE id = :id")
    abstract suspend fun renameConversation(id: Long, title: String?, atEpochMillis: Long)

    @Query("UPDATE conversations SET isArchived = :archived, updatedAtEpochMillis = :atEpochMillis WHERE id = :id")
    abstract suspend fun setArchived(id: Long, archived: Boolean, atEpochMillis: Long)

    @Query("UPDATE conversations SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis WHERE id = :id")
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long)

    /** Appends to the given thread and keeps its counters in step, in one transaction. */
    @Transaction
    open suspend fun appendMessage(message: ConversationMessageEntity): Long {
        val id = insertMessage(message)
        recordMessageAdded(message.conversationId, message.createdAtEpochMillis)
        return id
    }

    /**
     * Appends to the most recent open thread, starting one when there is none. Both the lookup
     * and the insert are inside the transaction, so two messages arriving together cannot each
     * decide to start a conversation of their own.
     */
    @Transaction
    open suspend fun appendToCurrent(
        message: ConversationMessageEntity,
        newConversation: (Long) -> ConversationEntity,
    ): Long {
        val existing = mostRecentConversation()
        val conversationId = existing?.id
            ?: insertConversation(newConversation(message.createdAtEpochMillis))
        return appendMessage(message.copy(conversationId = conversationId))
    }

    /** Clearing the transcript removes the threads; the messages go with them by cascade. */
    @Query("DELETE FROM conversations")
    abstract suspend fun deleteAllConversations()
}
