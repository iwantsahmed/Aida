package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.NoteSort

/**
 * The five lists Abu can ask for, each one a preset over [com.rayluxeo.aida.domain.model.NoteQuery].
 *
 * A view owns exactly one decision — which notes the query asks for — so adding a view cannot
 * leave half of it wired. Archived and the bin are their own views rather than a flag on [ALL],
 * because filing a note away and deleting it are different things and a list that quietly mixed
 * them would make archiving indistinguishable from doing nothing.
 */
enum class NotesView(val filter: NoteFilter) {
    ALL(NoteFilter.All),
    PINNED(NoteFilter.Pinned),
    INK(NoteFilter.Ink),
    ARCHIVED(NoteFilter.Archived),
    BIN(NoteFilter.Deleted),
}

enum class NoteNoticeTone { CONFIRM, PROBLEM }

/**
 * A short line about what just happened, with an undo when the action can genuinely be taken back.
 * [undoLabel] is null when it cannot — Mira never offers a button that does nothing.
 */
data class NotesNotice(
    val message: String,
    val tone: NoteNoticeTone,
    val undoLabel: String? = null,
)

/**
 * One note as the list shows it.
 *
 * Everything here is already worded in Abu's language: the view model is where the language is
 * known, so a composable never has to choose between an English literal and a Bengali one.
 *
 * A row carries no flag for pinned, filed or binned, and none of the three is an oversight.
 * Every one of them is already said by the list around the row: pinned notes arrive in a section
 * with a heading that names them, an archived note is only ever reached through the archived view,
 * and the bin is a view of its own. A glyph repeating that would say nothing twice — and, because
 * only some rows would carry one, it would push those rows' titles in from the left edge every
 * other row on the screen shares. The sheet a row opens reads the note itself, which is where
 * these states are actually needed.
 */
data class NoteListItem(
    val id: Long,
    val title: String,
    /** The moment it was last touched, and the labels it wears. Null when it wears none. */
    val supporting: String?,
)

/**
 * One heading and the rows under it.
 *
 * A list with nothing pinned is a single section with no [title], so the screen has one shape
 * whether or not anything floats above and does not branch twice over the same data.
 */
data class NoteSection(
    val key: String,
    val title: String?,
    val items: List<NoteListItem>,
)

/** One label Abu can narrow the list to. Only tags that appear on a note are ever offered. */
data class NoteTagChip(
    val id: Long,
    val label: String,
    val isSelected: Boolean,
)

/** How the list is ordered. Every sort reverses, so the direction travels with it. */
data class NoteSortUiState(
    val sort: NoteSort = NoteSort.UPDATED,
    val ascending: Boolean = false,
)

/**
 * The sheet a row's "more" glyph opens.
 *
 * Which verbs it offers is decided from the note's own state rather than from the view it was
 * tapped in: a note reached from the bin can only be restored, and pinning one that is there
 * would be refused by storage for a reason Abu could not see.
 */
data class NoteActionsUiState(
    val noteId: Long,
    val title: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isDeleted: Boolean,
)

data class NotesUiState(
    val isLoading: Boolean = true,
    /** Which language the screen's own labels are written in; the rows are worded to match. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val view: NotesView = NotesView.ALL,
    val query: String = "",
    val sections: List<NoteSection> = emptyList(),
    val tags: List<NoteTagChip> = emptyList(),
    val sort: NoteSortUiState = NoteSortUiState(),
    val actions: NoteActionsUiState? = null,
    val notice: NotesNotice? = null,
) {
    val isEmpty: Boolean get() = sections.none { it.items.isNotEmpty() }

    val hasTagFilter: Boolean get() = tags.any { it.isSelected }

    /**
     * Whether the empty list is drawing a way forward of its own.
     *
     * It decides where the screen's one champagne goes. An empty list is what this screen shows
     * most of the time, and it used to show four accented things at once — the Add glyph, the
     * selected view chip, the empty state's glyph and a solid champagne button under it — with the
     * glyph and the button being the *same* action, drawn twice at two sizes. A screen with
     * champagne in four places has none.
     *
     * So exactly one of the two carries it: when the empty list offers a button, that button is
     * the primary and the Add glyph goes quiet; when it offers nothing — the bin and the archive,
     * where there is no way forward to offer — the glyph keeps the accent. It is a property rather
     * than a condition inside a composable because it is a rule, and rules are worth testing.
     *
     * The four cases have to agree with what the empty state actually draws: a search, a label
     * filter, and the two views a note can be written into. A list still loading draws none of
     * them — it draws the arc — so the glyph must not go quiet and then brighten again as the
     * rows arrive.
     */
    val isOfferingWayForward: Boolean
        get() = !isLoading && isEmpty &&
            (query.isNotBlank() || hasTagFilter || view == NotesView.ALL || view == NotesView.PINNED)
}
