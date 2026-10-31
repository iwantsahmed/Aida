package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BengaliNumeralsTest {

    @Test
    fun `folds every Bengali digit to ASCII`() {
        assertThat(BengaliNumerals.toAscii("০১২৩৪৫৬৭৮৯")).isEqualTo("0123456789")
    }

    @Test
    fun `folds every ASCII digit to Bengali`() {
        assertThat(BengaliNumerals.toBengali("0123456789")).isEqualTo("০১২৩৪৫৬৭৮৯")
    }

    @Test
    fun `leaves surrounding text untouched`() {
        assertThat(BengaliNumerals.toAscii("আগামীকাল ১০টায়")).isEqualTo("আগামীকাল 10টায়")
    }

    @Test
    fun `returns the same text when there is nothing to fold`() {
        val text = "Remind me tomorrow"
        assertThat(BengaliNumerals.toAscii(text)).isEqualTo(text)
    }

    @Test
    fun `round trips through both directions`() {
        assertThat(BengaliNumerals.toAscii(BengaliNumerals.toBengali("meeting at 10:30")))
            .isEqualTo("meeting at 10:30")
    }

    @Test
    fun `recognises which digits belong to which script`() {
        assertThat(BengaliNumerals.isBengaliDigit('৭')).isTrue()
        assertThat(BengaliNumerals.isBengaliDigit('7')).isFalse()
        assertThat(BengaliNumerals.isAsciiDigit('7')).isTrue()
        assertThat(BengaliNumerals.isAsciiDigit('৭')).isFalse()
    }
}
