package com.rayluxeo.aida.ai.nlu

import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads numbers the way Abu writes them with an English keyboard: digits, "50k", "50 thousand",
 * and the South Asian scales that English borrowed unchanged — "1.5 lakh", "1 crore".
 *
 * The borrowed scales are worth what [Magnitude] says they are worth, the same constants the
 * Bengali parser counts in, so a lakh cannot come out different depending on how it was spelled.
 * The multiplier is a [BigDecimal] for the same reason: 1.5 lakh is exactly 150000, and a Double
 * would make it 149999.99999999999 on some inputs and 150000 on others.
 */
@Singleton
class EnglishNumberParser @Inject constructor() {

    fun parse(text: String): ParsedNumber? = grammar.matches(text).firstOrNull()?.toParsedNumber(text)

    internal fun matches(text: String): List<NumberMatch> = grammar.matches(text)

    private val grammar = NumberGrammar(
        units = UNITS,
        scales = SCALES,
        fractions = FRACTIONS,
        connectors = CONNECTORS,
    )

    private companion object {

        fun words(vararg entries: Pair<Int, List<String>>): Map<String, BigDecimal> = buildMap {
            for ((value, spellings) in entries) {
                val amount = BigDecimal(value)
                for (spelling in spellings) put(spelling, amount)
            }
        }

        val UNITS: Map<String, BigDecimal> = words(
            0 to listOf("zero"),
            1 to listOf("one"),
            2 to listOf("two"),
            3 to listOf("three"),
            4 to listOf("four"),
            5 to listOf("five"),
            6 to listOf("six"),
            7 to listOf("seven"),
            8 to listOf("eight"),
            9 to listOf("nine"),
            10 to listOf("ten"),
            11 to listOf("eleven"),
            12 to listOf("twelve"),
            13 to listOf("thirteen"),
            14 to listOf("fourteen"),
            15 to listOf("fifteen"),
            16 to listOf("sixteen"),
            17 to listOf("seventeen"),
            18 to listOf("eighteen"),
            19 to listOf("nineteen"),
            20 to listOf("twenty"),
            30 to listOf("thirty"),
            40 to listOf("forty", "fourty"),
            50 to listOf("fifty"),
            60 to listOf("sixty"),
            70 to listOf("seventy"),
            80 to listOf("eighty"),
            90 to listOf("ninety"),
        )

        /**
         * "k" is here as a whole token only. It is never split out of a longer word — see
         * [NumberGrammar] — because the Banglish "onek" means "many" and not one thousand.
         */
        val SCALES: Map<String, BigDecimal> = buildMap {
            for (spelling in listOf("hundred", "hundreds")) put(spelling, Magnitude.HUNDRED)
            for (spelling in listOf("thousand", "thousands", "k")) put(spelling, Magnitude.THOUSAND)
            for (spelling in listOf("lakh", "lakhs", "lac", "lacs")) put(spelling, Magnitude.LAKH)
            for (spelling in listOf("million", "millions")) put(spelling, Magnitude.MILLION)
            for (spelling in listOf("crore", "crores")) put(spelling, Magnitude.CRORE)
            for (spelling in listOf("billion", "billions")) put(spelling, Magnitude.BILLION)
        }

        val FRACTIONS: Map<String, BigDecimal> = mapOf(CanonicalToken.HALF to BigDecimal("0.5"))

        /** Only ever between two parts of one number: "five hundred and fifty", never "500 and 300". */
        val CONNECTORS: Set<String> = setOf("and")
    }
}
