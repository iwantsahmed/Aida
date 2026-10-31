package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskSort
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** The two halves of the screen. Reminders are read and cancelled here; they are created by voice. */
enum class TasksTab { TASKS, REMINDERS }

/**
 * The seven lists Abu can ask for, each one a preset over [com.rayluxeo.aida.domain.model.TaskQuery].
 *
 * A view owns exactly two decisions — which tasks the query asks for, and whether the answer is
 * one list or a list per heading — so adding a view cannot leave half of it wired. [grouping] is
 * separate from [filter] because By project and By client narrow to the same tasks as each other
 * and differ only in how the answer is cut up.
 *
 * [TasksView.ALL] is the only view that shows archived work. That is deliberate: archiving from a
 * swipe would otherwise be a one-way door, with the task gone from every list Abu can open.
 */
enum class TasksView(val filter: TaskFilter, val grouping: TaskGrouping) {
    TODAY(TaskFilter.DUE_TODAY, TaskGrouping.NONE),
    UPCOMING(TaskFilter.UPCOMING, TaskGrouping.NONE),
    OVERDUE(TaskFilter.OVERDUE, TaskGrouping.NONE),
    ALL(TaskFilter.ALL, TaskGrouping.NONE),
    COMPLETED(TaskFilter.DONE, TaskGrouping.NONE),

    /**
     * Outstanding work, cut by project and by client. Finished work is left out of both because
     * Completed already answers that question, and a project heading is most useful when it says
     * what is still owed rather than what has been settled.
     */
    BY_PROJECT(TaskFilter.OPEN, TaskGrouping.PROJECT),
    BY_CLIENT(TaskFilter.OPEN, TaskGrouping.CLIENT),
}

enum class TaskGrouping { NONE, PROJECT, CLIENT }

enum class NoticeTone { CONFIRM, PROBLEM }

/**
 * A short line about what just happened, with an undo when the action can genuinely be taken back.
 * [undoLabel] is null when it cannot — Mira never offers a button that does nothing.
 */
data class TasksNotice(
    val message: String,
    val tone: NoticeTone,
    val undoLabel: String? = null,
)

/**
 * The reminder Abu has asked to call off, held back until he says so a second time.
 *
 * Cancelling a reminder disarms the alarm and drops the row, and nothing puts either back — it is
 * the one destructive act on this screen that cannot be answered with an undo. So it is gated by
 * a question instead, and the question carries the reminder's own title so Abu is agreeing to
 * lose a named thing rather than a row he has to remember pressing.
 */
data class ReminderCancelRequest(
    val id: Long,
    val title: String,
)

/**
 * One task as the list shows it.
 *
 * Everything here is already worded in Abu's language: the view model is where the language is
 * known, so a composable never has to choose between an English literal and a Bengali one.
 */
data class TaskListItem(
    val id: Long,
    val title: String,
    /**
     * The line under the title — the due moment with the overdue word in front of it when the
     * task is late, then whatever else is true of it. Overdue is said in words as well as colour
     * so the row still reads correctly to someone who cannot tell the danger tone from the muted
     * one.
     */
    val supporting: String?,
    val isOverdue: Boolean,
    val isDone: Boolean,
    val isArchived: Boolean,
    val isHighPriority: Boolean,
    val isSelected: Boolean,
)

/**
 * One heading and the rows under it.
 *
 * A flat view is a single section with no [title], so the list has one shape whether or not it is
 * grouped and the screen does not branch twice over the same data.
 */
data class TaskSection(
    val key: String,
    val title: String?,
    val items: List<TaskListItem>,
)

/**
 * One project, client or tag Abu can narrow the list to.
 *
 * Only the name is carried. Tags are ordered by how many tasks wear them, so the count does its
 * work in the ordering rather than as a number printed beside every chip.
 */
data class TaskFacet(
    val id: Long,
    val label: String,
)

/**
 * What is currently narrowing the list, and what else it could be narrowed to.
 *
 * [appliedLabels] exists because a filter Abu cannot see is a list he thinks is broken: the screen
 * shows the applied labels above the rows rather than hiding them behind the sheet that set them.
 */
data class TaskFiltersUiState(
    val projectId: Long? = null,
    val clientId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    val priorities: Set<TaskPriority> = emptySet(),
    val projects: List<TaskFacet> = emptyList(),
    val clients: List<TaskFacet> = emptyList(),
    val tags: List<TaskFacet> = emptyList(),
    val appliedLabels: List<String> = emptyList(),
) {
    val isActive: Boolean get() = appliedLabels.isNotEmpty()
}

/** How the list is ordered. Every sort reverses, so the direction travels with it. */
data class TaskSortUiState(
    val sort: TaskSort = TaskSort.DUE_DATE,
    val ascending: Boolean = true,
)

/**
 * The bulk-selection mode.
 *
 * [isActive] is held apart from [ids] so that unticking the last row leaves Abu in selection with
 * nothing chosen, rather than dropping him out of it under his thumb.
 */
data class TaskSelectionUiState(
    val isActive: Boolean = false,
    val ids: Set<Long> = emptySet(),
    val allSelected: Boolean = false,
) {
    val count: Int get() = ids.size
}

/**
 * The sheet a left swipe opens: the things that are not completing the task.
 *
 * [canSnooze] is false for a task with no due date, because
 * [com.rayluxeo.aida.domain.usecase.SnoozeTaskUseCase] has nothing to move and refuses it. Hiding
 * the option is better than offering one that always comes back as a problem.
 */
data class TaskActionsUiState(
    val taskId: Long,
    val title: String,
    val isArchived: Boolean,
    val canSnooze: Boolean,
)

/**
 * How far a snooze moves a task.
 *
 * Each option is an exact amount of time rather than a part of the day, because "later today" at
 * eleven at night is tomorrow and Mira would have said something untrue. The domain snoozes by a
 * [Duration] from the due moment, so the option carries one rather than a target instant.
 */
enum class SnoozeOption(val amount: Duration) {
    ONE_HOUR(Duration.ofHours(1)),
    THREE_HOURS(Duration.ofHours(3)),
    ONE_DAY(Duration.ofDays(1)),
    ONE_WEEK(Duration.ofDays(7)),
}

/** The add-task sheet's contents. Raw date and time are kept beside their labels for the pickers. */
data class TaskComposerUiState(
    val title: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val dateLabel: String? = null,
    val timeLabel: String? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val isSaving: Boolean = false,
    /** Where the date and time pickers open when nothing has been chosen yet. */
    val pickerDate: LocalDate,
    val pickerTime: LocalTime,
) {
    val canSave: Boolean get() = title.isNotBlank() && !isSaving
}

data class TasksUiState(
    val isLoading: Boolean = true,
    /** Which language the screen's own labels are written in; the rows are worded to match. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val tab: TasksTab = TasksTab.TASKS,
    val view: TasksView = TasksView.TODAY,
    val query: String = "",
    val sections: List<TaskSection> = emptyList(),
    val filters: TaskFiltersUiState = TaskFiltersUiState(),
    val sort: TaskSortUiState = TaskSortUiState(),
    val selection: TaskSelectionUiState = TaskSelectionUiState(),
    val reminders: List<ReminderRow> = emptyList(),
    /**
     * Set when a place reminder has no alert behind it. Held apart from [notice] because it is a
     * standing state rather than something that just happened, and it must not time out under a
     * reminder that is still not being watched.
     */
    val placeNotice: RemindersPlaceNotice? = null,
    val composer: TaskComposerUiState? = null,
    val showReminderComposer: Boolean = false,
    val actions: TaskActionsUiState? = null,
    /** Set while the cancel question is up. Cleared by either answer, and by the row going away. */
    val confirmCancelReminder: ReminderCancelRequest? = null,
    val notice: TasksNotice? = null,
    /** A failure that stopped the list itself from loading, worded by Mira. */
    val errorMessage: String? = null,
) {
    /** Every row on screen, flattened. The bulk bar counts against this and so does select-all. */
    val visibleTasks: List<TaskListItem> get() = sections.flatMap { it.items }

    val isEmpty: Boolean get() = sections.none { it.items.isNotEmpty() }
}
