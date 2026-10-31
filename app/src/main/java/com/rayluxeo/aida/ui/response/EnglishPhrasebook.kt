package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Contact
import com.rayluxeo.aida.domain.model.ConversationStyle
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.salah.Prayer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Mira's English wording. She states what she did and stops — no exclamation marks, nothing said
 * twice, no commentary on how well it went.
 */
internal class EnglishPhrasebook(private val times: MiraTimes) : Phrasebook {

    override fun speak(response: MiraResponse, style: ConversationStyle): String = when (response) {
        is MiraResponse.TaskCreated -> taskCreatedSpeak(response, style)

        is MiraResponse.TaskUpdated -> response.dueAt
            ?.let { "Updated. ${response.title.forSpeech()}, ${whenPhrase(it)}." }
            ?: "Updated — ${response.title.forSpeech()}."

        is MiraResponse.TaskCompleted -> "Done — ${response.title.forSpeech()}."

        is MiraResponse.TasksFound -> listSpeak(response.count, response.titles, TASKS)

        is MiraResponse.ReminderCreated ->
            "Reminder set — ${response.title.forSpeech()}, ${whenPhrase(response.triggerAt)}."

        is MiraResponse.AlarmSet -> response.label.cleaned()
            ?.let { "Alarm set — ${it.forSpeech()}, ${alarmMoment(response)}." }
            ?: "Alarm set for ${alarmMoment(response)}."

        is MiraResponse.PlaceReminderCreated -> {
            val place = response.placeLabel.forSpeech()
            val title = response.title.forSpeech()
            if (response.isWatching) {
                "I'll remind you when you ${crossing(response.trigger)} $place — $title."
            } else {
                // No promise here, deliberately. A reminder nothing is waiting on reads exactly
                // like an armed one, and this is the only place Abu could be told the difference.
                "Saved — $title. I'm not watching $place yet, so it won't reach you on its own."
            }
        }

        is MiraResponse.PlaceUnknown -> {
            val label = response.placeLabel.forSpeech()
            if (response.known.isEmpty()) {
                "I don't have a place called $label. Add one in Settings and I'll use it."
            } else {
                "I don't have a place called $label. I know ${placesSpeak(response.known)}."
            }
        }

        is MiraResponse.TimerStarted -> {
            val label = response.label.trim()
            val length = lengthPhrase(response.duration)
            val ends = whenPhrase(response.endsAt)
            if (label.isEmpty()) "Timer started — $length, ending $ends."
            else "Timer started — ${label.forSpeech()}, $length, ending $ends."
        }

        is MiraResponse.BriefReady ->
            "${greeting(response.brief.dayPart)} ${taskSentence(response.brief)}${reminderSentence(response.brief)}"

        is MiraResponse.NoteCreated -> response.title.cleaned()
            ?.let { "Note saved — ${it.forSpeech()}." }
            ?: "Note saved."

        is MiraResponse.NotesFound -> listSpeak(response.count, response.titles, NOTES)

        is MiraResponse.MemoryStored ->
            "Remembered — ${response.subject.forSpeech()}: ${response.fact.forSpeech()}."

        is MiraResponse.MemoryForgotten ->
            "Fact forgotten."

        is MiraResponse.MemoriesFound -> {
            val spoken = response.facts.take(SPOKEN_TITLE_COUNT).map { it.forSpeech() }
            when {
                response.count == 0 -> "Nothing remembered about that."
                spoken.isEmpty() && response.count == 1 -> "One thing remembered."
                spoken.isEmpty() -> "${response.count} things remembered."
                response.count == 1 -> "Remembered — ${spoken.first()}."
                else -> "${response.count} things — ${joinWithAnd(spoken)}."
            }
        }

        is MiraResponse.UniversalSearchResultsFound ->
            if (response.totalCount == 0) "No records found for ${response.query.forSpeech()}."
            else "Found ${response.totalCount} results across records."

        is MiraResponse.ClientCreated -> "Client added — ${response.name.forSpeech()}."

        is MiraResponse.ClientsFound -> listSpeak(response.count, response.names, CLIENTS)

        is MiraResponse.InvoiceDraftCreated ->
            "Draft invoice ${response.number.trim()} — " +
                "${response.clientName.forSpeech()}, ${money(response.total)}."

        is MiraResponse.QuoteDraftCreated ->
            "Draft quote ${response.number.trim()} — " +
                "${response.clientName.forSpeech()}, ${money(response.total)}."

        is MiraResponse.RecurringInvoiceScheduled ->
            "Recurring invoice scheduled — ${response.cycle.name.lowercase()} for ${response.invoiceNumber}."

        is MiraResponse.PaymentRecorded -> {
            val client = response.clientName.cleaned()
            val invoice = response.invoiceNumber.cleaned()
            when {
                client != null -> "Payment recorded — ${money(response.amount)} from ${client.forSpeech()}."
                invoice != null -> "Payment recorded — ${money(response.amount)} against $invoice."
                else -> "Payment recorded — ${money(response.amount)}."
            }
        }

        is MiraResponse.InvoiceDraftRequested ->
            "Draft an invoice for ${response.clientName.forSpeech()}" +
                (response.amount?.let { " — ${money(it)}" } ?: "") +
                (response.description.cleaned()?.let { " for ${it.forSpeech()}" } ?: "") + "."

        is MiraResponse.QuoteDraftRequested ->
            "Draft a quote for ${response.clientName.forSpeech()}" +
                (response.amount?.let { " — ${money(it)}" } ?: "") +
                (response.description.cleaned()?.let { " for ${it.forSpeech()}" } ?: "") + "."

        is MiraResponse.PaymentByPercentageRequested -> {
            val client = response.clientName.cleaned()
            val invoice = response.invoiceNumber.cleaned()
            val share = "${response.percentage.stripTrailingZeros().toPlainString()}%"
            when {
                invoice != null && client != null -> "Record $share of $invoice from ${client.forSpeech()}."
                invoice != null -> "Record $share of $invoice."
                client != null -> "Record $share of ${client.forSpeech()}'s invoice."
                else -> "Record $share of the invoice."
            }
        }

        is MiraResponse.ExpenseRecorded -> expenseRecordedSpeak(response, style)

        is MiraResponse.IncomeRecorded -> incomeRecordedSpeak(response, style)

        is MiraResponse.CalculationResult -> "That's ${response.value}."

        is MiraResponse.ExpenseSummaryReport -> expenseSummarySpeak(response, style)

        is MiraResponse.IncomeSummaryReport -> incomeSummarySpeak(response, style)

        is MiraResponse.SubscriptionRenewalInfo ->
            "Next ${response.name.forSpeech()} renewal is on ${response.nextChargeOn}, ${money(response.amount)}."

        is MiraResponse.PrayerMarked -> {
            val day = response.onDate
                ?.takeIf { times.offsetOf(it) != DayOffset.TODAY }
                ?.let { " ${dayWordForDate(it)}" }
                ?: ""
            "Marked ${prayerName(response.prayer)} as prayed$day."
        }

        is MiraResponse.NextPrayerReport -> {
            val base = "Your next prayer is ${prayerName(response.prayer)} ${atPhrase(response.at)}."
            if (response.remaining.isZero || response.remaining.isNegative) base
            else "$base ${lengthPhrase(response.remaining)} remaining."
        }

        is MiraResponse.PrayerTimeRemainingReport ->
            if (response.remaining.isZero || response.remaining.isNegative) {
                "It's ${prayerName(response.prayer)} time now."
            } else {
                "${prayerName(response.prayer)} is in ${lengthPhrase(response.remaining)}."
            }

        is MiraResponse.PrayerTimeReport ->
            "${prayerName(response.prayer)} is ${whenPhrase(response.at)}."

        is MiraResponse.PrayerTrackingReport -> prayerTrackingSpeak(response)

        is MiraResponse.TahajjudReminderSet -> response.at
            ?.let { "Tahajjud reminder set — the last third of the night begins at ${clockPhrase(times.at(it))}." }
            ?: "Tahajjud reminder set for the last third of the night."

        is MiraResponse.InvoiceOutstandingReport -> invoiceOutstandingSpeak(response, style)

        is MiraResponse.ClientStatementReport -> clientStatementSpeak(response.statement, style)

        is MiraResponse.InvoiceDeliveryPrepared -> "Invoice draft created."

        is MiraResponse.AmbiguousInvoice -> {
            val client = response.clientName?.forSpeech()
            val candidates = response.candidateNumbers.joinToString(" or ")
            if (client != null) "Which invoice for $client: $candidates?"
            else "Which invoice: $candidates?"
        }

        is MiraResponse.NewConversationStarted -> "New conversation."
        is MiraResponse.ConversationDeleted -> "Conversation deleted."
        is MiraResponse.AllConversationsDeleted -> "All conversations deleted."
        is MiraResponse.ConversationsFound -> {
            if (response.count == 0) "No conversations found."
            else if (response.count == 1) "One conversation found."
            else "${response.count} conversations found."
        }

        is MiraResponse.CalendarEventCreated -> {
            val conflict = if (response.conflicts.isNotEmpty()) {
                " Note: conflicts with ${response.conflicts.joinToString(", ") { it.forSpeech() }}."
            } else ""
            "Added to the calendar — ${response.title.forSpeech()}, ${whenPhrase(response.startsAt)}.$conflict"
        }

        is MiraResponse.ScheduleReport -> {
            if (response.events.isEmpty()) {
                "You have nothing scheduled."
            } else {
                val list = response.events.take(SPOKEN_TITLE_COUNT).map {
                    "${it.title.forSpeech()} at ${clockPhrase(times.at(it.startsAt))}"
                }
                "${response.events.size} event${if (response.events.size > 1) "s" else ""}: ${joinWithAnd(list)}."
            }
        }

        is MiraResponse.FreeSlotsReport -> {
            if (response.slots.isEmpty()) {
                "You have no free time during working hours."
            } else {
                val list = response.slots.take(SPOKEN_TITLE_COUNT).map {
                    "${clockPhrase(times.at(it.startsAt))} to ${clockPhrase(times.at(it.endsAt))}"
                }
                "Free slots: ${joinWithAnd(list)}."
            }
        }

        is MiraResponse.CalendarEventUpdated ->
            "Calendar event updated — ${response.title.forSpeech()}, ${whenPhrase(response.startsAt)}."

        is MiraResponse.CalendarEventDeleted ->
            "Calendar event removed — ${response.title.forSpeech()}."

        is MiraResponse.ContactsFound ->
            listSpeak(response.count, response.contacts.map { it.displayName }, CONTACTS)

        is MiraResponse.ContactDetails -> {
            val name = response.contact.displayName.forSpeech()
            when (response.requestedField) {
                com.rayluxeo.aida.domain.model.ContactField.PHONE -> response.contact.phoneNumber.cleaned()
                    ?.let { "$name's number is $it." }
                    ?: "No phone number found for $name."
                com.rayluxeo.aida.domain.model.ContactField.EMAIL -> response.contact.email.cleaned()
                    ?.let { "$name's email is $it." }
                    ?: "No email address found for $name."
                com.rayluxeo.aida.domain.model.ContactField.ANY -> {
                    val phone = response.contact.phoneNumber.cleaned()
                    val email = response.contact.email.cleaned()
                    when {
                        phone != null && email != null -> "$name: $phone, $email."
                        phone != null -> "$name: $phone."
                        email != null -> "$name: $email."
                        else -> "No details found for $name."
                    }
                }
            }
        }

        is MiraResponse.CallPrepared -> {
            val name = response.contact.displayName.forSpeech()
            if (response.contact.isCallable) "Ready to call $name."
            else "I have $name, but no number for them."
        }

        is MiraResponse.MessagePrepared -> {
            val name = response.contact.displayName.forSpeech()
            if (response.app == com.rayluxeo.aida.domain.model.MessagingApp.WHATSAPP) {
                if (response.body.cleaned() != null) "WhatsApp message ready for $name."
                else "Ready to message $name on WhatsApp."
            } else {
                if (response.body.cleaned() != null) "Message ready for $name."
                else "Ready to message $name."
            }
        }

        is MiraResponse.AmbiguousTask ->
            ambiguousSpeak(response.candidates, "Which task do you mean?")

        is MiraResponse.AmbiguousClient ->
            ambiguousSpeak(response.candidates, "Which client do you mean?")

        is MiraResponse.AmbiguousContact ->
            ambiguousSpeak(response.candidates.map { it.displayName }, "Which contact do you mean?")

        is MiraResponse.TaskDeleted ->
            "Deleted — ${response.title.forSpeech()}. Restore it from Tasks if that was wrong."
        is MiraResponse.RemindersFound -> listSpeak(response.count, response.titles, REMINDERS)
        is MiraResponse.ReminderSnoozed -> "Snoozed — ${response.title.forSpeech()}."
        is MiraResponse.ReminderCancelled -> "Reminder cancelled — ${response.title.forSpeech()}."
        is MiraResponse.FactForgotten -> "Forgotten — ${response.subject.forSpeech()}."
        is MiraResponse.AmbiguousReminder ->
            ambiguousSpeak(response.candidates, "Which reminder do you mean?")
        is MiraResponse.AmbiguousFact ->
            ambiguousSpeak(response.facts, "Which fact do you mean?")

        is MiraResponse.PermissionNeeded -> permission(response.capability)

        MiraResponse.Abandoned -> "Left it."
        MiraResponse.Greeting -> greetingLine(style)
        is MiraResponse.Conversational -> response.answer
        is MiraResponse.NotUnderstood -> "I didn't catch that — say it again?"

        is MiraResponse.Problem -> problem(response.code)
    }

    override fun display(response: MiraResponse, style: ConversationStyle): String = when (response) {
        is MiraResponse.TaskCreated -> buildString {
            append("Added: ${response.title.trim()}")
            response.dueAt?.let { append("\nDue: ${whenPhrase(it)}") }
            if (response.repeats) append("\nRepeats")
        }

        is MiraResponse.TaskUpdated -> response.dueAt
            ?.let { "Updated: ${response.title.trim()}\nDue: ${whenPhrase(it)}" }
            ?: "Updated: ${response.title.trim()}"

        is MiraResponse.TaskCompleted -> "Completed: ${response.title.trim()}"

        is MiraResponse.TasksFound -> listDisplay(response.count, response.titles, TASKS)

        is MiraResponse.ReminderCreated ->
            "Reminder: ${response.title.trim()}\nWhen: ${whenPhrase(response.triggerAt)}"

        is MiraResponse.AlarmSet -> buildString {
            append(response.label.cleaned()?.let { "Alarm: $it" } ?: "Alarm set")
            val repeat = repeatPhrase(response.repeatDays, EnglishRepeatWording)
            if (repeat == null) {
                append("\nWhen: ${whenPhrase(response.triggerAt)}")
            } else {
                append("\nWhen: ${clockPhrase(times.at(response.triggerAt))}")
                append("\nRepeats: $repeat")
            }
        }

        is MiraResponse.PlaceReminderCreated -> buildString {
            append("Place reminder: ${response.title.trim()}")
            append("\nWhen: you ${crossing(response.trigger)} ${response.placeLabel.trim()}")
            if (!response.isWatching) {
                append("\nNot watching yet — Android has not let Aida watch that place, ")
                append("so nothing will reach you. The Reminders tab says why.")
            }
        }

        is MiraResponse.PlaceUnknown -> buildString {
            append("I don't have a place called \"${response.placeLabel.trim()}\".")
            if (response.known.isEmpty()) {
                append("\nNo places saved yet — add one in Settings.")
            } else {
                append("\nPlaces I know:")
                response.known.forEach { append("\n• ${it.trim()}") }
            }
        }

        is MiraResponse.TimerStarted -> buildString {
            val label = response.label.cleaned()
            if (label == null) {
                append("Timer: ${lengthPhrase(response.duration)}")
            } else {
                append("Timer: $label")
                append("\nLength: ${lengthPhrase(response.duration)}")
            }
            append("\nEnds: ${whenPhrase(response.endsAt)}")
        }

        is MiraResponse.BriefReady -> briefDisplay(response.brief)

        is MiraResponse.NoteCreated -> response.title.cleaned()
            ?.let { "Note: $it" }
            ?: "Note saved."

        is MiraResponse.NotesFound -> listDisplay(response.count, response.titles, NOTES)

        is MiraResponse.MemoryStored ->
            "Remembered: ${response.subject.trim()}\n${response.fact.trim()}"

        is MiraResponse.MemoryForgotten ->
            "Fact forgotten."

        is MiraResponse.MemoriesFound -> memoriesDisplay(response)

        is MiraResponse.UniversalSearchResultsFound ->
            "Found ${response.totalCount} results for '${response.query.trim()}':\n${response.topSnippets.joinToString("\n")}"

        is MiraResponse.ClientCreated -> "Client: ${response.name.trim()}"

        is MiraResponse.ClientsFound -> listDisplay(response.count, response.names, CLIENTS)

        is MiraResponse.InvoiceDraftCreated ->
            "Draft invoice ${response.number.trim()}\n" +
                "Client: ${response.clientName.trim()}\n" +
                "Total: ${money(response.total)}"

        is MiraResponse.QuoteDraftCreated ->
            "Draft quote ${response.number.trim()}\n" +
                "Client: ${response.clientName.trim()}\n" +
                "Total: ${money(response.total)}"

        is MiraResponse.RecurringInvoiceScheduled ->
            "Recurring invoice scheduled\n" +
                "Invoice: ${response.invoiceNumber}\n" +
                "Schedule: ${response.cycle.name.lowercase()}"

        is MiraResponse.PaymentRecorded -> buildString {
            append("Payment: ${money(response.amount)}")
            response.clientName.cleaned()?.let { append("\nFrom: $it") }
            response.invoiceNumber.cleaned()?.let { append("\nInvoice: $it") }
        }

        is MiraResponse.InvoiceDraftRequested -> buildString {
            append("Draft invoice\nClient: ${response.clientName.trim()}")
            response.amount?.let { append("\nTotal: ${money(it)}") }
            response.description.cleaned()?.let { append("\nFor: $it") }
        }

        is MiraResponse.QuoteDraftRequested -> buildString {
            append("Draft quote\nClient: ${response.clientName.trim()}")
            response.amount?.let { append("\nTotal: ${money(it)}") }
            response.description.cleaned()?.let { append("\nFor: $it") }
        }

        is MiraResponse.PaymentByPercentageRequested -> buildString {
            append("Payment: ${response.percentage.stripTrailingZeros().toPlainString()}%")
            response.invoiceNumber.cleaned()?.let { append("\nInvoice: $it") }
            response.clientName.cleaned()?.let { append("\nFrom: $it") }
        }

        is MiraResponse.ExpenseRecorded -> response.description.cleaned()
            ?.let { "Expense: ${money(response.amount)}\nFor: $it" }
            ?: "Expense: ${money(response.amount)}"

        is MiraResponse.IncomeRecorded -> response.source.cleaned()
            ?.let { "Income: ${money(response.amount)}\nFrom: $it" }
            ?: "Income: ${money(response.amount)}"

        is MiraResponse.CalculationResult -> "${response.expression} = ${response.value}"

        is MiraResponse.ExpenseSummaryReport -> expenseSummaryDisplay(response, style)

        is MiraResponse.IncomeSummaryReport -> incomeSummaryDisplay(response, style)

        is MiraResponse.SubscriptionRenewalInfo ->
            "Subscription: ${response.name.trim()}\nNext renewal: ${response.nextChargeOn}\nAmount: ${money(response.amount)}"

        is MiraResponse.PrayerMarked -> response.onDate
            ?.takeIf { times.offsetOf(it) != DayOffset.TODAY }
            ?.let { "Prayed: ${prayerName(response.prayer)} (${dayWordForDate(it)})" }
            ?: "Prayed: ${prayerName(response.prayer)}"

        is MiraResponse.NextPrayerReport -> buildString {
            append("Next prayer: ${prayerName(response.prayer)}")
            append("\nAt: ${whenPhrase(response.at)}")
            if (!response.remaining.isZero && !response.remaining.isNegative) {
                append("\nRemaining: ${lengthPhrase(response.remaining)}")
            }
        }

        is MiraResponse.PrayerTimeRemainingReport -> buildString {
            append(prayerName(response.prayer))
            if (response.remaining.isZero || response.remaining.isNegative) append(": now")
            else append(": in ${lengthPhrase(response.remaining)}")
            append("\nAt: ${whenPhrase(response.at)}")
        }

        is MiraResponse.PrayerTimeReport ->
            "${prayerName(response.prayer)}: ${whenPhrase(response.at)}"

        is MiraResponse.PrayerTrackingReport -> prayerTrackingDisplay(response)

        is MiraResponse.TahajjudReminderSet -> response.at
            ?.let { "Tahajjud reminder\nLast third begins: ${whenPhrase(it)}" }
            ?: "Tahajjud reminder set."

        is MiraResponse.InvoiceOutstandingReport -> invoiceOutstandingDisplay(response, style)

        is MiraResponse.ClientStatementReport -> clientStatementDisplay(response.statement, style)

        is MiraResponse.InvoiceDeliveryPrepared -> "Invoice draft created."

        is MiraResponse.AmbiguousInvoice -> {
            val client = response.clientName?.trim()
            val candidates = response.candidateNumbers.joinToString(" or ")
            if (client != null) "Which invoice for $client: $candidates?"
            else "Which invoice: $candidates?"
        }

        is MiraResponse.NewConversationStarted -> "New conversation."
        is MiraResponse.ConversationDeleted -> "Conversation deleted."
        is MiraResponse.AllConversationsDeleted -> "All conversations deleted."
        is MiraResponse.ConversationsFound -> {
            if (response.count == 0) "No conversations found."
            else if (response.count == 1) "1 conversation found"
            else "${response.count} conversations found"
        }

        is MiraResponse.CalendarEventCreated -> buildString {
            append("Calendar: ${response.title.trim()}\nWhen: ${whenPhrase(response.startsAt)}")
            if (response.conflicts.isNotEmpty()) {
                append("\nConflict warning: conflicts with ${response.conflicts.joinToString(", ")}")
            }
        }

        is MiraResponse.ScheduleReport -> buildString {
            append("Schedule:")
            if (response.events.isEmpty()) {
                append(" No events scheduled.")
            } else {
                response.events.forEach { event ->
                    val end = event.endsAt?.let { " - ${clockPhrase(times.at(it))}" } ?: ""
                    append("\n• ${event.title.trim()} (${clockPhrase(times.at(event.startsAt))}$end)")
                }
            }
        }

        is MiraResponse.FreeSlotsReport -> buildString {
            append("Free slots on ${response.date}:")
            if (response.slots.isEmpty()) {
                append(" None.")
            } else {
                response.slots.forEach { slot ->
                    append("\n• ${clockPhrase(times.at(slot.startsAt))} - ${clockPhrase(times.at(slot.endsAt))}")
                }
            }
        }

        is MiraResponse.CalendarEventUpdated ->
            "Updated: ${response.title.trim()}\nWhen: ${whenPhrase(response.startsAt)}"

        is MiraResponse.CalendarEventDeleted ->
            "Deleted from calendar: ${response.title.trim()}"

        is MiraResponse.ContactsFound ->
            listDisplay(response.count, response.contacts.map { contactLine(it) }, CONTACTS)

        is MiraResponse.ContactDetails -> buildString {
            append(response.contact.displayName.trim())
            response.contact.phoneNumber.cleaned()?.let { append("\nPhone: $it") }
            response.contact.email.cleaned()?.let { append("\nEmail: $it") }
            if (response.contact.phoneNumber.cleaned() == null && response.contact.email.cleaned() == null) {
                append("\nNo details saved.")
            }
        }

        is MiraResponse.CallPrepared -> {
            val contact = response.contact
            val number = contact.phoneNumber.cleaned()
            "Call ${contact.displayName.trim()}\n${number ?: "No number saved."}"
        }

        is MiraResponse.MessagePrepared -> buildString {
            if (response.app == com.rayluxeo.aida.domain.model.MessagingApp.WHATSAPP) {
                append("WhatsApp to ${response.contact.displayName.trim()}")
            } else {
                append("Message to ${response.contact.displayName.trim()}")
            }
            response.body.cleaned()?.let { append("\n\"$it\"") }
        }

        is MiraResponse.AmbiguousTask -> ambiguousDisplay(
            query = response.title,
            candidates = response.candidates,
            noun = "task",
            ask = "Which task do you mean?",
        )

        is MiraResponse.AmbiguousClient -> ambiguousDisplay(
            query = response.query,
            candidates = response.candidates,
            noun = "client",
            ask = "Which client do you mean?",
        )

        is MiraResponse.AmbiguousContact -> ambiguousDisplay(
            query = response.query,
            candidates = response.candidates.map { contactLine(it) },
            noun = "contact",
            ask = "Which contact do you mean?",
        )

        is MiraResponse.TaskDeleted -> "Deleted: ${response.title.trim()}"
        is MiraResponse.RemindersFound -> listDisplay(response.count, response.titles, REMINDERS)
        is MiraResponse.ReminderSnoozed -> "Snoozed: ${response.title.trim()}"
        is MiraResponse.ReminderCancelled -> "Cancelled reminder: ${response.title.trim()}"
        is MiraResponse.FactForgotten -> "Forgotten: ${response.subject.trim()}"
        is MiraResponse.AmbiguousReminder -> ambiguousDisplay(
            query = response.query,
            candidates = response.candidates,
            noun = "reminder",
            ask = "Which reminder do you mean?",
        )
        is MiraResponse.AmbiguousFact -> ambiguousDisplay(
            query = response.query,
            candidates = response.facts,
            noun = "fact",
            ask = "Which fact do you mean?",
        )

        is MiraResponse.PermissionNeeded ->
            "${permission(response.capability)}\n${permissionNextStep(response.capability)}"

        MiraResponse.Abandoned -> "Left it."
        MiraResponse.Greeting -> greetingLine(style)
        is MiraResponse.Conversational -> response.answer
        is MiraResponse.NotUnderstood -> response.input.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "I didn't catch that.\nHeard: \"${it.clipped(ECHO_LIMIT)}\"" }
            ?: "I didn't catch that."

        is MiraResponse.Problem -> problemDisplay(response.code)
    }

    /**
     * A repeating alarm is read back by its clock time and its repeat. Naming the next day as
     * well would be true and useless — "tomorrow at 7 am, every day" says nothing the repeat has
     * not already said, and it invites Abu to read it as a one-off.
     */
    private fun alarmMoment(response: MiraResponse.AlarmSet): String =
        repeatPhrase(response.repeatDays, EnglishRepeatWording)
            ?.let { "${clockPhrase(times.at(response.triggerAt))}, $it" }
            ?: whenPhrase(response.triggerAt)

    private fun crossing(trigger: PlaceTrigger): String = when (trigger) {
        PlaceTrigger.ENTER -> "get to"
        PlaceTrigger.EXIT -> "leave"
    }

    /**
     * The saved places, as far as one sentence will carry them. Past the second, the count is
     * said rather than the names — the same line is already carrying the name Mira did not know.
     */
    private fun placesSpeak(known: List<String>): String {
        val shown = known.take(SPOKEN_PLACE_COUNT).map { it.forSpeech() }
        val remaining = known.size - shown.size
        return if (remaining > 0) {
            "${shown.joinToString(", ")} and $remaining more"
        } else {
            joinWithAnd(shown)
        }
    }

    private fun listSpeak(count: Int, items: List<String>, counted: Counted): String {
        val spoken = items.take(SPOKEN_TITLE_COUNT).map { it.forSpeech() }
        return when {
            count == 0 -> counted.none
            spoken.isEmpty() && count == 1 -> "One ${counted.one}."
            spoken.isEmpty() -> "$count ${counted.many}."
            count == 1 -> "One ${counted.one} — ${spoken.first()}."
            else -> "$count ${counted.many} — ${joinWithAnd(spoken)}."
        }
    }

    private fun listDisplay(count: Int, items: List<String>, counted: Counted): String {
        if (count == 0) return counted.none
        val shown = items.take(SPOKEN_TITLE_COUNT)
        return buildString {
            append(if (count == 1) "1 ${counted.one}" else "$count ${counted.many}")
            shown.forEach { append("\n• ${it.trim()}") }
            val remaining = count - shown.size
            if (remaining > 0) append("\nand $remaining more")
        }
    }

    private fun memoriesDisplay(response: MiraResponse.MemoriesFound): String {
        if (response.count == 0) return "Nothing remembered about that."
        val shown = response.facts.take(SPOKEN_TITLE_COUNT)
        return buildString {
            append("Remembered:")
            shown.forEach { append("\n• ${it.trim()}") }
            val remaining = response.count - shown.size
            if (remaining > 0) append("\nand $remaining more")
        }
    }

    private fun ambiguousSpeak(candidates: List<String>, ask: String): String {
        val spoken = candidates.take(SPOKEN_TITLE_COUNT).map { it.forSpeech() }
        return if (spoken.isEmpty()) ask else "Which one — ${joinWithOr(spoken)}?"
    }

    /** Naming the candidates is Mira declining to guess, so every one of them is listed. */
    private fun ambiguousDisplay(
        query: String,
        candidates: List<String>,
        noun: String,
        ask: String,
    ): String {
        if (candidates.isEmpty()) return ask
        val trimmed = query.trim()
        val header =
            if (trimmed.isEmpty()) "More than one $noun matches:"
            else "More than one $noun matches \"$trimmed\":"
        return buildString {
            append(header)
            candidates.forEach { append("\n• ${it.trim()}") }
            append("\nWhich one?")
        }
    }

    private fun briefDisplay(brief: DailyBrief): String = buildString {
        append(greeting(brief.dayPart))
        val counts = buildList {
            if (brief.overdueCount > 0) add("Overdue: ${brief.overdueCount}")
            if (brief.dueTodayCount > 0) add("Due today: ${brief.dueTodayCount}")
            if (brief.openTaskCount > 0) add("Open: ${brief.openTaskCount}")
            if (brief.completedTodayCount > 0) add("Done today: ${brief.completedTodayCount}")
        }
        append('\n')
        append(if (counts.isEmpty()) "Nothing due today." else counts.joinToString(" · "))
        val title = brief.nextReminderTitle?.trim()?.takeIf { it.isNotEmpty() }
        val at = brief.nextReminderAt
        if (title != null && at != null) {
            append("\nNext reminder: $title, ${whenPhrase(at)}")
        }
    }

    /**
     * NIGHT deliberately shares EVENING's greeting: "good night" is a farewell in English, and
     * Mira opening a brief with a goodbye would read as a mistake.
     */
    private fun greeting(part: DayPart): String = when (part) {
        DayPart.MORNING -> "Good morning."
        DayPart.AFTERNOON -> "Good afternoon."
        DayPart.EVENING -> "Good evening."
        DayPart.NIGHT -> "Good evening."
    }

    /**
     * Mira greeting Abu back — the time of day, his name, and an opening, never a flat "hello".
     *
     * [ConversationStyle.CONCISE] keeps the time-of-day and the name and drops the invitation;
     * there is no truthful extra line a greeting could add, so DETAILED reads as BALANCED does.
     */
    private fun greetingLine(style: ConversationStyle): String {
        val opener = "${greeting(times.currentDayPart()).removeSuffix(".")}, Abu"
        return when (style) {
            ConversationStyle.CONCISE -> "$opener."
            ConversationStyle.BALANCED, ConversationStyle.DETAILED -> "$opener. What can I do for you?"
        }
    }

    // The repeat is said out loud, because "every Monday" and "next Monday" would otherwise get the
    // same reply and Abu could not tell which one he got. CONCISE drops the "Added" lead-in; the
    // title and its timing, which are the facts, stay in every style.
    private fun taskCreatedSpeak(response: MiraResponse.TaskCreated, style: ConversationStyle): String {
        val title = response.title.forSpeech()
        val due = response.dueAt
        return when (style) {
            ConversationStyle.CONCISE -> when {
                due != null && response.repeats -> "$title — from ${whenPhrase(due)}, repeating."
                due != null -> "$title — ${whenPhrase(due)}."
                response.repeats -> "$title — repeating."
                else -> "$title."
            }
            ConversationStyle.BALANCED, ConversationStyle.DETAILED -> when {
                due != null && response.repeats ->
                    "Added. $title, from ${whenPhrase(due)}, repeating."
                due != null -> "Added. $title, ${whenPhrase(due)}."
                response.repeats -> "Added — $title, repeating."
                else -> "Added — $title."
            }
        }
    }

    private fun incomeRecordedSpeak(response: MiraResponse.IncomeRecorded, style: ConversationStyle): String {
        val amount = money(response.amount)
        val source = response.source.cleaned()?.forSpeech()
        return when (style) {
            ConversationStyle.CONCISE -> if (source != null) "$amount from $source." else "$amount."
            ConversationStyle.BALANCED, ConversationStyle.DETAILED ->
                if (source != null) "Income recorded — $amount from $source."
                else "Income recorded — $amount."
        }
    }

    private fun expenseRecordedSpeak(response: MiraResponse.ExpenseRecorded, style: ConversationStyle): String {
        val amount = money(response.amount)
        val description = response.description.cleaned()?.forSpeech()
        return when (style) {
            ConversationStyle.CONCISE -> if (description != null) "$amount, $description." else "$amount."
            ConversationStyle.BALANCED, ConversationStyle.DETAILED ->
                if (description != null) "Expense recorded — $amount, $description."
                else "Expense recorded — $amount."
        }
    }

    private fun incomeSummarySpeak(response: MiraResponse.IncomeSummaryReport, style: ConversationStyle): String {
        val total = money(response.totalReceived)
        return when (style) {
            ConversationStyle.CONCISE -> "$total."
            ConversationStyle.BALANCED -> "Income this period: $total across ${response.count} entries."
            ConversationStyle.DETAILED ->
                "Income this period: $total across ${response.count} entries. " +
                    "That covers every income entry logged for the period."
        }
    }

    private fun expenseSummarySpeak(response: MiraResponse.ExpenseSummaryReport, style: ConversationStyle): String {
        val cat = response.category?.name?.lowercase() ?: "total"
        val total = money(response.totalSpent)
        return when (style) {
            ConversationStyle.CONCISE -> "$total."
            ConversationStyle.BALANCED -> "$cat expenses: $total across ${response.count} items."
            ConversationStyle.DETAILED ->
                "$cat expenses: $total across ${response.count} items. ${expenseScopeLine(response.category)}"
        }
    }

    private fun invoiceOutstandingSpeak(
        response: MiraResponse.InvoiceOutstandingReport,
        style: ConversationStyle,
    ): String {
        val client = response.clientName.forSpeech()
        val isClear = response.outstanding.amount.compareTo(java.math.BigDecimal.ZERO) == 0
        val outstanding = money(response.outstanding)
        return when (style) {
            ConversationStyle.CONCISE -> if (isClear) "Nothing outstanding." else "$outstanding outstanding."
            ConversationStyle.BALANCED ->
                if (isClear) "$client has no outstanding invoices."
                else "$client has $outstanding outstanding."
            ConversationStyle.DETAILED ->
                if (isClear) "$client has no outstanding invoices."
                else "$client has $outstanding outstanding, out of ${money(response.total)} invoiced."
        }
    }

    private fun clientStatementSpeak(
        statement: com.rayluxeo.aida.domain.model.ClientStatement,
        style: ConversationStyle,
    ): String {
        val client = statement.clientName.forSpeech()
        return when {
            statement.invoiceCount == 0 -> "$client has no issued invoices yet."
            statement.currencyMixed ->
                "$client has invoices in more than one currency, so I can't total them here."
            else -> {
                val owed = statement.outstanding!!
                val invoiced = money(statement.totalInvoiced!!)
                val outstanding = money(owed)
                val isClear = owed.amount.signum() == 0
                when (style) {
                    ConversationStyle.CONCISE ->
                        if (isClear) "Nothing outstanding." else "$outstanding outstanding."
                    ConversationStyle.BALANCED ->
                        if (isClear) "$client is all paid up, on $invoiced invoiced."
                        else "$client owes $outstanding of $invoiced invoiced."
                    ConversationStyle.DETAILED ->
                        "$client: $invoiced invoiced, ${money(statement.totalPaid!!)} paid, " +
                            "$outstanding outstanding."
                }
            }
        }
    }

    private fun clientStatementDisplay(
        statement: com.rayluxeo.aida.domain.model.ClientStatement,
        style: ConversationStyle,
    ): String {
        val client = statement.clientName.trim()
        return when {
            statement.invoiceCount == 0 -> "$client\nNo issued invoices yet."
            statement.currencyMixed ->
                "$client\nInvoices in more than one currency — totals cannot be combined."
            else -> {
                val owed = statement.outstanding!!
                val invoiced = money(statement.totalInvoiced!!)
                val outstanding = money(owed)
                when (style) {
                    ConversationStyle.CONCISE ->
                        if (owed.amount.signum() == 0) "Nothing outstanding."
                        else "$outstanding outstanding."
                    ConversationStyle.BALANCED ->
                        "$client\nInvoiced: $invoiced\nOutstanding: $outstanding"
                    ConversationStyle.DETAILED ->
                        "$client\nInvoiced: $invoiced\nPaid: ${money(statement.totalPaid!!)}\n" +
                            "Outstanding: $outstanding"
                }
            }
        }
    }

    private fun incomeSummaryDisplay(response: MiraResponse.IncomeSummaryReport, style: ConversationStyle): String {
        val total = money(response.totalReceived)
        return when (style) {
            ConversationStyle.CONCISE -> total
            ConversationStyle.BALANCED -> "Income: $total\nCount: ${response.count}"
            ConversationStyle.DETAILED ->
                "Income: $total\nCount: ${response.count}\nEvery income entry in the period is counted."
        }
    }

    private fun expenseSummaryDisplay(response: MiraResponse.ExpenseSummaryReport, style: ConversationStyle): String {
        val cat = response.category?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Total"
        val total = money(response.totalSpent)
        return when (style) {
            ConversationStyle.CONCISE -> total
            ConversationStyle.BALANCED -> "$cat Expenses: $total\nCount: ${response.count}"
            ConversationStyle.DETAILED ->
                "$cat Expenses: $total\nCount: ${response.count}\n${expenseScopeLine(response.category)}"
        }
    }

    private fun invoiceOutstandingDisplay(
        response: MiraResponse.InvoiceOutstandingReport,
        style: ConversationStyle,
    ): String {
        val client = response.clientName.trim()
        val isClear = response.outstanding.amount.compareTo(java.math.BigDecimal.ZERO) == 0
        val outstanding = money(response.outstanding)
        return when (style) {
            ConversationStyle.CONCISE -> if (isClear) "Nothing outstanding." else "$outstanding outstanding."
            ConversationStyle.BALANCED ->
                if (isClear) "$client has no outstanding invoices."
                else "$client has $outstanding outstanding."
            ConversationStyle.DETAILED ->
                if (isClear) "$client has no outstanding invoices."
                else "$client has $outstanding outstanding.\nOut of ${money(response.total)} invoiced."
        }
    }

    /** The scope a summary total covers, named plainly so DETAILED adds fact rather than flourish. */
    private fun expenseScopeLine(category: com.rayluxeo.aida.domain.model.ExpenseCategory?): String =
        category?.name?.lowercase()
            ?.let { "Every $it expense in the period is counted." }
            ?: "Every expense in the period is counted."

    /** Overdue work is named first, because that is the part Abu can still be surprised by. */
    private fun taskSentence(brief: DailyBrief): String = when {
        brief.overdueCount > 0 && brief.dueTodayCount > 0 ->
            "${brief.overdueCount} overdue and ${brief.dueTodayCount} due today."
        brief.overdueCount > 0 -> "${brief.overdueCount} overdue."
        brief.dueTodayCount > 0 -> "${brief.dueTodayCount} due today."
        brief.openTaskCount > 0 -> "Nothing due today, ${brief.openTaskCount} open."
        else -> "Nothing due today."
    }

    private fun reminderSentence(brief: DailyBrief): String {
        val title = brief.nextReminderTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
        val at = brief.nextReminderAt ?: return ""
        return " Next, ${title.forSpeech()}, ${whenPhrase(at)}."
    }

    /**
     * One line, naming the capability and what it buys. Abu asked for something reasonable and
     * the phone said no; repeating that in three sentences would be nagging.
     */
    private fun permission(capability: MiraCapability): String = when (capability) {
        MiraCapability.CONTACTS -> "I need your contacts before I can look up a name or a number."
        MiraCapability.CALENDAR -> "I need your calendar before I can put anything in it."
        MiraCapability.EXACT_ALARMS ->
            "I need the exact alarm permission to ring at the minute you asked for."
        MiraCapability.NOTIFICATIONS -> "I need notification permission before a reminder can reach you."
        MiraCapability.MICROPHONE -> "I need the microphone before I can listen."
    }

    /**
     * What Abu does next, which is NOT the same for every capability.
     *
     * An alarm is the exception and the difference matters. The alarm row is written whether or
     * not the operating system would take it — `SetAlarmUseCase` keeps it and clears only the
     * moment it was armed for — and the Alarms screen re-arms everything the instant the
     * permission comes back. So "ask again" would be wrong twice over: the alarm already exists,
     * and asking again would make a second one. Everything else here really did nothing, so
     * asking again is exactly right.
     */
    private fun permissionNextStep(capability: MiraCapability): String = when (capability) {
        MiraCapability.EXACT_ALARMS ->
            "It is saved either way — allow it in Settings and it will set itself."
        MiraCapability.CONTACTS,
        MiraCapability.CALENDAR,
        MiraCapability.NOTIFICATIONS,
        MiraCapability.MICROPHONE,
        -> "Open Settings, grant it, then ask again."
    }

    private fun problem(code: MiraErrorCode): String = when (code) {
        MiraErrorCode.VALIDATION -> "Something was missing there — try again with a name and a time."
        MiraErrorCode.NOT_FOUND -> "I couldn't find that one."
        MiraErrorCode.STORAGE -> "I couldn't save that — try once more."
        MiraErrorCode.NOT_UNDERSTOOD -> "I didn't catch that."
        MiraErrorCode.ENGINE_UNAVAILABLE ->
            "The online engine is unreachable — check the connection, or carry on offline."
        MiraErrorCode.PERMISSION_REQUIRED -> "I need permission for that — grant it in Settings."
        MiraErrorCode.AUTHENTICATION_FAILED -> "That didn't unlock — try again."
        MiraErrorCode.UNKNOWN -> "Something went wrong — try again."
    }

    /** The two codes Abu can actually resolve get the extra line the transcript has room for. */
    private fun problemDisplay(code: MiraErrorCode): String = when (code) {
        MiraErrorCode.ENGINE_UNAVAILABLE ->
            "The online engine is unreachable.\n" +
                "Check the connection, or carry on — tasks, reminders and the brief all work offline."
        MiraErrorCode.PERMISSION_REQUIRED ->
            "I need permission for that.\nOpen Settings, grant it, then ask again."
        else -> problem(code)
    }

    /** The currency is named by its code: a bare figure for a total Abu is billing is not enough. */
    private fun money(value: Money): String =
        "${value.currency.currencyCode} ${moneyFigure(value)}"

    private fun prayerName(prayer: Prayer): String = when (prayer) {
        Prayer.FAJR -> "Fajr"
        Prayer.DHUHR -> "Dhuhr"
        Prayer.ASR -> "Asr"
        Prayer.MAGHRIB -> "Maghrib"
        Prayer.ISHA -> "Isha"
        Prayer.TAHAJJUD -> "Tahajjud"
    }

    /** The next prayer's moment reads "at 3 pm" when it is today, and names the day otherwise. */
    private fun atPhrase(instant: Instant): String {
        val time = times.at(instant)
        return if (time.offset == DayOffset.TODAY) "at ${clockPhrase(time)}" else whenPhrase(instant)
    }

    private fun dayWordForDate(date: LocalDate): String = when (times.offsetOf(date)) {
        DayOffset.TODAY -> "today"
        DayOffset.TOMORROW -> "tomorrow"
        DayOffset.YESTERDAY -> "yesterday"
        DayOffset.OTHER -> {
            val month = date.month.getDisplayName(TextStyle.FULL, Locale.US)
            val year = if (date.year != times.today().year) " ${date.year}" else ""
            "on ${date.dayOfMonth} $month$year"
        }
    }

    private fun trackingMonthWord(from: LocalDate?): String {
        val month = from?.let { YearMonth.from(it) } ?: YearMonth.from(times.today())
        if (month == YearMonth.from(times.today())) return "This month"
        val name = month.month.getDisplayName(TextStyle.FULL, Locale.US)
        val year = if (month.year != times.today().year) " ${month.year}" else ""
        return "In $name$year"
    }

    private fun prayerTrackingSpeak(response: MiraResponse.PrayerTrackingReport): String {
        val per = response.perPrayer
        return if (per == null) {
            val day = dayWordForDate(response.from ?: times.today())
            val base = "You prayed ${response.completed} of ${response.possible} prayers $day."
            if (response.tahajjudNights > 0) "$base Tahajjud as well." else base
        } else {
            val base = "${trackingMonthWord(response.from)} you completed ${response.completed} of " +
                "${response.possible} prayers, all five on ${plural(response.daysAllFive.toLong(), "day")}."
            if (response.tahajjudNights > 0) {
                "$base Tahajjud on ${plural(response.tahajjudNights.toLong(), "night")}."
            } else {
                base
            }
        }
    }

    private fun prayerTrackingDisplay(response: MiraResponse.PrayerTrackingReport): String {
        val per = response.perPrayer
        return if (per == null) {
            buildString {
                val day = dayWordForDate(response.from ?: times.today()).replaceFirstChar { it.uppercase() }
                append("$day: ${response.completed} of ${response.possible} prayed")
                if (response.tahajjudNights > 0) append("\nTahajjud: prayed")
            }
        } else {
            buildString {
                append("${trackingMonthWord(response.from)}: ${response.completed} of ${response.possible}")
                append("\nAll five: ${plural(response.daysAllFive.toLong(), "day")}")
                per.forEach { (prayer, count) -> append("\n${prayerName(prayer)}: $count") }
                if (response.tahajjudNights > 0) {
                    append("\nTahajjud: ${plural(response.tahajjudNights.toLong(), "night")}")
                }
            }
        }
    }

    private fun lengthPhrase(duration: Duration): String {
        val total = maxOf(duration.seconds, 0L)
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        val parts = buildList {
            if (hours > 0) add(plural(hours, "hour"))
            if (minutes > 0) add(plural(minutes, "minute"))
            // Seconds beside hours are noise in a spoken length, and a timer that long is not set
            // to the second anyway.
            if (seconds > 0 && hours == 0L) add(plural(seconds, "second"))
        }
        return if (parts.isEmpty()) plural(0L, "second") else parts.joinToString(" ")
    }

    private fun plural(value: Long, noun: String): String =
        if (value == 1L) "1 $noun" else "$value ${noun}s"

    /** Blank is the same as absent in everything Abu types, and neither earns a line of its own. */
    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun contactLine(contact: Contact): String = contact.phoneNumber.cleaned()
        ?.let { "${contact.displayName.trim()} — $it" }
        ?: contact.displayName.trim()

    private fun whenPhrase(instant: Instant): String {
        val time = times.at(instant)
        return "${dayPhrase(time)} at ${clockPhrase(time)}"
    }

    private fun dayPhrase(time: WallTime): String = when (time.offset) {
        DayOffset.TODAY -> "today"
        DayOffset.TOMORROW -> "tomorrow"
        DayOffset.YESTERDAY -> "yesterday"
        DayOffset.OTHER -> "on ${datePhrase(time)}"
    }

    private fun datePhrase(time: WallTime): String {
        val month = time.local.month.getDisplayName(TextStyle.FULL, Locale.US)
        val day = time.local.dayOfMonth
        return if (time.sameYear) "$day $month" else "$day $month ${time.local.year}"
    }

    /** Minutes are padded by hand rather than by String.format, which follows the default locale. */
    private fun clockPhrase(time: WallTime): String {
        val meridiem = if (time.local.hour < 12) "am" else "pm"
        return if (time.minute == 0) {
            "${time.hour12} $meridiem"
        } else {
            val minute = if (time.minute < 10) "0${time.minute}" else "${time.minute}"
            "${time.hour12}:$minute $meridiem"
        }
    }

    private fun joinWithAnd(items: List<String>): String = join(items, "and")

    private fun joinWithOr(items: List<String>): String = join(items, "or")

    private fun join(items: List<String>, conjunction: String): String = when (items.size) {
        0 -> ""
        1 -> items.first()
        else -> "${items.dropLast(1).joinToString(", ")} $conjunction ${items.last()}"
    }

    /** One kind of thing Mira counts, so the five search results share a single shape. */
    private data class Counted(val one: String, val many: String, val none: String)

    private companion object {
        /** Long enough to show Abu what was heard, short enough to keep the transcript tidy. */
        const val ECHO_LIMIT = 120

        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3600L

        val TASKS = Counted("task", "tasks", "No matching tasks.")
        val NOTES = Counted("note", "notes", "No matching notes.")
        val CLIENTS = Counted("client", "clients", "No matching clients.")
        val CONTACTS = Counted("contact", "contacts", "No matching contacts.")
        val REMINDERS = Counted("reminder", "reminders", "No upcoming reminders.")
    }
}
