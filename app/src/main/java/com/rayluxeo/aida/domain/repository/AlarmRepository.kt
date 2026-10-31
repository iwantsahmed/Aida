package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

/**
 * The alarms Abu set, which is not the same thing as [AlarmRecordRepository].
 *
 * That one is the ledger of what Mira asked the operating system to do and is only ever appended
 * to. This one holds the alarm itself — a time of day, the days it repeats on, a label and a
 * switch — and it is edited, switched off and deleted as Abu changes his mind.
 *
 * Nothing here arms anything. Every write is a plain store, so that a row and the alarm the OS is
 * holding can only be brought into agreement in one place: the use cases, which do both in order.
 */
interface AlarmRepository {

    /** Every alarm, earliest in the day first, switched-off ones included. */
    fun observeAll(): Flow<List<Alarm>>

    suspend fun byId(id: Long): AppResult<Alarm>

    /**
     * The alarms that should have something armed for them right now: the enabled ones, plus any
     * alarm carrying a snooze that has not yet run out.
     *
     * The snooze half is not redundant. A one-time alarm switches itself off when it rings, so an
     * alarm Abu snoozed at seven is already disabled by ten past — and without this it would be
     * the one alarm a reboot loses.
     */
    suspend fun enabledOrSnoozed(): AppResult<List<Alarm>>

    /** Stores a new alarm. [Alarm.id] is ignored; the saved alarm comes back carrying its id. */
    suspend fun create(alarm: Alarm): AppResult<Alarm>

    /** Replaces the stored row wholesale. Fails with NOT_FOUND when the alarm is gone. */
    suspend fun update(alarm: Alarm): AppResult<Alarm>

    suspend fun delete(id: Long): AppResult<Unit>
}
