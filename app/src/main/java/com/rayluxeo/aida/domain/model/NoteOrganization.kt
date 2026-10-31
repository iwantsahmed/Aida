package com.rayluxeo.aida.domain.model

/**
 * What Mira suggests doing to a note, before Abu agrees to any of it.
 *
 * A proposal rather than an action, and that is the whole point: "organize" reaches into a note's
 * title, its tags and who it belongs to, and getting any of those wrong silently refiles
 * something Abu wrote. He sees what would change and says yes.
 *
 * Everything here is derived on the device by `OrganizeNoteUseCase` — matching words against tags
 * that already exist, and names against clients and projects that already exist. Nothing is
 * invented: Mira never proposes a tag Abu has never used or a client he does not have, because a
 * suggestion he has to correct is worse than no suggestion.
 *
 * Every field is null or empty when there is nothing worth proposing, and a proposal where
 * [hasSuggestions] is false is never put in front of him.
 */
data class NoteOrganization(
    /** Taken from the note's opening words, and only when it has no title of its own. */
    val title: String? = null,
    /** Existing tags whose names appear in the text. Never a new tag. */
    val tagIds: Set<Long> = emptySet(),
    /** An existing client named in the text, when exactly one matches. */
    val clientId: Long? = null,
    /** An existing project named in the text, when exactly one matches. */
    val projectId: Long? = null,
) {

    val hasSuggestions: Boolean
        get() = title != null || tagIds.isNotEmpty() || clientId != null || projectId != null
}

/**
 * A line of a note that could become a task.
 *
 * [selected] starts true for lines that look like something to do and false for the rest, so the
 * common case is one tap — but every line is shown and every one can be turned on. Mira deciding
 * silently which lines matter is how half of Abu's list goes missing without him noticing.
 */
data class NoteTaskCandidate(
    val line: String,
    val selected: Boolean,
)
