package com.rayluxeo.aida.ui.alarms

import java.time.DayOfWeek
import java.time.Duration
import java.time.format.TextStyle
import java.util.Locale

/** Mira's alarms and timers in English: plain, short, never chatty. */
internal object EnglishAlarmsWording : AlarmsWording {

    override val alarmsTitle: String = "Alarms"

    override val addAlarm: String = "Add an alarm"

    override val noAlarms: String = "No alarms set."

    override val addFirstAlarm: String = "Set one"

    override val newAlarm: String = "New alarm"

    override val editAlarm: String = "Edit alarm"

    override val alarmLabelPlaceholder: String = "What is it for?"

    override val alarmTimeTitle: String = "Alarm time"

    override val repeatLabel: String = "Repeat"

    override val repeatOnce: String = "Once"

    override val repeatEveryDay: String = "Every day"

    override val repeatWeekdays: String = "Weekdays"

    override val save: String = "Save"

    override val cancel: String = "Cancel"

    override val delete: String = "Delete"

    override val dismiss: String = "Dismiss"

    override val back: String = "Back"

    override val set: String = "Set"

    override fun toggleAlarm(label: String): String = "Turn $label on or off"

    override fun weekdayShort(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT, Locale.US)

    override fun rowTitle(time: String, label: String): String = "$time · $label"

    /**
     * An em dash rather than the middle dot that joins days, and the same mark Mira already uses
     * to hang a state off a moment in `TaskTimeFormatter.dueLabel`.
     */
    override fun rowSupporting(repeat: String, state: String): String = "$repeat — $state"

    override fun ringsAt(moment: String): String = "Rings $moment"

    override fun snoozedUntil(moment: String): String = "Snoozed until $moment"

    override val alarmOff: String = "Off"

    override val alarmNotArmed: String = "On, but Android is not holding it"

    override val alarmSaved: String = "Alarm saved."

    override val alarmDeleted: String = "Alarm deleted."

    override val untitledAlarm: String = "Alarm"

    override val exactAlarmsDenied: String =
        "Alarms and timers may be late: Android will not let Aida set them to the exact minute."

    override val openExactAlarmSettings: String = "Allow"

    override val timersTitle: String = "Timers"

    override val addTimer: String = "Start a timer"

    override val noTimers: String = "No timers running."

    override val addFirstTimer: String = "Start one"

    override val newTimer: String = "New timer"

    override val timerLabelPlaceholder: String = "What is it for?"

    override val durationLabel: String = "How long"

    override val minutesLabel: String = "Minutes"

    override val start: String = "Start"

    override val pause: String = "Pause"

    override val resume: String = "Resume"

    override val cancelTimer: String = "Cancel"

    override val timerPaused: String = "Paused"

    override val untitledTimer: String = "Timer"

    override fun countdown(remaining: Duration): String {
        val (hours, minutes, seconds) = countdownParts(remaining)
        return if (hours > 0) {
            "$hours:${padded(minutes)}:${padded(seconds)}"
        } else {
            "${padded(minutes)}:${padded(seconds)}"
        }
    }

    override fun durationSummary(duration: Duration): String {
        val (hours, minutes, _) = countdownParts(duration)
        val parts = buildList {
            if (hours > 0) add(plural(hours, "hour"))
            if (minutes > 0 || hours == 0L) add(plural(minutes, "minute"))
        }
        return parts.joinToString(" ")
    }

    override fun durationPreset(duration: Duration): String {
        val (hours, minutes, _) = countdownParts(duration)
        return if (hours > 0 && minutes == 0L) "${hours}h" else "${hours * 60 + minutes}m"
    }

    private fun plural(value: Long, noun: String): String =
        if (value == 1L) "$value $noun" else "$value ${noun}s"
}
