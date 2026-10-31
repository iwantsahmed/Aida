package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.Money
import java.util.Currency
import org.junit.Test

/**
 * Money, in all four languages, with the two rules that matter for money held to: the currency
 * is only claimed when Abu said it, and an amount his currency cannot express is refused rather
 * than rounded into something he did not say.
 */
class CurrencyExpressionParserTest {

    private val parser = CurrencyExpressionParser(BanglaNumberParser(), EnglishNumberParser())

    private val usd: Currency = Currency.getInstance("USD")

    // ------------------------------------------------------------- the BDT expressions

    @Test
    fun `reads the taka symbol`() {
        val parsed = parser.parse("৳500")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.money).isEqualTo(Money.of("500"))
        assertThat(parsed.currencyWasExplicit).isTrue()
        assertThat(parsed.matched).isEqualTo("৳500")
        assertThat(parsed.remainder).isEmpty()
    }

    @Test
    fun `reads every way Abu writes taka`() {
        assertThat(money("BDT 500")).isEqualTo(Money.of("500"))
        assertThat(money("500 taka")).isEqualTo(Money.of("500"))
        assertThat(money("500 tk")).isEqualTo(Money.of("500"))
        assertThat(money("500tk")).isEqualTo(Money.of("500"))
        assertThat(money("৫০০ টাকা")).isEqualTo(Money.of("500"))
        assertThat(money("taka 500")).isEqualTo(Money.of("500"))
    }

    @Test
    fun `reads Bengali amounts with a currency word`() {
        assertThat(money("৫০ হাজার টাকা")).isEqualTo(Money.of("50000"))
        assertThat(money("দেড় লাখ টাকা")).isEqualTo(Money.of("150000"))
        assertThat(money("আড়াই লাখ টাকা")).isEqualTo(Money.of("250000"))
        assertThat(money("এক কোটি টাকা")).isEqualTo(Money.of("10000000"))
    }

    @Test
    fun `reads Banglish and English amounts with a currency word`() {
        assertThat(money("1.5 lakh taka")).isEqualTo(Money.of("150000"))
        assertThat(money("50k taka")).isEqualTo(Money.of("50000"))
        assertThat(money("2.25 lakh tk")).isEqualTo(Money.of("225000"))
        assertThat(money("1 crore taka")).isEqualTo(Money.of("10000000"))
    }

    /**
     * The pipeline hands this parser normalized text, where every currency word has already
     * become the canonical "taka" and দেড় has become one_and_half. It has to read that as
     * readily as it reads what Abu typed.
     */
    @Test
    fun `reads a normalized sentence`() {
        assertThat(money("taka 500 expense")).isEqualTo(Money.of("500"))
        assertThat(money("one_and_half lakh taka invoice")).isEqualTo(Money.of("150000"))
        assertThat(money("2 lakh 25 thousand taka give")).isEqualTo(Money.of("225000"))
        assertThat(money("50 thousand taka")).isEqualTo(Money.of("50000"))
    }

    @Test
    fun `the longer reading of the same digits wins`() {
        // The English parser stops at "2 lakh"; the Bengali one reads the hazar too.
        val parsed = parser.parse("Ahmed ke 2 lakh 25 hazar taka dao")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.money).isEqualTo(Money.of("225000"))
        assertThat(parsed.remainder).isEqualTo("Ahmed ke dao")
    }

    // --------------------------------------------------------------------- what is left

    @Test
    fun `leaves the description behind`() {
        val parsed = parser.parse("500 taka khoroch")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.matched).isEqualTo("500 taka")
        assertThat(parsed.remainder).isEqualTo("khoroch")
    }

    @Test
    fun `leaves a Bengali description behind`() {
        val parsed = parser.parse("৫০০ টাকা খরচ")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.money).isEqualTo(Money.of("500"))
        assertThat(parsed.remainder).isEqualTo("খরচ")
    }

    @Test
    fun `leaves the words on both sides of the amount`() {
        val parsed = parser.parse("Kal 500 taka khoroch hoyeche")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.remainder).isEqualTo("Kal khoroch hoyeche")
    }

    /** The currency word claims the number beside it, which is how the hour stays an hour. */
    @Test
    fun `takes the number the currency word is standing next to`() {
        val parsed = parser.parse("kal 10 tay 500 taka dao")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.money).isEqualTo(Money.of("500"))
        assertThat(parsed.remainder).isEqualTo("kal 10 tay dao")
    }

    // ------------------------------------------------------------------- the honesty rules

    @Test
    fun `a bare number takes the default currency but does not claim Abu said it`() {
        val parsed = parser.parse("500")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.money).isEqualTo(Money.of("500"))
        assertThat(parsed.currencyWasExplicit).isFalse()
    }

    @Test
    fun `a currency word far from every number claims nothing`() {
        val parsed = parser.parse("500 diye ekta boi kinbo taka lagbe")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.currencyWasExplicit).isFalse()
    }

    @Test
    fun `the default currency is used when one is given`() {
        val parsed = parser.parse("500", usd)

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.money).isEqualTo(Money.of("500", usd))
        assertThat(parsed.currencyWasExplicit).isFalse()
    }

    @Test
    fun `a stated currency beats the default`() {
        val parsed = parser.parse("$500")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.money).isEqualTo(Money.of("500", usd))
        assertThat(parsed.currencyWasExplicit).isTrue()
    }

    @Test
    fun `an amount finer than the currency can express is refused, not rounded`() {
        assertThat(parser.parse("500.555 taka")).isNull()
    }

    @Test
    fun `poisha survive`() {
        val parsed = parser.parse("500.50 taka")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.money).isEqualTo(Money.of("500.50"))
        assertThat(parsed.money.amount.scale()).isEqualTo(2)
    }

    // ------------------------------------------------------------------- what it refuses

    @Test
    fun `a currency word with no number is not an amount`() {
        assertThat(parser.parse("taka lagbe")).isNull()
        assertThat(parser.parse("hazar taka")).isNull()
    }

    @Test
    fun `a sentence with no amount in it is not an amount`() {
        assertThat(parser.parse("Ahmed ke call koro")).isNull()
        assertThat(parser.parse("onek taka khoroch")).isNull()
    }

    /**
     * An ordinal is a calendar day, never money. "pay the bill before the 25th" names no amount, and
     * reading one booked 25 taka against a sentence that stated none.
     */
    @Test
    fun `an ordinal date is not an amount`() {
        assertThat(parser.parse("pay the electricity bill before the 25th")).isNull()
        assertThat(parser.parse("due on the 25th")).isNull()
        // The normalizer detaches the suffix, so the split form must be refused too.
        assertThat(parser.parse("pay the bill before the 25 th")).isNull()
        // A real amount in the same sentence shape still reads.
        assertThat(money("25 taka")).isEqualTo(Money.of("25"))
    }

    /**
     * Bengali writes a date ordinal with its own suffix — ২৫শে, ১লা, ২রা, ৪ঠা, ২৫তম — and that is a
     * day, not money, exactly as "25th" is. The emphatic ই of "৫ই" ("exactly 5") is the one left in,
     * so an amount written that way keeps its value.
     */
    @Test
    fun `a Bengali ordinal date is not an amount, but an emphatic number is`() {
        assertThat(parser.parse("২৫শে")).isNull()
        assertThat(parser.parse("১লা")).isNull()
        assertThat(parser.parse("২রা")).isNull()
        assertThat(parser.parse("৪ঠা")).isNull()
        assertThat(parser.parse("২৫তম")).isNull()
        assertThat(parser.parse("২৫শে ডিসেম্বর")).isNull()
        // The suffix set must not eat a real Bengali amount.
        assertThat(money("৫০ হাজার টাকা")).isEqualTo(Money.of("50000"))
        assertThat(parser.parse("৫ই টাকা")).isNotNull()
    }

    @Test
    fun `an identifier is not an amount`() {
        assertThat(parser.parse("01712345678 e call koro")).isNull()
    }

    private fun money(text: String): Money? = parser.parse(text)?.money
}
