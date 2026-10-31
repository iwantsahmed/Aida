package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.NoteAttachmentRow
import com.rayluxeo.aida.data.db.entity.InkPageEntity
import com.rayluxeo.aida.data.db.entity.MemoryEntity
import com.rayluxeo.aida.data.db.entity.NoteEntity
import com.rayluxeo.aida.data.db.entity.VoiceNoteEntity
import com.rayluxeo.aida.domain.model.InkPage
import com.rayluxeo.aida.domain.model.MemoryFact
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteAttachment
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.VoiceNote
import java.time.Duration
import java.time.Instant

/**
 * Notes, memory and voice notes across the storage boundary.
 *
 * Epoch millis and language tags are storage details; [Instant], [Duration] and [SpokenLanguage]
 * are what the domain deals in. The conversion lives here rather than in a Room TypeConverter so
 * the indexed time columns stay primitive and sort in SQL.
 */
/**
 * [tags] is handed in rather than read here, because a note's tags live in another table and are
 * loaded for a whole page at once. A mapper that fetched them would be one query per row, which
 * is what turns a long list into a stutter.
 */
internal fun NoteEntity.toDomain(tags: List<Tag> = emptyList()): Note = Note(
    id = id,
    title = title,
    body = body,
    isPinned = isPinned,
    clientId = clientId,
    projectId = projectId,
    taskId = taskId,
    tags = tags,
    language = SpokenLanguage.fromTag(languageTag),
    archivedAt = archivedAtEpochMillis?.let(Instant::ofEpochMilli),
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

/**
 * [Note.tags] does not cross back into the row: the links are their own table, and writing them
 * from a whole-note update is how a screen that was showing a stale copy silently un-tags a note.
 * Tagging goes through the repository's own tag calls.
 *
 * [Note.archivedAt] and [Note.deletedAt] do cross back, because a row has to be able to carry
 * them, but no caller may choose them: `NoteRepositoryImpl.create` clears both and `update` keeps
 * whatever the stored row says. Filing and binning are written by the guarded statements in
 * `NoteDao`, and this mapper is not a second way in.
 */
internal fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    body = body,
    isPinned = isPinned,
    clientId = clientId,
    projectId = projectId,
    taskId = taskId,
    languageTag = language.tag,
    archivedAtEpochMillis = archivedAt?.toEpochMilli(),
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

/**
 * A handwriting page as the domain reads it.
 *
 * There is no `toEntity` counterpart on purpose: a page is only ever written together with the
 * two files behind it, so building the row is the repository's job and cannot be done from an
 * [InkPage] alone.
 */
internal fun InkPageEntity.toDomain(): InkPage = InkPage(
    id = id,
    noteId = noteId,
    strokeFileId = strokeFileId,
    imageFileId = imageFileId,
    widthPx = widthPx,
    heightPx = heightPx,
    strokeCount = strokeCount,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun NoteAttachmentRow.toDomain(): NoteAttachment = NoteAttachment(
    id = id,
    noteId = noteId,
    fileId = fileId,
    position = position,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    relativePath = relativePath,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

internal fun MemoryEntity.toDomain(): MemoryFact = MemoryFact(
    id = id,
    subject = subject,
    fact = fact,
    category = category,
    isPinned = isPinned,
    source = source,
    language = SpokenLanguage.fromTag(languageTag),
    clientId = clientId,
    projectId = projectId,
    recallCount = recallCount,
    lastRecalledAt = lastRecalledAtEpochMillis?.let(Instant::ofEpochMilli),
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

/**
 * [MemoryFact.recallCount] and [MemoryFact.lastRecalledAt] do cross back into the row, so an edit
 * cannot invent a recall history: the repository writes an edit from the fact it just read, and
 * the recall path never goes through this mapper at all — it is a targeted UPDATE.
 */
internal fun MemoryFact.toEntity(): MemoryEntity = MemoryEntity(
    id = id,
    subject = subject,
    fact = fact,
    category = category,
    isPinned = isPinned,
    source = source,
    languageTag = language.tag,
    clientId = clientId,
    projectId = projectId,
    recallCount = recallCount,
    lastRecalledAtEpochMillis = lastRecalledAt?.toEpochMilli(),
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun VoiceNoteEntity.toDomain(): VoiceNote = VoiceNote(
    id = id,
    audioFileId = audioFileId,
    noteId = noteId,
    transcript = transcript,
    duration = Duration.ofMillis(durationMillis),
    language = SpokenLanguage.fromTag(languageTag),
    recordedAt = Instant.ofEpochMilli(recordedAtEpochMillis),
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

/** A negative duration would be a programming error upstream; it is clamped rather than stored. */
internal fun VoiceNote.toEntity(): VoiceNoteEntity = VoiceNoteEntity(
    id = id,
    audioFileId = audioFileId,
    noteId = noteId,
    transcript = transcript,
    durationMillis = duration.toMillis().coerceAtLeast(0L),
    languageTag = language.tag,
    recordedAtEpochMillis = recordedAt.toEpochMilli(),
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

/**
 * An UPDATE's row count, as the domain reads it.
 *
 * Every soft delete, restore and targeted update in this cluster is guarded in SQL, so "no rows
 * changed" is the answer to "there is no such row, or it was already in that state". Both are
 * NOT_FOUND to a caller, and neither is an exception.
 */
internal fun AppResult<Int>.orNotFound(detail: String): AppResult<Unit> = when (this) {
    is AppResult.Failure -> this
    is AppResult.Success ->
        if (value > 0) AppResult.Success(Unit) else failure(MiraErrorCode.NOT_FOUND, detail)
}
