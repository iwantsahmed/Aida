package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.model.InkPage
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteAttachment
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.NoteQuery
import com.rayluxeo.aida.domain.model.NoteSort
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.VoiceNote
import com.rayluxeo.aida.domain.repository.InkPageRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.TagRepository
import com.rayluxeo.aida.domain.repository.TagUsage
import com.rayluxeo.aida.domain.repository.VoiceNoteRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written collaborators for the notes screens.
 *
 * Hilt's test bytecode transform does not run under AGP 9, so nothing here is injected: each test
 * assembles the view model exactly as Dagger would. They behave like the real repositories in the
 * ways the screens depend on — the filters mean what storage means by them, pinned notes come back
 * first whatever the sort, and failures are values rather than exceptions.
 */
class FakeNoteRepository(
    private val clock: MiraClock,
    /**
     * Where a tag's NAME comes from.
     *
     * The note fake used to keep its own map of names that nothing ever filled, so every tag came
     * back as "tag-1" however it had been minted — two doubles each holding half of one fact, and
     * the fake quietly disagreeing with the repository it stands in for. It resolves names from
     * the tag repository now, which is the thing that actually mints them.
     */
    private val tagSource: TagRepository? = null,
) : NoteRepository {

    private val rows = MutableStateFlow<List<Note>>(emptyList())
    private val attachments = MutableStateFlow<List<NoteAttachment>>(emptyList())
    private var nextId = 1L

    /** Set to make the next call fail, so the screens' problem paths can be exercised. */
    var failWith: MiraErrorCode? = null

    /**
     * Holds a write open, so a test can have one in flight while Abu keeps typing.
     *
     * Nothing else in these doubles suspends — every call answers immediately — and a write that
     * cannot be caught mid-flight is a write whose in-flight behaviour cannot be tested.
     */
    var holdWrites: CompletableDeferred<Unit>? = null

    /** The notes storage would report as carrying handwriting, for [NoteFilter.Ink]. */
    val inkNoteIds: MutableSet<Long> = mutableSetOf()

    override fun observe(filter: NoteFilter): Flow<List<Note>> = observe(NoteQuery(filter = filter))

    override fun observe(query: NoteQuery): Flow<List<Note>> = rows.map { it.select(query) }

    override suspend fun list(query: NoteQuery): AppResult<List<Note>> {
        consumeFailure()?.let { return it }
        return AppResult.Success(rows.value.select(query))
    }

    override fun observeUsedTags(): Flow<List<Tag>> = rows.map { all ->
        all.filter { it.deletedAt == null }
            .flatMap { it.tags }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
    }

    override fun observeAttachments(noteId: Long): Flow<List<NoteAttachment>> =
        attachments.map { all -> all.filter { it.noteId == noteId } }

    /** Reads the row in any state, including binned, exactly as the port promises restore needs. */
    override suspend fun byId(id: Long): AppResult<Note> {
        consumeFailure()?.let { return it }
        return rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No note with id $id")
    }

    override suspend fun search(query: String?): AppResult<List<Note>> {
        consumeFailure()?.let { return it }
        return AppResult.Success(rows.value.select(NoteQuery(text = query)))
    }

    override suspend fun create(note: Note): AppResult<Note> {
        holdWrites?.await()
        consumeFailure()?.let { return it }
        val saved = note.copy(id = nextId++, deletedAt = null)
        rows.value = rows.value + saved
        return AppResult.Success(saved)
    }

    /** Tags are not read off [note], exactly as the port says: they are [setTags]'s alone. */
    override suspend fun update(note: Note): AppResult<Note> {
        holdWrites?.await()
        consumeFailure()?.let { return it }
        val existing = rows.value.firstOrNull { it.id == note.id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No note with id ${note.id}")
        val saved = note.copy(tags = existing.tags, updatedAt = clock.now())
        replace(saved)
        return AppResult.Success(saved)
    }

    override suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Note> =
        change(id, liveOnly = true) { it.copy(isPinned = pinned) }

    override suspend fun archive(id: Long): AppResult<Note> =
        change(id, liveOnly = true) { it.copy(archivedAt = clock.now()) }

    override suspend fun unarchive(id: Long): AppResult<Note> =
        change(id, liveOnly = true) { it.copy(archivedAt = null) }

    override suspend fun setTaskLink(id: Long, taskId: Long?): AppResult<Note> =
        change(id, liveOnly = false) { it.copy(taskId = taskId) }

    override suspend fun setClientLink(id: Long, clientId: Long?): AppResult<Note> =
        change(id, liveOnly = false) { it.copy(clientId = clientId) }

    override suspend fun setProjectLink(id: Long, projectId: Long?): AppResult<Note> =
        change(id, liveOnly = false) { it.copy(projectId = projectId) }

    override suspend fun setTags(noteId: Long, tagIds: Collection<Long>): AppResult<Note> {
        // Names are resolved BEFORE the update, because looking one up suspends and the update
        // itself must not.
        val resolved = tagIds.distinct().map { id -> Tag(id = id, name = nameOf(id)) }
        return change(noteId, liveOnly = false) { note -> note.copy(tags = resolved) }
    }

    /** Falls back only when no tag repository was supplied, so a mismatch is visible. */
    private suspend fun nameOf(id: Long): String =
        (tagSource?.byId(id) as? AppResult.Success)?.value?.name ?: "tag-$id"

    override suspend fun addTag(noteId: Long, tagId: Long): AppResult<Note> {
        val note = rows.value.firstOrNull { it.id == noteId }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No note with id $noteId")
        return setTags(noteId, note.tags.map { it.id } + tagId)
    }

    override suspend fun removeTag(noteId: Long, tagId: Long): AppResult<Note> {
        val note = rows.value.firstOrNull { it.id == noteId }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No note with id $noteId")
        return setTags(noteId, note.tags.map { it.id } - tagId)
    }

    override suspend fun attach(noteId: Long, fileId: Long): AppResult<NoteAttachment> {
        consumeFailure()?.let { return it }
        val attachment = NoteAttachment(
            id = attachments.value.size + 1L,
            noteId = noteId,
            fileId = fileId,
            position = attachments.value.count { it.noteId == noteId },
            displayName = "file-$fileId",
            mimeType = "application/pdf",
            sizeBytes = 2_048L,
            relativePath = "files/file-$fileId",
            createdAt = clock.now(),
        )
        attachments.value = attachments.value + attachment
        return AppResult.Success(attachment)
    }

    /** Nothing on the notes screens takes an attachment off a note, so this is never reached. */
    override suspend fun detach(noteId: Long, fileId: Long): AppResult<Unit> = unusedDouble()

    /** Soft, like the real repository: the row stays so [restore] brings back the same id. */
    override suspend fun delete(id: Long): AppResult<Unit> {
        consumeFailure()?.let { return it }
        val live = rows.value.firstOrNull { it.id == id && it.deletedAt == null }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No live note with id $id")
        replace(live.copy(deletedAt = clock.now(), updatedAt = clock.now()))
        return AppResult.Success(Unit)
    }

    override suspend fun restore(id: Long): AppResult<Unit> {
        consumeFailure()?.let { return it }
        val binned = rows.value.firstOrNull { it.id == id && it.deletedAt != null }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No binned note with id $id")
        replace(binned.copy(deletedAt = null, updatedAt = clock.now()))
        return AppResult.Success(Unit)
    }

    fun seed(vararg notes: Note) {
        rows.value = notes.toList()
        nextId = (notes.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    fun snapshot(): List<Note> = rows.value

    private suspend fun change(
        id: Long,
        liveOnly: Boolean,
        transform: (Note) -> Note,
    ): AppResult<Note> {
        consumeFailure()?.let { return it }
        val existing = rows.value.firstOrNull { it.id == id && (!liveOnly || it.deletedAt == null) }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No note with id $id")
        val saved = transform(existing).copy(updatedAt = clock.now())
        replace(saved)
        return AppResult.Success(saved)
    }

    private fun replace(note: Note) {
        rows.value = rows.value.map { if (it.id == note.id) note else it }
    }

    private fun consumeFailure(): AppResult.Failure? {
        val code = failWith ?: return null
        failWith = null
        return failure(code, "Injected by FakeNoteRepository")
    }

    /** Every dimension of [NoteQuery], applied the way the screens assume storage applies it. */
    private fun List<Note>.select(query: NoteQuery): List<Note> = asSequence()
        .filter { it.matches(query.filter) }
        .filter { note -> query.text?.let { note.matchesText(it) } ?: true }
        // EVERY named tag, not any of them, exactly as `NoteQuerySql` counts them: a second chip
        // narrows the list rather than widening it. The fake said "any" and no test used two
        // chips, so the divergence was invisible and a two-chip test would have asserted the
        // wrong answer.
        .filter { note -> query.tagIds.all { wanted -> note.tags.any { it.id == wanted } } }
        .sortedWith(query.ordering())
        .toList()

    private fun Note.matchesText(text: String): Boolean {
        val needle = text.trim().lowercase()
        return needle.isEmpty() ||
            title?.lowercase()?.contains(needle) == true ||
            body.lowercase().contains(needle)
    }

    /**
     * Pinned first, whatever the sort. Pinning is a statement about position and would mean
     * nothing if choosing a sort could bury it.
     *
     * A title sort puts untitled notes last whichever way it runs, and never looks at their body:
     * `NoteQuerySql` writes `notes.title IS NULL ASC` outside the direction, so reversing the list
     * does not drag the notes with no title to the top of it. The fake used to sort them by their
     * body text, which reads as a row saying "Apple" sitting below a row saying "Zebra" on real
     * storage — the one order a screen cannot be debugged from.
     */
    private fun NoteQuery.ordering(): Comparator<Note> {
        val chosen: Comparator<Note> = when (sort) {
            NoteSort.UPDATED -> directed(compareBy { it.updatedAt })
            NoteSort.CREATED -> directed(compareBy { it.createdAt })
            NoteSort.TITLE -> compareBy<Note> { it.title == null }
                .then(directed(compareBy { it.title?.lowercase().orEmpty() }))
                .thenByDescending { it.updatedAt }
        }
        return compareByDescending<Note> { it.isPinned }
            .then(chosen)
            .thenByDescending { it.id }
    }

    private fun NoteQuery.directed(base: Comparator<Note>): Comparator<Note> =
        if (ascending) base else base.reversed()

    private fun Note.matches(filter: NoteFilter): Boolean {
        val live = deletedAt == null && archivedAt == null
        return when (filter) {
            NoteFilter.All -> live
            NoteFilter.Recent -> live
            NoteFilter.Pinned -> live && isPinned
            NoteFilter.Archived -> deletedAt == null && archivedAt != null
            NoteFilter.Deleted -> deletedAt != null
            NoteFilter.Ink -> live && id in inkNoteIds
            is NoteFilter.ForClient -> live && clientId == filter.clientId
            is NoteFilter.ForProject -> live && projectId == filter.projectId
            is NoteFilter.ForTask -> live && taskId == filter.taskId
            is NoteFilter.ForTag -> live && tags.any { it.id == filter.tagId }
        }
    }
}

/** The pages of handwriting a note carries. Only what the editor reads is real. */
class FakeInkPageRepository : InkPageRepository {

    private val rows = MutableStateFlow<List<InkPage>>(emptyList())

    override fun observeForNote(noteId: Long): Flow<List<InkPage>> =
        rows.map { all -> all.filter { it.noteId == noteId } }

    override suspend fun byId(id: Long): AppResult<InkPage> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No ink page with id $id")

    override suspend fun forNote(noteId: Long): AppResult<List<InkPage>> =
        AppResult.Success(rows.value.filter { it.noteId == noteId })


    override suspend fun save(noteId: Long, drawing: InkDrawing, pageId: Long?): AppResult<InkPage> =
        unusedDouble()

    override suspend fun load(pageId: Long): AppResult<InkDrawing> = unusedDouble()

    override suspend fun delete(pageId: Long): AppResult<Unit> = unusedDouble()

    fun seed(vararg pages: InkPage) {
        rows.value = pages.toList()
    }
}

/** The recording a note was written up from. The editor reads it and changes nothing about it. */
class FakeVoiceNoteRepository : VoiceNoteRepository {

    private val rows = MutableStateFlow<List<VoiceNote>>(emptyList())

    override fun observe(): Flow<List<VoiceNote>> = rows

    override fun observeAwaitingTranscript(): Flow<List<VoiceNote>> =
        rows.map { all -> all.filter { !it.hasTranscript } }

    override suspend fun byId(id: Long): AppResult<VoiceNote> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No voice note with id $id")

    override suspend fun record(voiceNote: VoiceNote): AppResult<VoiceNote> = unusedDouble()

    override suspend fun setTranscript(id: Long, transcript: String): AppResult<VoiceNote> =
        unusedDouble()

    override suspend fun saveAsNote(id: Long, title: String?): AppResult<Note> = unusedDouble()

    override suspend fun delete(id: Long): AppResult<Unit> = unusedDouble()

    override suspend fun restore(id: Long): AppResult<Unit> = unusedDouble()

    fun seed(vararg voiceNotes: VoiceNote) {
        rows.value = voiceNotes.toList()
    }
}

/**
 * Just enough of the invoice book for the draft the note editor raises.
 *
 * [createWithNextNumber] is the only write the editor reaches, and the number it hands back is
 * what the confirmation reads out, so it is the one thing that has to behave.
 */
class FakeInvoiceRepository : InvoiceRepository {

    private val rows = MutableStateFlow<List<Invoice>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Invoice>> = rows

    override fun observeForClient(clientId: Long): Flow<List<Invoice>> =
        rows.map { all -> all.filter { it.clientId == clientId } }

    override fun observeUnpaid(): Flow<List<Invoice>> = unusedDouble()

    override fun observeOverdue(): Flow<List<Invoice>> = unusedDouble()

    override suspend fun byId(id: Long): AppResult<Invoice> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No invoice with id $id")

    override suspend fun byNumber(number: String): AppResult<Invoice> = unusedDouble()

    override suspend fun search(query: String?): AppResult<List<Invoice>> = unusedDouble()

    override suspend fun create(invoice: Invoice): AppResult<Invoice> = save(invoice)

    override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> =
        save(invoice.copy(number = "INV-%04d".format(nextId)))

    override suspend fun update(invoice: Invoice): AppResult<Invoice> = unusedDouble()

    override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> =
        unusedDouble()

    override suspend fun softDelete(id: Long): AppResult<Unit> = unusedDouble()

    override suspend fun restore(id: Long): AppResult<Invoice> = unusedDouble()

    override suspend fun delete(id: Long): AppResult<Unit> = unusedDouble()

    fun snapshot(): List<Invoice> = rows.value

    private fun save(invoice: Invoice): AppResult<Invoice> {
        val saved = invoice.copy(id = nextId++)
        rows.value = rows.value + saved
        return AppResult.Success(saved)
    }
}

/**
 * The label vocabulary notes and tasks share.
 *
 * [ensure] is real here because it is the one thing that creates a tag, and the editor's whole
 * labelling path runs through it: a fake that refused would hide the rule that a note wears
 * labels but never mints them.
 */
class FakeNoteTagRepository : TagRepository {

    private val rows = MutableStateFlow<List<Tag>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Tag>> = rows

    override fun observeUsage(): Flow<List<TagUsage>> =
        rows.map { all -> all.map { TagUsage(tag = it, taskCount = 0) } }

    override suspend fun byId(id: Long): AppResult<Tag> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No tag with id $id")

    override suspend fun ensure(name: String): AppResult<Tag> {
        val clean = name.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "A tag needs a name")
        val existing = rows.value.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        if (existing != null) return AppResult.Success(existing)
        val made = Tag(id = nextId++, name = clean)
        rows.value = rows.value + made
        return AppResult.Success(made)
    }

    override suspend fun rename(id: Long, name: String): AppResult<Tag> = unusedDouble()

    override suspend fun delete(id: Long): AppResult<Unit> = unusedDouble()

    fun seed(vararg tags: Tag) {
        rows.value = tags.toList()
        nextId = (tags.maxOfOrNull { it.id } ?: 0L) + 1L
    }
}

/** A note with only the fields a screen test cares about spelled out. */
fun testNote(
    id: Long = 0L,
    title: String? = null,
    body: String = "",
    isPinned: Boolean = false,
    clientId: Long? = null,
    projectId: Long? = null,
    taskId: Long? = null,
    tags: List<Tag> = emptyList(),
    language: SpokenLanguage = SpokenLanguage.ENGLISH,
    archivedAt: Instant? = null,
    deletedAt: Instant? = null,
    createdAt: Instant,
    updatedAt: Instant = createdAt,
): Note = Note(
    id = id,
    title = title,
    body = body,
    isPinned = isPinned,
    clientId = clientId,
    projectId = projectId,
    taskId = taskId,
    tags = tags,
    language = language,
    archivedAt = archivedAt,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun unusedDouble(): Nothing = error("The notes screens do not use this")
