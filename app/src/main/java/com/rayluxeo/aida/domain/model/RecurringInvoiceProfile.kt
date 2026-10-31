package com.rayluxeo.aida.domain.model

import java.time.LocalDate

/**
 * A schedule that automatically generates draft invoices for Abu to review and approve.
 *
 * Mira NEVER sends recurring invoices automatically: she creates a fresh draft in DRAFT status,
 * calculates its totals deterministically, and notifies Abu via an on-device reminder.
 */
data class RecurringInvoiceProfile(
    val id: String,
    val sourceInvoiceId: Long,
    val sourceInvoiceNumber: String,
    val clientId: Long,
    val cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
    val customDays: Int? = null,
    val nextRunDate: LocalDate,
    val active: Boolean = true,
)
