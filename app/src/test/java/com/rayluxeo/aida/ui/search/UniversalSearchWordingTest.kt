package com.rayluxeo.aida.ui.search

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.TurnRole
import org.junit.Test

/**
 * Universal Search gets its words from one phrasebook per language, so a missed translation here
 * is not a red build — it is an English heading sitting in a Bengali list, which nobody notices
 * until Abu does.
 *
 * Every assertion below is about a gap a human eye skips: a kind, category, speaker or fixed label
 * added on one side and forgotten on the other, or copy pasted across from English and never
 * translated. Running the English strings against the Bengali copy is the same assertion as "this
 * line was never translated".
 */
class UniversalSearchWordingTest {

    private val english = universalSearchWording(SpokenLanguage.ENGLISH)
    private val bengali = universalSearchWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishUniversalSearchWording)
        assertThat(bengali).isSameInstanceAs(BengaliUniversalSearchWording)
    }

    @Test
    fun `every kind is labelled in both languages, and differently`() {
        SearchResultKind.entries.forEach { kind ->
            assertThat(english.kindLabel(kind)).isNotEmpty()
            assertThat(bengali.kindLabel(kind)).isNotEmpty()
            assertThat(bengali.kindLabel(kind)).isNotEqualTo(english.kindLabel(kind))
        }
    }

    @Test
    fun `every section heading is worded in both languages, and differently`() {
        SearchResultKind.entries.forEach { kind ->
            val en = english.sectionHeader(kind, 3)
            val bn = bengali.sectionHeader(kind, 3)
            assertThat(en).isNotEmpty()
            assertThat(bn).isNotEmpty()
            assertThat(bn).isNotEqualTo(en)
        }
    }

    @Test
    fun `every expense category is labelled in both languages, and differently`() {
        ExpenseCategory.entries.forEach { category ->
            assertThat(english.expenseCategoryLabel(category)).isNotEmpty()
            assertThat(bengali.expenseCategoryLabel(category)).isNotEmpty()
            assertThat(bengali.expenseCategoryLabel(category))
                .isNotEqualTo(english.expenseCategoryLabel(category))
        }
    }

    @Test
    fun `every conversation speaker is named in both languages, and differently`() {
        TurnRole.entries.forEach { role ->
            assertThat(english.conversationSpeaker(role)).isNotEmpty()
            assertThat(bengali.conversationSpeaker(role)).isNotEmpty()
            assertThat(bengali.conversationSpeaker(role))
                .isNotEqualTo(english.conversationSpeaker(role))
        }
    }

    @Test
    fun `every fixed label is written in both languages, and differently`() {
        val fixed: List<UniversalSearchWording.() -> String> = listOf(
            { screenTitle },
            { searchPlaceholder },
            { searching },
            { emptyPrompt },
            { taskFallback },
            { clientFallback },
            { projectFallback },
            { fileFallback },
        )
        fixed.forEach { member ->
            assertThat(member(english)).isNotEmpty()
            assertThat(member(bengali)).isNotEmpty()
            assertThat(member(bengali)).isNotEqualTo(member(english))
        }
    }

    @Test
    fun `every worded result is produced in both languages, and differently`() {
        val amount = Money.of("1500")

        assertThat(english.resultsFound(3)).isNotEmpty()
        assertThat(bengali.resultsFound(3)).isNotEqualTo(english.resultsFound(3))

        assertThat(english.noMatches("ledger")).isNotEmpty()
        assertThat(bengali.noMatches("ledger")).isNotEqualTo(english.noMatches("ledger"))

        assertThat(english.noteTitleFallback(7)).isNotEmpty()
        assertThat(bengali.noteTitleFallback(7)).isNotEqualTo(english.noteTitleFallback(7))

        assertThat(english.invoiceTitle("INV-2026-014")).isNotEmpty()
        assertThat(bengali.invoiceTitle("INV-2026-014"))
            .isNotEqualTo(english.invoiceTitle("INV-2026-014"))

        assertThat(english.invoiceSubtitle(amount)).isNotEmpty()
        assertThat(bengali.invoiceSubtitle(amount)).isNotEqualTo(english.invoiceSubtitle(amount))

        assertThat(english.expenseSubtitle(amount, ExpenseCategory.SOFTWARE)).isNotEmpty()
        assertThat(bengali.expenseSubtitle(amount, ExpenseCategory.SOFTWARE))
            .isNotEqualTo(english.expenseSubtitle(amount, ExpenseCategory.SOFTWARE))
    }

    /**
     * A number inside a Bengali sentence is the one place a bilingual screen always slips. Every
     * count the screen shows is checked for Bengali numerals and against Latin ones, so a "3" can
     * never surface in a Bengali list.
     */
    @Test
    fun `counts in Bengali use Bengali numerals`() {
        val header = bengali.sectionHeader(SearchResultKind.TASKS, 3)
        assertThat(header).contains("৩")
        assertThat(header).doesNotContain("3")

        val total = bengali.resultsFound(12)
        assertThat(total).contains("১২")
        assertThat(total.any { it in '0'..'9' }).isFalse()

        val note = bengali.noteTitleFallback(7)
        assertThat(note).contains("৭")
        assertThat(note.any { it in '0'..'9' }).isFalse()
    }
}
