package com.rayluxeo.aida.ui.scanner

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.scanner.ScanType
import org.junit.Test

/**
 * The Scanner screen's phrasebook is the one place its title, the scan-type and category chips, the
 * form labels and every notice Mira says back get their words, so a missed translation here is not a
 * red build — it is an English label sitting in a Bengali screen, the exact defect the Scanner
 * screen shipped with until this phrasebook replaced its hard-coded English.
 *
 * Every assertion walks the whole of what the screen can say rather than sampling it: a scan type or
 * an expense category added to its enum and forgotten in one language, or a line pasted across from
 * English and never translated, is exactly the gap a human eye skips.
 */
class ScannerWordingTest {

    private val english = scannerWording(SpokenLanguage.ENGLISH)
    private val bengali = scannerWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishScannerWording)
        assertThat(bengali).isSameInstanceAs(BengaliScannerWording)
    }

    @Test
    fun `every fixed label is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            fixedLabelSelectors.forEach { select ->
                assertThat(select(words)).isNotEmpty()
            }
        }
    }

    @Test
    fun `no fixed label was left in English on the Bengali side`() {
        fixedLabelSelectors.forEach { select ->
            assertThat(select(bengali)).isNotEqualTo(select(english))
        }
    }

    @Test
    fun `every scan type is labelled in both languages, and never in English on the Bengali side`() {
        ScanType.entries.forEach { type ->
            assertThat(english.scanTypeLabel(type)).isNotEmpty()
            assertThat(bengali.scanTypeLabel(type)).isNotEmpty()
            assertThat(bengali.scanTypeLabel(type)).isNotEqualTo(english.scanTypeLabel(type))
        }
    }

    @Test
    fun `every expense category is labelled in both languages, and never in English on the Bengali side`() {
        ExpenseCategory.entries.forEach { category ->
            assertThat(english.expenseCategoryLabel(category)).isNotEmpty()
            assertThat(bengali.expenseCategoryLabel(category)).isNotEmpty()
            assertThat(bengali.expenseCategoryLabel(category))
                .isNotEqualTo(english.expenseCategoryLabel(category))
        }
    }

    /** Two chips wearing the same label would be two chips Abu cannot tell apart. */
    @Test
    fun `no two scan types or categories share a label`() {
        assertThat(ScanType.entries.map { english.scanTypeLabel(it) }).containsNoDuplicates()
        assertThat(ScanType.entries.map { bengali.scanTypeLabel(it) }).containsNoDuplicates()
        assertThat(ExpenseCategory.entries.map { english.expenseCategoryLabel(it) }).containsNoDuplicates()
        assertThat(ExpenseCategory.entries.map { bengali.expenseCategoryLabel(it) }).containsNoDuplicates()
    }

    @Test
    fun `every worded sentence with data in it is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.ocrFailed("boom")).isNotEmpty()
            assertThat(words.ocrFailed(null)).isNotEmpty()
            assertThat(words.receiptFileName(MERCHANT)).isNotEmpty()
            assertThat(words.businessCardFileName(1_700_000_000_000L)).isNotEmpty()
            assertThat(words.expenseSaved("1200", MERCHANT)).isNotEmpty()
            assertThat(words.failedToSaveExpense("boom")).isNotEmpty()
            assertThat(words.clientCreated(NAME)).isNotEmpty()
            assertThat(words.failedToCreateClient("boom")).isNotEmpty()
            assertThat(words.noteSaved(TITLE)).isNotEmpty()
            assertThat(words.failedToSaveNote("boom")).isNotEmpty()
            assertThat(words.documentSaved(TITLE)).isNotEmpty()
            assertThat(words.failedToSaveDocument("boom")).isNotEmpty()
            assertThat(words.askMiraDocumentPrompt(TEXT)).isNotEmpty()
            assertThat(words.askMiraHandwritingPrompt(TEXT)).isNotEmpty()
        }
    }

    @Test
    fun `no worded sentence with data in it was left in English on the Bengali side`() {
        assertThat(bengali.ocrFailed("boom")).isNotEqualTo(english.ocrFailed("boom"))
        assertThat(bengali.expenseSaved("1200", MERCHANT))
            .isNotEqualTo(english.expenseSaved("1200", MERCHANT))
        assertThat(bengali.clientCreated(NAME)).isNotEqualTo(english.clientCreated(NAME))
        assertThat(bengali.noteSaved(TITLE)).isNotEqualTo(english.noteSaved(TITLE))
        assertThat(bengali.documentSaved(TITLE)).isNotEqualTo(english.documentSaved(TITLE))
        assertThat(bengali.askMiraDocumentPrompt(TEXT)).isNotEqualTo(english.askMiraDocumentPrompt(TEXT))
        assertThat(bengali.askMiraHandwritingPrompt(TEXT))
            .isNotEqualTo(english.askMiraHandwritingPrompt(TEXT))
    }

    @Test
    fun `the data in a worded sentence is carried through untranslated`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.receiptFileName(MERCHANT)).contains(MERCHANT)
            assertThat(words.expenseSaved("1200", MERCHANT)).contains(MERCHANT)
            assertThat(words.clientCreated(NAME)).contains(NAME)
            assertThat(words.noteSaved(TITLE)).contains(TITLE)
            assertThat(words.documentSaved(TITLE)).contains(TITLE)
            assertThat(words.askMiraDocumentPrompt(TEXT)).contains(TEXT)
            assertThat(words.askMiraHandwritingPrompt(TEXT)).contains(TEXT)
        }
    }

    /** A saved amount sits in a Bengali sentence, so its digits are its own, never Latin on that line. */
    @Test
    fun `a saved amount is written in the language's own digits`() {
        assertThat(english.expenseSaved("1200", MERCHANT)).contains("1200")
        val bengaliLine = bengali.expenseSaved("1200", MERCHANT)
        assertThat(bengaliLine).contains("১২০০")
        assertThat(bengaliLine.contains("1200")).isFalse()
    }

    /**
     * The back arrow's label is the one string Abu cannot see is wrong, because only TalkBack reads
     * it. Left to the top bar's own default it announced the English word in a Bengali screen.
     */
    @Test
    fun `the back arrow is labelled in both languages`() {
        assertThat(english.back).isNotEmpty()
        assertThat(bengali.back).isNotEmpty()
        assertThat(bengali.back.any { it in 'a'..'z' || it in 'A'..'Z' }).isFalse()
    }

    private val fixedLabelSelectors: List<(ScannerWording) -> String> = listOf(
        { it.title },
        { it.back },
        { it.scanTypeSection },
        { it.takePhoto },
        { it.importImage },
        { it.imagePreview },
        { it.scannedImageDescription },
        { it.cleanImage },
        { it.processingOcr },
        { it.extractedInformation },
        { it.merchantLabel },
        { it.amountLabel },
        { it.expenseCategorySection },
        { it.saveExpense },
        { it.cardNameLabel },
        { it.cardCompanyLabel },
        { it.cardPhoneLabel },
        { it.cardEmailLabel },
        { it.cardWebsiteLabel },
        { it.cardAddressLabel },
        { it.createClient },
        { it.saveNote },
        { it.documentTitleLabel },
        { it.extractedTextLabel },
        { it.saveDocument },
        { it.saveAsNote },
        { it.askMiraAboutDocument },
        { it.handwritingLowConfidence },
        { it.noteTitleLabel },
        { it.recognizedTextLabel },
        { it.askMira },
        { it.defaultDocumentTitle },
        { it.defaultHandwrittenTitle },
        { it.defaultSavedNoteTitle },
        { it.defaultMerchant },
        { it.captureReceiptFirst },
        { it.enterValidAmount },
        { it.captureDocumentFirst },
    )

    private companion object {
        const val MERCHANT = "Daraz"
        const val NAME = "Rahim Uddin"
        const val TITLE = "Contract"
        const val TEXT = "Invoice line items"
    }
}
