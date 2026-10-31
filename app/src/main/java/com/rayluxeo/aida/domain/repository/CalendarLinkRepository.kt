package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.CalendarLink
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Which system calendar events correspond to which Mira records.
 *
 * Nothing here reads the calendar or asks for permission to. This is the storage side of the
 * pairing only: the provider remains the source of truth for what is scheduled, and these rows
 * are what lets Mira recognise an event again after a sync has changed it.
 */
interface CalendarLinkRepository {

    fun observeUpcoming(): Flow<List<CalendarLink>>

    /** [to] is exclusive, matching the meeting window so the two agree about midnight. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<CalendarLink>>

    /** Links whose task, project and meeting have all gone, and which now point at nothing. */
    fun observeStale(): Flow<List<CalendarLink>>

    /** Null is the ordinary answer for an event Mira has never linked, not a failure. */
    suspend fun bySystemEventId(systemEventId: Long): AppResult<CalendarLink?>

    suspend fun forMeeting(meetingId: Long): AppResult<List<CalendarLink>>

    suspend fun forTask(taskId: Long): AppResult<List<CalendarLink>>

    suspend fun forProject(projectId: Long): AppResult<List<CalendarLink>>

    /**
     * Records the pairing. One system event links to at most one Mira record, so linking an
     * event that is already linked fails with [com.rayluxeo.aida.core.common.MiraErrorCode.VALIDATION]
     * rather than replacing what is there.
     */
    suspend fun link(link: CalendarLink): AppResult<CalendarLink>

    /** Re-targets a link, or refreshes the copy of the event details a sync has changed. */
    suspend fun update(link: CalendarLink): AppResult<CalendarLink>

    suspend fun unlink(id: Long): AppResult<Unit>

    /** Removes every stale link and reports how many went. */
    suspend fun purgeStale(): AppResult<Int>
}
