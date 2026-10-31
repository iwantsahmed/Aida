package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArithmeticExpressionTest {

    @Test
    fun `finds a sum inside an English question`() {
        assertThat(ArithmeticExpression.extract("what is 12 * 4")).isEqualTo("12 * 4")
    }

    @Test
    fun `finds a sum followed by a Bengali question word`() {
        assertThat(ArithmeticExpression.extract("12 * 4 calculate")).isEqualTo("12 * 4")
    }

    @Test
    fun `finds a bare sum`() {
        assertThat(ArithmeticExpression.extract("12 * 4")).isEqualTo("12 * 4")
    }

    @Test
    fun `keeps brackets and decimals`() {
        assertThat(ArithmeticExpression.extract("( 12.5 + 3 ) * 2")).isEqualTo("( 12.5 + 3 ) * 2")
    }

    @Test
    fun `trims a trailing operator that belonged to the sentence`() {
        assertThat(ArithmeticExpression.extract("12 + 4 -")).isEqualTo("12 + 4")
    }

    @Test
    fun `needs two numbers and an operator`() {
        assertThat(ArithmeticExpression.extract("call 3 people")).isNull()
        assertThat(ArithmeticExpression.extract("remind me at 10")).isNull()
        assertThat(ArithmeticExpression.extract("12 4")).isNull()
        assertThat(ArithmeticExpression.extract("")).isNull()
    }

    @Test
    fun `ignores a clock time that only looks numeric`() {
        assertThat(ArithmeticExpression.extract("tomorrow at 10:30")).isNull()
    }

    // Regressions found by driving the real app: the extractor knew a narrower grammar than the
    // calculator, so percent sums were either refused or silently cut short and answered wrongly.

    @Test
    fun `keeps a percent written as a word`() {
        assertThat(ArithmeticExpression.extract("2400 er 15 percent koto")).isEqualTo("2400 er 15 percent")
    }

    @Test
    fun `keeps a percent written as a symbol`() {
        assertThat(ArithmeticExpression.extract("15% of 2400")).isEqualTo("15% of 2400")
    }

    @Test
    fun `does not truncate a sum at the percent sign`() {
        assertThat(ArithmeticExpression.extract("what is 12450 * 3 + 7.5% of 2400"))
            .isEqualTo("12450 * 3 + 7.5% of 2400")
    }

    @Test
    fun `keeps a currency word sitting inside the sum`() {
        assertThat(ArithmeticExpression.extract("2400 taka er 15 percent"))
            .isEqualTo("2400 taka er 15 percent")
    }

    @Test
    fun `refuses a sentence holding two separate sums rather than answering one of them`() {
        assertThat(ArithmeticExpression.extract("12 * 4 and then 5 + 6")).isNull()
    }
}
