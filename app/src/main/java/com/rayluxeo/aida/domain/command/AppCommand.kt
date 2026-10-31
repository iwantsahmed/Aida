package com.rayluxeo.aida.domain.command

import com.rayluxeo.aida.domain.model.ContactField
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.MessagingApp
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The structured verb vocabulary Mira executes. Every request — typed, spoken, Bengali,
 * English or Banglish — is reduced to one of these before anything is written anywhere.
 *
 * This hierarchy contains exactly the commands Mira can actually carry out today. A command is
 * added here only together with a working handler in [CommandDispatcher], a rule in
 * [CommandValidator], and wording in both languages; the planned vocabulary is tracked in
 * docs/FEATURE_CHECKLIST.md, not as empty types.
 */
sealed interface AppCommand {

    /**
     * True when carrying the command out changes stored data. Only mutating commands are
     * ever read back for confirmation — Mira does not ask permission to look something up.
     */
    val isMutating: Boolean

    // ---------------------------------------------------------------- work

    data class CreateTask(
        val title: String,
        val dueAt: Instant? = null,
        val priority: TaskPriority = TaskPriority.NORMAL,
        val notes: String? = null,
        /**
         * Set when Abu asked for it to repeat. "Every Monday remind me to review Rayluxeo" is a
         * repeat he said out loud, and creating it once would be Mira quietly doing less than she
         * was asked.
         */
        val recurrence: RecurrenceRule? = null,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    /**
     * A null field means "leave it as it is", which is why there is no way to clear a due date
     * from here. Removing one is a deletion of information and deserves its own explicit verb
     * rather than being inferred from an absent slot.
     */
    data class UpdateTask(
        val target: TaskTarget,
        val title: String? = null,
        val dueAt: Instant? = null,
        val priority: TaskPriority? = null,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    /** Completion targets a task by id when known, otherwise by fuzzy title match. */
    data class CompleteTask(val target: TaskTarget) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class SearchTasks(val query: String?, val filter: TaskFilter = TaskFilter.OPEN) : AppCommand {
        override val isMutating: Boolean = false
    }

    data class CreateReminder(val title: String, val triggerAt: Instant) : AppCommand {
        override val isMutating: Boolean = true
    }

    /**
     * Distinct from a reminder on purpose. A reminder is about something Abu has to do and
     * carries a subject; an alarm is a time he wants to be woken or interrupted at, and its
     * label is incidental. They are stored in different tables for the same reason.
     *
     * [triggerAt] is read for its time of day, not kept as a moment. An alarm clock means seven
     * o'clock wherever Abu is, so the handler reduces this to a `LocalTime` against his zone and
     * the alarm is stored that way — see `Alarm`. It stays an `Instant` here because that is what
     * the date parser produces and because "is that in the past" is a question only a moment can
     * answer.
     *
     * [repeatDays] empty is a one-time alarm. The days come from the same
     * `RecurrenceExpressionParser` tasks read their repeat from; only the shapes an alarm clock
     * can actually hold — daily, weekdays, named weekdays — reach here, because "every month at
     * seven" is a recurring reminder and not an alarm.
     */
    data class CreateAlarm(
        val label: String,
        val triggerAt: Instant,
        val repeatDays: Set<DayOfWeek> = emptySet(),
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    /**
     * A reminder that waits at a place instead of at a time.
     *
     * [placeLabel] is what Abu called the place in the sentence, not a coordinate. Mira has no
     * geocoder, so the handler resolves it against the places he has saved and refuses with
     * `PLACE_UNKNOWN` when it names nowhere she knows — naming the places she does know, so the
     * refusal is something he can act on rather than a blank miss.
     */
    data class CreatePlaceReminder(
        val title: String,
        val placeLabel: String,
        val trigger: PlaceTrigger,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class CreateTimer(val label: String, val duration: Duration) : AppCommand {
        override val isMutating: Boolean = true
    }

    data object GetDailyBrief : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * A greeting or a scrap of small talk — "hey", "assalamu alaikum", "kemon acho" — that asks
     * for nothing but an answer. It changes no state, so it never confirms; Mira simply says hello
     * back rather than telling Abu she did not understand, which is the one reply a greeting must
     * never get.
     */
    data object Greet : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * A plain answer Mira speaks back, for a question the on-device engine could not turn into any
     * command of its own. The cloud engine (Gemini) is the only thing that ever proposes this: it
     * is still confined to proposing an [AppCommand] and touches no storage, so a conversational
     * reply changes nothing and reads nothing — it carries only the words to say. It is read-only,
     * so Mira never holds it for confirmation, and it never reaches her without a validated network
     * and a key, exactly like every other cloud result.
     */
    data class Conversational(val answer: String) : AppCommand {
        override val isMutating: Boolean = false
    }

    // ---------------------------------------------------------------- knowledge

    data class CreateNote(val title: String?, val body: String) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class SearchNotes(val query: String) : AppCommand {
        override val isMutating: Boolean = false
    }

    /** [subject] is what the fact is about, so recall can match on that before its wording. */
    data class StoreMemory(val subject: String, val fact: String) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class ForgetMemory(val memoryId: Long) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class SearchMemory(val query: String) : AppCommand {
        override val isMutating: Boolean = false
    }

    data class UniversalSearch(val query: String) : AppCommand {
        override val isMutating: Boolean = false
    }

    // ---------------------------------------------------------------- business

    data class CreateClient(
        val name: String,
        val companyName: String? = null,
        val phone: String? = null,
        val email: String? = null,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class SearchClients(val query: String) : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * A draft, never a sent invoice. Speaking an invoice into existence is useful; speaking one
     * into Abu's client's inbox is not, so this always lands in DRAFT for him to check.
     */
    data class CreateInvoiceDraft(
        val client: ClientTarget,
        val amount: Money?,
        val description: String?,
        val dueOn: LocalDate? = null,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    /**
     * A draft quote — the offer before the bill. Like [CreateInvoiceDraft] it lands as a DRAFT Abu
     * prices and sends himself: speaking a price into a client's inbox is not wanted, and a quote is
     * the evidence of what was offered, so it is his to check first. It becomes an invoice later
     * through the existing quote→invoice conversion.
     */
    data class CreateQuoteDraft(
        val client: ClientTarget,
        val amount: Money?,
        val description: String?,
        val validUntil: LocalDate? = null,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class CreateRecurringInvoice(
        val sourceInvoiceNumber: String? = null,
        val cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    // ---------------------------------------------------------------- money

    data class RecordPayment(
        val amount: Money? = null,
        val percentage: BigDecimal? = null,
        val client: ClientTarget? = null,
        val invoiceNumber: String? = null,
        val method: PaymentMethod = PaymentMethod.OTHER,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class RecordExpense(
        val amount: Money,
        val description: String,
        val category: ExpenseCategory = ExpenseCategory.OTHER,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class RecordIncome(
        val amount: Money,
        val source: String,
        val client: ClientTarget? = null,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class PerformCalculation(val expression: String) : AppCommand {
        override val isMutating: Boolean = false
    }

    data class QueryExpenses(
        val category: ExpenseCategory? = null,
        val from: LocalDate? = null,
        val to: LocalDate? = null,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * How much came in over a window. Unlike [QueryExpenses] there is no category — income is not
     * categorised — and like it the window is left open when Abu named none, for the handler to
     * default to the current month from the clock.
     */
    data class QueryIncome(
        val from: LocalDate? = null,
        val to: LocalDate? = null,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    data class QuerySubscription(
        val name: String? = null,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    // ---------------------------------------------------------------- salah

    /**
     * Abu logging that he has prayed an obligatory prayer — or Tahajjud, which is tracked apart
     * from the five. [onDate] is null for the ordinary case of "I just prayed Asr", which the
     * handler reads as today; a day Abu names ("I prayed Fajr yesterday") sets it. Mutating,
     * because it writes a tracking record.
     */
    data class MarkPrayerPrayed(
        val prayer: Prayer,
        val onDate: LocalDate? = null,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    /** "When is the next prayer?" — the countdown Mira answers from the on-device times. */
    data object QueryNextPrayer : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * How long is left until a prayer. [prayer] null means the one coming up; a named prayer asks
     * about that one specifically ("how long until Maghrib").
     */
    data class QueryPrayerTimeRemaining(
        val prayer: Prayer? = null,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    /** The clock time of a named prayer on a day ("tomorrow's Fajr time"). */
    data class QueryPrayerTime(
        val prayer: Prayer,
        val onDate: LocalDate? = null,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * How Abu's praying stands over a window. Like [QueryIncome] the window is left open when he
     * named none, for the handler to default to the current month from the clock; a single day is
     * carried as [from]..[from]+1 and read back as a day's count rather than a month's totals.
     */
    data class QueryPrayerTracking(
        val from: LocalDate? = null,
        val to: LocalDate? = null,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * Turn the Tahajjud reminder on for the last third of the night. Mutating, because it writes a
     * standing setting and re-arms the night alarm.
     */
    data object SetTahajjudReminder : AppCommand {
        override val isMutating: Boolean = true
    }

    // ---------------------------------------------------------------- the phone itself

    data class CreateCalendarEvent(
        val title: String,
        val startsAt: Instant,
        val endsAt: Instant? = null,
        val reminderMinutes: Int? = 15,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class QuerySchedule(
        val from: Instant,
        val to: Instant,
        val query: String? = null,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    data class FindFreeSlots(
        val date: LocalDate,
        val preferredDurationMinutes: Int = 30,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    data class UpdateCalendarEvent(
        val targetTitle: String,
        val newTitle: String? = null,
        val newStartsAt: Instant? = null,
        val newEndsAt: Instant? = null,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class DeleteCalendarEvent(
        val targetTitle: String,
    ) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class FindContact(
        val query: String,
        val requestedField: ContactField = ContactField.ANY,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * Prepares, never places. Mira resolves the number and offers it; the call is Abu's to make,
     * because a misheard name that dials on its own is a phone call to a stranger.
     */
    data class PrepareCall(val contact: ContactTarget) : AppCommand {
        override val isMutating: Boolean = false
    }

    /** Same rule as [PrepareCall]: the message is composed and handed over, never sent. */
    data class PrepareMessage(
        val contact: ContactTarget,
        val body: String?,
        val app: MessagingApp = MessagingApp.SMS,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    data class QueryInvoice(
        val client: ClientTarget? = null,
        val invoiceNumber: String? = null,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * Read-only: a client's standing at a glance — what has been billed to them, what they have
     * paid, and what is still owed. The spoken counterpart to opening a client and totalling their
     * invoices by hand.
     */
    data class QueryClientStatement(
        val client: ClientTarget,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    data class DeliverInvoice(
        val invoiceNumber: String? = null,
        val client: ClientTarget? = null,
        val method: com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod = com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod.SYSTEM_SHARE,
    ) : AppCommand {
        override val isMutating: Boolean = false
    }

    data object NewConversation : AppCommand {
        override val isMutating: Boolean = true
    }

    data class DeleteConversation(val id: Long? = null) : AppCommand {
        override val isMutating: Boolean = true
    }

    data object DeleteAllConversations : AppCommand {
        override val isMutating: Boolean = true
    }

    data class SearchConversations(val query: String) : AppCommand {
        override val isMutating: Boolean = false
    }

    /**
     * Sends a task to the bin. Unlike [DeleteCalendarEvent], this does not force a confirmation: the
     * task is recoverable by `RestoreTaskUseCase` (the same bin Tasks shows), so a misheard delete
     * costs a tap to undo, not the record itself.
     */
    data class DeleteTask(val target: TaskTarget) : AppCommand {
        override val isMutating: Boolean = true
    }

    /** Read-only: what reminders are coming up, optionally narrowed by [query]. */
    data class QueryReminders(val query: String? = null) : AppCommand {
        override val isMutating: Boolean = false
    }

    data class SnoozeReminder(val target: ReminderTarget, val by: Duration) : AppCommand {
        override val isMutating: Boolean = true
    }

    data class CancelReminder(val target: ReminderTarget) : AppCommand {
        override val isMutating: Boolean = true
    }

    /** Forgets a stored fact Abu names in words — the spoken counterpart to [ForgetMemory] by id. */
    data class ForgetFact(val query: String) : AppCommand {
        override val isMutating: Boolean = true
    }
}

/** How a command points at a task. */
sealed interface TaskTarget {
    data class ById(val id: Long) : TaskTarget
    data class ByTitle(val title: String) : TaskTarget
}

/** How a command points at a reminder. Only by title today; an id form can join it later. */
sealed interface ReminderTarget {
    data class ByTitle(val title: String) : ReminderTarget
}

/** How a command points at a client. */
sealed interface ClientTarget {
    data class ById(val id: Long) : ClientTarget
    data class ByName(val name: String) : ClientTarget
}

/** How a command points at someone in the phone's address book. */
sealed interface ContactTarget {
    data class ByName(val name: String) : ContactTarget
    data class ByNumber(val number: String) : ContactTarget
}
