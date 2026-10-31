package com.rayluxeo.aida.ui.tasks

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.TaskSort
import org.junit.Test

/**
 * The phrasebook is the one place a bilingual screen slips, and it slips silently: a view added
 * to [TasksView] with no Bengali label still compiles, and Abu finds out by reading an English
 * word in the middle of a Bengali list.
 *
 * These tests walk every enum rather than sampling, so a value added later cannot go unworded.
 */
class TasksWordingTest {

    private val english = tasksWording(SpokenLanguage.ENGLISH)
    private val bengali = tasksWording(SpokenLanguage.BENGALI)

    @Test
    fun `every view is named and described in both languages`() {
        TasksView.entries.forEach { view ->
            assertThat(english.viewLabel(view)).isNotEmpty()
            assertThat(bengali.viewLabel(view)).isNotEmpty()
            assertThat(english.emptyMessage(view)).isNotEmpty()
            assertThat(bengali.emptyMessage(view)).isNotEmpty()
        }
    }

    /** Two views wearing the same chip label would be two chips Abu cannot tell apart. */
    @Test
    fun `no two views share a label`() {
        val englishLabels = TasksView.entries.map { english.viewLabel(it) }
        val bengaliLabels = TasksView.entries.map { bengali.viewLabel(it) }
        assertThat(englishLabels).containsNoDuplicates()
        assertThat(bengaliLabels).containsNoDuplicates()
    }

    @Test
    fun `every sort names itself and both of its directions`() {
        TaskSort.entries.forEach { sort ->
            assertThat(english.sortLabel(sort)).isNotEmpty()
            assertThat(bengali.sortLabel(sort)).isNotEmpty()
            assertThat(english.sortDirection(sort, ascending = true))
                .isNotEqualTo(english.sortDirection(sort, ascending = false))
            assertThat(bengali.sortDirection(sort, ascending = true))
                .isNotEqualTo(bengali.sortDirection(sort, ascending = false))
        }
    }

    @Test
    fun `every snooze option is worded and they are all different`() {
        val englishOptions = SnoozeOption.entries.map { english.snoozeOption(it) }
        val bengaliOptions = SnoozeOption.entries.map { bengali.snoozeOption(it) }
        assertThat(englishOptions).containsNoDuplicates()
        assertThat(bengaliOptions).containsNoDuplicates()
        assertThat(englishOptions.none { it.isEmpty() }).isTrue()
        assertThat(bengaliOptions.none { it.isEmpty() }).isTrue()
    }

    /** A snooze that moved a task backwards, or nowhere, would be refused by the use case. */
    @Test
    fun `the snooze options are positive and get longer`() {
        val amounts = SnoozeOption.entries.map { it.amount }
        assertThat(amounts.all { !it.isZero && !it.isNegative }).isTrue()
        assertThat(amounts).isInStrictOrder()
    }

    /**
     * Latin digits inside a Bengali sentence are the one bilingual slip that reads as broken even
     * to someone who cannot read the words.
     */
    @Test
    fun `Bengali counts are written in Bengali digits`() {
        assertThat(bengali.selectedCount(3)).contains("৩")
        assertThat(bengali.tasksCompleted(12)).contains("১২")
        assertThat(bengali.tasksArchived(2)).contains("২")
        assertThat(bengali.tasksDeleted(7)).contains("৭")
        assertThat(bengali.stepsProgress(done = 1, total = 5)).contains("৫")
        assertThat(bengali.stepsProgress(done = 1, total = 5)).contains("১")

        assertThat(bengali.selectedCount(3)).doesNotContain("3")
        assertThat(bengali.tasksCompleted(12)).doesNotContain("12")
        assertThat(bengali.stepsProgress(done = 1, total = 5)).doesNotContain("5")
    }

    @Test
    fun `English counts are written in the digits English uses`() {
        assertThat(english.selectedCount(3)).contains("3")
        assertThat(english.tasksCompleted(12)).contains("12")
        assertThat(english.stepsProgress(done = 1, total = 5)).isEqualTo("1 of 5 steps")
    }

    /**
     * A screen that mixes an English chip into a Bengali list is harder to read than either
     * language on its own, so the Bengali labels carry no Latin letters at all.
     */
    @Test
    fun `the Bengali labels are written in Bengali script`() {
        val labels = buildList {
            addAll(TasksView.entries.map { bengali.viewLabel(it) })
            addAll(TasksView.entries.map { bengali.emptyMessage(it) })
            addAll(TaskSort.entries.map { bengali.sortLabel(it) })
            addAll(TaskSort.entries.map { bengali.sortDirection(it, ascending = true) })
            addAll(TaskSort.entries.map { bengali.sortDirection(it, ascending = false) })
            addAll(SnoozeOption.entries.map { bengali.snoozeOption(it) })
            addAll(bengali.tabLabels)
            addAll(bengali.priorityOptions)
            add(bengali.screenTitle)
            add(bengali.addTask)
            add(bengali.filtersTitle)
            add(bengali.sortTitle)
            add(bengali.clearFilters)
            add(bengali.projectLabel)
            add(bengali.clientLabel)
            add(bengali.tagsLabel)
            add(bengali.anyProject)
            add(bengali.anyClient)
            add(bengali.noProjectSection)
            add(bengali.noClientSection)
            add(bengali.complete)
            add(bengali.reopen)
            add(bengali.archive)
            add(bengali.unarchive)
            add(bengali.duplicate)
            add(bengali.delete)
            add(bengali.moreActions)
            add(bengali.selectTask)
            add(bengali.selectAll)
            add(bengali.clearSelection)
            add(bengali.leaveSelection)
            add(bengali.undo)
            add(bengali.undone)
            add(bengali.taskArchived)
            add(bengali.taskUnarchived)
            add(bengali.taskDuplicated)
        }
        labels.forEach { label ->
            assertThat(label.none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
        }
    }

    /**
     * Cancelling a reminder is the one act on this screen that cannot be undone, so the question
     * in front of it is load-bearing: a missing Bengali line there would put an English dialog
     * over a Bengali list at exactly the moment Abu is being asked to lose something.
     */
    @Test
    fun `the cancel question is worded in both languages`() {
        val englishReminders = remindersWording(SpokenLanguage.ENGLISH)
        val bengaliReminders = remindersWording(SpokenLanguage.BENGALI)
        listOf(englishReminders, bengaliReminders).forEach { words ->
            assertThat(words.cancelTitle).isNotEmpty()
            assertThat(words.cancelConfirm).isNotEmpty()
            assertThat(words.keepReminder).isNotEmpty()
            // The two answers must not be the same word, or the dialog offers one choice twice.
            assertThat(words.cancelConfirm).isNotEqualTo(words.keepReminder)
        }
        assertThat(englishReminders.cancelMessage("Leave for the airport"))
            .contains("Leave for the airport")
        assertThat(bengaliReminders.cancelMessage("বিমানবন্দর")).contains("বিমানবন্দর")

        val bengaliLines = listOf(
            bengaliReminders.cancelTitle,
            bengaliReminders.cancelConfirm,
            bengaliReminders.keepReminder,
            bengaliReminders.cancelMessage("বিমানবন্দর"),
        )
        bengaliLines.forEach { line ->
            assertThat(line.none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
        }
    }

    /** The composer sheet is not this area's file, so its words are checked rather than assumed. */
    @Test
    fun `the composer keeps the words its sheet reads`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.newTask).isNotEmpty()
            assertThat(words.taskTitlePlaceholder).isNotEmpty()
            assertThat(words.addDate).isNotEmpty()
            assertThat(words.addTime).isNotEmpty()
            assertThat(words.clearDueDate).isNotEmpty()
            assertThat(words.dueTimeTitle).isNotEmpty()
            assertThat(words.priorityOptions).hasSize(3)
        }
    }
}
