package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.usecase.WidgetGlance
import java.time.Instant

/**
 * The finished lines a glance widget draws, worded in Abu's language.
 *
 * [tasks] is always present (the quiet-day line when there is nothing); the other three drop their
 * line entirely when there is nothing to say rather than printing a zero or a blank.
 */
data class WidgetGlanceLines(
    val tasks: String,
    val money: String?,
    val nextEvent: String?,
    val nextReminder: String?,
)

/**
 * Words a [WidgetGlance] for the home screen, borrowing the brief's own phrasebook so the numbers
 * and the way they are said match Home and the morning brief exactly — a glance that rounded or
 * grouped a figure differently from the card it mirrors would read as a second, untrusted app.
 *
 * Pure (no Android, no clock): the moment of an event or reminder arrives already formatted through
 * [momentLabel], which the worker builds from the injected clock, so this stays plain-JUnit testable.
 * [includeMoney] is false when Abu's vault flags protect Money/Invoices — the figure the lock exists
 * to hide is not leaked onto an unlocked home screen (see PRIVACY.md).
 */
internal fun widgetGlanceLines(
    glance: WidgetGlance,
    language: SpokenLanguage,
    includeMoney: Boolean,
    momentLabel: (Instant) -> String,
): WidgetGlanceLines {
    val words = briefWording(language)
    val tasks = if (glance.openTaskCount > 0 || glance.dueTodayCount > 0 || glance.overdueCount > 0) {
        words.taskSentence(
            dueToday = glance.dueTodayCount,
            overdue = glance.overdueCount,
            open = glance.openTaskCount,
        )
    } else {
        words.quietDay
    }
    val money = if (includeMoney && glance.outstanding != null) {
        words.moneySentence(outstanding = glance.outstanding, overdue = glance.overdue)
    } else {
        null
    }
    val nextEvent = glance.nextEvent?.let { words.nextEventSentence(it.title, momentLabel(it.startsAt)) }
    val nextReminder = glance.nextReminder?.let {
        words.reminderSentence(count = 1, title = it.title, moment = momentLabel(it.triggerAt))
    }
    return WidgetGlanceLines(
        tasks = tasks,
        money = money,
        nextEvent = nextEvent,
        nextReminder = nextReminder,
    )
}
