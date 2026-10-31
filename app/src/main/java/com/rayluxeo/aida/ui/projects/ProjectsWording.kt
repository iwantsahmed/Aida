package com.rayluxeo.aida.ui.projects

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * Every word the Projects screen puts in front of Abu, in one language: the title, the filter and
 * status labels, the list's empty state, the detail sheet, the create/edit sheet, the delete dialog
 * and the undo notice.
 *
 * One phrasebook with one voice, so the word the sheet uses for "Delete" and the word the dialog
 * uses cannot drift apart. Anything carrying data Abu typed — a project's name, a count of open
 * tasks — is a function rather than a format string, because Bengali writes its own digits and a
 * `%d` baked into a sentence would leave a Latin numeral in a Bengali row. Status and filter names
 * are functions over the enums, so a new status is a compile error here rather than a missing label.
 */
internal interface ProjectsWording {

    val title: String

    val searchPlaceholder: String

    val addProject: String

    /** The list's empty state: a normal state, not a fault, and a reason to add the first one. */
    val noProjects: String

    /** The filter chips across the top, one per [ProjectFilter]. */
    fun filterLabel(filter: ProjectFilter): String

    /** A project's status, wherever it is shown — the row badge, the detail sheet, the status picker. */
    fun statusLabel(status: ProjectStatus): String

    /** What a project with no client is called — Abu's own work rather than a client's. */
    val personal: String

    // --- The detail sheet ----------------------------------------------------------------------

    /** The client line, worded whole so the colon is a Bengali decision, not the composable's. */
    fun clientDetail(name: String): String

    fun startedDetail(date: String): String

    fun dueDetail(date: String): String

    fun budgetDetail(amount: String): String

    /** The description heading in the detail sheet, shown only when there is one. */
    val descriptionLabel: String

    /** The open-task count chip. Bengali counts in its own digits, so the number comes in here. */
    fun openTasksCount(count: Int): String

    /** The heading above the status picker in the detail sheet. */
    val statusSection: String

    val editProjectAction: String

    /** The sheet's button, distinct from the dialog's bare [delete] confirm. */
    val deleteProjectAction: String

    // --- The delete dialog ---------------------------------------------------------------------

    val deleteTitle: String

    fun deleteMessage(name: String): String

    val delete: String

    val cancel: String

    // --- The undo notice -----------------------------------------------------------------------

    val undo: String

    fun projectAdded(name: String): String

    val projectMovedToBin: String

    // --- The create / edit sheet ---------------------------------------------------------------

    val newProject: String

    val editProject: String

    val nameLabel: String

    val namePlaceholder: String

    val descriptionFieldLabel: String

    val clientSection: String

    val budgetLabel: String

    val budgetPlaceholder: String

    val startedLabel: String

    val dueLabel: String

    /** The trailing control on an unset date row. */
    val pickDate: String

    /** The date picker's confirm and dismiss. */
    val setDate: String

    val saveProject: String
}

internal fun projectsWording(language: SpokenLanguage): ProjectsWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliProjectsWording
    SpokenLanguage.ENGLISH -> EnglishProjectsWording
}

/** The Projects screen in English: plain, short, never chatty. */
internal object EnglishProjectsWording : ProjectsWording {

    override val title: String = "Projects"

    override val searchPlaceholder: String = "Search projects"

    override val addProject: String = "Add Project"

    override val noProjects: String =
        "No projects here yet. Add one to group its tasks, client and invoices together."

    override fun filterLabel(filter: ProjectFilter): String = when (filter) {
        ProjectFilter.OPEN -> "Open"
        ProjectFilter.ACTIVE -> "Active"
        ProjectFilter.COMPLETED -> "Completed"
        ProjectFilter.CANCELLED -> "Cancelled"
        ProjectFilter.ALL -> "All"
    }

    override fun statusLabel(status: ProjectStatus): String = when (status) {
        ProjectStatus.PLANNED -> "Planned"
        ProjectStatus.ACTIVE -> "Active"
        ProjectStatus.ON_HOLD -> "On hold"
        ProjectStatus.COMPLETED -> "Completed"
        ProjectStatus.CANCELLED -> "Cancelled"
    }

    override val personal: String = "Personal"

    override fun clientDetail(name: String): String = "Client: $name"

    override fun startedDetail(date: String): String = "Started: $date"

    override fun dueDetail(date: String): String = "Due: $date"

    override fun budgetDetail(amount: String): String = "Budget: $amount"

    override val descriptionLabel: String = "Notes"

    override fun openTasksCount(count: Int): String = "$count open tasks"

    override val statusSection: String = "Status"

    override val editProjectAction: String = "Edit Project"

    override val deleteProjectAction: String = "Delete Project"

    override val deleteTitle: String = "Delete this project?"

    override fun deleteMessage(name: String): String =
        "\"$name\" moves to the bin. You can undo straight after."

    override val delete: String = "Delete"

    override val cancel: String = "Cancel"

    override val undo: String = "Undo"

    override fun projectAdded(name: String): String = "Project '$name' added"

    override val projectMovedToBin: String = "Project moved to bin"

    override val newProject: String = "New Project"

    override val editProject: String = "Edit Project"

    override val nameLabel: String = "Project Name"

    override val namePlaceholder: String = "e.g., Rayluxeo Website"

    override val descriptionFieldLabel: String = "Notes (Optional)"

    override val clientSection: String = "Client (Optional)"

    override val budgetLabel: String = "Budget (Optional)"

    override val budgetPlaceholder: String = "e.g., 50000"

    override val startedLabel: String = "Start Date"

    override val dueLabel: String = "Due Date"

    override val pickDate: String = "Set"

    override val setDate: String = "Set"

    override val saveProject: String = "Save Project"
}

/**
 * The Projects screen in Bengali.
 *
 * Every count goes through [bengaliNumerals], so a chip reads "৩টি খোলা কাজ" rather than "3টি খোলা
 * কাজ". "ইনভয়েস" and "ক্লায়েন্ট" are written in Bengali script rather than translated, because those
 * are the words Abu already uses.
 */
internal object BengaliProjectsWording : ProjectsWording {

    override val title: String = "প্রকল্প"

    override val searchPlaceholder: String = "প্রকল্প খুঁজুন"

    override val addProject: String = "প্রকল্প যোগ করুন"

    override val noProjects: String =
        "এখানে এখনও কোনো প্রকল্প নেই। কাজ, ক্লায়েন্ট আর ইনভয়েস একসঙ্গে রাখতে একটি যোগ করুন।"

    override fun filterLabel(filter: ProjectFilter): String = when (filter) {
        ProjectFilter.OPEN -> "চলমান"
        ProjectFilter.ACTIVE -> "সক্রিয়"
        ProjectFilter.COMPLETED -> "সম্পন্ন"
        ProjectFilter.CANCELLED -> "বাতিল"
        ProjectFilter.ALL -> "সব"
    }

    override fun statusLabel(status: ProjectStatus): String = when (status) {
        ProjectStatus.PLANNED -> "পরিকল্পিত"
        ProjectStatus.ACTIVE -> "সক্রিয়"
        ProjectStatus.ON_HOLD -> "স্থগিত"
        ProjectStatus.COMPLETED -> "সম্পন্ন"
        ProjectStatus.CANCELLED -> "বাতিল"
    }

    override val personal: String = "নিজের"

    override fun clientDetail(name: String): String = "ক্লায়েন্ট: $name"

    override fun startedDetail(date: String): String = "শুরু: $date"

    override fun dueDetail(date: String): String = "জমা: $date"

    override fun budgetDetail(amount: String): String = "বাজেট: $amount"

    override val descriptionLabel: String = "টীকা"

    override fun openTasksCount(count: Int): String = "${bengaliNumerals(count.toString())}টি খোলা কাজ"

    override val statusSection: String = "অবস্থা"

    override val editProjectAction: String = "প্রকল্প সম্পাদনা"

    override val deleteProjectAction: String = "প্রকল্প মুছুন"

    override val deleteTitle: String = "এই প্রকল্প মুছবেন?"

    override fun deleteMessage(name: String): String =
        "\"$name\" বিনে চলে যাবে। ঠিক পরেই আপনি ফিরিয়ে আনতে পারবেন।"

    override val delete: String = "মুছুন"

    override val cancel: String = "বাতিল"

    override val undo: String = "ফিরিয়ে আনুন"

    override fun projectAdded(name: String): String = "প্রকল্প '$name' যোগ হয়েছে"

    override val projectMovedToBin: String = "প্রকল্প বিনে সরানো হয়েছে"

    override val newProject: String = "নতুন প্রকল্প"

    override val editProject: String = "প্রকল্প সম্পাদনা"

    override val nameLabel: String = "প্রকল্পের নাম"

    override val namePlaceholder: String = "যেমন, রেলাক্সিও ওয়েবসাইট"

    override val descriptionFieldLabel: String = "টীকা (ঐচ্ছিক)"

    override val clientSection: String = "ক্লায়েন্ট (ঐচ্ছিক)"

    override val budgetLabel: String = "বাজেট (ঐচ্ছিক)"

    override val budgetPlaceholder: String = "যেমন, ৫০০০০"

    override val startedLabel: String = "শুরুর তারিখ"

    override val dueLabel: String = "জমার তারিখ"

    override val pickDate: String = "নির্ধারণ"

    override val setDate: String = "নির্ধারণ"

    override val saveProject: String = "প্রকল্প সংরক্ষণ করুন"
}
