package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Abu himself, and the details that end up printed on an invoice.
 *
 * Exactly one row ever exists, at [SINGLETON_ID]. Mira is a single-person app with no accounts,
 * so a table with a fixed key is more honest than a users table that will never hold a second
 * name — and it keeps the business details queryable alongside the records that cite them,
 * rather than stranded in DataStore where an invoice cannot join to them.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val displayName: String,
    val businessName: String?,
    val email: String?,
    val phone: String?,
    val addressLine: String?,
    val taxId: String?,
    val defaultCurrencyCode: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
