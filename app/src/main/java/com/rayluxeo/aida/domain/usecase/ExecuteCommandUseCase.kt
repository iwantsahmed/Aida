package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandDispatcher
import com.rayluxeo.aida.domain.command.CommandInterpreter
import com.rayluxeo.aida.domain.command.CommandOutcome
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.CommandValidator
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.ReminderTarget
import com.rayluxeo.aida.domain.command.ConfirmationRequest
import com.rayluxeo.aida.domain.command.TaskTarget
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.domain.nlu.TextNormalizer
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * The whole pipeline in one place:
 * raw input -> normalisation -> interpretation -> validation -> confirmation -> execution
 * -> structured response.
 *
 * Abu's own words are logged here. Mira's reply is logged by the caller once it has been
 * rendered into Bengali or English, so the transcript holds real sentences rather than
 * the internal shape of a response.
 */
class ExecuteCommandUseCase @Inject constructor(
    private val normalizer: TextNormalizer,
    private val interpreter: CommandInterpreter,
    private val validator: CommandValidator,
    private val dispatcher: CommandDispatcher,
    private val conversation: ConversationRepository,
    private val contextManager: com.rayluxeo.aida.domain.context.ConversationContextManager,
    private val settings: SettingsRepository,
    private val clock: MiraClock,
) {

    /**
     * [onDispatch] fires at the moment interpretation is over and a validated command is about to
     * become an effect. It exists so a caller can tell Abu the difference between Mira working out
     * what he meant and Mira writing it down — the second is the wait where his phone is changing.
     * It never fires for a command that was only previewed for confirmation, because nothing is
     * being written yet.
     */
    suspend operator fun invoke(
        raw: String,
        source: InputSource,
        onDispatch: (() -> Unit)? = null,
    ): CommandOutcome {
        val trimmed = raw.trim()
        val mode = settings.current().languageMode
        val resolvedText = contextManager.resolveContextualReference(trimmed)
        val normalized = normalizer.normalize(resolvedText, mode)
        val language = normalized.language

        if (trimmed.isEmpty()) {
            return CommandOutcome.Rejected(MiraResponse.NotUnderstood(trimmed), language)
        }

        logAbu(trimmed, language, source)

        val request = CommandRequest(
            raw = resolvedText,
            normalized = normalized.normalized,
            language = language,
            script = normalized.script,
            source = source,
        )

        val routed = interpreter.route(request)
        return when (val result = routed.result) {
            is EngineResult.Interpreted -> interpreted(result, routed.engine, language, onDispatch)
            EngineResult.NotUnderstood -> CommandOutcome.Rejected(MiraResponse.NotUnderstood(trimmed), language)
            is EngineResult.Unavailable ->
                CommandOutcome.Rejected(MiraResponse.Problem(MiraErrorCode.ENGINE_UNAVAILABLE), language)
        }
    }

    /** Runs a command Abu has just agreed to. */
    suspend fun confirm(
        pending: ConfirmationRequest,
        language: SpokenLanguage,
        onDispatch: (() -> Unit)? = null,
    ): CommandOutcome =
        when (val validated = validator.validate(pending.command)) {
            is AppResult.Failure -> CommandOutcome.Rejected(MiraResponse.Problem(validated.error.code), language)
            is AppResult.Success -> execute(validated.value, pending.engine, language, onDispatch)
        }

    /** Records what Mira actually said, after the caller has rendered it. */
    suspend fun logMiraReply(text: String, language: SpokenLanguage, commandName: String?) {
        conversation.append(
            ConversationTurn(
                role = TurnRole.MIRA,
                text = text,
                language = language,
                source = InputSource.SYSTEM,
                commandName = commandName,
                createdAt = clock.now(),
            )
        )
    }

    private suspend fun interpreted(
        result: EngineResult.Interpreted,
        engine: EngineId,
        language: SpokenLanguage,
        onDispatch: (() -> Unit)?,
    ): CommandOutcome =
        when (val validated = validator.validate(result.command)) {
            is AppResult.Failure ->
                CommandOutcome.Rejected(MiraResponse.Problem(validated.error.code), language)
            is AppResult.Success -> {
                val command = validated.value
                // Cancelling a reminder joins deleting a calendar event in always confirming: ending
                // a reminder (and a repeating series with it) is not the one-tap restore a binned
                // task is, so Mira reads it back first regardless of how sure she is.
                val alwaysConfirm = command is AppCommand.DeleteCalendarEvent ||
                    command is AppCommand.CancelReminder
                val uncertain = result.confidence < EngineResult.CONFIRM_THRESHOLD || engine == EngineId.GEMINI
                if (command.isMutating && (uncertain || alwaysConfirm)) {
                    CommandOutcome.NeedsConfirmation(
                        response = command.preview(),
                        language = language,
                        pending = ConfirmationRequest(command, engine, command.preview()),
                    )
                } else {
                    execute(command, engine, language, onDispatch)
                }
            }
        }

    private suspend fun execute(
        command: AppCommand,
        engine: EngineId,
        language: SpokenLanguage,
        onDispatch: (() -> Unit)?,
    ): CommandOutcome {
        onDispatch?.invoke()
        val response = when (val result = dispatcher.dispatch(command)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> MiraResponse.Problem(result.error.code)
        }
        return CommandOutcome.Completed(response, language, engine)
    }

    private suspend fun logAbu(text: String, language: SpokenLanguage, source: InputSource) {
        conversation.append(
            ConversationTurn(
                role = TurnRole.ABU,
                text = text,
                language = language,
                source = source,
                createdAt = clock.now(),
            )
        )
    }
}

/**
 * What Mira reads back when she wants a yes before acting.
 *
 * Only mutating commands reach here, but the `when` covers the read-only ones too rather than
 * carrying an `else`: if a command ever changes from read-only to mutating, this is one of the
 * places that has to be reconsidered, and a compile error is how that gets noticed.
 */
private fun AppCommand.preview(): MiraResponse = when (this) {
    is AppCommand.CreateTask -> MiraResponse.TaskCreated(title, dueAt)
    is AppCommand.UpdateTask -> MiraResponse.TaskUpdated(title.orEmpty().ifEmpty { target.spoken() }, dueAt)
    is AppCommand.CompleteTask -> MiraResponse.TaskCompleted(target.spoken())
    is AppCommand.CreateReminder -> MiraResponse.ReminderCreated(title, triggerAt)
    is AppCommand.CreatePlaceReminder ->
        MiraResponse.PlaceReminderCreated(title, placeLabel, trigger, watchingUnknown)
    is AppCommand.CreateAlarm -> MiraResponse.AlarmSet(label, triggerAt, repeatDays)
    is AppCommand.CreateTimer -> MiraResponse.TimerStarted(label, duration, triggerAtUnknown)
    is AppCommand.CreateNote -> MiraResponse.NoteCreated(title)
    is AppCommand.StoreMemory -> MiraResponse.MemoryStored(subject, fact)
    is AppCommand.ForgetMemory -> MiraResponse.MemoryForgotten(memoryId)
    is AppCommand.UniversalSearch -> MiraResponse.UniversalSearchResultsFound(query, 0, emptyList())
    is AppCommand.CreateClient -> MiraResponse.ClientCreated(name)
    is AppCommand.CreateInvoiceDraft -> MiraResponse.InvoiceDraftRequested(client.spoken(), amount, description)
    is AppCommand.CreateQuoteDraft -> MiraResponse.QuoteDraftRequested(client.spoken(), amount, description)
    is AppCommand.CreateRecurringInvoice -> MiraResponse.RecurringInvoiceScheduled(sourceInvoiceNumber.orEmpty(), cycle)
    // A fixed amount is read back as the figure; a share of an invoice is read back as the share,
    // because the money itself is not known until the command runs against that invoice.
    is AppCommand.RecordPayment ->
        if (amount != null) {
            MiraResponse.PaymentRecorded(amount, client?.spoken(), invoiceNumber)
        } else {
            MiraResponse.PaymentByPercentageRequested(
                percentage = percentage ?: java.math.BigDecimal.ZERO,
                clientName = client?.spoken(),
                invoiceNumber = invoiceNumber,
            )
        }
    is AppCommand.RecordExpense -> MiraResponse.ExpenseRecorded(amount, description)
    is AppCommand.RecordIncome -> MiraResponse.IncomeRecorded(amount, source)
    is AppCommand.MarkPrayerPrayed -> MiraResponse.PrayerMarked(prayer, onDate)
    // The reminder's moment is not known until a place is set and the day computed, and the
    // confirmation happens before that, so the preview names no time.
    AppCommand.SetTahajjudReminder -> MiraResponse.TahajjudReminderSet(null)
    is AppCommand.CreateCalendarEvent -> MiraResponse.CalendarEventCreated(title, startsAt)
    is AppCommand.UpdateCalendarEvent -> MiraResponse.CalendarEventUpdated(targetTitle, newStartsAt ?: java.time.Instant.EPOCH)
    is AppCommand.DeleteCalendarEvent -> MiraResponse.CalendarEventDeleted(targetTitle)
    is AppCommand.DeleteTask -> MiraResponse.TaskDeleted(target.spoken())
    // The new moment is not known until the snooze runs, so the preview names the span, not the time.
    is AppCommand.SnoozeReminder -> MiraResponse.ReminderSnoozed(target.spoken(), triggerAtUnknown)
    is AppCommand.CancelReminder -> MiraResponse.ReminderCancelled(target.spoken())
    is AppCommand.ForgetFact -> MiraResponse.FactForgotten(query)
    is AppCommand.NewConversation -> MiraResponse.NewConversationStarted
    is AppCommand.DeleteConversation -> MiraResponse.ConversationDeleted(id)
    is AppCommand.DeleteAllConversations -> MiraResponse.AllConversationsDeleted

    // Read-only: Mira never asks permission to look something up, so these never reach here.
    is AppCommand.SearchTasks,
    AppCommand.GetDailyBrief,
    AppCommand.Greet,
    is AppCommand.Conversational,
    is AppCommand.PerformCalculation,
    is AppCommand.SearchNotes,
    is AppCommand.SearchMemory,
    is AppCommand.SearchClients,
    is AppCommand.QuerySchedule,
    is AppCommand.FindFreeSlots,
    is AppCommand.FindContact,
    is AppCommand.PrepareCall,
    is AppCommand.PrepareMessage,
    is AppCommand.QueryExpenses,
    is AppCommand.QueryIncome,
    is AppCommand.QuerySubscription,
    AppCommand.QueryNextPrayer,
    is AppCommand.QueryPrayerTimeRemaining,
    is AppCommand.QueryPrayerTime,
    is AppCommand.QueryPrayerTracking,
    is AppCommand.QueryInvoice,
    is AppCommand.QueryClientStatement,
    is AppCommand.DeliverInvoice,
    is AppCommand.SearchConversations,
    is AppCommand.QueryReminders,
    -> MiraResponse.NotUnderstood("")
}

/** The confirmation is read aloud, so a target has to be sayable rather than an id. */
private fun TaskTarget.spoken(): String = when (this) {
    is TaskTarget.ByTitle -> title
    is TaskTarget.ById -> ""
}

private fun ClientTarget.spoken(): String = when (this) {
    is ClientTarget.ByName -> name
    is ClientTarget.ById -> ""
}

private fun ReminderTarget.spoken(): String = when (this) {
    is ReminderTarget.ByTitle -> title
}

/**
 * A timer's end is only known once it starts, and a confirmation happens before that. The preview
 * says how long, not when — which is what Abu asked for anyway.
 */
private val triggerAtUnknown: java.time.Instant get() = java.time.Instant.EPOCH

/**
 * Whether the platform will hold a place alert is not known until it is asked, and a confirmation
 * happens before that.
 *
 * True rather than false for the same reason [triggerAtUnknown] is an instant nobody reads as a
 * time: the preview is the QUESTION — "when you get to the office?" — and `isWatching = false`
 * renders as a warning about a refusal that has not happened yet. Abu hears the real answer the
 * moment he says yes and the watch is actually attempted.
 */
private const val watchingUnknown: Boolean = true
