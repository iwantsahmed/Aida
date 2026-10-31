package com.rayluxeo.aida.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Something Mira does on her own.
 *
 * The stored row splits a trigger in two — a discriminator column and a JSON string of settings —
 * because a place trigger and a time trigger share no fields. The domain deliberately does not
 * repeat that split: [trigger] is one value carrying both, so a time-of-day automation that also
 * holds a latitude cannot be constructed here at all.
 */
data class Automation(
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val trigger: AutomationTriggerConfig,
    val action: AutomationAction,
    /** Off until Abu turns it on. A newly written automation never starts running by itself. */
    val isEnabled: Boolean = false,
    val lastRunAt: Instant? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * What makes an automation fire.
 */
sealed interface AutomationTriggerConfig {

    val kind: AutomationTrigger

    data object Manual : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.MANUAL
    }

    data class TimeOfDay(
        val minuteOfDay: Int,
        val daysOfWeek: Set<DayOfWeek> = emptySet(),
    ) : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.TIME_OF_DAY
    }

    data class Interval(val everyMinutes: Int) : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.INTERVAL
    }

    data class Place(
        val label: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMetres: Int,
    ) : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.PLACE
    }

    data class CalendarEvent(
        val minutesBefore: Int,
        val titleContains: String? = null,
    ) : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.CALENDAR_EVENT
    }

    /**
     * Another app posted a notification, seen through the opt-in notification-listener service. Both
     * filters are optional and narrow which notifications fire the rule: [packageName] to one app,
     * [titleContains] to a phrase in the title. The title is read only to decide [matches] and is
     * never stored or sent anywhere (docs/PRIVACY.md §6.5).
     */
    data class NotificationPosted(
        val packageName: String? = null,
        val titleContains: String? = null,
    ) : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.NOTIFICATION_POSTED

        /** Whether a notification from [fromPackage] carrying [title] should fire this rule. */
        fun matches(fromPackage: String, title: String?): Boolean {
            if (packageName != null && !packageName.equals(fromPackage, ignoreCase = true)) return false
            val needle = titleContains?.trim()
            if (!needle.isNullOrEmpty() && (title == null || !title.contains(needle, ignoreCase = true))) return false
            return true
        }
    }

    data class DateTrigger(
        val date: LocalDate,
        val minuteOfDay: Int,
    ) : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.DATE
    }

    data class DayOfWeekTrigger(
        val dayOfWeek: DayOfWeek,
        val minuteOfDay: Int,
    ) : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.DAY_OF_WEEK
    }

    data object TaskDue : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.TASK_DUE
    }

    data object TaskOverdue : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.TASK_OVERDUE
    }

    data object InvoiceDue : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.INVOICE_DUE
    }

    data object InvoiceOverdue : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.INVOICE_OVERDUE
    }

    data object SubscriptionRenewal : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.SUBSCRIPTION_RENEWAL
    }

    data object ProjectDeadline : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.PROJECT_DEADLINE
    }

    data object AppStart : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.APP_START
    }

    /** The charger was plugged in. A device event, delivered even while Mira is closed. */
    data object PowerConnected : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.POWER_CONNECTED
    }

    /** The charger was unplugged. */
    data object PowerDisconnected : AutomationTriggerConfig {
        override val kind: AutomationTrigger = AutomationTrigger.POWER_DISCONNECTED
    }
}

/**
 * What an automation does when it fires.
 */
sealed interface AutomationAction {

    data class RunUtterance(val utterance: String) : AutomationAction

    data class Notify(val title: String, val body: String? = null) : AutomationAction

    /**
     * Opens a link on the device — a web page, or any `scheme://` another app registers. Mira reaching
     * out of her own window is the one cross-app action here; the link is shown, never sent silently.
     */
    data class OpenLink(val url: String) : AutomationAction

    /**
     * A system-wide gesture — back, home, recents — performed through the opt-in accessibility
     * service, which Mira uses as a pure actuator: it presses the button but never reads the screen
     * (see [GlobalDeviceAction]). When the service is off the action is a visible *failed run*, never
     * a silent no-op (§12).
     */
    data class PerformGlobalAction(val action: GlobalDeviceAction) : AutomationAction

    /**
     * Brings another app forward by its package. Like [OpenLink] this reaches out of Mira's own
     * window, and like it the app is only launched, never driven — [label] is what Abu reads back, the
     * package is what the OS launches.
     */
    data class LaunchApp(val packageName: String, val label: String) : AutomationAction

    data class CreateTask(
        val title: String,
        val priority: TaskPriority = TaskPriority.NORMAL,
    ) : AutomationAction

    data class CreateReminder(
        val title: String,
        val offsetMinutes: Int = 0,
    ) : AutomationAction

    data class PrepareFollowUp(
        val targetName: String,
        val notes: String? = null,
    ) : AutomationAction

    data class PrepareInvoiceReminder(
        val invoiceNumber: String,
    ) : AutomationAction

    data class ChangeTaskPriority(
        val taskId: Long,
        val priority: TaskPriority,
    ) : AutomationAction

    data object GenerateDailyBrief : AutomationAction

    data object GenerateEveningReview : AutomationAction
}
