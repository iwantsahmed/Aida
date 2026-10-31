package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.ReminderPlace
import kotlinx.coroutines.flow.Flow

/**
 * The places Abu attached to his reminders.
 *
 * A coordinate is among the most sensitive things Mira holds, so this port has no lookup, no
 * geocoder and no notion of an address: a place is a number pair and whatever Abu called it. The
 * rows never leave the device and nothing here is exposed to Gemini.
 *
 * At most one place per reminder — `reminder_places.reminderId` is unique — which is why [save]
 * is keyed on the reminder rather than on the place's own id.
 */
interface ReminderPlaceRepository {

    fun observeAll(): Flow<List<ReminderPlace>>

    suspend fun byReminderId(reminderId: Long): AppResult<ReminderPlace?>

    /** Creates the reminder's place, or moves the one it already has. */
    suspend fun save(place: ReminderPlace): AppResult<ReminderPlace>

    /**
     * Records whether the operating system is holding this alert right now. Written by the
     * scheduler, because it is a statement about the platform rather than about Abu's intent, and
     * it is what lets Mira answer "so why did nothing happen" instead of guessing.
     */
    suspend fun setRegistered(id: Long, isRegistered: Boolean): AppResult<Unit>

    suspend fun delete(id: Long): AppResult<Unit>

    /**
     * The places whose reminder is still scheduled — the only ones worth re-registering after a
     * reboot, which drops every proximity alert the app held.
     */
    suspend fun pending(): AppResult<List<ReminderPlace>>

    /**
     * How many places the platform is holding, ignoring [placeId].
     *
     * The exclusion matters: arming a place Mira already watches replaces that registration
     * rather than adding one, so counting it would make her refuse a place she is already on.
     */
    suspend fun countRegisteredOtherThan(placeId: Long): AppResult<Int>
}
