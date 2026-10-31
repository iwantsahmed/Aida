package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * An event in the phone's own calendar.
 *
 * As with [Contact], Mira reads through rather than copying: the system provider stays the source
 * of truth for what is scheduled. `CalendarLink` in the database records only that a Mira record
 * and a system event correspond, so the connection survives a sync.
 */
data class CalendarEvent(
    /** CalendarContract.Events._ID, or null for one that has not been written yet. */
    val systemId: Long?,
    val calendarId: Long,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val location: String? = null,
    val reminderMinutes: Int? = 15,
)

/** A time slot on the schedule, bounded by [startsAt] and [endsAt]. */
data class TimeWindow(
    val startsAt: Instant,
    val endsAt: Instant,
)


/** A calendar Android will let Mira write to. */
data class WritableCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean,
)
