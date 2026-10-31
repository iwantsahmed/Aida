package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.CalendarEvent
import com.rayluxeo.aida.domain.model.WritableCalendar
import java.time.Instant

/**
 * Read and write access to the phone's own calendars.
 *
 * Mira does not keep a second copy of the schedule; the system provider stays the source of
 * truth. Writing needs a calendar to write into, and a device can have several — [writableCalendars]
 * is how one is chosen, and [defaultCalendarId] picks the sensible one when Abu has not said.
 */
interface CalendarRepository {

    /** False when the calendar permissions have not been granted. Never touches the provider. */
    fun canRead(): Boolean

    fun canWrite(): Boolean

    suspend fun writableCalendars(): AppResult<List<WritableCalendar>>

    /** The primary calendar, or the first writable one. Null when there is nowhere to write. */
    suspend fun defaultCalendarId(): AppResult<Long?>

    suspend fun eventsBetween(from: Instant, to: Instant): AppResult<List<CalendarEvent>>

    /** Returns the event with the system id Android assigned it. */
    suspend fun createEvent(event: CalendarEvent): AppResult<CalendarEvent>

    /** Updates an existing event in the calendar. */
    suspend fun updateEvent(event: CalendarEvent): AppResult<CalendarEvent>

    /** Removes an event by its system event ID. */
    suspend fun deleteEvent(systemEventId: Long): AppResult<Unit>

    /** Searches events matching [query] within optional [from] and [to] range. */
    suspend fun searchEvents(
        query: String,
        from: Instant? = null,
        to: Instant? = null,
    ): AppResult<List<CalendarEvent>>
}
