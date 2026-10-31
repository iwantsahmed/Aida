package com.rayluxeo.aida.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * How money crosses the database boundary.
 *
 * Nothing monetary is ever a `Float` or a `Double`. On disk an amount is a whole number of a
 * currency's **minor units** — paisa for BDT, cents for USD — held in a `Long`, beside the
 * currency code of the record it belongs to. That representation is exact, it sorts and sums
 * correctly in SQL, and it survives a round trip without a rounding decision.
 *
 * The conversion is deliberately total and deterministic in both directions: a [Money] at its
 * currency's own scale maps to exactly one `Long`, and that `Long` maps back to exactly the same
 * [Money]. An amount carrying more decimal places than the currency has is a programming error
 * and is rejected rather than quietly rounded — Abu's invoice totals are not a place to lose a
 * paisa silently.
 */
object MoneyAmount {

    /**
     * Quantities are stored at three decimal places. Line quantities in practice are things like
     * 1, 2.5 or 0.25 hours, and thousandths are finer than any invoice needs while still being a
     * whole number on disk.
     */
    const val QUANTITY_SCALE = 3

    /**
     * Rates — tax, discount — are given as a percentage with two decimal places and stored in
     * basis points, so 15.00 percent is the integer 1500. Exact, and small enough for an `Int`.
     */
    const val PERCENT_SCALE = 2

    fun toMinorUnits(money: Money): Long {
        val digits = money.currency.defaultFractionDigits.coerceAtLeast(0)
        val scaled = money.amount.setScale(digits, RoundingMode.UNNECESSARY)
        return scaled.movePointRight(digits).longValueExact()
    }

    fun fromMinorUnits(minorUnits: Long, currencyCode: String): Money {
        val currency = Currency.getInstance(currencyCode)
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        return Money(BigDecimal.valueOf(minorUnits).movePointLeft(digits).setScale(digits), currency)
    }

    fun toStoredQuantity(quantity: BigDecimal): Long =
        quantity.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY).movePointRight(QUANTITY_SCALE).longValueExact()

    fun fromStoredQuantity(stored: Long): BigDecimal =
        BigDecimal.valueOf(stored).movePointLeft(QUANTITY_SCALE).setScale(QUANTITY_SCALE)

    /** [percent] is a percentage, not a fraction: 15.00 means fifteen percent and stores as 1500. */
    fun toBasisPoints(percent: BigDecimal): Int =
        percent.setScale(PERCENT_SCALE, RoundingMode.UNNECESSARY).movePointRight(PERCENT_SCALE).intValueExact()

    fun fromBasisPoints(basisPoints: Int): BigDecimal =
        BigDecimal.valueOf(basisPoints.toLong()).movePointLeft(PERCENT_SCALE).setScale(PERCENT_SCALE)
}
