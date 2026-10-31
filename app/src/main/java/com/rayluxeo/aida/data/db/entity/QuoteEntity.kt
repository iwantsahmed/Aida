package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.QuoteStatus

/**
 * A price Abu has offered, before it becomes a bill.
 *
 * [convertedInvoiceId] records the moment a quote was accepted and turned into an invoice. It is
 * `SET NULL` rather than `CASCADE`: voiding the invoice must leave the quote standing, because
 * the quote is evidence of what was agreed.
 */
@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["convertedInvoiceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["number"], unique = true),
        Index(value = ["clientId"]),
        Index(value = ["projectId"]),
        Index(value = ["convertedInvoiceId"]),
        Index(value = ["status"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    val number: String,
    val clientId: Long,
    val projectId: Long?,
    val status: QuoteStatus,
    val issuedOnEpochDay: Long,
    val validUntilEpochDay: Long?,
    val currencyCode: String,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    val convertedInvoiceId: Long?,
    val notes: String?,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
