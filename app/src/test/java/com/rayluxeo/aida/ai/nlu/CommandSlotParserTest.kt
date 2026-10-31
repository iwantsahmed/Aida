package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock.Companion.dhaka
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.ContactTarget
import com.rayluxeo.aida.domain.command.TaskTarget
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import org.junit.Test

/**
 * The slot parser is given the intent, so these tests state it outright rather than going
 * through the classifier. What is under test is what Mira does with the rest of the sentence —
 * and, just as much, what she refuses to do when a slot the command needs is not there.
 */
class CommandSlotParserTest {

    private val clock = FixedMiraClock()
    private val parser = CommandSlotParser(
        dateTime = MiraDateTimeParser(clock),
        times = TimeExpressionParser(clock),
        currency = CurrencyExpressionParser(BanglaNumberParser(), EnglishNumberParser()),
        titles = TitleExtractor(),
    )

    private fun fill(
        intent: MiraIntent,
        raw: String,
        normalized: String,
        language: SpokenLanguage = SpokenLanguage.ENGLISH,
    ): AppCommand? {
        val request = CommandRequest(
            raw = raw,
            normalized = normalized,
            language = language,
            script = TextScript.LATIN,
            source = InputSource.TEXT,
        )
        return parser.fill(intent, request, parser.scan(normalized))?.command
    }

    // ---------------------------------------------------------------- work

    @Test
    fun `builds a task with a due date and one without`() {
        assertThat(fill(MiraIntent.CREATE_TASK, "Add a task to call Ahmed tomorrow at 10", "create a task to call ahmed tomorrow at 10"))
            .isEqualTo(AppCommand.CreateTask(title = "call Ahmed", dueAt = dhaka(2026, 3, 11, 10)))
        assertThat(fill(MiraIntent.CREATE_TASK, "Add a task to call Ahmed", "create a task to call ahmed"))
            .isEqualTo(AppCommand.CreateTask(title = "call Ahmed", dueAt = null))
    }

    @Test
    fun `reads urgency into the priority instead of into the title`() {
        assertThat(fill(MiraIntent.CREATE_TASK, "Urgent task to call Ahmed", "urgent task to call ahmed"))
            .isEqualTo(AppCommand.CreateTask(title = "call Ahmed", priority = TaskPriority.HIGH))
    }

    @Test
    fun `builds an update that names what changes`() {
        assertThat(
            fill(MiraIntent.UPDATE_TASK, "Reschedule the Ahmed call to tomorrow at 10", "reschedule the ahmed call to tomorrow at 10")
        ).isEqualTo(
            AppCommand.UpdateTask(TaskTarget.ByTitle("Ahmed call"), dueAt = dhaka(2026, 3, 11, 10))
        )
    }

    /** An update that changes nothing is a sentence Mira misread, not a no-op Abu asked for. */
    @Test
    fun `refuses an update that names nothing to change`() {
        assertThat(fill(MiraIntent.UPDATE_TASK, "Update the Ahmed task", "update the ahmed task")).isNull()
    }

    @Test
    fun `builds a completion from the task Abu named`() {
        assertThat(fill(MiraIntent.COMPLETE_TASK, "call Ahmed kaj ta hoye geche", "call ahmed task ta done", SpokenLanguage.BENGALI))
            .isEqualTo(AppCommand.CompleteTask(TaskTarget.ByTitle("call Ahmed")))
    }

    @Test
    fun `refuses a completion that names no task`() {
        assertThat(fill(MiraIntent.COMPLETE_TASK, "task done", "task done")).isNull()
    }

    @Test
    fun `reads a bare search as everything still open`() {
        assertThat(fill(MiraIntent.SEARCH_TASKS, "kaj gulo khujo", "task gulo search", SpokenLanguage.BENGALI))
            .isEqualTo(AppCommand.SearchTasks(query = null, filter = TaskFilter.OPEN))
        assertThat(fill(MiraIntent.SEARCH_TASKS, "search invoice", "search invoice"))
            .isEqualTo(AppCommand.SearchTasks(query = "invoice", filter = TaskFilter.OPEN))
    }

    @Test
    fun `builds the daily brief from nothing at all`() {
        assertThat(fill(MiraIntent.GET_DAILY_BRIEF, "daily brief dao", "brief give"))
            .isEqualTo(AppCommand.GetDailyBrief)
    }

    // ---------------------------------------------------------------- time

    @Test
    fun `builds a reminder`() {
        assertThat(fill(MiraIntent.CREATE_REMINDER, "Remind me tomorrow at 10 to call Ahmed.", "remind me tomorrow at 10 to call ahmed"))
            .isEqualTo(AppCommand.CreateReminder("call Ahmed", dhaka(2026, 3, 11, 10)))
    }

    @Test
    fun `refuses a reminder with no moment`() {
        assertThat(fill(MiraIntent.CREATE_REMINDER, "Remind me to call Ahmed", "remind me to call ahmed")).isNull()
    }

    /**
     * A reminder Abu gave a moment but no subject is still a reminder. His own sentence stands in
     * for the title, exactly as it does for an alarm's label — refusing would be Mira declining
     * work she can do.
     */
    @Test
    fun `builds a reminder with a moment and no subject, titled with what Abu said`() {
        assertThat(fill(MiraIntent.CREATE_REMINDER, "reminder dao tomorrow at 10", "remind give tomorrow at 10"))
            .isEqualTo(AppCommand.CreateReminder("reminder dao tomorrow at 10", dhaka(2026, 3, 11, 10)))
    }

    // ------------------------------------------------- reminders that wait at a place

    /**
     * "Remind me" is one intent whether it waits on a clock or on arriving somewhere, so the
     * place comes out of the slots. The place phrase is taken out of the sentence before the
     * title is built, exactly as a moment is.
     */
    @Test
    fun `builds a place reminder from an English sentence`() {
        assertThat(
            fill(
                MiraIntent.CREATE_REMINDER,
                "Remind me to buy printer ink when I get near the office.",
                "remind me to buy printer ink when i get near the office",
            )
        ).isEqualTo(AppCommand.CreatePlaceReminder("buy printer ink", "the office", PlaceTrigger.ENTER))
    }

    /** Bengali puts the place in front of the marker, where English puts it behind. */
    @Test
    fun `builds a place reminder from the Banglish form`() {
        assertThat(
            fill(
                MiraIntent.CREATE_REMINDER,
                "Office e pouchhale printer ink kinte mone koriye dao",
                "office e pouchhale printer ink kinte remind",
                SpokenLanguage.BENGALI,
            )
        ).isEqualTo(AppCommand.CreatePlaceReminder("printer ink kinte", "office", PlaceTrigger.ENTER))
    }

    /** In script the locative is glued to the place, so there is no separate particle to take. */
    @Test
    fun `builds a place reminder written in Bengali script`() {
        assertThat(
            fill(
                MiraIntent.CREATE_REMINDER,
                "অফিসে পৌঁছালে printer ink আনতে মনে করিয়ে দিও",
                "অফিসে পৌঁছালে printer ink আনতে remind",
                SpokenLanguage.BENGALI,
            )
        ).isEqualTo(AppCommand.CreatePlaceReminder("printer ink আনতে", "অফিসে", PlaceTrigger.ENTER))
    }

    @Test
    fun `reads leaving a place as a reminder that fires on the way out`() {
        assertThat(
            fill(
                MiraIntent.CREATE_REMINDER,
                "Remind me to lock the door when I leave the office",
                "remind me to lock the door when i leave the office",
            )
        ).isEqualTo(AppCommand.CreatePlaceReminder("lock the door", "the office", PlaceTrigger.EXIT))
    }

    @Test
    fun `reads leaving a place in Banglish as well`() {
        assertThat(
            fill(
                MiraIntent.CREATE_REMINDER,
                "Office theke berole Ahmed ke call korar reminder dao",
                "office theke berole ahmed to call do remind give",
                SpokenLanguage.BENGALI,
            )
        ).isEqualTo(AppCommand.CreatePlaceReminder("call Ahmed", "office", PlaceTrigger.EXIT))
    }

    /**
     * The regression that matters. A reminder with a time and no place is still a timed
     * reminder — a place phrase read into one would give Abu a reminder that never fires, and
     * nothing on screen would tell him so.
     */
    @Test
    fun `leaves a reminder with a moment alone`() {
        assertThat(fill(MiraIntent.CREATE_REMINDER, "Remind me to call Ahmed at 5 pm", "remind me to call ahmed at 5 pm"))
            .isEqualTo(AppCommand.CreateReminder("call Ahmed", dhaka(2026, 3, 10, 17)))
    }

    /** "Near" is a word long before it is a marker, and naming no place it stays one. */
    @Test
    fun `does not read the word near as a place`() {
        assertThat(
            fill(
                MiraIntent.CREATE_REMINDER,
                "Remind me tomorrow at 10 that the deadline is near",
                "remind me tomorrow at 10 that the deadline is near",
            )
        ).isEqualTo(AppCommand.CreateReminder("deadline is near", dhaka(2026, 3, 11, 10)))
    }

    /**
     * A bare time still makes an alarm, and it makes one with no label at all. Abu's own
     * sentence must not stand in the way a reminder's title does: the label is what he reads
     * back in his alarm list, and "Kal shokal 6 tay alarm dao" there would name the alarm after
     * the request that set it. Naming an unlabelled alarm belongs to the wording layer.
     */
    @Test
    fun `builds an alarm from a bare time and leaves it unlabelled`() {
        assertThat(fill(MiraIntent.CREATE_ALARM, "Kal shokal 6 tay alarm dao", "tomorrow morning 6 at alarm give", SpokenLanguage.BENGALI))
            .isEqualTo(AppCommand.CreateAlarm("", dhaka(2026, 3, 11, 6)))
    }

    @Test
    fun `keeps a subject Abu did give an alarm`() {
        assertThat(fill(MiraIntent.CREATE_ALARM, "Set an alarm to catch the flight tomorrow at 6", "set an alarm to catch the flight tomorrow at 6"))
            .isEqualTo(AppCommand.CreateAlarm("catch the flight", dhaka(2026, 3, 11, 6)))
    }

    /** A name Abu gave the alarm is the one thing in the sentence that is not framing. */
    @Test
    fun `keeps the name of a prayer alarm`() {
        assertThat(fill(MiraIntent.CREATE_ALARM, "Set an alarm for fajr at 5 am", "set an alarm for fajr at 5 am"))
            .isEqualTo(AppCommand.CreateAlarm("fajr", dhaka(2026, 3, 11, 5)))
    }

    /**
     * "Wake me up" is one verb, so the particle goes with it. Left behind, it labelled the alarm
     * "up" — which is what Abu saw in his alarm list.
     */
    @Test
    fun `never labels an alarm with the particle of a phrasal verb`() {
        assertThat(fill(MiraIntent.CREATE_ALARM, "Wake me up tomorrow at 6", "wake me up tomorrow at 6"))
            .isEqualTo(AppCommand.CreateAlarm("", dhaka(2026, 3, 11, 6)))
    }

    @Test
    fun `refuses an alarm with no time`() {
        assertThat(fill(MiraIntent.CREATE_ALARM, "alarm dao", "alarm give")).isNull()
    }

    // ------------------------------------------------------- alarms that repeat

    /**
     * The days come from the repeat the scan already read, and the words that said so are gone
     * from the label by the time it is built — an alarm called "wake me up every weekday" is
     * the repeat stated twice, once where it works and once where Abu has to read it.
     */
    @Test
    fun `reads every weekday into the days a repeating alarm rings on`() {
        val command = fill(MiraIntent.CREATE_ALARM, "Wake me up at 7 am every weekday", "wake me up at 7 am every weekday")
        assertThat(command).isEqualTo(
            AppCommand.CreateAlarm(
                label = "",
                triggerAt = dhaka(2026, 3, 11, 7),
                repeatDays = setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                ),
            )
        )
    }

    @Test
    fun `keeps a named alarm's label while reading its repeat`() {
        val command = fill(
            MiraIntent.CREATE_ALARM,
            "Set an alarm to catch the flight every weekday at 6",
            "set an alarm to catch the flight every weekday at 6",
        ) as AppCommand.CreateAlarm
        assertThat(command.label).isEqualTo("catch the flight")
        assertThat(command.repeatDays).containsExactly(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }

    @Test
    fun `reads every day as all seven`() {
        val command = fill(
            MiraIntent.CREATE_ALARM,
            "Wake me every day at 6",
            "wake me every day at 6",
        ) as AppCommand.CreateAlarm
        assertThat(command.repeatDays).containsExactlyElementsIn(DayOfWeek.entries)
    }

    @Test
    fun `reads named days into a repeating alarm`() {
        val command = fill(
            MiraIntent.CREATE_ALARM,
            "Set an alarm every monday and thursday at 6",
            "set an alarm every monday and thursday at 6",
        ) as AppCommand.CreateAlarm
        assertThat(command.repeatDays).containsExactly(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
    }

    /** No repeat stated is a one-time alarm, which is what no days means. */
    @Test
    fun `leaves an alarm Abu stated no repeat for as a one-time alarm`() {
        val command = fill(
            MiraIntent.CREATE_ALARM,
            "Set an alarm for 7 am",
            "set an alarm for 7 am",
        ) as AppCommand.CreateAlarm
        assertThat(command.repeatDays).isEmpty()
    }

    /**
     * A repeat an alarm clock cannot hold is dropped rather than approximated. "Every month at
     * seven" is a recurring reminder, and no set of weekdays says it.
     */
    @Test
    fun `leaves a repeat no alarm clock can hold off the days`() {
        val monthly = fill(
            MiraIntent.CREATE_ALARM,
            "Set an alarm every month at 6",
            "set an alarm every month at 6",
        ) as AppCommand.CreateAlarm
        assertThat(monthly.repeatDays).isEmpty()

        val counted = fill(
            MiraIntent.CREATE_ALARM,
            "Set an alarm every 3 days at 6",
            "set an alarm every 3 days at 6",
        ) as AppCommand.CreateAlarm
        assertThat(counted.repeatDays).isEmpty()
    }

    @Test
    fun `builds a timer from a span`() {
        assertThat(fill(MiraIntent.CREATE_TIMER, "Set a timer for 20 minutes", "set a timer for 20 minutes"))
            .isEqualTo(AppCommand.CreateTimer("Set a timer for 20 minutes", Duration.ofMinutes(20)))
    }

    @Test
    fun `refuses a timer with no span`() {
        assertThat(fill(MiraIntent.CREATE_TIMER, "timer dao", "timer give")).isNull()
    }

    @Test
    fun `builds a calendar event`() {
        assertThat(
            fill(MiraIntent.CREATE_CALENDAR_EVENT, "Create a calendar event with Ahmed tomorrow at 4 pm", "create a calendar event with ahmed tomorrow at 4 pm")
        ).isEqualTo(AppCommand.CreateCalendarEvent("event with Ahmed", dhaka(2026, 3, 11, 16)))
    }

    @Test
    fun `refuses a calendar event with no moment`() {
        assertThat(fill(MiraIntent.CREATE_CALENDAR_EVENT, "meeting with Ahmed", "meeting with ahmed")).isNull()
    }

    // ---------------------------------------------------------------- knowledge

    @Test
    fun `builds a note that is all body`() {
        assertThat(fill(MiraIntent.CREATE_NOTE, "Make a note that Ahmed wants the Dhaka office", "create a note that ahmed wants the dhaka office"))
            .isEqualTo(AppCommand.CreateNote(title = null, body = "Ahmed wants the Dhaka office"))
    }

    @Test
    fun `refuses a note with nothing in it`() {
        assertThat(fill(MiraIntent.CREATE_NOTE, "note dao", "note give")).isNull()
    }

    @Test
    fun `builds a note search`() {
        assertThat(fill(MiraIntent.SEARCH_NOTES, "Find the note about Dhaka", "search the note about dhaka"))
            .isEqualTo(AppCommand.SearchNotes("Dhaka"))
        assertThat(fill(MiraIntent.SEARCH_NOTES, "note khujo", "note search"))
            .isEqualTo(AppCommand.SearchNotes(""))
    }

    /** A memory is about someone or something named; recall matches on that before the wording. */
    @Test
    fun `takes a memory's subject from the name in it`() {
        assertThat(fill(MiraIntent.STORE_MEMORY, "Remember that Ahmed prefers morning calls", "remember that ahmed prefers morning call"))
            .isEqualTo(AppCommand.StoreMemory(subject = "Ahmed", fact = "Ahmed prefers morning call"))
    }

    @Test
    fun `falls back to the opening word when a memory names nobody`() {
        assertThat(fill(MiraIntent.STORE_MEMORY, "Remember the office rent is due", "remember the office rent is due"))
            .isEqualTo(AppCommand.StoreMemory(subject = "office", fact = "office rent is due"))
    }

    @Test
    fun `builds a memory search`() {
        assertThat(fill(MiraIntent.SEARCH_MEMORY, "Search my memory for Ahmed", "search my memory for ahmed"))
            .isEqualTo(AppCommand.SearchMemory("Ahmed"))
    }

    // ---------------------------------------------------------------- business

    @Test
    fun `builds a client`() {
        assertThat(fill(MiraIntent.CREATE_CLIENT, "Create a client Ahmed Rahman", "create a client ahmed rahman"))
            .isEqualTo(AppCommand.CreateClient(name = "Ahmed Rahman"))
    }

    @Test
    fun `takes a phone number out of a client's name`() {
        assertThat(fill(MiraIntent.CREATE_CLIENT, "Create a client Ahmed Rahman 01712345678", "create a client ahmed rahman 01712345678"))
            .isEqualTo(AppCommand.CreateClient(name = "Ahmed Rahman", phone = "01712345678"))
    }

    /** An e-mail Abu dictates is usually built out of the name he just said. */
    @Test
    fun `takes an e-mail out of a client's name without taking the name with it`() {
        assertThat(fill(MiraIntent.CREATE_CLIENT, "Create a client Ahmed at ahmed@rayluxeo.com", "create a client ahmed at ahmed rayluxeo com"))
            .isEqualTo(AppCommand.CreateClient(name = "Ahmed", email = "ahmed@rayluxeo.com"))
    }

    @Test
    fun `builds a client search`() {
        assertThat(fill(MiraIntent.SEARCH_CLIENTS, "Find the client Ahmed", "search the client ahmed"))
            .isEqualTo(AppCommand.SearchClients("Ahmed"))
    }

    /**
     * "show my clients" / "show my notes" name no one in particular, so the query comes through empty
     * and the repository reads that as "list them all" — the search must not refuse for want of a
     * term, the same way SEARCH_TASKS lists every open task.
     */
    @Test
    fun `a bare listing searches every client and every note`() {
        assertThat(fill(MiraIntent.SEARCH_CLIENTS, "show my clients", "show my client"))
            .isEqualTo(AppCommand.SearchClients(""))
        assertThat(fill(MiraIntent.SEARCH_NOTES, "show my notes", "show my note"))
            .isEqualTo(AppCommand.SearchNotes(""))
    }

    @Test
    fun `builds an invoice draft, splitting the client from the work`() {
        assertThat(
            fill(MiraIntent.CREATE_INVOICE_DRAFT, "Create an invoice for Ahmed for the website redesign for 50000 taka", "create an invoice for ahmed for the website redesign for 50000 taka")
        ).isEqualTo(
            AppCommand.CreateInvoiceDraft(
                client = ClientTarget.ByName("Ahmed"),
                amount = Money.of("50000"),
                description = "for the website redesign",
            )
        )
    }

    @Test
    fun `refuses an invoice draft that names no client`() {
        assertThat(fill(MiraIntent.CREATE_INVOICE_DRAFT, "invoice banao", "invoice create")).isNull()
    }

    // ---------------------------------------------------------------- money

    @Test
    fun `builds a payment with the client, the reference and how it arrived`() {
        assertThat(
            fill(MiraIntent.RECORD_PAYMENT, "Record a payment of 50000 taka from Ahmed by bank transfer for invoice INV-001", "record a payment of 50000 taka from ahmed by bank transfer for invoice inv 001")
        ).isEqualTo(
            AppCommand.RecordPayment(
                amount = Money.of("50000"),
                client = ClientTarget.ByName("Ahmed"),
                invoiceNumber = "INV-001",
                method = PaymentMethod.BANK_TRANSFER,
            )
        )
    }

    @Test
    fun `refuses any money command with no amount`() {
        assertThat(fill(MiraIntent.RECORD_PAYMENT, "payment peyechi", "payment peyechi")).isNull()
        assertThat(fill(MiraIntent.RECORD_EXPENSE, "khoroch hoyeche", "expense done")).isNull()
        assertThat(fill(MiraIntent.RECORD_INCOME, "income peyechi", "income peyechi")).isNull()
    }

    @Test
    fun `builds an expense with what it was for`() {
        assertThat(fill(MiraIntent.RECORD_EXPENSE, "Spent 1200 taka on lunch", "expense 1200 taka on lunch"))
            .isEqualTo(AppCommand.RecordExpense(Money.of("1200"), "lunch", ExpenseCategory.FOOD))
    }

    @Test
    fun `labels an expense with Abu's own sentence when he named nothing else`() {
        assertThat(fill(MiraIntent.RECORD_EXPENSE, "50000 taka khoroch", "50000 taka expense", SpokenLanguage.BENGALI))
            .isEqualTo(AppCommand.RecordExpense(Money.of("50000"), "50000 taka khoroch", ExpenseCategory.OTHER))
    }

    @Test
    fun `builds income with its source`() {
        assertThat(fill(MiraIntent.RECORD_INCOME, "Received 50000 taka from Ahmed as income", "received 50000 taka from ahmed as income"))
            .isEqualTo(
                AppCommand.RecordIncome(
                    amount = Money.of("50000"),
                    source = "Ahmed",
                    client = ClientTarget.ByName("Ahmed"),
                )
            )
    }

    @Test
    fun `passes a calculation through without evaluating it`() {
        assertThat(fill(MiraIntent.PERFORM_CALCULATION, "what is 12 * 4", "what is 12 * 4"))
            .isEqualTo(AppCommand.PerformCalculation("12 * 4"))
        assertThat(fill(MiraIntent.PERFORM_CALCULATION, "hisab koro", "calculate do")).isNull()
    }

    // ---------------------------------------------------------------- the phone itself

    @Test
    fun `builds a contact lookup`() {
        assertThat(fill(MiraIntent.FIND_CONTACT, "What is the number for Ahmed", "what is the number for ahmed"))
            .isEqualTo(AppCommand.FindContact("Ahmed", com.rayluxeo.aida.domain.model.ContactField.PHONE))
        assertThat(fill(MiraIntent.FIND_CONTACT, "Find Ahmed", "find ahmed"))
            .isEqualTo(AppCommand.FindContact("Ahmed", com.rayluxeo.aida.domain.model.ContactField.ANY))
    }

    @Test
    fun `builds a call by name and by number`() {
        assertThat(fill(MiraIntent.PREPARE_CALL, "Call Ahmed", "call ahmed"))
            .isEqualTo(AppCommand.PrepareCall(ContactTarget.ByName("Ahmed")))
        assertThat(fill(MiraIntent.PREPARE_CALL, "Call 01712345678", "call 01712345678"))
            .isEqualTo(AppCommand.PrepareCall(ContactTarget.ByNumber("01712345678")))
    }

    @Test
    fun `refuses a call that names nobody`() {
        assertThat(fill(MiraIntent.PREPARE_CALL, "call koro", "call do")).isNull()
    }

    @Test
    fun `splits a message into who it is for and what it says`() {
        assertThat(fill(MiraIntent.PREPARE_MESSAGE, "Message Ahmed that I am late", "message ahmed that i am late"))
            .isEqualTo(AppCommand.PrepareMessage(ContactTarget.ByName("Ahmed"), "I am late"))
    }

    @Test
    fun `hands over a message with no body rather than none at all`() {
        assertThat(fill(MiraIntent.PREPARE_MESSAGE, "Message Ahmed", "message ahmed"))
            .isEqualTo(AppCommand.PrepareMessage(ContactTarget.ByName("Ahmed"), body = null))
    }

    // ---------------------------------------------------------------- the scan

    @Test
    fun `reports what it found so the classifier can use it`() {
        val timed = parser.scan("remind me tomorrow at 10 to call ahmed")
        assertThat(timed.signals.hasTemporal).isTrue()
        assertThat(timed.signals.hasMoney).isFalse()

        val paid = parser.scan("expense 1200 taka on lunch")
        assertThat(paid.signals.hasMoney).isTrue()

        val counted = parser.scan("what is 12 * 4")
        assertThat(counted.signals.hasArithmetic).isTrue()

        val spanned = parser.scan("set a timer for 20 minutes")
        assertThat(spanned.signals.hasDuration).isTrue()
    }

    /** A bare number is a quantity. Only a stated currency makes it an amount of money. */
    @Test
    fun `does not read every number as money`() {
        assertThat(parser.scan("remind me tomorrow at 10 to call ahmed").signals.hasMoney).isFalse()
        assertThat(parser.scan("call 3 people").signals.hasMoney).isFalse()
    }

    @Test
    fun `carries the time's confidence out with the command`() {
        val request = CommandRequest(
            raw = "Remind me tomorrow at 10 to call Ahmed",
            normalized = "remind me tomorrow at 10 to call ahmed",
            language = SpokenLanguage.ENGLISH,
            script = TextScript.LATIN,
            source = InputSource.TEXT,
        )
        val scan = parser.scan(request.normalized)
        val slotted = requireNotNull(parser.fill(MiraIntent.CREATE_REMINDER, request, scan))
        assertThat(slotted.temporalConfidence).isEqualTo(scan.temporal?.confidence)
        assertThat(slotted.temporalConfidence).isNotNull()
    }

    @Test
    fun `leaves the time's confidence off a command that carries no time`() {
        val request = CommandRequest(
            raw = "Add a task to call Ahmed",
            normalized = "create a task to call ahmed",
            language = SpokenLanguage.ENGLISH,
            script = TextScript.LATIN,
            source = InputSource.TEXT,
        )
        val slotted = requireNotNull(
            parser.fill(MiraIntent.CREATE_TASK, request, parser.scan(request.normalized))
        )
        assertThat(slotted.temporalConfidence).isNull()
    }

    // ------------------------------------------------- a framing verb is not a description

    @Test
    fun `does not label an expense with half of the verb that asked for it`() {
        // Reported from the device: this recorded the right amount and described it as "likhe",
        // which is half of "likhe rakho" — "write it down". Nothing in the sentence describes the
        // expense, so the description falls back to Abu's own words rather than to a fragment.
        assertThat(
            fill(
                MiraIntent.RECORD_EXPENSE,
                "50 hajar takar expense likhe rakho",
                "50 thousand taka expense likhe rakho",
                SpokenLanguage.BENGALI,
            )
        ).isEqualTo(
            AppCommand.RecordExpense(
                Money.of("50000"),
                "50 hajar takar expense likhe rakho",
                ExpenseCategory.OTHER,
            )
        )
    }

    @Test
    fun `keeps what an expense was actually for when the verb is stripped`() {
        assertThat(
            fill(
                MiraIntent.RECORD_EXPENSE,
                "internet bill 1200 takar expense likhe rakho",
                "internet bill 1200 taka expense likhe rakho",
                SpokenLanguage.BENGALI,
            )
        ).isEqualTo(
            AppCommand.RecordExpense(Money.of("1200"), "internet bill", ExpenseCategory.UTILITIES)
        )
    }

    @Test
    fun `a framing verb never reaches a reminder title`() {
        val command = fill(
            MiraIntent.CREATE_REMINDER,
            "Kal shokal 10 tay Ahmed ke call korar reminder likhe rakho",
            "tomorrow morning 10 at ahmed to call do remind likhe rakho",
            SpokenLanguage.BENGALI,
        )
        assertThat((command as AppCommand.CreateReminder).title).isEqualTo("call Ahmed")
    }

    // ------------------------------------------------------- repeats

    @Test
    fun `takes the repeat out of the sentence before anything else is read from it`() {
        val scan = parser.scan("protidin website check do task create")
        assertThat(scan.recurrence?.rule).isEqualTo(RecurrenceRule.Daily())
        assertThat(scan.recurrence?.remainder).isEqualTo("website check do task create")
        assertThat(scan.temporal).isNull()
    }

    @Test
    fun `keeps the word that said how often out of the task's title`() {
        assertThat(
            fill(
                MiraIntent.CREATE_TASK,
                "protidin website check korar task banao",
                "protidin website check do task create",
                SpokenLanguage.BENGALI,
            )
        ).isEqualTo(
            AppCommand.CreateTask(
                title = "website check",
                dueAt = null,
                // "Protidin" is kept out of the title AND carried as the repeat. Dropping it was
                // Mira quietly creating a one-off for something Abu asked to happen every day.
                recurrence = RecurrenceRule.Daily(),
            )
        )
    }

    /**
     * A counted repeat puts a number next to a period word, which is the same shape as a day of
     * the month. Left in the sentence it reads as one, and the last assertion here is that
     * misreading itself — it is the whole reason the repeat is claimed first.
     */
    @Test
    fun `does not read a counted repeat as a day of the month`() {
        val quarterly = "every 5 month rayluxeo review do task create"
        val scan = parser.scan(quarterly)
        assertThat(scan.recurrence?.rule).isEqualTo(RecurrenceRule.Monthly(interval = 5))
        assertThat(scan.temporal).isNull()
        assertThat(MiraDateTimeParser(clock).parse(quarterly)).isNotNull()
    }

    @Test
    fun `leaves a one-off with no repeat at all`() {
        assertThat(parser.scan("remind me tomorrow at 10 to call ahmed").recurrence).isNull()
        assertThat(parser.scan("next friday website complete do task create").recurrence).isNull()
    }

    // ------------------------------------------- the four sentences from the brief

    /**
     * Bengali script, a relative weekday, and an English noun inside it. "complete" is what the
     * new task is ABOUT: a sentence that also says "add" is Abu writing a task down, not marking
     * one done.
     */
    @Test
    fun `adds a task for next Friday from a Bengali sentence`() {
        assertThat(
            fill(
                MiraIntent.CREATE_TASK,
                "আগামী শুক্রবার website complete করার task add করো.",
                "next friday website complete do task create করো",
                SpokenLanguage.BENGALI,
            )
        ).isEqualTo(AppCommand.CreateTask(title = "website complete", dueAt = dhaka(2026, 3, 20, 9)))
    }

    /**
     * Pure Banglish with no word for "task" anywhere in it. The obligation "hobe" — this has to
     * be done — is the whole of what makes it a request rather than a remark, and it belongs to
     * the asking, so it does not reach the title.
     */
    @Test
    fun `adds a task from an obligation that never says the word task`() {
        assertThat(
            fill(
                MiraIntent.CREATE_TASK,
                "Kal Ahmed ke proposal pathate hobe.",
                "tomorrow ahmed to proposal pathate hobe",
                SpokenLanguage.BENGALI,
            )
        ).isEqualTo(
            AppCommand.CreateTask(title = "proposal pathate Ahmed", dueAt = dhaka(2026, 3, 11, 9))
        )
    }

    /**
     * English, repeating, and a reminder rather than a plain task. The repeat is read, and the
     * weekday it names is deliberately left in the sentence so that the series opens on the
     * coming Monday rather than on nothing at all.
     */
    @Test
    fun `reads a repeating reminder and the Monday its series opens on`() {
        val normalized = "every monday remind me to review rayluxeo"
        assertThat(parser.scan(normalized).recurrence?.rule)
            .isEqualTo(RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)))
        assertThat(
            fill(MiraIntent.CREATE_REMINDER, "Every Monday remind me to review Rayluxeo.", normalized)
        ).isEqualTo(AppCommand.CreateReminder("review Rayluxeo", dhaka(2026, 3, 16, 9)))
    }

    /** Two scripts in one sentence, and a part of the day rather than a date. */
    @Test
    fun `adds a task for tonight from a sentence written in two scripts`() {
        assertThat(
            fill(
                MiraIntent.CREATE_TASK,
                "Tonight homepage finish করার task add করো.",
                "tonight homepage finish do task create করো",
                SpokenLanguage.BENGALI,
            )
        ).isEqualTo(AppCommand.CreateTask(title = "homepage finish", dueAt = dhaka(2026, 3, 10, 21)))
    }

    @Test
    fun `a note asked for in Banglish is still a note`() {
        // Only the Bengali-script "লিখো" was a trigger, so this classified as nothing while the
        // same sentence in script became a note.
        assertThat(fill(MiraIntent.CREATE_NOTE, "ei kotha ta likho", "ei kotha ta likho", SpokenLanguage.BENGALI))
            .isEqualTo(AppCommand.CreateNote(title = null, body = "ei kotha"))
    }

    @Test
    fun `parses create calendar event in Bengali with next Tuesday at 3pm`() {
        val raw = "আগামী মঙ্গলবার ৩টায় Ahmed এর meeting add করো."
        val normalized = "next tuesday 3 pm ahmed meeting add do"
        val cmd = fill(MiraIntent.CREATE_CALENDAR_EVENT, raw, normalized, SpokenLanguage.BENGALI)
        assertThat(cmd).isInstanceOf(AppCommand.CreateCalendarEvent::class.java)
        val event = cmd as AppCommand.CreateCalendarEvent
        assertThat(event.title).contains("Ahmed")
        assertThat(event.startsAt).isEqualTo(dhaka(2026, 3, 17, 15))
    }

    @Test
    fun `parses query schedule for tomorrow`() {
        val raw = "What do I have tomorrow?"
        val normalized = "what do i have tomorrow"
        val cmd = fill(MiraIntent.QUERY_SCHEDULE, raw, normalized, SpokenLanguage.ENGLISH)
        assertThat(cmd).isInstanceOf(AppCommand.QuerySchedule::class.java)
        val query = cmd as AppCommand.QuerySchedule
        assertThat(query.from).isEqualTo(dhaka(2026, 3, 11, 0))
        assertThat(query.to).isEqualTo(dhaka(2026, 3, 12, 0))
    }

    @Test
    fun `parses find free time for Thursday`() {
        val raw = "When am I free Thursday?"
        val normalized = "when am i free thursday"
        val cmd = fill(MiraIntent.FIND_FREE_TIME, raw, normalized, SpokenLanguage.ENGLISH)
        assertThat(cmd).isInstanceOf(AppCommand.FindFreeSlots::class.java)
        val free = cmd as AppCommand.FindFreeSlots
        assertThat(free.date).isEqualTo(LocalDate.of(2026, 3, 12))
    }

    @Test
    fun `parses find contact number for Ahmed`() {
        val raw = "Find Ahmed's number."
        val normalized = "find ahmed number"
        val cmd = fill(MiraIntent.FIND_CONTACT, raw, normalized, SpokenLanguage.ENGLISH)
        assertThat(cmd).isInstanceOf(AppCommand.FindContact::class.java)
        val contact = cmd as AppCommand.FindContact
        assertThat(contact.query).isEqualTo("Ahmed")
        assertThat(contact.requestedField).isEqualTo(com.rayluxeo.aida.domain.model.ContactField.PHONE)
    }

    @Test
    fun `parses find contact email in Bengali for Ahmed`() {
        val raw = "Ahmed এর email কি?"
        val normalized = "ahmed email what"
        val cmd = fill(MiraIntent.FIND_CONTACT, raw, normalized, SpokenLanguage.BENGALI)
        assertThat(cmd).isInstanceOf(AppCommand.FindContact::class.java)
        val contact = cmd as AppCommand.FindContact
        assertThat(contact.query).isEqualTo("Ahmed")
        assertThat(contact.requestedField).isEqualTo(com.rayluxeo.aida.domain.model.ContactField.EMAIL)
    }

    @Test
    fun `parses text message to Ahmed without hinge words`() {
        val raw = "Text Ahmed I'm running ten minutes late."
        val normalized = "text ahmed i am running ten minutes late"
        val cmd = fill(MiraIntent.PREPARE_MESSAGE, raw, normalized, SpokenLanguage.ENGLISH)
        assertThat(cmd).isInstanceOf(AppCommand.PrepareMessage::class.java)
        val msg = cmd as AppCommand.PrepareMessage
        assertThat(msg.contact).isEqualTo(ContactTarget.ByName("Ahmed"))
        assertThat(msg.body).isEqualTo("I'm running ten minutes late.")
    }
}
