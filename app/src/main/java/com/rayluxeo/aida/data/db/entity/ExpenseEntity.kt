package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.ExpenseCategory

/** Money that went out, optionally attached to the work it was spent on and to its receipt. */
@Entity(
    tableName = "expenses",
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
            entity = FileRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptFileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["projectId"]),
        Index(value = ["receiptFileId"]),
        Index(value = ["spentAtEpochMillis"]),
        Index(value = ["category"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val description: String,
    val category: ExpenseCategory,
    val amountMinor: Long,
    val currencyCode: String,
    val spentAtEpochMillis: Long,
    val clientId: Long?,
    val projectId: Long?,
    val receiptFileId: Long?,
    /** True when this is to be passed on to the client rather than absorbed. */
    val isBillable: Boolean,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
