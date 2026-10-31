package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.InvoiceStatus

/**
 * A bill Abu has issued.
 *
 * Every amount is a whole number of minor units of [currencyCode] — paisa, not a fraction of a
 * taka — because an invoice total is the one number in this app that must never be approximate.
 * The totals are stored rather than derived so that a sent invoice still reads exactly as it was
 * sent, even if a line is later corrected or a tax rate changes.
 *
 * The client link is `RESTRICT`: an invoice without a client is not a record Abu can act on, so
 * the delete is refused rather than allowed to leave one behind.
 */
@Entity(
    tableName = "invoices",
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
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["number"], unique = true),
        Index(value = ["clientId"]),
        Index(value = ["projectId"]),
        Index(value = ["status"]),
        Index(value = ["dueOnEpochDay"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    /** The human-facing number Abu quotes on the phone, such as "INV-2026-014". Unique. */
    val number: String,
    val clientId: Long,
    val projectId: Long?,
    val status: InvoiceStatus,
    val issuedOnEpochDay: Long,
    val dueOnEpochDay: Long,
    val currencyCode: String,
    val subtotalMinor: Long,
    val discountMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
    /** Kept in step with the payments that point at this invoice; never edited by hand. */
    val amountPaidMinor: Long,
    val notes: String?,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
