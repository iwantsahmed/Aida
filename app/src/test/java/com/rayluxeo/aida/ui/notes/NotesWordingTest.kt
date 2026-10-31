package com.rayluxeo.aida.ui.notes

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.NoteSort
import com.rayluxeo.aida.ui.notes.detail.NoteAction
import com.rayluxeo.aida.ui.notes.detail.noteDetailWording
import org.junit.Test

/**
 * The phrasebook is the one place a bilingual screen slips, and it slips silently: a view added
 * to [NotesView] with no Bengali label still compiles, and Abu finds out by reading an English
 * word in the middle of a Bengali list.
 *
 * These tests walk every enum rather than sampling, so a value added later cannot go unworded.
 */
class NotesWordingTest {

    private val english = notesWording(SpokenLanguage.ENGLISH)
    private val bengali = notesWording(SpokenLanguage.BENGALI)
    private val englishEditor = noteDetailWording(SpokenLanguage.ENGLISH)
    private val bengaliEditor = noteDetailWording(SpokenLanguage.BENGALI)

    @Test
    fun `every view is named and has an empty state in both languages`() {
        NotesView.entries.forEach { view ->
            assertThat(english.viewLabel(view)).isNotEmpty()
            assertThat(bengali.viewLabel(view)).isNotEmpty()
            assertThat(english.emptyMessage(view)).isNotEmpty()
            assertThat(bengali.emptyMessage(view)).isNotEmpty()
        }
    }

    /** Two views wearing the same chip label would be two chips Abu cannot tell apart. */
    @Test
    fun `no two views share a label`() {
        assertThat(NotesView.entries.map { english.viewLabel(it) }).containsNoDuplicates()
        assertThat(NotesView.entries.map { bengali.viewLabel(it) }).containsNoDuplicates()
    }

    @Test
    fun `every sort names itself and both of its directions`() {
        NoteSort.entries.forEach { sort ->
            assertThat(english.sortLabel(sort)).isNotEmpty()
            assertThat(bengali.sortLabel(sort)).isNotEmpty()
            assertThat(english.sortDirection(sort, ascending = true))
                .isNotEqualTo(english.sortDirection(sort, ascending = false))
            assertThat(bengali.sortDirection(sort, ascending = true))
                .isNotEqualTo(bengali.sortDirection(sort, ascending = false))
        }
    }

    @Test
    fun `every action on a note is named in both languages, and none twice`() {
        NoteAction.entries.forEach { action ->
            assertThat(englishEditor.actionLabel(action)).isNotEmpty()
            assertThat(bengaliEditor.actionLabel(action)).isNotEmpty()
        }
        assertThat(NoteAction.entries.map { englishEditor.actionLabel(it) }).containsNoDuplicates()
        assertThat(NoteAction.entries.map { bengaliEditor.actionLabel(it) }).containsNoDuplicates()
    }

    /**
     * The reasons are read as "<action> — <reason>", so a reason that ended in a full stop or
     * began with a capital would read as two sentences glued together.
     */
    @Test
    fun `every reason an action is unavailable is worded in both languages`() {
        val reasons = listOf(
            englishEditor.saveFirst,
            englishEditor.restoreFirst,
            englishEditor.linkClientFirst,
            englishEditor.needsWords,
            bengaliEditor.saveFirst,
            bengaliEditor.restoreFirst,
            bengaliEditor.linkClientFirst,
            bengaliEditor.needsWords,
        )
        reasons.forEach { reason ->
            assertThat(reason).isNotEmpty()
            assertThat(reason.endsWith(".")).isFalse()
            assertThat(reason.endsWith("।")).isFalse()
        }
    }

    /**
     * The line about handwriting is a sentence in its own right now, not a fragment hung off an
     * action label, so it ends the way a sentence ends in each language.
     */
    @Test
    fun `the handwriting line is a full sentence in both languages`() {
        assertThat(englishEditor.inkReadWhileWriting).endsWith(".")
        assertThat(bengaliEditor.inkReadWhileWriting).endsWith("।")
    }

    /**
     * Saving a note and filing one away are different things, and Bengali said both with the same
     * sentence: `archive` was the word `save` uses, and "নোট সংরক্ষিত হয়েছে।" answered both. Abu
     * filed a note and read the line he reads when he saves one, which is how a note he meant to
     * file stays in the list.
     */
    @Test
    fun `no two things Mira says about a note are the same sentence`() {
        listOf(englishEditor, bengaliEditor).forEach { words ->
            assertThat(
                listOf(
                    words.noteSaved,
                    words.noteArchived,
                    words.noteUnarchived,
                    words.noteDeleted,
                    words.noteRestored,
                    words.organizeApplied,
                ),
            ).containsNoDuplicates()
            assertThat(listOf(words.save, words.archive, words.unarchive, words.restore))
                .containsNoDuplicates()
        }
    }

    /** The list and the editor file the same note, so they must not describe it differently. */
    @Test
    fun `the list and the editor agree about filing a note`() {
        assertThat(english.noteArchived).isEqualTo(englishEditor.noteArchived)
        assertThat(bengali.noteArchived).isEqualTo(bengaliEditor.noteArchived)
        assertThat(english.archive).isEqualTo(englishEditor.archive)
        assertThat(bengali.archive).isEqualTo(bengaliEditor.archive)
        assertThat(bengali.noteArchived).isNotEqualTo(bengaliEditor.noteSaved)
    }

    /** An empty list under a label filter must not tell Abu he has written nothing at all. */
    @Test
    fun `a label filter that matches nothing has its own line in both languages`() {
        assertThat(english.noTagMatches).isNotEmpty()
        assertThat(bengali.noTagMatches).isNotEmpty()
        assertThat(english.noTagMatches).isNotEqualTo(english.emptyMessage(NotesView.ALL))
        assertThat(bengali.noTagMatches).isNotEqualTo(bengali.emptyMessage(NotesView.ALL))
    }

    /**
     * The back arrow's label is the one string on the list Abu cannot see is wrong, because only
     * TalkBack reads it. Left to the top bar's own default it announced the English word "Back"
     * in the middle of a Bengali screen.
     */
    @Test
    fun `the back arrow is labelled in both languages`() {
        assertThat(english.back).isNotEmpty()
        assertThat(bengali.back).isNotEmpty()
        assertThat(bengali.back.any { it in 'a'..'z' || it in 'A'..'Z' }).isFalse()
    }

    @Test
    fun `a Bengali count is written in Bengali digits`() {
        assertThat(bengaliEditor.tasksCreated(3)).contains("৩")
        assertThat(englishEditor.tasksCreated(3)).contains("3")
    }

    @Test
    fun `a file size is worded rather than printed as bytes in both languages`() {
        assertThat(englishEditor.fileSize(2_048L)).isEqualTo("2 KB")
        assertThat(bengaliEditor.fileSize(2_048L)).contains("২")
    }

    /**
     * A recording's length sat beside a moment that was already in Bengali numerals, so "2:45 ·
     * কাল, সকাল ৯টা" put two numeral systems on one line. The clock digits are wording like every
     * other number here.
     */
    @Test
    fun `a recording's length is written in each language's own digits`() {
        assertThat(englishEditor.voiceNoteLabel(minutes = 2L, seconds = 5L, moment = "today"))
            .startsWith("2:05")
        val bengaliLabel = bengaliEditor.voiceNoteLabel(minutes = 2L, seconds = 5L, moment = "আজ")
        assertThat(bengaliLabel).startsWith("২:০৫")
        assertThat(bengaliLabel.any { it in '0'..'9' }).isFalse()
    }
}
