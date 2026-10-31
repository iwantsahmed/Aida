package com.rayluxeo.aida.ai.local

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.BanglaNumberParser
import com.rayluxeo.aida.ai.nlu.BanglishNormalizer
import com.rayluxeo.aida.ai.nlu.CommandClassifier
import com.rayluxeo.aida.ai.nlu.CommandSlotParser
import com.rayluxeo.aida.ai.nlu.CurrencyExpressionParser
import com.rayluxeo.aida.ai.nlu.EnglishNumberParser
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.ai.nlu.InputNormalizer
import com.rayluxeo.aida.ai.nlu.LanguageHintDetector
import com.rayluxeo.aida.ai.nlu.MiraDateTimeParser
import com.rayluxeo.aida.ai.nlu.TimeExpressionParser
import com.rayluxeo.aida.ai.nlu.TitleExtractor
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.ClientTarget
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.ContactTarget
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.TaskTarget
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.TaskFilter
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The whole on-device pipeline, end to end, in the four ways Abu writes.
 *
 * Every case here starts from raw text and ends at a structured command, so a change anywhere
 * between the tokenizer and the slot parser shows up as a failing sentence rather than as a
 * passing unit test around a broken whole.
 */
class LocalCommandEngineTest {

    private val clock = FixedMiraClock()
    private val normalizer = InputNormalizer(LanguageHintDetector(), BanglishNormalizer())
    private val engine = LocalCommandEngine(
        classifier = CommandClassifier(),
        slots = CommandSlotParser(
            dateTime = MiraDateTimeParser(clock),
            times = TimeExpressionParser(clock),
            currency = CurrencyExpressionParser(BanglaNumberParser(), EnglishNumberParser()),
            titles = TitleExtractor(),
        ),
    )

    private suspend fun interpret(raw: String, mode: LanguageMode = LanguageMode.AUTO): EngineResult {
        val text = normalizer.normalize(raw, mode)
        return engine.interpret(
            CommandRequest(
                raw = text.raw,
                normalized = text.normalized,
                language = text.language,
                script = text.script,
                source = InputSource.TEXT,
            )
        )
    }

    private suspend fun interpretedOf(raw: String): EngineResult.Interpreted {
        val result = interpret(raw)
        assertThat(result).isInstanceOf(EngineResult.Interpreted::class.java)
        return result as EngineResult.Interpreted
    }

    private suspend fun commandOf(raw: String): AppCommand = interpretedOf(raw).command

    @Test
    fun `identifies itself as the on-device engine`() {
        assertThat(engine.id).isEqualTo(EngineId.LOCAL)
    }

    @Test
    fun `never reports itself unavailable`() = runTest {
        assertThat(engine.isAvailable()).isTrue()
    }

    // ------------------------------------------------- the acceptance: one request, four ways

    @Test
    fun `understands the English reminder`() = runTest {
        assertThat(commandOf(ENGLISH)).isEqualTo(EXPECTED_REMINDER)
    }

    @Test
    fun `understands the Bengali script reminder`() = runTest {
        assertThat(commandOf(BENGALI_SCRIPT)).isEqualTo(EXPECTED_REMINDER)
    }

    @Test
    fun `understands the Banglish reminder`() = runTest {
        assertThat(commandOf(BANGLISH)).isEqualTo(EXPECTED_REMINDER)
    }

    @Test
    fun `understands the mixed English and Banglish reminder`() = runTest {
        assertThat(commandOf(MIXED)).isEqualTo(EXPECTED_REMINDER)
    }

    @Test
    fun `gives all four phrasings the same command and a confidence Mira can act on`() = runTest {
        val results = listOf(ENGLISH, BENGALI_SCRIPT, BANGLISH, MIXED).map { interpretedOf(it) }
        assertThat(results.map { it.command }.distinct()).containsExactly(EXPECTED_REMINDER)
        for (result in results) {
            assertThat(result.confidence).isGreaterThan(EngineResult.CONFIRM_THRESHOLD)
        }
    }

    // ------------------------------------------------------------------ tasks

    @Test
    fun `creates a task in four languages`() = runTest {
        val expected = AppCommand.CreateTask(title = "call Ahmed", dueAt = null)
        assertThat(commandOf("Add a task to call Ahmed")).isEqualTo(expected)
        assertThat(commandOf("Ahmed ke call korar kaj banao")).isEqualTo(expected)
        assertThat(commandOf("Ahmed কে call করার কাজ বানাও")).isEqualTo(expected)
        assertThat(commandOf("Tomorrow er jonno Ahmed ke call korar kaj banao"))
            .isEqualTo(AppCommand.CreateTask(title = "call Ahmed", dueAt = FixedMiraClock.dhaka(2026, 3, 11, 9)))
    }

    @Test
    fun `creates a task with a due date`() = runTest {
        assertThat(commandOf("Add a task to call Ahmed tomorrow at 10")).isEqualTo(
            AppCommand.CreateTask(title = "call Ahmed", dueAt = FixedMiraClock.dhaka(2026, 3, 11, 10))
        )
    }

    @Test
    fun `completes a task in three languages`() = runTest {
        assertThat(commandOf("call Ahmed kaj ta hoye geche"))
            .isEqualTo(AppCommand.CompleteTask(TaskTarget.ByTitle("call Ahmed")))
        assertThat(commandOf("Mark the Ahmed call as done"))
            .isEqualTo(AppCommand.CompleteTask(TaskTarget.ByTitle("Ahmed call")))
        assertThat(commandOf("Ahmed এর কাজ শেষ"))
            .isEqualTo(AppCommand.CompleteTask(TaskTarget.ByTitle("Ahmed")))
    }

    @Test
    fun `updates a task in two languages`() = runTest {
        val expected = AppCommand.UpdateTask(
            target = TaskTarget.ByTitle("Ahmed call"),
            dueAt = FixedMiraClock.dhaka(2026, 3, 11, 10),
        )
        assertThat(commandOf("Reschedule the Ahmed call to tomorrow at 10")).isEqualTo(expected)
        assertThat(commandOf("Ahmed er kaj ta kal 10 tay pichiye dao")).isEqualTo(
            AppCommand.UpdateTask(
                target = TaskTarget.ByTitle("Ahmed"),
                dueAt = FixedMiraClock.dhaka(2026, 3, 11, 10),
            )
        )
    }

    @Test
    fun `searches tasks in four languages`() = runTest {
        assertThat(commandOf("search invoice"))
            .isEqualTo(AppCommand.SearchTasks(query = "invoice", filter = TaskFilter.OPEN))
        val everything = AppCommand.SearchTasks(query = null, filter = TaskFilter.OPEN)
        assertThat(commandOf("Show my pending tasks")).isEqualTo(everything)
        assertThat(commandOf("ajker kaj gulo khujo")).isEqualTo(everything)
        assertThat(commandOf("কাজগুলো দেখাও")).isEqualTo(everything)
    }

    @Test
    fun `asks for the daily brief in three languages`() = runTest {
        assertThat(commandOf("Give me the daily brief")).isEqualTo(AppCommand.GetDailyBrief)
        assertThat(commandOf("daily brief dao")).isEqualTo(AppCommand.GetDailyBrief)
        assertThat(commandOf("আজকের প্ল্যান দাও")).isEqualTo(AppCommand.GetDailyBrief)
    }

    // ------------------------------------------------------------------ time

    @Test
    fun `understands a reminder set by duration in two languages`() = runTest {
        assertThat(commandOf("Remind me in 20 minutes to drink water")).isEqualTo(
            AppCommand.CreateReminder("drink water", FixedMiraClock.dhaka(2026, 3, 10, 10, 20))
        )
        assertThat(commandOf("2 ghonta pore meeting er reminder dao")).isEqualTo(
            AppCommand.CreateReminder("meeting", FixedMiraClock.dhaka(2026, 3, 10, 12))
        )
    }

    /**
     * An alarm carries no subject, so an unnamed one keeps a BLANK label rather than echoing the
     * sentence back. Abu's own words used to stand in, which read as "Alarm: Set an alarm for
     * tomorrow at 6" in his alarm list. A blank is named where it is shown, in his language.
     */
    @Test
    fun `sets an alarm in four languages`() = runTest {
        val six = FixedMiraClock.dhaka(2026, 3, 11, 6)
        assertThat(commandOf("Set an alarm for tomorrow at 6"))
            .isEqualTo(AppCommand.CreateAlarm("", six))
        assertThat(commandOf("Kal shokal 6 tay alarm dao"))
            .isEqualTo(AppCommand.CreateAlarm("", six))
        assertThat(commandOf("আগামীকাল সকাল ৬টায় অ্যালার্ম দাও"))
            .isEqualTo(AppCommand.CreateAlarm("", six))
        assertThat(commandOf("Tomorrow shokal 6 tay alarm dio"))
            .isEqualTo(AppCommand.CreateAlarm("", six))
    }

    /** A named alarm keeps its name, which is the whole reason the label exists at all. */
    @Test
    fun `an alarm Abu named keeps its name`() = runTest {
        val five = FixedMiraClock.dhaka(2026, 3, 11, 5)
        assertThat(commandOf("Set an alarm for fajr tomorrow at 5"))
            .isEqualTo(AppCommand.CreateAlarm("fajr", five))
    }

    @Test
    fun `starts a timer in three languages`() = runTest {
        val twenty = Duration.ofMinutes(20)
        assertThat(commandOf("Set a timer for 20 minutes"))
            .isEqualTo(AppCommand.CreateTimer("Set a timer for 20 minutes", twenty))
        assertThat(commandOf("20 minit er timer dao"))
            .isEqualTo(AppCommand.CreateTimer("20 minit er timer dao", twenty))
        assertThat(commandOf("২০ মিনিট টাইমার দাও"))
            .isEqualTo(AppCommand.CreateTimer("২০ মিনিট টাইমার দাও", twenty))
    }

    @Test
    fun `creates a calendar event in three languages`() = runTest {
        val four = FixedMiraClock.dhaka(2026, 3, 11, 16)
        assertThat(commandOf("Create a calendar event with Ahmed tomorrow at 4 pm"))
            .isEqualTo(AppCommand.CreateCalendarEvent("event with Ahmed", four))
        assertThat(commandOf("Kal bikal 4 tay Ahmed er sathe meeting"))
            .isEqualTo(AppCommand.CreateCalendarEvent("Ahmed meeting", four))
        assertThat(commandOf("আগামীকাল বিকেল ৪টায় Ahmed এর সাথে মিটিং"))
            .isEqualTo(AppCommand.CreateCalendarEvent("Ahmed meeting", four))
    }

    // ------------------------------------------------------------------ knowledge

    @Test
    fun `creates a note in three languages`() = runTest {
        assertThat(commandOf("Make a note that Ahmed wants the Dhaka office"))
            .isEqualTo(AppCommand.CreateNote(title = null, body = "Ahmed wants the Dhaka office"))
        val office = AppCommand.CreateNote(title = null, body = "Ahmed Dhaka office")
        assertThat(commandOf("Ahmed er Dhaka office er note likhe rakho")).isEqualTo(office)
        assertThat(commandOf("নোট লিখো Ahmed Dhaka office")).isEqualTo(office)
    }

    @Test
    fun `searches notes in three languages`() = runTest {
        assertThat(commandOf("Find the note about Dhaka")).isEqualTo(AppCommand.SearchNotes("Dhaka"))
        assertThat(commandOf("Dhaka er note khujo")).isEqualTo(AppCommand.SearchNotes("Dhaka"))
        assertThat(commandOf("ঢাকা এর নোট খুঁজে দাও")).isEqualTo(AppCommand.SearchNotes("ঢাকা"))
    }

    @Test
    fun `stores a memory in three languages`() = runTest {
        assertThat(commandOf("Remember that Ahmed prefers morning calls"))
            .isEqualTo(AppCommand.StoreMemory(subject = "Ahmed", fact = "Ahmed prefers morning call"))
        assertThat(commandOf("Ahmed er jonmodin mone rakho"))
            .isEqualTo(AppCommand.StoreMemory(subject = "Ahmed", fact = "Ahmed jonmodin"))
        assertThat(commandOf("Ahmed এর জন্মদিন মনে রেখো"))
            .isEqualTo(AppCommand.StoreMemory(subject = "Ahmed", fact = "Ahmed জন্মদিন"))
    }

    @Test
    fun `searches memory in three languages`() = runTest {
        val ahmed = AppCommand.SearchMemory("Ahmed")
        assertThat(commandOf("Search my memory for Ahmed")).isEqualTo(ahmed)
        assertThat(commandOf("Ahmed somporke ki mone ache")).isEqualTo(ahmed)
        assertThat(commandOf("Ahmed সম্পর্কে কি মনে আছে")).isEqualTo(ahmed)
    }

    // ------------------------------------------------------------------ business

    @Test
    fun `creates a client in three languages`() = runTest {
        val rahman = AppCommand.CreateClient(name = "Ahmed Rahman")
        assertThat(commandOf("Create a client Ahmed Rahman")).isEqualTo(rahman)
        assertThat(commandOf("notun client Ahmed Rahman")).isEqualTo(rahman)
        assertThat(commandOf("নতুন ক্লায়েন্ট Ahmed Rahman")).isEqualTo(rahman)
    }

    @Test
    fun `searches clients in three languages`() = runTest {
        val ahmed = AppCommand.SearchClients("Ahmed")
        assertThat(commandOf("Find the client Ahmed")).isEqualTo(ahmed)
        assertThat(commandOf("client Ahmed ke khujo")).isEqualTo(ahmed)
        assertThat(commandOf("ক্লায়েন্ট Ahmed কে খুঁজো")).isEqualTo(ahmed)
    }

    @Test
    fun `drafts an invoice in two languages`() = runTest {
        val expected = AppCommand.CreateInvoiceDraft(
            client = ClientTarget.ByName("Ahmed"),
            amount = Money.of("50000"),
            description = null,
        )
        assertThat(commandOf("Create an invoice for Ahmed for 50000 taka")).isEqualTo(expected)
        assertThat(commandOf("Ahmed er jonno 50 hajar takar invoice banao")).isEqualTo(expected)
    }

    /**
     * The defect this guards, end to end: "pay the electricity bill before the 25th" used to draft
     * an invoice — the bare "bill" carried the draft and the ordinal "25th" was read as a "25 taka"
     * total, with the whole rambling sentence as the client. Paying a bill is an expense, never an
     * invoice to issue, so this is anything but a draft now. (It comes back NotUnderstood here,
     * which the router hands to Gemini — a far better outcome than a confident wrong invoice.)
     */
    @Test
    fun `paying a bill before a date is never an invoice draft`() = runTest {
        val result = interpret("ugh I keep forgetting make sure I pay the electricity bill before the 25th")
        val command = (result as? EngineResult.Interpreted)?.command
        assertThat(command).isNotInstanceOf(AppCommand.CreateInvoiceDraft::class.java)
    }

    // ------------------------------------------------------------------ money

    @Test
    fun `records a payment in two languages`() = runTest {
        assertThat(commandOf("Record a payment of 50000 taka from Ahmed")).isEqualTo(
            AppCommand.RecordPayment(amount = Money.of("50000"), client = ClientTarget.ByName("Ahmed"))
        )
        assertThat(commandOf("Ahmed er 50 hajar taka payment peyechi")).isEqualTo(
            AppCommand.RecordPayment(amount = Money.of("50000"), client = null)
        )
    }

    @Test
    fun `records an expense in three languages`() = runTest {
        val lunch = AppCommand.RecordExpense(Money.of("1200"), "lunch", ExpenseCategory.FOOD)
        assertThat(commandOf("Spent 1200 taka on lunch")).isEqualTo(lunch)
        assertThat(commandOf("lunch e 1200 taka khoroch")).isEqualTo(lunch)
        assertThat(commandOf("১২০০ টাকা খাবার খরচ"))
            .isEqualTo(AppCommand.RecordExpense(Money.of("1200"), "খাবার", ExpenseCategory.FOOD))
    }

    @Test
    fun `records income in two languages`() = runTest {
        assertThat(commandOf("Received 50000 taka from Ahmed as income")).isEqualTo(
            AppCommand.RecordIncome(
                amount = Money.of("50000"),
                source = "Ahmed",
                client = ClientTarget.ByName("Ahmed"),
            )
        )
        assertThat(commandOf("Ahmed theke 50 hajar taka income peyechi")).isEqualTo(
            AppCommand.RecordIncome(amount = Money.of("50000"), source = "Ahmed", client = null)
        )
    }

    @Test
    fun `tells spending, earning and being paid apart`() = runTest {
        assertThat(commandOf("Spent 1200 taka on lunch")).isInstanceOf(AppCommand.RecordExpense::class.java)
        assertThat(commandOf("Received 50000 taka from Ahmed as income")).isInstanceOf(AppCommand.RecordIncome::class.java)
        assertThat(commandOf("Record a payment of 50000 taka from Ahmed")).isInstanceOf(AppCommand.RecordPayment::class.java)
    }

    @Test
    fun `passes a calculation through in three languages without evaluating it`() = runTest {
        assertThat(commandOf("what is 12 * 4")).isEqualTo(AppCommand.PerformCalculation("12 * 4"))
        assertThat(commandOf("12 * 4 koto")).isEqualTo(AppCommand.PerformCalculation("12 * 4"))
        assertThat(commandOf("১২ * ৪")).isEqualTo(AppCommand.PerformCalculation("12 * 4"))
    }

    // ------------------------------------------------------------------ the phone itself

    @Test
    fun `looks a contact up in three languages`() = runTest {
        val ahmed = AppCommand.FindContact("Ahmed", com.rayluxeo.aida.domain.model.ContactField.PHONE)
        assertThat(commandOf("What is the number for Ahmed")).isEqualTo(ahmed)
        assertThat(commandOf("Ahmed er number koto")).isEqualTo(ahmed)
        assertThat(commandOf("Ahmed এর নাম্বার কত")).isEqualTo(ahmed)
    }

    @Test
    fun `prepares a call in three languages`() = runTest {
        val ahmed = AppCommand.PrepareCall(ContactTarget.ByName("Ahmed"))
        assertThat(commandOf("Call Ahmed")).isEqualTo(ahmed)
        assertThat(commandOf("Ahmed ke call koro")).isEqualTo(ahmed)
        assertThat(commandOf("Ahmed কে কল করো")).isEqualTo(ahmed)
    }

    @Test
    fun `prepares a message in three languages`() = runTest {
        assertThat(commandOf("Message Ahmed that I am late"))
            .isEqualTo(AppCommand.PrepareMessage(ContactTarget.ByName("Ahmed"), "I am late"))
        assertThat(commandOf("Ahmed ke message koro je ami late"))
            .isEqualTo(AppCommand.PrepareMessage(ContactTarget.ByName("Ahmed"), "ami late"))
        assertThat(commandOf("Ahmed কে মেসেজ করো"))
            .isEqualTo(AppCommand.PrepareMessage(ContactTarget.ByName("Ahmed"), body = null))
    }

    /** "Ahmed ke call koro" is a call; "Ahmed er number koto" is a lookup. */
    @Test
    fun `tells a call apart from a lookup`() = runTest {
        assertThat(commandOf("Ahmed ke call koro")).isInstanceOf(AppCommand.PrepareCall::class.java)
        assertThat(commandOf("Ahmed er number koto")).isInstanceOf(AppCommand.FindContact::class.java)
    }

    // ------------------------------------------------------------------ saying so

    @Test
    fun `says so rather than guessing at gibberish`() = runTest {
        assertThat(interpret("asdkjh qwe zxc")).isEqualTo(EngineResult.NotUnderstood)
        assertThat(interpret("blorf")).isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `says so rather than guessing at empty input`() = runTest {
        assertThat(interpret("   ...   ")).isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `refuses to invent a time for a reminder that names none`() = runTest {
        assertThat(interpret("Remind me to call Ahmed")).isEqualTo(EngineResult.NotUnderstood)
    }

    @Test
    fun `refuses to invent an amount for an expense that names none`() = runTest {
        assertThat(interpret("Spent on lunch")).isEqualTo(EngineResult.NotUnderstood)
    }

    // ------------------------------------------------------------------ small talk

    @Test
    fun `small talk is a greeting the local engine would rather the cloud answered`() = runTest {
        // Offline Mira still greets; online the router hands these to Gemini for a real reply in
        // Abu's own words. Both the English and the Banglish forms must carry that deferral.
        for (hello in listOf("hello", "tumi kemon acho", "how are you", "assalamu alaikum")) {
            val result = interpretedOf(hello)
            assertThat(result.command).isEqualTo(AppCommand.Greet)
            assertThat(result.deferToCloud).isTrue()
        }
    }

    @Test
    fun `a real command is never deferred to the cloud`() = runTest {
        assertThat(interpretedOf(ENGLISH).deferToCloud).isFalse()
        assertThat(interpretedOf(BANGLISH).deferToCloud).isFalse()
    }

    // ------------------------------------------------------------------ confidence

    @Test
    fun `weighs a shaky time into the confidence of a scheduled command`() = runTest {
        val precise = interpretedOf(ENGLISH).confidence
        val vague = interpretedOf("Kal Ahmed ke call korar reminder dao").confidence
        assertThat(precise).isGreaterThan(vague)
    }

    /**
     * The confirmation gate reads this number and nothing else, so a request Mira only half
     * recognised has to land below it while a clear one lands above.
     */
    @Test
    fun `puts a weak mutating match below the confirmation gate and a clear one above`() = runTest {
        assertThat(interpretedOf("Urgent task to call Ahmed").confidence)
            .isLessThan(EngineResult.CONFIRM_THRESHOLD)
        assertThat(interpretedOf(BANGLISH).confidence).isGreaterThan(EngineResult.CONFIRM_THRESHOLD)
    }

    private companion object {
        const val ENGLISH = "Remind me tomorrow at 10 to call Ahmed."
        const val BENGALI_SCRIPT = "আগামীকাল সকাল ১০টায় Ahmed কে call করার reminder দাও."
        const val BANGLISH = "Kal shokal 10 tay Ahmed ke call korar reminder dao."
        const val MIXED = "Tomorrow 10 tay Ahmed ke call korar reminder dio."

        val EXPECTED_REMINDER = AppCommand.CreateReminder(
            title = "call Ahmed",
            triggerAt = FixedMiraClock.dhaka(2026, 3, 11, 10),
        )
    }
}
