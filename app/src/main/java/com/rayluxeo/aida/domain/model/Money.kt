package com.rayluxeo.aida.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * Money is never a Double and never comes from a language model.
 * All arithmetic is deterministic Kotlin on [BigDecimal].
 */
data class Money(
    val amount: BigDecimal,
    val currency: Currency = BDT,
) {
    init {
        require(amount.scale() <= MAX_SCALE) { "Money scale ${amount.scale()} exceeds $MAX_SCALE" }
    }

    operator fun plus(other: Money): Money = sameCurrency(other) { a, b -> a + b }
    operator fun minus(other: Money): Money = sameCurrency(other) { a, b -> a - b }

    operator fun times(factor: BigDecimal): Money =
        copy(amount = amount.multiply(factor).setScale(currency.minorUnitDigits(), RoundingMode.HALF_UP))

    private inline fun sameCurrency(other: Money, op: (BigDecimal, BigDecimal) -> BigDecimal): Money {
        require(currency == other.currency) {
            "Refusing to combine ${currency.currencyCode} with ${other.currency.currencyCode}"
        }
        return copy(amount = op(amount, other.amount))
    }

    companion object {
        const val MAX_SCALE = 6

        /**
         * `Currency.defaultFractionDigits` is -1 for the pseudo-currencies the JVM also accepts —
         * XXX, XAU, XDR and the rest. Passing that straight to `setScale` does not mean "no
         * decimals"; it rounds to the nearest TEN units, which would quietly multiply an amount
         * by ten. Zero is the honest reading, and it is what MoneyAmount already does on the way
         * to and from disk.
         */
        internal fun Currency.minorUnitDigits(): Int = defaultFractionDigits.coerceAtLeast(0)
        val BDT: Currency = Currency.getInstance("BDT")

        fun of(value: String, currency: Currency = BDT): Money =
            Money(BigDecimal(value).setScale(currency.minorUnitDigits(), RoundingMode.HALF_UP), currency)

        fun zero(currency: Currency = BDT): Money =
            Money(BigDecimal.ZERO.setScale(currency.minorUnitDigits()), currency)
    }
}
