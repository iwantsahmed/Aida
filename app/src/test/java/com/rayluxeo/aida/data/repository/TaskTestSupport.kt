package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.TaskPriority
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The rows the task tests need around a task: a client to bill, a project to file it under and a
 * file to attach. They are written through the open helper rather than through their own
 * repositories so that a change in one of those cannot make these tests fail for reasons that
 * have nothing to do with tasks.
 *
 * Foreign keys are on in `openTestDatabase`, so these parents have to be real.
 */
internal object TaskTestData {

    val ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

    /** Thursday, so "every weekday" and "every Monday" both have somewhere to go. */
    val TODAY: LocalDate = LocalDate.of(2026, 3, 5)

    fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli()

    fun now(hour: Int = 14): LocalDateTime = TODAY.atTime(hour, 0)

    fun startOfDay(): Long = TODAY.atStartOfDay(ZONE).toInstant().toEpochMilli()

    fun endOfDay(): Long = TODAY.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()
}

internal fun MiraDatabase.insertClient(name: String, at: Long = TaskTestData.at(TaskTestData.TODAY, 9)): Long {
    val statement = openHelper.writableDatabase.compileStatement(
        "INSERT INTO clients (uuid, name, companyName, email, phone, addressLine, notes, " +
            "defaultCurrencyCode, defaultRateMinor, isArchived, deletedAtEpochMillis, " +
            "createdAtEpochMillis, updatedAtEpochMillis) " +
            "VALUES (?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, ?, ?)",
    )
    statement.bindString(1, "client-$name")
    statement.bindString(2, name)
    statement.bindLong(3, at)
    statement.bindLong(4, at)
    return statement.executeInsert()
}

internal fun MiraDatabase.insertProject(
    name: String,
    clientId: Long? = null,
    at: Long = TaskTestData.at(TaskTestData.TODAY, 9),
): Long {
    val statement = openHelper.writableDatabase.compileStatement(
        "INSERT INTO projects (uuid, clientId, name, description, status, startedOnEpochDay, " +
            "dueOnEpochDay, budgetMinor, currencyCode, deletedAtEpochMillis, " +
            "createdAtEpochMillis, updatedAtEpochMillis) " +
            "VALUES (?, ?, ?, NULL, ?, NULL, NULL, NULL, 'BDT', NULL, ?, ?)",
    )
    statement.bindString(1, "project-$name")
    if (clientId == null) statement.bindNull(2) else statement.bindLong(2, clientId)
    statement.bindString(3, name)
    statement.bindString(4, ProjectStatus.ACTIVE.name)
    statement.bindLong(5, at)
    statement.bindLong(6, at)
    return statement.executeInsert()
}

internal fun MiraDatabase.insertFile(
    name: String,
    at: Long = TaskTestData.at(TaskTestData.TODAY, 9),
): Long {
    val statement = openHelper.writableDatabase.compileStatement(
        "INSERT INTO files (uuid, displayName, relativePath, mimeType, sizeBytes, source, " +
            "extractedText, clientId, projectId, deletedAtEpochMillis, createdAtEpochMillis, " +
            "updatedAtEpochMillis) " +
            "VALUES (?, ?, ?, 'application/pdf', 1024, ?, NULL, NULL, NULL, NULL, ?, ?)",
    )
    statement.bindString(1, "file-$name")
    statement.bindString(2, name)
    statement.bindString(3, "documents/$name")
    statement.bindString(4, FileSource.IMPORT.name)
    statement.bindLong(5, at)
    statement.bindLong(6, at)
    return statement.executeInsert()
}

/** A task row with everything the newer columns need, so a test only says what it cares about. */
internal fun taskEntity(
    title: String,
    description: String? = null,
    notes: String? = null,
    dueAt: Long? = null,
    dueAllDay: Boolean = false,
    estimatedMinutes: Int? = null,
    priority: TaskPriority = TaskPriority.NORMAL,
    isDone: Boolean = false,
    completedAt: Long? = null,
    projectId: Long? = null,
    clientId: Long? = null,
    recurrenceRule: String? = null,
    seriesUuid: String? = null,
    archivedAt: Long? = null,
    deletedAt: Long? = null,
    at: Long = TaskTestData.at(TaskTestData.TODAY, 9),
): TaskEntity = TaskEntity(
    title = title,
    description = description,
    notes = notes,
    dueAtEpochMillis = dueAt,
    dueAllDay = dueAllDay,
    estimatedMinutes = estimatedMinutes,
    priority = priority,
    isDone = isDone,
    completedAtEpochMillis = completedAt,
    origin = RecordOrigin.MANUAL,
    projectId = projectId,
    clientId = clientId,
    recurrenceRule = recurrenceRule,
    seriesUuid = seriesUuid,
    archivedAtEpochMillis = archivedAt,
    deletedAtEpochMillis = deletedAt,
    createdAtEpochMillis = at,
    updatedAtEpochMillis = at,
)

