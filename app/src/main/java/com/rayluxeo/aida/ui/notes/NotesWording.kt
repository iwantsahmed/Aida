package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.NoteSort

/**
 * Every word the notes list puts in front of Abu, in one language.
 *
 * The rows beside these labels are worded by [com.rayluxeo.aida.ui.tasks.TaskTimeFormatter], which
 * already answers in Bengali when Bengali is selected. A chip that said "Archived" above a row
 * reading "গতকাল, সকাল ৯টা" is the drift this interface exists to prevent: a label that sits beside
 * worded data is chosen where the language is known, never typed at a call site.
 *
 * English and Bengali are separate implementations so either can be reworded without reading the
 * other, exactly as the tasks wording and the response phrasebooks are.
 */
internal interface NotesWording {

    val screenTitle: String

    /**
     * What TalkBack calls the back arrow.
     *
     * It is wording like every other label on the screen. Left to the top bar's own default it
     * was announced as the English word "Back" in the middle of a Bengali screen — the one string
     * on this list Abu could not see was wrong, because only the reader hears it.
     */
    val back: String

    /** The control that opens a blank note, and the empty list's way forward. */
    val newNote: String
    val voiceNotes: String

    val writeFirstNote: String

    val searchPlaceholder: String

    val clearSearch: String

    val dismiss: String

    /** Closes a sheet once Abu has finished with it. */
    val done: String

    val undo: String

    // --- Narrowing and ordering --------------------------------------------------------------

    fun viewLabel(view: NotesView): String

    val sortTitle: String

    fun sortLabel(sort: NoteSort): String

    /** Reads as a direction rather than as an arrow nobody can decode. */
    fun sortDirection(sort: NoteSort, ascending: Boolean): String

    /** Clears the chosen tags only. The view and the search field are separate decisions. */
    val clearTags: String

    // --- The rows ----------------------------------------------------------------------------

    /** The heading over notes Abu has pinned. They float above whichever sort he chose. */
    val pinnedSection: String

    /** The heading over everything else, which only appears when something is pinned above it. */
    val otherNotesSection: String

    /**
     * What a row shows when a note has neither a title nor a line of body — a page of handwriting
     * that has not been written up. It is a real state, not a fault, so it is not worded as one.
     */
    val untitled: String

    fun updatedAt(moment: String): String

    /** The one-line summary under a row's title, when the note carries labels worth naming. */
    fun tagSummary(names: List<String>): String

    // --- Acting on one note ------------------------------------------------------------------

    val actionsTitle: String

    /** TalkBack cannot see which row the glyph belongs to, so the title travels with it. */
    fun moreActionsFor(title: String): String

    val pin: String

    val unpin: String

    val archive: String

    val unarchive: String

    val delete: String

    val restore: String

    // --- What Mira says afterwards -----------------------------------------------------------

    val noteArchived: String

    val noteUnarchived: String

    val noteDeleted: String

    val noteRestored: String

    /** One line for every undo: "Note restored" would be wrong for an un-archive. */
    val undone: String

    // --- Lists with nothing in them ----------------------------------------------------------

    fun emptyMessage(view: NotesView): String

    fun noMatches(query: String): String

    /**
     * What an empty list says when tags are narrowing it and nothing was typed.
     *
     * Without it the list falls back to "No notes yet" over a filter Abu set days ago and has
     * forgotten, which tells him he has written nothing at all.
     */
    val noTagMatches: String
}

internal fun notesWording(language: SpokenLanguage): NotesWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliNotesWording
    SpokenLanguage.ENGLISH -> EnglishNotesWording
}
