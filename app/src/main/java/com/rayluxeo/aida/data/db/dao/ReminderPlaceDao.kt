package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.ReminderPlaceEntity
import com.rayluxeo.aida.domain.model.ReminderState
import kotlinx.coroutines.flow.Flow

/**
 * Where a reminder waits for Abu to arrive.
 *
 * An abstract class rather than an interface because [upsertForReminder] is a read followed by a
 * write and `reminderId` carries a unique index: two callers racing on the same reminder would
 * otherwise turn into a constraint violation rather than a second edit of the same place.
 *
 * Neither this table nor `reminders` carries a soft-delete column, so there is no
 * `deletedAtEpochMillis` filter to apply here.
 */
@Dao
abstract class ReminderPlaceDao {

    @Query("SELECT * FROM reminder_places ORDER BY id ASC")
    abstract fun observeAll(): Flow<List<ReminderPlaceEntity>>

    @Query("SELECT * FROM reminder_places WHERE id = :id")
    abstract suspend fun findById(id: Long): ReminderPlaceEntity?

    @Query("SELECT * FROM reminder_places WHERE reminderId = :reminderId")
    abstract suspend fun findByReminderId(reminderId: Long): ReminderPlaceEntity?

    /**
     * The places still worth registering with the operating system: a reminder that has already
     * fired or been cancelled keeps its row as the record of where Abu asked, but re-arming it
     * after a reboot would fire something he has already dealt with.
     */
    @Query(
        """
        SELECT p.* FROM reminder_places AS p
        JOIN reminders AS r ON r.id = p.reminderId
        WHERE r.state = :state
        ORDER BY p.id ASC
        """
    )
    abstract suspend fun listForReminderState(state: ReminderState): List<ReminderPlaceEntity>

    /**
     * [excludingId] is the place about to be armed. Re-registering one the platform already holds
     * replaces it rather than adding a second, so counting it would make Mira refuse a place she
     * is in fact already watching.
     */
    @Query("SELECT COUNT(*) FROM reminder_places WHERE isRegistered = 1 AND id != :excludingId")
    abstract suspend fun countRegisteredExcluding(excludingId: Long): Int

    @Insert
    abstract suspend fun insert(place: ReminderPlaceEntity): Long

    @Update
    abstract suspend fun update(place: ReminderPlaceEntity): Int

    @Query(
        """
        UPDATE reminder_places
        SET isRegistered = :isRegistered, updatedAtEpochMillis = :updatedAt
        WHERE id = :id
        """
    )
    abstract suspend fun setRegistered(id: Long, isRegistered: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM reminder_places WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    /**
     * Moving a place keeps the row it already had. The id is what the operating system's
     * registration is keyed on, so replacing the row would leave an alert armed under an id
     * nothing can cancel any more; [ReminderPlaceEntity.isRegistered] is left alone for the same
     * reason, because only the scheduler knows what the platform is holding.
     */
    @Transaction
    open suspend fun upsertForReminder(place: ReminderPlaceEntity): Long {
        val existing = findByReminderId(place.reminderId) ?: return insert(place)
        update(
            place.copy(
                id = existing.id,
                isRegistered = existing.isRegistered,
                createdAtEpochMillis = existing.createdAtEpochMillis,
            ),
        )
        return existing.id
    }
}
