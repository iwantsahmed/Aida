package com.rayluxeo.aida.ui.alarms

import com.rayluxeo.aida.core.common.SpokenLanguage
import java.time.DayOfWeek
import java.time.LocalTime

/** Whether a line Mira puts up is something that went right or something that went wrong. */
enum class AlarmsNoticeTone { CONFIRM, PROBLEM }

/**
 * A short line about what just happened.
 *
 * Shared by the Alarms and the Timers screens, which sit side by side and must not report the
 * same kind of thing two different ways.
 */
data class AlarmsNotice(
    val message: String,
    val tone: AlarmsNoticeTone,
)

/**
 * One alarm as the list shows it.
 *
 * Everything is already worded: the view model is where Abu's language is known, so a composable
 * never has to choose between an English literal and a Bengali one.
 *
 * [supporting] is the one line under the label and it says only what is true — the next ring for
 * an alarm that is on, the snooze for one that is pushed away, and the word for "off" for one
 * that is not armed at all. A row must never read as though it will ring when it will not.
 */
data class AlarmRow(
    val id: Long,
    val label: String,
    val timeLabel: String,
    val repeatLabel: String,
    val supporting: String,
    val isEnabled: Boolean,
)

/**
 * The add-or-edit sheet.
 *
 * [id] is null for an alarm that does not exist yet, which is also what decides whether Delete is
 * offered: there is nothing to delete until there is a row.
 */
data class AlarmEditorUiState(
    val id: Long? = null,
    val label: String = "",
    val time: LocalTime,
    /**
     * [time] worded in Abu's language. Derived rather than stored: the view model words it on
     * every emission, so the button reads "6 am" or "সকাল ৬টা" and never `LocalTime.toString()`.
     */
    val timeLabel: String = "",
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val isSaving: Boolean = false,
) {
    val isNew: Boolean get() = id == null
}

data class AlarmsUiState(
    val isLoading: Boolean = true,
    /** Which language the screen's own labels are written in; the rows are worded to match. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val alarms: List<AlarmRow> = emptyList(),
    val editor: AlarmEditorUiState? = null,
    /**
     * False when Android has withheld exact alarms. The screen says so rather than showing a row
     * that looks armed to the minute when it is not.
     */
    val canScheduleExact: Boolean = true,
    val notice: AlarmsNotice? = null,
) {
    val isEmpty: Boolean get() = alarms.isEmpty()
}
