package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line of an invoice.
 *
 * Quantity is stored at three decimal places as a whole number of thousandths, and the tax rate
 * in basis points, so "2.5 hours at 15% tax" round-trips exactly. [lineTotalMinor] is stored
 * rather than recomputed on read for the same reason the invoice totals are: the line has to
 * keep saying what it said when it was sent.
 *
 * `CASCADE` is safe: a line has no meaning without its invoice and nothing else points at it.
 */
@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["invoiceId"])],
)
data class InvoiceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val invoiceId: Long,
    val description: String,
    /** Thousandths: 2500 is 2.5. */
    val quantityMilli: Long,
    val unitPriceMinor: Long,
    /** Basis points: 1500 is 15.00 percent. */
    val taxRateBasisPoints: Int,
    val lineTotalMinor: Long,
    val position: Int,
)
