package com.rayluxeo.aida.ui.tasks.detail

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskStatus
import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.LocalDate
import java.time.LocalTime

/**
 * The optional parts of a task, each of which stays out of sight until it holds something.
 *
 * A task carries eighteen fields and Abu fills in three of them most days. Showing all eighteen
 * empty would turn a two-second edit into a scroll, so a field appears once it has a value or
 * once he asks for it by name — see [TaskDetailUiState.addableFields].
 *
 * Attachments are deliberately absent: there is nothing to reveal, because a linked file can only
 * arrive from elsewhere. They show when the task has them and stay hidden when it does not.
 */
enum class TaskDetailField {
    DESCRIPTION,
    DUE,
    REMINDER,
    RECURRENCE,
    PROJECT,
    CLIENT,
    TAGS,
    ESTIMATE,
    SUBTASKS,
    NOTES,
}

/** Which modal the screen has open. One value rather than five booleans that can disagree. */
enum class TaskDetailSheet { NONE, RECURRENCE, PROJECT, CLIENT, TAGS, ACTIONS }

/**
 * How far ahead of the due moment Mira speaks up.
 *
 * A reminder is stored as an offset from the due moment rather than as a wall-clock time of its
 * own, which is what keeps the two in step: moving a task to Friday moves its reminder with it
 * instead of leaving an alarm pointing at a day that no longer means anything.
 */
enum class ReminderLead(val minutesBefore: Long) {
    AT_TIME(0),
    TEN_MINUTES(10),
    ONE_HOUR(60),
    ONE_DAY(24 * 60),
}

/** A short line about what just happened. The tone only reinforces wording that already says it. */
data class TaskDetailNotice(val message: String, val tone: MiraNoticeTone)

/** One step of the checklist, as the editor holds it before it is saved. */
data class SubtaskRow(
    val id: Long,
    val title: String,
    val isDone: Boolean,
)

/**
 * A file already linked to the task.
 *
 * Read-only on purpose: Mira has no file picker and no `FileProvider` yet, so the editor shows
 * what is linked and lets Abu unlink it rather than offering an add or an open control that could
 * not do anything (`AGENTS.md` §12).
 */
data class AttachmentRow(
    val id: Long,
    val name: String,
    val detail: String,
)

/** One line of a project or client picker, already worded. */
data class PickerOption(
    val id: Long,
    val label: String,
    val supporting: String? = null,
)

/**
 * Everything the task editor draws, already worded in Abu's language.
 *
 * The screen reads nothing but this: every date, duration and repeat rule arrives as a string
 * chosen where the language was known, and the raw values sit beside their labels only so the
 * pickers can open where Abu left them.
 */
data class TaskDetailUiState(
    val isLoading: Boolean = true,
    /** True while the task has never been saved, which changes the title and the verbs offered. */
    val isNew: Boolean = true,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    /** Derived from the stored task, never edited here. The screen offers the verbs instead. */
    val status: TaskStatus = TaskStatus.OPEN,
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val priority: TaskPriority = TaskPriority.NORMAL,
    val dueDate: LocalDate? = null,
    /** Null means an all-day task: `Task.dueAllDay` is written from exactly this. */
    val dueTime: LocalTime? = null,
    val dueDateLabel: String? = null,
    val dueTimeLabel: String? = null,
    /** Where the date and time pickers open before anything has been chosen. */
    val pickerDate: LocalDate,
    val pickerTime: LocalTime,
    val reminderLead: ReminderLead? = null,
    /**
     * What the reminder row reads. Null when there is none; the exact moment rather than a lead
     * when Mira set one by voice at an offset the chips do not carry.
     */
    val reminderLabel: String? = null,
    val recurrence: RecurrenceRule? = null,
    val recurrenceLabel: String? = null,
    /** The next few dates the rule lands on, so Abu can see he set up what he meant. */
    val recurrencePreview: List<String> = emptyList(),
    val projectId: Long? = null,
    val projectName: String? = null,
    val clientId: Long? = null,
    val clientName: String? = null,
    val tags: List<String> = emptyList(),
    /** Tags already in use elsewhere, offered so one name does not become three spellings. */
    val tagSuggestions: List<String> = emptyList(),
    val estimatedMinutes: Int? = null,
    val estimateLabel: String? = null,
    val subtasks: List<SubtaskRow> = emptyList(),
    val attachments: List<AttachmentRow> = emptyList(),
    val projectOptions: List<PickerOption> = emptyList(),
    val clientOptions: List<PickerOption> = emptyList(),
    /** What is on screen, whether because it has a value or because Abu asked for it. */
    val visibleFields: Set<TaskDetailField> = emptySet(),
    /** What is not, offered as "add" chips in the order they are listed. */
    val addableFields: List<TaskDetailField> = emptyList(),
    val createdLabel: String? = null,
    val updatedLabel: String? = null,
    val completedLabel: String? = null,
    val sheet: TaskDetailSheet = TaskDetailSheet.NONE,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val confirmDiscard: Boolean = false,
    val confirmDelete: Boolean = false,
    val notice: TaskDetailNotice? = null,
    /** A failure that stopped the task itself from loading, worded by Mira. */
    val errorMessage: String? = null,
    /** Set once the editor is done with — saved, or deleted — so the screen leaves. */
    val isFinished: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank() && !isSaving && (isNew || isDirty)

    /** A repeat and a reminder both hang off the due moment, so neither is offered without one. */
    val hasDueDate: Boolean get() = dueDate != null
}
