package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.TaskSort

/**
 * Every word the Tasks screen puts in front of Abu, in one language.
 *
 * The screen wrote its own labels as English literals while the rows between them came back from
 * [TaskTimeFormatter] in Bengali, so with বাংলা selected a filter chip said "Overdue" above a row
 * that said "গতকাল, সকাল ৯টা". A label that sits beside worded data has to be chosen where the
 * language is known, not typed at the call site.
 *
 * English and Bengali are separate implementations for the same reason the phrasebooks under
 * `ui/response` are: either can be reworded without reading the other. The overdue word itself
 * lives in [TaskTimeFormatter] instead of here, beside the moment it is glued to, because Home
 * shows the same line and must not word it a second way.
 *
 * Anything counted is written with the digits of the language it sits in — Bengali numerals in a
 * Bengali sentence — so the count functions take the number rather than the caller formatting it.
 */
internal interface TasksWording {

    val screenTitle: String

    /** The two tabs, in [TasksTab] order so the selected index is the ordinal. */
    val tabLabels: List<String>

    /** The control that opens the composer, and the composer's own save action. */
    val addTask: String

    /** The empty list's way forward, which reads as an invitation rather than as a command. */
    val addFirstTask: String

    val searchPlaceholder: String

    val clearSearch: String

    val noReminders: String

    val cancel: String

    val set: String

    val dismiss: String

    val done: String

    val newTask: String

    val fullDetails: String

    val newReminder: String

    val addReminder: String

    val reminderTitlePlaceholder: String

    val taskTitlePlaceholder: String

    val addDate: String

    val addTime: String

    val clearDueDate: String

    val priorityLabel: String

    /** In [com.rayluxeo.aida.domain.model.TaskPriority] order, so the selected index is the ordinal. */
    val priorityOptions: List<String>

    /** Shown on a row of its own, so it says "high priority" in one word rather than repeating it. */
    val highPriority: String

    /** The time picker's title. The date picker carries Material's own. */
    val dueTimeTitle: String

    // --- Filtering and sorting -------------------------------------------------------------

    val filtersTitle: String

    val sortTitle: String

    val clearFilters: String

    val projectLabel: String

    val clientLabel: String

    val tagsLabel: String

    /** The "no narrowing" option in the project and client pickers. */
    val anyProject: String

    val anyClient: String

    /** The heading for tasks that belong to no project, or to no client. */
    val noProjectSection: String

    val noClientSection: String

    fun sortLabel(sort: TaskSort): String

    /** Reads as a direction, not as a letter: "Soonest first" beats an arrow nobody can decode. */
    fun sortDirection(sort: TaskSort, ascending: Boolean): String

    // --- Acting on a task ------------------------------------------------------------------

    val complete: String

    val reopen: String

    val archive: String

    val unarchive: String

    val duplicate: String

    val delete: String

    /** The left swipe does not act on its own; it says what it opens. */
    val moreActions: String

    /** Marks a row in the All view that is out of the way rather than outstanding. */
    val archivedLabel: String

    /** One word on a row that comes back, rather than a rule Mira would have to read out here. */
    val repeats: String

    val snoozeTitle: String

    fun snoozeOption(option: SnoozeOption): String

    fun stepsProgress(done: Int, total: Int): String

    // --- Bulk selection --------------------------------------------------------------------

    /** What a long press does, said out loud for TalkBack, which cannot see the gesture. */
    val selectTask: String

    val selectAll: String

    val clearSelection: String

    /** Leaves selection without doing anything to what was selected. */
    val leaveSelection: String

    fun selectedCount(count: Int): String

    // --- What Mira says afterwards ---------------------------------------------------------

    /**
     * MiraResponse has no "removed", "restored", "archived" or "snoozed" variant — nothing in the
     * command vocabulary reaches those — so these lines are worded here rather than by inventing
     * domain responses that no engine can produce.
     */
    val taskDeleted: String

    /**
     * One line for every undo. "Task restored" would be wrong for an un-archive and wrong again
     * for a re-opened task, and three near-identical sentences would drift apart.
     */
    val undone: String

    val taskArchived: String

    val taskUnarchived: String

    val taskDuplicated: String

    val reminderCancelled: String

    val undo: String

    /** Names the moment the task moved to, so a snooze is never a silent change of date. */
    fun taskSnoozed(moment: String): String

    fun tasksCompleted(count: Int): String

    fun tasksArchived(count: Int): String

    fun tasksDeleted(count: Int): String

    // --- Lists that have nothing in them ---------------------------------------------------

    fun viewLabel(view: TasksView): String

    fun emptyMessage(view: TasksView): String

    fun noMatches(query: String): String

    /** The same for the control that opens a row's actions, which a swipe cannot offer TalkBack. */
    fun moreActionsFor(title: String): String
}

internal fun tasksWording(language: SpokenLanguage): TasksWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliTasksWording
    SpokenLanguage.ENGLISH -> EnglishTasksWording
}
