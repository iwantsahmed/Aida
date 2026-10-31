package com.rayluxeo.aida.ui.alarms

import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.time.DayOfWeek
import java.time.Duration

/**
 * Mira's alarms and timers in Bengali.
 *
 * Every figure goes through [bengaliNumerals], including the colons of a countdown: a Bengali row
 * that reads "০১:২৫" beside "২৫ মিনিট" is one sentence, while one that reads "01:25" is two.
 *
 * The weekday names are written out rather than taken from `DayOfWeek.getDisplayName`, whose
 * bn-BD short forms are the full words and would not fit a chip.
 */
internal object BengaliAlarmsWording : AlarmsWording {

    override val alarmsTitle: String = "অ্যালার্ম"

    override val addAlarm: String = "অ্যালার্ম যোগ করুন"

    override val noAlarms: String = "কোনো অ্যালার্ম নেই।"

    override val addFirstAlarm: String = "একটি দিন"

    override val newAlarm: String = "নতুন অ্যালার্ম"

    override val editAlarm: String = "অ্যালার্ম বদলান"

    override val alarmLabelPlaceholder: String = "কীসের জন্য?"

    override val alarmTimeTitle: String = "অ্যালার্মের সময়"

    override val repeatLabel: String = "পুনরাবৃত্তি"

    override val repeatOnce: String = "একবার"

    override val repeatEveryDay: String = "প্রতিদিন"

    override val repeatWeekdays: String = "কর্মদিবস"

    override val save: String = "সংরক্ষণ"

    override val cancel: String = "বাতিল"

    override val delete: String = "মুছুন"

    override val dismiss: String = "সরান"

    override val back: String = "ফিরে যান"

    override val set: String = "ঠিক আছে"

    override fun toggleAlarm(label: String): String = "$label চালু বা বন্ধ করুন"

    override fun weekdayShort(day: DayOfWeek): String = WEEKDAY_NAMES[day.value - 1]

    override fun rowTitle(time: String, label: String): String = "$time · $label"

    /**
     * An em dash, not the middle dot that joins days and not a comma — [ringsAt] already carries
     * the comma inside a worded moment ("আগামীকাল, সকাল ৫টা"), so a comma here would give the row
     * two breaks of equal weight and no hierarchy.
     */
    override fun rowSupporting(repeat: String, state: String): String = "$repeat — $state"

    override fun ringsAt(moment: String): String = "$moment বাজবে"

    override fun snoozedUntil(moment: String): String = "$moment পর্যন্ত স্থগিত"

    override val alarmOff: String = "বন্ধ"

    override val alarmNotArmed: String = "চালু, কিন্তু অ্যান্ড্রয়েড এটি ধরে রাখেনি"

    override val alarmSaved: String = "অ্যালার্ম সংরক্ষণ হয়েছে।"

    override val alarmDeleted: String = "অ্যালার্ম মুছে ফেলা হয়েছে।"

    override val untitledAlarm: String = "অ্যালার্ম"

    override val exactAlarmsDenied: String =
        "অ্যালার্ম ও টাইমার দেরিতে আসতে পারে: অ্যান্ড্রয়েড আইদাকে ঠিক মিনিটে সেগুলো দিতে দিচ্ছে না।"

    override val openExactAlarmSettings: String = "অনুমতি দিন"

    override val timersTitle: String = "টাইমার"

    override val addTimer: String = "টাইমার চালু করুন"

    override val noTimers: String = "কোনো টাইমার চলছে না।"

    override val addFirstTimer: String = "একটি চালু করুন"

    override val newTimer: String = "নতুন টাইমার"

    override val timerLabelPlaceholder: String = "কীসের জন্য?"

    override val durationLabel: String = "কত সময়"

    override val minutesLabel: String = "মিনিট"

    override val start: String = "শুরু"

    override val pause: String = "থামান"

    override val resume: String = "চালু করুন"

    override val cancelTimer: String = "বাতিল"

    override val timerPaused: String = "থামানো"

    override val untitledTimer: String = "টাইমার"

    override fun countdown(remaining: Duration): String {
        val (hours, minutes, seconds) = countdownParts(remaining)
        val latin = if (hours > 0) {
            "$hours:${padded(minutes)}:${padded(seconds)}"
        } else {
            "${padded(minutes)}:${padded(seconds)}"
        }
        return bengaliNumerals(latin)
    }

    override fun durationSummary(duration: Duration): String {
        val (hours, minutes, _) = countdownParts(duration)
        val parts = buildList {
            if (hours > 0) add("${bengaliNumerals(hours.toString())} ঘণ্টা")
            if (minutes > 0 || hours == 0L) add("${bengaliNumerals(minutes.toString())} মিনিট")
        }
        return parts.joinToString(" ")
    }

    override fun durationPreset(duration: Duration): String {
        val (hours, minutes, _) = countdownParts(duration)
        return if (hours > 0 && minutes == 0L) {
            "${bengaliNumerals(hours.toString())} ঘ"
        } else {
            "${bengaliNumerals((hours * 60 + minutes).toString())} মি"
        }
    }

    /** Indexed by `DayOfWeek.value - 1`, so Monday sits at zero the way `java.time` counts. */
    private val WEEKDAY_NAMES = listOf(
        "সোম",
        "মঙ্গল",
        "বুধ",
        "বৃহঃ",
        "শুক্র",
        "শনি",
        "রবি",
    )
}
