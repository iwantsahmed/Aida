package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One thread of talk between Abu and Mira.
 *
 * [messageCount] and [lastMessageAtEpochMillis] are kept on the row rather than counted on read,
 * because the conversation list shows both for every thread and a COUNT per row would scan the
 * messages table once per thread. They are maintained in the same transaction as the insert, so
 * they cannot drift from the messages they describe.
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["lastMessageAtEpochMillis"]),
        Index(value = ["isArchived"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Null until there is enough said to name it. */
    val title: String?,
    val startedAtEpochMillis: Long,
    val lastMessageAtEpochMillis: Long,
    val messageCount: Int,
    val isArchived: Boolean,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
