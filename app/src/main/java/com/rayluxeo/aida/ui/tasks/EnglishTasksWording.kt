package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.domain.model.TaskSort

/** The Tasks screen in English. Short labels, plain sentences, nothing exclaimed. */
internal object EnglishTasksWording : TasksWording {

    override val screenTitle: String = "Tasks"

    override val tabLabels: List<String> = listOf("Tasks", "Reminders")

    override val addTask: String = "Add task"

    override val addFirstTask: String = "Add a task"

    override val searchPlaceholder: String = "Find a task"

    override val clearSearch: String = "Clear search"

    override val noReminders: String = "Nothing scheduled. Ask Aida to remind you about something."

    override val cancel: String = "Cancel"

    override val set: String = "Set"

    override val dismiss: String = "Dismiss"

    override val done: String = "Done"

    override val newTask: String = "New task"

    override val fullDetails: String = "Open full details"

    override val newReminder: String = "New reminder"

    override val addReminder: String = "Add reminder"

    override val reminderTitlePlaceholder: String = "What to remind you about?"

    override val taskTitlePlaceholder: String = "What needs doing?"

    override val addDate: String = "Add date"

    override val addTime: String = "Add time"

    override val clearDueDate: String = "Clear due date"

    override val priorityLabel: String = "Priority"

    override val priorityOptions: List<String> = listOf("Low", "Normal", "High")

    override val highPriority: String = "High"

    override val dueTimeTitle: String = "Due time"

    override val filtersTitle: String = "Filter"

    override val sortTitle: String = "Sort"

    override val clearFilters: String = "Clear filters"

    override val projectLabel: String = "Project"

    override val clientLabel: String = "Client"

    override val tagsLabel: String = "Tags"

    override val anyProject: String = "Any project"

    override val anyClient: String = "Any client"

    override val noProjectSection: String = "No project"

    override val noClientSection: String = "No client"

    override fun sortLabel(sort: TaskSort): String = when (sort) {
        TaskSort.DUE_DATE -> "Due date"
        TaskSort.PRIORITY -> "Priority"
        TaskSort.CREATED -> "Created"
        TaskSort.UPDATED -> "Updated"
        TaskSort.TITLE -> "Title"
    }

    override fun sortDirection(sort: TaskSort, ascending: Boolean): String = when (sort) {
        TaskSort.DUE_DATE -> if (ascending) "Soonest first" else "Latest first"
        TaskSort.PRIORITY -> if (ascending) "Lowest first" else "Highest first"
        TaskSort.CREATED -> if (ascending) "Oldest first" else "Newest first"
        TaskSort.UPDATED -> if (ascending) "Least recent first" else "Most recent first"
        TaskSort.TITLE -> if (ascending) "A to Z" else "Z to A"
    }

    override val complete: String = "Complete"

    override val reopen: String = "Reopen"

    override val archive: String = "Archive"

    override val unarchive: String = "Unarchive"

    override val duplicate: String = "Duplicate"

    override val delete: String = "Delete"

    override val moreActions: String = "More"

    override val archivedLabel: String = "Archived"

    override val repeats: String = "Repeats"

    override val snoozeTitle: String = "Move it to"

    override fun snoozeOption(option: SnoozeOption): String = when (option) {
        SnoozeOption.ONE_HOUR -> "In an hour"
        SnoozeOption.THREE_HOURS -> "In three hours"
        SnoozeOption.ONE_DAY -> "In a day"
        SnoozeOption.ONE_WEEK -> "In a week"
    }

    override fun stepsProgress(done: Int, total: Int): String = "$done of $total steps"

    override val selectTask: String = "Select"

    override val selectAll: String = "Select all"

    override val clearSelection: String = "Clear"

    override val leaveSelection: String = "Leave selection"

    override fun selectedCount(count: Int): String =
        if (count == 1) "1 selected" else "$count selected"

    override val taskDeleted: String = "Task deleted."

    override val undone: String = "Put back."

    override val taskArchived: String = "Task archived."

    override val taskUnarchived: String = "Task back in the list."

    override val taskDuplicated: String = "Copy added."

    override val reminderCancelled: String = "Reminder cancelled."

    override val undo: String = "Undo"

    override fun taskSnoozed(moment: String): String = "Moved to $moment."

    override fun tasksCompleted(count: Int): String =
        if (count == 1) "1 task completed." else "$count tasks completed."

    override fun tasksArchived(count: Int): String =
        if (count == 1) "1 task archived." else "$count tasks archived."

    override fun tasksDeleted(count: Int): String =
        if (count == 1) "1 task deleted." else "$count tasks deleted."

    override fun viewLabel(view: TasksView): String = when (view) {
        TasksView.TODAY -> "Today"
        TasksView.UPCOMING -> "Upcoming"
        TasksView.OVERDUE -> "Overdue"
        TasksView.ALL -> "All"
        TasksView.COMPLETED -> "Completed"
        TasksView.BY_PROJECT -> "By project"
        TasksView.BY_CLIENT -> "By client"
    }

    override fun emptyMessage(view: TasksView): String = when (view) {
        TasksView.TODAY -> "Nothing due today."
        TasksView.UPCOMING -> "Nothing coming up."
        TasksView.OVERDUE -> "Nothing overdue."
        TasksView.ALL -> "No tasks yet."
        TasksView.COMPLETED -> "Nothing finished yet."
        TasksView.BY_PROJECT -> "No open work on any project."
        TasksView.BY_CLIENT -> "No open work for any client."
    }

    override fun noMatches(query: String): String = "Nothing matches \"$query\"."

    override fun moreActionsFor(title: String): String = "More for $title"
}
