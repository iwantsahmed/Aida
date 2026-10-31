package com.rayluxeo.aida.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * An exchange rate between two currencies.
 *
 * All rate arithmetic uses deterministic Kotlin [BigDecimal].
 * [isManual] indicates whether this rate was manually set by Abu or loaded from default offline rates.
 */
data class ExchangeRate(
    val baseCurrencyCode: String,
    val targetCurrencyCode: String,
    val rate: BigDecimal,
    val isManual: Boolean = true,
    val updatedAt: Instant,
) {
    val pairKey: String get() = "$baseCurrencyCode/$targetCurrencyCode"
}
