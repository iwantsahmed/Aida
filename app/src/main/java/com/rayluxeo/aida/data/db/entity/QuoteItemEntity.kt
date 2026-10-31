package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One line of a quote. Same exact representation as an invoice line, for the same reasons. */
@Entity(
    tableName = "quote_items",
    foreignKeys = [
        ForeignKey(
            entity = QuoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["quoteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["quoteId"])],
)
data class QuoteItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val quoteId: Long,
    val description: String,
    val quantityMilli: Long,
    val unitPriceMinor: Long,
    val taxRateBasisPoints: Int,
    val lineTotalMinor: Long,
    val position: Int,
)
