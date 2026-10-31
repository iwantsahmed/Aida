package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal

/**
 * Every word the Reminders tab puts in front of Abu, in one language.
 *
 * Held apart from [TasksWording] because the two halves of that screen answer different questions
 * and have different vocabularies: a reminder has a kind, a snooze and sometimes a place, none of
 * which a task has. Putting eight kind labels and six refusal sentences into the tasks phrasebook
 * would have made the file that words a list of tasks mostly about something else.
 *
 * The refusal sentences are the part that earns its keep. Every [PlaceWatchRefusal] gets its own
 * line, because a screen that answers "could not watch that place" to six different situations has
 * told Abu nothing: a permission he has not granted is something he can fix in a tap, a device
 * with no location provider is not, and a full register is Mira's problem rather than his.
 * [placeRefusalAction] is null for exactly the two he cannot act on, so no button is offered that
 * would do nothing.
 */
internal interface RemindersWording {

    /** The word for each kind, shown as a chip on the row. */
    fun kindLabel(kind: ReminderKind): String

    /** Frames an already-worded moment: "Tomorrow, 9 am" becomes "At tomorrow, 9 am". */
    fun at(moment: String): String

    fun snoozedUntil(moment: String): String

    /** A place reminder has no moment; it says where it is waiting instead. */
    fun waitingAt(place: String): String

    /** A place reminder Mira is holding but the OS is not. Said plainly, not as an error. */
    val notBeingWatched: String

    /** One line, shown once, when any place reminder has no alert behind it. */
    fun placesNotWatched(count: Int): String

    val watchAgain: String

    /** Shown on a repeating reminder, so a row that comes back says so. */
    val repeats: String

    /** Puts a snoozed reminder back on its own moment. Only offered for one that is snoozed. */
    val unsnooze: String

    val snoozeCalledOff: String

    /**
     * The question asked before a reminder is called off.
     *
     * Cancelling disarms the alarm and drops the row, and nothing puts either back. Every other
     * destructive act on this screen answers with an undo; this one cannot, so it asks first.
     */
    val cancelTitle: String

    /** Names the reminder that is about to go, and says plainly that it will not come back. */
    fun cancelMessage(title: String): String

    /** The confirm on that question. It says what it does rather than agreeing with a title. */
    val cancelConfirm: String

    /**
     * The way out of that question. Worded as keeping the reminder rather than as "cancel",
     * which on a dialog about cancelling something would be two opposite answers to one word.
     */
    val keepReminder: String

    fun placeRefusal(refusal: PlaceWatchRefusal): String

    /**
     * The way out of a refusal, or null when there is none. A device with no location provider is
     * not something Abu can fix, and a register Mira has filled is hers to empty.
     */
    fun placeRefusalAction(refusal: PlaceWatchRefusal): String?

    /** Said once when every place Mira was holding is being watched again. */
    val watchingAgain: String
}

internal fun remindersWording(language: SpokenLanguage): RemindersWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliRemindersWording
    SpokenLanguage.ENGLISH -> EnglishRemindersWording
}

/**
 * One refusal, as the line Abu reads and the button that answers it.
 *
 * The action is present exactly when [RemindersWording.placeRefusalAction] has something to
 * offer, so the notice can never show a button that would do nothing.
 *
 * [RemindersPlaceNotice.settingsFor] is narrower than the action, and the two are NOT the same
 * question. A button that says "try again" is a real action with no settings screen behind it,
 * and filling `settingsFor` from the action alone would send Abu to the app details page to fix
 * a phone that has nothing wrong with it.
 */
internal fun PlaceWatchRefusal.toNotice(words: RemindersWording): RemindersPlaceNotice {
    val action = words.placeRefusalAction(this)
    return RemindersPlaceNotice(
        message = words.placeRefusal(this),
        actionText = action,
        settingsFor = this.takeIf { action != null && it.opensSettings },
    )
}
