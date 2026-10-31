package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.domain.finance.Calculator
import com.rayluxeo.aida.finance.CalculationVocabulary

/**
 * Pulls the sum out of a sentence, without working it out.
 *
 * Evaluation belongs to the Calculator, which computes on BigDecimal; all that happens here is
 * recognising that "what is 12 * 4", "12 * 4 koto", "১২ * ৪" and "2400 er 15 percent" each
 * contain a sum, and handing over the part that is arithmetic.
 *
 * The grammar is not restated here — it is read from [CalculationVocabulary], so this cannot
 * drift away from what the calculator actually accepts. Where the sentence is ambiguous, the
 * answer is null: Mira asking again is always better than Mira confidently answering a sum
 * Abu did not ask.
 */
object ArithmeticExpression {

    private const val MIN_OPERANDS = 2

    /** The one run of numbers and operators that forms a real sum, or null if there is not exactly one. */
    fun extract(normalized: String): String? {
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        val qualifying = mutableListOf<List<String>>()
        var run = mutableListOf<String>()

        fun close() {
            val trimmed = trimToValues(run)
            if (qualifies(trimmed)) qualifying += trimmed
            run = mutableListOf()
        }

        for (token in tokens) {
            if (belongsToSum(token)) run += token else close()
        }
        close()

        // Two separate sums in one sentence is a question Mira cannot answer with one number.
        return qualifying.singleOrNull()?.joinToString(separator = " ")
    }

    private fun qualifies(tokens: List<String>): Boolean =
        tokens.count(::isValue) >= MIN_OPERANDS && tokens.any(CalculationVocabulary::carriesOperation)

    private fun belongsToSum(token: String): Boolean =
        isValue(token) ||
            CalculationVocabulary.isSymbolRun(token) ||
            CalculationVocabulary.isOperatorWord(token) ||
            CalculationVocabulary.isPercentWord(token) ||
            // Filler such as "what", "taka" or "koto" may sit inside a sum, but never anchor one.
            CalculationVocabulary.isFillerWord(token)

    private fun isValue(token: String): Boolean = CalculationVocabulary.isNumericToken(token)

    /**
     * A run may open or close on framing words or a stray operator; only the arithmetic is kept.
     * A trailing percent is arithmetic, not framing — "2400 er 15 percent" ends where it means to.
     */
    private fun trimToValues(tokens: List<String>): List<String> {
        var start = 0
        var end = tokens.size
        while (start < end && !opensSum(tokens[start])) start++
        while (end > start && !closesSum(tokens[end - 1])) end--
        return tokens.subList(start, end)
    }

    private fun opensSum(token: String): Boolean = isValue(token) || token == "(" || token == "["

    private fun closesSum(token: String): Boolean =
        isValue(token) || token == ")" || token == "]" || CalculationVocabulary.isPercentWord(token)
}
