package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.ExpenseCategory

/**
 * A ceiling Abu set for a stretch of time.
 *
 * The period is stored as epoch days rather than a month string, so a budget can cover any span
 * — a quarter, a single project's run — and so comparing an expense against it is one integer
 * range check rather than date-string parsing.
 */
@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["periodStartEpochDay", "periodEndEpochDay"]),
        Index(value = ["category"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** Null means the budget covers every category. */
    val category: ExpenseCategory?,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val limitMinor: Long,
    val currencyCode: String,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
