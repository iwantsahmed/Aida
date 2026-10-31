package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import org.junit.Test

/**
 * The Bengali number system, in the two scripts Abu writes it in.
 *
 * Bengali counts in লাখ and কোটি and compounds them by addition, and it says one and a half and
 * two and a half as single words. None of that survives a translation to English digits, so all
 * of it is tested here directly.
 */
class BanglaNumberParserTest {

    private val parser = BanglaNumberParser()

    // ------------------------------------------------------------------ Bengali script

    @Test
    fun `reads Bengali digits`() {
        assertThat(value("৫০০")).isEqualToIgnoringScale("500")
    }

    @Test
    fun `reads Bengali digits followed by a scale word`() {
        assertThat(value("৫০ হাজার")).isEqualToIgnoringScale("50000")
    }

    @Test
    fun `reads a number spelled out in Bengali`() {
        assertThat(value("পঞ্চাশ হাজার")).isEqualToIgnoringScale("50000")
    }

    @Test
    fun `reads lakh`() {
        assertThat(value("এক লাখ")).isEqualToIgnoringScale("100000")
    }

    @Test
    fun `der is one and a half`() {
        assertThat(value("দেড় লাখ")).isEqualToIgnoringScale("150000")
    }

    @Test
    fun `arai is two and a half`() {
        assertThat(value("আড়াই লাখ")).isEqualToIgnoringScale("250000")
    }

    @Test
    fun `scales compound by addition`() {
        assertThat(value("দুই লাখ পঁচিশ হাজার")).isEqualToIgnoringScale("225000")
    }

    @Test
    fun `reads crore`() {
        assertThat(value("এক কোটি")).isEqualToIgnoringScale("10000000")
    }

    @Test
    fun `sare adds a half to the number that follows it`() {
        assertThat(value("সাড়ে তিন লাখ")).isEqualToIgnoringScale("350000")
    }

    @Test
    fun `shoya adds a quarter and poune takes one away`() {
        assertThat(value("সোয়া দুই লাখ")).isEqualToIgnoringScale("225000")
        assertThat(value("পৌনে দুই লাখ")).isEqualToIgnoringScale("175000")
    }

    @Test
    fun `reads a hundred written as one word with its number`() {
        assertThat(value("পাঁচশ")).isEqualToIgnoringScale("500")
        assertThat(value("একশ পঞ্চাশ")).isEqualToIgnoringScale("150")
        assertThat(value("দুই লাখ পঁচিশ হাজার পাঁচশ")).isEqualToIgnoringScale("225500")
    }

    @Test
    fun `reads the whole Bengali counting table`() {
        assertThat(value("নিরানব্বই হাজার")).isEqualToIgnoringScale("99000")
        assertThat(value("চব্বিশ")).isEqualToIgnoringScale("24")
        assertThat(value("নব্বই কোটি")).isEqualToIgnoringScale("900000000")
    }

    // --------------------------------------------------------------- Banglish and mixed

    @Test
    fun `reads the same numbers written in Latin letters`() {
        assertThat(value("ek lakh")).isEqualToIgnoringScale("100000")
        assertThat(value("50 hazar")).isEqualToIgnoringScale("50000")
        assertThat(value("dui lakh pochish hazar")).isEqualToIgnoringScale("225000")
        assertThat(value("ponchash hazar")).isEqualToIgnoringScale("50000")
    }

    @Test
    fun `reads der and arai in Latin letters`() {
        assertThat(value("der lakh")).isEqualToIgnoringScale("150000")
        assertThat(value("arai lakh")).isEqualToIgnoringScale("250000")
        assertThat(value("sare tin lakh")).isEqualToIgnoringScale("350000")
    }

    @Test
    fun `reads ASCII digits with a Bengali scale word`() {
        assertThat(value("50 হাজার")).isEqualToIgnoringScale("50000")
        assertThat(value("২ লাখ 25 হাজার")).isEqualToIgnoringScale("225000")
    }

    @Test
    fun `reads the canonical tokens the normalizer produces`() {
        assertThat(value("one_and_half lakh")).isEqualToIgnoringScale("150000")
        assertThat(value("two_and_half lakh")).isEqualToIgnoringScale("250000")
        assertThat(value("পঞ্চাশ thousand")).isEqualToIgnoringScale("50000")
    }

    /**
     * "tin", "char" and "sat" are also ordinary English words, and Abu's sentences are half
     * English. They count only when a scale word follows them.
     */
    @Test
    fun `a Banglish word that is also English needs a scale word after it`() {
        assertThat(value("tin lakh")).isEqualToIgnoringScale("300000")
        assertThat(parser.parse("tin ta kaj")).isNull()
        assertThat(parser.parse("der")).isNull()
        // "at" is eight in Banglish and a preposition in English; here it is the preposition,
        // so the only number in the sentence is the hour.
        assertThat(parser.parse("call Ahmed at 10")?.matched).isEqualTo("10")
    }

    // ------------------------------------------------------------------- what is left

    @Test
    fun `hands back the rest of the sentence`() {
        val parsed = parser.parse("৫০০ টাকা খরচ")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.matched).isEqualTo("৫০০")
        assertThat(parsed.remainder).isEqualTo("টাকা খরচ")
    }

    @Test
    fun `keeps the words on both sides of the number`() {
        val parsed = parser.parse("আগামীকাল ২ লাখ টাকা পাবো")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.value).isEqualToIgnoringScale("200000")
        assertThat(parsed.matched).isEqualTo("২ লাখ")
        assertThat(parsed.remainder).isEqualTo("আগামীকাল টাকা পাবো")
    }

    @Test
    fun `reads the first number when a sentence holds two`() {
        assertThat(value("১০ টায় ৫০০ টাকা")).isEqualToIgnoringScale("10")
    }

    // ----------------------------------------------------------------- what it refuses

    @Test
    fun `a sentence with no number in it is not a number`() {
        assertThat(parser.parse("খরচ কত")).isNull()
        assertThat(parser.parse("Ahmed ke call koro")).isNull()
    }

    @Test
    fun `a scale word with nothing in front of it states no quantity`() {
        assertThat(parser.parse("হাজার টাকা")).isNull()
        assertThat(parser.parse("lakh taka")).isNull()
    }

    @Test
    fun `a digit run too long to be an amount is an identifier, not a number`() {
        assertThat(parser.parse("০১৭১২৩৪৫৬৭৮")).isNull()
        assertThat(value("৯৯৯৯৯৯৯৯৯")).isEqualToIgnoringScale("999999999")
    }

    @Test
    fun `a quantity beyond any plausible amount is refused`() {
        assertThat(parser.parse("৯৯৯৯৯৯৯৯৯ কোটি")).isNull()
    }

    // ----------------------------------------------------------------------- exactness

    @Test
    fun `der lakh is exactly one hundred and fifty thousand`() {
        assertThat(value("দেড় লাখ")).isEqualTo(BigDecimal("150000"))
        assertThat(value("দেড় লাখ")!!.scale()).isEqualTo(0)
    }

    @Test
    fun `a fraction survives as a fraction`() {
        assertThat(value("আড়াই")).isEqualTo(BigDecimal("2.5"))
    }

    private fun value(text: String): BigDecimal? = parser.parse(text)?.value
}
