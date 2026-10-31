package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.PaymentMethod

/**
 * Money that actually arrived.
 *
 * Both links are `SET NULL`. A payment is a fact about Abu's bank balance: it happened whether or
 * not the invoice it was matched against still exists, so it must outlive both the invoice and
 * the client. Deleting either orphans the payment rather than erasing it.
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["invoiceId"]),
        Index(value = ["clientId"]),
        Index(value = ["receivedAtEpochMillis"]),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val invoiceId: Long?,
    val clientId: Long?,
    val amountMinor: Long,
    val currencyCode: String,
    val method: PaymentMethod,
    val receivedAtEpochMillis: Long,
    val reference: String?,
    val note: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
