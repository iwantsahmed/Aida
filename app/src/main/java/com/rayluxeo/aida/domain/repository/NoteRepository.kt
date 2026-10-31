package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteAttachment
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.NoteQuery
import com.rayluxeo.aida.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * Notes and the files they cite.
 *
 * A note that has been filed or binned is reachable only by asking for it: [byId], which reads a
 * note whatever state it is in because restoring one has to be able to, and the views that are
 * about those states — [NoteFilter.Archived] and [NoteFilter.Deleted] through [observe] or
 * [list]. Every other read is over live notes, including [search].
 *
 * Writing is narrower still. Filing and binning have verbs of their own ([archive], [delete] and
 * their undo), and no other call can move a note between those states, so a screen holding a
 * stale copy cannot unfile a note by saving it.
 */
interface NoteRepository {

    fun observe(filter: NoteFilter): Flow<List<Note>>

    /**
     * The same read with a filter, a sort, a search and a set of tags as one value.
     *
     * Every list a notes screen can draw goes through here, so the archive rule and the bin rule
     * are decided once rather than per screen.
     */
    fun observe(query: NoteQuery): Flow<List<Note>>

    /** The one-shot twin of [observe]: what a widget or a command handler reads once. */
    suspend fun list(query: NoteQuery): AppResult<List<Note>>

    /**
     * The tags worn by a live note, for a filter list that offers only what exists.
     *
     * A tag whose every note has been binned or filed away is not offered: a chip that answers
     * with an empty list is indistinguishable from a filter that does not work. The same
     * vocabulary is offered in the archive and the bin, so a tag used only on filed notes cannot
     * be tapped there yet; narrowing it per view needs a query that takes the view.
     */
    fun observeUsedTags(): Flow<List<Tag>>

    /** Attachments whose file still exists; a file that was deleted drops out of the list. */
    fun observeAttachments(noteId: Long): Flow<List<NoteAttachment>>

    /** Reads a note whether or not it is in the bin, because restore needs to see it. */
    suspend fun byId(id: Long): AppResult<Note>

    /**
     * Full-text search over title and body, across live notes. A blank query returns every one.
     *
     * The archive is not searched from here: it is its own view, and a search that quietly
     * returned filed notes would make archiving indistinguishable from doing nothing. Searching
     * it is [list] with [NoteQuery] on [NoteFilter.Archived].
     */
    suspend fun search(query: String?): AppResult<List<Note>>

    /**
     * The note starts live, whatever [Note.archivedAt] and [Note.deletedAt] the caller's copy
     * carried. [Note.tags] is not written either — tags are a second table and [setTags] is the
     * one verb that writes it, so that a note is never briefly reachable wearing half its labels.
     */
    suspend fun create(note: Note): AppResult<Note>

    /**
     * The words, the links and the language of [note]. Not its tags, and not its filing.
     *
     * Whether the note is archived or binned, and when it was created, are kept from the stored
     * row: they are facts about what happened to the note rather than fields a form may
     * overwrite. [archive], [unarchive], [delete] and [restore] are the only ways they move.
     */
    suspend fun update(note: Note): AppResult<Note>

    suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Note>

    /**
     * Files the note away. Not a delete: it keeps its attachments, its links and its place in the
     * archive, and emptying the bin never touches it.
     */
    suspend fun archive(id: Long): AppResult<Note>

    suspend fun unarchive(id: Long): AppResult<Note>

    /** Links the note to a task, or clears the link with null. The note outlives the task. */
    suspend fun setTaskLink(id: Long, taskId: Long?): AppResult<Note>

    suspend fun setClientLink(id: Long, clientId: Long?): AppResult<Note>

    suspend fun setProjectLink(id: Long, projectId: Long?): AppResult<Note>

    /**
     * Replaces the note's whole tag set. The tags themselves are [TagRepository]'s.
     *
     * A note in the bin is refused with
     * [com.rayluxeo.aida.core.common.MiraErrorCode.VALIDATION] here and in [addTag] and
     * [removeTag]: labelling something Abu has thrown away is not a change he asked for, and the
     * label would outlive the note. A filed note is tagged like any other — archiving is not
     * throwing away.
     */
    suspend fun setTags(noteId: Long, tagIds: Collection<Long>): AppResult<Note>

    /** Idempotent: a tag the note already carries is not an error and is not added twice. */
    suspend fun addTag(noteId: Long, tagId: Long): AppResult<Note>

    suspend fun removeTag(noteId: Long, tagId: Long): AppResult<Note>

    /**
     * Cites a file from a note. A file already attached to this note is refused with
     * [com.rayluxeo.aida.core.common.MiraErrorCode.VALIDATION] rather than attached twice.
     */
    suspend fun attach(noteId: Long, fileId: Long): AppResult<NoteAttachment>

    /** Drops the citation. The file itself is untouched. */
    suspend fun detach(noteId: Long, fileId: Long): AppResult<Unit>

    /** Sends the note to the bin. The row and its attachments survive so restore can undo it. */
    suspend fun delete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Unit>
}
