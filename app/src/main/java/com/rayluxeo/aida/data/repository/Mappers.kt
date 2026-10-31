package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.db.dao.VaultSummaryRow
import com.rayluxeo.aida.data.db.entity.ConversationMessageEntity
import com.rayluxeo.aida.data.db.entity.ReminderEntity
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.VaultItemSummary
import com.rayluxeo.aida.domain.task.RecurrenceCodec
import java.time.Instant

/**
 * Reminders, conversation turns and vault summaries, between storage and the domain.
 *
 * Epoch millis are a storage detail; [Instant] is the domain currency. The conversion lives here
 * rather than in a Room TypeConverter so the indexed time columns stay primitive. Tasks have
 * grown their own file, `TaskMappers`, because they carry rows from four tables.
 *
 * [codec] is passed in rather than a second serialisation being invented here: a repeating
 * reminder uses the same `RecurrenceRule` and the same stored format as a repeating task, and two
 * encoders over one grammar is how the two quietly stop agreeing.
 *
 * An unreadable rule comes back as no rule at all, which leaves the reminder as one that happens
 * once. That is the same decision `TextRecurrenceCodec` documents, and it is the safe direction:
 * a reminder that repeats on the wrong days is worse than one that does not repeat.
 */
internal fun ReminderEntity.toDomain(codec: RecurrenceCodec): Reminder = Reminder(
    id = id,
    title = title,
    triggerAt = Instant.ofEpochMilli(triggerAtEpochMillis),
    state = state,
    kind = kind,
    recurrence = codec.decode(recurrenceRule),
    snoozedUntil = snoozedUntilEpochMillis?.let(Instant::ofEpochMilli),
    linkedTaskId = linkedTaskId,
    linkedInvoiceId = linkedInvoiceId,
    linkedClientId = linkedClientId,
    linkedProjectId = linkedProjectId,
    linkedSubscriptionId = linkedSubscriptionId,
    origin = origin,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

internal fun Reminder.toEntity(codec: RecurrenceCodec): ReminderEntity = ReminderEntity(
    id = id,
    title = title,
    triggerAtEpochMillis = triggerAt.toEpochMilli(),
    state = state,
    kind = kind,
    recurrenceRule = recurrence?.let(codec::encode),
    snoozedUntilEpochMillis = snoozedUntil?.toEpochMilli(),
    linkedTaskId = linkedTaskId,
    linkedInvoiceId = linkedInvoiceId,
    linkedClientId = linkedClientId,
    linkedProjectId = linkedProjectId,
    linkedSubscriptionId = linkedSubscriptionId,
    origin = origin,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

internal fun ConversationMessageEntity.toDomain(): ConversationTurn = ConversationTurn(
    id = id,
    role = role,
    text = text,
    language = SpokenLanguage.fromTag(languageTag),
    source = source,
    commandName = commandName,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

internal fun ConversationMessageEntity.toSearchHit(): com.rayluxeo.aida.domain.model.ConversationSearchHit =
    com.rayluxeo.aida.domain.model.ConversationSearchHit(
        conversationId = conversationId,
        turn = toDomain(),
    )

internal fun com.rayluxeo.aida.data.db.entity.ConversationEntity.toDomain(): com.rayluxeo.aida.domain.model.Conversation =
    com.rayluxeo.aida.domain.model.Conversation(
        id = id,
        title = title,
        startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
        lastMessageAt = Instant.ofEpochMilli(lastMessageAtEpochMillis),
        messageCount = messageCount,
        isArchived = isArchived,
        deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )

/**
 * [conversationId] is supplied by the repository rather than carried on the domain turn: which
 * thread a turn belongs to is a storage concern, and the screens only ever read one transcript.
 */
internal fun ConversationTurn.toEntity(conversationId: Long): ConversationMessageEntity =
    ConversationMessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        text = text,
        languageTag = language.tag,
        source = source,
        commandName = commandName,
        engineName = null,
        createdAtEpochMillis = createdAt.toEpochMilli(),
    )

internal fun VaultSummaryRow.toDomain(): VaultItemSummary = VaultItemSummary(
    id = id,
    title = title,
    category = category,
    fileType = fileType,
    folder = folder,
    tags = tagsCsv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    clientId = clientId,
    projectId = projectId,
    invoiceId = invoiceId,
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)
