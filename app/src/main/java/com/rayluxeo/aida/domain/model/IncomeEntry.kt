package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * Something Abu earned.
 *
 * Deliberately not the same thing as a [Payment]: a payment settles an invoice, an income entry
 * is any money earned at all, including work that was never invoiced. Where a payment did
 * produce one, [paymentId] links the two so the same taka is never counted twice in a total.
 */
data class IncomeEntry(
    val id: Long = 0L,
    val source: String,
    val description: String? = null,
    val amount: Money,
    val receivedAt: Instant,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val paymentId: Long? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
