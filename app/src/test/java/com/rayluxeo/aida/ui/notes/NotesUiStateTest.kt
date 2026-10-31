package com.rayluxeo.aida.ui.notes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Where the notes list's one champagne goes.
 *
 * The empty list is what this screen shows most of the time, and it had four accented things on it
 * at once — two of which were the same action drawn twice, at two sizes, in two shapes. The rule
 * that fixed it is a property rather than a condition buried in a composable precisely so it can
 * be pinned here: exactly one of the top bar's Add glyph and the empty state's button may be
 * champagne, and which one depends on whether the empty list has a way forward to offer at all.
 *
 * Each case below is one branch of what `NotesScreen` actually draws in the empty state. They have
 * to agree, and nothing in the compiler makes them.
 */
class NotesUiStateTest {

    private val oneNote = listOf(
        NoteSection(
            key = "all",
            title = null,
            items = listOf(NoteListItem(id = 1L, title = "Lease renewal", supporting = null)),
        ),
    )

    @Test
    fun `a list with notes in it draws no way forward, so the glyph keeps the accent`() {
        val state = NotesUiState(isLoading = false, sections = oneNote)

        assertThat(state.isEmpty).isFalse()
        assertThat(state.isOfferingWayForward).isFalse()
    }

    /** An empty section list is still an empty list: the rows are what count, not the headings. */
    @Test
    fun `a section with no rows in it is empty`() {
        val state = NotesUiState(
            isLoading = false,
            sections = listOf(NoteSection(key = "all", title = null, items = emptyList())),
        )

        assertThat(state.isEmpty).isTrue()
    }

    @Test
    fun `an empty list Abu can write into offers to write the first note`() {
        assertThat(NotesUiState(isLoading = false, view = NotesView.ALL).isOfferingWayForward)
            .isTrue()
        assertThat(NotesUiState(isLoading = false, view = NotesView.PINNED).isOfferingWayForward)
            .isTrue()
    }

    /**
     * The bin, the archive and the handwriting view offer nothing, because there is no way forward
     * from them and inventing one would be a button that does not belong. With no button under it,
     * the top bar's Add is the only primary on the screen and keeps its champagne.
     */
    @Test
    fun `an empty bin, archive or handwriting list offers nothing`() {
        listOf(NotesView.BIN, NotesView.ARCHIVED, NotesView.INK).forEach { view ->
            assertThat(NotesUiState(isLoading = false, view = view).isOfferingWayForward).isFalse()
        }
    }

    /** A search that matched nothing offers to clear itself, in every view including the bin. */
    @Test
    fun `a search that matched nothing offers a way forward from any view`() {
        val state = NotesUiState(isLoading = false, view = NotesView.BIN, query = "lease")

        assertThat(state.isOfferingWayForward).isTrue()
    }

    /** Blank is not a search: spaces in the field must not count as something to clear. */
    @Test
    fun `whitespace in the search field is not a search`() {
        val state = NotesUiState(isLoading = false, view = NotesView.ARCHIVED, query = "   ")

        assertThat(state.isOfferingWayForward).isFalse()
    }

    @Test
    fun `a label filter that matched nothing offers to clear the labels`() {
        val state = NotesUiState(
            isLoading = false,
            view = NotesView.ARCHIVED,
            tags = listOf(NoteTagChip(id = 1L, label = "rent", isSelected = true)),
        )

        assertThat(state.hasTagFilter).isTrue()
        assertThat(state.isOfferingWayForward).isTrue()
    }

    /** Offered is not the same as chosen: an unselected chip narrows nothing. */
    @Test
    fun `labels that are merely offered are not a filter`() {
        val state = NotesUiState(
            isLoading = false,
            view = NotesView.BIN,
            tags = listOf(NoteTagChip(id = 1L, label = "rent", isSelected = false)),
        )

        assertThat(state.hasTagFilter).isFalse()
        assertThat(state.isOfferingWayForward).isFalse()
    }

    /** A full list never offers a way forward, whatever is narrowing it. */
    @Test
    fun `a list with rows offers nothing even under a search`() {
        val state = NotesUiState(isLoading = false, sections = oneNote, query = "lease")

        assertThat(state.isOfferingWayForward).isFalse()
    }

    /**
     * A list that has not arrived yet has no rows, and every state with no rows is empty — so
     * without this the glyph would go quiet under the loading arc and brighten again the moment
     * the notes landed, which is a colour changing for no reason Abu did anything to cause.
     */
    @Test
    fun `a list that is still loading offers nothing`() {
        val state = NotesUiState(isLoading = true, view = NotesView.ALL)

        assertThat(state.isEmpty).isTrue()
        assertThat(state.isOfferingWayForward).isFalse()
    }
}
