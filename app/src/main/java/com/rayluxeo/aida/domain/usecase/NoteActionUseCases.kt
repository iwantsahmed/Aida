package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.ink.HandwritingResult
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.NoteOrganization
import com.rayluxeo.aida.domain.model.NoteTaskCandidate
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.TagRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The most a title may run to before it stops being a title and starts being the note again.
 */
private const val TITLE_MAX_CHARS = 48

/**
 * The fewest characters a name may match on.
 *
 * A one-letter client or tag would match nearly every note Abu has ever written, and a suggestion
 * he has to undo is worse than no suggestion at all.
 */
private const val MIN_MATCH_CHARS = 2

/** Past this many words a line is a sentence about the work rather than one thing to do. */
private const val MAX_TASK_WORDS = 12

/**
 * The longest run of digits that can still be a list number.
 *
 * A list Abu writes in a note does not reach a thousand items, and a four-digit run before a full
 * stop is a year: "2024. review the year" is a heading with a date on it, not the two-thousand-
 * and-twenty-fourth thing to do, and stripping the year would leave a task he never wrote.
 */
private const val MAX_LIST_NUMBER_DIGITS = 3

private val BULLETS: Set<Char> = setOf('-', '–', '—', '*', '•', '·', '‣', '▪', '◦')

private val CHECKBOXES: List<String> = listOf("[ ]", "[]", "[x]", "[X]", "☐", "☑", "✓", "✔")

/** A full stop in either script. The Bengali দাঁড়ি ends a sentence exactly as a period does. */
private val SENTENCE_ENDINGS: Set<Char> = setOf('.', '!', '?', '।', '॥')

/**
 * The marks that are not letters in Unicode's sense but are letters in Abu's.
 *
 * Bengali carries its vowels as combining marks — the "া" of "চালান" is a spacing mark, not a
 * letter. Left out of a word, "চালান" would break into "চ", "ল", "ন", and "চাল" would then match
 * inside it: the exact mistake the whole-word rule exists to prevent, only in the script Abu
 * writes most.
 */
private val WORD_MARKS: Set<CharCategory> = setOf(
    CharCategory.NON_SPACING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
    CharCategory.ENCLOSING_MARK,
)

/** Bengali joins its conjuncts with these, and a joiner sits inside a word, never between two. */
private const val ZERO_WIDTH_NON_JOINER = '‌'
private const val ZERO_WIDTH_JOINER = '‍'

/**
 * Where a word ends, for the purpose of matching a name against a note.
 *
 * This draws the boundary exactly where `ai/nlu`'s normalizer draws it — the same three mark
 * categories, the same two joiners, the same underscore — because search, normalization and
 * organize disagreeing about where "চালান" ends is how one of them quietly answers with
 * confidence about a word that was never there. It is a second statement of that rule only
 * because the layering forbids the shorter route: `domain` may not import `ai`, and the
 * normalizer's copy is private to it. The rule belongs in one pure function under `domain`, and
 * both should call it.
 */
private fun isWordCharacter(character: Char): Boolean =
    character.isLetterOrDigit() ||
        character.category in WORD_MARKS ||
        character == '_' ||
        character == ZERO_WIDTH_NON_JOINER ||
        character == ZERO_WIDTH_JOINER

/**
 * The words of a text, lowercased, with everything between them dropped.
 *
 * Words rather than substrings, and that is the whole point of it: a client called "Ray" must not
 * be proposed because the note happens to say "array". What counts as a word is Unicode's answer
 * rather than the ASCII table's, so a Bengali sentence breaks into words exactly as an English
 * one does — see [isWordCharacter], which is what makes that true rather than nearly true.
 */
private fun noteTokens(text: String): List<String> {
    val tokens = mutableListOf<String>()
    val word = StringBuilder()
    for (character in text) {
        if (isWordCharacter(character)) {
            word.append(character.lowercaseChar())
        } else if (word.isNotEmpty()) {
            tokens += word.toString()
            word.clear()
        }
    }
    if (word.isNotEmpty()) tokens += word.toString()
    return tokens
}

/**
 * How many words of [tokens] [phrase] took, or null when it is not in there as whole words.
 *
 * The length is what lets a longer name win over a shorter one it contains, so a note about
 * "Ray Luxeo" is filed under Ray Luxeo rather than being called ambiguous against Ray.
 */
private fun matchedWords(tokens: List<String>, phrase: String): Int? {
    val needle = noteTokens(phrase)
    if (needle.isEmpty() || needle.sumOf { it.length } < MIN_MATCH_CHARS) return null
    if (needle.size > tokens.size) return null
    for (start in 0..(tokens.size - needle.size)) {
        var matches = true
        for (offset in needle.indices) {
            if (tokens[start + offset] != needle[offset]) {
                matches = false
                break
            }
        }
        if (matches) return needle.size
    }
    return null
}

/**
 * The one candidate named in [tokens], or null when none is or when two are named equally well.
 *
 * Ambiguity is answered with silence rather than with a coin toss: Mira proposing the wrong
 * client silently refiles something Abu wrote, and he has no way to notice it happened.
 */
private fun <T> singleMatch(
    tokens: List<String>,
    candidates: List<T>,
    namesOf: (T) -> List<String>,
): T? {
    var best: T? = null
    var bestWords = 0
    var tied = false
    for (candidate in candidates) {
        val words = namesOf(candidate).mapNotNull { matchedWords(tokens, it) }.maxOrNull() ?: continue
        when {
            words > bestWords -> {
                best = candidate
                bestWords = words
                tied = false
            }
            words == bestWords -> tied = true
        }
    }
    return if (tied) null else best
}

/**
 * A line of a note once its markers, if it had any, have been taken off.
 *
 * [marked] and [heading] are two different statements and are kept apart on purpose: a bullet
 * says Abu meant a thing to do, a hash says he meant a title for the things under it.
 */
private data class MarkedLine(val text: String, val marked: Boolean, val heading: Boolean)

/**
 * Strips every list marker and heading marker off the front of a line.
 *
 * It loops because Abu writes "- [ ] call the printer" as readily as either half alone, and a
 * single pass would leave the checkbox sitting in the task's title.
 */
private fun markerOf(raw: String): MarkedLine {
    var rest = raw.trim()
    var marked = false
    var heading = false
    var stripping = true
    while (stripping) {
        stripping = false
        if (rest.isNotEmpty() && rest.first() in BULLETS) {
            rest = rest.drop(1).trimStart()
            marked = true
            stripping = true
        }
        val box = CHECKBOXES.firstOrNull { rest.startsWith(it) }
        if (box != null) {
            rest = rest.removePrefix(box).trimStart()
            marked = true
            stripping = true
        }
        val numbered = numberPrefixLength(rest)
        if (numbered > 0) {
            rest = rest.drop(numbered).trimStart()
            marked = true
            stripping = true
        }
        val hashes = headingPrefixLength(rest)
        if (hashes > 0) {
            rest = rest.drop(hashes).trimStart()
            heading = true
            stripping = true
        }
    }
    return MarkedLine(rest, marked, heading)
}

/**
 * The length of a "1." or "৩)" at the front of [line], or zero.
 *
 * [Char.isDigit] is Unicode's question, so Bengali numerals number a list here exactly as ASCII
 * ones do. A bare year is left alone by [MAX_LIST_NUMBER_DIGITS] rather than by hoping it has no
 * separator after it, because "2024." has one.
 */
private fun numberPrefixLength(line: String): Int {
    var index = 0
    while (index < line.length && line[index].isDigit()) index++
    if (index == 0 || index > MAX_LIST_NUMBER_DIGITS || index >= line.length) return 0
    return if (line[index] == '.' || line[index] == ')') index + 1 else 0
}

/**
 * The length of a "#" heading marker at the front of [line], or zero.
 *
 * The space after the hashes is required, which is what keeps "#chalan" a word Abu wrote rather
 * than a heading with its first letters eaten.
 */
private fun headingPrefixLength(line: String): Int {
    val hashes = line.takeWhile { it == '#' }.length
    if (hashes == 0) return 0
    return if (hashes == line.length || line[hashes].isWhitespace()) hashes else 0
}

/** A line that names what follows it rather than being one of them. */
private fun isHeading(line: MarkedLine): Boolean =
    line.heading || line.text.endsWith(":") || line.text.endsWith("：")

/**
 * Counted by runs of non-space rather than by splitting on one space character, because a line
 * pasted from elsewhere arrives full of the spaces that are not `' '`.
 */
private fun wordCount(text: String): Int {
    var words = 0
    var inWord = false
    for (character in text) {
        if (character.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            words++
            inWord = true
        }
    }
    return words
}

/**
 * Whether an unmarked line reads as one thing to do rather than as prose about it.
 *
 * There is deliberately no table of verbs here. One would have to carry English, Bengali and
 * Banglish at once to be fair to how Abu writes, and whichever of the three ended up thinnest
 * would quietly decide that half his lines are not tasks. Shape is something all three share: a
 * short line that does not close with a full stop or a দাঁড়ি is a thing jotted down, and a long
 * one that does is a sentence.
 */
private fun looksLikeOneThingToDo(text: String): Boolean =
    text.last() !in SENTENCE_ENDINGS && wordCount(text) <= MAX_TASK_WORDS

/**
 * What Mira would suggest doing to a note, and — separately — doing it once Abu has agreed.
 *
 * Nothing is invented. Tags are matched against tags that already exist, names against clients
 * and projects that already exist, and a title is taken from the note's own opening words. Mira
 * proposing a tag Abu has never used would mean correcting her, which is more work than typing
 * it himself, so a proposal with nothing in it comes back empty rather than approximate.
 */
class OrganizeNoteUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val tags: TagRepository,
    private val clients: ClientRepository,
    private val projects: ProjectRepository,
    private val update: UpdateNoteUseCase,
    private val setTags: SetNoteTagsUseCase,
    private val link: LinkNoteUseCase,
) {

    /**
     * Reads the note and proposes; writes nothing, whatever it finds.
     *
     * The candidates are read through the one-shot reads rather than by taking the first value of
     * a list flow, so a storage failure is reported as one. A flow read swallows it into an empty
     * list, and Mira would then say there was nothing to suggest — an answer Abu cannot tell from
     * the truth.
     */
    suspend operator fun invoke(noteId: Long): AppResult<NoteOrganization> {
        val note = when (val found = notes.byId(noteId)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        val tokens = noteTokens(listOfNotNull(note.title, note.body).joinToString(separator = " "))

        val worn = note.tags.map { it.id }.toSet()
        val suggestedTags = tags.observeAll().first()
            .filter { it.id !in worn && matchedWords(tokens, it.name) != null }
            .map { it.id }
            .toSet()

        // A link Abu already made is never overwritten: a proposal may fill a blank, never
        // refile something he decided himself. Finished work is not offered at all — a note
        // mentioning a project he cancelled last year belongs nowhere near it.
        val client = if (note.clientId != null) {
            null
        } else {
            when (val found = clients.search(query = null, filter = ClientFilter.ACTIVE)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> singleMatch(tokens, found.value) { candidate ->
                    listOfNotNull(candidate.name, candidate.companyName)
                }
            }
        }
        val project = if (note.projectId != null) {
            null
        } else {
            when (val found = projects.search(query = null, filter = ProjectFilter.OPEN)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> singleMatch(tokens, found.value) { listOf(it.name) }
            }
        }

        return AppResult.Success(
            NoteOrganization(
                title = if (note.title.trimmedOrNull() == null) openingTitle(note.body) else null,
                tagIds = suggestedTags,
                clientId = client?.id,
                projectId = project?.id,
            ),
        )
    }

    /**
     * Writes the parts of [organization] Abu kept that are still his to fill in.
     *
     * Each part is its own write, because a title, a tag set and a link live in three different
     * places and there is no transaction spanning them. A failure part way through therefore
     * leaves what already landed, and returns the failure rather than reporting a success that
     * only half happened — the note he is looking at is the truth either way.
     *
     * Every part is checked against the note as it is **now**, not as it was when the proposal was
     * made: a title he typed, a client he picked or a tag he added in between is his decision and
     * a suggestion may not overwrite it. When that leaves nothing at all to write, this refuses
     * rather than returning the untouched note — the screen says "done" on a success, and Mira
     * claiming a change landed when none did is the one mistake Abu has no way to notice.
     */
    suspend fun applyProposal(noteId: Long, organization: NoteOrganization): AppResult<Note> {
        if (!organization.hasSuggestions) {
            return failure(MiraErrorCode.VALIDATION, "There was nothing to apply to note $noteId")
        }
        var note = when (val found = notes.byId(noteId)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        var wrote = false

        val title = organization.title.trimmedOrNull()
        if (title != null && note.title.trimmedOrNull() == null) {
            note = when (val saved = update(note.copy(title = title))) {
                is AppResult.Failure -> return saved
                is AppResult.Success -> saved.value
            }
            wrote = true
        }
        val newTags = organization.tagIds - note.tags.map { it.id }.toSet()
        if (newTags.isNotEmpty()) {
            note = when (val saved = setTags(noteId, note.tags.map { it.id } + newTags)) {
                is AppResult.Failure -> return saved
                is AppResult.Success -> saved.value
            }
            wrote = true
        }
        if (organization.clientId != null && note.clientId == null) {
            note = when (val saved = link.toClient(noteId, organization.clientId)) {
                is AppResult.Failure -> return saved
                is AppResult.Success -> saved.value
            }
            wrote = true
        }
        if (organization.projectId != null && note.projectId == null) {
            note = when (val saved = link.toProject(noteId, organization.projectId)) {
                is AppResult.Failure -> return saved
                is AppResult.Success -> saved.value
            }
            wrote = true
        }

        return if (wrote) {
            AppResult.Success(note)
        } else {
            failure(
                MiraErrorCode.VALIDATION,
                "Note $noteId already carries everything the proposal offered",
            )
        }
    }

    /**
     * A title taken from the note's opening words, or null when there are none.
     *
     * Cut at a word boundary rather than mid-word, and with no ellipsis: what comes back is
     * offered to Abu as a title he can edit, and a trailing "…" in his own list would read as
     * something having gone wrong.
     */
    private fun openingTitle(body: String): String? {
        val opening = body.lines().map { markerOf(it).text }.firstOrNull { it.isNotBlank() } ?: return null
        if (opening.length <= TITLE_MAX_CHARS) return opening
        val cut = opening.take(TITLE_MAX_CHARS)
        val boundary = cut.lastIndexOf(' ')
        return if (boundary <= 0) cut.trim() else cut.substring(0, boundary).trim()
    }
}

/**
 * Turns the lines of a note into tasks — proposing first, creating second.
 *
 * The split is the same one [OrganizeNoteUseCase] makes and for the same reason: Abu sees every
 * line and says which are tasks. Mira deciding silently is how half his list goes missing without
 * him noticing.
 */
class TurnNoteIntoTasksUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val createTask: CreateTaskUseCase,
    private val link: LinkNoteUseCase,
) {

    /** The note's lines, marked up with a default selection. Writes nothing. */
    suspend fun candidates(noteId: Long): AppResult<List<NoteTaskCandidate>> {
        val note = when (val found = notes.byId(noteId)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        return AppResult.Success(candidatesOf(note.body))
    }

    /**
     * Creates the chosen lines as tasks and hands back what was made.
     *
     * Each one goes through [CreateTaskUseCase], so a task made from a note is validated, dated
     * and stored exactly as one Abu typed into the composer — indistinguishable afterwards, which
     * is the point. The note's client and project travel with them because the work belongs where
     * the note belongs; its tags do not, because a tag on a note describes the note.
     *
     * A failure part way through keeps the tasks that already landed, and says so by failing:
     * they are in his list where he can see them, and silently swallowing the rest would not be.
     */
    suspend fun create(noteId: Long, chosen: List<NoteTaskCandidate>): AppResult<List<Task>> {
        val wanted = chosen.filter { it.selected }.mapNotNull { it.line.trimmedOrNull() }
        if (wanted.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "No line of note $noteId was chosen")
        }
        val note = when (val found = notes.byId(noteId)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }

        val created = mutableListOf<Task>()
        for (title in wanted) {
            val made = createTask(
                title = title,
                projectId = note.projectId,
                clientId = note.clientId,
            )
            when (made) {
                is AppResult.Failure -> return made
                is AppResult.Success -> created += made.value
            }
        }

        // The link only exists on the note's side — a task carries no note id — so it can only be
        // drawn when there is one task to draw it to. Its failure is not the operation's failure:
        // the tasks are made, and telling Abu otherwise would be the one lie he cannot check.
        if (created.size == 1 && note.taskId == null) {
            link.toTask(noteId, created.single().id)
        }
        return AppResult.Success(created)
    }

    /**
     * Splits a body into lines and decides which start out ticked.
     *
     * A line Abu marked — a bullet, a dash, a box, a number — is a thing to do in any script, so
     * those are selected. When he marked nothing at all, the shape test in [looksLikeOneThingToDo]
     * decides. When he marked *something*, the lines he did not mark are the context around his
     * list rather than more of it, so they start unticked — he can still turn any of them on.
     */
    private fun candidatesOf(body: String): List<NoteTaskCandidate> {
        val lines = body.lines().map(::markerOf).filter { it.text.isNotBlank() }
        val anyMarked = lines.any { it.marked }
        return lines.map { line ->
            NoteTaskCandidate(
                line = line.text,
                selected = when {
                    line.marked -> true
                    anyMarked -> false
                    isHeading(line) -> false
                    else -> looksLikeOneThingToDo(line.text)
                },
            )
        }
    }
}

/**
 * Files text the recogniser produced into the note the handwriting belongs to.
 *
 * **This does not read handwriting, and cannot.** The platform's stylus handwriting is performed
 * by the keyboard on a live session as Abu writes; there is no call anywhere in Android that
 * takes a finished page and returns its text, and Gemini never sees a file (`docs/PRIVACY.md`).
 * So the text arrives here from the canvas that was open at the time, and this use case's whole
 * job is to put it somewhere it will still be tomorrow.
 *
 * It is keyed on the note rather than on the ink page because the canvas may hold a page that has
 * not been saved yet, and the text belongs to the note either way. A page id would make this
 * uncallable at exactly the moment Abu asks for it.
 *
 * Nothing here can ever cost Abu his writing: the page is his whatever happens to the text, and
 * every path through this ends either with text appended or with a refusal the screen explains.
 */
class ExtractNoteTextUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val update: UpdateNoteUseCase,
) {

    /**
     * @param recognised what the keyboard made of the session Abu just wrote. Anything other than
     *   actual words comes back as a failure for the screen to explain — NOT_FOUND when there was
     *   nothing to read, ENGINE_UNAVAILABLE when this phone's keyboard cannot read handwriting at
     *   all, VALIDATION when the note is in the bin. None of the three is an error on Abu's part
     *   and none of them costs him the page.
     */
    suspend operator fun invoke(noteId: Long, recognised: HandwritingResult): AppResult<Note> {
        val text = when (recognised) {
            is HandwritingResult.Recognised -> recognised.text.trimmedOrNull()
                ?: return failure(MiraErrorCode.NOT_FOUND, "Nothing was recognised for note $noteId")
            HandwritingResult.NoTextRecognised ->
                return failure(MiraErrorCode.NOT_FOUND, "Nothing was recognised for note $noteId")
            is HandwritingResult.Unavailable ->
                return failure(
                    MiraErrorCode.ENGINE_UNAVAILABLE,
                    "Handwriting could not be read: ${recognised.reason}",
                )
        }

        val note = when (val found = notes.byId(noteId)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        // `byId` is the one read that answers for a note in the bin, so this is the one place
        // that can be asked to write into one. Text filed onto a binned note is text no list but
        // the bin would ever show him again.
        if (note.deletedAt != null) {
            return failure(MiraErrorCode.VALIDATION, "Note $noteId is in the bin")
        }

        // Appended, never replacing: what he typed and what he wrote by hand are both his. The
        // blank line is what separates them, and it is decided here and nowhere else — a screen
        // joining them its own way is how the same note reads differently depending on which
        // control Abu used.
        val body = if (note.body.isBlank()) text else note.body.trimEnd() + "\n\n" + text
        return update(note.copy(body = body))
    }
}
