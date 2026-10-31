package com.rayluxeo.aida.ui.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * When the canvas offers its controls.
 *
 * Each of these is AGENTS.md §12 in miniature: a save button that does nothing, a save button
 * that refuses the one thing Abu actually needs it for, or a hint sitting under his first word.
 */
class InkCanvasUiStateTest {

    @Test
    fun `an untouched new page offers nothing to save`() {
        val state = InkCanvasUiState(loading = false)

        assertThat(state.canSave).isFalse()
        assertThat(state.canClear).isFalse()
        assertThat(state.showEmptyHint).isTrue()
    }

    @Test
    fun `a page with writing on it can be saved`() {
        val state = InkCanvasUiState(
            loading = false,
            strokeCount = 2,
            hasMarks = true,
            hasUnsavedWork = true,
        )

        assertThat(state.canSave).isTrue()
        assertThat(state.showEmptyHint).isFalse()
    }

    @Test
    fun `a saved page Abu has rubbed clean is offered as a removal, not a save`() {
        val state = InkCanvasUiState(
            loading = false,
            pageId = 7L,
            strokeCount = 0,
            hasUnsavedWork = true,
        )

        // The control has to be there: refusing it would leave the page on disk saying the
        // opposite of what is in front of him. But storage will not hold a page with nothing on
        // it, so what the control does is take the page off the note — and it says so, rather
        // than reading "Save page" and failing with an instruction to try again.
        assertThat(state.canSave).isTrue()
        assertThat(state.saveIsRemoval).isTrue()
    }

    @Test
    fun `a page with writing on it is saved rather than removed`() {
        val state = InkCanvasUiState(
            loading = false,
            pageId = 7L,
            strokeCount = 2,
            hasUnsavedWork = true,
        )

        assertThat(state.saveIsRemoval).isFalse()
    }

    @Test
    fun `an empty page that was never stored has nothing to remove`() {
        val state = InkCanvasUiState(loading = false, strokeCount = 0, hasUnsavedWork = true)

        assertThat(state.canSave).isFalse()
        assertThat(state.saveIsRemoval).isFalse()
    }

    @Test
    fun `nothing is offered twice while a save is in flight`() {
        val state = InkCanvasUiState(
            loading = false,
            strokeCount = 2,
            hasUnsavedWork = true,
            saving = true,
        )

        assertThat(state.canSave).isFalse()
    }

    /**
     * The control is not merely dimmed on an untouched page — it is not there.
     *
     * A champagne button shown dim above a blank page for the whole time Abu is writing is the
     * loudest thing on a screen meant to be nothing but ink, and §12 calls a button that does
     * nothing a defect rather than a decoration.
     */
    @Test
    fun `the save control is not on the bar until there is something to write`() {
        val untouched = InkCanvasUiState(loading = false)

        assertThat(untouched.isSaveOffered).isFalse()

        val written = InkCanvasUiState(
            loading = false,
            strokeCount = 2,
            hasMarks = true,
            hasUnsavedWork = true,
        )

        assertThat(written.isSaveOffered).isTrue()
    }

    /** Pressing it must not make it disappear: a press whose control vanishes reads as a miss. */
    @Test
    fun `the save control stays, refusing a second press, while the write is in flight`() {
        val state = InkCanvasUiState(
            loading = false,
            strokeCount = 2,
            hasUnsavedWork = true,
            saving = true,
        )

        assertThat(state.isSaveOffered).isTrue()
        assertThat(state.canSave).isFalse()
    }

    /** Once the page is stored there is nothing left to write, so the control goes on its own. */
    @Test
    fun `a stored page offers no save until it is written on again`() {
        val state = InkCanvasUiState(
            loading = false,
            pageId = 7L,
            strokeCount = 2,
            hasMarks = true,
            hasUnsavedWork = false,
        )

        assertThat(state.isSaveOffered).isFalse()
    }

    @Test
    fun `the hint goes on the first contact rather than on the first finished stroke`() {
        val state = InkCanvasUiState(loading = false, strokeCount = 0, hasMarks = true)

        assertThat(state.showEmptyHint).isFalse()
    }
}
