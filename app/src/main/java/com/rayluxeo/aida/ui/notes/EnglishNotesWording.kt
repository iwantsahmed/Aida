package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.domain.model.NoteSort

/** The notes list in English. Short labels, plain sentences, nothing exclaimed. */
internal object EnglishNotesWording : NotesWording {

    override val screenTitle: String = "Notes"

    override val back: String = "Back"

    override val newNote: String = "New note"
    override val voiceNotes: String = "Voice notes"

    override val writeFirstNote: String = "Write a note"

    override val searchPlaceholder: String = "Find a note"

    override val clearSearch: String = "Clear search"

    override val dismiss: String = "Dismiss"

    override val done: String = "Done"

    override val undo: String = "Undo"

    override fun viewLabel(view: NotesView): String = when (view) {
        NotesView.ALL -> "All"
        NotesView.PINNED -> "Pinned"
        NotesView.INK -> "Handwriting"
        NotesView.ARCHIVED -> "Archived"
        NotesView.BIN -> "Bin"
    }

    override val sortTitle: String = "Sort"

    override fun sortLabel(sort: NoteSort): String = when (sort) {
        NoteSort.UPDATED -> "Updated"
        NoteSort.CREATED -> "Created"
        NoteSort.TITLE -> "Title"
    }

    override fun sortDirection(sort: NoteSort, ascending: Boolean): String = when (sort) {
        NoteSort.UPDATED -> if (ascending) "Least recent first" else "Most recent first"
        NoteSort.CREATED -> if (ascending) "Oldest first" else "Newest first"
        NoteSort.TITLE -> if (ascending) "A to Z" else "Z to A"
    }

    override val clearTags: String = "Clear tags"

    override val pinnedSection: String = "Pinned"

    override val otherNotesSection: String = "Everything else"

    override val untitled: String = "Untitled"

    override fun updatedAt(moment: String): String = "Updated $moment"

    override fun tagSummary(names: List<String>): String = names.joinToString(separator = ", ")

    override val actionsTitle: String = "Note"

    override fun moreActionsFor(title: String): String = "More for $title"

    override val pin: String = "Pin"

    override val unpin: String = "Unpin"

    override val archive: String = "Archive"

    override val unarchive: String = "Put back in the list"

    override val delete: String = "Delete"

    override val restore: String = "Restore"

    override val noteArchived: String = "Note filed."

    override val noteUnarchived: String = "Note back in the list."

    override val noteDeleted: String = "Note deleted."

    override val noteRestored: String = "Note restored."

    override val undone: String = "Put back."

    override fun emptyMessage(view: NotesView): String = when (view) {
        NotesView.ALL -> "No notes yet."
        NotesView.PINNED -> "Nothing pinned."
        NotesView.INK -> "No handwriting yet."
        NotesView.ARCHIVED -> "Nothing filed away."
        NotesView.BIN -> "The bin is empty."
    }

    override fun noMatches(query: String): String = "Nothing matches \"$query\"."

    override val noTagMatches: String = "No notes under these labels."
}
