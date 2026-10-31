package com.rayluxeo.aida.domain.model

import java.time.Instant
import java.util.Currency

/**
 * Someone Abu works for.
 *
 * [uuid] is assigned once, by the repository, when the client is first stored. It is the identity
 * that survives leaving the phone — printed on an exported invoice, matched again on restore — so
 * nothing outside the repository may supply or change it, and a write that carries a different
 * one is ignored rather than honoured.
 *
 * Billing is one field rather than two because a rate with no currency is a number with no
 * meaning: see [ClientBilling].
 */
data class Client(
    val id: Long = 0L,
    /** Empty until the repository stores the client and assigns it. */
    val uuid: String = "",
    val name: String,
    val companyName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val addressLine: String? = null,
    val notes: String? = null,
    val billing: ClientBilling? = null,
    /** Archived clients stay in the record and out of the way; they are not deleted. */
    val isArchived: Boolean = false,
    /** Set while the client sits in the bin, so an undo restores this row rather than a copy. */
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * What Abu bills a client in.
 *
 * The currency is the part that stands on its own — "this one pays in USD" is a fact even before
 * a rate is agreed. The rate cannot: a bare number is not an amount of anything. So the rate hangs
 * off the currency here, and a stored rate with no currency beside it is read as no rate at all
 * rather than quietly becoming taka.
 */
data class ClientBilling(
    val currency: Currency,
    val defaultRate: Money? = null,
) {
    init {
        require(defaultRate == null || defaultRate.currency == currency) {
            "Default rate is in ${defaultRate?.currency?.currencyCode} but billing is in ${currency.currencyCode}"
        }
    }
}

/** Which clients a screen is asking for. Deleted clients are in none of them. */
enum class ClientFilter { ALL, ACTIVE, ARCHIVED }
