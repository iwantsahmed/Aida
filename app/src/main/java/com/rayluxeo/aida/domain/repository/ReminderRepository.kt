package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderKind
import kotlinx.coroutines.flow.Flow

/**
 * Everything Abu asked to be told about, whatever kind it is.
 *
 * One port rather than one per kind: the eight kinds differ in their wording, their links and the
 * buttons on their notification, not in how they are stored or scheduled. Splitting them would
 * mean eight copies of the same snooze.
 */
interface ReminderRepository {

    /**
     * Still to happen, soonest first — ordered by the moment each one is actually armed for, so a
     * snoozed reminder sits where Abu pushed it rather than where it was originally set.
     */
    fun observeUpcoming(): Flow<List<Reminder>>

    /** The scheduled reminders of one kind, for a screen that shows only invoices or birthdays. */
    fun observeByKind(kind: ReminderKind): Flow<List<Reminder>>

    /**
     * Every reminder of these kinds, in EVERY state.
     *
     * Deliberately not just the scheduled ones: a reminder that has already fired, and one Abu
     * cancelled, are both proof that Mira has said this once. The daily sweep reads them to avoid
     * raising the same invoice reminder every morning until it is paid.
     */
    suspend fun listOfKinds(kinds: Collection<ReminderKind>): AppResult<List<Reminder>>

    /**
     * Reads a reminder whatever its state, including a cancelled or a fired one, because a
     * notification action arrives after the reminder has already been marked fired.
     */
    suspend fun byId(id: Long): AppResult<Reminder>

    suspend fun create(reminder: Reminder): AppResult<Reminder>

    /**
     * Writes the whole row back. This is how a snooze, an advanced occurrence and a state change
     * all reach storage: they are one read-modify-write, so none of them can leave a reminder
     * half-moved.
     */
    suspend fun update(reminder: Reminder): AppResult<Reminder>

    suspend fun markFired(id: Long): AppResult<Unit>

    /** Ends the reminder, and with it the series if it repeats. */
    suspend fun cancel(id: Long): AppResult<Unit>

    suspend fun nextScheduled(): AppResult<Reminder?>

    /**
     * Re-arms every still-scheduled reminder and answers how many reached the OS.
     *
     * Used after a reboot, after an app upgrade, and whenever the exact-alarm permission changes
     * — the platform cancels every future exact alarm when that permission is withdrawn, so these
     * rows are the only record that those reminders ever existed.
     */
    suspend fun rescheduleAll(): AppResult<Int>
}
