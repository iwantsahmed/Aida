package com.rayluxeo.aida.ui.scanner

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.FileSource
import org.junit.Test

/**
 * The Files screen's phrasebook is the one place the title, the source chips, the detail sheet and
 * the undo notice get their words, so a missed translation here is not a red build — it is an
 * English label sitting in a Bengali list, which nobody notices until Abu does.
 *
 * Every assertion walks the whole of what the screen can say rather than sampling it: a source
 * added to [FileSource] and forgotten in one language, or a line pasted across from English and
 * never translated, is exactly the gap a human eye skips.
 */
class FilesWordingTest {

    private val english = filesWording(SpokenLanguage.ENGLISH)
    private val bengali = filesWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishFilesWording)
        assertThat(bengali).isSameInstanceAs(BengaliFilesWording)
    }

    @Test
    fun `every fixed label is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            fixedLabels(words).forEach { label ->
                assertThat(label).isNotEmpty()
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
    fun `every source is labelled in both languages, and never in English on the Bengali side`() {
        FileSource.entries.forEach { source ->
            assertThat(english.sourceLabel(source)).isNotEmpty()
            assertThat(bengali.sourceLabel(source)).isNotEmpty()
            assertThat(bengali.sourceLabel(source)).isNotEqualTo(english.sourceLabel(source))
        }
    }

    /** Two sources wearing the same chip label would be two chips Abu cannot tell apart. */
    @Test
    fun `no two sources share a label`() {
        assertThat(FileSource.entries.map { english.sourceLabel(it) }).containsNoDuplicates()
        assertThat(FileSource.entries.map { bengali.sourceLabel(it) }).containsNoDuplicates()
    }

    @Test
    fun `every worded sentence with data in it is written in both languages`() {
        val name = "Contract"
        val mime = "application/pdf"
        listOf(english, bengali).forEach { words ->
            assertThat(words.fileSize(2_048L)).isNotEmpty()
            assertThat(words.fileDetails(FileSource.SCAN, 2_048L, mime)).isNotEmpty()
            assertThat(words.deleteMessage(name)).isNotEmpty()
            assertThat(words.askMiraPrompt(name)).isNotEmpty()
        }
    }

    @Test
    fun `no worded sentence with data in it was left in English on the Bengali side`() {
        val name = "Contract"
        val mime = "application/pdf"
        assertThat(bengali.fileSize(2_048L)).isNotEqualTo(english.fileSize(2_048L))
        assertThat(bengali.fileDetails(FileSource.SCAN, 2_048L, mime))
            .isNotEqualTo(english.fileDetails(FileSource.SCAN, 2_048L, mime))
        assertThat(bengali.deleteMessage(name)).isNotEqualTo(english.deleteMessage(name))
        assertThat(bengali.askMiraPrompt(name)).isNotEqualTo(english.askMiraPrompt(name))
    }

    @Test
    fun `the data in a worded sentence is carried through untranslated`() {
        val name = "Contract"
        val mime = "application/pdf"
        assertThat(bengali.deleteMessage(name)).contains(name)
        assertThat(bengali.askMiraPrompt(name)).contains(name)
        assertThat(bengali.fileDetails(FileSource.SCAN, 2_048L, mime)).contains(mime)
    }

    /**
     * A file size sits beside a Bengali chip, so its digits are wording like every other number
     * here: English prints Latin digits, Bengali its own, and never the two on one line.
     */
    @Test
    fun `a file size is written in each language's own digits`() {
        assertThat(english.fileSize(2_048L)).isEqualTo("2 KB")
        val bengaliSize = bengali.fileSize(2_048L)
        assertThat(bengaliSize).contains("২")
        assertThat(bengaliSize.any { it in '0'..'9' }).isFalse()
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

    private val fixedLabelSelectors: List<(FilesWording) -> String> = listOf(
        { it.screenTitle },
        { it.back },
        { it.dismiss },
        { it.undo },
        { it.searchPlaceholder },
        { it.allFilter },
        { it.openScanner },
        { it.emptyFiles },
        { it.extractedTextLabel },
        { it.rename },
        { it.delete },
        { it.linkClient },
        { it.linkProject },
        { it.askMiraAboutDocument },
        { it.renameTitle },
        { it.fileNameLabel },
        { it.save },
        { it.cancel },
        { it.deleteTitle },
        { it.linkToClient },
        { it.linkToProject },
        { it.unlink },
        { it.nothingToLink },
        { it.linked },
        { it.renamed },
        { it.fileMovedToBin },
    )

    private fun fixedLabels(words: FilesWording): List<String> = fixedLabelSelectors.map { it(words) }
}
