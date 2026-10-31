package com.rayluxeo.aida.domain.model

import com.rayluxeo.aida.core.common.SpokenLanguage
import java.time.Instant

enum class TurnRole { ABU, MIRA }

/**
 * Where an utterance came from.
 *
 * [SHARED] is not a cosmetic distinction. It marks content another app handed to Mira, which she
 * did not receive from Abu and has no permission to send anywhere — [com.rayluxeo.aida.ai
 * .CommandRouter] refuses the cloud engine for it. Stored by name with a defaulting decoder, so
 * adding a value needs no migration.
 */
enum class InputSource { VOICE, TEXT, SHARED, SYSTEM }

data class ConversationTurn(
    val id: Long = 0L,
    val role: TurnRole,
    val text: String,
    val language: SpokenLanguage,
    val source: InputSource,
    /** Simple class name of the AppCommand this turn produced, for auditability. Null when none. */
    val commandName: String? = null,
    val createdAt: Instant,
)

/**
 * A thread of talk, as the history screens see it.
 *
 * [messageCount] and [lastMessageAt] are read straight off the stored row rather than counted,
 * because they are maintained in the same transaction that appends a message. A thread therefore
 * cannot describe itself with numbers that disagree with the messages it holds.
 */
data class Conversation(
    val id: Long = 0L,
    /** Null until enough has been said to name it. */
    val title: String? = null,
    val startedAt: Instant,
    val lastMessageAt: Instant,
    val messageCount: Int,
    val isArchived: Boolean = false,
    /** Set when the thread is in the bin. Soft, so restoring gives back the same messages. */
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * One message that matched a search, carrying the thread it was said in — without which a hit can
 * be read but not opened.
 */
data class ConversationSearchHit(
    val conversationId: Long,
    val turn: ConversationTurn,
)
