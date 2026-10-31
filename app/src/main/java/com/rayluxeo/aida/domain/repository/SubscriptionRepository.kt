package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Subscription
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Recurring charges.
 *
 * [advanceOverdueCharges] is the sweep that keeps the next-charge dates honest. It is one
 * transaction over every row it moves, and it steps only dates that are strictly in the past —
 * a charge falling today is still due today and stays where Abu can see it.
 */
interface SubscriptionRepository {

    fun observeActive(): Flow<List<Subscription>>

    fun observeAll(): Flow<List<Subscription>>
    /**
     * Reads a subscription **whatever its state, including one in the bin**.
     *
     * That is deliberate: restoring a binned record has to be able to read it first. It does mean
     * a caller showing the result must check `deletedAt` — every listing, search and recall path
     * already filters, so this is the one accessor where a binned row can come back.
     */
    suspend fun byId(id: Long): AppResult<Subscription>

    suspend fun dueOnOrBefore(date: LocalDate): AppResult<List<Subscription>>

    suspend fun create(subscription: Subscription): AppResult<Subscription>

    suspend fun update(subscription: Subscription): AppResult<Subscription>

    /**
     * Pausing rather than deleting: a paused subscription keeps its history and its next-charge
     * date, so resuming it does not mean typing the whole thing in again.
     */
    suspend fun setActive(id: Long, active: Boolean): AppResult<Subscription>

    /** Returns how many subscriptions actually moved. */
    suspend fun advanceOverdueCharges(): AppResult<Int>

    suspend fun delete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Subscription>
}
