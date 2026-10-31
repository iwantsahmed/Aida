package com.rayluxeo.aida.data.note

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.knowledge.SEED_MILLIS
import com.rayluxeo.aida.data.knowledge.expectFailure
import com.rayluxeo.aida.data.knowledge.expectSuccess
import com.rayluxeo.aida.data.knowledge.noteEntity
import com.rayluxeo.aida.data.knowledge.openKnowledgeDatabase
import com.rayluxeo.aida.data.repository.NoteQuerySql
import com.rayluxeo.aida.data.repository.NoteRepositoryImpl
import com.rayluxeo.aida.data.repository.taskEntity
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.NoteQuery
import com.rayluxeo.aida.domain.model.NoteSort
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.repository.NoteRepository
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a notes list is allowed to show.
 *
 * Every test here is about a note being in one list and out of another — filed, binned, pinned,
 * tagged, handwritten. Those are the rules a screen cannot be trusted to remember for itself, and
 * the ones Abu would not notice being broken until something he had filed walked back into "my
 * notes" or something he had binned came back out of search.
 *
 * One dispatcher for the class, handed to both the repository and `runTest`: two schedulers in
 * one test is the "Detected use of different schedulers" failure, and it looks like a flake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NoteRepositoryQueryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))
    private lateinit var database: MiraDatabase

    @Before
    fun openDatabase() {
        database = openKnowledgeDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an archived note leaves my notes without going to the bin`() = runTest(dispatcher) {
        val repository = repository()
        val kept = insert(body = "gold logo, no purple")
        val filed = insert(body = "spring campaign wrapped up")

        assertThat(repository.archive(filed).expectSuccess().archivedAt).isEqualTo(clock.now())

        assertThat(repository.observe(NoteFilter.All).first().map { it.id }).containsExactly(kept)
        assertThat(repository.observe(NoteFilter.Archived).first().map { it.id })
            .containsExactly(filed)
        assertThat(repository.observe(NoteFilter.Deleted).first()).isEmpty()

        assertThat(repository.unarchive(filed).expectSuccess().archivedAt).isNull()
        assertThat(repository.observe(NoteFilter.All).first().map { it.id })
            .containsExactly(kept, filed)
    }

    @Test
    fun `archiving twice reports NOT_FOUND rather than moving the moment it was filed`() =
        runTest(dispatcher) {
            val repository = repository()
            val id = insert(body = "spring campaign wrapped up")
            val filedAt = repository.archive(id).expectSuccess().archivedAt

            clock.moveTo(LocalDateTime.of(2026, 3, 6, 9, 0))

            assertThat(repository.archive(id).expectFailure()).isEqualTo(MiraErrorCode.NOT_FOUND)
            assertThat(repository.byId(id).expectSuccess().archivedAt).isEqualTo(filedAt)
        }

    @Test
    fun `a binned note is in neither my notes nor the archive`() = runTest(dispatcher) {
        val repository = repository()
        val live = insert(body = "still open")
        val binned = insert(body = "written by mistake")
        val filedAndBinned = insert(
            body = "filed, then binned",
            archivedAtEpochMillis = SEED_MILLIS,
            deletedAtEpochMillis = SEED_MILLIS,
        )
        repository.delete(binned)

        assertThat(repository.observe(NoteFilter.All).first().map { it.id }).containsExactly(live)
        assertThat(repository.observe(NoteFilter.Archived).first()).isEmpty()
        assertThat(repository.observe(NoteFilter.Deleted).first().map { it.id })
            .containsExactly(binned, filedAndBinned)
    }

    @Test
    fun `saving a note cannot unfile it or take it out of the bin`() = runTest(dispatcher) {
        val repository = repository()
        val filed = insert(body = "spring campaign wrapped up")
        val binned = insert(body = "written by mistake")
        val filedAt = repository.archive(filed).expectSuccess().archivedAt
        repository.delete(binned)

        // An editor open since before Abu filed the note holds a copy that says it is live. Only
        // archive, unarchive, delete and restore move a note between those states; saving what is
        // in the editor takes the words and leaves the filing where he put it.
        val stale = repository.byId(filed).expectSuccess()
        val saved = repository
            .update(stale.copy(body = "spring campaign, reworded", archivedAt = null))
            .expectSuccess()

        assertThat(saved.archivedAt).isEqualTo(filedAt)
        assertThat(saved.body).isEqualTo("spring campaign, reworded")
        assertThat(repository.observe(NoteFilter.All).first()).isEmpty()

        val staleBinned = repository.byId(binned).expectSuccess()
        assertThat(repository.update(staleBinned.copy(deletedAt = null)).expectSuccess().deletedAt)
            .isNotNull()
        assertThat(repository.update(stale.copy(id = 404L)).expectFailure())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a pinned note stays on top under every sort and both directions`() = runTest(dispatcher) {
        val repository = repository()
        val pinned = insert(
            title = "Aaa first alphabetically",
            body = "pinned and old",
            isPinned = true,
        )
        val newest = insert(
            title = "Zzz last alphabetically",
            body = "newer, and not pinned",
            createdAtEpochMillis = SEED_MILLIS + 10 * MINUTE_MILLIS,
        )

        NoteSort.entries.forEach { sort ->
            listOf(true, false).forEach { ascending ->
                val ids = repository
                    .observe(NoteQuery(sort = sort, ascending = ascending))
                    .first()
                    .map { it.id }

                assertThat(ids).containsExactly(pinned, newest)
                assertThat(ids.first()).isEqualTo(pinned)
            }
        }
    }

    @Test
    fun `a sort still orders the notes that are not pinned`() = runTest(dispatcher) {
        val repository = repository()
        val older = insert(title = "Brief", body = "older", createdAtEpochMillis = SEED_MILLIS)
        val newer = insert(
            title = "Amounts",
            body = "newer",
            createdAtEpochMillis = SEED_MILLIS + 10 * MINUTE_MILLIS,
        )

        assertThat(repository.observe(NoteQuery(sort = NoteSort.CREATED)).first().map { it.id })
            .containsExactly(newer, older).inOrder()
        assertThat(
            repository.observe(NoteQuery(sort = NoteSort.CREATED, ascending = true))
                .first().map { it.id },
        ).containsExactly(older, newer).inOrder()
        assertThat(
            repository.observe(NoteQuery(sort = NoteSort.TITLE, ascending = true))
                .first().map { it.id },
        ).containsExactly(newer, older).inOrder()
    }

    @Test
    fun `search finds a word in the body and never a binned note`() = runTest(dispatcher) {
        val repository = repository()
        val live = insert(title = "Rahman", body = "champagne foil on the invoice")
        val binned = insert(body = "foil order cancelled")
        val filed = insert(body = "foil supplier, last spring", archivedAtEpochMillis = SEED_MILLIS)
        repository.delete(binned)

        assertThat(repository.search("foil").expectSuccess().map { it.id }).containsExactly(live)
        assertThat(repository.search("FOIL").expectSuccess().map { it.id }).containsExactly(live)

        // The index shadows hidden rows too, so the filters have to sit on the notes table rather
        // than on the join. Asked for the archive or the bin by name, the text condition searches
        // what is in them; asked for "my notes", as `search` does, it reaches neither.
        assertThat(
            repository.list(NoteQuery(filter = NoteFilter.Archived, text = "foil"))
                .expectSuccess().map { it.id },
        ).containsExactly(filed)
        assertThat(
            repository.list(NoteQuery(filter = NoteFilter.Deleted, text = "foil"))
                .expectSuccess().map { it.id },
        ).containsExactly(binned)
    }

    @Test
    fun `two tags narrow the list to the notes carrying both`() = runTest(dispatcher) {
        val repository = repository()
        val urgent = database.seedTag("urgent")
        val rayluxeo = database.seedTag("rayluxeo")
        val both = insert(body = "chase the foil quote")
        val onlyUrgent = insert(body = "call the printer")
        repository.setTags(both, listOf(urgent, rayluxeo))
        repository.setTags(onlyUrgent, listOf(urgent))

        assertThat(
            repository.observe(NoteQuery(tagIds = setOf(urgent))).first().map { it.id },
        ).containsExactly(both, onlyUrgent)
        assertThat(
            repository.observe(NoteQuery(tagIds = setOf(urgent, rayluxeo))).first().map { it.id },
        ).containsExactly(both)
        assertThat(repository.observe(NoteFilter.ForTag(rayluxeo)).first().map { it.id })
            .containsExactly(both)

        // A tag-scoped list and a tapped chip are one condition, not two mechanisms that have to
        // agree: every tag named has to be on the note, however it was named.
        assertThat(
            repository
                .observe(NoteQuery(filter = NoteFilter.ForTag(rayluxeo), tagIds = setOf(urgent)))
                .first().map { it.id },
        ).containsExactly(both)

        assertThat(repository.byId(both).expectSuccess().tags.map { it.name })
            .containsExactly("rayluxeo", "urgent").inOrder()
        assertThat(repository.observeUsedTags().first().map { it.name })
            .containsExactly("rayluxeo", "urgent").inOrder()
    }

    @Test
    fun `a tag is offered as a chip only while a live note wears it`() = runTest(dispatcher) {
        val repository = repository()
        val urgent = database.seedTag("urgent")
        val mistake = database.seedTag("mistake")
        val spring = database.seedTag("spring")
        val live = insert(body = "chase the foil quote")
        val binned = insert(body = "written by mistake")
        val filed = insert(body = "spring campaign wrapped up")
        repository.setTags(live, listOf(urgent))
        repository.setTags(binned, listOf(mistake))
        repository.setTags(filed, listOf(spring))
        repository.delete(binned)
        repository.archive(filed)

        // The links in note_tags survive both, so the chip row has to ask the notes and not the
        // join: a chip that answers with an empty list reads as a filter that does not work.
        assertThat(repository.observeUsedTags().first().map { it.name }).containsExactly("urgent")
    }

    @Test
    fun `text, a tag and the archive rule narrow together`() = runTest(dispatcher) {
        val repository = repository()
        val urgent = database.seedTag("urgent")
        val wanted = insert(body = "chase the foil quote")
        val otherTagged = insert(body = "chase the paper quote")
        val untagged = insert(body = "foil, but nobody asked")
        val filed = insert(body = "foil quote, last spring")
        repository.setTags(wanted, listOf(urgent))
        repository.setTags(otherTagged, listOf(urgent))
        repository.setTags(filed, listOf(urgent))
        repository.archive(filed)

        val found = repository
            .list(NoteQuery(text = "foil", tagIds = setOf(urgent)))
            .expectSuccess()
            .map { it.id }

        // Three conditions, three sets of bound values, one statement: if they went in out of
        // order this comes back with the wrong notes rather than with an error.
        assertThat(found).containsExactly(wanted)
        assertThat(found).doesNotContain(otherTagged)
        assertThat(found).doesNotContain(untagged)
        assertThat(found).doesNotContain(filed)
    }

    @Test
    fun `tagging a note that is not there is NOT_FOUND rather than a constraint failure`() =
        runTest(dispatcher) {
            val repository = repository()
            val tagId = database.seedTag("urgent")

            assertThat(repository.addTag(noteId = 404L, tagId = tagId).expectFailure())
                .isEqualTo(MiraErrorCode.NOT_FOUND)
            assertThat(repository.setTags(404L, listOf(tagId)).expectFailure())
                .isEqualTo(MiraErrorCode.NOT_FOUND)
        }

    @Test
    fun `a note in the bin is refused a tag, and a filed note is not`() = runTest(dispatcher) {
        val repository = repository()
        val tagId = database.seedTag("urgent")
        val binned = insert(body = "written by mistake")
        val filed = insert(body = "spring campaign wrapped up")
        repository.delete(binned)
        repository.archive(filed)

        // findById reads a note whatever state it is in, so without the check these would all be
        // written: a label on something Abu has thrown away, which he would next meet as a chip
        // that finds nothing.
        assertThat(repository.addTag(binned, tagId).expectFailure())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.setTags(binned, listOf(tagId)).expectFailure())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.removeTag(binned, tagId).expectFailure())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.byId(binned).expectSuccess().tags).isEmpty()

        // Filing a note away is not throwing it away.
        assertThat(repository.addTag(filed, tagId).expectSuccess().tags.map { it.id })
            .containsExactly(tagId)
    }

    @Test
    fun `adding a tag twice leaves the note carrying it once`() = runTest(dispatcher) {
        val repository = repository()
        val tagId = database.seedTag("urgent")
        val id = insert(body = "chase the foil quote")

        repository.addTag(id, tagId)
        val twice = repository.addTag(id, tagId).expectSuccess()

        assertThat(twice.tags.map { it.id }).containsExactly(tagId)
        assertThat(repository.removeTag(id, tagId).expectSuccess().tags).isEmpty()
    }

    @Test
    fun `the handwriting filter holds only the notes carrying a page`() = runTest(dispatcher) {
        val repository = repository()
        val written = insert(body = "sketched the layout")
        insert(body = "typed, no handwriting")
        val archivedWithInk = insert(body = "old sketch", archivedAtEpochMillis = SEED_MILLIS)
        database.seedInkPage(written)
        database.seedInkPage(archivedWithInk)

        assertThat(repository.observe(NoteFilter.Ink).first().map { it.id })
            .containsExactly(written)
    }

    @Test
    fun `the handwriting filter is empty rather than everything when nothing is written`() =
        runTest(dispatcher) {
            val repository = repository()
            insert(body = "typed, no handwriting")

            assertThat(repository.observe(NoteFilter.Ink).first()).isEmpty()
        }

    @Test
    fun `the handwriting filter is not written into the statement`() {
        // The ids of the notes carrying ink used to be spelled into this SQL, which froze the
        // answer for as long as a screen stayed subscribed — a page written afterwards never
        // appeared. The statement asks for "my notes" and the repository narrows what comes back,
        // on every emission. If a condition over ink_pages reappears here, so does that bug.
        assertThat(NoteQuerySql.rows(NoteQuery(filter = NoteFilter.Ink)))
            .isEqualTo(NoteQuerySql.rows(NoteQuery(filter = NoteFilter.All)))
    }

    @Test
    fun `recent notes are the newest few, and never the filed or binned ones`() =
        runTest(dispatcher) {
            val repository = repository()
            val binned = insert(body = "mistake")
            repository.delete(binned)
            insert(body = "filed away", archivedAtEpochMillis = SEED_MILLIS)
            val written = (1..NoteQuerySql.RECENT_LIMIT + 3).map { minute ->
                insert(
                    body = "note $minute",
                    createdAtEpochMillis = SEED_MILLIS + minute * MINUTE_MILLIS,
                )
            }

            val recent = repository.observe(NoteFilter.Recent).first()

            assertThat(recent).hasSize(NoteQuerySql.RECENT_LIMIT)
            assertThat(recent.first().id).isEqualTo(written.last())
            assertThat(recent.map { it.id }).doesNotContain(binned)
        }

    @Test
    fun `the recent list is the newest notes even when everything else is pinned`() =
        runTest(dispatcher) {
            val repository = repository()
            val pinned = (1..NoteQuerySql.RECENT_LIMIT).map { minute ->
                insert(
                    title = "Aaa pinned $minute",
                    body = "pinned $minute",
                    isPinned = true,
                    createdAtEpochMillis = SEED_MILLIS + minute * MINUTE_MILLIS,
                )
            }
            val newest = insert(
                title = "Zzz written just now",
                body = "the note he is looking for",
                createdAtEpochMillis = SEED_MILLIS + 1_000 * MINUTE_MILLIS,
            )

            // A hub row is a fixed window, not a list Abu sorts: pinning twenty notes must not
            // push every recent one out of it, and a title sort must not turn "recent" into
            // "alphabetically first".
            val recent = repository
                .observe(
                    NoteQuery(
                        filter = NoteFilter.Recent,
                        sort = NoteSort.TITLE,
                        ascending = true,
                    ),
                )
                .first()

            assertThat(recent).hasSize(NoteQuerySql.RECENT_LIMIT)
            assertThat(recent.first().id).isEqualTo(newest)
            assertThat(recent.map { it.id }).doesNotContain(pinned.first())
        }

    @Test
    fun `a new note starts live and wearing no tags, whatever it arrived carrying`() =
        runTest(dispatcher) {
            val repository = repository()
            val urgent = database.seedTag("urgent")

            val created = repository.create(
                Note(
                    body = "written from the widget",
                    tags = listOf(Tag(id = urgent, name = "urgent")),
                    archivedAt = clock.now(),
                    deletedAt = clock.now(),
                    createdAt = clock.now(),
                    updatedAt = clock.now(),
                ),
            ).expectSuccess()

            assertThat(created.archivedAt).isNull()
            assertThat(created.deletedAt).isNull()
            // Tags are a second table and setTags is the one verb that writes it, so a note is
            // never reachable wearing half of them. The rule that a tag has to exist first is
            // NoteRules.tags, in the domain, and is stated once.
            assertThat(created.tags).isEmpty()
            assertThat(repository.observe(NoteFilter.All).first().map { it.id })
                .containsExactly(created.id)
            assertThat(
                repository.setTags(created.id, listOf(urgent)).expectSuccess().tags.map { it.id },
            ).containsExactly(urgent)
        }

    @Test
    fun `a task link is set and cleared, and scopes its own list`() = runTest(dispatcher) {
        val repository = repository()
        val taskId = database.taskDao().insert(taskEntity(title = "Reprint the foil"))
        val id = insert(body = "about the reprint")

        assertThat(repository.setTaskLink(id, taskId).expectSuccess().taskId).isEqualTo(taskId)
        assertThat(repository.observe(NoteFilter.ForTask(taskId)).first().map { it.id })
            .containsExactly(id)

        assertThat(repository.setTaskLink(id, taskId = null).expectSuccess().taskId).isNull()
        assertThat(repository.observe(NoteFilter.ForTask(taskId)).first()).isEmpty()
        assertThat(repository.setTaskLink(404L, taskId = null).expectFailure())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    private fun repository(): NoteRepository = NoteRepositoryImpl(
        dao = database.noteDao(),
        inkPages = database.inkPageDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private suspend fun insert(
        title: String? = null,
        body: String,
        isPinned: Boolean = false,
        archivedAtEpochMillis: Long? = null,
        deletedAtEpochMillis: Long? = null,
        createdAtEpochMillis: Long = SEED_MILLIS,
    ): Long = database.noteDao().insert(
        noteEntity(
            title = title,
            body = body,
            isPinned = isPinned,
            archivedAtEpochMillis = archivedAtEpochMillis,
            deletedAtEpochMillis = deletedAtEpochMillis,
            createdAtEpochMillis = createdAtEpochMillis,
            // A note that was written later was also touched later, which is what the default
            // sort and the recent window both read.
            updatedAtEpochMillis = createdAtEpochMillis,
        ),
    )
}
