package com.rayluxeo.aida.ui.notes.detail

import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * Every word the note editor puts in front of Abu, in one language.
 *
 * The editor sits on top of data that is already worded — a moment from `TaskTimeFormatter`, a
 * failure from `ResponseRenderer` — so a label typed as an English literal at a call site would
 * read as an English caption over a Bengali value. Both languages are written here from the first
 * line, because retrofitting one is how the two drift apart.
 *
 * The reasons an action is unavailable are wording, not state: the view model decides *whether*
 * an action can run, and this decides how that is said. An action Abu cannot use is always given
 * a reason — a control that dims without explaining itself is one he taps twice and then stops
 * trusting.
 */
internal interface NoteDetailWording {

    val newNoteTitle: String

    val noteTitle: String

    val back: String

    val moreActions: String

    val save: String

    val cancel: String

    val set: String

    val done: String

    val dismiss: String

    /** The "no link" option in every picker, and what an empty link row says. */
    val none: String

    // --- The words themselves ------------------------------------------------------------------

    val titlePlaceholder: String

    val bodyPlaceholder: String

    // --- Sections ------------------------------------------------------------------------------

    val sectionLabels: String

    val sectionBelongs: String

    val sectionHandwriting: String

    val sectionAttachments: String

    val sectionVoice: String

    val sectionActions: String

    val sectionHistory: String

    // --- Links ---------------------------------------------------------------------------------

    val clientLabel: String

    val projectLabel: String

    val taskLabel: String

    val clientSheetTitle: String

    val projectSheetTitle: String

    val taskSheetTitle: String

    val noClients: String

    val noProjects: String

    val noTasks: String

    // --- Labels --------------------------------------------------------------------------------

    val tagsSheetTitle: String

    val tagPlaceholder: String

    val addTag: String

    fun removeTag(name: String): String

    val noTags: String

    // --- What the note carries -------------------------------------------------------------------

    fun fileSize(bytes: Long): String

    /** A page of handwriting, said in strokes because that is what survives a lost image. */
    fun inkPageLabel(strokeCount: Int, moment: String): String

    /** A page whose files were reclaimed. Said plainly, never drawn as a blank page. */
    /** The row that starts a page of handwriting. A note may have none, so this is how ink begins. */
    val addHandwriting: String

    val inkLost: String

    /**
     * Why there is no control here that turns a page of handwriting into words: the keyboard reads
     * it on the canvas as Abu writes. It is said under the pages themselves, where the question
     * occurs to him, rather than as an action that could only ever be refused.
     */
    val inkReadWhileWriting: String

    /**
     * The length of the recording, as minutes and seconds.
     *
     * The two numbers arrive unformatted because the digits are wording too — Bengali writes them
     * in Bengali numerals, beside a moment that is already written that way.
     */
    fun voiceNoteLabel(minutes: Long, seconds: Long, moment: String): String

    fun created(moment: String): String

    fun updated(moment: String): String

    // --- The six actions -------------------------------------------------------------------------

    fun actionLabel(action: NoteAction): String

    val saveFirst: String

    val restoreFirst: String

    val linkClientFirst: String

    val needsWords: String

    /** Reads as "Invoice draft — link a client first." */
    fun unavailable(label: String, reason: String): String

    // --- Organize --------------------------------------------------------------------------------

    val organizeTitle: String

    val organizeApply: String

    val organizeNothing: String

    val organizeTitleLabel: String

    val organizeTagsLabel: String

    val organizeApplied: String

    // --- Turning lines into tasks ------------------------------------------------------------------

    val tasksTitle: String

    val tasksCreate: String

    val tasksEmpty: String

    fun tasksCreated(count: Int): String

    // --- Reminder and invoice ----------------------------------------------------------------------

    val reminderTimeTitle: String

    fun reminderCreated(moment: String): String

    fun invoiceDraftCreated(number: String, client: String): String

    // --- Putting a note away, and taking it back ----------------------------------------------------

    val pin: String

    val unpin: String

    val archive: String

    val unarchive: String

    val delete: String

    val restore: String

    val inBin: String

    val noteSaved: String

    val noteArchived: String

    val noteUnarchived: String

    val noteDeleted: String

    val noteRestored: String

    // --- The two questions worth asking ---------------------------------------------------------------

    val deleteTitle: String

    val deleteMessage: String

    val discardTitle: String

    val discardMessage: String

    val discardConfirm: String

    val keepEditing: String
}

internal fun noteDetailWording(language: SpokenLanguage): NoteDetailWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliNoteDetailWording
    SpokenLanguage.ENGLISH -> EnglishNoteDetailWording
}
