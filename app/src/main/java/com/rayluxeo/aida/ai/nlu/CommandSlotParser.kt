package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.ContactTarget
import com.rayluxeo.aida.domain.command.ReminderTarget
import com.rayluxeo.aida.domain.command.TaskTarget
import com.rayluxeo.aida.domain.usecase.SnoozeReminderUseCase
import com.rayluxeo.aida.domain.model.ContactField
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.MessagingApp
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.nlu.DateTimeParser
import com.rayluxeo.aida.domain.nlu.TemporalMatch
import com.rayluxeo.aida.domain.task.RecurrenceRule
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.SystemMiraClock
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.salah.Prayer
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the parsers found in one utterance, gathered once.
 *
 * The scan happens before the intent is known, because the structural facts — a moment, a span,
 * an amount, a sum — are part of what decides the intent. Doing it once also guarantees that
 * the command is filled from the same time expression the classifier was told about, rather
 * than from a second parse that might read the sentence differently.
 */
data class RequestScan(
    val normalized: String,
    /**
     * The repeat Abu stated, if he stated one.
     *
     * It is found before anything else is read out of the sentence, because what a repeat leaves
     * behind misleads every other parser: "every 5 months" hands the date parser a number sitting
     * next to a month word, which is exactly how it reads "the 5th of the month", and a stray
     * "every" nobody claimed ends up at the front of the task's own title.
     *
     * The rule reaches `CreateTask.recurrence`, so a task Abu asks to repeat is created as a
     * repeating one. `CreateReminder` still has no slot for it: a repeating reminder needs the
     * scheduler to re-arm itself after each firing, which is a larger change than this one.
     */
    val recurrence: ParsedRecurrence?,
    /**
     * The place Abu asked to be reminded at, if he named one instead of a moment.
     *
     * Read once here for the same reason the repeat is: the place phrase is most of the
     * sentence, and a second parse at fill time could bound it differently from the one the
     * title is built out of. "Remind me" is a single intent whether it waits on a clock or on
     * arriving somewhere, so which of the two it is comes out of the slots rather than out of
     * the classifier.
     */
    val place: ParsedPlace?,
    val temporal: TemporalMatch?,
    val duration: ParsedDuration?,
    val money: ParsedMoney?,
    val arithmetic: String?,
) {
    val signals: IntentSignals = IntentSignals(
        hasTemporal = temporal != null,
        hasDuration = duration != null,
        // A bare number is not an amount of money. Only a stated currency — "taka", "৳", "tk" —
        // makes it one, otherwise every sentence with a digit in it would argue for an expense.
        hasMoney = money != null && money.currencyWasExplicit,
        hasArithmetic = arithmetic != null,
    )
}

/**
 * A filled command, and the confidence of the time expression it rests on where it rests on one.
 * Kept apart from the command so the engine can weigh a shaky time into what it reports without
 * the command itself carrying a number that means nothing to the dispatcher.
 */
data class SlottedCommand(val command: AppCommand, val temporalConfidence: Float? = null)

/**
 * Builds the actual command once the classifier has decided what Abu asked for.
 *
 * It returns null whenever a slot the command genuinely requires is missing. That is the whole
 * contract: a reminder with no moment, a completion with no task named, an expense with no
 * amount are all better answered with "I did not follow" than with an invented blank, and the
 * engine turns the null into exactly that.
 *
 * Four free-text slots are the deliberate exception. A reminder's title, a timer's label, an
 * expense's description and an income's source are all incidental wording rather than something
 * Mira has to match a record against, so when nothing is left after the parsers have taken their
 * share, they fall back to Abu's own sentence. That keeps "20 minuter timer dao" working without
 * Mira inventing an English word she would then have to translate back.
 *
 * An alarm's label is the one that deliberately does NOT fall back, because it is the one Abu
 * reads back in a list. "Wake me up at 7 am every weekday" is all framing and no subject, and
 * his own sentence standing in for the label would name the alarm after the request that set it.
 * It comes through empty instead, and is named where wording belongs.
 *
 * The reminder joined that list because of one sentence Abu actually says: "২০ মিনিট পরে আমাকে
 * মনে করিও" names a moment and no subject at all, and refusing it would be Mira declining to do
 * a thing she can do perfectly well. The moment is still required — a reminder with nothing to
 * fire at is not a reminder — so only the subject falls back.
 */
@Singleton
class CommandSlotParser @Inject constructor(
    private val dateTime: DateTimeParser,
    private val times: TimeExpressionParser,
    private val currency: CurrencyExpressionParser,
    private val titles: TitleExtractor,
    private val clock: MiraClock = SystemMiraClock(),
    private val english: EnglishNumberParser = EnglishNumberParser(),
    private val bangla: BanglaNumberParser = BanglaNumberParser(),
) {

    fun scan(normalized: String): RequestScan {
        val recurrence = RecurrenceExpressionParser.parse(normalized)
        // Everything below reads the sentence with the repeat already taken out of it, for the
        // reasons RequestScan.recurrence gives.
        val stated = recurrence?.remainder ?: normalized
        return RequestScan(
            normalized = normalized,
            recurrence = recurrence,
            place = PlaceExpressionParser.parse(stated),
            temporal = dateTime.parse(stated),
            duration = times.parseDuration(stated),
            money = currency.parse(stated),
            arithmetic = ArithmeticExpression.extract(normalized),
        )
    }

    fun fill(intent: MiraIntent, request: CommandRequest, scan: RequestScan): SlottedCommand? = when (intent) {

        // ---------------------------------------------------------------- work

        MiraIntent.CREATE_TASK -> titleOf(intent, request, withoutPriority(afterTime(scan)))?.let { title ->
            SlottedCommand(
                AppCommand.CreateTask(
                    title = title,
                    dueAt = scan.temporal?.instant,
                    priority = priorityIn(scan.normalized) ?: TaskPriority.NORMAL,
                    recurrence = scan.recurrence?.rule,
                ),
                scan.temporal?.confidence,
            )
        }

        MiraIntent.UPDATE_TASK -> {
            val priority = priorityIn(scan.normalized)
            val title = titleOf(intent, request, withoutPriority(afterTime(scan)))
            val dueAt = scan.temporal?.instant
            // An update that changes nothing is rejected by the validator anyway, and locally it
            // means Mira misread the sentence rather than that Abu asked for a no-op.
            if (title == null || (dueAt == null && priority == null)) {
                null
            } else {
                SlottedCommand(
                    AppCommand.UpdateTask(TaskTarget.ByTitle(title), dueAt = dueAt, priority = priority),
                    scan.temporal?.confidence,
                )
            }
        }

        MiraIntent.COMPLETE_TASK -> titleOf(intent, request, afterTime(scan))
            ?.let { SlottedCommand(AppCommand.CompleteTask(TaskTarget.ByTitle(it))) }

        MiraIntent.DELETE_TASK -> titleOf(intent, request, afterTime(scan))
            ?.let { SlottedCommand(AppCommand.DeleteTask(TaskTarget.ByTitle(it))) }

        // An empty query is meaningful here: it asks for everything still open.
        MiraIntent.SEARCH_TASKS -> SlottedCommand(
            AppCommand.SearchTasks(query = titleOf(intent, request, afterTime(scan)), filter = TaskFilter.OPEN),
        )

        // A place is read before a moment. "Office e pouchhale mone koriye dao" carries no clock
        // time at all, so the temporal path would refuse a reminder Mira can keep perfectly well
        // — and a sentence naming both is still about arriving somewhere.
        MiraIntent.CREATE_REMINDER -> scan.place?.let { place ->
            val title = titleOf(intent, request, place.remainder) ?: sentenceOf(request.raw)
            title?.let { SlottedCommand(AppCommand.CreatePlaceReminder(it, place.label, place.trigger)) }
        } ?: scan.temporal?.let { temporal ->
            val title = titleOf(intent, request, temporal.remainder) ?: sentenceOf(request.raw)
            title?.let { SlottedCommand(AppCommand.CreateReminder(it, temporal.instant), temporal.confidence) }
        }

        // The query is optional — "what reminders do I have" narrows nothing.
        MiraIntent.QUERY_REMINDERS -> SlottedCommand(
            AppCommand.QueryReminders(query = titleOf(intent, request, afterTime(scan))),
        )

        // The span comes from a parsed duration ("by 15 minutes"); without one, the short default.
        MiraIntent.SNOOZE_REMINDER -> {
            val by = scan.duration?.duration ?: SnoozeReminderUseCase.SHORT
            val remainder = scan.duration?.remainder ?: afterTime(scan)
            titleOf(intent, request, remainder)
                ?.let { SlottedCommand(AppCommand.SnoozeReminder(ReminderTarget.ByTitle(it), by)) }
        }

        MiraIntent.CANCEL_REMINDER -> titleOf(intent, request, afterTime(scan))
            ?.let { SlottedCommand(AppCommand.CancelReminder(ReminderTarget.ByTitle(it))) }

        // An alarm's label is genuinely optional — "wake me up at 7" names no subject — and Abu's
        // own sentence must NOT stand in for it the way a reminder's title does, or the alarm
        // list reads "wake me up at 7 am every weekday" where it should read the alarm's name.
        // An empty label is the honest answer and the domain layer supplies the default.
        MiraIntent.CREATE_ALARM -> scan.temporal?.let { temporal ->
            SlottedCommand(
                AppCommand.CreateAlarm(
                    label = titleOf(intent, request, afterTime(scan)).orEmpty(),
                    triggerAt = temporal.instant,
                    repeatDays = repeatDaysOf(scan.recurrence?.rule),
                ),
                temporal.confidence,
            )
        }

        MiraIntent.CREATE_TIMER -> scan.duration?.let { parsed ->
            val label = titleOf(intent, request, parsed.remainder) ?: sentenceOf(request.raw)
            label?.let { SlottedCommand(AppCommand.CreateTimer(it, parsed.duration)) }
        }

        MiraIntent.GET_DAILY_BRIEF -> SlottedCommand(AppCommand.GetDailyBrief)

        // ---------------------------------------------------------------- knowledge

        // A note is all body: splitting a spoken sentence into a heading and a body invents a
        // structure Abu did not say, and the note list shows the opening words anyway.
        MiraIntent.CREATE_NOTE -> titleOf(intent, request, scan.normalized)
            ?.let { SlottedCommand(AppCommand.CreateNote(title = null, body = it)) }

        // A query is not required: "show my notes" names no search term and lists them all, the
        // same way SEARCH_TASKS lists every open task. An empty query reaches the repository as a
        // blank FTS match, which it reads as "everything".
        MiraIntent.SEARCH_NOTES ->
            SlottedCommand(AppCommand.SearchNotes(titleOf(intent, request, scan.normalized).orEmpty()))

        MiraIntent.STORE_MEMORY -> titleOf(intent, request, scan.normalized)?.let { fact ->
            SlottedCommand(AppCommand.StoreMemory(subject = subjectOf(fact, request.raw), fact = fact))
        }

        MiraIntent.SEARCH_MEMORY -> titleOf(intent, request, scan.normalized)
            ?.let { SlottedCommand(AppCommand.SearchMemory(it)) }

        MiraIntent.FORGET_FACT -> titleOf(intent, request, scan.normalized)
            ?.let { SlottedCommand(AppCommand.ForgetFact(it)) }

        // ---------------------------------------------------------------- business

        MiraIntent.CREATE_CLIENT -> {
            val phone = phoneIn(request.raw)
            val email = emailIn(request.raw)
            val remainder = withoutContactDetails(scan.normalized, phone, email)
            titleOf(intent, request, remainder)?.let { name ->
                SlottedCommand(AppCommand.CreateClient(name = name, phone = phone, email = email))
            }
        }

        // As with notes: "show my clients" / "list my clients" names no one in particular and
        // lists them all. An empty query is a blank FTS match, which the repository reads as
        // "every client" rather than refusing.
        MiraIntent.SEARCH_CLIENTS ->
            SlottedCommand(AppCommand.SearchClients(titleOf(intent, request, scan.normalized).orEmpty()))

        MiraIntent.CREATE_INVOICE_DRAFT -> {
            val isRecurring = scan.recurrence != null ||
                scan.normalized.contains("monthly") ||
                scan.normalized.contains("weekly") ||
                scan.normalized.contains("quarterly") ||
                scan.normalized.contains("yearly") ||
                scan.normalized.contains("recurring")
            val isTheSame = scan.normalized.contains("same") ||
                scan.normalized.contains("ebarer") ||
                scan.normalized.contains("আগের") ||
                scan.normalized.contains("এই") ||
                scan.normalized.contains("ei")
            if (isRecurring && isTheSame) {
                val cycle = when (scan.recurrence?.rule) {
                    is RecurrenceRule.Daily,
                    is RecurrenceRule.Weekly,
                    is RecurrenceRule.Weekdays -> SubscriptionCycle.WEEKLY
                    is RecurrenceRule.Monthly -> SubscriptionCycle.MONTHLY
                    is RecurrenceRule.Yearly -> SubscriptionCycle.YEARLY
                    is RecurrenceRule.EveryNDays -> SubscriptionCycle.CUSTOM_DAYS
                    null -> when {
                        scan.normalized.contains("weekly") -> SubscriptionCycle.WEEKLY
                        scan.normalized.contains("quarterly") -> SubscriptionCycle.QUARTERLY
                        scan.normalized.contains("yearly") -> SubscriptionCycle.YEARLY
                        else -> SubscriptionCycle.MONTHLY
                    }
                }
                SlottedCommand(
                    AppCommand.CreateRecurringInvoice(
                        sourceInvoiceNumber = invoiceNumberIn(request.raw),
                        cycle = cycle,
                    ),
                )
            } else {
                val remainder = afterMoneyAndTime(scan)
                val dueOn = scan.temporal?.instant?.atZone(clock.zone())?.toLocalDate()
                val draftSlots = parseInvoiceDraftSlots(remainder, request.raw, request.language, intent)
                if (draftSlots != null) {
                    SlottedCommand(
                        AppCommand.CreateInvoiceDraft(
                            client = ClientTarget.ByName(draftSlots.client),
                            amount = scan.money?.money,
                            description = draftSlots.description,
                            dueOn = dueOn,
                        ),
                        scan.temporal?.confidence,
                    )
                } else null
            }
        }

        MiraIntent.CREATE_QUOTE_DRAFT -> {
            // Same slotting as an invoice draft — client, the amount Abu named, and a description —
            // because a quote and the invoice it becomes are the same document at different stages.
            // The quote keywords strip from the name via the intent's own triggers (namesTheRequest).
            val remainder = afterMoneyAndTime(scan)
            val validUntil = scan.temporal?.instant?.atZone(clock.zone())?.toLocalDate()
            val draftSlots = parseInvoiceDraftSlots(remainder, request.raw, request.language, intent)
            if (draftSlots != null) {
                SlottedCommand(
                    AppCommand.CreateQuoteDraft(
                        client = ClientTarget.ByName(draftSlots.client),
                        amount = scan.money?.money,
                        description = draftSlots.description,
                        validUntil = validUntil,
                    ),
                    scan.temporal?.confidence,
                )
            } else null
        }

        MiraIntent.QUERY_INVOICE -> {
            val invNumber = invoiceNumberIn(request.raw) ?: invoiceNumberIn(scan.normalized)
            val rawTokens = request.raw.split(' ').map { it.trim { c -> !c.isLetterOrDigit() && c != '#' && c != '-' } }.filter { it.isNotEmpty() }
            val clientCandidate = rawTokens.firstOrNull { token ->
                val lowerToken = token.lowercase()
                lowerToken != "invoice" &&
                    lowerToken != "invoices" &&
                    lowerToken != "ইনভয়েস" &&
                    lowerToken != "কত" &&
                    lowerToken != "koto" &&
                    lowerToken != "due" &&
                    lowerToken != "outstanding" &&
                    lowerToken != "baki" &&
                    lowerToken != "বাকি" &&
                    lowerToken != "বকেয়া" &&
                    lowerToken != "er" &&
                    lowerToken != "এর" &&
                    lowerToken != "what" &&
                    lowerToken != "is" &&
                    lowerToken != "the" &&
                    lowerToken != "show" &&
                    lowerToken != "how" &&
                    lowerToken != "much" &&
                    !lowerToken.startsWith("inv-")
            }
            val clientName = clientCandidate?.removeSuffix("'s")?.removeSuffix("er")?.removeSuffix("এর")
            SlottedCommand(
                AppCommand.QueryInvoice(
                    client = clientName?.takeIf { it.isNotBlank() }?.let { ClientTarget.ByName(it) },
                    invoiceNumber = invNumber,
                ),
            )
        }

        MiraIntent.QUERY_CLIENT_STATEMENT -> {
            // The client is the whole slot: pull the first token that is not a statement keyword or a
            // framing word, the same token-filter shape QUERY_INVOICE uses to find a client name.
            val rawTokens = request.raw.split(' ').map { it.trim { c -> !c.isLetterOrDigit() && c != '#' && c != '-' } }.filter { it.isNotEmpty() }
            val clientCandidate = rawTokens.firstOrNull { token ->
                val lowerToken = token.lowercase()
                lowerToken !in STATEMENT_FRAMING_WORDS
            }
            val clientName = clientCandidate?.removeSuffix("'s")?.removeSuffix("er")?.removeSuffix("এর")
            clientName?.takeIf { it.isNotBlank() }
                ?.let { SlottedCommand(AppCommand.QueryClientStatement(ClientTarget.ByName(it))) }
        }

        MiraIntent.DELIVER_INVOICE -> {
            val invNumber = invoiceNumberIn(request.raw) ?: invoiceNumberIn(scan.normalized)
            val rawTokens = request.raw.split(' ').map { it.trim { c -> !c.isLetterOrDigit() && c != '#' && c != '-' } }.filter { it.isNotEmpty() }
            val toIndex = rawTokens.indexOfFirst {
                it.equals("to", ignoreCase = true) ||
                    it.equals("কাছে", ignoreCase = true) ||
                    it.equals("কে", ignoreCase = true)
            }
            val clientName = if (toIndex >= 0 && toIndex < rawTokens.size - 1) {
                rawTokens[toIndex + 1]
            } else {
                rawTokens.firstOrNull { token ->
                    val lowerToken = token.lowercase()
                    lowerToken != "send" &&
                        lowerToken != "invoice" &&
                        lowerToken != "invoices" &&
                        lowerToken != "ইনভয়েস" &&
                        lowerToken != "pathao" &&
                        lowerToken != "পাঠাও" &&
                        lowerToken != "পাঠিয়ে" &&
                        lowerToken != "পাঠিয়ে" &&
                        lowerToken != "দাও" &&
                        lowerToken != "dao" &&
                        lowerToken != "it" &&
                        lowerToken != "him" &&
                        lowerToken != "her" &&
                        lowerToken != "them" &&
                        lowerToken != "take" &&
                        lowerToken != "তাকে" &&
                        !lowerToken.startsWith("inv-")
                }
            }
            val method = when {
                scan.normalized.contains("whatsapp") -> com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod.WHATSAPP
                scan.normalized.contains("email") || scan.normalized.contains("mail") -> com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod.EMAIL
                else -> com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod.SYSTEM_SHARE
            }
            SlottedCommand(
                AppCommand.DeliverInvoice(
                    invoiceNumber = invNumber,
                    client = clientName?.takeIf { it.isNotBlank() }?.let { ClientTarget.ByName(it) },
                    method = method,
                ),
            )
        }

        MiraIntent.NEW_CONVERSATION -> SlottedCommand(AppCommand.NewConversation)

        MiraIntent.DELETE_CONVERSATION -> SlottedCommand(AppCommand.DeleteConversation(null))

        MiraIntent.DELETE_ALL_CONVERSATIONS -> SlottedCommand(AppCommand.DeleteAllConversations)

        MiraIntent.CHITCHAT -> SlottedCommand(AppCommand.Greet)

        // ---------------------------------------------------------------- money

        MiraIntent.RECORD_PAYMENT -> {
            val percentage = percentageIn(scan.normalized)
            val invNumber = invoiceNumberIn(request.raw) ?: invoiceNumberIn(scan.normalized)
            if (scan.money != null) {
                val text = titleOf(intent, request, afterMoney(scan))
                val rawTokens = request.raw.split(' ').map { it.trim { c -> !c.isLetter() } }.filter { it.isNotEmpty() }
                val fromIndex = rawTokens.indexOfFirst { it.equals("from", ignoreCase = true) || it.equals("হতে", ignoreCase = true) || it.equals("থেকে", ignoreCase = true) }
                val clientName = if (fromIndex >= 0 && fromIndex < rawTokens.size - 1) {
                    rawTokens[fromIndex + 1]
                } else if (rawTokens.size >= 2 && rawTokens[1].equals("paid", ignoreCase = true)) {
                    rawTokens[0]
                } else {
                    null
                }
                SlottedCommand(
                    AppCommand.RecordPayment(
                        amount = scan.money.money,
                        percentage = percentage,
                        client = clientName?.let { ClientTarget.ByName(it) },
                        invoiceNumber = invNumber,
                        method = methodIn(scan.normalized),
                    ),
                )
            } else if (percentage != null && invNumber != null) {
                SlottedCommand(
                    AppCommand.RecordPayment(
                        amount = null,
                        percentage = percentage,
                        client = null,
                        invoiceNumber = invNumber,
                        method = methodIn(scan.normalized),
                    ),
                )
            } else {
                null
            }
        }

        MiraIntent.RECORD_EXPENSE -> scan.money?.let { parsed ->
            val description = titleOf(intent, request, afterMoney(scan)) ?: sentenceOf(request.raw)
            val cleanedDesc = description?.let { rawDesc ->
                var d = rawDesc.trim()
                var hadLeadingI = false
                if (d.startsWith("i on ", ignoreCase = true)) {
                    d = d.substring(5).trim()
                    hadLeadingI = true
                } else if (d.startsWith("i ", ignoreCase = true)) {
                    d = d.substring(2).trim()
                    hadLeadingI = true
                }
                for (prep in listOf("on ", "for ", "at ", "in ", "এ ", "তে ")) {
                    if (d.startsWith(prep, ignoreCase = true)) {
                        d = d.substring(prep.length).trim()
                    }
                }
                if (d.endsWith(" on", ignoreCase = true)) {
                    d = d.substring(0, d.length - 3).trim()
                }
                if (hadLeadingI) d.replaceFirstChar { it.uppercase() } else d
            }
            cleanedDesc?.let {
                SlottedCommand(AppCommand.RecordExpense(parsed.money, it, categoryIn(scan.normalized)))
            }
        }

        MiraIntent.RECORD_INCOME -> scan.money?.let { parsed ->
            val source = titleOf(intent, request, afterMoney(scan)) ?: sentenceOf(request.raw)
            source?.let {
                SlottedCommand(
                    AppCommand.RecordIncome(
                        amount = parsed.money,
                        source = it,
                        client = properNounIn(it, request.raw)?.let { name -> ClientTarget.ByName(name) },
                    ),
                )
            }
        }

        MiraIntent.PERFORM_CALCULATION -> scan.arithmetic
            ?.let { SlottedCommand(AppCommand.PerformCalculation(it)) }

        MiraIntent.QUERY_EXPENSES -> {
            val category = categoryIn(scan.normalized)
            val (from, to) = moneyQueryWindow(request, scan)
            SlottedCommand(AppCommand.QueryExpenses(category = category, from = from, to = to))
        }

        MiraIntent.QUERY_INCOME -> {
            val (from, to) = moneyQueryWindow(request, scan)
            SlottedCommand(AppCommand.QueryIncome(from = from, to = to))
        }

        MiraIntent.QUERY_SUBSCRIPTION -> {
            val words = request.raw.split(' ', '\t', '\n')
                .map { it.trim { c -> !c.isLetterOrDigit() } }
                .filter { it.isNotEmpty() }
            val candidate = words.firstOrNull { word ->
                val lower = word.lowercase()
                lower != "subscription" && lower != "next" && lower != "kobe" && lower != "when" &&
                    lower != "সাবস্ক্রিপশন" && lower != "কবে" && lower != "renewal"
            } ?: "Subscription"
            SlottedCommand(AppCommand.QuerySubscription(name = candidate))
        }

        // ---------------------------------------------------------------- salah

        // A prayer has to be named (or implied by a day-part) to log one; with none, this is a
        // miss rather than a blank record.
        MiraIntent.MARK_PRAYER_PRAYED -> prayerIn(scan.normalized)?.let { prayer ->
            SlottedCommand(AppCommand.MarkPrayerPrayed(prayer = prayer, onDate = dateIn(scan)))
        }

        MiraIntent.QUERY_NEXT_PRAYER -> SlottedCommand(AppCommand.QueryNextPrayer)

        // A null prayer is meaningful here: it asks about the one coming up.
        MiraIntent.QUERY_PRAYER_TIME_REMAINING ->
            SlottedCommand(AppCommand.QueryPrayerTimeRemaining(prayer = prayerIn(scan.normalized)))

        MiraIntent.QUERY_PRAYER_TIME -> prayerIn(scan.normalized)?.let { prayer ->
            SlottedCommand(AppCommand.QueryPrayerTime(prayer = prayer, onDate = dateIn(scan)))
        }

        MiraIntent.QUERY_PRAYER_TRACKING -> {
            val (from, to) = prayerTrackingWindow(request, scan)
            SlottedCommand(AppCommand.QueryPrayerTracking(from = from, to = to))
        }

        MiraIntent.SET_TAHAJJUD_REMINDER -> SlottedCommand(AppCommand.SetTahajjudReminder)

        // ---------------------------------------------------------------- the phone itself

        MiraIntent.CREATE_CALENDAR_EVENT -> scan.temporal?.let { temporal ->
            val title = titleOf(intent, request, temporal.remainder) ?: sentenceOf(request.raw)
            title?.let {
                SlottedCommand(AppCommand.CreateCalendarEvent(it, temporal.instant), temporal.confidence)
            }
        }

        MiraIntent.QUERY_SCHEDULE -> {
            val zone = clock.zone()
            val date = scan.temporal?.instant?.atZone(zone)?.toLocalDate() ?: clock.today()
            val from = date.atStartOfDay(zone).toInstant()
            val to = date.plusDays(1).atStartOfDay(zone).toInstant()
            val query = scan.temporal?.remainder?.let { rem ->
                val cleaned = rem.split(' ')
                    .filterNot { it in SCHEDULE_STOP_WORDS }
                    .joinToString(" ")
                    .trim()
                cleaned.ifEmpty { null }
            }
            SlottedCommand(AppCommand.QuerySchedule(from = from, to = to, query = query))
        }

        MiraIntent.FIND_FREE_TIME -> {
            val zone = clock.zone()
            val date = scan.temporal?.instant?.atZone(zone)?.toLocalDate() ?: clock.today()
            SlottedCommand(AppCommand.FindFreeSlots(date = date))
        }

        MiraIntent.UPDATE_CALENDAR_EVENT -> {
            val title = titleOf(intent, request, scan.temporal?.remainder ?: scan.normalized)
                ?: sentenceOf(request.raw)
                ?: "meeting"
            SlottedCommand(AppCommand.UpdateCalendarEvent(targetTitle = title, newStartsAt = scan.temporal?.instant))
        }

        MiraIntent.DELETE_CALENDAR_EVENT -> {
            val title = titleOf(intent, request, scan.normalized) ?: sentenceOf(request.raw) ?: "meeting"
            SlottedCommand(AppCommand.DeleteCalendarEvent(targetTitle = title))
        }

        MiraIntent.FIND_CONTACT -> {
            val field = when {
                scan.normalized.contains("email") || scan.normalized.contains("ইমেইল") ||
                    scan.normalized.contains("ইমেল") || scan.normalized.contains("mail") ||
                    scan.normalized.contains("মেইল") -> ContactField.EMAIL
                scan.normalized.contains("number") || scan.normalized.contains("phone") ||
                    scan.normalized.contains("nambar") || scan.normalized.contains("nombor") ||
                    scan.normalized.contains("ফোন") || scan.normalized.contains("নাম্বার") ||
                    scan.normalized.contains("নম্বর") -> ContactField.PHONE
                else -> ContactField.ANY
            }
            val queryText = titleOf(intent, request, scan.normalized) ?: scan.normalized
            val cleanedQuery = cleanContactQuery(queryText, request.raw)
            SlottedCommand(AppCommand.FindContact(query = cleanedQuery, requestedField = field))
        }

        MiraIntent.PREPARE_CALL -> contactOf(titleOf(intent, request, scan.normalized), request.raw)
            ?.let { SlottedCommand(AppCommand.PrepareCall(it)) }

        MiraIntent.PREPARE_MESSAGE -> {
            val text = titleOf(intent, request, scan.normalized) ?: sentenceOf(request.raw)
            text?.let {
                val (named, body) = splitMessage(it, request.raw)
                val app = if (scan.normalized.contains("whatsapp") || request.raw.contains("হোয়াটসঅ্যাপ", ignoreCase = true)) {
                    MessagingApp.WHATSAPP
                } else {
                    MessagingApp.SMS
                }
                contactOf(named, request.raw)?.let { contact ->
                    SlottedCommand(AppCommand.PrepareMessage(contact, body, app))
                }
            }
        }
    }

    // -------------------------------------------------------------------- remainders

    /**
     * What is left once the moment has been taken out. The repeat's remainder stands in when
     * there was no moment, so that "protidin website check korar task" does not keep the word
     * that said how often as part of what the task is.
     */
    private fun afterTime(scan: RequestScan): String =
        scan.temporal?.remainder ?: scan.recurrence?.remainder ?: scan.normalized

    private fun afterMoney(scan: RequestScan): String =
        scan.money?.remainder ?: scan.recurrence?.remainder ?: scan.normalized

    private fun afterMoneyAndTime(scan: RequestScan): String {
        val withoutTime = scan.temporal?.remainder ?: scan.normalized
        return currency.parse(withoutTime)?.remainder ?: scan.money?.remainder ?: withoutTime
    }

    /**
     * The date window an expense or income query covers. A stated moment narrows it to that one
     * day; "this month" in any of Abu's spellings is the current calendar month; otherwise the
     * window is left open for the handler to default to the current month from the clock.
     *
     * One copy, read by both money queries. They once each carried the window logic, and a
     * second hand-maintained copy is exactly the drift AGENTS.md §7.12 warns against.
     */
    private fun moneyQueryWindow(request: CommandRequest, scan: RequestScan): Pair<LocalDate?, LocalDate?> {
        val temporal = scan.temporal
        return if (temporal != null) {
            val date = temporal.instant.atZone(clock.zone()).toLocalDate()
            date to date.plusDays(1)
        } else if (scan.normalized.contains("this month") || scan.normalized.contains("ei mashe") || request.raw.contains("এই মাসে")) {
            val today = clock.now().atZone(clock.zone()).toLocalDate()
            today.withDayOfMonth(1) to today.plusMonths(1).withDayOfMonth(1)
        } else {
            null to null
        }
    }

    /**
     * The prayer Abu named, or null. An explicit name wins; a bare day-part in a prayer sentence
     * is read as the prayer that falls in it — "dupurer namaz" is Dhuhr, "shokaler namaz" Fajr —
     * which is why "দুপুর" stays NOON in the vocabulary (§7.12) and is read as Dhuhr only here, in
     * a prayer context.
     */
    private fun prayerIn(normalized: String): Prayer? {
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        for (token in tokens) PRAYER_TOKENS[token]?.let { return it }
        for (token in tokens) DAY_PART_PRAYERS[token]?.let { return it }
        return null
    }

    /** The day a prayer command names, or null for the ordinary "now", left to the handler's clock. */
    private fun dateIn(scan: RequestScan): LocalDate? =
        scan.temporal?.instant?.atZone(clock.zone())?.toLocalDate()

    /**
     * The window a prayer-tracking query covers, the same shape [moneyQueryWindow] builds: "this
     * month" is the calendar month, a stated day is that one day, and an open window is left for
     * the handler to default to the current month from the clock.
     */
    private fun prayerTrackingWindow(request: CommandRequest, scan: RequestScan): Pair<LocalDate?, LocalDate?> {
        val today = clock.now().atZone(clock.zone()).toLocalDate()
        val temporal = scan.temporal
        return when {
            scan.normalized.contains("this month") ||
                scan.normalized.contains("ei mashe") ||
                request.raw.contains("এই মাসে") ->
                today.withDayOfMonth(1) to today.plusMonths(1).withDayOfMonth(1)
            temporal != null -> {
                val date = temporal.instant.atZone(clock.zone()).toLocalDate()
                date to date.plusDays(1)
            }
            scan.normalized.contains("today") || request.raw.contains("আজ") ->
                today to today.plusDays(1)
            else -> null to null
        }
    }

    /**
     * The days an alarm clock can actually ring on, out of the repeat the scan already read.
     * There is no second vocabulary of repeat words here on purpose: "every weekday" is one
     * grammar, owned by [RecurrenceExpressionParser], and a private copy of it beside the alarm
     * would drift from the one tasks use.
     *
     * Only the shapes that mean "these weekdays, every week" survive. A monthly or yearly
     * repeat, or one counted in more than one period — "every month at seven", "every 3 days" —
     * is a recurring reminder rather than an alarm clock, and no set of weekdays says what Abu
     * asked for. Those come back empty, which is a one-time alarm: Mira does the part she was
     * asked for that an alarm can hold, and does not invent a cadence he did not state.
     */
    private fun repeatDaysOf(rule: RecurrenceRule?): Set<DayOfWeek> = when (rule) {
        null -> emptySet()
        is RecurrenceRule.Daily -> if (rule.interval == 1) EVERY_DAY else emptySet()
        is RecurrenceRule.Weekdays -> WORKING_DAYS
        // Weekly with no day named means "the same weekday as the first occurrence", which an
        // alarm has no first occurrence to take its day from.
        is RecurrenceRule.Weekly -> if (rule.interval == 1) rule.days else emptySet()
        is RecurrenceRule.Monthly, is RecurrenceRule.Yearly, is RecurrenceRule.EveryNDays -> emptySet()
    }

    private fun titleOf(intent: MiraIntent, request: CommandRequest, remainder: String): String? =
        titles.extract(remainder, request.raw, request.language, intent)

    private fun withoutPriority(text: String): String =
        text.split(' ').filter { token ->
            Lexicon.priorityWords.keys.none { Lexicon.triggerMatches(it, token) }
        }.joinToString(separator = " ")

    /**
     * Takes the phone number and the e-mail back out of the sentence before the name is read
     * from it, removing the LAST occurrence of each word the address contributed. An e-mail
     * Abu dictates is usually built from the name he just said — "Ahmed ahmed@rayluxeo.com" —
     * and removing every occurrence would take the name away with it.
     */
    private fun withoutContactDetails(normalized: String, phone: String?, email: String?): String {
        var tokens = normalized.split(' ').filter { it.isNotEmpty() }
        // A client's name carries no digits, so once a number has been read out of the sentence
        // every digit run in it belonged to that number, however the separators split it up.
        if (phone != null) tokens = tokens.filterNot { token -> token.all { it.isDigit() || it in "+-" } }
        if (email != null) tokens = removeLast(tokens, email.split('@', '.', '_', '-', '+'))
        return tokens.joinToString(separator = " ")
    }

    private fun removeLast(tokens: List<String>, values: List<String>): List<String> {
        val remaining = tokens.toMutableList()
        for (value in values) {
            if (value.isEmpty()) continue
            val index = remaining.indexOfLast { it == value }
            if (index >= 0) remaining.removeAt(index)
        }
        return remaining
    }

    // -------------------------------------------------------------------- slot vocabulary

    private fun priorityIn(normalized: String): TaskPriority? = firstMatch(normalized, Lexicon.priorityWords)

    private fun methodIn(normalized: String): PaymentMethod =
        firstMatch(normalized, Lexicon.paymentMethods) ?: PaymentMethod.OTHER

    private fun categoryIn(normalized: String): ExpenseCategory =
        firstMatch(normalized, Lexicon.expenseCategories) ?: ExpenseCategory.OTHER

    private fun <T> firstMatch(normalized: String, table: Map<String, T>): T? {
        for (token in normalized.split(' ')) {
            val hit = table.entries.firstOrNull { Lexicon.triggerMatches(it.key, token) }
            if (hit != null) return hit.value
        }
        return null
    }

    // -------------------------------------------------------------------- names and numbers

    /**
     * What a remembered fact is about. A memory is nearly always about someone or something
     * named, and the normalizer has already worked out which words those are by protecting them
     * from the spelling fold, so the first of them is the best subject available offline. With
     * no name in the sentence the fact's opening word has to serve; recall matches on the fact
     * as well, so a weak subject narrows the search rather than losing the memory.
     */
    private fun subjectOf(fact: String, raw: String): String =
        properNounIn(fact, raw) ?: fact.split(' ').first()

    private fun properNounIn(text: String, raw: String): String? {
        val nouns = properNouns(raw)
        for (token in text.split(' ')) {
            val lower = token.lowercase()
            nouns[lower]?.let { return it }
        }
        return null
    }

    /**
     * The words Abu capitalised somewhere other than the start of a sentence. Bengali script has
     * no capitals, so this is empty for a Bengali-script utterance and every caller falls back.
     */
    private fun properNouns(raw: String): Map<String, String> {
        val nouns = mutableMapOf<String, String>()
        var atSentenceStart = true
        for (word in raw.split(' ', '\t', '\n', '\r')) {
            val cleaned = word.trim { !it.isLetter() }
            val withoutPossessive = cleaned.removeSuffix("'s").removeSuffix("’s").removeSuffix("'S")
            if (withoutPossessive.isNotEmpty()) {
                if (!atSentenceStart && withoutPossessive.length > 1 && withoutPossessive[0].isUpperCase()) {
                    nouns[withoutPossessive.lowercase()] = withoutPossessive
                }
                atSentenceStart = false
            }
            if (word.any { it in SENTENCE_ENDS }) atSentenceStart = true
        }
        return nouns
    }

    /** [text] with the first occurrence of [word] taken out, or null when nothing is left. */
    private fun remaining(text: String, word: String): String? {
        val tokens = text.split(' ').toMutableList()
        val index = tokens.indexOfFirst { it.equals(word, ignoreCase = true) }
        if (index >= 0) tokens.removeAt(index)
        return tokens.joinToString(separator = " ").trim().ifEmpty { null }
    }

    /** Whether [token] is one of [intent]'s request keywords — the words that name the ask, not the subject. */
    private fun namesTheRequest(token: String, intent: MiraIntent): Boolean {
        val rule = Lexicon.byIntent[intent] ?: return false
        return rule.triggers.any { it.namesTheRequest && Lexicon.triggerMatches(it.token, token) }
    }

    private data class InvoiceDraftSlots(val client: String, val description: String?)

    private fun parseInvoiceDraftSlots(
        remainder: String,
        raw: String,
        language: SpokenLanguage,
        intent: MiraIntent,
    ): InvoiceDraftSlots? {
        val tokens = remainder.split(' ').map { it.trim { c -> !c.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        val surfaces = mutableMapOf<String, String>()
        for (word in raw.split(' ', '\t', '\n', '\r')) {
            val cleaned = word.trim { !it.isLetterOrDigit() }
            if (cleaned.isNotEmpty()) {
                surfaces[cleaned.lowercase()] = cleaned
            }
        }

        val jonnoIndex = tokens.indexOfFirst { it == "jonno" || it == "jonne" }
        if (jonnoIndex > 0) {
            // Drop the particle "er" and the intent's own request keywords ("invoice"/"bill",
            // "quote"/"quotation"/…), so "invoice Ahmed er jonno website" names the client "Ahmed",
            // not "invoice Ahmed". The non-jonno path strips these through TitleExtractor; this path
            // bypasses it, so it must drop them here too.
            val clientTokens = tokens.subList(0, jonnoIndex)
                .filter { it != "er" }
                .filterNot { namesTheRequest(it, intent) }
            if (clientTokens.isNotEmpty()) {
                val client = clientTokens.joinToString(" ") { surfaces[it] ?: it.replaceFirstChar { c -> c.uppercase() } }
                val descTokens = tokens.subList(jonnoIndex + 1, tokens.size)
                val descStr = descTokens.joinToString(" ")
                val desc = titleOf(intent, CommandRequest(raw, descStr, language, TextScript.LATIN, InputSource.TEXT), descStr)
                return InvoiceDraftSlots(client = client, description = desc)
            }
        }

        val text = titleOf(intent, CommandRequest(raw, remainder, language, TextScript.LATIN, InputSource.TEXT), remainder) ?: return null
        val named = properNounIn(text, raw)
            ?: raw.split(' ').firstOrNull()?.trim { !it.isLetter() }?.takeIf { it.isNotEmpty() && it[0].isUpperCase() }
                ?.takeIf { text.contains(it, ignoreCase = true) }
        val client = named ?: text
        val desc = named?.let { remaining(text, it) }
        return InvoiceDraftSlots(client = client, description = desc)
    }

    /**
     * The phone number in [raw], as digits. Anything shorter than [MIN_PHONE_DIGITS] is a
     * quantity or a clock time: Abu writes "kal 10 tay" far more often than he dictates a number.
     */
    private fun phoneIn(raw: String): String? {
        val candidates = mutableListOf<String>()
        val span = StringBuilder()
        fun close() {
            val digits = span.filter { it.isDigit() }.toString()
            if (digits.length >= MIN_PHONE_DIGITS) {
                candidates += if (span.startsWith("+")) "+$digits" else digits
            }
            span.clear()
        }
        for (character in raw) {
            if (character.isDigit() || character == '+' || (span.isNotEmpty() && character in PHONE_SEPARATORS)) {
                span.append(character)
            } else {
                close()
            }
        }
        close()
        return candidates.maxByOrNull { it.length }
    }

    private fun emailIn(raw: String): String? = raw.split(' ', '\t', '\n', '\r')
        .map { it.trim { character -> !character.isLetterOrDigit() && character !in EMAIL_CHARACTERS } }
        .firstOrNull { candidate ->
            val at = candidate.indexOf('@')
            at > 0 && candidate.indexOf('.', at) > at + 1 && candidate.count { it == '@' } == 1
        }
        ?.lowercase()

    private fun percentageIn(normalized: String): BigDecimal? {
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        for (i in tokens.indices) {
            val token = tokens[i]
            if (token.endsWith("%")) {
                token.removeSuffix("%").toIntOrNull()?.let { return BigDecimal(it) }
            }
            if (token in PERCENT_WORDS) {
                val prev = tokens.getOrNull(i - 1) ?: continue
                val numeric = prev.toIntOrNull()?.let { BigDecimal(it) }
                    ?: english.parse(prev)?.value
                    ?: bangla.parse(prev)?.value
                if (numeric != null) return numeric
            }
        }
        return null
    }

    /**
     * The invoice reference in [raw], if Abu named one. Either an explicit reference ("#1024",
     * "INV-001") or a number directly qualified by "invoice", e.g. "invoice 41".
     */
    private fun invoiceNumberIn(raw: String): String? {
        val tokens = raw.split(' ', '\t', '\n', '\r')
            .map { it.trim { character -> !character.isLetterOrDigit() && character != '-' && character != '#' } }
            .filter { it.isNotEmpty() }

        for (i in 0 until tokens.size - 1) {
            val lower = tokens[i].lowercase()
            if (lower == "invoice" || lower == "invoices" || lower == "চালান" || lower == "bill") {
                val next = tokens[i + 1].removePrefix("#")
                if (next.isNotEmpty() && next.any { it.isDigit() }) {
                    return next
                }
            }
        }

        return tokens
            .firstOrNull { it.length > 1 && it.any { character -> character.isDigit() } && looksLikeReference(it) }
            ?.removePrefix("#")
    }

    private fun looksLikeReference(token: String): Boolean =
        token.startsWith("#") || (token.any { it.isLetter() } && token.any { it == '-' })

    // -------------------------------------------------------------------- contacts and wording

    private fun contactOf(text: String?, raw: String): ContactTarget? {
        val name = text ?: return null
        val number = phoneIn(raw)
        if (number != null && name.count { it.isDigit() } >= MIN_PHONE_DIGITS) {
            return ContactTarget.ByNumber(number)
        }
        return ContactTarget.ByName(name)
    }

    /**
     * Splits "Ahmed that I am late" or "Ahmed I'm running ten minutes late" into who the message is for
     * and what it says.
     */
    private fun splitMessage(text: String, raw: String): Pair<String?, String?> {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        val hinge = tokens.indexOfFirst { it.lowercase() in MESSAGE_SEPARATORS }
        if (hinge >= 0) {
            val named = tokens.subList(0, hinge)
                .dropLastWhile { it == CanonicalToken.TO || it == CanonicalToken.AT }
                .joinToString(separator = " ")
                .ifEmpty { null }
            val body = tokens.subList(hinge + 1, tokens.size).joinToString(separator = " ").ifEmpty { null }
            return named to body
        }

        // Look for a proper noun in the raw text to cleanly separate recipient from message body
        val proper = properNounIn(text, raw)
        if (proper != null) {
            val rawTokens = raw.split(' ', '\t', '\n').map { it.trim { c -> !c.isLetterOrDigit() && c != '\'' } }.filter { it.isNotEmpty() }
            val properIndex = rawTokens.indexOfFirst { it.equals(proper, ignoreCase = true) }
            val rawBody = if (properIndex >= 0 && properIndex < rawTokens.size - 1) {
                raw.substring(raw.indexOf(rawTokens[properIndex]) + rawTokens[properIndex].length).trim()
            } else {
                tokens.filterNot { it.equals(proper, ignoreCase = true) }.joinToString(" ").ifEmpty { null }
            }
            return proper to rawBody
        }

        if (tokens.size > 1) {
            val named = tokens.first()
            val body = tokens.drop(1).joinToString(" ").ifEmpty { null }
            return named to body
        }

        return text.ifEmpty { null } to null
    }

    private fun cleanContactQuery(query: String, raw: String): String {
        val proper = properNounIn(query, raw)
        if (proper != null) return proper

        val tokens = query.split(' ')
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
            .filterNot { it.lowercase() in CONTACT_STOP_WORDS }

        val cleaned = tokens.joinToString(" ") { token ->
            var t = token
            for (suffix in listOf("'s", "’s", "এর", "er", "ke", "কে", "ta", "টা")) {
                if (t.endsWith(suffix, ignoreCase = true) && t.length > suffix.length) {
                    t = t.substring(0, t.length - suffix.length)
                }
            }
            t
        }.trim()

        val rawWord = raw.split(' ', '\t', '\n', '\r')
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .firstOrNull { it.equals(cleaned, ignoreCase = true) }
        val finalResult = rawWord ?: cleaned

        return finalResult.ifEmpty { query }
    }

    /** Abu's own sentence, without the punctuation that ended it. */
    private fun sentenceOf(raw: String): String? =
        raw.trim().trimEnd { it in SENTENCE_ENDS }.trim().ifEmpty { null }

    private companion object {
        const val MIN_PHONE_DIGITS = 7
        const val PHONE_SEPARATORS = "-() "
        const val EMAIL_CHARACTERS = "@._-+"

        /** The canonical prayer tokens, as the vocabulary folds them. */
        val PRAYER_TOKENS: Map<String, Prayer> = mapOf(
            CanonicalToken.FAJR to Prayer.FAJR,
            CanonicalToken.DHUHR to Prayer.DHUHR,
            CanonicalToken.ASR to Prayer.ASR,
            CanonicalToken.MAGHRIB to Prayer.MAGHRIB,
            CanonicalToken.ISHA to Prayer.ISHA,
            CanonicalToken.TAHAJJUD to Prayer.TAHAJJUD,
        )

        /** A day-part standing in for the prayer that falls in it, when no prayer was named. */
        val DAY_PART_PRAYERS: Map<String, Prayer> = mapOf(
            CanonicalToken.MORNING to Prayer.FAJR,
            CanonicalToken.NOON to Prayer.DHUHR,
            CanonicalToken.AFTERNOON to Prayer.ASR,
            CanonicalToken.EVENING to Prayer.MAGHRIB,
            CanonicalToken.NIGHT to Prayer.ISHA,
        )

        /** Including the Bengali danda, which ends a sentence written in Bengali script. */
        const val SENTENCE_ENDS = ".!?।"

        /**
         * The statement-query framing: the command's own keywords plus the request verbs and small
         * words that sit in front of a client's name, so the first token left is the name. "give me
         * Ahmed's statement" must land on "Ahmed", not on "give".
         */
        val STATEMENT_FRAMING_WORDS: Set<String> = setOf(
            "statement", "স্টেটমেন্ট", "ledger", "লেজার", "account",
            "give", "get", "pull", "fetch", "find", "bring", "show", "see",
            "me", "my", "what", "whats", "what's", "is", "the", "a", "for", "of",
            "er", "এর", "s",
        )

        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

        /**
         * Monday to Friday, because that is what `RecurrenceRule.Weekdays` is defined to mean.
         * Which days count as the working week is the rule's decision, and reopening it here
         * would leave an alarm ringing on days the same words put a task on.
         */
        val WORKING_DAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

        val MESSAGE_SEPARATORS = setOf("that", "saying", "je", "bole", "বলে", "যে")

        val PERCENT_WORDS = setOf(
            "percent", "percentage", "parcent", "shotangsho", "শতাংশ", "পার্সেন্ট", "%",
        )

        val SCHEDULE_STOP_WORDS = setOf(
            "what", "do", "i", "have", "whats", "on", "schedule", "agenda", "plan",
            "কি", "আছে", "কী", "শিডিউল", "আজকের", "ki", "ache", "ase",
        )

        val CONTACT_STOP_WORDS = setOf(
            "find", "search", "number", "nambar", "nombor", "phone", "email", "mail",
            "খুঁজো", "খুজো", "নম্বর", "নাম্বার", "ফোন", "ইমেইল", "ইমেল", "মেইল",
            "কি", "কী", "ki", "koto", "what", "is", "the", "এর", "er",
            "tell", "me", "show", "dekhao", "বলো", "দেখাও",
        )
    }
}
