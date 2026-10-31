package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import org.junit.Test

/**
 * Numbers as Abu writes them with an English keyboard, including the South Asian scales English
 * borrowed. The decimal multipliers are the point of several of these: 1.5 lakh has to be exactly
 * 150000, which only holds while every multiplication stays on BigDecimal.
 */
class EnglishNumberParserTest {

    private val parser = EnglishNumberParser()

    @Test
    fun `reads plain digits`() {
        assertThat(value("500")).isEqualToIgnoringScale("500")
    }

    @Test
    fun `reads the k abbreviation, in either case`() {
        assertThat(value("50k")).isEqualToIgnoringScale("50000")
        assertThat(value("50 K")).isEqualToIgnoringScale("50000")
    }

    @Test
    fun `reads thousand and million`() {
        assertThat(value("50 thousand")).isEqualToIgnoringScale("50000")
        assertThat(value("1 million")).isEqualToIgnoringScale("1000000")
        assertThat(value("2 billion")).isEqualToIgnoringScale("2000000000")
    }

    @Test
    fun `reads lakh however it is spelled`() {
        assertThat(value("1 lakh")).isEqualToIgnoringScale("100000")
        assertThat(value("1 lac")).isEqualToIgnoringScale("100000")
        assertThat(value("2 lakhs")).isEqualToIgnoringScale("200000")
    }

    @Test
    fun `reads crore`() {
        assertThat(value("1 crore")).isEqualToIgnoringScale("10000000")
    }

    @Test
    fun `a decimal multiplier is exact`() {
        assertThat(value("1.5 lakh")).isEqualTo(BigDecimal("150000"))
        assertThat(value("2.25 lakh")).isEqualTo(BigDecimal("225000"))
        assertThat(value("1.234 lakh")).isEqualTo(BigDecimal("123400"))
    }

    @Test
    fun `reads a number spelled out in words`() {
        assertThat(value("five hundred")).isEqualToIgnoringScale("500")
        assertThat(value("twenty five thousand")).isEqualToIgnoringScale("25000")
        assertThat(value("twenty-five thousand")).isEqualToIgnoringScale("25000")
        assertThat(value("one hundred and fifty")).isEqualToIgnoringScale("150")
        assertThat(value("two thousand five hundred")).isEqualToIgnoringScale("2500")
    }

    @Test
    fun `reads half`() {
        assertThat(value("half lakh")).isEqualToIgnoringScale("50000")
    }

    @Test
    fun `reads a thousands separator, grouped either way`() {
        assertThat(value("50,000")).isEqualToIgnoringScale("50000")
        assertThat(value("1,25,000")).isEqualToIgnoringScale("125000")
        assertThat(parser.parse("1,2,3")).isNull()
    }

    @Test
    fun `hands back the rest of the sentence`() {
        val parsed = parser.parse("500 taka khoroch")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.matched).isEqualTo("500")
        assertThat(parsed.remainder).isEqualTo("taka khoroch")
    }

    @Test
    fun `a scale word with nothing in front of it states no quantity`() {
        assertThat(parser.parse("lakh")).isNull()
        assertThat(parser.parse("crore taka")).isNull()
        assertThat(parser.parse("thousand taka")).isNull()
    }

    @Test
    fun `a sentence with no number in it is not a number`() {
        assertThat(parser.parse("call Ahmed tomorrow")).isNull()
    }

    /**
     * "onek" is Banglish for "many". Splitting the k abbreviation out of a longer word would
     * turn it into a thousand, so an abbreviation is only ever read as a token of its own.
     */
    @Test
    fun `an abbreviation is never split out of a word`() {
        assertThat(parser.parse("onek taka")).isNull()
    }

    @Test
    fun `and joins two halves of one number, never two numbers`() {
        val parsed = parser.parse("500 and 300")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.value).isEqualToIgnoringScale("500")
        assertThat(parsed.matched).isEqualTo("500")
    }

    @Test
    fun `a digit run too long to be an amount is an identifier, not a number`() {
        assertThat(parser.parse("01712345678")).isNull()
        assertThat(value("999999999")).isEqualToIgnoringScale("999999999")
    }

    @Test
    fun `a quantity beyond any plausible amount is refused`() {
        assertThat(parser.parse("999999999 crore")).isNull()
    }

    @Test
    fun `the value carries no trailing zeros`() {
        assertThat(value("1.5 lakh")!!.scale()).isEqualTo(0)
        assertThat(value("2.25 lakh")!!.scale()).isEqualTo(0)
    }

    /**
     * A digit run glued to an ordinal suffix names a day, not a quantity. "25th" must not read as
     * 25, or "pay the bill before the 25th" books 25 taka. The calendar day itself is the date
     * parser's work, and it strips the suffix on its own, so nothing is lost by refusing it here.
     */
    @Test
    fun `an ordinal is a day, not a number`() {
        assertThat(parser.parse("the 25th")).isNull()
        assertThat(parser.parse("1st")).isNull()
        assertThat(parser.parse("2nd")).isNull()
        assertThat(parser.parse("3rd")).isNull()
        assertThat(parser.parse("pay the bill before the 25th")).isNull()
        // The normalizer splits the digits from the letters, so the suffix arrives as its own token
        // ("25" "th"); a space between them is no evidence the ordinal was meant any less.
        assertThat(parser.parse("25 th")).isNull()
        assertThat(parser.parse("due the 25 th")).isNull()
        // But the suffix has to be the WHOLE following word, and a plain number or a scale stands.
        assertThat(value("25thing")).isEqualToIgnoringScale("25")
        assertThat(value("25")).isEqualToIgnoringScale("25")
        assertThat(value("25 thousand")).isEqualToIgnoringScale("25000")
    }

    private fun value(text: String): BigDecimal? = parser.parse(text)?.value
}
