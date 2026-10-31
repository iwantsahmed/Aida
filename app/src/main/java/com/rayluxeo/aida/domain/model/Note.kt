package com.rayluxeo.aida.domain.model

import com.rayluxeo.aida.core.common.SpokenLanguage
import java.time.Instant

/**
 * Anything Abu wrote down.
 *
 * [language] is carried per note rather than read from Settings, because Abu writes in Bengali
 * and English on the same day and a note keeps the language it was written in forever.
 */
data class Note(
    val id: Long = 0L,
    val title: String? = null,
    val body: String,
    val isPinned: Boolean = false,
    /** Set when the note belongs to a client. */
    val clientId: Long? = null,
    /** Set when the note belongs to a project. */
    val projectId: Long? = null,
    /** Set when the note is about a task. `SET NULL` on disk: the note outlives the task. */
    val taskId: Long? = null,
    /**
     * The same [Tag] type tasks carry, from the same table.
     *
     * Read for a whole page at once rather than per note — a query per row turns a long list into
     * a stutter, and a list that fetched them itself would show Abu an untagged note for a frame.
     */
    val tags: List<Tag> = emptyList(),
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    /**
     * Filed away, which is not the same as deleted.
     *
     * Archiving means Abu is finished with a note; deleting means it was a mistake. Emptying the
     * bin must never touch what he has filed, which is why these are two columns and not one.
     */
    val archivedAt: Instant? = null,
    /**
     * When the note went to the bin, or null while it is live.
     *
     * Deleting is soft so undo restores the same row rather than inserting a copy: a copy takes
     * a new id, and every attachment pointing at the old one would be cut loose by cascade.
     */
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * A file cited by a note, joined to what the file actually is.
 *
 * The join row on its own carries only two ids, which no screen can draw. The file's name, type
 * and size travel with it so a note's attachment list is one read rather than one read per file.
 */
data class NoteAttachment(
    val id: Long = 0L,
    val noteId: Long,
    val fileId: Long,
    /** Abu's own ordering within the note, dense from zero. */
    val position: Int,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String,
    val createdAt: Instant,
)

/**
 * Which notes a screen is asking for.
 *
 * A sealed type rather than an enum because two of the four carry the id they are scoped to, and
 * a separate `observeForClient` would have duplicated the ordering rules a third and fourth time.
 */
sealed interface NoteFilter {
    /** Live notes: not archived, not in the bin. What Abu means by "my notes". */
    data object All : NoteFilter
    data object Pinned : NoteFilter

    /** The ones he has filed. Reachable, never mixed into [All]. */
    data object Archived : NoteFilter

    /** The bin. Its own view because restoring is the only thing to do from here. */
    data object Deleted : NoteFilter

    /** Written recently, newest first — what a hub row and the widget show. */
    data object Recent : NoteFilter

    /** Handwriting: every note carrying at least one ink page. */
    data object Ink : NoteFilter

    data class ForClient(val clientId: Long) : NoteFilter
    data class ForProject(val projectId: Long) : NoteFilter
    data class ForTask(val taskId: Long) : NoteFilter
    data class ForTag(val tagId: Long) : NoteFilter
}

/**
 * How a list of notes is ordered.
 *
 * [UPDATED] is the default because a note's value is usually how recently Abu touched it, which
 * is the opposite of a task list — that one is about what is next, so it sorts by due date.
 * Pinned notes float above whichever of these is chosen; pinning is a statement about position
 * and would be meaningless if a sort could bury it.
 */
enum class NoteSort { UPDATED, CREATED, TITLE }

/**
 * Everything a note list can narrow on, as one value.
 *
 * One object rather than a long parameter list, for the reason `TaskQuery` gives: a view, a
 * search and a filter chip all describe what they want the same way, and adding a dimension does
 * not change every call site.
 */
data class NoteQuery(
    val filter: NoteFilter = NoteFilter.All,
    val sort: NoteSort = NoteSort.UPDATED,
    val ascending: Boolean = false,
    /** Full-text over title and body. Null or blank means "no text condition". */
    val text: String? = null,
    val tagIds: Set<Long> = emptySet(),
)
