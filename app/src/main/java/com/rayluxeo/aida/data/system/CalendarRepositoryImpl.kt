package com.rayluxeo.aida.data.system

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.CalendarEvent
import com.rayluxeo.aida.domain.model.WritableCalendar
import com.rayluxeo.aida.domain.repository.CalendarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * The phone's own calendars, over CalendarContract.
 *
 * The system provider stays the source of truth for what is scheduled; Mira keeps no second copy
 * of the schedule, so an event created here is the same row the calendar app and the account's
 * sync adapter already see.
 *
 * Reads go through `Instances` rather than `Events` on purpose: a weekly meeting is one Events row
 * and many instances, and Abu asking what is on tomorrow means the instance, not the rule.
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : CalendarRepository {

    override fun canRead(): Boolean = granted(Manifest.permission.READ_CALENDAR)

    /**
     * Writing needs the read as well, because the calendar an event goes into is chosen by reading
     * the list of them. WRITE_CALENDAR on its own would pass this check and then fail at the first
     * query, and telling Abu now is a better answer than failing halfway.
     */
    override fun canWrite(): Boolean =
        granted(Manifest.permission.READ_CALENDAR) && granted(Manifest.permission.WRITE_CALENDAR)

    override suspend fun writableCalendars(): AppResult<List<WritableCalendar>> {
        if (!canRead()) return failure(MiraErrorCode.PERMISSION_REQUIRED, "list writable calendars")
        return providerCall("list writable calendars", MiraErrorCode.UNKNOWN) {
            // The access level is an int constant, not user input, so it is compared inline: a
            // bound argument arrives as text and would lean on SQLite's affinity rules to match.
            val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
                "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND " +
                "(${CalendarContract.Calendars.DELETED} IS NULL OR " +
                "${CalendarContract.Calendars.DELETED} = 0)"
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.OWNER_ACCOUNT,
                    CalendarContract.Calendars.IS_PRIMARY,
                ),
                selection,
                null,
                null,
            )?.use(::readCalendars).orEmpty()
                .sortedWith(
                    compareByDescending<WritableCalendar> { it.isPrimary }
                        .thenBy { it.displayName.lowercase() },
                )
        }
    }

    override suspend fun defaultCalendarId(): AppResult<Long?> =
        writableCalendars().map { calendars ->
            calendars.firstOrNull { it.isPrimary }?.id ?: calendars.firstOrNull()?.id
        }

    override suspend fun eventsBetween(from: Instant, to: Instant): AppResult<List<CalendarEvent>> {
        if (!canRead()) return failure(MiraErrorCode.PERMISSION_REQUIRED, "read calendar events")
        if (!to.isAfter(from)) return AppResult.Success(emptyList())
        return providerCall("read calendar events", MiraErrorCode.UNKNOWN) {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .also { ContentUris.appendId(it, from.toEpochMilli()) }
                .also { ContentUris.appendId(it, to.toEpochMilli()) }
                .build()
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.EVENT_LOCATION,
                ),
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use(::readInstances).orEmpty()
        }
    }

    override suspend fun createEvent(event: CalendarEvent): AppResult<CalendarEvent> {
        if (!canWrite()) return failure(MiraErrorCode.PERMISSION_REQUIRED, "create calendar event")
        if (event.calendarId <= 0L) {
            return failure(MiraErrorCode.VALIDATION, "No calendar to write the event into")
        }
        val title = event.title.trim()
        if (title.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Calendar event has no title")
        val endsAt = event.endsAt ?: event.startsAt.plus(DEFAULT_DURATION)
        if (!endsAt.isAfter(event.startsAt)) {
            return failure(MiraErrorCode.VALIDATION, "Calendar event ends before it starts")
        }
        val written = providerCall("create calendar event", MiraErrorCode.STORAGE) {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, event.calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, event.startsAt.toEpochMilli())
                put(CalendarContract.Events.DTEND, endsAt.toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, clock.zone().id)
                event.location?.trim()?.takeIf(String::isNotEmpty)?.let {
                    put(CalendarContract.Events.EVENT_LOCATION, it)
                }
            }
            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val newId = eventUri?.let(ContentUris::parseId)?.takeIf { it > 0L }
            if (newId != null && event.reminderMinutes != null && event.reminderMinutes > 0) {
                try {
                    val reminderValues = ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, newId)
                        put(CalendarContract.Reminders.MINUTES, event.reminderMinutes)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                } catch (_: Exception) {
                    // Reminders are best-effort; event creation still succeeds
                }
            }
            newId
        }
        return when (written) {
            is AppResult.Failure -> written
            is AppResult.Success -> written.value
                ?.let { AppResult.Success(event.copy(systemId = it, title = title, endsAt = endsAt)) }
                ?: failure(MiraErrorCode.STORAGE, "Calendar provider returned no id for the event")
        }
    }

    override suspend fun updateEvent(event: CalendarEvent): AppResult<CalendarEvent> {
        if (!canWrite()) return failure(MiraErrorCode.PERMISSION_REQUIRED, "update calendar event")
        val systemId = event.systemId ?: return failure(MiraErrorCode.VALIDATION, "Event has no system id")
        val title = event.title.trim()
        if (title.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Calendar event has no title")
        val endsAt = event.endsAt ?: event.startsAt.plus(DEFAULT_DURATION)
        if (!endsAt.isAfter(event.startsAt)) {
            return failure(MiraErrorCode.VALIDATION, "Calendar event ends before it starts")
        }
        return providerCall("update calendar event", MiraErrorCode.STORAGE) {
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, event.startsAt.toEpochMilli())
                put(CalendarContract.Events.DTEND, endsAt.toEpochMilli())
                event.location?.trim()?.takeIf(String::isNotEmpty)?.let {
                    put(CalendarContract.Events.EVENT_LOCATION, it)
                }
            }
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, systemId)
            val rows = context.contentResolver.update(uri, values, null, null)
            if (rows > 0) {
                event.copy(title = title, endsAt = endsAt)
            } else {
                throw IllegalStateException("No calendar event updated for id $systemId")
            }
        }
    }

    override suspend fun deleteEvent(systemEventId: Long): AppResult<Unit> {
        if (!canWrite()) return failure(MiraErrorCode.PERMISSION_REQUIRED, "delete calendar event")
        if (systemEventId <= 0L) return failure(MiraErrorCode.VALIDATION, "Invalid system event id")
        return providerCall("delete calendar event", MiraErrorCode.STORAGE) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, systemEventId)
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows <= 0) {
                throw IllegalStateException("No calendar event deleted for id $systemEventId")
            }
            Unit
        }
    }

    override suspend fun searchEvents(
        query: String,
        from: Instant?,
        to: Instant?,
    ): AppResult<List<CalendarEvent>> {
        if (!canRead()) return failure(MiraErrorCode.PERMISSION_REQUIRED, "search calendar events")
        val term = query.trim()
        val start = from ?: clock.now().minus(Duration.ofDays(7))
        val end = to ?: clock.now().plus(Duration.ofDays(60))
        return eventsBetween(start, end).map { list ->
            if (term.isEmpty()) list
            else list.filter { it.title.contains(term, ignoreCase = true) }
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun readCalendars(cursor: Cursor): List<WritableCalendar> {
        val idColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
        val nameColumn =
            cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
        val accountColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
        val ownerColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT)
        val primaryColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
        val calendars = ArrayList<WritableCalendar>(cursor.count)
        while (cursor.moveToNext()) {
            val account = cursor.getString(accountColumn)?.trim().orEmpty()
            val owner = cursor.getString(ownerColumn)?.trim().orEmpty()
            val name = cursor.getString(nameColumn)?.trim().orEmpty()
            calendars += WritableCalendar(
                id = cursor.getLong(idColumn),
                displayName = name.ifEmpty { account },
                accountName = account,
                // IS_PRIMARY is a computed column and some providers leave it null. The platform's
                // own definition of primary is the owner being the account, so fall back to that.
                isPrimary = if (cursor.isNull(primaryColumn)) {
                    account.isNotEmpty() && account.equals(owner, ignoreCase = true)
                } else {
                    cursor.getInt(primaryColumn) == 1
                },
            )
        }
        return calendars
    }

    private fun readInstances(cursor: Cursor): List<CalendarEvent> {
        val idColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
        val calendarColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
        val titleColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
        val beginColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
        val endColumn = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
        val locationColumn =
            cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
        val events = ArrayList<CalendarEvent>(cursor.count)
        while (cursor.moveToNext()) {
            if (cursor.isNull(beginColumn)) continue
            events += CalendarEvent(
                systemId = cursor.getLong(idColumn),
                calendarId = cursor.getLong(calendarColumn),
                title = cursor.getString(titleColumn)?.trim().orEmpty(),
                startsAt = Instant.ofEpochMilli(cursor.getLong(beginColumn)),
                endsAt = if (cursor.isNull(endColumn)) {
                    null
                } else {
                    Instant.ofEpochMilli(cursor.getLong(endColumn))
                },
                location = cursor.getString(locationColumn)?.trim()?.takeIf(String::isNotEmpty),
            )
        }
        return events
    }

    /**
     * ContentResolver throws where the port returns values. A SecurityException means the grant
     * went away between the permission check and the query, which is a permission to ask for
     * rather than a fault; [code] separates a failed read from a refused write, because the two
     * are said differently to Abu.
     */
    private suspend fun <T> providerCall(
        detail: String,
        code: MiraErrorCode,
        block: () -> T,
    ): AppResult<T> = withContext(dispatchers.io) {
        try {
            AppResult.Success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (denied: SecurityException) {
            failure(MiraErrorCode.PERMISSION_REQUIRED, detail, denied)
        } catch (error: Exception) {
            failure(code, detail, error)
        }
    }

    private companion object {
        /**
         * CalendarContract refuses a non-recurring event with no end, so one Abu gave only a start
         * for is written as an hour. The written end is returned rather than dropped, so the reply
         * states the hour instead of implying the event is open-ended.
         */
        val DEFAULT_DURATION: Duration = Duration.ofHours(1)
    }
}
