package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.InkPage
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteAttachment
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.NoteQuery
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectMilestone
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InkPageRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.TagRepository
import com.rayluxeo.aida.domain.repository.TagUsage
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The note verbs, which every way of writing a note reaches — the editor, the widget, the app
 * shortcut, a spoken capture and the handwriting canvas.
 *
 * What is worth asserting here is what Abu could not notice going wrong: a note whose tags landed
 * in a second write that could have failed on its own, an editor holding a stale copy quietly
 * un-archiving a note he filed somewhere else, and a link written to a client who is not there.
 *
 * Nothing is injected — Hilt's local-test transform does not run under AGP 9 (google/dagger#5261)
 * — and the clock never moves, so every instant below is the same on every run.
 */
class NoteUseCasesTest {

    private val clock = FixedMiraClock()
    private val tags = FakeTagRepository()
    private val notes = FakeNoteRepository(clock, tags)
    private val inkPages = FakeInkPageRepository()
    private val clients = FakeClientRepository()
    private val projects = FakeProjectRepository()
    private val tasks = FakeTaskRepository(clock)

    private val link = LinkNoteUseCase(notes, clients, projects, tasks)
    private val create = CreateNoteUseCase(notes, clock, link)
    private val update = UpdateNoteUseCase(notes, inkPages, clock)
    private val pin = PinNoteUseCase(notes)
    private val archive = ArchiveNoteUseCase(notes)
    private val restore = RestoreNoteUseCase(notes)
    private val delete = DeleteNoteUseCase(notes)
    private val setTags = SetNoteTagsUseCase(notes)

    // ------------------------------------------------------------------ create

    @Test
    fun `create refuses a note that is empty in every way`() = runTest {
        val created = create(title = "   ", body = "\n  \n")

        assertThat(created.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.snapshot()).isEmpty()
    }

    @Test
    fun `create refuses a wordless note because no page can exist before the note does`() = runTest {
        // An ink page carries the id of the note it sits on, so "this note is only handwriting"
        // is not a state a brand-new row can be in. The exemption belongs to update, and the
        // test below proves it is there.
        val created = create()

        assertThat(created.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.snapshot()).isEmpty()
    }

    @Test
    fun `create trims the words and drops a blank title`() = runTest {
        val created = create(title = "   ", body = "  kal shokale Ray ke call korte hobe  ").getOrNull()!!

        assertThat(created.title).isNull()
        assertThat(created.body).isEqualTo("kal shokale Ray ke call korte hobe")
        assertThat(created.createdAt).isEqualTo(clock.now())
        assertThat(created.updatedAt).isEqualTo(clock.now())
    }

    @Test
    fun `create keeps a Bengali note in Bengali`() = runTest {
        val created = create(
            title = "মিটিং",
            body = "কাল সকালে ফোন করতে হবে",
            language = SpokenLanguage.BENGALI,
        ).getOrNull()!!

        assertThat(created.title).isEqualTo("মিটিং")
        assertThat(created.language).isEqualTo(SpokenLanguage.BENGALI)
    }

    @Test
    fun `create refuses a tag that does not exist yet`() = runTest {
        val created = create(body = "Anything", tagIds = listOf(0L))

        assertThat(created.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.snapshot()).isEmpty()
    }

    @Test
    fun `create puts each tag it was given on the saved note once`() = runTest {
        tags.seed(Tag(4L, "invoice"), Tag(7L, "rayluxeo"))

        val created = create(body = "Anything", tagIds = listOf(4L, 4L, 7L)).getOrNull()!!

        // Asserted against what the port was handed, not against what came back: the storage
        // side de-duplicates too, so a note carrying each tag once proves nothing on its own.
        assertThat(notes.lastTagWrite).containsExactly(4L, 7L).inOrder()
        assertThat(created.tags.map { it.id }).containsExactly(4L, 7L).inOrder()
        assertThat(created.tags.map { it.name }).containsExactly("invoice", "rayluxeo").inOrder()
    }

    @Test
    fun `create checks what a note is filed under before it writes anything`() = runTest {
        assertThat(create(body = "anything", clientId = 99L).errorCode())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(create(body = "anything", projectId = 99L).errorCode())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(create(body = "anything", taskId = 99L).errorCode())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(notes.snapshot()).isEmpty()
    }

    @Test
    fun `create files a note under a client who is there`() = runTest {
        clients.seed(client(id = 3L, name = "Ray"))

        val created = create(body = "banners", clientId = 3L).getOrNull()!!

        assertThat(created.clientId).isEqualTo(3L)
        assertThat(notes.row(created.id)?.clientId).isEqualTo(3L)
    }

    // ------------------------------------------------------------------ update

    @Test
    fun `update refuses to empty a note that has no handwriting behind it`() = runTest {
        val stored = notes.seed(note(title = "Groceries", body = "rice")).single()

        val saved = update(stored.copy(title = " ", body = " "))

        assertThat(saved.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.row(stored.id)?.body).isEqualTo("rice")
    }

    @Test
    fun `update lets a handwritten note have no words at all`() = runTest {
        val stored = notes.seed(note(body = "typed for now")).single()
        inkPages.seed(stored.id)

        val saved = update(stored.copy(title = null, body = "")).getOrNull()!!

        assertThat(saved.body).isEmpty()
    }

    @Test
    fun `update takes the words and leaves filing pinning and links alone`() = runTest {
        val stored = notes.seed(
            note(
                title = "Site copy",
                body = "first draft",
                isPinned = true,
                clientId = 3L,
                archivedAt = clock.now(),
            ),
        ).single()

        // The editor was opened before Abu filed and linked the note from the list.
        val stale = stored.copy(
            body = "second draft",
            isPinned = false,
            clientId = null,
            archivedAt = null,
        )
        val saved = update(stale).getOrNull()!!

        assertThat(saved.body).isEqualTo("second draft")
        assertThat(saved.isPinned).isTrue()
        assertThat(saved.clientId).isEqualTo(3L)
        assertThat(saved.archivedAt).isEqualTo(clock.now())
    }

    @Test
    fun `update rewrites the tags only when they changed`() = runTest {
        val stored = notes.seed(note(body = "anything", tags = listOf(Tag(2L, "rayluxeo")))).single()

        update(stored.copy(body = "anything else"))
        assertThat(notes.tagWrites).isEqualTo(0)

        update(stored.copy(tags = listOf(Tag(2L, "rayluxeo"), Tag(5L, "invoice"))))
        assertThat(notes.tagWrites).isEqualTo(1)
        assertThat(notes.row(stored.id)?.tags?.map { it.id }).containsExactly(2L, 5L)
    }

    @Test
    fun `update refuses a tag that was never saved`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()

        val saved = update(stored.copy(tags = listOf(Tag(0L, "brand new"))))

        assertThat(saved.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    // ------------------------------------------------- pin, file, bin, restore

    @Test
    fun `pin floats a note and unpins it again`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()

        assertThat(pin(stored.id, true).getOrNull()!!.isPinned).isTrue()
        assertThat(pin(stored.id, false).getOrNull()!!.isPinned).isFalse()
    }

    @Test
    fun `archiving files a note without binning it`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()

        val filed = archive(stored.id).getOrNull()!!

        assertThat(filed.archivedAt).isEqualTo(clock.now())
        assertThat(filed.deletedAt).isNull()
    }

    @Test
    fun `restore takes a note out of the archive`() = runTest {
        val stored = notes.seed(note(body = "anything", archivedAt = clock.now())).single()

        val back = restore(stored.id).getOrNull()!!

        assertThat(back.archivedAt).isNull()
    }

    @Test
    fun `restore takes a note out of the bin and leaves it filed where it was`() = runTest {
        val stored = notes.seed(
            note(body = "anything", archivedAt = clock.now(), deletedAt = clock.now()),
        ).single()

        val back = restore(stored.id).getOrNull()!!

        assertThat(back.deletedAt).isNull()
        assertThat(back.archivedAt).isEqualTo(clock.now())
    }

    @Test
    fun `restore of a note that is already back changes nothing`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()

        val back = restore(stored.id).getOrNull()!!

        assertThat(back).isEqualTo(stored)
    }

    @Test
    fun `delete bins the note and keeps the row`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()

        assertThat(delete(stored.id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(notes.row(stored.id)?.deletedAt).isEqualTo(clock.now())
    }

    // -------------------------------------------------------------- tags, links

    @Test
    fun `setting tags to nothing takes the last one off`() = runTest {
        val stored = notes.seed(note(body = "anything", tags = listOf(Tag(2L, "rayluxeo")))).single()

        val saved = setTags(stored.id, emptyList()).getOrNull()!!

        assertThat(saved.tags).isEmpty()
    }

    @Test
    fun `setting tags refuses one that does not exist yet`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()

        assertThat(setTags(stored.id, listOf(3L, 0L)).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.row(stored.id)?.tags).isEmpty()
    }

    @Test
    fun `linking to a client who is not there is refused before anything is written`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()

        assertThat(link.toClient(stored.id, 99L).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(notes.row(stored.id)?.clientId).isNull()
    }

    @Test
    fun `linking to a client who exists writes the link and null cuts it loose`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()
        clients.seed(client(id = 3L, name = "Ray"))

        assertThat(link.toClient(stored.id, 3L).getOrNull()!!.clientId).isEqualTo(3L)
        assertThat(link.toClient(stored.id, null).getOrNull()!!.clientId).isNull()
    }

    @Test
    fun `linking to a project and a task goes through the same check`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()
        projects.seed(project(id = 8L, name = "Site rebuild"))
        val existing = tasks.seed(task(title = "Write the copy")).single()

        assertThat(link.toProject(stored.id, 8L).getOrNull()!!.projectId).isEqualTo(8L)
        assertThat(link.toProject(stored.id, 12L).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(link.toTask(stored.id, existing.id).getOrNull()!!.taskId).isEqualTo(existing.id)
        assertThat(link.toTask(stored.id, 404L).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }
}

// ---------------------------------------------------------------------- shared helpers

/** A note with only the fields a test cares about spelled out. */
internal fun note(
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
    createdAt: Instant = FixedMiraClock.dhaka(2026, 3, 1, 9),
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
    updatedAt = createdAt,
)

internal fun client(
    id: Long,
    name: String,
    companyName: String? = null,
    isArchived: Boolean = false,
): Client = Client(
    id = id,
    uuid = "client-$id",
    name = name,
    companyName = companyName,
    isArchived = isArchived,
    createdAt = FixedMiraClock.dhaka(2026, 1, 5, 9),
    updatedAt = FixedMiraClock.dhaka(2026, 1, 5, 9),
)

internal fun project(
    id: Long,
    name: String,
    clientId: Long? = null,
    status: ProjectStatus = ProjectStatus.ACTIVE,
): Project = Project(
    id = id,
    uuid = "project-$id",
    clientId = clientId,
    name = name,
    status = status,
    createdAt = FixedMiraClock.dhaka(2026, 1, 5, 9),
    updatedAt = FixedMiraClock.dhaka(2026, 1, 5, 9),
)

/**
 * Storage for the note verbs, behaving the way the port says storage behaves.
 *
 * Three of those behaviours are the point of it rather than convenience: [update] writes every
 * column it is handed, including the archive and the links, so a use case that fails to merge
 * onto the stored row is caught here rather than on Abu's phone; [archive], [unarchive] and
 * [restore] are guarded on the state they claim to change, exactly as the DAO's own statements
 * are; and [create] writes the row and no tags, exactly as `NoteRepositoryImpl` does, so a use
 * case that expects tags to travel with the row is caught here rather than on his phone.
 *
 * [lastTagWrite] keeps what the tag port was handed, verbatim, because storage de-duplicates as
 * well — a test reading only the note back cannot tell whose de-duplication it is watching.
 *
 * Tag names are resolved from the tag repository, as `NoteRepositoryImpl` resolves them by
 * re-reading the note. A map of names nothing ever fills would make every tag look alike, and a
 * test asserting on them would then be asserting on the fake.
 */
internal class FakeNoteRepository(
    private val clock: MiraClock,
    private val tags: FakeTagRepository = FakeTagRepository(),
) : NoteRepository {

    private val rows = mutableListOf<Note>()
    private var nextId = 1L

    /** How often a note's tags were rewritten, to prove an edit does not rewrite them for nothing. */
    var tagWrites: Int = 0

    /** The ids the last tag write was handed, before this fake tidied anything. */
    var lastTagWrite: List<Long> = emptyList()

    fun seed(vararg notes: Note): List<Note> = notes.map { note ->
        val saved = if (note.id == 0L) note.copy(id = nextId++) else note.also { nextId = maxOf(nextId, it.id + 1) }
        rows += saved
        saved
    }

    fun snapshot(): List<Note> = rows.toList()

    fun row(id: Long): Note? = rows.firstOrNull { it.id == id }

    override fun observe(filter: NoteFilter): Flow<List<Note>> = flowOf(live())

    override fun observe(query: NoteQuery): Flow<List<Note>> = flowOf(live())

    override suspend fun list(query: NoteQuery): AppResult<List<Note>> = AppResult.Success(live())

    override fun observeUsedTags(): Flow<List<Tag>> =
        flowOf(rows.flatMap { it.tags }.distinctBy { it.id })

    override fun observeAttachments(noteId: Long): Flow<List<NoteAttachment>> = flowOf(emptyList())

    override suspend fun byId(id: Long): AppResult<Note> =
        row(id)?.let { AppResult.Success(it) } ?: failure(MiraErrorCode.NOT_FOUND, "No note with id $id")

    override suspend fun search(query: String?): AppResult<List<Note>> {
        val needle = query?.lowercase()
        return AppResult.Success(
            live().filter { stored ->
                needle == null ||
                    stored.body.lowercase().contains(needle) ||
                    stored.title.orEmpty().lowercase().contains(needle)
            },
        )
    }

    override suspend fun create(note: Note): AppResult<Note> {
        // The row and nothing else: the tags on a note handed to `create` are dropped by
        // `NoteRepositoryImpl` too, and a fake that kept them would hide a use case that never
        // wrote them.
        val saved = note.copy(id = nextId++, deletedAt = null, tags = emptyList())
        rows += saved
        return AppResult.Success(saved)
    }

    override suspend fun update(note: Note): AppResult<Note> {
        val existing = row(note.id) ?: return failure(MiraErrorCode.NOT_FOUND, "No note with id ${note.id}")
        val saved = note.copy(tags = existing.tags, updatedAt = clock.now())
        replace(saved)
        return AppResult.Success(saved)
    }

    override suspend fun setPinned(id: Long, pinned: Boolean): AppResult<Note> =
        change(id) { it.copy(isPinned = pinned, updatedAt = clock.now()) }

    override suspend fun archive(id: Long): AppResult<Note> {
        val existing = row(id)
        if (existing == null || existing.deletedAt != null || existing.archivedAt != null) {
            return failure(MiraErrorCode.NOT_FOUND, "No live unfiled note with id $id")
        }
        return change(id) { it.copy(archivedAt = clock.now(), updatedAt = clock.now()) }
    }

    override suspend fun unarchive(id: Long): AppResult<Note> {
        val existing = row(id)
        if (existing == null || existing.deletedAt != null || existing.archivedAt == null) {
            return failure(MiraErrorCode.NOT_FOUND, "No filed note with id $id")
        }
        return change(id) { it.copy(archivedAt = null, updatedAt = clock.now()) }
    }

    override suspend fun setTaskLink(id: Long, taskId: Long?): AppResult<Note> =
        change(id) { it.copy(taskId = taskId, updatedAt = clock.now()) }

    override suspend fun setClientLink(id: Long, clientId: Long?): AppResult<Note> =
        change(id) { it.copy(clientId = clientId, updatedAt = clock.now()) }

    override suspend fun setProjectLink(id: Long, projectId: Long?): AppResult<Note> =
        change(id) { it.copy(projectId = projectId, updatedAt = clock.now()) }

    override suspend fun setTags(noteId: Long, tagIds: Collection<Long>): AppResult<Note> {
        tagWrites++
        lastTagWrite = tagIds.toList()
        return change(noteId) { stored ->
            stored.copy(tags = tagIds.distinct().map { Tag(it, nameOf(it)) })
        }
    }

    override suspend fun addTag(noteId: Long, tagId: Long): AppResult<Note> = change(noteId) { stored ->
        if (stored.tags.any { it.id == tagId }) {
            stored
        } else {
            stored.copy(tags = stored.tags + Tag(tagId, nameOf(tagId)))
        }
    }

    override suspend fun removeTag(noteId: Long, tagId: Long): AppResult<Note> =
        change(noteId) { stored -> stored.copy(tags = stored.tags.filterNot { it.id == tagId }) }

    override suspend fun attach(noteId: Long, fileId: Long): AppResult<NoteAttachment> =
        unusedNoteDouble()

    override suspend fun detach(noteId: Long, fileId: Long): AppResult<Unit> = unusedNoteDouble()

    override suspend fun delete(id: Long): AppResult<Unit> {
        val existing = row(id)
        if (existing == null || existing.deletedAt != null) {
            return failure(MiraErrorCode.NOT_FOUND, "No live note with id $id")
        }
        replace(existing.copy(deletedAt = clock.now(), updatedAt = clock.now()))
        return AppResult.Success(Unit)
    }

    override suspend fun restore(id: Long): AppResult<Unit> {
        val existing = row(id)
        if (existing == null || existing.deletedAt == null) {
            return failure(MiraErrorCode.NOT_FOUND, "No binned note with id $id")
        }
        replace(existing.copy(deletedAt = null, updatedAt = clock.now()))
        return AppResult.Success(Unit)
    }

    private fun nameOf(tagId: Long): String = tags.nameOf(tagId) ?: "tag-$tagId"

    private fun live(): List<Note> = rows.filter { it.deletedAt == null && it.archivedAt == null }

    private fun replace(note: Note) {
        val at = rows.indexOfFirst { it.id == note.id }
        if (at >= 0) rows[at] = note
    }

    private fun change(id: Long, edit: (Note) -> Note): AppResult<Note> {
        val at = rows.indexOfFirst { it.id == id }
        if (at < 0) return failure(MiraErrorCode.NOT_FOUND, "No note with id $id")
        val saved = edit(rows[at])
        rows[at] = saved
        return AppResult.Success(saved)
    }
}

/** Only what the note verbs ask of handwriting: which pages a note has, and which note a page is on. */
internal class FakeInkPageRepository : InkPageRepository {

    private val rows = mutableListOf<InkPage>()
    private var nextId = 1L

    /** @return the page that was added, so a test can give a note handwriting to stand on. */
    fun seed(noteId: Long): InkPage {
        val page = InkPage(
            id = nextId++,
            noteId = noteId,
            strokeFileId = 1L,
            imageFileId = 2L,
            widthPx = 1440,
            heightPx = 3088,
            strokeCount = 12,
            createdAt = FixedMiraClock.DEFAULT_NOW,
            updatedAt = FixedMiraClock.DEFAULT_NOW,
        )
        rows += page
        return page
    }

    override fun observeForNote(noteId: Long): Flow<List<InkPage>> =
        flowOf(rows.filter { it.noteId == noteId })

    override suspend fun byId(id: Long): AppResult<InkPage> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No ink page with id $id")

    override suspend fun forNote(noteId: Long): AppResult<List<InkPage>> =
        AppResult.Success(rows.filter { it.noteId == noteId })


    override suspend fun save(noteId: Long, drawing: InkDrawing, pageId: Long?): AppResult<InkPage> =
        unusedNoteDouble()

    override suspend fun load(pageId: Long): AppResult<InkDrawing> = unusedNoteDouble()

    override suspend fun delete(pageId: Long): AppResult<Unit> = unusedNoteDouble()
}

/** The tags that already exist, which is the only thing "organize" is allowed to propose. */
internal class FakeTagRepository : TagRepository {

    private val rows = mutableListOf<Tag>()

    fun seed(vararg tags: Tag) {
        rows += tags
    }

    /** What a note's tag is called, for a fake note store that stores only the ids. */
    fun nameOf(id: Long): String? = rows.firstOrNull { it.id == id }?.name

    override fun observeAll(): Flow<List<Tag>> = flowOf(rows.toList())

    override fun observeUsage(): Flow<List<TagUsage>> = flowOf(rows.map { TagUsage(it, 0) })

    override suspend fun byId(id: Long): AppResult<Tag> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No tag with id $id")

    override suspend fun ensure(name: String): AppResult<Tag> = unusedNoteDouble()

    override suspend fun rename(id: Long, name: String): AppResult<Tag> = unusedNoteDouble()

    override suspend fun delete(id: Long): AppResult<Unit> = unusedNoteDouble()
}

/**
 * The clients that already exist, for linking and for matching a name in a note.
 *
 * [search] honours the filter it is given, because whether Mira offers to file a note under a
 * client Abu archived is decided by that argument and by nothing else.
 */
internal class FakeClientRepository : ClientRepository {

    private val rows = mutableListOf<Client>()

    /** Set to make the one-shot read fail, so a storage fault can be told apart from "no match". */
    var searchFails: Boolean = false

    fun seed(vararg clients: Client) {
        rows += clients
    }

    override fun observe(filter: ClientFilter): Flow<List<Client>> = flowOf(matching(filter))

    override suspend fun byId(id: Long): AppResult<Client> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No client with id $id")

    override suspend fun search(query: String?, filter: ClientFilter): AppResult<List<Client>> =
        if (searchFails) {
            failure(MiraErrorCode.STORAGE, "The client list could not be read")
        } else {
            AppResult.Success(matching(filter))
        }

    override suspend fun create(client: Client): AppResult<Client> = unusedNoteDouble()

    override suspend fun update(client: Client): AppResult<Client> = unusedNoteDouble()

    override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client> = unusedNoteDouble()

    override suspend fun softDelete(id: Long): AppResult<Unit> = unusedNoteDouble()

    override suspend fun restore(id: Long): AppResult<Client> = unusedNoteDouble()

    override suspend fun purge(id: Long): AppResult<Unit> = unusedNoteDouble()

    private fun matching(filter: ClientFilter): List<Client> = rows.filter { client ->
        when (filter) {
            ClientFilter.ALL -> true
            ClientFilter.ACTIVE -> !client.isArchived
            ClientFilter.ARCHIVED -> client.isArchived
        }
    }
}

/**
 * The projects that already exist, for linking and for matching a name in a note.
 *
 * [search] honours the filter for the reason [FakeClientRepository] does: a note must not be
 * filed under work that is over, and the filter is the only thing that says so.
 */
internal class FakeProjectRepository : ProjectRepository {

    private val rows = mutableListOf<Project>()

    fun seed(vararg projects: Project) {
        rows += projects
    }

    override fun observe(filter: ProjectFilter): Flow<List<Project>> = flowOf(matching(filter))

    override fun observeForClient(clientId: Long): Flow<List<Project>> =
        flowOf(rows.filter { it.clientId == clientId })

    override fun observeMilestones(projectId: Long): Flow<List<ProjectMilestone>> = flowOf(emptyList())

    override suspend fun byId(id: Long): AppResult<Project> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No project with id $id")

    override suspend fun search(query: String?, filter: ProjectFilter): AppResult<List<Project>> =
        AppResult.Success(matching(filter))

    override suspend fun create(project: Project, milestones: List<ProjectMilestone>): AppResult<Project> =
        unusedNoteDouble()

    override suspend fun update(project: Project): AppResult<Project> = unusedNoteDouble()

    override suspend fun setStatus(id: Long, status: ProjectStatus): AppResult<Project> = unusedNoteDouble()

    override suspend fun softDelete(id: Long): AppResult<Unit> = unusedNoteDouble()

    override suspend fun restore(id: Long): AppResult<Project> = unusedNoteDouble()

    override suspend fun purge(id: Long): AppResult<Unit> = unusedNoteDouble()

    override suspend fun addMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> =
        unusedNoteDouble()

    override suspend fun updateMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> =
        unusedNoteDouble()

    override suspend fun setMilestoneComplete(id: Long, complete: Boolean): AppResult<ProjectMilestone> =
        unusedNoteDouble()

    override suspend fun deleteMilestone(id: Long): AppResult<Unit> = unusedNoteDouble()

    override suspend fun reorderMilestones(
        projectId: Long,
        orderedIds: List<Long>,
    ): AppResult<List<ProjectMilestone>> = unusedNoteDouble()

    private fun matching(filter: ProjectFilter): List<Project> = rows.filter { stored ->
        when (filter) {
            ProjectFilter.ALL -> true
            ProjectFilter.OPEN -> stored.status !in Project.CLOSED_STATUSES
            ProjectFilter.ACTIVE -> stored.status == ProjectStatus.ACTIVE
            ProjectFilter.COMPLETED -> stored.status == ProjectStatus.COMPLETED
            ProjectFilter.CANCELLED -> stored.status == ProjectStatus.CANCELLED
        }
    }
}

private fun unusedNoteDouble(): Nothing = error("The note use cases do not use this")
