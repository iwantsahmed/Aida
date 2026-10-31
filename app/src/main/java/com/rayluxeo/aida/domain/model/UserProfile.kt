package com.rayluxeo.aida.domain.model

import java.time.Instant
import java.util.Currency

/**
 * Abu himself, and the details that end up printed on an invoice.
 *
 * There is at most one of these — Mira has no accounts and never will — so the profile carries no
 * id: the storage layer owns the fixed key. Until Abu sets one there is no profile at all, and
 * the repository says so with a null rather than inventing an empty name.
 *
 * [defaultCurrency] is a [Currency] rather than a code so that an amount built from the profile
 * is already a well-formed [Money] and cannot pick up a currency that does not exist.
 */
data class UserProfile(
    val displayName: String,
    val businessName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val addressLine: String? = null,
    val taxId: String? = null,
    val defaultCurrency: Currency = Money.BDT,
    val createdAt: Instant,
    val updatedAt: Instant,
)
