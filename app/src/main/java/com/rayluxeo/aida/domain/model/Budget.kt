package com.rayluxeo.aida.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A ceiling Abu set for a stretch of time.
 *
 * The period is a pair of [LocalDate]s with no time of day, so a budget can cover any span — a
 * month, a quarter, one project's run — and so "the 3rd" cannot drift a day when the phone
 * changes timezone. Both ends are inclusive: a budget ending on the 31st counts the 31st.
 *
 * A null [category] means the budget covers every category rather than none.
 */
data class Budget(
    val id: Long = 0L,
    val name: String,
    val category: ExpenseCategory? = null,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val limit: Money,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun covers(date: LocalDate): Boolean = !date.isBefore(periodStart) && !date.isAfter(periodEnd)
}

/**
 * A budget beside what has actually been spent against it.
 *
 * [spent] is summed in SQL over whole minor units, so a hundred expense rows total to the same
 * figure however they are ordered. [remaining] goes negative rather than clamping at zero —
 * Abu needs to know by how much he is over, not merely that he is.
 */
data class BudgetProgress(
    val budget: Budget,
    val spent: Money,
) {
    val remaining: Money get() = budget.limit - spent

    val isOverspent: Boolean get() = spent.amount > budget.limit.amount
}
