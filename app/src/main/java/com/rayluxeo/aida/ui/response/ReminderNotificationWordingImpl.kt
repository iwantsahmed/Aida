package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.BriefNotificationText
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.scheduler.ReminderNotificationAction
import com.rayluxeo.aida.domain.scheduler.ReminderNotificationWording
import com.rayluxeo.aida.domain.usecase.SnoozeReminderUseCase
import javax.inject.Inject

/**
 * The notification's words, taken from the one place Mira keeps them.
 *
 * This exists so the code that posts a notification, which lives in `data/scheduler`, can reach
 * Mira's wording without `data` importing `ui` (§4). It holds no sentences of its own: every line
 * comes from [ReminderWording], exactly as [BriefNotificationWordingImpl] takes its lines from
 * [BriefNarrator].
 *
 * The two snooze labels are rendered from [SnoozeReminderUseCase.SHORT] and
 * [SnoozeReminderUseCase.LONG] rather than from a number typed into the words, so the span on the
 * button and the span the reminder is actually pushed away by cannot drift apart.
 */
class ReminderNotificationWordingImpl @Inject constructor() : ReminderNotificationWording {

    override fun actionLabel(
        action: ReminderNotificationAction,
        language: SpokenLanguage,
    ): String {
        val words = reminderWording(language)
        return when (action) {
            ReminderNotificationAction.COMPLETE -> words.complete
            ReminderNotificationAction.SNOOZE_TEN_MINUTES -> words.snooze(SnoozeReminderUseCase.SHORT)
            ReminderNotificationAction.SNOOZE_ONE_HOUR -> words.snooze(SnoozeReminderUseCase.LONG)
            ReminderNotificationAction.OPEN -> words.open
            ReminderNotificationAction.ASK_MIRA -> words.askMira
        }
    }

    override fun kindLine(kind: ReminderKind, language: SpokenLanguage): String? =
        reminderWording(language).kindLine(kind)

    /**
     * What a reminder that reached the shade with no title of its own is called.
     *
     * It has a word rather than borrowing the channel's name because the channel is the plural
     * "Reminders", and one reminder headed that way reads as a list Abu does not have.
     */
    override fun untitledReminder(language: SpokenLanguage): String =
        reminderWording(language).untitledReminderTitle

    override fun alarmChannel(language: SpokenLanguage): BriefNotificationText {
        val words = reminderWording(language)
        return BriefNotificationText(words.alarmChannelName, words.alarmChannelDescription)
    }

    override fun timerChannel(language: SpokenLanguage): BriefNotificationText {
        val words = reminderWording(language)
        return BriefNotificationText(words.timerChannelName, words.timerChannelDescription)
    }

    /**
     * A named alarm leads with its name, because that is what Abu set it for; the word "alarm"
     * then becomes the second line rather than a heading he already knows.
     *
     * The unnamed branch is reached in earnest: "wake me up at seven" names no subject the way a
     * reminder always does, so a blank label is an ordinary alarm rather than a missing one. It is
     * headed with the word for an alarm in Abu's language, never with the app's own name.
     */
    override fun alarm(label: String, language: SpokenLanguage): BriefNotificationText {
        val words = reminderWording(language)
        val named = label.trim()
        return if (named.isEmpty()) {
            BriefNotificationText(words.alarmTitle, "")
        } else {
            BriefNotificationText(named, words.alarmTitle)
        }
    }

    override fun timerFinished(
        label: String,
        ranOutWhileOff: Boolean,
        language: SpokenLanguage,
    ): BriefNotificationText {
        val words = reminderWording(language)
        val named = label.trim()
        val title = if (named.isEmpty()) words.timerFinishedTitle else named
        val line = when {
            ranOutWhileOff -> words.timerRanOutWhileOff
            named.isEmpty() -> ""
            else -> words.timerFinishedTitle
        }
        return BriefNotificationText(title, line)
    }

    override fun dismiss(language: SpokenLanguage): String = reminderWording(language).dismiss
}
