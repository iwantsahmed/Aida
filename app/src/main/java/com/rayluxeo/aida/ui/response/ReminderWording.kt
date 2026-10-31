package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ReminderKind
import java.time.Duration

/**
 * Every word Mira puts on a notification: the buttons, the line that says what a reminder is
 * about, the two channels Android draws in its own settings, and what a ringing alarm or a
 * finished timer is called.
 *
 * Same shape as [BriefWording] beside it — one interface, one object per language, one selector —
 * and for the same reason: a button label is not a translated word but a whole short phrase, and
 * "১০ মিনিট পরে" is not "10" plus "minutes later".
 *
 * Nothing here bakes a number into a format string. [snooze] takes the span and renders it, so
 * the figure on the button and the figure the alarm is actually re-armed for come from one
 * constant, and a Bengali button never shows a Latin digit.
 */
internal interface ReminderWording {

    // ---- The buttons. Short: Android truncates an action label without mercy. ----

    val complete: String
    val open: String
    val askMira: String
    val dismiss: String

    /** "Snooze 10 min", "১ ঘণ্টা পরে" — the span is read from [by], never typed into the words. */
    fun snooze(by: Duration): String

    // ---- What a reminder is about, under the title. ----

    /** Null where the title already says everything; a line repeating it is noise in the shade. */
    fun kindLine(kind: ReminderKind): String?

    // ---- The two channels, which Android shows Abu in system settings. ----

    val alarmChannelName: String
    val alarmChannelDescription: String
    val timerChannelName: String
    val timerChannelDescription: String

    // ---- What an unnamed reminder, a ringing alarm and a finished timer are called. ----

    /**
     * What a reminder that arrived with no title of its own is called.
     *
     * Singular, and a word of its own rather than the reminders channel's name: the channel is
     * "Reminders", and heading one reminder with it offers Abu a list he has not got.
     */
    val untitledReminderTitle: String

    /** What an alarm Abu never named is called. */
    val alarmTitle: String

    val timerFinishedTitle: String

    /** A timer whose moment passed while the phone was off, reported late and honestly. */
    val timerRanOutWhileOff: String
}

internal object EnglishReminderWording : ReminderWording {

    override val complete = "Complete"
    override val open = "Open"
    override val askMira = "Ask Aida"
    override val dismiss = "Dismiss"

    override fun snooze(by: Duration): String {
        val hours = by.toHours()
        return if (hours >= 1 && by.minusHours(hours).isZero) {
            if (hours == 1L) "Snooze 1 hour" else "Snooze $hours hours"
        } else {
            "Snooze ${by.toMinutes()} min"
        }
    }

    override fun kindLine(kind: ReminderKind): String? = when (kind) {
        ReminderKind.ONE_TIME -> null
        ReminderKind.RECURRING -> "Repeating reminder"
        ReminderKind.TASK -> "Task"
        ReminderKind.INVOICE -> "Invoice"
        ReminderKind.CLIENT_FOLLOW_UP -> "Follow-up"
        ReminderKind.BIRTHDAY -> "Birthday"
        ReminderKind.PROJECT_DEADLINE -> "Deadline"
        ReminderKind.SUBSCRIPTION -> "Subscription"
        ReminderKind.PLACE -> "Place reminder"
    }

    override val alarmChannelName = "Alarms"
    override val alarmChannelDescription = "Alarms you set. They sound at alarm volume."
    override val timerChannelName = "Timers"
    override val timerChannelDescription = "Countdowns you started, when they run out."

    override val untitledReminderTitle = "Reminder"
    override val alarmTitle = "Alarm"
    override val timerFinishedTitle = "Timer finished"
    override val timerRanOutWhileOff = "Ended while the phone was off"
}

internal object BengaliReminderWording : ReminderWording {

    override val complete = "হয়ে গেছে"
    override val open = "খুলুন"
    override val askMira = "আইদাকে বলুন"
    override val dismiss = "বন্ধ করুন"

    /**
     * Bengali puts the span first and the pushing-away after it — "১০ মিনিট পরে", ten minutes
     * later — rather than translating "snooze", which has no single natural word here.
     */
    override fun snooze(by: Duration): String {
        val hours = by.toHours()
        return if (hours >= 1 && by.minusHours(hours).isZero) {
            "${bengaliNumerals(hours.toString())} ঘণ্টা পরে"
        } else {
            "${bengaliNumerals(by.toMinutes().toString())} মিনিট পরে"
        }
    }

    override fun kindLine(kind: ReminderKind): String? = when (kind) {
        ReminderKind.ONE_TIME -> null
        ReminderKind.RECURRING -> "নিয়মিত রিমাইন্ডার"
        ReminderKind.TASK -> "কাজ"
        ReminderKind.INVOICE -> "ইনভয়েস"
        ReminderKind.CLIENT_FOLLOW_UP -> "যোগাযোগ"
        ReminderKind.BIRTHDAY -> "জন্মদিন"
        ReminderKind.PROJECT_DEADLINE -> "সময়সীমা"
        ReminderKind.SUBSCRIPTION -> "সাবস্ক্রিপশন"
        ReminderKind.PLACE -> "জায়গার রিমাইন্ডার"
    }

    override val alarmChannelName = "অ্যালার্ম"
    override val alarmChannelDescription = "আপনার দেওয়া অ্যালার্ম। অ্যালার্মের ভলিউমে বাজে।"
    override val timerChannelName = "টাইমার"
    override val timerChannelDescription = "আপনার চালু করা টাইমার শেষ হলে।"

    override val untitledReminderTitle = "রিমাইন্ডার"
    override val alarmTitle = "অ্যালার্ম"
    override val timerFinishedTitle = "টাইমার শেষ"
    override val timerRanOutWhileOff = "ফোন বন্ধ থাকা অবস্থায় শেষ হয়েছে"
}

internal fun reminderWording(language: SpokenLanguage): ReminderWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishReminderWording
    SpokenLanguage.BENGALI -> BengaliReminderWording
}
