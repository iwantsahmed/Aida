package com.rayluxeo.aida.ui.notes.detail

/** The note editor in English. Short labels, plain sentences, nothing exclaimed. */
internal object EnglishNoteDetailWording : NoteDetailWording {

    override val newNoteTitle: String = "New note"

    override val noteTitle: String = "Note"

    override val back: String = "Back"

    override val moreActions: String = "More"

    override val save: String = "Save"

    override val cancel: String = "Cancel"

    override val set: String = "Set"

    override val done: String = "Done"

    override val dismiss: String = "Dismiss"

    override val none: String = "None"

    override val titlePlaceholder: String = "Title"

    override val bodyPlaceholder: String = "Write it down"

    override val sectionLabels: String = "Labels"

    override val sectionBelongs: String = "Belongs to"

    override val sectionHandwriting: String = "Handwriting"

    override val sectionAttachments: String = "Attachments"

    override val sectionVoice: String = "Recording"

    override val sectionActions: String = "Do something with this"

    override val sectionHistory: String = "History"

    override val clientLabel: String = "Client"

    override val projectLabel: String = "Project"

    override val taskLabel: String = "Task"

    override val clientSheetTitle: String = "Client"

    override val projectSheetTitle: String = "Project"

    override val taskSheetTitle: String = "Task"

    override val noClients: String = "No clients yet."

    override val noProjects: String = "No projects yet."

    override val noTasks: String = "Nothing open to link to."

    override val tagsSheetTitle: String = "Labels"

    override val tagPlaceholder: String = "New label"

    override val addTag: String = "Add label"

    override fun removeTag(name: String): String = "Remove $name"

    override val noTags: String = "No labels yet"

    override fun fileSize(bytes: Long): String = when {
        bytes < KILOBYTE -> "$bytes B"
        bytes < MEGABYTE -> "${bytes / KILOBYTE} KB"
        else -> "${bytes / MEGABYTE} MB"
    }

    override fun inkPageLabel(strokeCount: Int, moment: String): String =
        if (strokeCount == 1) "1 stroke · $moment" else "$strokeCount strokes · $moment"

    override val addHandwriting: String = "Write a page by hand"

    override val inkLost: String = "The writing on this page is gone."

    override val inkReadWhileWriting: String =
        "Handwriting is read as you write it, on the canvas."

    override fun voiceNoteLabel(minutes: Long, seconds: Long, moment: String): String =
        "$minutes:${seconds.toString().padStart(SECONDS_DIGITS, '0')} · $moment"

    override fun created(moment: String): String = "Written $moment"

    override fun updated(moment: String): String = "Updated $moment"

    override fun actionLabel(action: NoteAction): String = when (action) {
        NoteAction.ORGANIZE -> "Organize"
        NoteAction.TURN_INTO_TASKS -> "Turn into tasks"
        NoteAction.CREATE_REMINDER -> "Create reminder"
        NoteAction.LINK_CLIENT -> "Link client"
        NoteAction.CREATE_INVOICE_DRAFT -> "Invoice draft"
        NoteAction.ASK_MIRA -> "Ask Aida"
    }

    override val saveFirst: String = "save the note first"

    override val restoreFirst: String = "the note is in the bin"

    override val linkClientFirst: String = "link a client first"

    override val needsWords: String = "there are no lines to work from"

    override fun unavailable(label: String, reason: String): String = "$label — $reason."

    override val organizeTitle: String = "Aida would change this"

    override val organizeApply: String = "Apply"

    override val organizeNothing: String = "Nothing to change — this note is already filed."

    override val organizeTitleLabel: String = "Title"

    override val organizeTagsLabel: String = "Labels"

    override val organizeApplied: String = "Note organized."

    override val tasksTitle: String = "Turn into tasks"

    override val tasksCreate: String = "Create tasks"

    override val tasksEmpty: String = "There are no lines to turn into tasks."

    override fun tasksCreated(count: Int): String =
        if (count == 1) "1 task created." else "$count tasks created."

    override val reminderTimeTitle: String = "Remind me at"

    override fun reminderCreated(moment: String): String = "Reminder set for $moment."

    override fun invoiceDraftCreated(number: String, client: String): String =
        "Draft $number raised for $client."

    override val pin: String = "Pin"

    override val unpin: String = "Unpin"

    override val archive: String = "Archive"

    override val unarchive: String = "Put back in the list"

    override val delete: String = "Delete"

    override val restore: String = "Restore"

    override val inBin: String = "This note is in the bin."

    override val noteSaved: String = "Note saved."

    override val noteArchived: String = "Note filed."

    override val noteUnarchived: String = "Note back in the list."

    override val noteDeleted: String = "Note deleted."

    override val noteRestored: String = "Note restored."

    override val deleteTitle: String = "Delete this note?"

    override val deleteMessage: String = "It goes to the bin, where you can take it back."

    override val discardTitle: String = "Leave without saving?"

    override val discardMessage: String = "What you have written here will be lost."

    override val discardConfirm: String = "Leave"

    override val keepEditing: String = "Keep writing"
}

/** Seconds are always two digits beside the minutes, as any clock face writes them. */
private const val SECONDS_DIGITS = 2

private const val KILOBYTE = 1_024L

private const val MEGABYTE = 1_024L * 1_024L
