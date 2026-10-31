package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.domain.model.Money
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the amount of money in a sentence, in whichever of the four languages it was written.
 *
 * Both number parsers are asked, because "১.৫ লাখ টাকা" and "1.5 lakh taka" are the same amount
 * and only one of them reads as Bengali. Where the two overlap, the longer reading wins: the
 * English parser stops at "2 lakh" in "2 lakh 25 hazar" and the Bengali one does not.
 *
 * Two honesty rules hold here, and both exist because this is money:
 *
 * 1. `currencyWasExplicit` is false unless Abu actually said a currency. A bare "500" becomes
 *    500 BDT because that is his default, not because he said so, and the caller may want to
 *    confirm before writing it down.
 * 2. An amount finer than its currency can express is refused rather than rounded, exactly as
 *    `MoneyAmount` refuses it at the database boundary. "500.555 taka" is not 500.56 — it is a
 *    sentence Mira did not understand.
 */
@Singleton
class CurrencyExpressionParser @Inject constructor(
    private val bangla: BanglaNumberParser,
    private val english: EnglishNumberParser,
) {

    fun parse(text: String, defaultCurrency: Currency = Money.BDT): ParsedMoney? {
        val numbers = merge(bangla.matches(text), english.matches(text))
        if (numbers.isEmpty()) return null

        val pairing = pairWithCurrency(text, numbers)
        val number = pairing?.number ?: numbers.first()
        val currency = pairing?.marker?.currency ?: defaultCurrency
        val digits = minorUnitDigits(currency)
        if (number.value.scale() > digits) return null

        val spans = listOfNotNull(
            number.start until number.end,
            pairing?.marker?.let { it.start until it.end },
        )
        return ParsedMoney(
            money = Money(number.value.setScale(digits), currency),
            remainder = NumberText.removeSpans(text, spans),
            matched = text.substring(spans.minOf { it.first }, spans.maxOf { it.last } + 1),
            currencyWasExplicit = pairing != null,
        )
    }

    /**
     * A currency word only claims the number it is standing next to. In "kal 10 tay 500 taka
     * dao" that rule is the whole difference between the amount and the hour, and where no
     * number is next to a currency word at all, nothing is claimed and the first number stands
     * as a bare quantity.
     */
    private fun pairWithCurrency(text: String, numbers: List<NumberMatch>): Pairing? {
        for (marker in markers(text)) {
            val before = numbers.lastOrNull {
                it.end <= marker.start && onlySeparators(text, it.end, marker.start)
            }
            if (before != null) return Pairing(before, marker)
            val after = numbers.firstOrNull {
                it.start >= marker.end && onlySeparators(text, marker.end, it.start)
            }
            if (after != null) return Pairing(after, marker)
        }
        return null
    }

    private fun markers(text: String): List<CurrencyMarker> = NumberText.tokenize(text).mapNotNull { token ->
        MARKERS[token.text.lowercase()]?.let { CurrencyMarker(it, token.start, token.end) }
    }

    private fun onlySeparators(text: String, from: Int, to: Int): Boolean =
        text.substring(from, to).none { it.isLetterOrDigit() }

    /** Overlapping readings of the same digits are one number; the longer reading is the truer one. */
    private fun merge(first: List<NumberMatch>, second: List<NumberMatch>): List<NumberMatch> {
        val ordered = (first + second).sortedWith(compareBy({ it.start }, { it.start - it.end }))
        val kept = mutableListOf<NumberMatch>()
        for (candidate in ordered) {
            val last = kept.lastOrNull()
            if (last != null && candidate.start < last.end) {
                if (candidate.end - candidate.start > last.end - last.start) kept[kept.lastIndex] = candidate
                continue
            }
            kept += candidate
        }
        return kept
    }

    /**
     * The pseudo-currencies the JVM also accepts report -1 minor digits, which is not a scale;
     * zero is the honest reading, and it is what [Money] and `MoneyAmount` both use.
     */
    private fun minorUnitDigits(currency: Currency): Int = currency.defaultFractionDigits.coerceAtLeast(0)

    private data class CurrencyMarker(val currency: Currency, val start: Int, val end: Int)

    private data class Pairing(val number: NumberMatch, val marker: CurrencyMarker)

    private companion object {

        /**
         * Only currencies Abu actually writes. A three-letter code is not matched generically on
         * purpose: "LAK" is the Lao kip and "ALL" the Albanian lek, and either would turn a lakh
         * or an ordinary English word into a currency.
         */
        val MARKERS: Map<String, Currency> = buildMap {
            currency(
                Money.BDT,
                "৳", "bdt", "taka", "takar", "takay", "tk", "tka", "tks",
                "টাকা", "টাকার", "টাকায়",
            )
            currency(Currency.getInstance("USD"), "$", "usd", "dollar", "dollars", "ডলার")
            currency(Currency.getInstance("EUR"), "€", "eur", "euro", "euros", "ইউরো")
            currency(Currency.getInstance("GBP"), "£", "gbp", "pound", "pounds", "পাউন্ড")
            currency(Currency.getInstance("INR"), "₹", "inr", "rupee", "rupees", "রুপি")
        }

        fun MutableMap<String, Currency>.currency(currency: Currency, vararg spellings: String) {
            for (spelling in spellings) put(spelling, currency)
        }
    }
}
