package com.rayluxeo.aida.ui.invoices

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import java.math.BigDecimal
import org.junit.Test

/**
 * The Invoices phrasebook is the one place the screen gets its words, so a missed translation here
 * is not a red build — it is an English label sitting in a Bengali screen, which nobody notices
 * until Abu does. The screen was English-only once; this test is what keeps it from drifting back.
 *
 * Every assertion walks all of the members rather than sampling one: the fault this guards against
 * is a line pasted across from English and never translated, or a member added in one language and
 * forgotten in the other.
 */
class InvoicesWordingTest {

    private val english = invoicesWording(SpokenLanguage.ENGLISH)
    private val bengali = invoicesWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishInvoicesWording)
        assertThat(bengali).isSameInstanceAs(BengaliInvoicesWording)
    }

    @Test
    fun `every word is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            words.everything().forEach { line ->
                assertThat(line).isNotEmpty()
            }
        }
    }

    @Test
    fun `no line was left in English on the Bengali side`() {
        english.everything().zip(bengali.everything()).forEach { (englishLine, bengaliLine) ->
            assertThat(bengaliLine).isNotEqualTo(englishLine)
        }
    }

    /**
     * Bengali counts in its own digits. A tab that reads "Invoices (৩)" with a Latin 3 in the middle
     * of a Bengali screen is the one slip that reads as broken even to someone who cannot read the
     * words around it, so every counted or quantity line carries no Latin digit.
     */
    @Test
    fun `nothing counted in Bengali is written with Latin digits`() {
        val counted = listOf(
            bengali.invoicesTab(3),
            bengali.quotesTab(12),
            bengali.recurringTab(5),
            bengali.lineItems(7),
            bengali.lineQuantity(BigDecimal("2")),
            bengali.pdfPreviewTitle(3),
            bengali.noticeText(InvoicesNotice.RecurringGenerated(4)),
        )

        counted.forEach { line ->
            assertThat(line.none { it in '0'..'9' }).isTrue()
            assertThat(line).isNotEmpty()
        }
    }

    /**
     * An invoice or quote number is a code Abu assigned, not a word to translate, so it survives into
     * both languages unchanged — in a recurring template row and in the notices alike.
     */
    @Test
    fun `a number Abu assigned is placed, not translated`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.recurringTemplate(INVOICE_NO)).contains(INVOICE_NO)
            assertThat(words.noticeText(InvoicesNotice.ClientNotFound(INVOICE_NO))).contains(INVOICE_NO)
            words.noticeText(InvoicesNotice.QuoteConverted(QUOTE_NO, INVOICE_NO)).let { line ->
                assertThat(line).contains(QUOTE_NO)
                assertThat(line).contains(INVOICE_NO)
            }
        }
    }

    /** Every member the screen reads, with the same sample data on both sides, in the same order. */
    private fun InvoicesWording.everything(): List<String> = buildList {
        add(title)
        add(subtitle)
        add(searchPlaceholder)
        add(invoicesTab(3))
        add(quotesTab(12))
        add(recurringTab(5))
        InvoiceFilter.entries.forEach { add(filterLabel(it)) }
        add(noInvoices)
        add(noQuotes)
        add(noRecurring)
        add(automatedSchedules)
        add(sweepDueNow)
        add(recurringTemplate(INVOICE_NO))
        add(recurringClient(CLIENT))
        add(recurringNextDraft(DATE))
        SubscriptionCycle.entries.forEach { add(cycleLabel(it)) }
        add(draftPlaceholder)
        add(unknownClient)
        add(unknown)
        add(total(AMOUNT))
        add(dueBy(AMOUNT, DATE))
        add(previewPdfAction)
        add(deliverInvoiceAction)
        InvoiceStatus.entries.forEach { add(invoiceStatus(it)) }
        QuoteStatus.entries.forEach { add(quoteStatus(it)) }
        add(convertToInvoice)
        add(converted)
        add(close)
        add(labelClient)
        add(labelIssued)
        add(labelDue)
        add(labelCurrency)
        add(lineItems(7))
        add(lineQuantity(BigDecimal("2")))
        add(labelSubtotal)
        add(labelDiscount)
        add(labelTax)
        add(labelTotal)
        add(labelAmountPaid)
        add(labelBalanceDue)
        add(notesPaymentInfo)
        add(previewPdf)
        add(deliver)
        add(pdfPreviewTitle(1))
        add(pdfPreviewTitle(3))
        add(closePreview)
        add(invoicePdfPage)
        add(noticeText(InvoicesNotice.QuoteConverted(QUOTE_NO, INVOICE_NO)))
        add(noticeText(InvoicesNotice.QuoteConversionFailed(null)))
        add(noticeText(InvoicesNotice.ClientNotFound(INVOICE_NO)))
        add(noticeText(InvoicesNotice.PdfPreviewFailed(null)))
        add(noticeText(InvoicesNotice.PdfGenerationFailed(null)))
        add(noticeText(InvoicesNotice.DeliveryPdfFailed(null)))
        add(noticeText(InvoicesNotice.InvoiceShared(INVOICE_NO, InvoiceDeliveryMethod.EMAIL)))
        add(noticeText(InvoicesNotice.DeliveryFailed(null)))
        add(noticeText(InvoicesNotice.RecurringGenerated(4)))
        add(noticeText(InvoicesNotice.NoRecurringDue))
        add(noticeText(InvoicesNotice.RecurringFailed(null)))
    }

    private companion object {
        const val INVOICE_NO = "INV-2026-014"
        const val QUOTE_NO = "QUO-2026-003"
        const val CLIENT = "Ahmed Traders"
        const val AMOUNT = "৳45,000"
        const val DATE = "14 Oct 2026"
    }
}
