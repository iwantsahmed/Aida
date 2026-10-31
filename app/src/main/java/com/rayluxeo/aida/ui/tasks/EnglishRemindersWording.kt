package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal

/** The Reminders tab in English. */
internal object EnglishRemindersWording : RemindersWording {

    override fun kindLabel(kind: ReminderKind): String = when (kind) {
        ReminderKind.ONE_TIME -> "Reminder"
        ReminderKind.RECURRING -> "Repeating"
        ReminderKind.TASK -> "Task"
        ReminderKind.INVOICE -> "Invoice"
        ReminderKind.CLIENT_FOLLOW_UP -> "Follow-up"
        ReminderKind.BIRTHDAY -> "Birthday"
        ReminderKind.PROJECT_DEADLINE -> "Deadline"
        ReminderKind.SUBSCRIPTION -> "Subscription"
        ReminderKind.PLACE -> "Place"
    }

    override fun at(moment: String): String = "At $moment"

    override fun snoozedUntil(moment: String): String = "Snoozed until $moment"

    override fun waitingAt(place: String): String = "Waiting at $place"

    override val notBeingWatched: String = "not being watched"

    override fun placesNotWatched(count: Int): String = if (count == 1) {
        "One place reminder has nothing watching for it."
    } else {
        "$count place reminders have nothing watching for them."
    }

    override val watchAgain: String = "Watch again"

    override val repeats: String = "Repeats"

    override val unsnooze: String = "Un-snooze"

    override val snoozeCalledOff: String = "Back on its own time."

    override val cancelTitle: String = "Cancel this reminder?"

    override fun cancelMessage(title: String): String =
        "$title will not go off. Cancelling it cannot be undone."

    override val cancelConfirm: String = "Cancel it"

    override val keepReminder: String = "Keep it"

    override fun placeRefusal(refusal: PlaceWatchRefusal): String = when (refusal) {
        PlaceWatchRefusal.PERMISSION_MISSING ->
            "Aida cannot watch for a place until you allow her to see this phone's location."
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING ->
            "Android only lets Aida watch for a place while she is open. Allowing location all " +
                "the time lets her tell you when you arrive."
        PlaceWatchRefusal.LOCATION_DISABLED ->
            "Location is switched off on this phone, so nothing can tell Aida you have arrived."
        PlaceWatchRefusal.NO_PROVIDER ->
            "This phone has no location provider Aida can use, so place reminders cannot work here."
        PlaceWatchRefusal.NO_FIX_YET ->
            "Aida could not work out where you are just now. It usually works near a window or " +
                "outdoors."
        PlaceWatchRefusal.LIMIT_REACHED ->
            "Aida is already watching as many places as Android will hold. Remove one to add this."
        PlaceWatchRefusal.BATTERY_RESTRICTED ->
            "Android has restricted Aida in the background to save battery, so she may miss your " +
                "arrival. Allowing her to run unrestricted fixes it."
    }

    override fun placeRefusalAction(refusal: PlaceWatchRefusal): String? = when (refusal) {
        PlaceWatchRefusal.PERMISSION_MISSING -> "Allow location"
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING -> "Allow all the time"
        PlaceWatchRefusal.LOCATION_DISABLED -> "Open location settings"
        PlaceWatchRefusal.BATTERY_RESTRICTED -> "Open battery settings"
        // A real retry, not a settings trip — nothing on the phone is wrong.
        PlaceWatchRefusal.NO_FIX_YET -> "Try again"
        PlaceWatchRefusal.NO_PROVIDER, PlaceWatchRefusal.LIMIT_REACHED -> null
    }

    override val watchingAgain: String = "Watching those places again."
}
