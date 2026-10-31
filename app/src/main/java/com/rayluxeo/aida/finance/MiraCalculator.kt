package com.rayluxeo.aida.finance

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.finance.Calculator
import java.math.BigDecimal
import java.math.MathContext
import javax.inject.Inject

/**
 * A real expression evaluator, because a language model is never trusted with Abu's sums.
 *
 * The grammar is deliberately small — `+ - * /`, parentheses, a leading sign and percent, all
 * over [BigDecimal]. A short list of English and Banglish words is accepted as operators so that
 * "15% of 2400" and "2400 er 15%" reach the same arithmetic, and Bengali digits are tolerated in
 * case an utterance reaches here without passing through the NLU layer's digit folding.
 *
 * Percent means "divide by one hundred", with the one exception every desk calculator makes:
 * a bare percent term added to or subtracted from something is read as a percentage *of that
 * something*, so "2400 + 15%" is 2760 rather than 2400.15. Parenthesising it — "2400 + (15%)" —
 * opts out and gives the literal 0.15.
 */
class MiraCalculator @Inject constructor() : Calculator {

    override fun evaluate(expression: String): AppResult<String> {
        val input = expression.trim()
        if (input.isEmpty()) return failure(MiraErrorCode.VALIDATION, "There is nothing to calculate")
        if (input.length > MAX_INPUT_LENGTH) {
            return failure(MiraErrorCode.VALIDATION, "Expression is longer than $MAX_INPUT_LENGTH characters")
        }
        return try {
            val preprocessed = preprocess(input)
            val tokens = tokenize(preprocessed)
            if (tokens.isEmpty()) {
                failure(MiraErrorCode.VALIDATION, "No numbers to work with in \"$input\"")
            } else {
                format(Parser(tokens).parse()).asSuccess()
            }
        } catch (error: ExpressionError) {
            failure(MiraErrorCode.VALIDATION, error.detail, error)
        } catch (error: ArithmeticException) {
            // BigDecimal reports unrepresentable results this way, and evaluate() promises never to throw.
            failure(MiraErrorCode.VALIDATION, "That calculation has no representable answer", error)
        }
    }

    private companion object {
        /** Also caps parenthesis nesting, which is what keeps the recursive parser off the stack limit. */
        const val MAX_INPUT_LENGTH = 200
    }
}

/** DECIMAL64 is explicit so that 1/3 terminates instead of throwing on a non-terminating quotient. */
private val DIVISION_CONTEXT: MathContext = MathContext.DECIMAL64

private const val PERCENT_POINTS = 2

private const val BENGALI_ZERO = '০'
private const val BENGALI_NINE = '৯'

private enum class Op(val glyph: String) {
    PLUS("+"),
    MINUS("-"),
    TIMES("*"),
    DIVIDE("/"),
}

private sealed interface Token {
    data class Literal(val value: BigDecimal) : Token
    data class Symbol(val op: Op) : Token
    data object OpenParen : Token
    data object CloseParen : Token
    data object Percent : Token
}

private val SYMBOL_TOKENS: Map<Char, Token> = mapOf(
    '+' to Token.Symbol(Op.PLUS),
    '-' to Token.Symbol(Op.MINUS),
    '−' to Token.Symbol(Op.MINUS),
    '–' to Token.Symbol(Op.MINUS),
    '*' to Token.Symbol(Op.TIMES),
    '×' to Token.Symbol(Op.TIMES),
    '/' to Token.Symbol(Op.DIVIDE),
    '÷' to Token.Symbol(Op.DIVIDE),
    '(' to Token.OpenParen,
    ')' to Token.CloseParen,
    '[' to Token.OpenParen,
    ']' to Token.CloseParen,
    '%' to Token.Percent,
)

private val WORD_OPERATORS: Map<String, Op> = mapOf(
    "plus" to Op.PLUS,
    "add" to Op.PLUS,
    "jog" to Op.PLUS,
    "যোগ" to Op.PLUS,
    "minus" to Op.MINUS,
    "biyog" to Op.MINUS,
    "বিয়োগ" to Op.MINUS,
    "times" to Op.TIMES,
    "x" to Op.TIMES,
    "into" to Op.TIMES,
    "gun" to Op.TIMES,
    "গুণ" to Op.TIMES,
    "multiplied" to Op.TIMES,
    // "of" and "er" are the two halves of the same idiom: "15% of 2400" and "2400 er 15%".
    "of" to Op.TIMES,
    "er" to Op.TIMES,
    "এর" to Op.TIMES,
    "divided" to Op.DIVIDE,
    "over" to Op.DIVIDE,
    "bhag" to Op.DIVIDE,
    "ভাগ" to Op.DIVIDE,
    "between" to Op.DIVIDE,
    "among" to Op.DIVIDE,
)

private val PERCENT_WORDS = setOf("percent", "percentage", "parcent", "shotangsho", "শতাংশ", "পারসেন্ট")

/** Question framing and currency words the NLU layer may not have stripped. */
private val FILLER_WORDS = setOf(
    "what", "whats", "is", "the", "result", "answer", "calculate", "please", "by",
    "koto", "hobe", "hoy", "taka", "tk",
    "with", "on", "off", "for", "in", "each", "person", "people",
    "split", "discount", "tax", "vat", "markup", "margin",
    "ট্যাক্স", "ভ্যাট", "ডিসকাউন্ট",
)

private fun preprocess(input: String): String {
    // Strip contractions like "What's" -> "What is"
    var text = input.trim().replace(Regex("""(?i)\bwhat's\b"""), "what is")

    // 1. Split amounts: "split 5000 by 4", "split 5000 between 4", "split 5000 among 4"
    val splitRegex = Regex("""(?i)\bsplit\s+([0-9.,]+)\s+(?:by|between|among|in|into)\s+([0-9.,]+)""")
    text = splitRegex.replace(text) { "${it.groupValues[1]} / ${it.groupValues[2]}" }

    val splitRegexTrailing = Regex("""(?i)([0-9.,]+)\s+split\s+(?:by|between|among|in|into)?\s*([0-9.,]+)""")
    text = splitRegexTrailing.replace(text) { "${it.groupValues[1]} / ${it.groupValues[2]}" }

    // 2. Margin: cost with X% margin -> cost / (1 - X%)
    val marginWithRegex = Regex("""(?i)([0-9.,]+)\s+(?:with\s+)?([0-9.,]+)\s*%\s+margin""")
    text = marginWithRegex.replace(text) { "${it.groupValues[1]} / (1 - ${it.groupValues[2]}%)" }

    val marginOfRegex = Regex("""(?i)margin\s+(?:of\s+)?([0-9.,]+)\s*%\s+on\s+([0-9.,]+)""")
    text = marginOfRegex.replace(text) { "${it.groupValues[2]} / (1 - ${it.groupValues[1]}%)" }

    // 3. Discount: "5000 with 20% discount", "5000 discount 20%", "discount of 20% on 1000"
    val discountWithRegex = Regex("""(?i)([0-9.,]+)\s+(?:with|-|minus)?\s*([0-9.,]+)\s*%\s+discount""")
    text = discountWithRegex.replace(text) { "${it.groupValues[1]} - ${it.groupValues[2]}%" }

    val discountTrailingRegex = Regex("""(?i)([0-9.,]+)\s+discount\s+([0-9.,]+)\s*%?""")
    text = discountTrailingRegex.replace(text) { "${it.groupValues[1]} - ${it.groupValues[2]}%" }

    val discountOfRegex = Regex("""(?i)(?:calculate\s+)?(?:the\s+)?discount\s+(?:of\s+)?([0-9.,]+)\s*%\s+(?:on|of|for)\s+([0-9.,]+)""")
    text = discountOfRegex.replace(text) { "${it.groupValues[2]} - ${it.groupValues[1]}%" }

    // 4. Tax / VAT: "2000 with 15% tax", "2000 plus 15% tax", "tax of 10% on 5000"
    val taxWithRegex = Regex("""(?i)([0-9.,]+)\s+(?:with|\+|plus)?\s*([0-9.,]+)\s*%\s+(?:tax|vat)""")
    text = taxWithRegex.replace(text) { "${it.groupValues[1]} + ${it.groupValues[2]}%" }

    val taxOfRegex = Regex("""(?i)(?:calculate\s+)?(?:the\s+)?(?:tax|vat)\s+(?:of\s+)?([0-9.,]+)\s*%\s+(?:on|of|for)\s+([0-9.,]+)""")
    text = taxOfRegex.replace(text) { "${it.groupValues[2]} + ${it.groupValues[1]}%" }

    // 5. Markup: "1000 with 25% markup", "1000 markup 25%"
    val markupWithRegex = Regex("""(?i)([0-9.,]+)\s+(?:with|\+|plus)?\s*([0-9.,]+)\s*%\s+markup""")
    text = markupWithRegex.replace(text) { "${it.groupValues[1]} + ${it.groupValues[2]}%" }

    val markupOfRegex = Regex("""(?i)markup\s+(?:of\s+)?([0-9.,]+)\s*%\s+on\s+([0-9.,]+)""")
    text = markupOfRegex.replace(text) { "${it.groupValues[2]} + ${it.groupValues[1]}%" }

    return text
}

private val IGNORABLE_CHARS = setOf('=', '?', '\'', ',', '৳')

private fun isDigitChar(c: Char): Boolean = c in '0'..'9' || c in BENGALI_ZERO..BENGALI_NINE

private fun asciiDigit(c: Char): Char = if (c in BENGALI_ZERO..BENGALI_NINE) '0' + (c - BENGALI_ZERO) else c

private fun tokenize(input: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var index = 0
    while (index < input.length) {
        val current = input[index]
        val following = input.getOrNull(index + 1)
        when {
            current.isWhitespace() || current in IGNORABLE_CHARS -> index++

            isDigitChar(current) || (current == '.' && following != null && isDigitChar(following)) -> {
                val digits = StringBuilder()
                var seenPoint = false
                while (index < input.length) {
                    val c = input[index]
                    val next = input.getOrNull(index + 1)
                    when {
                        isDigitChar(c) -> {
                            digits.append(asciiDigit(c))
                            index++
                        }
                        c == '.' && !seenPoint && next != null && isDigitChar(next) -> {
                            seenPoint = true
                            digits.append('.')
                            index++
                        }
                        // "2,400" is one number; the separator carries no arithmetic meaning.
                        c == ',' && next != null && isDigitChar(next) -> index++
                        else -> break
                    }
                }
                tokens += Token.Literal(BigDecimal(digits.toString()))
            }

            current.isLetter() -> {
                val start = index
                while (index < input.length && input[index].isLetter()) index++
                val word = input.substring(start, index).lowercase()
                val operator = WORD_OPERATORS[word]
                when {
                    word in FILLER_WORDS -> Unit
                    word in PERCENT_WORDS -> tokens += Token.Percent
                    operator != null -> tokens += Token.Symbol(operator)
                    else -> throw ExpressionError("\"$word\" is not something I can calculate with")
                }
            }

            else -> {
                tokens += SYMBOL_TOKENS[current] ?: throw ExpressionError("'$current' is not part of a calculation")
                index++
            }
        }
    }
    return tokens
}

private class Parser(private val tokens: List<Token>) {

    private var position = 0

    fun parse(): BigDecimal {
        val result = additive()
        val leftover = tokens.getOrNull(position)
        if (leftover != null) throw ExpressionError("${describe(leftover)} is out of place")
        return result.value
    }

    private fun additive(): Term {
        var left = multiplicative()
        while (true) {
            val op = peekOperator(Op.PLUS, Op.MINUS) ?: return left
            position++
            val right = multiplicative()
            val delta = if (right.isBarePercent) left.value.multiply(right.value) else right.value
            val value = if (op == Op.PLUS) left.value.add(delta) else left.value.subtract(delta)
            left = Term(value, isBarePercent = false)
        }
    }

    private fun multiplicative(): Term {
        var left = unary()
        while (true) {
            val op = peekOperator(Op.TIMES, Op.DIVIDE) ?: return left
            position++
            val right = unary()
            val value = if (op == Op.TIMES) {
                left.value.multiply(right.value)
            } else {
                if (right.value.signum() == 0) throw ExpressionError("Dividing by zero has no answer")
                left.value.divide(right.value, DIVISION_CONTEXT)
            }
            left = Term(value, isBarePercent = false)
        }
    }

    private fun unary(): Term = when {
        matchOperator(Op.MINUS) -> unary().let { Term(it.value.negate(), it.isBarePercent) }
        matchOperator(Op.PLUS) -> unary()
        else -> postfix()
    }

    private fun postfix(): Term {
        val value = primary()
        return if (match(Token.Percent)) {
            Term(value.movePointLeft(PERCENT_POINTS), isBarePercent = true)
        } else {
            Term(value, isBarePercent = false)
        }
    }

    private fun primary(): BigDecimal {
        val token = tokens.getOrNull(position) ?: throw ExpressionError("The expression ends unexpectedly")
        return when (token) {
            is Token.Literal -> {
                position++
                token.value
            }
            Token.OpenParen -> {
                position++
                val inner = additive().value
                if (!match(Token.CloseParen)) throw ExpressionError("A '(' was never closed")
                inner
            }
            else -> throw ExpressionError("${describe(token)} is out of place")
        }
    }

    private fun peekOperator(vararg wanted: Op): Op? {
        val symbol = tokens.getOrNull(position) as? Token.Symbol ?: return null
        return symbol.op.takeIf { it in wanted }
    }

    private fun matchOperator(op: Op): Boolean {
        if (peekOperator(op) == null) return false
        position++
        return true
    }

    private fun match(token: Token): Boolean {
        if (tokens.getOrNull(position) != token) return false
        position++
        return true
    }

    /** [isBarePercent] is what lets "2400 + 15%" mean 2760 while "2400 + (15%)" stays literal. */
    private data class Term(val value: BigDecimal, val isBarePercent: Boolean)
}

private fun describe(token: Token): String = when (token) {
    is Token.Literal -> "the number ${format(token.value)}"
    is Token.Symbol -> "'${token.op.glyph}'"
    Token.OpenParen -> "'('"
    Token.CloseParen -> "')'"
    Token.Percent -> "'%'"
}

/** 12.50 reads back as "12.5" and 12.00 as "12"; trailing zeros suggest a precision Mira does not have. */
private fun format(value: BigDecimal): String {
    val stripped = value.stripTrailingZeros()
    val plain = if (stripped.scale() < 0) stripped.setScale(0) else stripped
    return plain.toPlainString()
}

/**
 * Raised only inside this file's tokenizer and parser, and turned into a VALIDATION failure at
 * the single boundary in [MiraCalculator.evaluate]. It never escapes into the rest of Mira.
 */
private class ExpressionError(val detail: String) : RuntimeException(detail)

/**
 * The calculator's vocabulary, published so the layer that decides *whether* a sentence contains
 * a sum uses the same grammar as the layer that works it out.
 *
 * Keeping two lists in step by hand failed exactly once and badly: the extractor knew nothing of
 * `%`, `of` or `percent`, so "2400 er 15 percent" was refused outright and
 * "12450 * 3 + 7.5% of 2400" was quietly truncated to "12450 * 3 + 7.5" and answered with
 * confidence. A wrong number delivered calmly is the worst thing this app could do.
 */
object CalculationVocabulary {

    /** Every character the tokenizer treats as an operator, bracket or percent sign. */
    val symbolChars: Set<Char> = SYMBOL_TOKENS.keys

    /** Characters that carry no meaning but are allowed to appear inside a sum. */
    val ignorableChars: Set<Char> = IGNORABLE_CHARS

    /** Words that act as operators: "plus", "times", "of", "er" and friends. */
    val operatorWords: Set<String> = WORD_OPERATORS.keys

    /** Words that mean "%": "percent", "shotangsho" and friends. */
    val percentWords: Set<String> = PERCENT_WORDS

    /** Question framing and currency words the calculator skips over. */
    val fillerWords: Set<String> = FILLER_WORDS

    fun isOperatorWord(word: String): Boolean = word in operatorWords

    fun isPercentWord(word: String): Boolean = word in percentWords

    fun isFillerWord(word: String): Boolean = word in fillerWords

    /** True for a run of symbols alone, such as "*", "(" or "%". */
    fun isSymbolRun(token: String): Boolean =
        token.isNotEmpty() && token.all { it in symbolChars || it in ignorableChars } &&
            token.any { it in symbolChars }

    /**
     * True for a token built only from digits, a decimal point and calculator symbols —
     * "12", "7.5", "7.5%" and "12*3" all qualify, while "10:30" does not.
     */
    fun isNumericToken(token: String): Boolean =
        token.isNotEmpty() &&
            token.all { isDigitChar(it) || it == '.' || it in symbolChars || it in ignorableChars } &&
            token.any(::isDigitChar)

    /** True when the token contributes an operation rather than only a value. */
    fun carriesOperation(token: String): Boolean = when {
        isOperatorWord(token) || isPercentWord(token) -> true
        else -> token.any { it in symbolChars && it != '(' && it != ')' }
    }
}
