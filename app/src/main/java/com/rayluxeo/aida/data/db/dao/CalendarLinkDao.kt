package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.CalendarLinkEntity
import kotlinx.coroutines.flow.Flow

/**
 * The join between the phone's calendar and Mira's own records.
 *
 * There is no soft delete here and no `deletedAtEpochMillis` column: a link is not a record of
 * Abu's, it is a statement that two ids refer to the same thing, and once that stops being true
 * there is nothing to keep. Unlinking is a real delete.
 *
 * `systemEventId` is unique, so linking an event that is already linked is refused rather than
 * left to the constraint: the check and the insert share a transaction so the answer is the same
 * one the insert would have got.
 */
@Dao
abstract class CalendarLinkDao {

    @Query(
        """
        SELECT * FROM calendar_links
        WHERE startsAtEpochMillis >= :fromEpochMillis
        ORDER BY startsAtEpochMillis ASC, id ASC
        """
    )
    abstract fun observeUpcoming(fromEpochMillis: Long): Flow<List<CalendarLinkEntity>>

    /** [toEpochMillis] is exclusive, matching the meeting window so the two agree about midnight. */
    @Query(
        """
        SELECT * FROM calendar_links
        WHERE startsAtEpochMillis >= :fromEpochMillis AND startsAtEpochMillis < :toEpochMillis
        ORDER BY startsAtEpochMillis ASC, id ASC
        """
    )
    abstract fun observeBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<CalendarLinkEntity>>

    /**
     * Links with nothing left on Mira's side. All three local columns are `SET NULL`, so deleting
     * the last task, project or meeting empties the row instead of removing it, and these are
     * what a cleanup pass has to find.
     */
    @Query(
        """
        SELECT * FROM calendar_links
        WHERE taskId IS NULL AND projectId IS NULL AND meetingId IS NULL
        ORDER BY startsAtEpochMillis ASC, id ASC
        """
    )
    abstract fun observeStale(): Flow<List<CalendarLinkEntity>>

    @Query("SELECT * FROM calendar_links WHERE id = :id")
    abstract suspend fun findById(id: Long): CalendarLinkEntity?

    @Query("SELECT * FROM calendar_links WHERE systemEventId = :systemEventId")
    abstract suspend fun findBySystemEventId(systemEventId: Long): CalendarLinkEntity?

    @Query("SELECT * FROM calendar_links WHERE meetingId = :meetingId ORDER BY startsAtEpochMillis ASC, id ASC")
    abstract suspend fun linksForMeeting(meetingId: Long): List<CalendarLinkEntity>

    @Query("SELECT * FROM calendar_links WHERE taskId = :taskId ORDER BY startsAtEpochMillis ASC, id ASC")
    abstract suspend fun linksForTask(taskId: Long): List<CalendarLinkEntity>

    @Query("SELECT * FROM calendar_links WHERE projectId = :projectId ORDER BY startsAtEpochMillis ASC, id ASC")
    abstract suspend fun linksForProject(projectId: Long): List<CalendarLinkEntity>

    @Insert
    abstract suspend fun insertRow(link: CalendarLinkEntity): Long

    @Update
    abstract suspend fun update(link: CalendarLinkEntity): Int

    @Query("DELETE FROM calendar_links WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM calendar_links WHERE taskId IS NULL AND projectId IS NULL AND meetingId IS NULL")
    abstract suspend fun deleteStale(): Int

    /**
     * Null when this system event is already linked. Refusing here rather than letting the unique
     * index throw keeps a second link attempt — a re-run of a calendar sync, say — an ordinary
     * answer instead of a storage failure.
     */
    @Transaction
    open suspend fun linkIfAbsent(link: CalendarLinkEntity): Long? {
        if (findBySystemEventId(link.systemEventId) != null) return null
        return insertRow(link)
    }
}
