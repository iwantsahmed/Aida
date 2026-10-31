package com.rayluxeo.aida.ui.notes.detail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * When the editor offers to save, and when it says nothing at all.
 *
 * The note editor is meant to read as a sheet of paper, and the one thing on it that can shout is
 * the save bar. These fix where it may appear, because the rule is easy to lose the next time the
 * screen is rearranged: the control exists exactly while it can do something (AGENTS.md §12), and
 * it survives its own press so that pressing it never looks like a miss.
 */
class NoteDetailUiStateTest {

    @Test
    fun `a note opened and not typed into offers no save`() {
        val state = NoteDetailUiState(isLoading = false, title = "Lease renewal")

        assertThat(state.isDirty).isFalse()
        assertThat(state.canSave).isFalse()
        assertThat(state.isSaveOffered).isFalse()
    }

    @Test
    fun `the first keystroke brings the control out`() {
        val state = NoteDetailUiState(isLoading = false, title = "Lease renewal", isDirty = true)

        assertThat(state.canSave).isTrue()
        assertThat(state.isSaveOffered).isTrue()
    }

    /**
     * Still there, and still refusing a second press. A control that vanished the instant it was
     * pressed would leave Abu unsure whether the press landed, and one that stayed pressable
     * would write the same words twice.
     */
    @Test
    fun `the control stays through the write and takes no second press`() {
        val state = NoteDetailUiState(
            isLoading = false,
            title = "Lease renewal",
            isDirty = true,
            isSaving = true,
        )

        assertThat(state.isSaveOffered).isTrue()
        assertThat(state.canSave).isFalse()
    }

    /** Its going is the confirmation: nothing has to say "saved" once the words are stored. */
    @Test
    fun `the control goes once the words are stored`() {
        val state = NoteDetailUiState(
            isLoading = false,
            title = "Lease renewal",
            isDirty = false,
            isSaving = false,
        )

        assertThat(state.isSaveOffered).isFalse()
    }

    /**
     * A binned note is read-only, so there is nothing to write and nothing to edit. Offering
     * either would be offering something storage refuses for a reason Abu cannot see from here.
     */
    @Test
    fun `a note in the bin offers neither a save nor an edit`() {
        val state = NoteDetailUiState(isLoading = false, title = "Lease renewal", isDeleted = true)

        assertThat(state.canSave).isFalse()
        assertThat(state.isSaveOffered).isFalse()
        assertThat(state.canEdit).isFalse()
    }
}
