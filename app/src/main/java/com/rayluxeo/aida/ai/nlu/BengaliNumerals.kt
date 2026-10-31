package com.rayluxeo.aida.ai.nlu

import java.math.BigDecimal

/**
 * Bengali-Indic digits and ASCII digits are the same numbers written two ways.
 *
 * Everything downstream — the date parser, the arithmetic extractor, the intent matcher —
 * reasons in ASCII, so input is folded one way. Anything Mira shows or speaks back to Abu in
 * Bengali reads wrong with Latin digits, so the fold has to work the other way too.
 */
object BengaliNumerals {

    private const val BENGALI_ZERO = '০'
    private const val BENGALI_NINE = '৯'
    private const val ASCII_ZERO = '0'
    private const val ASCII_NINE = '9'

    fun isBengaliDigit(character: Char): Boolean = character in BENGALI_ZERO..BENGALI_NINE

    fun isAsciiDigit(character: Char): Boolean = character in ASCII_ZERO..ASCII_NINE

    /** Replaces ০১২৩৪৫৬৭৮৯ with 0123456789, leaving every other character untouched. */
    fun toAscii(text: String): String {
        if (text.none(::isBengaliDigit)) return text
        return buildString(text.length) {
            for (character in text) {
                append(if (isBengaliDigit(character)) toAscii(character) else character)
            }
        }
    }

    /** Replaces 0123456789 with ০১২৩৪৫৬৭৮৯, leaving every other character untouched. */
    fun toBengali(text: String): String {
        if (text.none(::isAsciiDigit)) return text
        return buildString(text.length) {
            for (character in text) {
                append(if (isAsciiDigit(character)) toBengali(character) else character)
            }
        }
    }

    fun toAscii(character: Char): Char =
        if (isBengaliDigit(character)) ASCII_ZERO + (character - BENGALI_ZERO) else character

    fun toBengali(character: Char): Char =
        if (isAsciiDigit(character)) BENGALI_ZERO + (character - ASCII_ZERO) else character
}

/** How a number scan sees a piece of text: a run of digits, a run of letters, or a lone symbol. */
internal enum class NumberTokenKind { DIGITS, WORD, SYMBOL }

/**
 * A token together with the span it occupies in the text it came from.
 *
 * The span is what lets a parser hand back a usable remainder. "500 taka khoroch" only yields
 * the description "khoroch" because the amount is cut out by position; cutting it out by string
 * replacement would also remove a second "500" further along the sentence.
 */
internal data class NumberToken(
    val kind: NumberTokenKind,
    val text: String,
    val start: Int,
    /** Exclusive, so the token is `text.substring(start, end)`. */
    val end: Int,
)

/**
 * The text work every number parser has to do before it can think about numbers at all.
 *
 * It sits beside [BengaliNumerals] because it is the same kind of thing — script-level
 * housekeeping that both the Bengali and the English number parsers build on, so that neither
 * of them grows its own idea of where a number starts and stops.
 */
internal object NumberText {

    /**
     * Nine digits is a hundred crore, larger than any amount Abu states in a sentence. A longer
     * run of digits is an identifier — a phone number, an NID, a bank account — and reading one
     * of those as a quantity is worse than not reading it at all.
     */
    const val MAX_DIGITS = 9

    /**
     * Splits text into digit runs, word runs and single symbols, each carrying its span.
     *
     * A digit run keeps an internal `.` or `,` only when another digit follows it, so "10.30am"
     * holds together while the full stop ending a sentence does not become part of the number.
     */
    fun tokenize(text: String): List<NumberToken> {
        val tokens = mutableListOf<NumberToken>()
        var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                character.isWhitespace() -> index++

                isDigit(character) -> {
                    val start = index
                    index = consumeDigits(text, index)
                    tokens += NumberToken(NumberTokenKind.DIGITS, text.substring(start, index), start, index)
                }

                isWordCharacter(character) -> {
                    val start = index
                    while (index < text.length && isWordCharacter(text[index])) index++
                    tokens += NumberToken(NumberTokenKind.WORD, text.substring(start, index), start, index)
                }

                else -> {
                    tokens += NumberToken(NumberTokenKind.SYMBOL, character.toString(), index, index + 1)
                    index++
                }
            }
        }
        return tokens
    }

    /**
     * A digit run as a number, or null when it is not one: two decimal points, a comma that is
     * not a thousands separator, or more digits than an amount ever has.
     */
    fun digitsToNumber(raw: String): BigDecimal? {
        val ascii = BengaliNumerals.toAscii(raw)
        val plain = when {
            GROUPED.matches(ascii) || GROUPED_SOUTH_ASIAN.matches(ascii) -> ascii.replace(",", "")
            ascii.contains(',') -> return null
            else -> ascii
        }
        if (!PLAIN.matches(plain)) return null
        if (plain.substringBefore('.').length > MAX_DIGITS) return null
        return canonical(BigDecimal(plain))
    }

    /**
     * The one form a value is allowed to be in once a parser is done with it.
     *
     * `1.5 lakh` multiplies out to 150000.0 and `2.25 lakh` to 225000.00, which are equal in
     * value to 150000 and 225000 but not equal as [BigDecimal]. Callers compare amounts, so the
     * trailing zeros come off here rather than at every call site.
     */
    fun canonical(value: BigDecimal): BigDecimal {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }

    /**
     * The text with [spans] cut out and the whitespace tidied up, which is what every parser
     * hands back as its remainder. Spans are half-open — `start until end` — and may be in any
     * order; they must not overlap.
     */
    fun removeSpans(text: String, spans: List<IntRange>): String {
        if (spans.isEmpty()) return collapse(text)
        val builder = StringBuilder(text.length)
        var cursor = 0
        for (span in spans.sortedBy { it.first }) {
            if (span.first > cursor) builder.append(text, cursor, span.first)
            builder.append(' ')
            cursor = maxOf(cursor, span.last + 1)
        }
        if (cursor < text.length) builder.append(text, cursor, text.length)
        return collapse(builder.toString())
    }

    private fun collapse(text: String): String = text.trim().replace(WHITESPACE, " ")

    private fun consumeDigits(text: String, from: Int): Int {
        var index = from
        while (index < text.length) {
            val character = text[index]
            if (isDigit(character)) {
                index++
                continue
            }
            val separates = (character == '.' || character == ',') &&
                index + 1 < text.length &&
                isDigit(text[index + 1])
            if (!separates) break
            index++
        }
        return index
    }

    private fun isDigit(character: Char): Boolean =
        BengaliNumerals.isAsciiDigit(character) || BengaliNumerals.isBengaliDigit(character)

    /**
     * Bengali vowel signs, the nukta and the hasanta are combining marks rather than letters,
     * and the joiners are format characters. A word boundary drawn on "letter or not" would cut
     * টাকা into pieces that mean nothing, so a mark continues the word it is attached to.
     *
     * The underscore is a word character because a normalized sentence contains canonical tokens
     * such as `one_and_half`, and splitting one into three words would lose the number.
     */
    private fun isWordCharacter(character: Char): Boolean =
        character.isLetter() ||
            character.category in MARKS ||
            character == '_' ||
            character == ZERO_WIDTH_NON_JOINER ||
            character == ZERO_WIDTH_JOINER

    private const val ZERO_WIDTH_NON_JOINER = '‌'
    private const val ZERO_WIDTH_JOINER = '‍'

    private val MARKS = setOf(
        CharCategory.NON_SPACING_MARK,
        CharCategory.COMBINING_SPACING_MARK,
        CharCategory.ENCLOSING_MARK,
    )

    private val GROUPED = Regex("""\d{1,3}(,\d{3})+(\.\d+)?""")

    /** Abu's keyboard and his bank both group in lakh: 1,25,000 is a hundred and a quarter thousand. */
    private val GROUPED_SOUTH_ASIAN = Regex("""\d{1,2}(,\d{2})+(,\d{3})(\.\d+)?""")
    private val PLAIN = Regex("""\d+(\.\d+)?""")
    private val WHITESPACE = Regex("""\s+""")
}
