package com.rayluxeo.aida.data.calendar

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.CalendarLinkEntity
import com.rayluxeo.aida.data.db.entity.MeetingEntity
import com.rayluxeo.aida.data.db.entity.MeetingNoteEntity
import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Abu's zone. Offset +06:00 with no daylight saving, which keeps the arithmetic readable. */
internal val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")

internal val NOW: Instant = LocalDateTime.of(2026, 3, 5, 14, 0).atZone(DHAKA).toInstant()

internal fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDateTime.of(year, month, day, hour, minute).atZone(DHAKA).toInstant()

/**
 * Foreign keys are off by default in an in-memory database, and Room's callback that turns them
 * on is not wired into this builder. Without the pragma every cascade and `SET NULL` assertion in
 * this package would pass because SQLite never checked, which is worse than having no test.
 */
internal fun openTestDatabase(): MiraDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        MiraDatabase::class.java,
    )
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .allowMainThreadQueries()
        .build()
        .also { it.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON") }

/**
 * Clients and projects are inserted as raw SQL rather than through their DAOs: this cluster only
 * needs a parent row to hang a foreign key on, and borrowing another area's DAO would couple
 * these tests to a contract that is not theirs.
 */
internal fun MiraDatabase.insertClient(name: String = "Rayluxeo"): Long {
    val db = openHelper.writableDatabase
    db.execSQL(
        """
        INSERT INTO clients (
            uuid, name, companyName, email, phone, addressLine, notes,
            defaultCurrencyCode, defaultRateMinor, isArchived,
            deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis
        ) VALUES (?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, ?, ?)
        """.trimIndent(),
        arrayOf<Any?>(UUID.randomUUID().toString(), name, NOW.toEpochMilli(), NOW.toEpochMilli()),
    )
    return lastRowId()
}

internal fun MiraDatabase.insertProject(clientId: Long? = null, name: String = "Site build"): Long {
    val db = openHelper.writableDatabase
    db.execSQL(
        """
        INSERT INTO projects (
            uuid, clientId, name, description, status, startedOnEpochDay, dueOnEpochDay,
            budgetMinor, currencyCode, deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis
        ) VALUES (?, ?, ?, NULL, 'ACTIVE', NULL, NULL, NULL, 'BDT', NULL, ?, ?)
        """.trimIndent(),
        arrayOf<Any?>(
            UUID.randomUUID().toString(),
            clientId,
            name,
            NOW.toEpochMilli(),
            NOW.toEpochMilli(),
        ),
    )
    return lastRowId()
}

internal fun MiraDatabase.deleteClient(id: Long) {
    openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = ?", arrayOf<Any>(id))
}

internal fun MiraDatabase.deleteProject(id: Long) {
    openHelper.writableDatabase.execSQL("DELETE FROM projects WHERE id = ?", arrayOf<Any>(id))
}

private fun MiraDatabase.lastRowId(): Long =
    openHelper.writableDatabase.query("SELECT last_insert_rowid()").use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

internal suspend fun MiraDatabase.insertTask(title: String = "Send the quote"): Long =
    taskDao().insert(
        TaskEntity(
            title = title,
            notes = null,
            dueAtEpochMillis = null,
            priority = TaskPriority.NORMAL,
            isDone = false,
            completedAtEpochMillis = null,
            origin = RecordOrigin.MANUAL,
            createdAtEpochMillis = NOW.toEpochMilli(),
            updatedAtEpochMillis = NOW.toEpochMilli(),
        ),
    )

internal fun meetingRow(
    title: String = "Kickoff with Rayluxeo",
    clientId: Long? = null,
    projectId: Long? = null,
    startsAt: Instant = at(2026, 3, 6, 11),
    endsAt: Instant? = null,
    location: String? = null,
    deletedAt: Instant? = null,
): MeetingEntity = MeetingEntity(
    title = title,
    clientId = clientId,
    projectId = projectId,
    startsAtEpochMillis = startsAt.toEpochMilli(),
    endsAtEpochMillis = endsAt?.toEpochMilli(),
    location = location,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = NOW.toEpochMilli(),
    updatedAtEpochMillis = NOW.toEpochMilli(),
)

internal fun noteRow(
    meetingId: Long,
    body: String = "Agreed the scope",
    isActionItem: Boolean = false,
    taskId: Long? = null,
    createdAt: Instant = NOW,
): MeetingNoteEntity = MeetingNoteEntity(
    meetingId = meetingId,
    body = body,
    isActionItem = isActionItem,
    taskId = taskId,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = createdAt.toEpochMilli(),
)

internal fun linkRow(
    systemEventId: Long,
    systemCalendarId: Long = 1L,
    title: String = "Kickoff",
    startsAt: Instant = at(2026, 3, 6, 11),
    endsAt: Instant? = null,
    taskId: Long? = null,
    projectId: Long? = null,
    meetingId: Long? = null,
): CalendarLinkEntity = CalendarLinkEntity(
    systemEventId = systemEventId,
    systemCalendarId = systemCalendarId,
    title = title,
    startsAtEpochMillis = startsAt.toEpochMilli(),
    endsAtEpochMillis = endsAt?.toEpochMilli(),
    taskId = taskId,
    projectId = projectId,
    meetingId = meetingId,
    createdAtEpochMillis = NOW.toEpochMilli(),
    updatedAtEpochMillis = NOW.toEpochMilli(),
)
