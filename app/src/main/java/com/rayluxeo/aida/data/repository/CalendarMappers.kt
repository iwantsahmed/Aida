package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.data.db.entity.CalendarLinkEntity
import com.rayluxeo.aida.data.db.entity.MeetingEntity
import com.rayluxeo.aida.data.db.entity.MeetingNoteEntity
import com.rayluxeo.aida.domain.model.CalendarLink
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.model.MeetingNote
import java.time.Instant

/**
 * Epoch millis are a storage detail; [Instant] is the domain currency. The conversion stays here
 * rather than in a Room TypeConverter because every time column in this cluster is indexed and
 * compared against a window, and an index over a converted column is unusable to SQLite.
 */
internal fun MeetingEntity.toDomain(): Meeting = Meeting(
    id = id,
    title = title,
    clientId = clientId,
    projectId = projectId,
    startsAt = Instant.ofEpochMilli(startsAtEpochMillis),
    endsAt = endsAtEpochMillis?.let(Instant::ofEpochMilli),
    location = location,
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun Meeting.toEntity(): MeetingEntity = MeetingEntity(
    id = id,
    title = title,
    clientId = clientId,
    projectId = projectId,
    startsAtEpochMillis = startsAt.toEpochMilli(),
    endsAtEpochMillis = endsAt?.toEpochMilli(),
    location = location,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun MeetingNoteEntity.toDomain(): MeetingNote = MeetingNote(
    id = id,
    meetingId = meetingId,
    body = body,
    isActionItem = isActionItem,
    taskId = taskId,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun MeetingNote.toEntity(): MeetingNoteEntity = MeetingNoteEntity(
    id = id,
    meetingId = meetingId,
    body = body,
    isActionItem = isActionItem,
    taskId = taskId,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun CalendarLinkEntity.toDomain(): CalendarLink = CalendarLink(
    id = id,
    systemEventId = systemEventId,
    systemCalendarId = systemCalendarId,
    title = title,
    startsAt = Instant.ofEpochMilli(startsAtEpochMillis),
    endsAt = endsAtEpochMillis?.let(Instant::ofEpochMilli),
    taskId = taskId,
    projectId = projectId,
    meetingId = meetingId,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun CalendarLink.toEntity(): CalendarLinkEntity = CalendarLinkEntity(
    id = id,
    systemEventId = systemEventId,
    systemCalendarId = systemCalendarId,
    title = title,
    startsAtEpochMillis = startsAt.toEpochMilli(),
    endsAtEpochMillis = endsAt?.toEpochMilli(),
    taskId = taskId,
    projectId = projectId,
    meetingId = meetingId,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)
