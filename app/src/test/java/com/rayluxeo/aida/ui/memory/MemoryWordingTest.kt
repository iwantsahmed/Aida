package com.rayluxeo.aida.ui.memory

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemorySource
import org.junit.Test

/**
 * The Memory phrasebook is the one place the screen gets its words, so a missed translation here
 * is not a red build — it is an English label sitting in a Bengali sheet, which nobody notices
 * until Abu does.
 *
 * Every assertion walks all of the members rather than sampling one: the fault this guards against
 * is a line pasted across from English and never translated, a category or source named in one
 * language and forgotten in the other, or a notice variant added without a Bengali rendering.
 */
class MemoryWordingTest {

    private val english = memoryWording(SpokenLanguage.ENGLISH)
    private val bengali = memoryWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishMemoryWording)
        assertThat(bengali).isSameInstanceAs(BengaliMemoryWording)
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
     * Bengali counts in its own digits. A recall count that reads "3 বার" in the middle of a
     * Bengali card is the one slip that reads as broken even to someone who cannot read the words
     * around it, so the counted lines carry no Latin digit.
     */
    @Test
    fun `nothing Bengali is written with Latin digits`() {
        val counted = listOf(
            bengali.recallSummary(MemorySource.TYPED, 3),
            bengali.recallSummary(MemorySource.SPOKEN, 12),
        )

        counted.forEach { line ->
            assertThat(line.none { it in '0'..'9' }).isTrue()
            assertThat(line).isNotEmpty()
        }
    }

    /**
     * The subject a fact is about and the text Abu searched for are data he typed, not words to
     * translate, so they survive into both languages unchanged.
     */
    @Test
    fun `data Abu typed is placed, not translated`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.noticeText(MemoryNotice.Remembered(SUBJECT))).contains(SUBJECT)
            assertThat(words.noticeText(MemoryNotice.Updated(SUBJECT))).contains(SUBJECT)
            assertThat(words.emptyNoMatch(QUERY)).contains(QUERY)
        }
    }

    /** Every member the screen reads, with the same sample data on both sides. */
    private fun MemoryWording.everything(): List<String> = listOf(
        title,
        searchPlaceholder,
        allCategories,
        rememberFact,
        addFact,
        emptyNoMemories,
        emptyNoMatch(QUERY),
        pinMemory,
        editMemory,
        forgetMemory,
        newFactTitle,
        editFactTitle,
        subjectLabel,
        subjectPlaceholder,
        factLabel,
        factPlaceholder,
        categoryLabel,
        storeMemory,
        saveChanges,
    ) +
        MemoryCategory.entries.map { categoryName(it) } +
        MemorySource.entries.map { recallSummary(it, 3) } +
        listOf(
            noticeText(MemoryNotice.Remembered(SUBJECT)),
            noticeText(MemoryNotice.Updated(SUBJECT)),
            noticeText(MemoryNotice.Forgotten),
            noticeText(MemoryNotice.RememberFailed),
            noticeText(MemoryNotice.UpdateFailed),
            noticeText(MemoryNotice.ForgetFailed),
        )

    private companion object {
        const val SUBJECT = "Rayluxeo"
        const val QUERY = "deposit"
    }
}
