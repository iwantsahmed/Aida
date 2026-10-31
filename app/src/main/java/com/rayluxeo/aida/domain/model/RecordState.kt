package com.rayluxeo.aida.domain.model

/**
 * The states Mira's records move through.
 *
 * Every one of these is persisted by NAME rather than ordinal, so reordering a constant here can
 * never silently relabel Abu's existing rows. An unrecognised name decays to the listed default
 * rather than throwing: one bad row must not take a whole list down.
 */

/** Where a project stands. Default: [ACTIVE]. */
enum class ProjectStatus { PLANNED, ACTIVE, ON_HOLD, COMPLETED, CANCELLED }

/** Where an invoice stands. Default: [DRAFT]. Nothing but a payment moves it to [PAID]. */
enum class InvoiceStatus { DRAFT, READY, SENT, PARTIALLY_PAID, PAID, OVERDUE, VOID }

/** Where a quote stands. Default: [DRAFT]. */
enum class QuoteStatus { DRAFT, SENT, ACCEPTED, DECLINED, EXPIRED }

/** How money arrived. Default: [OTHER]. */
enum class PaymentMethod { CASH, BANK_TRANSFER, MOBILE_WALLET, CARD, CHEQUE, OTHER }

/** How often a subscription charges. Default: [MONTHLY]. */
enum class SubscriptionCycle { WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM_DAYS }

/** What an expense was for. Default: [OTHER]. */
enum class ExpenseCategory {
    SOFTWARE, HARDWARE, TRAVEL, FOOD, RENT, UTILITIES, MARKETING, PROFESSIONAL_FEES, TAX, OTHER
}

/** Where a timer stands. Default: [FINISHED], because an unknown timer must not look live. */
enum class TimerState { RUNNING, PAUSED, FINISHED, CANCELLED }

/** Where an OS alarm stands. Default: [SCHEDULED]. */
enum class AlarmState { SCHEDULED, FIRED, CANCELLED, MISSED }

/** How Mira came to hold a fact. Default: [INFERRED], the weakest claim. */
enum class MemorySource { SPOKEN, TYPED, INFERRED, IMPORTED }

/** Where a file on disk came from. Default: [IMPORTED]. */
enum class FileSource { SCAN, IMPORT, RECORDING, EXPORT, GENERATED }

/** What fires an automation. Default: [MANUAL], which cannot run on its own. */
enum class AutomationTrigger {
    MANUAL, TIME_OF_DAY, INTERVAL, PLACE, CALENDAR_EVENT,
    DATE, DAY_OF_WEEK, TASK_DUE, TASK_OVERDUE,
    INVOICE_DUE, INVOICE_OVERDUE, SUBSCRIPTION_RENEWAL, PROJECT_DEADLINE, APP_START,
    POWER_CONNECTED, POWER_DISCONNECTED,
    // Appended, never reordered — the enum is persisted by name (see the file KDoc).
    NOTIFICATION_POSTED
}

/** How an automation run ended. Default: [FAILED], so an unfinished run never reads as success. */
enum class AutomationOutcome { SUCCESS, FAILED, SKIPPED }

/** What kind of thing happened, for the activity log. Default: [OTHER]. */
enum class ActivityCategory { TASK, REMINDER, NOTE, MONEY, CLIENT, PROJECT, FILE, VOICE, SETTING, AUTOMATION, BACKUP, OTHER }
