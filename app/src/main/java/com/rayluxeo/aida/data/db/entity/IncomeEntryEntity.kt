package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Money that came in.
 *
 * Distinct from [PaymentEntity] on purpose: a payment settles an invoice, while an income entry
 * is anything Abu earned — including work that was never invoiced. When a payment does produce
 * one, [paymentId] links them so the same taka is not counted twice.
 */
@Entity(
    tableName = "income_entries",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["projectId"]),
        Index(value = ["paymentId"]),
        Index(value = ["receivedAtEpochMillis"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class IncomeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val source: String,
    val description: String?,
    val amountMinor: Long,
    val currencyCode: String,
    val receivedAtEpochMillis: Long,
    val clientId: Long?,
    val projectId: Long?,
    val paymentId: Long?,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
