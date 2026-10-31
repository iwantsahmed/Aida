package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.salah.Prayer
import java.time.Duration
import org.junit.Test

/** Mira's English wording, pinned line by line against a clock fixed at 2026-09-24 09:00 Dhaka. */
class EnglishResponseRendererTest {

    private val renderer = testRenderer()

    private fun speak(response: MiraResponse) = renderer.speak(response, SpokenLanguage.ENGLISH)

    private fun display(response: MiraResponse) = renderer.display(response, SpokenLanguage.ENGLISH)

    @Test
    fun `the smarter-assistant verbs read back correctly in English`() {
        assertThat(speak(MiraResponse.TaskDeleted("Buy milk"))).contains("Buy milk")
        assertThat(display(MiraResponse.TaskDeleted("Buy milk"))).isEqualTo("Deleted: Buy milk")

        assertThat(speak(MiraResponse.RemindersFound(0, emptyList()))).isEqualTo("No upcoming reminders.")
        assertThat(display(MiraResponse.RemindersFound(2, listOf("Call bank", "Dentist")))).contains("Call bank")

        assertThat(display(MiraResponse.ReminderSnoozed("Dentist", java.time.Instant.EPOCH)))
            .isEqualTo("Snoozed: Dentist")
        assertThat(display(MiraResponse.ReminderCancelled("Dentist"))).isEqualTo("Cancelled reminder: Dentist")
        assertThat(display(MiraResponse.FactForgotten("Ahmed's tea"))).isEqualTo("Forgotten: Ahmed's tea")

        assertThat(speak(MiraResponse.AmbiguousReminder("call", listOf("Call bank", "Call Ahmed"))))
            .contains("Call bank")
        assertThat(display(MiraResponse.AmbiguousFact("ahmed", listOf("Ahmed likes tea", "Ahmed's number"))))
            .contains("Ahmed likes tea")
    }

    @Test
    fun `task due today is spoken as today`() {
        val response = MiraResponse.TaskCreated("Call Ahmed", testInstant(TEST_TODAY, 16, 30))

        assertThat(speak(response)).isEqualTo("Added. Call Ahmed, today at 4:30 pm.")
        assertThat(display(response)).isEqualTo("Added: Call Ahmed\nDue: today at 4:30 pm")
    }

    @Test
    fun `task due tomorrow is spoken as tomorrow`() {
        val response = MiraResponse.TaskCreated("Call Ahmed", testInstant(TEST_TOMORROW, 10, 0))

        assertThat(speak(response)).isEqualTo("Added. Call Ahmed, tomorrow at 10 am.")
    }

    @Test
    fun `task due yesterday is spoken as yesterday`() {
        val response = MiraResponse.TaskCreated("Call Ahmed", testInstant(TEST_YESTERDAY, 18, 0))

        assertThat(speak(response)).isEqualTo("Added. Call Ahmed, yesterday at 6 pm.")
    }

    @Test
    fun `a later date in this year is named without the year`() {
        val response = MiraResponse.TaskCreated("Call Ahmed", testInstant(TEST_LATER, 16, 30))

        assertThat(speak(response)).isEqualTo("Added. Call Ahmed, on 3 October at 4:30 pm.")
    }

    @Test
    fun `a date in another year carries the year`() {
        val response = MiraResponse.TaskCreated("Call Ahmed", testInstant(TEST_NEXT_YEAR, 9, 0))

        assertThat(speak(response)).isEqualTo("Added. Call Ahmed, on 5 January 2027 at 9 am.")
    }

    @Test
    fun `midnight and noon keep the right half of the day`() {
        val midnight = MiraResponse.TaskCreated("Call Ahmed", testInstant(TEST_TODAY, 0, 0))
        val noon = MiraResponse.TaskCreated("Call Ahmed", testInstant(TEST_TODAY, 12, 0))

        assertThat(speak(midnight)).isEqualTo("Added. Call Ahmed, today at 12 am.")
        assertThat(speak(noon)).isEqualTo("Added. Call Ahmed, today at 12 pm.")
    }

    @Test
    fun `task without a due time says nothing about time`() {
        val response = MiraResponse.TaskCreated("Call Ahmed", null)

        assertThat(speak(response)).isEqualTo("Added — Call Ahmed.")
        assertThat(display(response)).isEqualTo("Added: Call Ahmed")
    }

    @Test
    fun `next prayer is spoken with its time and the time remaining`() {
        val response = MiraResponse.NextPrayerReport(
            prayer = Prayer.ASR,
            at = testInstant(TEST_TODAY, 15, 8),
            remaining = Duration.ofMinutes(84),
        )

        assertThat(speak(response))
            .isEqualTo("Your next prayer is Asr at 3:08 pm. 1 hour 24 minutes remaining.")
    }

    @Test
    fun `completing a task is two words and the title`() {
        val response = MiraResponse.TaskCompleted("Call Ahmed")

        assertThat(speak(response)).isEqualTo("Done — Call Ahmed.")
        assertThat(display(response)).isEqualTo("Completed: Call Ahmed")
    }

    @Test
    fun `no tasks found`() {
        val response = MiraResponse.TasksFound(0, emptyList())

        assertThat(speak(response)).isEqualTo("No matching tasks.")
        assertThat(display(response)).isEqualTo("No matching tasks.")
    }

    @Test
    fun `one task found`() {
        val response = MiraResponse.TasksFound(1, listOf("Pay rent"))

        assertThat(speak(response)).isEqualTo("One task — Pay rent.")
        assertThat(display(response)).isEqualTo("1 task\n• Pay rent")
    }

    @Test
    fun `three tasks are listed with a conjunction`() {
        val response = MiraResponse.TasksFound(3, listOf("Call Ahmed", "Pay rent", "Book flight"))

        assertThat(speak(response)).isEqualTo("3 tasks — Call Ahmed, Pay rent and Book flight.")
        assertThat(display(response)).isEqualTo("3 tasks\n• Call Ahmed\n• Pay rent\n• Book flight")
    }

    @Test
    fun `more than three tasks speaks the count and lists three`() {
        val response = MiraResponse.TasksFound(
            count = 5,
            titles = listOf("Call Ahmed", "Pay rent", "Book flight", "Renew lease", "File taxes"),
        )

        assertThat(speak(response)).isEqualTo("5 tasks — Call Ahmed, Pay rent and Book flight.")
        assertThat(display(response)).endsWith("\nand 2 more")
    }

    @Test
    fun `a count without titles still speaks the count`() {
        assertThat(speak(MiraResponse.TasksFound(4, emptyList()))).isEqualTo("4 tasks.")
        assertThat(speak(MiraResponse.TasksFound(1, emptyList()))).isEqualTo("One task.")
    }

    @Test
    fun `reminder names the task and the time`() {
        val response = MiraResponse.ReminderCreated("Call Ahmed", testInstant(TEST_TOMORROW, 10, 0))

        assertThat(speak(response)).isEqualTo("Reminder set — Call Ahmed, tomorrow at 10 am.")
        assertThat(display(response)).isEqualTo("Reminder: Call Ahmed\nWhen: tomorrow at 10 am")
    }

    @Test
    fun `brief leads with overdue work`() {
        val response = MiraResponse.BriefReady(
            testBrief(
                dayPart = DayPart.MORNING,
                openTaskCount = 5,
                dueTodayCount = 3,
                overdueCount = 2,
                completedTodayCount = 1,
                nextReminderTitle = "Call Ahmed",
                nextReminderAt = testInstant(TEST_TODAY, 16, 30),
            )
        )

        assertThat(speak(response))
            .isEqualTo("Good morning. 2 overdue and 3 due today. Next, Call Ahmed, today at 4:30 pm.")
        assertThat(display(response)).isEqualTo(
            "Good morning.\n" +
                "Overdue: 2 · Due today: 3 · Open: 5 · Done today: 1\n" +
                "Next reminder: Call Ahmed, today at 4:30 pm"
        )
    }

    @Test
    fun `brief with only work due today omits overdue`() {
        val response = MiraResponse.BriefReady(
            testBrief(dayPart = DayPart.AFTERNOON, openTaskCount = 4, dueTodayCount = 2)
        )

        assertThat(speak(response)).isEqualTo("Good afternoon. 2 due today.")
    }

    @Test
    fun `brief with open work but nothing due says so`() {
        val response = MiraResponse.BriefReady(testBrief(dayPart = DayPart.EVENING, openTaskCount = 4))

        assertThat(speak(response)).isEqualTo("Good evening. Nothing due today, 4 open.")
    }

    @Test
    fun `an empty brief is short and plain`() {
        val response = MiraResponse.BriefReady(testBrief(dayPart = DayPart.EVENING))

        assertThat(speak(response)).isEqualTo("Good evening. Nothing due today.")
        assertThat(display(response)).isEqualTo("Good evening.\nNothing due today.")
    }

    @Test
    fun `night does not greet with a farewell`() {
        val response = MiraResponse.BriefReady(testBrief(dayPart = DayPart.NIGHT))

        assertThat(speak(response)).startsWith("Good evening.")
    }

    @Test
    fun `calculation speaks the result and shows the working`() {
        val response = MiraResponse.CalculationResult("1200 + 50", "1,250")

        assertThat(speak(response)).isEqualTo("That's 1,250.")
        assertThat(display(response)).isEqualTo("1200 + 50 = 1,250")
    }

    @Test
    fun `ambiguity names the candidates and asks`() {
        val response = MiraResponse.AmbiguousTask(
            title = "call ahmed",
            candidates = listOf("Call Ahmed", "Call Ahmed's office"),
        )

        assertThat(speak(response)).isEqualTo("Which one — Call Ahmed or Call Ahmed's office?")
        assertThat(display(response)).isEqualTo(
            "More than one task matches \"call ahmed\":\n" +
                "• Call Ahmed\n" +
                "• Call Ahmed's office\n" +
                "Which one?"
        )
    }

    @Test
    fun `ambiguity without candidates still asks rather than guessing`() {
        val response = MiraResponse.AmbiguousTask(title = "call", candidates = emptyList())

        assertThat(speak(response)).isEqualTo("Which task do you mean?")
        assertThat(display(response)).isEqualTo("Which task do you mean?")
    }

    @Test
    fun `not understood is honest and does not blame him`() {
        val response = MiraResponse.NotUnderstood("mmm the thing")

        assertThat(speak(response)).isEqualTo("I didn't catch that — say it again?")
        assertThat(display(response)).isEqualTo("I didn't catch that.\nHeard: \"mmm the thing\"")
        assertThat(display(response)).doesNotContain("you")
    }

    @Test
    fun `not understood with no heard text omits the echo`() {
        assertThat(display(MiraResponse.NotUnderstood("   "))).isEqualTo("I didn't catch that.")
    }

    @Test
    fun `every error code has wording`() {
        MiraErrorCode.entries.forEach { code ->
            val response = MiraResponse.Problem(code)
            assertThat(speak(response)).isNotEmpty()
            assertThat(speak(response)).doesNotContain("\n")
            assertThat(display(response)).isNotEmpty()
        }
    }

    @Test
    fun `permission and engine problems say what to do`() {
        val permission = MiraResponse.Problem(MiraErrorCode.PERMISSION_REQUIRED)
        val engine = MiraResponse.Problem(MiraErrorCode.ENGINE_UNAVAILABLE)

        assertThat(speak(permission)).contains("Settings")
        assertThat(display(permission)).contains("Settings")
        assertThat(speak(engine)).contains("offline")
        assertThat(display(engine)).contains("offline")
    }

    @Test
    fun `updating a task says so rather than adding it again`() {
        val response = MiraResponse.TaskUpdated("Call Ahmed", testInstant(TEST_TOMORROW, 10, 0))

        assertThat(speak(response)).isEqualTo("Updated. Call Ahmed, tomorrow at 10 am.")
        assertThat(display(response)).isEqualTo("Updated: Call Ahmed\nDue: tomorrow at 10 am")
    }

    @Test
    fun `updating a task without a time says nothing about time`() {
        val response = MiraResponse.TaskUpdated("Call Ahmed", null)

        assertThat(speak(response)).isEqualTo("Updated — Call Ahmed.")
        assertThat(display(response)).isEqualTo("Updated: Call Ahmed")
    }

    @Test
    fun `an alarm names its label and when it rings`() {
        val response = MiraResponse.AlarmSet("Wake up", testInstant(TEST_TOMORROW, 6, 30))

        assertThat(speak(response)).isEqualTo("Alarm set — Wake up, tomorrow at 6:30 am.")
        assertThat(display(response)).isEqualTo("Alarm: Wake up\nWhen: tomorrow at 6:30 am")
    }

    @Test
    fun `an alarm with no label still says when it rings`() {
        val response = MiraResponse.AlarmSet("  ", testInstant(TEST_TOMORROW, 6, 30))

        assertThat(speak(response)).isEqualTo("Alarm set for tomorrow at 6:30 am.")
        assertThat(display(response)).isEqualTo("Alarm set\nWhen: tomorrow at 6:30 am")
    }

    @Test
    fun `a timer carries its length and its end`() {
        val response = MiraResponse.TimerStarted(
            label = "Tea",
            duration = Duration.ofMinutes(25),
            endsAt = testInstant(TEST_TODAY, 9, 25),
        )

        assertThat(speak(response)).isEqualTo("Timer started — Tea, 25 minutes, ending today at 9:25 am.")
        assertThat(display(response))
            .isEqualTo("Timer: Tea\nLength: 25 minutes\nEnds: today at 9:25 am")
    }

    @Test
    fun `an hour and a half is said in hours and minutes`() {
        val response = MiraResponse.TimerStarted(
            label = "",
            duration = Duration.ofMinutes(90),
            endsAt = testInstant(TEST_TODAY, 10, 30),
        )

        assertThat(speak(response))
            .isEqualTo("Timer started — 1 hour 30 minutes, ending today at 10:30 am.")
        assertThat(display(response)).isEqualTo("Timer: 1 hour 30 minutes\nEnds: today at 10:30 am")
    }

    @Test
    fun `a timer under a minute is counted in seconds`() {
        val response = MiraResponse.TimerStarted(
            label = "",
            duration = Duration.ofSeconds(45),
            endsAt = testInstant(TEST_TODAY, 9, 1),
        )

        assertThat(speak(response)).isEqualTo("Timer started — 45 seconds, ending today at 9:01 am.")
    }

    @Test
    fun `a note with a title and a note without one`() {
        assertThat(speak(MiraResponse.NoteCreated("Lease terms")))
            .isEqualTo("Note saved — Lease terms.")
        assertThat(display(MiraResponse.NoteCreated("Lease terms"))).isEqualTo("Note: Lease terms")
        assertThat(speak(MiraResponse.NoteCreated(null))).isEqualTo("Note saved.")
        assertThat(display(MiraResponse.NoteCreated(null))).isEqualTo("Note saved.")
    }

    @Test
    fun `notes found are counted and listed`() {
        assertThat(speak(MiraResponse.NotesFound(0, emptyList()))).isEqualTo("No matching notes.")
        assertThat(speak(MiraResponse.NotesFound(1, listOf("Lease terms"))))
            .isEqualTo("One note — Lease terms.")

        val many = MiraResponse.NotesFound(
            count = 5,
            titles = listOf("Lease terms", "Warehouse", "Bank", "Insurance", "Tax"),
        )

        assertThat(speak(many)).isEqualTo("5 notes — Lease terms, Warehouse and Bank.")
        assertThat(display(many))
            .isEqualTo("5 notes\n• Lease terms\n• Warehouse\n• Bank\nand 2 more")
    }

    @Test
    fun `a stored memory repeats the fact back`() {
        val response = MiraResponse.MemoryStored("Ahmed", "prefers morning calls")

        assertThat(speak(response)).isEqualTo("Remembered — Ahmed: prefers morning calls.")
        assertThat(display(response)).isEqualTo("Remembered: Ahmed\nprefers morning calls")
    }

    @Test
    fun `memories found are listed, and none is said plainly`() {
        assertThat(speak(MiraResponse.MemoriesFound(0, emptyList())))
            .isEqualTo("Nothing remembered about that.")
        assertThat(display(MiraResponse.MemoriesFound(0, emptyList())))
            .isEqualTo("Nothing remembered about that.")

        val one = MiraResponse.MemoriesFound(1, listOf("prefers morning calls"))
        assertThat(speak(one)).isEqualTo("Remembered — prefers morning calls.")
        assertThat(display(one)).isEqualTo("Remembered:\n• prefers morning calls")

        val many = MiraResponse.MemoriesFound(
            count = 4,
            facts = listOf("bank is City", "office in Banani", "son at BUET", "car is grey"),
        )
        assertThat(speak(many)).isEqualTo("4 things — bank is City, office in Banani and son at BUET.")
        assertThat(display(many)).endsWith("\nand 1 more")
    }

    @Test
    fun `a new client is one line`() {
        val response = MiraResponse.ClientCreated("Rahim Traders")

        assertThat(speak(response)).isEqualTo("Client added — Rahim Traders.")
        assertThat(display(response)).isEqualTo("Client: Rahim Traders")
    }

    @Test
    fun `clients found are counted and listed`() {
        assertThat(speak(MiraResponse.ClientsFound(0, emptyList())))
            .isEqualTo("No matching clients.")
        assertThat(speak(MiraResponse.ClientsFound(1, listOf("Rahim Traders"))))
            .isEqualTo("One client — Rahim Traders.")

        val many = MiraResponse.ClientsFound(
            count = 5,
            names = listOf("Rahim Traders", "Karim Ltd", "Sultan Co", "Delta", "Everest"),
        )

        assertThat(speak(many)).isEqualTo("5 clients — Rahim Traders, Karim Ltd and Sultan Co.")
        assertThat(display(many)).endsWith("\nand 2 more")
    }

    @Test
    fun `an invoice draft names the client and the total`() {
        val response = MiraResponse.InvoiceDraftCreated(
            number = "INV-2026-0042",
            clientName = "Rahim Traders",
            total = taka("150000"),
        )

        assertThat(speak(response))
            .isEqualTo("Draft invoice INV-2026-0042 — Rahim Traders, BDT 1,50,000.")
        assertThat(display(response)).isEqualTo(
            "Draft invoice INV-2026-0042\nClient: Rahim Traders\nTotal: BDT 1,50,000"
        )
    }

    @Test
    fun `a payment names whoever it can be attributed to`() {
        val fromClient = MiraResponse.PaymentRecorded(taka("50000"), "Rahim Traders", "INV-2026-0042")
        assertThat(speak(fromClient)).isEqualTo("Payment recorded — BDT 50,000 from Rahim Traders.")
        assertThat(display(fromClient)).isEqualTo(
            "Payment: BDT 50,000\nFrom: Rahim Traders\nInvoice: INV-2026-0042"
        )

        val againstInvoice = MiraResponse.PaymentRecorded(taka("1234.56"), null, "INV-2026-0042")
        assertThat(speak(againstInvoice))
            .isEqualTo("Payment recorded — BDT 1,234.56 against INV-2026-0042.")

        val bare = MiraResponse.PaymentRecorded(taka("500"), null, null)
        assertThat(speak(bare)).isEqualTo("Payment recorded — BDT 500.")
        assertThat(display(bare)).isEqualTo("Payment: BDT 500")
    }

    @Test
    fun `an invoice-draft confirmation reads back the client and the amount, not a client search`() {
        val withAmount = MiraResponse.InvoiceDraftRequested("Rahim Traders", taka("150000"), "logo design")
        assertThat(speak(withAmount))
            .isEqualTo("Draft an invoice for Rahim Traders — BDT 1,50,000 for logo design.")
        assertThat(display(withAmount)).isEqualTo(
            "Draft invoice\nClient: Rahim Traders\nTotal: BDT 1,50,000\nFor: logo design"
        )

        val noAmount = MiraResponse.InvoiceDraftRequested("Rahim Traders", null, null)
        assertThat(speak(noAmount)).isEqualTo("Draft an invoice for Rahim Traders.")
    }

    @Test
    fun `a percentage payment confirmation reads back the share and the invoice, never a zero amount`() {
        val resp = MiraResponse.PaymentByPercentageRequested(
            java.math.BigDecimal("50"), "Rahim Traders", "INV-2026-0042",
        )
        assertThat(speak(resp)).isEqualTo("Record 50% of INV-2026-0042 from Rahim Traders.")
        assertThat(display(resp)).isEqualTo("Payment: 50%\nInvoice: INV-2026-0042\nFrom: Rahim Traders")
    }

    @Test
    fun `an expense and an income name the amount and the other side`() {
        val expense = MiraResponse.ExpenseRecorded(taka("2500.50"), "office rent")
        assertThat(speak(expense)).isEqualTo("Expense recorded — BDT 2,500.50, office rent.")
        assertThat(display(expense)).isEqualTo("Expense: BDT 2,500.50\nFor: office rent")

        val income = MiraResponse.IncomeRecorded(taka("225000"), "consulting")
        assertThat(speak(income)).isEqualTo("Income recorded — BDT 2,25,000 from consulting.")
        assertThat(display(income)).isEqualTo("Income: BDT 2,25,000\nFrom: consulting")
    }

    @Test
    fun `an expense with nothing written against it still states the amount`() {
        val response = MiraResponse.ExpenseRecorded(taka("2500"), " ")

        assertThat(speak(response)).isEqualTo("Expense recorded — BDT 2,500.")
        assertThat(display(response)).isEqualTo("Expense: BDT 2,500")
    }

    @Test
    fun `a calendar event says where it went and when`() {
        val response = MiraResponse.CalendarEventCreated("Site visit", testInstant(TEST_TOMORROW, 15, 0))

        assertThat(speak(response))
            .isEqualTo("Added to the calendar — Site visit, tomorrow at 3 pm.")
        assertThat(display(response)).isEqualTo("Calendar: Site visit\nWhen: tomorrow at 3 pm")
    }

    @Test
    fun `contacts found speak names and show numbers`() {
        val response = MiraResponse.ContactsFound(
            count = 2,
            contacts = listOf(testContact("Ahmed Khan"), testContact("Karim Uddin", phoneNumber = null)),
        )

        assertThat(speak(response)).isEqualTo("2 contacts — Ahmed Khan and Karim Uddin.")
        assertThat(display(response))
            .isEqualTo("2 contacts\n• Ahmed Khan — 01711-223344\n• Karim Uddin")
        assertThat(speak(MiraResponse.ContactsFound(0, emptyList())))
            .isEqualTo("No matching contacts.")
    }

    @Test
    fun `a prepared call is offered, not placed`() {
        val response = MiraResponse.CallPrepared(testContact("Ahmed Khan"))

        assertThat(speak(response)).isEqualTo("Ready to call Ahmed Khan.")
        assertThat(display(response)).isEqualTo("Call Ahmed Khan\n01711-223344")
    }

    @Test
    fun `a contact with no number is admitted rather than dialled`() {
        val response = MiraResponse.CallPrepared(testContact("Ahmed Khan", phoneNumber = null))

        assertThat(speak(response)).isEqualTo("I have Ahmed Khan, but no number for them.")
        assertThat(display(response)).isEqualTo("Call Ahmed Khan\nNo number saved.")
    }

    @Test
    fun `a prepared message is ready, not sent`() {
        val written = MiraResponse.MessagePrepared(testContact("Ahmed Khan"), "On my way")
        assertThat(speak(written)).isEqualTo("Message ready for Ahmed Khan.")
        assertThat(display(written)).isEqualTo("Message to Ahmed Khan\n\"On my way\"")

        val empty = MiraResponse.MessagePrepared(testContact("Ahmed Khan"), null)
        assertThat(speak(empty)).isEqualTo("Ready to message Ahmed Khan.")
        assertThat(display(empty)).isEqualTo("Message to Ahmed Khan")
    }

    @Test
    fun `two clients of the same name are put back to him`() {
        val response = MiraResponse.AmbiguousClient(
            query = "rahim",
            candidates = listOf("Rahim Traders", "Rahim Enterprise"),
        )

        assertThat(speak(response)).isEqualTo("Which one — Rahim Traders or Rahim Enterprise?")
        assertThat(display(response)).isEqualTo(
            "More than one client matches \"rahim\":\n" +
                "• Rahim Traders\n" +
                "• Rahim Enterprise\n" +
                "Which one?"
        )
    }

    @Test
    fun `two contacts of the same name are named with their numbers`() {
        val response = MiraResponse.AmbiguousContact(
            query = "ahmed",
            candidates = listOf(
                testContact("Ahmed Khan"),
                testContact("Ahmed Rahman", phoneNumber = "01911-556677"),
            ),
        )

        assertThat(speak(response)).isEqualTo("Which one — Ahmed Khan or Ahmed Rahman?")
        assertThat(display(response)).isEqualTo(
            "More than one contact matches \"ahmed\":\n" +
                "• Ahmed Khan — 01711-223344\n" +
                "• Ahmed Rahman — 01911-556677\n" +
                "Which one?"
        )
    }

    @Test
    fun `ambiguity with no candidates still asks rather than guessing`() {
        assertThat(speak(MiraResponse.AmbiguousClient("rahim", emptyList())))
            .isEqualTo("Which client do you mean?")
        assertThat(speak(MiraResponse.AmbiguousContact("ahmed", emptyList())))
            .isEqualTo("Which contact do you mean?")
    }

    @Test
    fun `a withheld permission says what granting it would let her do`() {
        val response = MiraResponse.PermissionNeeded(MiraCapability.CONTACTS)

        assertThat(speak(response))
            .isEqualTo("I need your contacts before I can look up a name or a number.")
        assertThat(display(response)).isEqualTo(
            "I need your contacts before I can look up a name or a number.\n" +
                "Open Settings, grant it, then ask again."
        )
    }

    @Test
    fun `every capability has wording and none of it nags`() {
        MiraCapability.entries.forEach { capability ->
            val response = MiraResponse.PermissionNeeded(capability)

            assertThat(speak(response)).isNotEmpty()
            assertThat(speak(response)).doesNotContain("\n")
            assertThat(display(response)).contains("Settings")
        }
    }

    /**
     * An alarm is kept whether or not the OS would take it, and arms itself when the permission
     * returns. Telling Abu to ask again would hand him a second alarm for the same morning.
     */
    @Test
    fun `a refused alarm is not answered by telling Abu to ask again`() {
        val alarm = MiraResponse.PermissionNeeded(MiraCapability.EXACT_ALARMS)

        assertThat(display(alarm)).doesNotContain("ask again")
        assertThat(display(alarm)).contains("saved")

        // The others really did nothing, so asking again is the correct instruction there.
        assertThat(display(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS)))
            .contains("ask again")
    }
}
