package com.rayluxeo.aida.domain.scheduler

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.BriefNotificationText
import com.rayluxeo.aida.domain.model.ReminderKind

/**
 * A button on a reminder's notification.
 *
 * The set is fixed here rather than at each call site because which buttons a reminder gets is a
 * property of what it is about, and offering one that cannot work is the defect this whole file
 * exists to prevent.
 */
enum class ReminderNotificationAction {
    COMPLETE,
    SNOOZE_TEN_MINUTES,
    SNOOZE_ONE_HOUR,

    /** Opens the record the reminder is about. Tapping the notification itself does the same. */
    OPEN,

    /** Opens Mira with the reminder as context, for anything the fixed buttons cannot do. */
    ASK_MIRA,
    ;

    companion object {

        /**
         * Android draws three actions and quietly drops the rest, so Mira chooses which three
         * rather than letting the system decide which two of five survive.
         */
        const val MAX_SHOWN = 3

        /**
         * Which buttons a reminder of this [kind] can honestly offer.
         *
         * Complete appears only where there is something to complete: on a birthday it means
         * nothing, and on a task reminder whose task has been deleted there is no longer a row to
         * mark done. A place reminder gets no time snooze at all — pushing it ten minutes into the
         * future would not re-arm the place, so the button would be a lie.
         */
        fun forKind(
            kind: ReminderKind,
            hasLinkedTask: Boolean,
        ): List<ReminderNotificationAction> = when (kind) {
            ReminderKind.ONE_TIME,
            ReminderKind.RECURRING,
            -> listOf(SNOOZE_TEN_MINUTES, SNOOZE_ONE_HOUR, ASK_MIRA)

            ReminderKind.TASK ->
                if (hasLinkedTask) listOf(COMPLETE, SNOOZE_TEN_MINUTES, OPEN)
                else listOf(SNOOZE_TEN_MINUTES, SNOOZE_ONE_HOUR, ASK_MIRA)

            ReminderKind.INVOICE,
            ReminderKind.CLIENT_FOLLOW_UP,
            ReminderKind.BIRTHDAY,
            ReminderKind.PROJECT_DEADLINE,
            ReminderKind.SUBSCRIPTION,
            -> listOf(OPEN, SNOOZE_ONE_HOUR, ASK_MIRA)

            ReminderKind.PLACE ->
                if (hasLinkedTask) listOf(COMPLETE, OPEN, ASK_MIRA)
                else listOf(OPEN, ASK_MIRA)
        }
    }
}

/**
 * Every word a reminder, an alarm or a finished timer puts on the notification shade.
 *
 * A port in `domain` with its implementation in `ui/response`, for the same reason
 * [com.rayluxeo.aida.domain.brief.BriefNotificationWording] is one: the code that posts these
 * notifications lives in `data/scheduler`, and nothing in `data` may reach into `ui`. Without the
 * port the notification would either import the presentation layer or grow a second copy of Mira's
 * wording, and the second copy is how a button comes to say one thing in the shade and another on
 * screen.
 *
 * Everything here arrives already in Abu's language, the action labels included. Buttons in
 * English on a Bengali phone are a shipped defect, not a rough edge.
 *
 * Where a [BriefNotificationText] is returned, an empty `body` means the title already says the
 * whole of it and the notification carries no second line.
 */
interface ReminderNotificationWording {

    /** What the button says. Short: Android truncates an action label without mercy. */
    fun actionLabel(action: ReminderNotificationAction, language: SpokenLanguage): String

    /**
     * The line under the title naming what sort of reminder this is — "Invoice", "Birthday".
     *
     * Null where the title already says everything, because a second line repeating the first is
     * noise in a shade Abu is scanning.
     */
    fun kindLine(kind: ReminderKind, language: SpokenLanguage): String?

    /**
     * What to call a reminder that arrived without a title.
     *
     * A reminder's title travels in the intent extras so that it survives a database Mira cannot
     * open, which means "no title" is a state that genuinely reaches the notification. It used to
     * be answered by a hard-coded English `"Reminder"` in the receiver — a word in one language on
     * a path Abu reads, which §12 forbids. It belongs here because this is the port that already
     * knows his language.
     *
     * Singular on purpose: the channel's own name is the plural "Reminders", and naming one
     * notification after the channel it arrived on reads like a category rather than a thing.
     */
    fun untitledReminder(language: SpokenLanguage): String

    /** The alarm channel's name and description, which Android draws in its own settings screen. */
    fun alarmChannel(language: SpokenLanguage): BriefNotificationText

    fun timerChannel(language: SpokenLanguage): BriefNotificationText

    /** [label] is what Abu called the alarm, and is empty for one he never named. */
    fun alarm(label: String, language: SpokenLanguage): BriefNotificationText

    /**
     * A countdown that has run out.
     *
     * [ranOutWhileOff] is the reboot case: the timer ended while the phone was off and Mira is
     * reporting it late. Saying so is the difference between a timer that just finished and one
     * Abu missed an hour ago, and he acts differently on each.
     */
    fun timerFinished(
        label: String,
        ranOutWhileOff: Boolean,
        language: SpokenLanguage,
    ): BriefNotificationText

    /** The button that stops an alarm or a finished timer making noise. */
    fun dismiss(language: SpokenLanguage): String
}
