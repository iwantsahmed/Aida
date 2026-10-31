package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.domain.nlu.NormalizedText
import com.rayluxeo.aida.domain.nlu.TextNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns whatever Abu typed or dictated into the one canonical form everything downstream reads:
 * lowercase, ASCII digits, Banglish spellings folded onto canonical tokens, punctuation that
 * carries no meaning dropped, whitespace collapsed.
 *
 * It composes rather than decides. [LanguageHintDetector] judges the language and the script,
 * [BanglishNormalizer] folds the words, and what is left here is the one thing neither of them
 * can do alone: splitting the raw text so that a name never becomes vocabulary by accident.
 * Tokens that look like proper nouns — capitalised, but not because a sentence started — are
 * exempt from the fuzzy fold, which is what keeps "Ahmed" out of the Bengali dictionary and in
 * the reminder title where he belongs.
 *
 * A Bengali word the table has no entry for stays in Bengali script rather than being
 * transliterated by guesswork. The parsers downstream read both scripts for that reason.
 */
@Singleton
class InputNormalizer @Inject constructor(
    private val hints: LanguageHintDetector,
    private val banglish: BanglishNormalizer,
) : TextNormalizer {

    override fun normalize(raw: String, mode: LanguageMode): NormalizedText {
        val scan = UtteranceScanner.scan(raw)
        val hint = hints.detect(raw, mode)
        return NormalizedText(
            raw = raw,
            normalized = banglish.foldTokens(scan.tokens, scan.protectedTokens)
                .joinToString(separator = " "),
            language = hint.language,
            script = hint.script,
        )
    }
}

/**
 * One pass over the raw utterance: its tokens, the ones that must not be fuzzily matched, and
 * the letter counts the script decision rests on.
 */
internal data class UtteranceScan(
    val tokens: List<String>,
    /** Capitalised mid-sentence words — names, as a rule. */
    val protectedTokens: Set<String>,
    val bengaliLetters: Int,
    val latinLetters: Int,
)

/**
 * Splits raw text the one way the whole pipeline agrees on.
 *
 * It is shared by [InputNormalizer] and [LanguageHintDetector] rather than written twice
 * because they must see the same words: a detector that tokenised "১০টায়" differently from the
 * normalizer would decide the language of a sentence that never reaches the parsers.
 */
internal object UtteranceScanner {

    /**
     * Runs of digits, Latin letters and Bengali letters are separate tokens, so "১০টায়" arrives
     * as "10" followed by the hour marker rather than as one unsplittable blob. Punctuation is
     * dropped unless it is load-bearing: a colon or a dot between digits, a percent sign on the
     * end of a number, or an arithmetic operator, all of which a later stage still needs.
     */
    fun scan(raw: String): UtteranceScan {
        val text = BengaliNumerals.toAscii(raw)
        val tokens = mutableListOf<String>()
        val protectedTokens = mutableSetOf<String>()
        var bengaliLetters = 0
        var latinLetters = 0
        var buffer = StringBuilder()
        var bufferKind = CharKind.NONE
        var bufferCapitalised = false
        var atSentenceStart = true

        fun flush() {
            if (buffer.isEmpty()) return
            val token = buffer.toString().lowercase()
            if (bufferKind == CharKind.LATIN && bufferCapitalised && !atSentenceStart && token.length > 1) {
                protectedTokens += token
            }
            tokens += token
            atSentenceStart = false
            buffer = StringBuilder()
            bufferKind = CharKind.NONE
            bufferCapitalised = false
        }

        for ((position, character) in text.withIndex()) {
            when (val kind = classify(character)) {
                CharKind.DIGIT, CharKind.LATIN, CharKind.BENGALI -> {
                    if (kind != bufferKind) flush()
                    if (buffer.isEmpty()) bufferCapitalised = character.isUpperCase()
                    buffer.append(character)
                    bufferKind = kind
                    if (kind == CharKind.LATIN) latinLetters++
                    if (kind == CharKind.BENGALI && character.isLetter()) bengaliLetters++
                }

                CharKind.PUNCTUATION -> {
                    val joinsDigits = bufferKind == CharKind.DIGIT && isDigit(text.getOrNull(position + 1))
                    if (joinsDigits) {
                        buffer.append(character)
                    } else {
                        flush()
                        if (character in SENTENCE_ENDS) atSentenceStart = true
                    }
                }

                CharKind.OPERATOR -> {
                    flush()
                    if (character != '-' || joinsTwoValues(tokens.lastOrNull(), text, position)) {
                        tokens += character.toString()
                    }
                }

                // A percent sign belongs to the number in front of it. Detached from it the
                // calculator cannot read it, and "7.5% of 2400" silently becomes 7.5 of 2400.
                CharKind.PERCENT -> {
                    if (bufferKind == CharKind.DIGIT) buffer.append(character) else flush()
                }

                CharKind.NONE -> flush()
            }
        }
        flush()

        return UtteranceScan(tokens, protectedTokens, bengaliLetters, latinLetters)
    }

    /** True for anything in the Bengali block: its letters, its marks and the taka sign. */
    fun isBengaliScript(character: Char): Boolean =
        character in BENGALI_BLOCK_START..BENGALI_BLOCK_END

    /** A hyphen is arithmetic only between a value and another value; elsewhere it is a word break. */
    private fun joinsTwoValues(previousToken: String?, text: String, position: Int): Boolean {
        if (previousToken == null || !previousToken.all { isDigit(it) || it == ')' }) return false
        var lookahead = position + 1
        while (lookahead < text.length && text[lookahead] == ' ') lookahead++
        val next = text.getOrNull(lookahead) ?: return false
        return isDigit(next) || next == '('
    }

    private fun classify(character: Char): CharKind = when {
        BengaliNumerals.isAsciiDigit(character) -> CharKind.DIGIT
        character in 'a'..'z' || character in 'A'..'Z' -> CharKind.LATIN
        isBengaliScript(character) -> CharKind.BENGALI
        character in OPERATORS -> CharKind.OPERATOR
        character == '%' -> CharKind.PERCENT
        character == ':' || character == '.' -> CharKind.PUNCTUATION
        character in SENTENCE_ENDS -> CharKind.PUNCTUATION
        else -> CharKind.NONE
    }

    private fun isDigit(character: Char?): Boolean =
        character != null && BengaliNumerals.isAsciiDigit(character)

    private enum class CharKind { DIGIT, LATIN, BENGALI, OPERATOR, PERCENT, PUNCTUATION, NONE }

    private const val BENGALI_BLOCK_START = '\u0980'
    private const val BENGALI_BLOCK_END = '\u09FF'
    private const val OPERATORS = "+-*/()"
    private const val SENTENCE_ENDS = ".!?"
}
