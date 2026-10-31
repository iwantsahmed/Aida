package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.InkPageDao
import com.rayluxeo.aida.data.db.dao.NoteDao
import com.rayluxeo.aida.data.db.entity.NoteEntity
import com.rayluxeo.aida.data.db.entity.NoteTagEntity
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteAttachment
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.NoteQuery
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.repository.NoteRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Notes over the notes, note_fts, note_tags and note_attachments tables.
 *
 * Every list is one [NoteQuery] shaped into SQL by [NoteQuerySql], so the archive rule and the
 * soft-delete rule live in one statement builder rather than in one query per screen. Notes come
 * back whole: a page's tags are read in a single query and attached, never one query per row.
 *
 * What this class owns beyond that is the wording of a refusal — a file attached twice, a note
 * that is not there, a tag put on a note in the bin — as a [MiraErrorCode] and never as an
 * exception out of Room.
 */
@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
    private val inkPages: InkPageDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : NoteRepository {

    override fun observe(filter: NoteFilter): Flow<List<Note>> = observe(NoteQuery(filter = filter))

    override fun observe(query: NoteQuery): Flow<List<Note>> =
        dao.observeByQuery(NoteQuerySql.rows(query).asRoomQuery())
            .map { rows -> hydrate(withInkOnly(query, rows)) }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun list(query: NoteQuery): AppResult<List<Note>> =
        storageCall(dispatchers, "list notes") {
            val rows = dao.listByQuery(NoteQuerySql.rows(query).asRoomQuery())
            hydrate(withInkOnly(query, rows))
        }

    /**
     * The tags Abu can actually filter by: the ones worn by a note that is neither binned nor
     * filed away.
     *
     * Not `NoteDao.observeUsedTags`, which asks `note_tags` alone. Those links outlive both a soft
     * delete and an archive — the cascade only fires on a hard row delete, which Mira never
     * does — so a tag whose only note he binned last year would sit in the chip row forever, and
     * answer with an empty list when tapped. §8's rule is blanket for this reason: the filter is
     * a read over notes, so it filters like every other read over notes.
     *
     * The price is reading the live rows to collect their tags, which is what it costs while the
     * question can only be asked through a statement over `notes`. A `NoteDao` query that joined
     * `tags` to live notes would do it in one read.
     */
    override fun observeUsedTags(): Flow<List<Tag>> =
        dao.observeByQuery(NoteQuerySql.liveRows().asRoomQuery())
            .map { rows -> tagsOn(rows.map { it.id }) }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeAttachments(noteId: Long): Flow<List<NoteAttachment>> =
        dao.observeAttachments(noteId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Note> {
        val found = storageCall(dispatchers, "load note $id") {
            dao.findById(id)?.let { hydrate(listOf(it)).first() }
        }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No note with id $id")
        }
    }

    override suspend fun search(query: String?): AppResult<List<Note>> =
        list(NoteQuery(text = query))

    /**
     * A new note is never created in the bin or in the archive, whatever the caller's copy
     * happened to carry.
     *
     * [Note.tags] is not written from here, for the reason `Note.toEntity` gives: the links are
     * their own table. The one verb that puts them there is [setTags], which every caller reaches
     * through `applyNoteTags` — and the rule that a tag has to exist first is `NoteRules.tags`,
     * in the domain, stated once.
     */
    override suspend fun create(note: Note): AppResult<Note> {
        val inserted = storageCall(dispatchers, "create note") {
            dao.insert(note.copy(id = 0L, archivedAt = null, deletedAt = null).toEntity())
        }
        return when (inserted) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> byId(inserted.value)
        }
    }

    /**
     * The words, the links and the language. Not the filing.
     *
     * Tags are not written from here for the reason `Note.toEntity` gives: a screen holding a
     * stale copy would silently un-tag the note. Whether the note is filed or binned is kept from
     * the stored row for the same reason and a stronger one — those moments are written by the
     * guarded statements in `NoteDao`, whose whole purpose is that a second write cannot move the
     * moment Abu first filed or binned something. An editor that had been open since before he
     * archived the note would otherwise unfile it just by saving.
     */
    override suspend fun update(note: Note): AppResult<Note> {
        val updated = storageCall(dispatchers, "update note ${note.id}") {
            val stored = dao.findById(note.id) ?: return@storageCall 0
            dao.update(
                note.copy(updatedAt = clock.now()).toEntity().copy(
                    archivedAtEpochMillis = stored.archivedAtEpochMillis,
                    deletedAtEpochMillis = stored.deletedAtEpochMillis,
                    createdAtEpochMillis = stored.createdAtEpochMillis,
                ),
            )
        }
        return updated.thenLoad(note.id)
    }

    override suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Note> {
        val changed = storageCall(dispatchers, "pin note $id") {
            dao.setPinned(id = id, pinned = pinned, atEpochMillis = clock.now().toEpochMilli())
        }
        return changed.thenLoad(id, "No live note with id $id")
    }

    override suspend fun archive(id: Long): AppResult<Note> {
        val changed = storageCall(dispatchers, "archive note $id") {
            dao.archive(id, clock.now().toEpochMilli())
        }
        return changed.thenLoad(id, "No live, unarchived note with id $id")
    }

    override suspend fun unarchive(id: Long): AppResult<Note> {
        val changed = storageCall(dispatchers, "unarchive note $id") {
            dao.unarchive(id, clock.now().toEpochMilli())
        }
        return changed.thenLoad(id, "No archived note with id $id")
    }

    override suspend fun setTaskLink(id: Long, taskId: Long?): AppResult<Note> {
        val changed = storageCall(dispatchers, "link note $id to task $taskId") {
            dao.setTaskLink(id = id, taskId = taskId, atEpochMillis = clock.now().toEpochMilli())
        }
        return changed.thenLoad(id)
    }

    override suspend fun setClientLink(id: Long, clientId: Long?): AppResult<Note> {
        val changed = storageCall(dispatchers, "link note $id to client $clientId") {
            dao.setClientLink(
                id = id,
                clientId = clientId,
                atEpochMillis = clock.now().toEpochMilli(),
            )
        }
        return changed.thenLoad(id)
    }

    override suspend fun setProjectLink(id: Long, projectId: Long?): AppResult<Note> {
        val changed = storageCall(dispatchers, "link note $id to project $projectId") {
            dao.setProjectLink(
                id = id,
                projectId = projectId,
                atEpochMillis = clock.now().toEpochMilli(),
            )
        }
        return changed.thenLoad(id)
    }

    /**
     * Tagging does not touch `updatedAtEpochMillis`, and that is deliberate: a label is about how
     * Abu finds a note, not about the note having changed, and bumping it would shuffle his
     * most-recent list every time he filed something.
     */
    override suspend fun setTags(noteId: Long, tagIds: Collection<Long>): AppResult<Note> =
        onLiveNote(noteId, "set tags on note $noteId") {
            dao.replaceTags(noteId, tagIds.distinct())
        }

    override suspend fun addTag(noteId: Long, tagId: Long): AppResult<Note> =
        onLiveNote(noteId, "tag note $noteId with $tagId") {
            dao.linkTag(NoteTagEntity(noteId = noteId, tagId = tagId))
        }

    override suspend fun removeTag(noteId: Long, tagId: Long): AppResult<Note> =
        onLiveNote(noteId, "untag note $noteId") { dao.unlinkTag(noteId, tagId) }

    /**
     * The DAO returns null for a pair that is already there. That is the only way this can fail
     * without an exception, so it is the only case worded here; a note or file that does not
     * exist is refused by the foreign key and arrives as a STORAGE failure.
     */
    override suspend fun attach(noteId: Long, fileId: Long): AppResult<NoteAttachment> {
        val inserted = storageCall(dispatchers, "attach file $fileId to note $noteId") {
            dao.attachFile(noteId, fileId, clock.now().toEpochMilli())
        }
        return when (inserted) {
            is AppResult.Failure -> inserted
            is AppResult.Success -> {
                val attachmentId = inserted.value
                    ?: return failure(
                        MiraErrorCode.VALIDATION,
                        "File $fileId is already attached to note $noteId",
                    )
                attachmentById(attachmentId)
            }
        }
    }

    override suspend fun detach(noteId: Long, fileId: Long): AppResult<Unit> =
        storageCall(dispatchers, "detach file $fileId from note $noteId") {
            dao.deleteAttachment(noteId, fileId)
        }.orNotFound("File $fileId is not attached to note $noteId")

    override suspend fun delete(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "delete note $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }.orNotFound("No live note with id $id")

    override suspend fun restore(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "restore note $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }.orNotFound("No binned note with id $id")

    /**
     * Narrows [rows] to the notes that carry handwriting, on [NoteFilter.Ink] and nothing else.
     *
     * This is the other half of that branch in [NoteQuerySql], and it is here rather than in the
     * statement because `NoteDao.observeByQuery` observes `notes` and `note_tags`: a condition
     * over `ink_pages` would answer correctly when the screen opened and then never notice a page
     * being written. Asked on every emission, the answer cannot go stale — and saving or deleting
     * a page also touches its note row, which is what guarantees there is an emission to answer
     * on. `InkPageRepositoryImpl` holds up that end.
     */
    private suspend fun withInkOnly(query: NoteQuery, rows: List<NoteEntity>): List<NoteEntity> {
        if (query.filter != NoteFilter.Ink || rows.isEmpty()) return rows
        val written = inkPages.noteIdsWithInk().toSet()
        return rows.filter { it.id in written }
    }

    /** Loads the tags of a whole page of notes in one read rather than one read per note. */
    private suspend fun hydrate(rows: List<NoteEntity>): List<Note> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.id }
        val tags = ids.chunked(CHUNK).flatMap { dao.tagsForNotes(it) }.groupBy { it.noteId }
        return rows.map { row ->
            row.toDomain(tags = tags[row.id].orEmpty().map { Tag(id = it.id, name = it.name) })
        }
    }

    /** The distinct tags worn by [noteIds], ordered as the chip row draws them. */
    private suspend fun tagsOn(noteIds: List<Long>): List<Tag> {
        if (noteIds.isEmpty()) return emptyList()
        return noteIds.chunked(CHUNK)
            .flatMap { dao.tagsForNotes(it) }
            .distinctBy { it.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { Tag(id = it.id, name = it.name) }
    }

    /**
     * Applies [block] to a note that is out of the bin, or says why it did not.
     *
     * Two refusals, and the second is the one worth stating. The links in `note_tags` are foreign
     * keys, so writing one against a note that is not there throws a constraint violation, which
     * would reach Abu as a STORAGE failure — true, and useless. And `findById` reads a note
     * whatever its state, on purpose, so without the second check a tag put on a note in the bin
     * would be written and reported as done: a label on something he has thrown away, which he
     * would next meet as a chip that finds nothing. Filing a note away is not throwing it away,
     * so an archived note is tagged like any other.
     */
    private suspend fun onLiveNote(
        noteId: Long,
        detail: String,
        block: suspend () -> Unit,
    ): AppResult<Note> {
        val found = storageCall(dispatchers, detail) {
            val row = dao.findById(noteId)
            if (row != null && row.deletedAtEpochMillis == null) block()
            row
        }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> {
                val row = found.value
                    ?: return failure(MiraErrorCode.NOT_FOUND, "No note with id $noteId")
                if (row.deletedAtEpochMillis != null) {
                    failure(
                        MiraErrorCode.VALIDATION,
                        "Note $noteId is in the bin; restore it before tagging it",
                    )
                } else {
                    byId(noteId)
                }
            }
        }
    }

    private suspend fun attachmentById(id: Long): AppResult<NoteAttachment> {
        val found = storageCall(dispatchers, "load attachment $id") { dao.attachmentById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No attachment with id $id")
        }
    }

    /**
     * Re-reads the row an update touched, or reports the id that matched nothing.
     *
     * Every guarded UPDATE in `NoteDao` answers "no such row, or it was already in that state"
     * the same way — zero rows changed — so [detail] is what says which of those a caller hit.
     */
    private suspend fun AppResult<Int>.thenLoad(
        id: Long,
        detail: String = "No note with id $id",
    ): AppResult<Note> = when (this) {
        is AppResult.Failure -> this
        is AppResult.Success ->
            if (value > 0) byId(id) else failure(MiraErrorCode.NOT_FOUND, detail)
    }

    private companion object {
        /** Well under SQLite's 999 bound-variable ceiling, with room for the rest of a statement. */
        const val CHUNK = 900
    }
}
