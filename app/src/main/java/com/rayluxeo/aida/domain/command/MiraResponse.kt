package com.rayluxeo.aida.domain.command

import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.CalendarEvent
import com.rayluxeo.aida.domain.model.Contact
import com.rayluxeo.aida.domain.model.ContactField
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.MessagingApp
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.TimeWindow
import com.rayluxeo.aida.domain.salah.Prayer
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * What Mira has to say, as data rather than prose. The presentation layer renders these
 * into Bengali or English, so the domain never hard-codes a language.
 */
sealed interface MiraResponse {

    // Work
    /**
     * [repeats] is carried so Mira can say she set up a repeat rather than a single task. Without
     * it "every Monday" and "next Monday" get the same reply, and Abu cannot tell which he got.
     */
    data class TaskCreated(
        val title: String,
        val dueAt: Instant?,
        val repeats: Boolean = false,
    ) : MiraResponse
    data class TaskUpdated(val title: String, val dueAt: Instant?) : MiraResponse
    data class TaskCompleted(val title: String) : MiraResponse
    data class TasksFound(val count: Int, val titles: List<String>) : MiraResponse
    data class ReminderCreated(val title: String, val triggerAt: Instant) : MiraResponse
    /** [repeatDays] empty is a one-time alarm; anything else is read back as the days it rings. */
    data class AlarmSet(
        val label: String,
        val triggerAt: Instant,
        val repeatDays: Set<DayOfWeek> = emptySet(),
    ) : MiraResponse

    /**
     * [isWatching] is false when the place was saved to the reminder but the platform would not
     * hold the alert — a withheld permission, location switched off. Abu is told both halves,
     * because a reminder that is stored but not armed looks identical to one that is.
     */
    data class PlaceReminderCreated(
        val title: String,
        val placeLabel: String,
        val trigger: PlaceTrigger,
        val isWatching: Boolean,
    ) : MiraResponse

    /** Mira was asked about a place she has no coordinate for. [known] is what she can offer. */
    data class PlaceUnknown(val placeLabel: String, val known: List<String>) : MiraResponse
    data class TimerStarted(val label: String, val duration: Duration, val endsAt: Instant) : MiraResponse
    data class BriefReady(val brief: DailyBrief) : MiraResponse

    // Knowledge
    data class NoteCreated(val title: String?) : MiraResponse
    data class NotesFound(val count: Int, val titles: List<String>) : MiraResponse
    data class MemoryStored(val subject: String, val fact: String) : MiraResponse
    data class MemoryForgotten(val memoryId: Long) : MiraResponse
    data class MemoriesFound(val count: Int, val facts: List<String>) : MiraResponse
    data class UniversalSearchResultsFound(val query: String, val totalCount: Int, val topSnippets: List<String>) : MiraResponse

    // Business and money
    data class ClientCreated(val name: String) : MiraResponse
    data class ClientsFound(val count: Int, val names: List<String>) : MiraResponse
    data class InvoiceDraftCreated(val number: String, val clientName: String, val total: Money) : MiraResponse

    /** A draft quote Mira created, read back the same way an invoice draft is. */
    data class QuoteDraftCreated(val number: String, val clientName: String, val total: Money) : MiraResponse

    /**
     * The invoice draft Mira is about to create, read back for a yes. The number is not assigned
     * until she acts, so the confirmation names the client and the amount Abu is approving instead
     * — never a client-search result, which says nothing about the money.
     */
    data class InvoiceDraftRequested(val clientName: String, val amount: Money?, val description: String?) : MiraResponse

    /** The quote draft Mira is about to create, read back for a yes — same shape as the invoice one. */
    data class QuoteDraftRequested(val clientName: String, val amount: Money?, val description: String?) : MiraResponse
    data class RecurringInvoiceScheduled(val invoiceNumber: String, val cycle: com.rayluxeo.aida.domain.model.SubscriptionCycle) : MiraResponse
    data class PaymentRecorded(val amount: Money, val clientName: String?, val invoiceNumber: String?) : MiraResponse

    /**
     * A payment given as a share of an invoice, read back for a yes before the exact figure is
     * worked out against that invoice. The percentage and the invoice are shown, never a ৳0.00 that
     * is not the real amount — in a finance app the read-back must state what Abu is approving.
     */
    data class PaymentByPercentageRequested(
        val percentage: java.math.BigDecimal,
        val clientName: String?,
        val invoiceNumber: String?,
    ) : MiraResponse
    data class ExpenseRecorded(val amount: Money, val description: String) : MiraResponse
    data class IncomeRecorded(val amount: Money, val source: String) : MiraResponse
    data class CalculationResult(val expression: String, val value: String) : MiraResponse
    data class ExpenseSummaryReport(
        val totalSpent: Money,
        val category: com.rayluxeo.aida.domain.model.ExpenseCategory?,
        val count: Int,
    ) : MiraResponse
    data class IncomeSummaryReport(
        val totalReceived: Money,
        val count: Int,
    ) : MiraResponse
    data class SubscriptionRenewalInfo(
        val name: String,
        val nextChargeOn: java.time.LocalDate,
        val amount: Money,
    ) : MiraResponse
    data class InvoiceOutstandingReport(
        val clientName: String,
        val number: String?,
        val outstanding: Money,
        val total: Money,
    ) : MiraResponse
    data class InvoiceDeliveryPrepared(
        val draft: com.rayluxeo.aida.domain.model.InvoiceDeliveryDraft,
    ) : MiraResponse

    /** A client's standing — billed, paid, outstanding — rendered per-language by the phrasebook. */
    data class ClientStatementReport(
        val statement: com.rayluxeo.aida.domain.model.ClientStatement,
    ) : MiraResponse

    // Salah
    /** A prayer logged as prayed. [onDate] null is the ordinary "just now", read back as today. */
    data class PrayerMarked(val prayer: Prayer, val onDate: LocalDate?) : MiraResponse
    data class NextPrayerReport(
        val prayer: Prayer,
        val at: Instant,
        val remaining: Duration,
    ) : MiraResponse
    data class PrayerTimeRemainingReport(
        val prayer: Prayer,
        val at: Instant,
        val remaining: Duration,
    ) : MiraResponse
    data class PrayerTimeReport(
        val prayer: Prayer,
        val at: Instant,
        val onDate: LocalDate?,
    ) : MiraResponse

    /**
     * How Abu's praying stands over a window. [perPrayer] carries the per-prayer completions for a
     * month and is null for a single day, which is what tells the renderer to read back a day's
     * count of five rather than a month's totals.
     */
    data class PrayerTrackingReport(
        val from: LocalDate?,
        val to: LocalDate?,
        val completed: Int,
        val possible: Int,
        val daysAllFive: Int,
        val tahajjudNights: Int,
        val perPrayer: Map<Prayer, Int>?,
    ) : MiraResponse

    /** The Tahajjud reminder is on. [at] is tonight's last-third start, null when no place is set yet. */
    data class TahajjudReminderSet(val at: Instant?) : MiraResponse

    // Conversation management
    data object NewConversationStarted : MiraResponse
    data class ConversationDeleted(val id: Long?) : MiraResponse
    data object AllConversationsDeleted : MiraResponse
    data class ConversationsFound(val count: Int, val snippets: List<String>) : MiraResponse

    // The phone itself
    data class CalendarEventCreated(
        val title: String,
        val startsAt: Instant,
        val conflicts: List<String> = emptyList(),
    ) : MiraResponse
    data class ScheduleReport(
        val from: Instant,
        val to: Instant,
        val events: List<CalendarEvent>,
    ) : MiraResponse
    data class FreeSlotsReport(
        val date: LocalDate,
        val slots: List<TimeWindow>,
    ) : MiraResponse
    data class CalendarEventUpdated(
        val title: String,
        val startsAt: Instant,
    ) : MiraResponse
    data class CalendarEventDeleted(
        val title: String,
    ) : MiraResponse
    data class ContactsFound(val count: Int, val contacts: List<Contact>) : MiraResponse
    data class ContactDetails(
        val contact: Contact,
        val requestedField: ContactField,
    ) : MiraResponse

    /**
     * Mira has the number and is holding it out. The screen turns this into a control Abu
     * presses; nothing dials on its own, because a misheard name would be a call to a stranger.
     */
    data class CallPrepared(val contact: Contact) : MiraResponse
    data class MessagePrepared(
        val contact: Contact,
        val body: String?,
        val app: MessagingApp = MessagingApp.SMS,
    ) : MiraResponse

    // Smarter-assistant verbs
    data class TaskDeleted(val title: String) : MiraResponse
    data class RemindersFound(val count: Int, val titles: List<String>) : MiraResponse
    data class ReminderSnoozed(val title: String, val until: Instant) : MiraResponse
    data class ReminderCancelled(val title: String) : MiraResponse
    data class FactForgotten(val subject: String) : MiraResponse

    // Mira could not finish
    data class AmbiguousTask(val title: String, val candidates: List<String>) : MiraResponse
    data class AmbiguousClient(val query: String, val candidates: List<String>) : MiraResponse
    data class AmbiguousInvoice(val clientName: String?, val candidateNumbers: List<String>) : MiraResponse
    data class AmbiguousContact(val query: String, val candidates: List<Contact>) : MiraResponse
    data class AmbiguousReminder(val query: String, val candidates: List<String>) : MiraResponse
    data class AmbiguousFact(val query: String, val facts: List<String>) : MiraResponse

    /**
     * Mira understood perfectly and cannot act because Android has not let her. Distinct from a
     * [Problem] so the screen can offer the grant rather than only apologising.
     */
    data class PermissionNeeded(val capability: MiraCapability) : MiraResponse

    /**
     * A command Mira read back and Abu waved off. Nothing ran, and saying so is the whole of it —
     * it lives here rather than as words in a view model because both the typed path and the
     * spoken path end this way and they must end it identically.
     */
    data object Abandoned : MiraResponse

    /**
     * Mira greeting Abu back. Carries nothing: the actual words — and whether it is a morning or an
     * evening — are the renderer's to choose from the clock, so the one thing the domain knows is
     * that a greeting is owed.
     */
    data object Greeting : MiraResponse

    /**
     * A cloud-answered reply, carried as the words themselves because they are the model's own,
     * already in Abu's language — the presentation layer speaks and shows them verbatim rather than
     * composing them from a code, which is the one place that holds for a MiraResponse (§4.6).
     */
    data class Conversational(val answer: String) : MiraResponse

    data class NotUnderstood(val input: String) : MiraResponse
    data class Problem(val code: MiraErrorCode) : MiraResponse
}

/** Something Mira can only do once Android has granted it. */
enum class MiraCapability { CONTACTS, CALENDAR, EXACT_ALARMS, NOTIFICATIONS, MICROPHONE }

/**
 * The result of running one request end to end. Each variant carries the language the
 * exchange happened in, so the caller renders the reply in the language Abu actually used.
 */
sealed interface CommandOutcome {
    val response: MiraResponse
    val language: SpokenLanguage

    data class Completed(
        override val response: MiraResponse,
        override val language: SpokenLanguage,
        val engine: EngineId,
    ) : CommandOutcome

    /** Mira understood but wants a yes before acting. Re-submit via [ConfirmationRequest]. */
    data class NeedsConfirmation(
        override val response: MiraResponse,
        override val language: SpokenLanguage,
        val pending: ConfirmationRequest,
    ) : CommandOutcome

    data class Rejected(
        override val response: MiraResponse,
        override val language: SpokenLanguage,
    ) : CommandOutcome
}

data class ConfirmationRequest(
    val command: AppCommand,
    val engine: EngineId,
    val summary: MiraResponse,
)
