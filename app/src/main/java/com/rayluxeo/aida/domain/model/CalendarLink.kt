package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * The record that a system calendar event and a Mira record are the same thing.
 *
 * Mira does not copy the phone's calendar; the system provider stays the source of truth for what
 * is scheduled. [title], [startsAt] and [endsAt] are a deliberate local copy rather than
 * duplication to be removed: without them a link is a bare pair of ids, and nothing here can be
 * shown or reasoned about until calendar permission has been granted.
 */
data class CalendarLink(
    val id: Long = 0L,
    /** `CalendarContract.Events._ID` on this device. Unique — one event links to one record. */
    val systemEventId: Long,
    val systemCalendarId: Long,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant? = null,
    val taskId: Long? = null,
    val projectId: Long? = null,
    val meetingId: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /**
     * Nothing is left on Mira's side of the pairing.
     *
     * All three local links are `SET NULL`, so deleting the last of them empties this row instead
     * of removing it. What survives records a correspondence with a record that no longer exists,
     * which is what a cleanup pass is looking for.
     */
    val isStale: Boolean get() = taskId == null && projectId == null && meetingId == null
}
