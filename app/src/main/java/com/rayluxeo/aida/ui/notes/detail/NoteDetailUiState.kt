package com.rayluxeo.aida.ui.notes.detail

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import java.time.LocalDate
import java.time.LocalTime

/** The sheets the editor can have open. One at a time, so opening one closes the last. */
enum class NoteDetailSheet { ACTIONS, TAGS, CLIENT, PROJECT, TASK, ORGANIZE, TASKS }

/** The three things a note can be tied to. One picker serves all of them. */
enum class NoteLinkKind { CLIENT, PROJECT, TASK }

/**
 * The six things Mira can do with a note she is already showing.
 *
 * They are one type rather than six callbacks so the row can be drawn from a list and an action
 * that is not available cannot be drawn as though it were.
 *
 * Reading a saved page of handwriting is not one of them and never will be: recognition happens on
 * the canvas while Abu writes, so the handwriting section says so in a sentence instead. An entry
 * here that no state could ever enable would be a control that exists to be refused.
 */
enum class NoteAction {
    ORGANIZE,
    TURN_INTO_TASKS,
    CREATE_REMINDER,
    LINK_CLIENT,
    CREATE_INVOICE_DRAFT,
    ASK_MIRA,
}

/**
 * One action as the row shows it.
 *
 * [reason] is filled in only when [isEnabled] is false, and then it is always shown: a control
 * that is dimmed without saying why is a control Abu taps twice and then stops trusting.
 */
data class NoteActionUi(
    val action: NoteAction,
    val label: String,
    val isEnabled: Boolean,
    val reason: String? = null,
)

/** A short line about what just happened. The editor's failures are worded by Mira, never raw. */
data class NoteDetailNotice(
    val message: String,
    val tone: MiraNoticeTone,
)

data class NoteTagUi(
    val id: Long,
    val name: String,
)

/** One option in a link picker: a client, a project or a task, reduced to what a row draws. */
data class NoteLinkOption(
    val id: Long,
    val label: String,
)

data class NoteLinkPickerUiState(
    val kind: NoteLinkKind,
    val options: List<NoteLinkOption>,
    val selectedId: Long?,
)

/**
 * One attachment of a note, already worded: the file's name and how big it is.
 *
 * The file's id is deliberately not here. Nothing on this screen opens an attachment — Mira has no
 * viewer — so an id would be a handle carried for a control that does not exist.
 */
data class NoteAttachmentUi(
    val name: String,
    val detail: String,
)

/**
 * One page of handwriting belonging to the note.
 *
 * [isLost] is a real state rather than an oversight: files are reclaimed, so a page can outlive
 * its own ink, and Abu is told the writing is gone rather than shown a blank page that looks like
 * he never wrote anything.
 */
data class InkPageUi(
    val id: Long,
    val label: String,
    val isLost: Boolean,
)

/** The recording a note was written up from, when it came from one. */
data class NoteVoiceUi(
    val label: String,
)

/**
 * What Mira proposes doing to the note, part by part, before any of it is written.
 *
 * Every part carries its own tick because a proposal is not all-or-nothing: the title may be
 * right and the client wrong, and forcing one answer for all four is how a wrong link gets
 * accepted along with three right ones.
 */
data class NoteOrganizeUiState(
    val title: String? = null,
    val acceptTitle: Boolean = true,
    val tags: List<NoteOrganizeTagUi> = emptyList(),
    val client: NoteLinkOption? = null,
    val acceptClient: Boolean = true,
    val project: NoteLinkOption? = null,
    val acceptProject: Boolean = true,
    val isApplying: Boolean = false,
) {
    val canApply: Boolean
        get() = !isApplying &&
            ((title != null && acceptTitle) ||
                tags.any { it.isAccepted } ||
                (client != null && acceptClient) ||
                (project != null && acceptProject))
}

data class NoteOrganizeTagUi(
    val id: Long,
    val name: String,
    val isAccepted: Boolean,
)

/** One line of the note, offered as a task. Every line is shown; Abu decides which are tasks. */
data class NoteTaskCandidateUi(
    val line: String,
    val isSelected: Boolean,
)

data class NoteTasksUiState(
    val candidates: List<NoteTaskCandidateUi> = emptyList(),
    val isCreating: Boolean = false,
) {
    val canCreate: Boolean get() = !isCreating && candidates.any { it.isSelected }
}

data class NoteDetailUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = false,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val title: String = "",
    val body: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    /** In the bin. The editor is read-only until Abu takes it back out. */
    val isDeleted: Boolean = false,
    val isSaving: Boolean = false,
    /** True once the words differ from what is stored, which is what leaving has to ask about. */
    val isDirty: Boolean = false,
    val tags: List<NoteTagUi> = emptyList(),
    val tagSuggestions: List<NoteTagUi> = emptyList(),
    val clientLabel: String? = null,
    val projectLabel: String? = null,
    val taskLabel: String? = null,
    val attachments: List<NoteAttachmentUi> = emptyList(),
    val inkPages: List<InkPageUi> = emptyList(),
    val voiceNote: NoteVoiceUi? = null,
    val createdLabel: String? = null,
    val updatedLabel: String? = null,
    val actions: List<NoteActionUi> = emptyList(),
    val sheet: NoteDetailSheet? = null,
    val linkPicker: NoteLinkPickerUiState? = null,
    val organize: NoteOrganizeUiState? = null,
    val tasks: NoteTasksUiState? = null,
    /** Where the reminder pickers open when Abu has chosen nothing yet. */
    val reminderDate: LocalDate? = null,
    val reminderTime: LocalTime? = null,
    val notice: NoteDetailNotice? = null,
    /** Set when the note itself could not be read, which is the one failure that owns the screen. */
    val errorMessage: String? = null,
    /** Asked before leaving with unsaved words, and never asked when there are none. */
    val isConfirmingDiscard: Boolean = false,
    val isConfirmingDelete: Boolean = false,
    /** The screen is done with itself: the caller pops the back stack. */
    val isFinished: Boolean = false,
) {
    /** Nothing to save while it is in the bin, and nothing to save when nothing changed. */
    val canSave: Boolean get() = isDirty && !isSaving && !isDeleted

    /**
     * Whether the save control is on the page at all.
     *
     * The editor is meant to read as a sheet of paper, and a full-width champagne bar sitting dim
     * across it from the moment a note opens is both the loudest thing on that sheet and a button
     * that does nothing (AGENTS.md §12). It arrives with the first keystroke and leaves once the
     * words are stored, so its going is the confirmation and no line of prose has to say "saved".
     *
     * It stays, dimmed, through the write itself, because a control that vanished the instant it
     * was pressed would leave Abu unsure whether the press landed. This is the same rule the
     * handwriting canvas follows — see `InkCanvasUiState.isSaveOffered`.
     */
    val isSaveOffered: Boolean get() = canSave || isSaving

    val canEdit: Boolean get() = !isDeleted && !isSaving
}
