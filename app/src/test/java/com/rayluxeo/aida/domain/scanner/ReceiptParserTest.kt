package com.rayluxeo.aida.domain.scanner

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.ExpenseCategory
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReceiptParserTest {

    @Test
    fun `parse receipt extracts merchant date amount and category`() {
        val ocrText = """
            STAR TECH & ENGINEERING
            Dhaka, Bangladesh
            
            Tax Invoice: 10429
            Date: 2026-09-20
            
            1x USB-C Cable   500.00
            1x Wireless Mouse  1200.00
            
            SUBTOTAL: 1700.00
            TOTAL AMOUNT: 1700.00 BDT
            
            THANK YOU FOR SHOPPING
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText, "BDT")

        assertThat(parsed.merchant).isEqualTo("STAR TECH & ENGINEERING")
        assertThat(parsed.date).isEqualTo(LocalDate.of(2026, 9, 20))
        assertThat(parsed.amount?.amount).isEqualTo(BigDecimal("1700.00"))
        assertThat(parsed.amount?.currency?.currencyCode).isEqualTo("BDT")
        assertThat(parsed.category).isEqualTo(ExpenseCategory.HARDWARE)
    }

    @Test
    fun `parse receipt with restaurant detects food category`() {
        val ocrText = """
            Sultans Dine
            Dhanmondi, Dhaka
            
            Date: 15 Oct 2026
            Kacchi Biryani x2   800.00
            Borhani x2          120.00
            
            GRAND TOTAL: 920.00 BDT
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText, "BDT")

        assertThat(parsed.merchant).isEqualTo("Sultans Dine")
        assertThat(parsed.amount?.amount).isEqualTo(BigDecimal("920.00"))
        assertThat(parsed.category).isEqualTo(ExpenseCategory.FOOD)
    }

    @Test
    fun `parse receipt with uber detects travel category`() {
        val ocrText = """
            Uber Ride Receipt
            Date: 2026-10-05
            
            Trip Fare: 350.00
            TOTAL: 350.00 BDT
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText, "BDT")

        assertThat(parsed.merchant).isEqualTo("Uber Ride Receipt")
        assertThat(parsed.category).isEqualTo(ExpenseCategory.TRAVEL)
    }
}
