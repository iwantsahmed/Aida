package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.AlarmRecord
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The ledger of what Mira has asked the operating system to do.
 *
 * This is not a second reminders table. A reminder is Abu's intent and can be edited or deleted;
 * a record here is what actually happened to an OS alarm, and it is only ever appended to and
 * closed. Nothing deletes from it, which is what lets Mira explain a reminder that never fired.
 */
interface AlarmRecordRepository {

    /** The alarms the OS is still expected to deliver, soonest first. */
    fun observeScheduled(): Flow<List<AlarmRecord>>

    suspend fun byId(id: Long): AppResult<AlarmRecord>

    /**
     * The alarm a PendingIntent request code currently belongs to, or null when nothing is
     * scheduled under it. This is how a scheduled alarm is found again in order to cancel it.
     */
    suspend fun scheduledByRequestCode(requestCode: Int): AppResult<AlarmRecord?>

    /** Everything ever scheduled for one reminder, newest first. */
    suspend fun forReminder(reminderId: Long): AppResult<List<AlarmRecord>>

    /**
     * Writes down that an alarm has been handed to the OS. [isExact] is false when the OS refused
     * an exact alarm and Mira settled for a window, which is the difference between "late" and
     * "broken" when Abu asks afterwards.
     */
    suspend fun record(
        reminderId: Long?,
        label: String,
        triggerAt: Instant,
        isExact: Boolean,
        requestCode: Int,
    ): AppResult<AlarmRecord>

    /** Records that the alarm went off at [firedAt]. */
    suspend fun markFired(id: Long, firedAt: Instant): AppResult<AlarmRecord>

    /** Records that the alarm's moment passed without it being delivered. */
    suspend fun markMissed(id: Long): AppResult<AlarmRecord>

    /**
     * Records that the alarm was called off before it fired. An alarm that has already fired
     * cannot be cancelled after the fact and the attempt is reported as a failure.
     */
    suspend fun cancel(id: Long): AppResult<AlarmRecord>
}
