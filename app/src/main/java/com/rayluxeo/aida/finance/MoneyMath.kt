package com.rayluxeo.aida.finance

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Money
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.util.Currency

/**
 * Deterministic money arithmetic over the [Money] model. Everything rounds HALF_UP at the
 * currency's own minor unit and nothing here ever touches a Double, so the same input always
 * produces the same taka and poisha.
 *
 * Operations that can be asked an impossible question — splitting into zero parts, summing
 * nothing, mixing currencies — return a failure instead of throwing, because those are ordinary
 * things Abu might say rather than programming mistakes.
 */
object MoneyMath {

    /** "2400 er 15%" in money terms. Multiplication only, so no rounding context is needed. */
    fun percentageOf(amount: Money, percent: BigDecimal): Money {
        val scale = amount.currency.minorScale()
        val share = amount.amount.multiply(percent.movePointLeft(PERCENT_POINTS))
        return Money(share.setScale(scale, RoundingMode.HALF_UP), amount.currency)
    }

    /** Fails rather than inventing a currency for an empty list, or silently mixing two of them. */
    fun sum(amounts: List<Money>): AppResult<Money> {
        val currency = amounts.firstOrNull()?.currency
            ?: return failure(MiraErrorCode.VALIDATION, "Nothing to add up")
        if (amounts.any { it.currency != currency }) {
            return failure(MiraErrorCode.VALIDATION, "Refusing to add amounts in different currencies")
        }
        val total = amounts.fold(BigDecimal.ZERO) { running, money -> running.add(money.amount) }
        return Money(total.atLeastScale(currency.minorScale()), currency).asSuccess()
    }

    /**
     * Splits [total] into [parts] shares that add back up to exactly [total]. The indivisible
     * remainder goes one minor unit at a time to the earliest shares, so three ways of 100.00
     * is 33.34 + 33.33 + 33.33 rather than three shares that quietly lose a poisha.
     */
    fun splitEvenly(total: Money, parts: Int): AppResult<List<Money>> {
        if (parts <= 0) return failure(MiraErrorCode.VALIDATION, "Cannot split into $parts parts")
        val scale = maxOf(total.amount.scale(), total.currency.minorScale())
        val units = total.amount.atLeastScale(scale).movePointRight(scale).toBigIntegerExact()
        val divisor = BigInteger.valueOf(parts.toLong())
        val base = units.divide(divisor)
        val remainder = units.subtract(base.multiply(divisor))
        // Integer division truncates toward zero, so a negative total leaves a negative remainder.
        val step = if (remainder.signum() < 0) BigInteger.ONE.negate() else BigInteger.ONE
        val extras = remainder.abs().toInt()
        return List(parts) { index ->
            val share = if (index < extras) base.add(step) else base
            Money(BigDecimal(share).movePointLeft(scale), total.currency)
        }.asSuccess()
    }

    /** VAT-style gross-up. A negative rate is rejected, which also keeps [removeTax]'s divisor above zero. */
    fun addTax(net: Money, ratePercent: BigDecimal): AppResult<Money> {
        if (ratePercent.signum() < 0) return failure(MiraErrorCode.VALIDATION, "A tax rate cannot be negative")
        val gross = net.amount.multiply(taxMultiplier(ratePercent))
        return Money(gross.setScale(net.currency.minorScale(), RoundingMode.HALF_UP), net.currency).asSuccess()
    }

    /** Recovers the pre-tax amount from a tax-inclusive one. The multiplier is at least 1, so this never divides by zero. */
    fun removeTax(gross: Money, ratePercent: BigDecimal): AppResult<Money> {
        if (ratePercent.signum() < 0) return failure(MiraErrorCode.VALIDATION, "A tax rate cannot be negative")
        val net = gross.amount.divide(taxMultiplier(ratePercent), MathContext.DECIMAL64)
        return Money(net.setScale(gross.currency.minorScale(), RoundingMode.HALF_UP), gross.currency).asSuccess()
    }

    private const val PERCENT_POINTS = 2

    private fun taxMultiplier(ratePercent: BigDecimal): BigDecimal =
        BigDecimal.ONE.add(ratePercent.movePointLeft(PERCENT_POINTS))

    /** Currencies with no minor unit report -1, which is not a scale BigDecimal can use. */
    private fun Currency.minorScale(): Int = defaultFractionDigits.coerceAtLeast(0)

    /** Raising a scale never rounds, so this cannot lose a poisha the way setScale otherwise might. */
    private fun BigDecimal.atLeastScale(scale: Int): BigDecimal =
        if (this.scale() < scale) setScale(scale) else this
}
