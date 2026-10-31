package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * Money that went out.
 *
 * Deleting is soft, because an expense that has already been counted against a budget or billed
 * on to a client must be recoverable as the same row: a re-inserted copy would take a new id and
 * the receipt file pointing at the old one would stop resolving.
 */
data class Expense(
    val id: Long = 0L,
    val description: String,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val amount: Money,
    val spentAt: Instant,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val receiptFileId: Long? = null,
    /** True when this is to be passed on to the client rather than absorbed. */
    val isBillable: Boolean = false,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
