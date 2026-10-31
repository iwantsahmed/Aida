package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Recurring charges.
 *
 * Advancing a run of overdue subscriptions is one transaction: a sweep that moved half the rows
 * and then failed would leave Abu told about some charges twice and others not at all, and the
 * next sweep would have no way to tell which was which.
 *
 * The new date is computed in Kotlin and handed in, because a month is calendar arithmetic —
 * the 31st of January becomes the last day of February — and SQLite's date functions cannot be
 * trusted to agree with `java.time` about that.
 */
@Dao
abstract class SubscriptionDao {

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE deletedAtEpochMillis IS NULL AND isActive = 1
        ORDER BY nextChargeOnEpochDay ASC, id ASC
        """
    )
    abstract fun observeActive(): Flow<List<SubscriptionEntity>>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY isActive DESC, nextChargeOnEpochDay ASC, id ASC
        """
    )
    abstract fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE deletedAtEpochMillis IS NULL AND isActive = 1
          AND nextChargeOnEpochDay <= :onEpochDay
        ORDER BY nextChargeOnEpochDay ASC, id ASC
        """
    )
    abstract suspend fun listDueOnOrBefore(onEpochDay: Long): List<SubscriptionEntity>

    /** Finds the row whatever its state, so a soft-deleted subscription can still be restored. */
    @Query("SELECT * FROM subscriptions WHERE id = :id")
    abstract suspend fun findById(id: Long): SubscriptionEntity?

    @Insert
    abstract suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    abstract suspend fun update(subscription: SubscriptionEntity): Int

    @Query(
        """
        UPDATE subscriptions
        SET nextChargeOnEpochDay = :nextChargeOnEpochDay, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id
        """
    )
    abstract suspend fun setNextCharge(id: Long, nextChargeOnEpochDay: Long, atEpochMillis: Long): Int

    @Query("UPDATE subscriptions SET isActive = :active, updatedAtEpochMillis = :atEpochMillis WHERE id = :id")
    abstract suspend fun setActive(id: Long, active: Boolean, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE subscriptions
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE subscriptions
        SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, atEpochMillis: Long): Int

    /**
     * Strictly in the past, so a charge falling today stays put and is still shown to Abu as due
     * today. Stepping it forward the moment the day opened would mean he was never told about it.
     */
    @Query(
        """
        SELECT * FROM subscriptions
        WHERE deletedAtEpochMillis IS NULL AND isActive = 1
          AND nextChargeOnEpochDay < :todayEpochDay
        ORDER BY nextChargeOnEpochDay ASC, id ASC
        """
    )
    protected abstract suspend fun listOverdue(todayEpochDay: Long): List<SubscriptionEntity>

    /**
     * Moves every charge date that has gone by onto its next occurrence, in one transaction.
     *
     * [nextChargeOn] does the calendar arithmetic and must return a day strictly after the one
     * the row already holds; a row it cannot move is left alone rather than rewritten with the
     * same value, so the returned count is the number of subscriptions that genuinely advanced.
     */
    @Transaction
    open suspend fun advanceOverdue(
        todayEpochDay: Long,
        atEpochMillis: Long,
        nextChargeOn: (SubscriptionEntity) -> Long,
    ): Int {
        var advanced = 0
        for (subscription in listOverdue(todayEpochDay)) {
            val next = nextChargeOn(subscription)
            if (next > subscription.nextChargeOnEpochDay) {
                advanced += setNextCharge(subscription.id, next, atEpochMillis)
            }
        }
        return advanced
    }
}
