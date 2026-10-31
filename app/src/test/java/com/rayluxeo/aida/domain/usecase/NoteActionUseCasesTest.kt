package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.ink.HandwritingResult
import com.rayluxeo.aida.domain.ink.HandwritingUnavailable
import com.rayluxeo.aida.domain.model.NoteOrganization
import com.rayluxeo.aida.domain.model.NoteTaskCandidate
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.Tag
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The things Mira offers to do with a note once it is written: organize it, turn it into tasks,
 * and file the text a handwriting session produced.
 *
 * The suggestions are where the damage would be. A proposal Abu has to correct is worse than no
 * proposal, so the cases worth pinning down are the ones where Mira would have to guess — a
 * client called "Ray" against the word "array", two names that match equally well, a note that
 * is already filed — and in each of them she is expected to say nothing. Applying is where the
 * damage would be invisible: a part Abu has since decided himself is left alone, and when that
 * leaves nothing to do, Mira says so instead of reporting a change that never happened.
 *
 * Nothing is injected (google/dagger#5261), the fakes are hand written, and both scripts Abu
 * writes in are exercised: none of this may assume Latin letters.
 */
class NoteActionUseCasesTest {

    private val clock = FixedMiraClock()
    private val tags = FakeTagRepository()
    private val notes = FakeNoteRepository(clock, tags)
    private val inkPages = FakeInkPageRepository()
    private val clients = FakeClientRepository()
    private val projects = FakeProjectRepository()
    private val tasks = FakeTaskRepository(clock)
    private val steps = FakeSubtaskRepository(clock)

    private val update = UpdateNoteUseCase(notes, inkPages, clock)
    private val setTags = SetNoteTagsUseCase(notes)
    private val link = LinkNoteUseCase(notes, clients, projects, tasks)
    private val createTask = CreateTaskUseCase(tasks, steps, clock)

    private val organize = OrganizeNoteUseCase(notes, tags, clients, projects, update, setTags, link)
    private val turnIntoTasks = TurnNoteIntoTasksUseCase(notes, createTask, link)
    private val extractText = ExtractNoteTextUseCase(notes, update)

    // ---------------------------------------------------------------- organize

    @Test
    fun `organize proposes a title from the opening words when the note has none`() = runTest {
        val stored = notes.seed(note(body = "banner sizes for the shop\n1080 by 1350")).single()

        val proposal = organize(stored.id).getOrNull()!!

        assertThat(proposal.title).isEqualTo("banner sizes for the shop")
        assertThat(proposal.hasSuggestions).isTrue()
    }

    @Test
    fun `organize leaves a title Abu already wrote alone`() = runTest {
        val stored = notes.seed(note(title = "Banners", body = "banner sizes for the shop")).single()

        assertThat(organize(stored.id).getOrNull()!!.title).isNull()
    }

    @Test
    fun `organize cuts a long opening line at a word`() = runTest {
        val opening = "the printer needs the artwork at three hundred dpi before Thursday morning"
        val stored = notes.seed(note(body = opening)).single()

        val title = organize(stored.id).getOrNull()!!.title!!

        assertThat(opening).startsWith(title)
        assertThat(title.length).isAtMost(48)
        assertThat(title).doesNotContain("  ")
        assertThat(opening.split(' ')).contains(title.split(' ').last())
    }

    @Test
    fun `organize matches a tag that already exists on a whole word`() = runTest {
        tags.seed(Tag(2L, "invoice"), Tag(5L, "personal"))
        val stored = notes.seed(note(body = "send the Invoice on Monday")).single()

        assertThat(organize(stored.id).getOrNull()!!.tagIds).containsExactly(2L)
    }

    @Test
    fun `organize matches a Bengali tag in a Bengali note`() = runTest {
        tags.seed(Tag(9L, "চালান"))
        val stored = notes.seed(note(body = "কাল সকালে চালান পাঠাতে হবে")).single()

        assertThat(organize(stored.id).getOrNull()!!.tagIds).containsExactly(9L)
    }

    @Test
    fun `organize does not propose a tag the note already wears`() = runTest {
        tags.seed(Tag(2L, "invoice"))
        val stored = notes.seed(
            note(body = "send the invoice on Monday", tags = listOf(Tag(2L, "invoice"))),
        ).single()

        assertThat(organize(stored.id).getOrNull()!!.tagIds).isEmpty()
    }

    @Test
    fun `organize does not find Ray inside array`() = runTest {
        clients.seed(client(id = 3L, name = "Ray"))
        tags.seed(Tag(4L, "ray"))
        val stored = notes.seed(note(body = "the array of banners still needs sorting")).single()

        val proposal = organize(stored.id).getOrNull()!!

        assertThat(proposal.clientId).isNull()
        assertThat(proposal.tagIds).isEmpty()
    }

    @Test
    fun `organize does not find one Bengali word inside another`() = runTest {
        // "চাল" is rice and "চালান" is an invoice: the same trap as Ray inside array, in the
        // script where a vowel is a combining mark rather than a letter of its own.
        tags.seed(Tag(4L, "চাল"), Tag(9L, "চালান"))
        val stored = notes.seed(note(title = "চালান", body = "কাল সকালে চালান পাঠাতে হবে")).single()

        assertThat(organize(stored.id).getOrNull()!!.tagIds).containsExactly(9L)
    }

    @Test
    fun `organize finds Ray when the note actually names him`() = runTest {
        clients.seed(client(id = 3L, name = "Ray"))
        val stored = notes.seed(note(body = "Ray wants the banners by Thursday")).single()

        assertThat(organize(stored.id).getOrNull()!!.clientId).isEqualTo(3L)
    }

    @Test
    fun `organize prefers the longer name over the shorter one inside it`() = runTest {
        clients.seed(client(id = 3L, name = "Ray"), client(id = 4L, name = "Ray Luxeo"))
        val stored = notes.seed(note(body = "Ray Luxeo wants the banners")).single()

        assertThat(organize(stored.id).getOrNull()!!.clientId).isEqualTo(4L)
    }

    @Test
    fun `organize says nothing when two clients are named equally well`() = runTest {
        clients.seed(client(id = 3L, name = "Ray"), client(id = 4L, name = "Luxeo"))
        val stored = notes.seed(note(title = "Banners", body = "Ray and Luxeo both want banners")).single()

        val proposal = organize(stored.id).getOrNull()!!

        assertThat(proposal.clientId).isNull()
        assertThat(proposal.hasSuggestions).isFalse()
    }

    @Test
    fun `organize proposes nothing rather than a guess`() = runTest {
        tags.seed(Tag(2L, "invoice"))
        clients.seed(client(id = 3L, name = "Ray"))
        val stored = notes.seed(note(title = "Kitchen", body = "measure the worktop again")).single()

        val proposal = organize(stored.id).getOrNull()!!

        assertThat(proposal.hasSuggestions).isFalse()
        assertThat(notes.row(stored.id)).isEqualTo(stored)
    }

    @Test
    fun `organize never refiles a link Abu made himself`() = runTest {
        clients.seed(client(id = 3L, name = "Ray"))
        projects.seed(project(id = 8L, name = "Banners"))
        val stored = notes.seed(
            note(title = "Print run", body = "Ray wants the Banners reprinted", clientId = 7L, projectId = 9L),
        ).single()

        val proposal = organize(stored.id).getOrNull()!!

        assertThat(proposal.clientId).isNull()
        assertThat(proposal.projectId).isNull()
    }

    @Test
    fun `organize matches a client by the company he trades as`() = runTest {
        clients.seed(client(id = 3L, name = "Ahmed", companyName = "Northline Press"))
        val stored = notes.seed(note(body = "Northline Press asked for a quote")).single()

        assertThat(organize(stored.id).getOrNull()!!.clientId).isEqualTo(3L)
    }

    @Test
    fun `organize proposes the one project the note names`() = runTest {
        projects.seed(project(id = 8L, name = "Banner reprint"))
        val stored = notes.seed(
            note(title = "Print run", body = "the Banner reprint is due Thursday"),
        ).single()

        assertThat(organize(stored.id).getOrNull()!!.projectId).isEqualTo(8L)
    }

    @Test
    fun `organize leaves out a project Abu has already finished`() = runTest {
        projects.seed(project(id = 8L, name = "Banner reprint", status = ProjectStatus.CANCELLED))
        val stored = notes.seed(
            note(title = "Print run", body = "the Banner reprint is due Thursday"),
        ).single()

        assertThat(organize(stored.id).getOrNull()!!.projectId).isNull()
    }

    @Test
    fun `organize reports a storage fault rather than proposing nothing`() = runTest {
        // "Nothing to suggest" and "the list could not be read" look identical to Abu, so the
        // read is the one-shot that can fail rather than a flow that swallows it.
        clients.searchFails = true
        val stored = notes.seed(note(title = "Banners", body = "Ray wants the banners")).single()

        assertThat(organize(stored.id).errorCode()).isEqualTo(MiraErrorCode.STORAGE)
    }

    @Test
    fun `applying a proposal writes the title the tags and the link`() = runTest {
        clients.seed(client(id = 3L, name = "Ray"))
        val stored = notes.seed(
            note(body = "Ray wants the invoice", tags = listOf(Tag(5L, "personal"))),
        ).single()

        val applied = organize.applyProposal(
            noteId = stored.id,
            organization = NoteOrganization(title = "Ray wants the invoice", tagIds = setOf(2L), clientId = 3L),
        ).getOrNull()!!

        assertThat(applied.title).isEqualTo("Ray wants the invoice")
        assertThat(applied.tags.map { it.id }).containsExactly(5L, 2L)
        assertThat(applied.clientId).isEqualTo(3L)
    }

    @Test
    fun `applying a proposal files the note under the project Abu kept`() = runTest {
        projects.seed(project(id = 8L, name = "Banner reprint"))
        val stored = notes.seed(note(title = "Print run", body = "the Banner reprint is due")).single()

        val applied = organize.applyProposal(stored.id, NoteOrganization(projectId = 8L)).getOrNull()!!

        assertThat(applied.projectId).isEqualTo(8L)
        assertThat(notes.row(stored.id)?.projectId).isEqualTo(8L)
    }

    @Test
    fun `a proposal that fails part way leaves what already landed`() = runTest {
        val stored = notes.seed(note(body = "Ray wants the invoice")).single()

        val applied = organize.applyProposal(
            noteId = stored.id,
            // Client 99 is not there, so the link is refused after the title and the tag landed.
            organization = NoteOrganization(title = "Ray wants the invoice", tagIds = setOf(2L), clientId = 99L),
        )

        assertThat(applied.errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        val after = notes.row(stored.id)!!
        assertThat(after.title).isEqualTo("Ray wants the invoice")
        assertThat(after.tags.map { it.id }).containsExactly(2L)
        assertThat(after.clientId).isNull()
    }

    @Test
    fun `applying what the note already says is refused rather than reported as done`() = runTest {
        // Abu typed his own title while the sheet was open, and the title was all he kept.
        val stored = notes.seed(note(title = "Banners", body = "Ray wants the banners")).single()

        val applied = organize.applyProposal(stored.id, NoteOrganization(title = "Ray wants the banners"))

        assertThat(applied.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.row(stored.id)).isEqualTo(stored)
    }

    @Test
    fun `applying never refiles a note Abu linked in the meantime`() = runTest {
        clients.seed(client(id = 3L, name = "Ray"), client(id = 4L, name = "Luxeo"))
        val stored = notes.seed(
            note(title = "Banners", body = "Ray wants the banners", clientId = 3L),
        ).single()

        val applied = organize.applyProposal(stored.id, NoteOrganization(clientId = 4L))

        assertThat(applied.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.row(stored.id)?.clientId).isEqualTo(3L)
    }

    @Test
    fun `applying a proposal that adds a tag the note already wears writes nothing`() = runTest {
        val stored = notes.seed(
            note(title = "Banners", body = "send the invoice", tags = listOf(Tag(2L, "invoice"))),
        ).single()

        val applied = organize.applyProposal(stored.id, NoteOrganization(tagIds = setOf(2L)))

        assertThat(applied.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.tagWrites).isEqualTo(0)
    }

    @Test
    fun `applying an empty proposal is refused rather than written`() = runTest {
        val stored = notes.seed(note(body = "anything")).single()

        assertThat(organize.applyProposal(stored.id, NoteOrganization()).errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.row(stored.id)).isEqualTo(stored)
    }

    @Test
    fun `organize reports a note that is not there`() = runTest {
        assertThat(organize(404L).errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    // ----------------------------------------------------------- turn into tasks

    @Test
    fun `candidates select the lines Abu marked and strip the marker off`() = runTest {
        val stored = notes.seed(
            note(
                body = """
                    Shopping:
                    - rice
                    * 2 kg chicken
                    1. call the plumber
                    [ ] pay the electricity bill

                    The kitchen still needs doing before the guests arrive on Friday.
                """.trimIndent(),
            ),
        ).single()

        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        assertThat(candidates.map { it.line }).containsExactly(
            "Shopping:",
            "rice",
            "2 kg chicken",
            "call the plumber",
            "pay the electricity bill",
            "The kitchen still needs doing before the guests arrive on Friday.",
        ).inOrder()
        assertThat(candidates.filter { it.selected }.map { it.line }).containsExactly(
            "rice",
            "2 kg chicken",
            "call the plumber",
            "pay the electricity bill",
        )
    }

    @Test
    fun `candidates select short unmarked lines when nothing at all is marked`() = runTest {
        val stored = notes.seed(
            note(
                body = """
                    Notes:
                    call Ray about the banners
                    send the invoice
                    I think the kitchen needs doing before the guests arrive on Friday.
                """.trimIndent(),
            ),
        ).single()

        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        assertThat(candidates.filter { it.selected }.map { it.line })
            .containsExactly("call Ray about the banners", "send the invoice")
    }

    @Test
    fun `candidates take the hash off a heading and leave it unticked`() = runTest {
        // Ticking it used to make a task called "# Shopping", which is not something to do.
        val stored = notes.seed(note(body = "# Shopping\nrice\nchicken")).single()

        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        assertThat(candidates.map { it.line }).containsExactly("Shopping", "rice", "chicken").inOrder()
        assertThat(candidates.filter { it.selected }.map { it.line })
            .containsExactly("rice", "chicken")
    }

    @Test
    fun `candidates leave a hash that starts a word where it is`() = runTest {
        val stored = notes.seed(note(body = "#banners still need sorting")).single()

        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        assertThat(candidates.single().line).isEqualTo("#banners still need sorting")
        assertThat(candidates.single().selected).isTrue()
    }

    @Test
    fun `candidates read a year at the start of a line as a year`() = runTest {
        val stored = notes.seed(note(body = "2024. review the year\n1. call the plumber")).single()

        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        assertThat(candidates.map { it.line })
            .containsExactly("2024. review the year", "call the plumber").inOrder()
        assertThat(candidates.filter { it.selected }.map { it.line })
            .containsExactly("call the plumber")
    }

    @Test
    fun `candidates split Bengali lines and leave a finished sentence alone`() = runTest {
        val stored = notes.seed(
            note(
                body = """
                    কাল সকালে ফোন করতে হবে
                    আজ অফিসে অনেক কাজ ছিল।
                """.trimIndent(),
            ),
        ).single()

        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        assertThat(candidates.map { it.line })
            .containsExactly("কাল সকালে ফোন করতে হবে", "আজ অফিসে অনেক কাজ ছিল।").inOrder()
        assertThat(candidates.filter { it.selected }.map { it.line })
            .containsExactly("কাল সকালে ফোন করতে হবে")
    }

    @Test
    fun `candidates strip a Bengali numbered marker`() = runTest {
        val stored = notes.seed(note(body = "১. চালান পাঠাও\n২. টাকা জমা দাও")).single()

        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        assertThat(candidates.map { it.line }).containsExactly("চালান পাঠাও", "টাকা জমা দাও").inOrder()
        assertThat(candidates.all { it.selected }).isTrue()
    }

    @Test
    fun `created tasks go through the real create and are made where the note lives`() = runTest {
        projects.seed(project(id = 8L, name = "Banners"))
        clients.seed(client(id = 3L, name = "Ray"))
        val stored = notes.seed(
            note(body = "- rice\n- chicken", clientId = 3L, projectId = 8L),
        ).single()
        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        val created = turnIntoTasks.create(stored.id, candidates).getOrNull()!!

        assertThat(created.map { it.title }).containsExactly("rice", "chicken").inOrder()
        assertThat(created.map { it.clientId }).containsExactly(3L, 3L)
        assertThat(created.map { it.projectId }).containsExactly(8L, 8L)
        assertThat(created.map { it.origin }).containsExactly(RecordOrigin.MANUAL, RecordOrigin.MANUAL)
        assertThat(created.map { it.createdAt }).containsExactly(clock.now(), clock.now())
        assertThat(tasks.snapshot()).hasSize(2)
    }

    @Test
    fun `one task made from a note is linked back to it`() = runTest {
        val stored = notes.seed(note(body = "- call the plumber")).single()
        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        val created = turnIntoTasks.create(stored.id, candidates).getOrNull()!!

        assertThat(notes.row(stored.id)?.taskId).isEqualTo(created.single().id)
    }

    @Test
    fun `several tasks leave the note unlinked because only one link exists`() = runTest {
        val stored = notes.seed(note(body = "- rice\n- chicken")).single()
        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        turnIntoTasks.create(stored.id, candidates)

        assertThat(notes.row(stored.id)?.taskId).isNull()
    }

    @Test
    fun `a link Abu already made is not replaced by one made from a line`() = runTest {
        val existing = tasks.seed(task(title = "Write the copy")).single()
        val stored = notes.seed(note(body = "- call the plumber", taskId = existing.id)).single()
        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!

        turnIntoTasks.create(stored.id, candidates)

        assertThat(notes.row(stored.id)?.taskId).isEqualTo(existing.id)
    }

    @Test
    fun `creating nothing is refused rather than reported as done`() = runTest {
        val stored = notes.seed(note(body = "- rice")).single()
        val candidates = turnIntoTasks.candidates(stored.id).getOrNull()!!
            .map { it.copy(selected = false) }

        assertThat(turnIntoTasks.create(stored.id, candidates).errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(tasks.snapshot()).isEmpty()
    }

    @Test
    fun `a chosen line with nothing in it makes no task`() = runTest {
        val stored = notes.seed(note(body = "- rice")).single()

        val created = turnIntoTasks.create(stored.id, listOf(NoteTaskCandidate("   ", selected = true)))

        assertThat(created.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(tasks.snapshot()).isEmpty()
    }

    // ------------------------------------------------------------- extract text

    @Test
    fun `recognised text is appended to what the note already says`() = runTest {
        val stored = notes.seed(note(body = "typed earlier")).single()

        val saved = extractText(stored.id, HandwritingResult.Recognised("  written by hand  ")).getOrNull()!!

        // The blank line between them is the one copy of this rule; a screen joining them its own
        // way is how the same note reads differently depending on which control Abu used.
        assertThat(saved.body).isEqualTo("typed earlier\n\nwritten by hand")
    }

    @Test
    fun `recognised text becomes the body of a note that had only handwriting`() = runTest {
        val stored = notes.seed(note(body = "")).single()
        inkPages.seed(stored.id)

        val saved = extractText(stored.id, HandwritingResult.Recognised("কাল সকালে ফোন করতে হবে")).getOrNull()!!

        assertThat(saved.body).isEqualTo("কাল সকালে ফোন করতে হবে")
    }

    @Test
    fun `a page the recogniser made nothing of is explained and not written`() = runTest {
        val stored = notes.seed(note(body = "typed earlier")).single()

        val saved = extractText(stored.id, HandwritingResult.NoTextRecognised)

        assertThat(saved.errorCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(notes.row(stored.id)?.body).isEqualTo("typed earlier")
    }

    @Test
    fun `blank recognised text is treated as nothing recognised`() = runTest {
        val stored = notes.seed(note(body = "typed earlier")).single()

        assertThat(extractText(stored.id, HandwritingResult.Recognised("   ")).errorCode())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a keyboard that cannot read handwriting is reported as unavailable`() = runTest {
        val stored = notes.seed(note(body = "typed earlier")).single()

        val saved = extractText(
            stored.id,
            HandwritingResult.Unavailable(HandwritingUnavailable.NO_RECOGNISER),
        )

        assertThat(saved.errorCode()).isEqualTo(MiraErrorCode.ENGINE_UNAVAILABLE)
        assertThat(notes.row(stored.id)?.body).isEqualTo("typed earlier")
    }

    @Test
    fun `extracting into a note that is not there is refused`() = runTest {
        assertThat(extractText(404L, HandwritingResult.Recognised("anything")).errorCode())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `text is never filed onto a note in the bin`() = runTest {
        // Nothing else reads a binned note, so text put there is text Abu never sees again.
        val stored = notes.seed(note(body = "typed earlier", deletedAt = clock.now())).single()

        val saved = extractText(stored.id, HandwritingResult.Recognised("written by hand"))

        assertThat(saved.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.row(stored.id)?.body).isEqualTo("typed earlier")
    }
}
