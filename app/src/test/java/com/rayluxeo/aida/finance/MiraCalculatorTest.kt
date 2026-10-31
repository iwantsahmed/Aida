package com.rayluxeo.aida.finance

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraError
import com.rayluxeo.aida.core.common.MiraErrorCode
import org.junit.Test

class MiraCalculatorTest {

    private val calculator = MiraCalculator()

    @Test
    fun `multiplication binds tighter than addition`() {
        assertThat(valueOf("2 + 3 * 4")).isEqualTo("14")
    }

    @Test
    fun `parentheses override precedence`() {
        assertThat(valueOf("(2 + 3) * 4")).isEqualTo("20")
    }

    @Test
    fun `subtraction is left associative`() {
        assertThat(valueOf("10 - 3 - 2")).isEqualTo("5")
    }

    @Test
    fun `division is left associative`() {
        assertThat(valueOf("100 / 5 / 2")).isEqualTo("10")
    }

    @Test
    fun `leading minus negates the whole term`() {
        assertThat(valueOf("-5 + 2")).isEqualTo("-3")
        assertThat(valueOf("-(2 + 3)")).isEqualTo("-5")
        assertThat(valueOf("2 * -3")).isEqualTo("-6")
    }

    @Test
    fun `decimals divide exactly when they can`() {
        assertThat(valueOf("10 / 4")).isEqualTo("2.5")
    }

    @Test
    fun `a repeating decimal terminates instead of throwing`() {
        assertThat(valueOf("1 / 3")).isEqualTo("0.3333333333333333")
    }

    @Test
    fun `dividing by zero is a validation failure, not an exception`() {
        val error = errorFrom("5 / 0")
        assertThat(error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(error.detail).contains("zero")
    }

    @Test
    fun `dividing by an expression that evaluates to zero also fails`() {
        assertThat(errorFrom("5 / (3 - 3)").code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `trailing zeros never reach Abu`() {
        assertThat(valueOf("12.50 + 0")).isEqualTo("12.5")
        assertThat(valueOf("12.00 * 1")).isEqualTo("12")
        assertThat(valueOf("0.10 + 0.20")).isEqualTo("0.3")
    }

    @Test
    fun `percent of reads the same in English and Banglish`() {
        assertThat(valueOf("15% of 2400")).isEqualTo("360")
        assertThat(valueOf("2400 er 15%")).isEqualTo("360")
        assertThat(valueOf("2400 taka er 15 percent")).isEqualTo("360")
    }

    @Test
    fun `a bare percent on its own is a hundredth`() {
        assertThat(valueOf("50%")).isEqualTo("0.5")
    }

    @Test
    fun `adding a bare percent means a percentage of the left operand`() {
        assertThat(valueOf("2400 + 15%")).isEqualTo("2760")
        assertThat(valueOf("100 - 20%")).isEqualTo("80")
    }

    @Test
    fun `parenthesising a percent opts out of the desk-calculator reading`() {
        assertThat(valueOf("2400 + (15%)")).isEqualTo("2400.15")
    }

    @Test
    fun `word operators are accepted`() {
        assertThat(valueOf("what is 7 times 6")).isEqualTo("42")
        assertThat(valueOf("10 divided by 4")).isEqualTo("2.5")
        assertThat(valueOf("12 plus 30 minus 2")).isEqualTo("40")
    }

    @Test
    fun `Bengali digits are tolerated`() {
        assertThat(valueOf("২৫ + ৭৫")).isEqualTo("100")
    }

    @Test
    fun `digit group separators are ignored inside a number`() {
        assertThat(valueOf("2,400 + 100")).isEqualTo("2500")
    }

    @Test
    fun `a trailing operator is reported rather than guessed at`() {
        assertThat(errorFrom("2 +").code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `an unclosed parenthesis names what broke`() {
        assertThat(errorFrom("(2 + 3").detail).contains("never closed")
    }

    @Test
    fun `an unknown word names itself`() {
        assertThat(errorFrom("two plus two").detail).contains("two")
    }

    @Test
    fun `two numbers with no operator between them is a failure`() {
        assertThat(errorFrom("2 3").code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `an empty expression is a failure`() {
        assertThat(errorFrom("   ").code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `an expression with no numbers at all is a failure`() {
        assertThat(errorFrom("what is the result").code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `an absurdly long expression is refused before it is parsed`() {
        val error = errorFrom("1 + ".repeat(60) + "1")
        assertThat(error.detail).contains("longer than")
    }

    @Test
    fun `percentage of calculation evaluates correctly`() {
        assertThat(valueOf("What's 30% of 450000?")).isEqualTo("135000")
        assertThat(valueOf("30% of 450000")).isEqualTo("135000")
        assertThat(valueOf("450000 er 30%")).isEqualTo("135000")
    }

    @Test
    fun `discount calculations evaluate correctly`() {
        assertThat(valueOf("12000 with 15% discount")).isEqualTo("10200")
        assertThat(valueOf("5000 discount 10%")).isEqualTo("4500")
        assertThat(valueOf("discount of 20% on 1000")).isEqualTo("800")
    }

    @Test
    fun `tax and vat calculations evaluate correctly`() {
        assertThat(valueOf("10000 with 15% tax")).isEqualTo("11500")
        assertThat(valueOf("10000 with 5% vat")).isEqualTo("10500")
        assertThat(valueOf("tax of 10% on 5000")).isEqualTo("5500")
    }

    @Test
    fun `markup calculations evaluate correctly`() {
        assertThat(valueOf("8000 with 20% markup")).isEqualTo("9600")
    }

    @Test
    fun `margin calculations evaluate correctly`() {
        // Cost of 8000 with 20% margin gives price = 8000 / (1 - 0.20) = 10000
        assertThat(valueOf("8000 with 20% margin")).isEqualTo("10000")
    }

    @Test
    fun `split calculations evaluate correctly`() {
        assertThat(valueOf("split 20000 by 4")).isEqualTo("5000")
        assertThat(valueOf("split 15000 between 3")).isEqualTo("5000")
    }

    private fun valueOf(expression: String): String {
        val result = calculator.evaluate(expression)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return (result as AppResult.Success).value
    }

    private fun errorFrom(expression: String): MiraError {
        val result = calculator.evaluate(expression)
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        return (result as AppResult.Failure).error
    }
}
