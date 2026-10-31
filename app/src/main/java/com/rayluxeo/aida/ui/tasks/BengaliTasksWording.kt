package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.domain.model.TaskSort
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * The Tasks screen in Bengali. Bengali script throughout — a screen that mixes an English chip
 * into a Bengali list is harder to read than either language on its own.
 *
 * Every number goes through [bengaliNumerals] for the same reason: "3 selected" written with
 * Latin digits inside a Bengali sentence is the one place a bilingual screen always slips.
 */
internal object BengaliTasksWording : TasksWording {

    override val screenTitle: String = "কাজ"

    override val tabLabels: List<String> = listOf("কাজ", "রিমাইন্ডার")

    override val addTask: String = "কাজ যোগ করুন"

    override val addFirstTask: String = "একটি কাজ যোগ করুন"

    override val searchPlaceholder: String = "কাজ খুঁজুন"

    override val clearSearch: String = "খোঁজা মুছুন"

    override val noReminders: String = "কিছু নির্ধারিত নেই। আইদাকে কিছু মনে করিয়ে দিতে বলুন।"

    override val cancel: String = "বাতিল"

    override val set: String = "ঠিক আছে"

    override val dismiss: String = "বন্ধ করুন"

    override val done: String = "ঠিক আছে"

    override val newTask: String = "নতুন কাজ"

    override val fullDetails: String = "বিস্তারিত বিবরণ খুলুন"

    override val newReminder: String = "নতুন রিমাইন্ডার"

    override val addReminder: String = "রিমাইন্ডার যোগ করুন"

    override val reminderTitlePlaceholder: String = "কী মনে করিয়ে দিতে হবে?"

    override val taskTitlePlaceholder: String = "কী করতে হবে?"

    override val addDate: String = "তারিখ দিন"

    override val addTime: String = "সময় দিন"

    override val clearDueDate: String = "তারিখ মুছুন"

    override val priorityLabel: String = "গুরুত্ব"

    override val priorityOptions: List<String> = listOf("কম", "স্বাভাবিক", "জরুরি")

    override val highPriority: String = "জরুরি"

    override val dueTimeTitle: String = "কাজের সময়"

    override val filtersTitle: String = "ছাঁকনি"

    override val sortTitle: String = "সাজানো"

    override val clearFilters: String = "ছাঁকনি মুছুন"

    override val projectLabel: String = "প্রকল্প"

    override val clientLabel: String = "ক্লায়েন্ট"

    override val tagsLabel: String = "ট্যাগ"

    override val anyProject: String = "সব প্রকল্প"

    override val anyClient: String = "সব ক্লায়েন্ট"

    override val noProjectSection: String = "প্রকল্প নেই"

    override val noClientSection: String = "ক্লায়েন্ট নেই"

    override fun sortLabel(sort: TaskSort): String = when (sort) {
        TaskSort.DUE_DATE -> "তারিখ"
        TaskSort.PRIORITY -> "গুরুত্ব"
        TaskSort.CREATED -> "তৈরির সময়"
        TaskSort.UPDATED -> "শেষ বদলের সময়"
        TaskSort.TITLE -> "নাম"
    }

    override fun sortDirection(sort: TaskSort, ascending: Boolean): String = when (sort) {
        TaskSort.DUE_DATE -> if (ascending) "কাছেরটা আগে" else "দূরেরটা আগে"
        TaskSort.PRIORITY -> if (ascending) "কম গুরুত্ব আগে" else "বেশি গুরুত্ব আগে"
        TaskSort.CREATED -> if (ascending) "পুরনো আগে" else "নতুন আগে"
        TaskSort.UPDATED -> if (ascending) "আগে বদলানো আগে" else "সদ্য বদলানো আগে"
        TaskSort.TITLE -> if (ascending) "অ থেকে হ" else "হ থেকে অ"
    }

    override val complete: String = "সম্পন্ন করুন"

    override val reopen: String = "আবার খুলুন"

    override val archive: String = "আর্কাইভ করুন"

    override val unarchive: String = "আর্কাইভ থেকে আনুন"

    override val duplicate: String = "নকল করুন"

    override val delete: String = "মুছুন"

    override val moreActions: String = "আরও"

    override val archivedLabel: String = "আর্কাইভ"

    override val repeats: String = "পুনরাবৃত্তি"

    override val snoozeTitle: String = "সরিয়ে দিন"

    override fun snoozeOption(option: SnoozeOption): String = when (option) {
        SnoozeOption.ONE_HOUR -> "এক ঘণ্টা পরে"
        SnoozeOption.THREE_HOURS -> "তিন ঘণ্টা পরে"
        SnoozeOption.ONE_DAY -> "এক দিন পরে"
        SnoozeOption.ONE_WEEK -> "এক সপ্তাহ পরে"
    }

    override fun stepsProgress(done: Int, total: Int): String =
        "${digits(total)}টির মধ্যে ${digits(done)}টি ধাপ"

    override val selectTask: String = "বাছুন"

    override val selectAll: String = "সব বাছুন"

    override val clearSelection: String = "বাছাই মুছুন"

    override val leaveSelection: String = "বাছাই বন্ধ করুন"

    override fun selectedCount(count: Int): String = "${digits(count)}টি বাছা হয়েছে"

    override val taskDeleted: String = "কাজটি মুছে দিয়েছি।"

    override val undone: String = "ফিরিয়ে দিয়েছি।"

    override val taskArchived: String = "কাজটি আর্কাইভ করেছি।"

    override val taskUnarchived: String = "কাজটি তালিকায় ফিরিয়ে এনেছি।"

    override val taskDuplicated: String = "কাজটির একটি নকল যোগ করেছি।"

    override val reminderCancelled: String = "রিমাইন্ডার বাতিল করেছি।"

    override val undo: String = "ফিরিয়ে আনুন"

    override fun taskSnoozed(moment: String): String = "$moment-এ সরিয়ে দিয়েছি।"

    override fun tasksCompleted(count: Int): String = "${digits(count)}টি কাজ সম্পন্ন করেছি।"

    override fun tasksArchived(count: Int): String = "${digits(count)}টি কাজ আর্কাইভ করেছি।"

    override fun tasksDeleted(count: Int): String = "${digits(count)}টি কাজ মুছে দিয়েছি।"

    override fun viewLabel(view: TasksView): String = when (view) {
        TasksView.TODAY -> "আজ"
        TasksView.UPCOMING -> "সামনে"
        TasksView.OVERDUE -> "সময় পেরিয়েছে"
        TasksView.ALL -> "সব"
        TasksView.COMPLETED -> "সম্পন্ন"
        TasksView.BY_PROJECT -> "প্রকল্প অনুযায়ী"
        TasksView.BY_CLIENT -> "ক্লায়েন্ট অনুযায়ী"
    }

    override fun emptyMessage(view: TasksView): String = when (view) {
        TasksView.TODAY -> "আজ কোনো কাজ নেই।"
        TasksView.UPCOMING -> "সামনে কোনো কাজ নেই।"
        TasksView.OVERDUE -> "কোনো কাজের সময় পেরোয়নি।"
        TasksView.ALL -> "এখনও কোনো কাজ নেই।"
        TasksView.COMPLETED -> "এখনও কিছু সম্পন্ন হয়নি।"
        TasksView.BY_PROJECT -> "কোনো প্রকল্পে বাকি কাজ নেই।"
        TasksView.BY_CLIENT -> "কোনো ক্লায়েন্টের বাকি কাজ নেই।"
    }

    override fun noMatches(query: String): String = "\"$query\" মেলে এমন কিছু নেই।"

    override fun moreActionsFor(title: String): String = "$title-এর আরও কিছু"

    private fun digits(value: Int): String = bengaliNumerals(value.toString())
}
