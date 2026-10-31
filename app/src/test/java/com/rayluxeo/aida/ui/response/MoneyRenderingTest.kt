package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Money
import java.util.Currency
import org.junit.Test

/**
 * An amount is the one part of a reply that has to come out of the renderer exactly as it went
 * in. These cases pin the figure itself — the grouping, the paisa and the currency — in both
 * languages, so a change to Mira's wording can never quietly move a decimal point.
 */
class MoneyRenderingTest {

    private val renderer = testRenderer()

    private fun figureIn(language: SpokenLanguage, amount: Money): String =
        renderer.display(MiraResponse.ExpenseRecorded(amount, ""), language)
            .removePrefix(if (language == SpokenLanguage.ENGLISH) "Expense: " else "খরচ: ")

    private fun english(amount: Money): String = figureIn(SpokenLanguage.ENGLISH, amount)

    private fun bengali(amount: Money): String = figureIn(SpokenLanguage.BENGALI, amount)

    @Test
    fun `paisa are rendered exactly, not reformatted`() {
        assertThat(english(taka("1234.56"))).isEqualTo("BDT 1,234.56")
        assertThat(bengali(taka("1234.56"))).isEqualTo("১,২৩৪.৫৬ টাকা")
    }

    @Test
    fun `a lakh is grouped the way Bangladesh writes it`() {
        assertThat(english(taka("150000"))).isEqualTo("BDT 1,50,000")
        assertThat(bengali(taka("150000"))).isEqualTo("১,৫০,০০০ টাকা")
    }

    @Test
    fun `a crore is grouped in lakhs too`() {
        assertThat(english(taka("10000000"))).isEqualTo("BDT 1,00,00,000")
        assertThat(bengali(taka("10000000"))).isEqualTo("১,০০,০০,০০০ টাকা")
    }

    @Test
    fun `a whole number of taka loses the decimal point, half a taka keeps both digits`() {
        assertThat(english(taka("500"))).isEqualTo("BDT 500")
        assertThat(bengali(taka("500"))).isEqualTo("৫০০ টাকা")
        assertThat(english(taka("1200.5"))).isEqualTo("BDT 1,200.50")
        assertThat(bengali(taka("1200.5"))).isEqualTo("১,২০০.৫০ টাকা")
    }

    @Test
    fun `zero is said as zero rather than left out`() {
        assertThat(english(taka("0"))).isEqualTo("BDT 0")
        assertThat(bengali(taka("0"))).isEqualTo("০ টাকা")
    }

    /** Eight digits and two paisa is past where a Double stops being exact. */
    @Test
    fun `a large amount keeps every digit`() {
        assertThat(english(taka("99999999.99"))).isEqualTo("BDT 9,99,99,999.99")
        assertThat(bengali(taka("99999999.99"))).isEqualTo("৯,৯৯,৯৯,৯৯৯.৯৯ টাকা")
    }

    @Test
    fun `a currency outside the lakh convention is grouped in thousands`() {
        val dollars = Money.of("1234567.89", Currency.getInstance("USD"))

        assertThat(english(dollars)).isEqualTo("USD 1,234,567.89")
        assertThat(bengali(dollars)).isEqualTo("১,২৩৪,৫৬৭.৮৯ USD")
    }

    @Test
    fun `an amount is spoken in Bengali numerals and in taka`() {
        val response = MiraResponse.ExpenseRecorded(taka("150000"), "অফিস ভাড়া")
        val spoken = renderer.speak(response, SpokenLanguage.BENGALI)

        assertThat(spoken).isEqualTo("খরচ লিখে রেখেছি — ১,৫০,০০০ টাকা, অফিস ভাড়া।")
        assertThat(spoken.none { it in '0'..'9' }).isTrue()
    }
}
