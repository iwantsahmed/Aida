package com.rayluxeo.aida.finance

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.model.Money
import java.math.BigDecimal
import java.util.Currency
import org.junit.Test

class MoneyMathTest {

    private val usd: Currency = Currency.getInstance("USD")

    @Test
    fun `a percentage of an amount rounds half up at the minor unit`() {
        assertThat(MoneyMath.percentageOf(Money.of("2400"), BigDecimal("15"))).isEqualTo(Money.of("360"))
        assertThat(MoneyMath.percentageOf(Money.of("10.05"), BigDecimal("50"))).isEqualTo(Money.of("5.03"))
    }

    @Test
    fun `splitting one hundred three ways still adds up to one hundred`() {
        val parts = valueOf(MoneyMath.splitEvenly(Money.of("100.00"), 3))
        assertThat(parts).containsExactly(Money.of("33.34"), Money.of("33.33"), Money.of("33.33")).inOrder()
        assertThat(valueOf(MoneyMath.sum(parts))).isEqualTo(Money.of("100.00"))
    }

    @Test
    fun `an exactly divisible split has no remainder to hand out`() {
        val parts = valueOf(MoneyMath.splitEvenly(Money.of("90.00"), 3))
        assertThat(parts).containsExactly(Money.of("30.00"), Money.of("30.00"), Money.of("30.00"))
    }

    @Test
    fun `splitting a debt keeps the sign and still adds back up`() {
        val parts = valueOf(MoneyMath.splitEvenly(Money.of("-100.00"), 3))
        assertThat(parts).containsExactly(Money.of("-33.34"), Money.of("-33.33"), Money.of("-33.33")).inOrder()
        assertThat(valueOf(MoneyMath.sum(parts))).isEqualTo(Money.of("-100.00"))
    }

    @Test
    fun `splitting a single way returns the whole amount`() {
        assertThat(valueOf(MoneyMath.splitEvenly(Money.of("7.77"), 1))).containsExactly(Money.of("7.77"))
    }

    @Test
    fun `splitting into no parts is a validation failure`() {
        assertThat(errorCodeOf(MoneyMath.splitEvenly(Money.of("10"), 0))).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(errorCodeOf(MoneyMath.splitEvenly(Money.of("10"), -2))).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `summing adds every amount`() {
        val total = valueOf(MoneyMath.sum(listOf(Money.of("10.50"), Money.of("0.25"), Money.of("4.25"))))
        assertThat(total).isEqualTo(Money.of("15.00"))
    }

    @Test
    fun `summing nothing is a failure rather than an invented zero`() {
        assertThat(errorCodeOf(MoneyMath.sum(emptyList()))).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `mixed currencies are refused`() {
        val mixed = listOf(Money.of("10"), Money.of("10", usd))
        assertThat(errorCodeOf(MoneyMath.sum(mixed))).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `adding and removing tax are inverses`() {
        val rate = BigDecimal("15")
        val gross = valueOf(MoneyMath.addTax(Money.of("1000"), rate))
        assertThat(gross).isEqualTo(Money.of("1150.00"))
        assertThat(valueOf(MoneyMath.removeTax(gross, rate))).isEqualTo(Money.of("1000.00"))
    }

    @Test
    fun `tax rounds half up at the minor unit`() {
        assertThat(valueOf(MoneyMath.addTax(Money.of("99.99"), BigDecimal("7.5")))).isEqualTo(Money.of("107.49"))
    }

    @Test
    fun `a zero rate leaves the amount alone`() {
        assertThat(valueOf(MoneyMath.addTax(Money.of("42.42"), BigDecimal.ZERO))).isEqualTo(Money.of("42.42"))
        assertThat(valueOf(MoneyMath.removeTax(Money.of("42.42"), BigDecimal.ZERO))).isEqualTo(Money.of("42.42"))
    }

    @Test
    fun `a negative tax rate is refused by both directions`() {
        assertThat(errorCodeOf(MoneyMath.addTax(Money.of("10"), BigDecimal("-5")))).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(errorCodeOf(MoneyMath.removeTax(Money.of("10"), BigDecimal("-5")))).isEqualTo(MiraErrorCode.VALIDATION)
    }

    private fun <T> valueOf(result: AppResult<T>): T {
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return (result as AppResult.Success).value
    }

    private fun errorCodeOf(result: AppResult<*>): MiraErrorCode {
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        return (result as AppResult.Failure).error.code
    }
}
