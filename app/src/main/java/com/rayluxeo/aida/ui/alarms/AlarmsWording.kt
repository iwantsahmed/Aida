package com.rayluxeo.aida.ui.alarms

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.response.RepeatWording
import com.rayluxeo.aida.ui.response.repeatPhrase
import java.time.DayOfWeek
import java.time.Duration

/**
 * Every word the Alarms and Timers screens put in front of Abu, in one language.
 *
 * Alarms and timers share a phrasebook because they are one pair of screens with one voice: the
 * same "Off", the same "Cancel", the same way of counting minutes. Splitting them would mean two
 * files that start identical and drift.
 *
 * Nothing here is a format string with a number baked into it. Bengali writes its own digits, so
 * anything counted takes the value and renders it — [countdown] and [durationSummary] are the
 * whole reason a Bengali alarm row does not read "1:04:59" in the middle of a Bengali sentence.
 */
internal interface AlarmsWording {

    // --- Alarms ----------------------------------------------------------------------------

    val alarmsTitle: String

    val addAlarm: String

    val noAlarms: String

    val addFirstAlarm: String

    val newAlarm: String

    val editAlarm: String

    val alarmLabelPlaceholder: String

    /** The time picker's own title; the wheel below it carries no words of its own. */
    val alarmTimeTitle: String

    val repeatLabel: String

    /** The chip that clears every weekday, leaving a one-time alarm. */
    val repeatOnce: String

    val repeatEveryDay: String

    val repeatWeekdays: String

    val save: String

    val cancel: String

    val delete: String

    val dismiss: String

    val back: String

    val set: String

    /** What a row's switch does, said out loud for TalkBack, which cannot see the state. */
    fun toggleAlarm(label: String): String

    fun weekdayShort(day: DayOfWeek): String

    /**
     * "Every day", "Weekdays", "Mon · Thu", or the word for a one-time alarm.
     *
     * The decision is [repeatPhrase]'s, which is the same one Mira uses when she says an alarm's
     * repeat out loud. One alarm called "every weekday" in her reply and "Mon · Tue · Wed · Thu ·
     * Fri" in the list is one alarm Abu has to check twice. Only the words are the screen's own,
     * through [AlarmsRepeatWording]; the empty set is the row's to name, because "no repeat" and
     * "once" are the same fact said two ways.
     */
    fun repeatSummary(days: Set<DayOfWeek>): String =
        repeatPhrase(days, AlarmsRepeatWording(this)) ?: repeatOnce

    /**
     * A row's title: the time Abu scans for and what the alarm is called.
     *
     * Joined here rather than in the composable because the mark between them is a decision about
     * one language's punctuation, not about layout.
     */
    fun rowTitle(time: String, label: String): String

    /**
     * A row's one supporting line: what it repeats on, then what is true of it now.
     *
     * The mark between the two halves must never be [DAY_SEPARATOR]. A Mon/Wed/Fri alarm read
     * "Mon · Wed · Fri · Rings tomorrow, 5 am", where the ring time parses as a fourth day — the
     * separator that joins days inside the left half cannot also be the one that ends it.
     */
    fun rowSupporting(repeat: String, state: String): String

    /** The moment is already worded; this only frames it. */
    fun ringsAt(moment: String): String

    fun snoozedUntil(moment: String): String

    /** A switched-off alarm has no next moment, and saying "off" is the whole of it. */
    val alarmOff: String

    /**
     * Switched on with nothing armed behind it — which is what exact alarms being withdrawn
     * leaves. It must not read as "off": Abu did not switch it off, and it arms itself again the
     * moment the permission comes back. It must not read as a ring time either.
     */
    val alarmNotArmed: String

    val alarmSaved: String

    val alarmDeleted: String

    /** An alarm with no label is ordinary; the list still needs something to call it. */
    val untitledAlarm: String

    // --- When the OS will not let Mira name a minute ----------------------------------------

    /**
     * One sentence, not two glued together.
     *
     * This sits in a notice whose action button is measured before the text, so every word here
     * is paid for twice: once in height and once in danger-coloured ink at the top of both
     * screens. It was a title and a body concatenated with an em dash, which came to about nine
     * wrapped lines on a phone in a state Samsung devices reach routinely. It says what Abu loses
     * and why; the button beside it says the remedy.
     */
    val exactAlarmsDenied: String

    /** Short on purpose: it shares a row with [exactAlarmsDenied] and takes its width from it. */
    val openExactAlarmSettings: String

    // --- Timers ------------------------------------------------------------------------------

    val timersTitle: String

    val addTimer: String

    val noTimers: String

    val addFirstTimer: String

    val newTimer: String

    val timerLabelPlaceholder: String

    val durationLabel: String

    val minutesLabel: String

    val start: String

    val pause: String

    val resume: String

    val cancelTimer: String

    val timerPaused: String

    val untitledTimer: String

    /** Hours only when there are any: a 25-minute timer reads "24:59", never "00:24:59". */
    fun countdown(remaining: Duration): String

    /** How long the timer was set for, read under the countdown. */
    fun durationSummary(duration: Duration): String

    /** The shortcuts above the minutes field. They fill it in; the field stays the truth. */
    fun durationPreset(duration: Duration): String
}

internal fun alarmsWording(language: SpokenLanguage): AlarmsWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliAlarmsWording
    SpokenLanguage.ENGLISH -> EnglishAlarmsWording
}

/**
 * The Alarms screen's words for a repeat, handed to [repeatPhrase] so the row and Mira's spoken
 * reply reach one decision about one set of days.
 *
 * The words are the screen's own on purpose: a chip has room for "Mon" where a sentence wants
 * "Monday", and "Weekdays" is a heading rather than "every weekday". What must not differ is which
 * sets are said as one idea and in what order the rest are read, and that is not here.
 */
private class AlarmsRepeatWording(private val words: AlarmsWording) : RepeatWording {

    override val everyDay: String get() = words.repeatEveryDay

    override val everyWeekday: String get() = words.repeatWeekdays

    override fun day(day: DayOfWeek): String = words.weekdayShort(day)

    /** A middle dot in both languages: a Bengali row and an English one are the same shape. */
    override fun joinDays(names: List<String>): String = names.joinToString(DAY_SEPARATOR)
}

/** What sits between two days in a repeat read back as a list. */
internal const val DAY_SEPARATOR: String = " · "

/** Every day of the week, for the editor's shortcut that selects the lot in one tap. */
internal val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

/**
 * Splits a countdown into the three numbers a clock shows.
 *
 * Shared rather than written twice, because the arithmetic is not a matter of language: only the
 * digits are, and each phrasebook renders those itself. Never negative — [Duration.toHours] on a
 * negative span counts backwards, and a timer that has run out reads as zero.
 */
internal fun countdownParts(remaining: Duration): Triple<Long, Long, Long> {
    val seconds = remaining.seconds.coerceAtLeast(0)
    return Triple(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
}

/** Two digits, padded by hand: String.format would follow the machine's default locale. */
internal fun padded(value: Long): String = if (value < 10) "0$value" else "$value"
