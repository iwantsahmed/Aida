package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The digits of an amount, shared by both phrasebooks.
 *
 * The figure lives outside either language because it is the one part of an amount that must not
 * differ between them: a payment Abu hears in Bengali and reads back in English is the same
 * payment, and only the numerals and the word for the currency change around it.
 *
 * It is built from the [BigDecimal] itself — never through a `Double`, which AGENTS §8 forbids
 * anywhere in the life of an amount including here on the way to the screen, and never through a
 * locale-aware `NumberFormat`, which would change the separators with the phone's language.
 */
internal fun moneyFigure(money: Money): String {
    val magnitude = money.amount.abs().stripTrailingZeros()
    val digits = fractionDigits(magnitude, money.currency.defaultFractionDigits)
    val plain = magnitude.setScale(digits, RoundingMode.UNNECESSARY).toPlainString()
    val point = plain.indexOf('.')
    val whole = if (point < 0) plain else plain.substring(0, point)
    val fraction = if (point < 0) "" else plain.substring(point)
    val grouped = group(whole, money.currency.currencyCode in LAKH_GROUPED)
    return if (money.amount.signum() < 0) "-$grouped$fraction" else "$grouped$fraction"
}

/**
 * A whole number of taka is written without a decimal point; 1234.56 keeps both paisa. Trailing
 * zeros on an exact amount suggest a precision Mira was not given, and `.00` on every figure
 * makes a spoken total longer for nothing.
 *
 * Never narrower than what the value actually holds, so widening to this scale is always exact.
 */
private fun fractionDigits(magnitude: BigDecimal, currencyDigits: Int): Int =
    if (magnitude.scale() <= 0) 0 else maxOf(magnitude.scale(), currencyDigits.coerceAtLeast(0))

/**
 * Abu reads and writes taka the way Bangladesh does — 1,50,000 is one and a half lakh at a
 * glance, where 150,000 has to be counted. Currencies outside that convention keep thousands.
 */
private fun group(whole: String, lakhs: Boolean): String {
    if (whole.length <= 3) return whole
    val parts = ArrayDeque<String>()
    var rest = whole
    if (lakhs) {
        parts.addFirst(rest.takeLast(3))
        rest = rest.dropLast(3)
        while (rest.length > 2) {
            parts.addFirst(rest.takeLast(2))
            rest = rest.dropLast(2)
        }
    } else {
        while (rest.length > 3) {
            parts.addFirst(rest.takeLast(3))
            rest = rest.dropLast(3)
        }
    }
    parts.addFirst(rest)
    return parts.joinToString(",")
}

/** The currencies written in lakhs and crores rather than in thousands. */
private val LAKH_GROUPED = setOf("BDT", "INR", "PKR", "NPR", "LKR")
