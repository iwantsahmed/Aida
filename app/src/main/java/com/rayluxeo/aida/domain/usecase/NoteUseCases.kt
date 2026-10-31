package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.InkPageRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * The rules every note verb holds to, in one place.
 *
 * Two copies of them would drift, and the one that drifted would be whichever path Abu happened
 * to use — a note captured from the widget would accept what the editor refuses.
 */
internal object NoteRules {

    /** The words as they will be stored, so every verb trims them identically. */
    fun text(title: String?, body: String): NoteText = NoteText(title.trimmedOrNull(), body.trim())

    /**
     * A note has to be *something*, and handwriting counts as something.
     *
     * A page Abu wrote with the S Pen and never typed a word on is a complete note: the writing
     * IS the note. So ink satisfies this exactly as a title or a body does, and only a note that
     * is empty in all three ways is refused — a blank row in his list cannot be told apart from
     * one whose writing was lost.
     *
     * @param hasInk whether a page of handwriting stands behind this note — or is about to. A
     *   page carries the id of the note it sits on, so at creation no page CAN exist yet; Quick
     *   Ink therefore mints the row first and attaches the page to it a moment later, and passes
     *   true to say so. Refusing that row would make handwriting startable only from a note Abu
     *   had already written words into, which is the opposite of what Quick Ink is for.
     */
    fun requireContent(text: NoteText, hasInk: Boolean): AppResult.Failure? =
        if (text.title == null && text.body.isEmpty() && !hasInk) {
            failure(MiraErrorCode.VALIDATION, "A note needs a title, a body or a page of ink")
        } else {
            null
        }

    /**
     * Tags are worn here, never minted.
     *
     * `TagRepository.ensure` is the one thing that creates a tag, so a note cannot quietly add a
     * second spelling of a label tasks already use — the two vocabularies that §7.12 forbids.
     * This is the only statement of that rule: `NoteRepositoryImpl` leaves it to the domain.
     */
    fun tags(tagIds: Collection<Long>): AppResult.Failure? =
        if (tagIds.any { it <= 0L }) {
            failure(MiraErrorCode.VALIDATION, "A tag must exist before a note can wear it")
        } else {
            null
        }
}

/** A note's words once they have been trimmed, so every verb trims them the same way. */
internal data class NoteText(val title: String?, val body: String)

/**
 * Puts [tagIds] on a saved note. Tags live in a join table rather than on the row, and
 * `NoteRepository.create` does not write them, so every verb that can change them goes through
 * the one port that does.
 */
internal suspend fun applyNoteTags(
    notes: NoteRepository,
    note: Note,
    tagIds: Collection<Long>,
): AppResult<Note> =
    if (tagIds.isEmpty()) AppResult.Success(note) else notes.setTags(note.id, tagIds.distinct())

/**
 * The one way a note comes into existence, whatever asked for it.
 *
 * Abu typing it, dictating it, tapping the widget or long-pressing the app icon all arrive here,
 * so quick capture cannot end up with rules of its own.
 */
class CreateNoteUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val clock: MiraClock,
    private val links: LinkNoteUseCase,
) {

    /**
     * Whatever this note is filed under is read before anything is written, by the same rule
     * [LinkNoteUseCase] holds: a stale id is a NOT_FOUND Mira can explain rather than a
     * foreign-key failure out of storage, which she cannot.
     *
     * @param tagIds tags that already exist; see [NoteRules.tags]. They are written by
     *   [applyNoteTags] once the row has an id, because `NoteRepository.create` writes the row
     *   and nothing else.
     */
    suspend operator fun invoke(
        title: String? = null,
        body: String = "",
        language: SpokenLanguage = SpokenLanguage.ENGLISH,
        clientId: Long? = null,
        projectId: Long? = null,
        taskId: Long? = null,
        tagIds: Collection<Long> = emptyList(),
        isPinned: Boolean = false,
        /**
         * True when the caller is about to attach a page of handwriting to this row.
         *
         * The caller then owns finishing the job: a note minted on this promise and left without
         * a page is the blank row [NoteRules.requireContent] exists to prevent. `InkCanvasViewModel`
         * takes the note back out again if the page fails to save.
         */
        willCarryInk: Boolean = false,
    ): AppResult<Note> {
        val text = NoteRules.text(title, body)
        NoteRules.requireContent(text, hasInk = willCarryInk)?.let { return it }
        NoteRules.tags(tagIds)?.let { return it }
        links.requireTargets(clientId = clientId, projectId = projectId, taskId = taskId)
            ?.let { return it }

        val now = clock.now()
        val note = Note(
            title = text.title,
            body = text.body,
            isPinned = isPinned,
            clientId = clientId,
            projectId = projectId,
            taskId = taskId,
            language = language,
            createdAt = now,
            updatedAt = now,
        )
        return notes.create(note).flatMap { saved -> applyNoteTags(notes, saved, tagIds) }
    }
}

/**
 * Applies the fields Abu can edit to the stored note.
 *
 * [edited] is a whole note because the editor holds one, but only the words are taken from it.
 * Pinning, filing, deleting, linking and the creation time are facts about what happened to the
 * note rather than fields a form may overwrite — an editor holding a stale copy would otherwise
 * un-archive a note he filed from the list while it was open.
 */
class UpdateNoteUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val inkPages: InkPageRepository,
    private val clock: MiraClock,
) {

    suspend operator fun invoke(edited: Note): AppResult<Note> {
        NoteRules.tags(edited.tags.map { it.id })?.let { return it }
        val stored = when (val found = notes.byId(edited.id)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }

        val text = NoteRules.text(edited.title, edited.body)
        // The pages are only consulted once the words are gone, so clearing the text of a
        // handwritten note is allowed while an ordinary edit never pays for the read.
        val hasInk = if (text.title == null && text.body.isEmpty()) {
            when (val found = inkPages.forNote(edited.id)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> found.value.isNotEmpty()
            }
        } else {
            false
        }
        NoteRules.requireContent(text, hasInk)?.let { return it }

        val merged = stored.copy(
            title = text.title,
            body = text.body,
            language = edited.language,
            updatedAt = clock.now(),
        )
        return notes.update(merged).flatMap { saved ->
            if (edited.tags.map { it.id }.toSet() == stored.tags.map { it.id }.toSet()) {
                AppResult.Success(saved)
            } else {
                notes.setTags(saved.id, edited.tags.map { it.id })
            }
        }
    }
}

/** Pinning, which floats a note above whichever sort the list is in. */
class PinNoteUseCase @Inject constructor(private val notes: NoteRepository) {
    suspend operator fun invoke(id: Long, pinned: Boolean): AppResult<Note> =
        notes.setPinned(id, pinned)
}

/**
 * Files a note away, which is not deleting it.
 *
 * Archiving says Abu is finished with a note; the bin says it was a mistake. They are two
 * columns on the row for that reason, and emptying the bin must never touch what he has filed.
 */
class ArchiveNoteUseCase @Inject constructor(private val notes: NoteRepository) {
    suspend operator fun invoke(id: Long): AppResult<Note> = notes.archive(id)
}

/**
 * Undoes both ways a note can be put away — out of the bin, or out of the archive.
 *
 * One verb rather than two because the word on the button is the same and Abu is not thinking
 * about which column the note sits in. A note that was archived and then deleted comes back to
 * the archive rather than to the top of his list, because that is where he left it.
 */
class RestoreNoteUseCase @Inject constructor(private val notes: NoteRepository) {

    suspend operator fun invoke(id: Long): AppResult<Note> {
        val note = when (val found = notes.byId(id)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        return when {
            note.deletedAt != null -> notes.restore(id).flatMap { notes.byId(id) }
            note.archivedAt != null -> notes.unarchive(id)
            // Already where he wants it. Idempotent on purpose: a second tap on an undo must not
            // raise a failure for a note that is already back.
            else -> AppResult.Success(note)
        }
    }
}

/** Sends a note to the bin. The row survives, so [RestoreNoteUseCase] brings back the same id. */
class DeleteNoteUseCase @Inject constructor(private val notes: NoteRepository) {
    suspend operator fun invoke(id: Long): AppResult<Unit> = notes.delete(id)
}

/**
 * Replaces the whole tag set on a note.
 *
 * An empty collection is a real instruction — Abu took the last tag off — and is not the same as
 * "leave them alone", which is simply not calling this.
 */
class SetNoteTagsUseCase @Inject constructor(private val notes: NoteRepository) {

    suspend operator fun invoke(noteId: Long, tagIds: Collection<Long>): AppResult<Note> {
        NoteRules.tags(tagIds)?.let { return it }
        return notes.setTags(noteId, tagIds.distinct())
    }
}

/**
 * Ties a note to a client, a project or a task — or cuts it loose again.
 *
 * The target is read before the link is written so a link to something that is not there comes
 * back as NOT_FOUND, which Mira can explain, rather than as a foreign-key failure out of storage,
 * which she cannot. Null is always allowed and always means unlink: the note outlives whatever
 * it was about.
 */
class LinkNoteUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val clients: ClientRepository,
    private val projects: ProjectRepository,
    private val tasks: TaskRepository,
) {

    suspend fun toClient(noteId: Long, clientId: Long?): AppResult<Note> {
        requireTargets(clientId = clientId)?.let { return it }
        return notes.setClientLink(noteId, clientId)
    }

    suspend fun toProject(noteId: Long, projectId: Long?): AppResult<Note> {
        requireTargets(projectId = projectId)?.let { return it }
        return notes.setProjectLink(noteId, projectId)
    }

    suspend fun toTask(noteId: Long, taskId: Long?): AppResult<Note> {
        requireTargets(taskId = taskId)?.let { return it }
        return notes.setTaskLink(noteId, taskId)
    }

    /**
     * Reads whichever of the three is not null, and answers with the first that is not there.
     *
     * [CreateNoteUseCase] asks the same question, because a note that arrives already carrying a
     * client id makes exactly the claim a note linked afterwards makes. One rule answers both;
     * two would differ on the day one of them was forgotten.
     */
    internal suspend fun requireTargets(
        clientId: Long? = null,
        projectId: Long? = null,
        taskId: Long? = null,
    ): AppResult.Failure? {
        if (clientId != null) {
            when (val found = clients.byId(clientId)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> Unit
            }
        }
        if (projectId != null) {
            when (val found = projects.byId(projectId)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> Unit
            }
        }
        if (taskId != null) {
            when (val found = tasks.byId(taskId)) {
                is AppResult.Failure -> return found
                is AppResult.Success -> Unit
            }
        }
        return null
    }
}
