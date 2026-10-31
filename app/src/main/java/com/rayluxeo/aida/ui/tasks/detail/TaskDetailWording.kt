package com.rayluxeo.aida.ui.tasks.detail

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.TaskStatus
import com.rayluxeo.aida.domain.task.RecurrenceRule
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.time.DayOfWeek

/**
 * Every word the task editor puts in front of Abu, in one language.
 *
 * The editor sits on top of data that is already worded — a due date from `TaskTimeFormatter`, a
 * failure from `ResponseRenderer` — so a label typed as an English literal at a call site would
 * read as an English caption over a Bengali value. Both languages are written here from the first
 * line for the same reason `TasksWording` was: retrofitting one is how the two drift apart.
 *
 * English and Bengali are separate implementations rather than one class with `when` branches, so
 * either can be reworded without reading the other.
 */
internal interface TaskDetailWording {

    val newTaskTitle: String

    val taskTitle: String

    val back: String

    val moreActions: String

    val save: String

    val cancel: String

    val set: String

    val done: String

    val none: String

    // Sections.

    val sectionWhen: String

    val sectionBelongs: String

    val sectionDetail: String

    val sectionHistory: String

    // The three text fields.

    val titlePlaceholder: String

    val descriptionPlaceholder: String

    val notesPlaceholder: String

    // Priority.

    val priorityLabel: String

    /** In [com.rayluxeo.aida.domain.model.TaskPriority] order, so the selected index is the ordinal. */
    val priorityOptions: List<String>

    // The due moment.

    val addDate: String

    val addTime: String

    /** What a date with no time is called, which is the whole point of `Task.dueAllDay`. */
    val allDay: String

    val clearDueDate: String

    val clearDueTime: String

    val dueTimeTitle: String

    // The reminder.

    val reminderNone: String

    fun reminderLeadLabel(lead: ReminderLead): String

    /** Said when the task saved but its reminder would have been due in the past. */
    val reminderInThePast: String

    // The repeat.

    val repeatNever: String

    val repeatPreviewTitle: String

    val repeatEveryLabel: String

    val repeatEndsLabel: String

    val repeatEndsNever: String

    val repeatEndsOn: String

    val repeatFewer: String

    val repeatMore: String

    fun repeatKindLabel(kind: RecurrenceKind): String

    fun repeatLabel(rule: RecurrenceRule, untilLabel: String?): String

    fun weekdayShort(day: DayOfWeek): String

    /** A bare number, in the digits the language is written with. */
    fun number(value: Int): String

    // Where the task belongs.

    val projectSheetTitle: String

    val clientSheetTitle: String

    val noProjects: String

    val noClients: String

    val tagsSheetTitle: String

    val tagPlaceholder: String

    val addTag: String

    fun removeTag(name: String): String

    // The detail.

    fun estimateLabel(minutes: Int): String

    val subtaskPlaceholder: String

    val addSubtask: String

    fun subtaskProgress(done: Int, total: Int): String

    fun moveSubtaskUp(title: String): String

    fun moveSubtaskDown(title: String): String

    fun removeSubtask(title: String): String

    /** Attachments are not a [TaskDetailField]: there is no add control, so nothing to reveal. */
    val attachmentsLabel: String

    fun unlinkAttachment(name: String): String

    fun fileSize(bytes: Long): String

    // The three timestamps, shown and never edited.

    val createdLabel: String

    val updatedLabel: String

    val completedLabel: String

    // The verbs. Status follows from these; it is never picked from a list.

    val actionsTitle: String

    val complete: String

    val reopen: String

    val archive: String

    val unarchive: String

    val delete: String

    fun statusLabel(status: TaskStatus): String

    // Decisions that cannot be taken back without asking.

    val discardTitle: String

    val discardMessage: String

    val discardConfirm: String

    val keepEditing: String

    val deleteTitle: String

    val deleteMessage: String

    // What Mira says once something has happened.

    val savedNotice: String

    val completedNotice: String

    val reopenedNotice: String

    val archivedNotice: String

    val unarchivedNotice: String

    val deletedNotice: String

    val dismiss: String

    /** The name of a field, used as its row label and inside [addField]. */
    fun fieldName(field: TaskDetailField): String

    /** The chip that reveals an empty field. */
    fun addField(field: TaskDetailField): String

    /** What TalkBack reads for the control that empties a field. */
    fun clearField(field: TaskDetailField): String
}

internal fun taskDetailWording(language: SpokenLanguage): TaskDetailWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliTaskDetailWording
    SpokenLanguage.ENGLISH -> EnglishTaskDetailWording
}

/** The task editor in English. Short labels, plain sentences, nothing exclaimed. */
internal object EnglishTaskDetailWording : TaskDetailWording {

    override val newTaskTitle: String = "New task"

    override val taskTitle: String = "Task"

    override val back: String = "Back"

    override val moreActions: String = "More"

    override val save: String = "Save"

    override val cancel: String = "Cancel"

    override val set: String = "Set"

    override val done: String = "Done"

    override val none: String = "None"

    override val sectionWhen: String = "When"

    override val sectionBelongs: String = "Where it belongs"

    override val sectionDetail: String = "Detail"

    override val sectionHistory: String = "History"

    override val titlePlaceholder: String = "What needs doing?"

    override val descriptionPlaceholder: String = "What the task is"

    override val notesPlaceholder: String = "Anything worth remembering"

    override val priorityLabel: String = "Priority"

    override val priorityOptions: List<String> = listOf("Low", "Normal", "High")

    override val addDate: String = "Add date"

    override val addTime: String = "Add time"

    override val allDay: String = "All day"

    override val clearDueDate: String = "Clear due date"

    override val clearDueTime: String = "Clear due time"

    override val dueTimeTitle: String = "Due time"

    override val reminderNone: String = "No reminder"

    override fun reminderLeadLabel(lead: ReminderLead): String = when (lead) {
        ReminderLead.AT_TIME -> "At the time"
        ReminderLead.TEN_MINUTES -> "10 minutes before"
        ReminderLead.ONE_HOUR -> "1 hour before"
        ReminderLead.ONE_DAY -> "1 day before"
    }

    override val reminderInThePast: String = "Task saved. The reminder would have been due already, so none was set."

    override val repeatNever: String = "Does not repeat"

    override val repeatPreviewTitle: String = "Next"

    override val repeatEveryLabel: String = "Every"

    override val repeatEndsLabel: String = "Ends"

    override val repeatEndsNever: String = "Never"

    override val repeatEndsOn: String = "On a date"

    override val repeatFewer: String = "Fewer"

    override val repeatMore: String = "More"

    override fun repeatKindLabel(kind: RecurrenceKind): String = when (kind) {
        RecurrenceKind.NONE -> "Never"
        RecurrenceKind.DAILY -> "Daily"
        RecurrenceKind.WEEKLY -> "Weekly"
        RecurrenceKind.WEEKDAYS -> "Weekdays"
        RecurrenceKind.MONTHLY -> "Monthly"
        RecurrenceKind.YEARLY -> "Yearly"
        RecurrenceKind.EVERY_N_DAYS -> "Every few days"
    }

    override fun repeatLabel(rule: RecurrenceRule, untilLabel: String?): String {
        val body = when (rule) {
            is RecurrenceRule.Daily ->
                if (rule.interval == 1) "Every day" else "Every ${rule.interval} days"
            is RecurrenceRule.Weekly -> weeklyLabel(rule)
            is RecurrenceRule.Weekdays -> "Every weekday"
            is RecurrenceRule.Monthly -> monthlyLabel(rule)
            is RecurrenceRule.Yearly ->
                if (rule.interval == 1) "Every year" else "Every ${rule.interval} years"
            is RecurrenceRule.EveryNDays ->
                if (rule.interval == 1) "Every day" else "Every ${rule.interval} days"
        }
        return if (untilLabel == null) body else "$body, until $untilLabel"
    }

    override fun number(value: Int): String = value.toString()

    override fun weekdayShort(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

    override val projectSheetTitle: String = "Project"

    override val clientSheetTitle: String = "Client"

    override val noProjects: String = "No projects yet."

    override val noClients: String = "No clients yet."

    override val tagsSheetTitle: String = "Tags"

    override val tagPlaceholder: String = "New tag"

    override val addTag: String = "Add tag"

    override fun removeTag(name: String): String = "Remove $name"

    override fun estimateLabel(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "$minutes min"
            rest == 0 -> "$hours hr"
            else -> "$hours hr $rest min"
        }
    }

    override val subtaskPlaceholder: String = "Add a step"

    override val addSubtask: String = "Add step"

    override fun subtaskProgress(done: Int, total: Int): String = "$done of $total done"

    override fun moveSubtaskUp(title: String): String = "Move $title up"

    override fun moveSubtaskDown(title: String): String = "Move $title down"

    override fun removeSubtask(title: String): String = "Remove $title"

    override val attachmentsLabel: String = "Attachments"

    override fun unlinkAttachment(name: String): String = "Unlink $name"

    override fun fileSize(bytes: Long): String = when {
        bytes < KILOBYTE -> "$bytes B"
        bytes < MEGABYTE -> "${bytes / KILOBYTE} KB"
        else -> "${bytes / MEGABYTE} MB"
    }

    override val createdLabel: String = "Created"

    override val updatedLabel: String = "Updated"

    override val completedLabel: String = "Completed"

    override val actionsTitle: String = "Task"

    override val complete: String = "Mark complete"

    override val reopen: String = "Reopen"

    override val archive: String = "Archive"

    override val unarchive: String = "Move out of the archive"

    override val delete: String = "Delete"

    override fun statusLabel(status: TaskStatus): String = when (status) {
        TaskStatus.OPEN -> "Open"
        TaskStatus.DONE -> "Done"
        TaskStatus.ARCHIVED -> "Archived"
        TaskStatus.DELETED -> "Deleted"
    }

    override val discardTitle: String = "Leave without saving?"

    override val discardMessage: String = "The changes on this screen have not been saved."

    override val discardConfirm: String = "Discard"

    override val keepEditing: String = "Keep editing"

    override val deleteTitle: String = "Delete this task?"

    override val deleteMessage: String = "It goes to the bin and can be restored from the task list."

    override val savedNotice: String = "Saved."

    override val completedNotice: String = "Marked complete."

    override val reopenedNotice: String = "Reopened."

    override val archivedNotice: String = "Archived."

    override val unarchivedNotice: String = "Moved out of the archive."

    override val deletedNotice: String = "Deleted."

    override val dismiss: String = "Dismiss"

    override fun fieldName(field: TaskDetailField): String = when (field) {
        TaskDetailField.DESCRIPTION -> "Description"
        TaskDetailField.DUE -> "Due date"
        TaskDetailField.REMINDER -> "Reminder"
        TaskDetailField.RECURRENCE -> "Repeat"
        TaskDetailField.PROJECT -> "Project"
        TaskDetailField.CLIENT -> "Client"
        TaskDetailField.TAGS -> "Tags"
        TaskDetailField.ESTIMATE -> "Estimate"
        TaskDetailField.SUBTASKS -> "Steps"
        TaskDetailField.NOTES -> "Notes"
    }

    override fun addField(field: TaskDetailField): String = "Add ${fieldName(field).lowercase()}"

    override fun clearField(field: TaskDetailField): String = "Clear ${fieldName(field).lowercase()}"

    private fun weeklyLabel(rule: RecurrenceRule.Weekly): String {
        val every = if (rule.interval == 1) "Every week" else "Every ${rule.interval} weeks"
        if (rule.days.isEmpty()) return every
        val days = DayOfWeek.entries.filter { it in rule.days }.joinToString(", ") { weekdayShort(it) }
        return "$every on $days"
    }

    private fun monthlyLabel(rule: RecurrenceRule.Monthly): String {
        val every = if (rule.interval == 1) "Every month" else "Every ${rule.interval} months"
        val day = rule.dayOfMonth ?: return every
        return "$every on the ${ordinal(day)}"
    }

    /** 1st, 2nd, 3rd, 4th — and 11th to 13th, which are the ones a naive rule gets wrong. */
    private fun ordinal(day: Int): String {
        val suffix = when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }
}

/**
 * The task editor in Bengali. Bengali script and Bengali numerals throughout — a screen that mixes
 * an English label into a Bengali form is harder to read than either language on its own.
 */
internal object BengaliTaskDetailWording : TaskDetailWording {

    override val newTaskTitle: String = "নতুন কাজ"

    override val taskTitle: String = "কাজ"

    override val back: String = "ফিরে যান"

    override val moreActions: String = "আরও"

    override val save: String = "সংরক্ষণ করুন"

    override val cancel: String = "বাতিল"

    override val set: String = "ঠিক আছে"

    override val done: String = "হয়েছে"

    override val none: String = "কিছু নয়"

    override val sectionWhen: String = "কখন"

    override val sectionBelongs: String = "কোথায় পড়ে"

    override val sectionDetail: String = "বিস্তারিত"

    override val sectionHistory: String = "ইতিহাস"

    override val titlePlaceholder: String = "কী করতে হবে?"

    override val descriptionPlaceholder: String = "কাজটি কী"

    override val notesPlaceholder: String = "মনে রাখার মতো কিছু"

    override val priorityLabel: String = "গুরুত্ব"

    override val priorityOptions: List<String> = listOf("কম", "স্বাভাবিক", "জরুরি")

    override val addDate: String = "তারিখ দিন"

    override val addTime: String = "সময় দিন"

    override val allDay: String = "সারাদিন"

    override val clearDueDate: String = "তারিখ মুছুন"

    override val clearDueTime: String = "সময় মুছুন"

    override val dueTimeTitle: String = "কাজের সময়"

    override val reminderNone: String = "রিমাইন্ডার নেই"

    override fun reminderLeadLabel(lead: ReminderLead): String = when (lead) {
        ReminderLead.AT_TIME -> "ঠিক সময়ে"
        ReminderLead.TEN_MINUTES -> "১০ মিনিট আগে"
        ReminderLead.ONE_HOUR -> "১ ঘণ্টা আগে"
        ReminderLead.ONE_DAY -> "১ দিন আগে"
    }

    override val reminderInThePast: String = "কাজটি সংরক্ষণ করেছি। রিমাইন্ডারের সময় পেরিয়ে গেছে, তাই সেট করিনি।"

    override val repeatNever: String = "পুনরাবৃত্তি নেই"

    override val repeatPreviewTitle: String = "পরবর্তী"

    override val repeatEveryLabel: String = "প্রতি"

    override val repeatEndsLabel: String = "শেষ"

    override val repeatEndsNever: String = "কখনও না"

    override val repeatEndsOn: String = "একটি তারিখে"

    override val repeatFewer: String = "কমান"

    override val repeatMore: String = "বাড়ান"

    override fun repeatKindLabel(kind: RecurrenceKind): String = when (kind) {
        RecurrenceKind.NONE -> "কখনও না"
        RecurrenceKind.DAILY -> "প্রতিদিন"
        RecurrenceKind.WEEKLY -> "প্রতি সপ্তাহে"
        RecurrenceKind.WEEKDAYS -> "কর্মদিবসে"
        RecurrenceKind.MONTHLY -> "প্রতি মাসে"
        RecurrenceKind.YEARLY -> "প্রতি বছরে"
        RecurrenceKind.EVERY_N_DAYS -> "কয়েক দিন পরপর"
    }

    override fun repeatLabel(rule: RecurrenceRule, untilLabel: String?): String {
        val body = when (rule) {
            is RecurrenceRule.Daily ->
                if (rule.interval == 1) "প্রতিদিন" else "প্রতি ${digits(rule.interval)} দিনে"
            is RecurrenceRule.Weekly -> weeklyLabel(rule)
            is RecurrenceRule.Weekdays -> "প্রতি কর্মদিবসে"
            is RecurrenceRule.Monthly -> monthlyLabel(rule)
            is RecurrenceRule.Yearly ->
                if (rule.interval == 1) "প্রতি বছরে" else "প্রতি ${digits(rule.interval)} বছরে"
            is RecurrenceRule.EveryNDays ->
                if (rule.interval == 1) "প্রতিদিন" else "প্রতি ${digits(rule.interval)} দিনে"
        }
        return if (untilLabel == null) body else "$body, $untilLabel পর্যন্ত"
    }

    override fun number(value: Int): String = digits(value)

    override fun weekdayShort(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "সোম"
        DayOfWeek.TUESDAY -> "মঙ্গল"
        DayOfWeek.WEDNESDAY -> "বুধ"
        DayOfWeek.THURSDAY -> "বৃহস্পতি"
        DayOfWeek.FRIDAY -> "শুক্র"
        DayOfWeek.SATURDAY -> "শনি"
        DayOfWeek.SUNDAY -> "রবি"
    }

    override val projectSheetTitle: String = "প্রজেক্ট"

    override val clientSheetTitle: String = "ক্লায়েন্ট"

    override val noProjects: String = "এখনও কোনো প্রজেক্ট নেই।"

    override val noClients: String = "এখনও কোনো ক্লায়েন্ট নেই।"

    override val tagsSheetTitle: String = "ট্যাগ"

    override val tagPlaceholder: String = "নতুন ট্যাগ"

    override val addTag: String = "ট্যাগ যোগ করুন"

    override fun removeTag(name: String): String = "$name সরান"

    override fun estimateLabel(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "${digits(minutes)} মিনিট"
            rest == 0 -> "${digits(hours)} ঘণ্টা"
            else -> "${digits(hours)} ঘণ্টা ${digits(rest)} মিনিট"
        }
    }

    override val subtaskPlaceholder: String = "একটি ধাপ যোগ করুন"

    override val addSubtask: String = "ধাপ যোগ করুন"

    override fun subtaskProgress(done: Int, total: Int): String =
        "${digits(total)}টির মধ্যে ${digits(done)}টি হয়েছে"

    override fun moveSubtaskUp(title: String): String = "$title উপরে নিন"

    override fun moveSubtaskDown(title: String): String = "$title নিচে নিন"

    override fun removeSubtask(title: String): String = "$title সরান"

    override val attachmentsLabel: String = "সংযুক্তি"

    override fun unlinkAttachment(name: String): String = "$name আলাদা করুন"

    override fun fileSize(bytes: Long): String = when {
        bytes < KILOBYTE -> "${digits(bytes)} বাইট"
        bytes < MEGABYTE -> "${digits(bytes / KILOBYTE)} কেবি"
        else -> "${digits(bytes / MEGABYTE)} এমবি"
    }

    override val createdLabel: String = "তৈরি হয়েছে"

    override val updatedLabel: String = "সর্বশেষ বদল"

    override val completedLabel: String = "সম্পন্ন হয়েছে"

    override val actionsTitle: String = "কাজ"

    override val complete: String = "সম্পন্ন চিহ্নিত করুন"

    override val reopen: String = "আবার চালু করুন"

    override val archive: String = "আর্কাইভ করুন"

    override val unarchive: String = "আর্কাইভ থেকে ফেরান"

    override val delete: String = "মুছুন"

    override fun statusLabel(status: TaskStatus): String = when (status) {
        TaskStatus.OPEN -> "বাকি"
        TaskStatus.DONE -> "সম্পন্ন"
        TaskStatus.ARCHIVED -> "আর্কাইভ"
        TaskStatus.DELETED -> "মুছে ফেলা"
    }

    override val discardTitle: String = "সংরক্ষণ না করে চলে যাবেন?"

    override val discardMessage: String = "এই স্ক্রিনের পরিবর্তনগুলো সংরক্ষণ করা হয়নি।"

    override val discardConfirm: String = "বাদ দিন"

    override val keepEditing: String = "সম্পাদনা চালিয়ে যান"

    override val deleteTitle: String = "কাজটি মুছে ফেলবেন?"

    override val deleteMessage: String = "এটি বিনে যাবে, কাজের তালিকা থেকে ফিরিয়ে আনা যাবে।"

    override val savedNotice: String = "সংরক্ষণ করেছি।"

    override val completedNotice: String = "সম্পন্ন চিহ্নিত করেছি।"

    override val reopenedNotice: String = "আবার চালু করেছি।"

    override val archivedNotice: String = "আর্কাইভ করেছি।"

    override val unarchivedNotice: String = "আর্কাইভ থেকে ফিরিয়েছি।"

    override val deletedNotice: String = "মুছে দিয়েছি।"

    override val dismiss: String = "বন্ধ করুন"

    override fun fieldName(field: TaskDetailField): String = when (field) {
        TaskDetailField.DESCRIPTION -> "বিবরণ"
        TaskDetailField.DUE -> "সময়সীমা"
        TaskDetailField.REMINDER -> "রিমাইন্ডার"
        TaskDetailField.RECURRENCE -> "পুনরাবৃত্তি"
        TaskDetailField.PROJECT -> "প্রজেক্ট"
        TaskDetailField.CLIENT -> "ক্লায়েন্ট"
        TaskDetailField.TAGS -> "ট্যাগ"
        TaskDetailField.ESTIMATE -> "আনুমানিক সময়"
        TaskDetailField.SUBTASKS -> "ধাপ"
        TaskDetailField.NOTES -> "নোট"
    }

    override fun addField(field: TaskDetailField): String = "${fieldName(field)} যোগ করুন"

    override fun clearField(field: TaskDetailField): String = "${fieldName(field)} মুছুন"

    private fun weeklyLabel(rule: RecurrenceRule.Weekly): String {
        val every = if (rule.interval == 1) "প্রতি সপ্তাহে" else "প্রতি ${digits(rule.interval)} সপ্তাহে"
        if (rule.days.isEmpty()) return every
        val days = DayOfWeek.entries.filter { it in rule.days }.joinToString(", ") { weekdayShort(it) }
        return "$every $days-বারে"
    }

    private fun monthlyLabel(rule: RecurrenceRule.Monthly): String {
        val every = if (rule.interval == 1) "প্রতি মাসে" else "প্রতি ${digits(rule.interval)} মাসে"
        val day = rule.dayOfMonth ?: return every
        return "$every ${digits(day)} তারিখে"
    }

    private fun digits(value: Int): String = bengaliNumerals(value.toString())

    private fun digits(value: Long): String = bengaliNumerals(value.toString())
}

private const val KILOBYTE = 1_024L

private const val MEGABYTE = 1_024L * 1_024L
