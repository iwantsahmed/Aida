package com.rayluxeo.aida.domain.command

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.Money
import java.time.Duration
import javax.inject.Inject

/**
 * The last gate before execution, and the only one.
 *
 * Every command passes through here whichever engine proposed it, so Gemini cannot smuggle a
 * blank title or a reminder in the past past the on-device rules. The `when` is exhaustive with
 * no `else`: a new command must state its rule, even if that rule is "nothing to check".
 *
 * Trimming happens here too, which is why most branches return a copy rather than the original —
 * a title with a trailing space from dictation should not become a title with a trailing space.
 */
class DefaultCommandValidator @Inject constructor(
    private val clock: MiraClock,
) : CommandValidator {

    override fun validate(command: AppCommand): AppResult<AppCommand> = when (command) {

        is AppCommand.CreateTask -> command.title.asTitle("task")
            .map { command.copy(title = it, notes = command.notes.tidy()) }

        is AppCommand.UpdateTask -> validateUpdateTask(command)

        is AppCommand.CompleteTask -> command.target.validated()
            .map { AppCommand.CompleteTask(it) }

        is AppCommand.SearchTasks -> command.copy(query = command.query.tidy()).asSuccess()

        is AppCommand.CreateReminder -> command.title.asTitle("reminder")
            .flatMapIf(command.triggerAt.isFuture(), "Reminder time is in the past")
            .map { command.copy(title = it) }

        is AppCommand.CreatePlaceReminder -> command.title.asTitle("place reminder")
            .flatMap { title -> command.placeLabel.asTitle("place name").map { title to it } }
            .map { (title, place) -> command.copy(title = title, placeLabel = place) }

        // The past-time rule holds only for a one-time alarm. A repeating one names a time of day
        // rather than a moment — seven o'clock having already gone by today is ordinary, and it
        // rings tomorrow morning — so applying it there would refuse the commonest alarm there is.
        is AppCommand.CreateAlarm -> command.label.asOptionalTitle("alarm label")
            .flatMapIf(
                command.repeatDays.isNotEmpty() || command.triggerAt.isFuture(),
                "Alarm time is in the past",
            )
            .map { command.copy(label = it) }

        is AppCommand.CreateTimer -> validateTimer(command)

        AppCommand.GetDailyBrief -> command.asSuccess()

        AppCommand.Greet -> command.asSuccess()

        // The answer is the model's own words, bounded only so a runaway reply cannot be stored or
        // spoken unchecked. Blank is a miss, not a thing to say.
        is AppCommand.Conversational -> command.answer.asTitle("answer", MAX_BODY)
            .map { command.copy(answer = it) }

        is AppCommand.CreateNote -> command.body.asTitle("note body", MAX_BODY)
            .map { command.copy(title = command.title.tidy(), body = it) }

        // A blank query is "list them all", not a validation failure — the same rule SearchTasks
        // holds. "show my notes" names no search term, and the repository reads an empty query as
        // every note rather than refusing it.
        is AppCommand.SearchNotes -> command.copy(query = command.query.tidy().orEmpty()).asSuccess()

        is AppCommand.StoreMemory -> command.subject.asTitle("memory subject")
            .flatMap { subject -> command.fact.asTitle("memory fact", MAX_BODY).map { subject to it } }
            .map { (subject, fact) -> command.copy(subject = subject, fact = fact) }

        is AppCommand.ForgetMemory -> if (command.memoryId <= 0L) {
            failure(MiraErrorCode.VALIDATION, "Invalid memory id")
        } else {
            AppResult.Success(command)
        }

        is AppCommand.SearchMemory -> command.query.asTitle("memory search")
            .map { command.copy(query = it) }

        is AppCommand.UniversalSearch -> command.query.asTitle("universal search")
            .map { command.copy(query = it) }

        is AppCommand.CreateClient -> command.name.asTitle("client name")
            .map {
                command.copy(
                    name = it,
                    companyName = command.companyName.tidy(),
                    phone = command.phone.tidy(),
                    email = command.email.tidy(),
                )
            }

        // As with notes: an empty query lists every client ("show my clients"), it is not a miss.
        is AppCommand.SearchClients -> command.copy(query = command.query.tidy().orEmpty()).asSuccess()

        is AppCommand.CreateInvoiceDraft -> command.client.validated()
            .flatMapIf(command.amount?.isPositive() ?: true, "Invoice amount must be positive")
            .map { command.copy(client = it, description = command.description.tidy()) }

        is AppCommand.CreateQuoteDraft -> command.client.validated()
            .flatMapIf(command.amount?.isPositive() ?: true, "Quote amount must be positive")
            .map { command.copy(client = it, description = command.description.tidy()) }

        is AppCommand.CreateRecurringInvoice -> nothing()
            .map { command }

        is AppCommand.RecordPayment -> nothing()
            .flatMapIf(
                (command.amount != null && command.amount.isPositive()) ||
                    (command.percentage != null && command.percentage > java.math.BigDecimal.ZERO),
                "Payment amount or percentage must be positive",
            )
            .map { command }

        is AppCommand.RecordExpense -> command.description.asTitle("expense description")
            .flatMapIf(command.amount.isPositive(), "Expense amount must be positive")
            .map { command.copy(description = it) }

        is AppCommand.RecordIncome -> command.source.asTitle("income source")
            .flatMapIf(command.amount.isPositive(), "Income amount must be positive")
            .map { command.copy(source = it) }

        is AppCommand.PerformCalculation -> command.expression.asTitle("expression", MAX_BODY)
            .map { command.copy(expression = it) }

        is AppCommand.QueryExpenses -> command.asSuccess()

        is AppCommand.QueryIncome -> command.asSuccess()

        is AppCommand.QuerySubscription -> command.asSuccess()

        // A prayer cannot be logged as prayed on a day that has not happened yet.
        is AppCommand.MarkPrayerPrayed ->
            if (command.onDate == null || !command.onDate.isAfter(clock.today())) command.asSuccess()
            else failure(MiraErrorCode.VALIDATION, "Cannot mark a prayer for a future day")

        AppCommand.QueryNextPrayer -> command.asSuccess()

        is AppCommand.QueryPrayerTimeRemaining -> command.asSuccess()

        is AppCommand.QueryPrayerTime -> command.asSuccess()

        is AppCommand.QueryPrayerTracking ->
            if (command.from != null && command.to != null && command.to.isBefore(command.from)) {
                failure(MiraErrorCode.VALIDATION, "Tracking window ends before it starts")
            } else {
                command.asSuccess()
            }

        AppCommand.SetTahajjudReminder -> command.asSuccess()

        is AppCommand.CreateCalendarEvent -> command.title.asTitle("event")
            .flatMapIf(command.startsAt.isFuture(), "Event start is in the past")
            .flatMapIf(
                command.endsAt == null || command.endsAt.isAfter(command.startsAt),
                "Event ends before it starts",
            )
            .map { command.copy(title = it) }

        is AppCommand.QuerySchedule ->
            if (!command.to.isBefore(command.from)) command.asSuccess()
            else failure(MiraErrorCode.VALIDATION, "End of schedule window is before start")

        is AppCommand.FindFreeSlots ->
            if (command.preferredDurationMinutes > 0) command.asSuccess()
            else failure(MiraErrorCode.VALIDATION, "Preferred duration must be positive")

        is AppCommand.UpdateCalendarEvent -> command.targetTitle.asTitle("target event title")
            .flatMapIf(
                command.newStartsAt == null || command.newEndsAt == null || command.newEndsAt.isAfter(command.newStartsAt),
                "Event ends before it starts",
            )
            .map { command.copy(targetTitle = it, newTitle = command.newTitle.tidy()) }

        is AppCommand.DeleteCalendarEvent -> command.targetTitle.asTitle("target event title")
            .map { command.copy(targetTitle = it) }

        is AppCommand.FindContact -> command.query.asTitle("contact search")
            .map { command.copy(query = it) }

        is AppCommand.PrepareCall -> command.contact.validated()
            .map { AppCommand.PrepareCall(it) }

        is AppCommand.PrepareMessage -> command.contact.validated()
            .map { AppCommand.PrepareMessage(it, command.body.tidy(), command.app) }

        is AppCommand.QueryInvoice -> {
            if (command.client == null && command.invoiceNumber.isNullOrBlank()) {
                failure(MiraErrorCode.VALIDATION, "Client or invoice number required")
            } else {
                command.asSuccess()
            }
        }

        is AppCommand.QueryClientStatement -> command.client.validated()
            .map { command.copy(client = it) }

        is AppCommand.DeliverInvoice -> {
            if (command.invoiceNumber.isNullOrBlank() && command.client == null) {
                failure(MiraErrorCode.VALIDATION, "Invoice number or client required")
            } else {
                command.asSuccess()
            }
        }

        is AppCommand.NewConversation -> command.asSuccess()

        is AppCommand.DeleteConversation -> command.asSuccess()

        is AppCommand.DeleteAllConversations -> command.asSuccess()

        is AppCommand.SearchConversations -> command.query.asTitle("conversation search")
            .map { command.copy(query = it) }

        is AppCommand.DeleteTask -> command.target.validated()
            .map { AppCommand.DeleteTask(it) }

        is AppCommand.QueryReminders -> command.copy(query = command.query.tidy()).asSuccess()

        is AppCommand.SnoozeReminder ->
            if (command.by <= Duration.ZERO) {
                failure(MiraErrorCode.VALIDATION, "Snooze span must be a positive length of time")
            } else {
                command.target.validated().map { AppCommand.SnoozeReminder(it, command.by) }
            }

        is AppCommand.CancelReminder -> command.target.validated()
            .map { AppCommand.CancelReminder(it) }

        is AppCommand.ForgetFact -> command.query.asTitle("fact to forget")
            .map { AppCommand.ForgetFact(it) }
    }

    private fun validateUpdateTask(command: AppCommand.UpdateTask): AppResult<AppCommand> {
        val target = command.target.validated()
        if (target is AppResult.Failure) return target
        if (command.title == null && command.dueAt == null && command.priority == null) {
            return failure(MiraErrorCode.VALIDATION, "Update names nothing to change")
        }
        val title = command.title?.trim()
        if (title != null && (title.isEmpty() || title.length > MAX_TITLE)) {
            return failure(MiraErrorCode.VALIDATION, "Replacement task title is blank or too long")
        }
        return command.copy(title = title).asSuccess()
    }

    private fun validateTimer(command: AppCommand.CreateTimer): AppResult<AppCommand> {
        val label = command.label.trim()
        if (label.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Timer label is blank")
        if (command.duration <= Duration.ZERO) {
            return failure(MiraErrorCode.VALIDATION, "Timer duration must be positive")
        }
        if (command.duration > MAX_TIMER) {
            return failure(MiraErrorCode.VALIDATION, "Timer longer than a day belongs in a reminder")
        }
        return command.copy(label = label).asSuccess()
    }

    private fun java.time.Instant.isFuture(): Boolean = isAfter(clock.now())

    private fun Money.isPositive(): Boolean = amount.signum() > 0

    private fun String.asTitle(what: String, max: Int = MAX_TITLE): AppResult<String> {
        val trimmed = trim()
        return when {
            trimmed.isEmpty() -> failure(MiraErrorCode.VALIDATION, "Blank $what")
            trimmed.length > max -> failure(MiraErrorCode.VALIDATION, "$what is too long")
            else -> trimmed.asSuccess()
        }
    }

    /**
     * A name Abu may legitimately not have given.
     *
     * Trimmed and length-capped exactly as [asTitle] is, but blank passes: an alarm need not be
     * about anything — "wake me up at 7" names no subject — and the word shown in its place is
     * user-facing text, which never lives in the domain (§4). It is stored blank and named by the
     * presentation layer.
     */
    private fun String.asOptionalTitle(what: String, max: Int = MAX_TITLE): AppResult<String> {
        val trimmed = trim()
        return if (trimmed.length > max) {
            failure(MiraErrorCode.VALIDATION, "$what is too long")
        } else {
            trimmed.asSuccess()
        }
    }

    private fun String?.tidy(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun nothing(): AppResult<Unit> = Unit.asSuccess()

    private fun TaskTarget.validated(): AppResult<TaskTarget> = when (this) {
        is TaskTarget.ById ->
            if (id > 0) asSuccess() else failure(MiraErrorCode.VALIDATION, "Task id must be positive")
        is TaskTarget.ByTitle -> title.asTitle("task reference").map { TaskTarget.ByTitle(it) }
    }

    private fun ReminderTarget.validated(): AppResult<ReminderTarget> = when (this) {
        is ReminderTarget.ByTitle -> title.asTitle("reminder reference").map { ReminderTarget.ByTitle(it) }
    }

    private fun ClientTarget.validated(): AppResult<ClientTarget> = when (this) {
        is ClientTarget.ById ->
            if (id > 0) asSuccess() else failure(MiraErrorCode.VALIDATION, "Client id must be positive")
        is ClientTarget.ByName -> name.asTitle("client reference").map { ClientTarget.ByName(it) }
    }

    private fun ContactTarget.validated(): AppResult<ContactTarget> = when (this) {
        is ContactTarget.ByName -> name.asTitle("contact name").map { ContactTarget.ByName(it) }
        is ContactTarget.ByNumber -> {
            val digits = number.filter { it.isDigit() || it == '+' }
            if (digits.length < MIN_NUMBER) {
                failure(MiraErrorCode.VALIDATION, "Not enough digits to be a phone number")
            } else {
                ContactTarget.ByNumber(digits).asSuccess()
            }
        }
    }

    /** Carries an existing success forward only while [condition] holds. */
    private fun <T> AppResult<T>.flatMapIf(condition: Boolean, detail: String): AppResult<T> =
        if (this is AppResult.Success && !condition) failure(MiraErrorCode.VALIDATION, detail) else this

    private companion object {
        const val MAX_TITLE = 200
        const val MAX_BODY = 20_000
        const val MIN_NUMBER = 4
        val MAX_TIMER: Duration = Duration.ofHours(24)
    }
}
