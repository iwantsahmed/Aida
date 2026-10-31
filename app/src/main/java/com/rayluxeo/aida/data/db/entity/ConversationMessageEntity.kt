package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.TurnRole

/**
 * One thing said, by Abu or by Mira.
 *
 * [commandName] and [engineName] record what the pipeline made of the message, which is what
 * lets Abu see later whether an answer came from the phone or from Gemini. `CASCADE` is correct
 * here: a message outside its conversation is not a record of anything.
 */
@Entity(
    tableName = "conversation_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationId", "createdAtEpochMillis"]),
        Index(value = ["role"]),
    ],
)
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val role: TurnRole,
    val text: String,
    val languageTag: String,
    val source: InputSource,
    val commandName: String?,
    /** Which engine answered — the on-device one or Gemini. Null for Abu's own messages. */
    val engineName: String?,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "conversation_message_fts")
@Fts4(contentEntity = ConversationMessageEntity::class)
data class ConversationMessageFtsEntity(
    val text: String,
)
