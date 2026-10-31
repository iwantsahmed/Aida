package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Someone Abu works for.
 *
 * Carries a [uuid] alongside the integer key. The `id` is what the database joins on — compact
 * and fast — while the uuid is what leaves the phone: printed on an exported invoice, written
 * into a backup, matched again on restore. An autoincrement key is only unique within one copy
 * of this database, so anything that may be read outside it needs the stable one too.
 */
@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["name"]),
        Index(value = ["isArchived"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    val name: String,
    val companyName: String?,
    val email: String?,
    val phone: String?,
    val addressLine: String?,
    val notes: String?,
    val defaultCurrencyCode: String?,
    /** Abu's usual rate for this client, in minor units of [defaultCurrencyCode]. */
    val defaultRateMinor: Long?,
    val isArchived: Boolean,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** External-content index so "rayluxeo" finds the client by company as readily as by name. */
@Entity(tableName = "client_fts")
@Fts4(contentEntity = ClientEntity::class)
data class ClientFtsEntity(
    val name: String,
    val companyName: String?,
    val notes: String?,
)
