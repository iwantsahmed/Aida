package com.rayluxeo.aida.ai.nlu

import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** A number found in a sentence, and the half-open span of text it was written in. */
internal data class NumberMatch(
    val value: BigDecimal,
    val start: Int,
    val end: Int,
)

/** The public shape of a match: the value, what was claimed, and the sentence without it. */
internal fun NumberMatch.toParsedNumber(text: String): ParsedNumber = ParsedNumber(
    value = value,
    remainder = NumberText.removeSpans(text, listOf(start until end)),
    matched = text.substring(start, end),
)

/**
 * The magnitudes both number systems count in.
 *
 * English borrowed লাখ and কোটি from Bengali and did not change what they are worth, so the two
 * parsers read the same constants. Two hand-kept copies of a magnitude is exactly the defect
 * AGENTS.md §7.11 forbids, and a লাখ that disagreed with a lakh by a factor of ten would show up
 * as a wrong invoice rather than as a failing build.
 */
internal object Magnitude {
    val HUNDRED: BigDecimal = BigDecimal("100")
    val THOUSAND: BigDecimal = BigDecimal("1000")
    val LAKH: BigDecimal = BigDecimal("100000")
    val MILLION: BigDecimal = BigDecimal("1000000")
    val CRORE: BigDecimal = BigDecimal("10000000")
    val BILLION: BigDecimal = BigDecimal("1000000000")

    /**
     * Beyond a thousand trillion nothing is an amount Abu stated; it is a digit run that happened
     * to sit next to a scale word. Refusing is the safe direction — Mira asking again costs a
     * sentence, a silently invented quantity costs money.
     */
    val LARGEST_PLAUSIBLE: BigDecimal = BigDecimal("1000000000000000")
}

/**
 * One grammar, two vocabularies.
 *
 * Bengali and English count the same way: a multiplier, then a scale, and scales compound by
 * addition — "দুই লাখ পঁচিশ হাজার" and "two lakh twenty five thousand" are both 200000 + 25000.
 * Only the words differ, so the words are what is passed in and the arithmetic lives here once.
 *
 * Everything is [BigDecimal]. "1.5 lakh" is exactly 150000, which is the whole reason a Double
 * may not appear anywhere in this file.
 *
 * @param units words worth a plain number of their own — এক, পঞ্চাশ, twenty.
 * @param scales words that multiply what came before them — হাজার, লাখ, thousand, crore.
 * @param fractions single words that are not whole — দেড় is one and a half, আড়াই is two and a half.
 * @param steps words that adjust the number that follows them — সাড়ে adds a half, পৌনে takes a quarter.
 * @param connectors words that may sit between two parts of one number without ending it.
 * @param needScale units that only count as numbers when a scale word follows, because they are
 *   also ordinary English words and half of what Abu writes is English.
 */
internal class NumberGrammar(
    private val units: Map<String, BigDecimal>,
    private val scales: Map<String, BigDecimal>,
    private val fractions: Map<String, BigDecimal> = emptyMap(),
    private val steps: Map<String, BigDecimal> = emptyMap(),
    private val connectors: Set<String> = emptySet(),
    private val needScale: Set<String> = emptySet(),
) {

    /**
     * Every number expression in [text], left to right and non-overlapping.
     *
     * The list rather than the first hit, because "kal 10 tay 500 taka dao" holds two numbers and
     * only the caller — which can see the word "taka" — knows which one is the amount.
     */
    fun matches(text: String): List<NumberMatch> {
        val tokens = NumberText.tokenize(text)
        val found = mutableListOf<NumberMatch>()
        var index = 0
        while (index < tokens.size) {
            val scan = matchAt(tokens, index)
            if (scan == null) {
                index++
            } else {
                val (match, next) = scan
                // "25th", "1st", "2nd" name a day, not a quantity. A digit run beside an ordinal
                // suffix would otherwise reach the money parser as a bare number, and an ordinal due
                // date would book that many taka. The date parser reads ordinals on its own
                // (RelativeDateParser.parseDayNumber), so dropping them here loses no reading.
                if (!endsInOrdinalSuffix(tokens, next)) found += match
                index = next
            }
        }
        return found
    }

    /**
     * True when the digit run that just matched is immediately followed by a lone ordinal suffix —
     * the "25" of "25th" or of "২৫শে". The suffix has to be the WHOLE next word, so "25thing" and
     * "25 thousand" are untouched, but a space between the two is NOT required: the normalizer splits
     * a run of digits from the letters beside it, so "25th"/"২৫শে" reach every parser downstream
     * already as two tokens ("25" "th", "25" "শে"), and the glued form only survives when a caller
     * skips normalization. Catching both is what actually stops "invoice ahmed due the 25th" booking
     * 25 taka. Only a digit run qualifies — a spelled-out "twenty fifth" never reaches here as a
     * number.
     */
    private fun endsInOrdinalSuffix(tokens: List<NumberToken>, next: Int): Boolean {
        val digits = tokens.getOrNull(next - 1) ?: return false
        if (digits.kind != NumberTokenKind.DIGITS) return false
        val suffix = tokens.getOrNull(next) ?: return false
        return suffix.kind == NumberTokenKind.WORD && suffix.text.lowercase() in ORDINAL_SUFFIXES
    }

    private fun matchAt(tokens: List<NumberToken>, from: Int): Pair<NumberMatch, Int>? {
        var total = BigDecimal.ZERO
        var current: BigDecimal? = null
        var step: BigDecimal? = null
        var currentFromDigits = false
        var sawNumber = false
        var first = -1
        var last = -1
        var index = from

        scan@ while (index < tokens.size) {
            val token = tokens[index]
            val word = token.text.lowercase()

            if (token.kind == NumberTokenKind.SYMBOL || word in connectors) {
                if (!joins(tokens, index, last)) break@scan
                index++
                continue@scan
            }

            if (token.kind == NumberTokenKind.DIGITS) {
                // Two digit runs in a row are two numbers, not one: "500 500" is never 1000.
                if (current != null) break@scan
                val digits = NumberText.digitsToNumber(token.text) ?: break@scan
                current = digits + (step ?: BigDecimal.ZERO)
                step = null
                currentFromDigits = true
                sawNumber = true
            } else {
                val fraction = fractions[word]
                val increment = steps[word]
                val unit = units[word]
                val scale = scales[word]
                val glued = if (fraction == null && increment == null && unit == null && scale == null) {
                    splitCompound(word)
                } else {
                    null
                }

                when {
                    fraction != null -> {
                        if (current != null || step != null) break@scan
                        if (word in needScale && !scaleFollows(tokens, index)) break@scan
                        current = fraction
                        currentFromDigits = false
                        sawNumber = true
                    }

                    increment != null -> {
                        if (current != null || step != null) break@scan
                        step = increment
                    }

                    unit != null -> {
                        if (currentFromDigits) break@scan
                        if (word in needScale && !scaleFollows(tokens, index)) break@scan
                        val value = unit + (step ?: BigDecimal.ZERO)
                        val running = current
                        // "twenty five" is 25; "five twenty" is two numbers Abu ran together.
                        if (running != null && running <= value) break@scan
                        step = null
                        current = if (running == null) value else running + value
                        sawNumber = true
                    }

                    scale != null -> {
                        // A scale with nothing in front of it states no quantity. "হাজার টাকা"
                        // could be one thousand or many thousands, and guessing invents money.
                        val base = current ?: break@scan
                        if (step != null) break@scan
                        if (scale < Magnitude.THOUSAND) {
                            current = base * scale
                        } else {
                            total += base * scale
                            current = null
                        }
                        currentFromDigits = false
                        sawNumber = true
                    }

                    glued != null -> {
                        if (current != null || currentFromDigits) break@scan
                        val base = glued.first + (step ?: BigDecimal.ZERO)
                        step = null
                        if (glued.second < Magnitude.THOUSAND) {
                            current = base * glued.second
                        } else {
                            total += base * glued.second
                            current = null
                        }
                        sawNumber = true
                    }

                    else -> break@scan
                }
            }

            if (first < 0) first = index
            last = index
            index++
        }

        if (!sawNumber || last < 0) return null
        val value = NumberText.canonical(total + (current ?: BigDecimal.ZERO))
        if (value > Magnitude.LARGEST_PLAUSIBLE) return null
        return NumberMatch(value, tokens[first].start, tokens[last].end) to (last + 1)
    }

    /**
     * A hyphen or a connecting word stays inside a number only when a number word follows it, so
     * "twenty-five thousand" holds together while "500 and 300 taka" stays two numbers.
     */
    private fun joins(tokens: List<NumberToken>, index: Int, last: Int): Boolean {
        if (last < 0) return false
        val token = tokens[index]
        if (token.kind == NumberTokenKind.SYMBOL && token.text != "-") return false
        val next = tokens.getOrNull(index + 1) ?: return false
        if (next.kind != NumberTokenKind.WORD) return false
        val word = next.text.lowercase()
        return units.containsKey(word) || fractions.containsKey(word) || splitCompound(word) != null
    }

    private fun scaleFollows(tokens: List<NumberToken>, index: Int): Boolean {
        val next = tokens.getOrNull(index + 1) ?: return false
        return next.kind == NumberTokenKind.WORD && scales.containsKey(next.text.lowercase())
    }

    /** "পাঁচশ" is পাঁচ and শ written as one word. Both halves have to be known for the split to stand. */
    private fun splitCompound(word: String): Pair<BigDecimal, BigDecimal>? {
        if (units.containsKey(word) || scales.containsKey(word)) return null
        for ((spelling, scale) in compoundScales) {
            if (word.length <= spelling.length || !word.endsWith(spelling)) continue
            val unit = units[word.dropLast(spelling.length)] ?: continue
            return unit to scale
        }
        return null
    }

    /**
     * "50k" arrives as two tokens, so an abbreviation never needs splitting out of a longer word
     * — and splitting on one would read the Banglish "onek" ("many") as one thousand. A single
     * Latin letter is therefore never glued to a number. Bengali শ is a whole word and stays.
     */
    private val compoundScales: List<Pair<String, BigDecimal>> = scales
        .filterKeys { spelling -> spelling.length > 1 || spelling.none { it in 'a'..'z' } }
        .toList()
        .sortedByDescending { it.first.length }

    private companion object {
        /**
         * The suffixes that turn a digit run into a day rather than a quantity. Latin "st/nd/rd/th"
         * cover "25th"; the Bengali date ordinals "লা/রা/ঠা/শে" (১লা, ২রা, ৪ঠা, ২৫শে) and the general
         * "তম" (২৫তম) cover the Bengali side. The Bengali "ই" of "৫ই" is deliberately NOT here: it is
         * also the emphatic particle, so "৫ই টাকা" ("exactly 5 taka") must keep its amount — a
         * Bengali "৫ই"-style due date is the one ordinal this still reads as money, and the date
         * parser's own markers ("তারিখ", a month name) remain the way to pin it.
         */
        val ORDINAL_SUFFIXES = setOf("st", "nd", "rd", "th", "লা", "রা", "ঠা", "শে", "তম")
    }
}

/**
 * Reads numbers the way Bengali says them, in Bengali script and in the Latin spellings Abu uses
 * when his keyboard is in English.
 *
 * Three things here have no English equivalent as single words and are the reason this cannot be
 * done with digits alone: দেড় is one and a half, আড়াই is two and a half, and সাড়ে adds a half to
 * whatever follows it. The rest is the Bengali counting table — নব্বই is ninety, not nine tens —
 * which has to be listed because it is irregular.
 */
@Singleton
class BanglaNumberParser @Inject constructor() {

    fun parse(text: String): ParsedNumber? = grammar.matches(text).firstOrNull()?.toParsedNumber(text)

    internal fun matches(text: String): List<NumberMatch> = grammar.matches(text)

    private val grammar = NumberGrammar(
        units = UNITS,
        scales = SCALES,
        fractions = FRACTIONS,
        steps = STEPS,
        connectors = CONNECTORS,
        needScale = NEED_SCALE,
    )

    private companion object {

        fun words(vararg entries: Pair<Int, List<String>>): Map<String, BigDecimal> = buildMap {
            for ((value, spellings) in entries) {
                val amount = BigDecimal(value)
                for (spelling in spellings) put(spelling, amount)
            }
        }

        fun scaleWords(vararg entries: Pair<BigDecimal, List<String>>): Map<String, BigDecimal> = buildMap {
            for ((magnitude, spellings) in entries) {
                for (spelling in spellings) put(spelling, magnitude)
            }
        }

        /**
         * Bengali script for the whole table; Latin only where the spelling cannot be mistaken
         * for an English word. "at", "char", "sat" and "tin" are listed but held back by
         * [NEED_SCALE], because a sentence half in English says all four of them constantly.
         */
        val UNITS: Map<String, BigDecimal> = words(
            0 to listOf("শূন্য", "shunno", "shunyo"),
            1 to listOf("এক", "ek"),
            2 to listOf("দুই", "দু", "dui", "du"),
            3 to listOf("তিন", "tin"),
            4 to listOf("চার", "char"),
            5 to listOf("পাঁচ", "পাচ", "panch", "pach"),
            6 to listOf("ছয়", "choy", "chhoy"),
            7 to listOf("সাত", "sat", "saat"),
            8 to listOf("আট", "at", "aat"),
            9 to listOf("নয়", "noy", "noi"),
            10 to listOf("দশ", "dosh", "dos"),
            11 to listOf("এগারো", "এগার", "egaro"),
            12 to listOf("বারো", "বার", "baro"),
            13 to listOf("তেরো", "তের", "tero"),
            14 to listOf("চৌদ্দ", "choddo"),
            15 to listOf("পনেরো", "পনের", "ponero", "ponro"),
            16 to listOf("ষোলো", "ষোল", "sholo"),
            17 to listOf("সতেরো", "সতের", "sotero"),
            18 to listOf("আঠারো", "আঠার", "atharo"),
            19 to listOf("উনিশ", "ঊনিশ", "unish"),
            20 to listOf("বিশ", "কুড়ি", "bish", "kuri"),
            21 to listOf("একুশ", "ekush"),
            22 to listOf("বাইশ", "baish"),
            23 to listOf("তেইশ", "teish"),
            24 to listOf("চব্বিশ", "chobbish"),
            25 to listOf("পঁচিশ", "পচিশ", "pochish", "pachish"),
            26 to listOf("ছাব্বিশ", "chabbish"),
            27 to listOf("সাতাশ", "satash"),
            28 to listOf("আটাশ", "atash"),
            29 to listOf("ঊনত্রিশ", "উনত্রিশ", "unotrish"),
            30 to listOf("ত্রিশ", "তিরিশ", "trish", "tirish"),
            31 to listOf("একত্রিশ"),
            32 to listOf("বত্রিশ"),
            33 to listOf("তেত্রিশ"),
            34 to listOf("চৌত্রিশ"),
            35 to listOf("পঁয়ত্রিশ", "poytrish"),
            36 to listOf("ছত্রিশ"),
            37 to listOf("সাঁইত্রিশ"),
            38 to listOf("আটত্রিশ"),
            39 to listOf("ঊনচল্লিশ", "উনচল্লিশ"),
            40 to listOf("চল্লিশ", "chollish"),
            41 to listOf("একচল্লিশ"),
            42 to listOf("বিয়াল্লিশ"),
            43 to listOf("তেতাল্লিশ"),
            44 to listOf("চুয়াল্লিশ"),
            45 to listOf("পঁয়তাল্লিশ", "poytallish"),
            46 to listOf("ছেচল্লিশ"),
            47 to listOf("সাতচল্লিশ"),
            48 to listOf("আটচল্লিশ"),
            49 to listOf("ঊনপঞ্চাশ", "উনপঞ্চাশ"),
            50 to listOf("পঞ্চাশ", "ponchash", "ponchas"),
            51 to listOf("একান্ন"),
            52 to listOf("বায়ান্ন"),
            53 to listOf("তিপ্পান্ন"),
            54 to listOf("চুয়ান্ন"),
            55 to listOf("পঞ্চান্ন", "ponchanno"),
            56 to listOf("ছাপ্পান্ন"),
            57 to listOf("সাতান্ন"),
            58 to listOf("আটান্ন"),
            59 to listOf("ঊনষাট", "উনষাট"),
            60 to listOf("ষাট"),
            61 to listOf("একষট্টি"),
            62 to listOf("বাষট্টি"),
            63 to listOf("তেষট্টি"),
            64 to listOf("চৌষট্টি"),
            65 to listOf("পঁয়ষট্টি"),
            66 to listOf("ছেষট্টি"),
            67 to listOf("সাতষট্টি"),
            68 to listOf("আটষট্টি"),
            69 to listOf("ঊনসত্তর", "উনসত্তর"),
            70 to listOf("সত্তর", "sottor"),
            71 to listOf("একাত্তর"),
            72 to listOf("বাহাত্তর"),
            73 to listOf("তিয়াত্তর"),
            74 to listOf("চুয়াত্তর"),
            75 to listOf("পঁচাত্তর", "pochattor"),
            76 to listOf("ছিয়াত্তর"),
            77 to listOf("সাতাত্তর"),
            78 to listOf("আটাত্তর"),
            79 to listOf("ঊনআশি", "উনআশি"),
            80 to listOf("আশি", "ashi"),
            81 to listOf("একাশি"),
            82 to listOf("বিরাশি"),
            83 to listOf("তিরাশি"),
            84 to listOf("চুরাশি"),
            85 to listOf("পঁচাশি", "pochashi"),
            86 to listOf("ছিয়াশি"),
            87 to listOf("সাতাশি"),
            88 to listOf("অষ্টাশি", "আটাশি"),
            89 to listOf("ঊননব্বই", "উননব্বই"),
            90 to listOf("নব্বই", "nobboi", "nobbui"),
            91 to listOf("একানব্বই"),
            92 to listOf("বিরানব্বই"),
            93 to listOf("তিরানব্বই"),
            94 to listOf("চুরানব্বই"),
            95 to listOf("পঁচানব্বই"),
            96 to listOf("ছিয়ানব্বই"),
            97 to listOf("সাতানব্বই"),
            98 to listOf("আটানব্বই"),
            99 to listOf("নিরানব্বই", "niranobboi"),
        )

        /**
         * Each list ends with the canonical token [Vocabulary] folds that word to, so a sentence
         * reads the same whether it arrives raw from Abu or normalized by the pipeline.
         */
        val SCALES: Map<String, BigDecimal> = scaleWords(
            Magnitude.HUNDRED to listOf("শ", "শো", "শত", "sho", "shoto"),
            Magnitude.THOUSAND to listOf(
                "হাজার", "হাজারে", "হাজারের",
                "hazar", "hajar", "hazaar", "hajaar",
                CanonicalToken.THOUSAND,
            ),
            Magnitude.LAKH to listOf(
                "লাখ", "লাখে", "লাখের", "লক্ষ", "লক্ষে",
                "lakh", "lakhs", "lac", "lacs", "lokkho", "lokho",
            ),
            Magnitude.MILLION to listOf("মিলিয়ন", CanonicalToken.MILLION),
            Magnitude.CRORE to listOf(
                "কোটি", "কোটিতে", "কোটির",
                "koti", "kuti", "crore", "crores",
            ),
        )

        /** Bengali says half, one and a half and two and a half in one word; English cannot. */
        val FRACTIONS: Map<String, BigDecimal> = scaleWords(
            BigDecimal("0.5") to listOf("আধা", "আধ", "অর্ধেক", "adha", "adhek", "ordhek", CanonicalToken.HALF),
            BigDecimal("1.5") to listOf("দেড়", "der", "derh", "dedh", CanonicalToken.ONE_AND_HALF),
            BigDecimal("2.5") to listOf("আড়াই", "arai", "aarai", "araai", "adhai", CanonicalToken.TWO_AND_HALF),
        )

        /** These adjust whatever number comes next: সাড়ে তিন লাখ is three and a half lakh. */
        val STEPS: Map<String, BigDecimal> = scaleWords(
            BigDecimal("0.5") to listOf("সাড়ে", "sare", "saare", "sarhe"),
            BigDecimal("0.25") to listOf("সোয়া", "shoya", "sowa"),
            BigDecimal("-0.25") to listOf("পৌনে", "poune"),
        )

        val CONNECTORS: Set<String> = setOf("ও", "আর", "ar", "o")

        /**
         * "at", "char", "sat" and "tin" are ordinary English words and half of what Abu writes is
         * English; "der" is also the possessive plural ending he types as its own word. All of
         * them mean a quantity only when a scale word follows: "der lakh" counts, "der" does not.
         */
        val NEED_SCALE: Set<String> = setOf("at", "char", "sat", "saat", "tin", "der")
    }
}
