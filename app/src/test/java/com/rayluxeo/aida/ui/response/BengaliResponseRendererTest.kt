package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.DayPart
import java.time.Duration
import org.junit.Test

/** Mira's Bengali wording, pinned against the same fixed clock the English tests use. */
class BengaliResponseRendererTest {

    private val renderer = testRenderer()

    private fun speak(response: MiraResponse) = renderer.speak(response, SpokenLanguage.BENGALI)

    private fun display(response: MiraResponse) = renderer.display(response, SpokenLanguage.BENGALI)

    private fun english(response: MiraResponse) = renderer.speak(response, SpokenLanguage.ENGLISH)

    @Test
    fun `the smarter-assistant verbs read back in Bengali, not left in English`() {
        val responses = listOf(
            MiraResponse.TaskDeleted("কাজ"),
            MiraResponse.RemindersFound(0, emptyList()),
            MiraResponse.ReminderSnoozed("দাঁতের ডাক্তার", java.time.Instant.EPOCH),
            MiraResponse.ReminderCancelled("দাঁতের ডাক্তার"),
            MiraResponse.FactForgotten("চা"),
            MiraResponse.AmbiguousReminder("call", listOf("ব্যাঙ্কে ফোন", "আহমেদকে ফোন")),
            MiraResponse.AmbiguousFact("ahmed", listOf("আহমেদ চা পছন্দ করে")),
        )
        responses.forEach { response ->
            assertThat(speak(response)).isNotEmpty()
            assertThat(display(response)).isNotEmpty()
            // The Bengali wording must differ from the English — no response left untranslated.
            assertThat(speak(response)).isNotEqualTo(english(response))
        }
        // The empty-reminders line is Bengali, with no stray Latin letters.
        val none = speak(MiraResponse.RemindersFound(0, emptyList()))
        assertThat(none.none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
    }

    @Test
    fun `a day's prayer count is spoken in Bengali numerals`() {
        val response = MiraResponse.PrayerTrackingReport(
            from = TEST_TODAY,
            to = TEST_TODAY.plusDays(1),
            completed = 3,
            possible = 5,
            daysAllFive = 0,
            tahajjudNights = 0,
            perPrayer = null,
        )

        assertThat(speak(response)).isEqualTo("আজ ৫ ওয়াক্তের মধ্যে ৩ ওয়াক্ত নামাজ পড়েছেন।")
    }

    @Test
    fun `task due tomorrow morning`() {
        val response = MiraResponse.TaskCreated("Ahmed কে call", testInstant(TEST_TOMORROW, 10, 0))

        assertThat(speak(response)).isEqualTo("যোগ করেছি। আগামীকাল সকাল ১০টায় Ahmed কে call।")
        assertThat(display(response))
            .isEqualTo("যোগ করেছি: Ahmed কে call\nসময়: আগামীকাল সকাল ১০টায়")
    }

    @Test
    fun `task due today in the late afternoon`() {
        val response = MiraResponse.TaskCreated("Ahmed কে call", testInstant(TEST_TODAY, 16, 30))

        assertThat(speak(response)).isEqualTo("যোগ করেছি। আজ বিকেল ৪টা ৩০ মিনিটে Ahmed কে call।")
    }

    @Test
    fun `task due yesterday`() {
        val response = MiraResponse.TaskCreated("Ahmed কে call", testInstant(TEST_YESTERDAY, 19, 0))

        assertThat(speak(response)).isEqualTo("যোগ করেছি। গতকাল সন্ধ্যা ৭টায় Ahmed কে call।")
    }

    @Test
    fun `a later date is named in Bengali numerals and months`() {
        val response = MiraResponse.TaskCreated("Ahmed কে call", testInstant(TEST_LATER, 20, 15))

        assertThat(speak(response)).isEqualTo("যোগ করেছি। ৩ অক্টোবর রাত ৮টা ১৫ মিনিটে Ahmed কে call।")
    }

    @Test
    fun `a date in another year carries the year`() {
        val response = MiraResponse.TaskCreated("Ahmed কে call", testInstant(TEST_NEXT_YEAR, 9, 0))

        assertThat(speak(response)).isEqualTo("যোগ করেছি। ৫ জানুয়ারি ২০২৭ সকাল ৯টায় Ahmed কে call।")
    }

    @Test
    fun `each part of the day gets its own word`() {
        fun partOf(hour: Int): String =
            speak(MiraResponse.TaskCreated("কাজ", testInstant(TEST_TODAY, hour, 0)))

        assertThat(partOf(8)).contains("সকাল")
        assertThat(partOf(13)).contains("দুপুর")
        assertThat(partOf(16)).contains("বিকেল")
        assertThat(partOf(19)).contains("সন্ধ্যা")
        assertThat(partOf(22)).contains("রাত")
        assertThat(partOf(2)).contains("রাত")
    }

    @Test
    fun `task without a due time says nothing about time`() {
        val response = MiraResponse.TaskCreated("Ahmed কে call", null)

        assertThat(speak(response)).isEqualTo("যোগ করেছি — Ahmed কে call।")
        assertThat(display(response)).isEqualTo("যোগ করেছি: Ahmed কে call")
    }

    @Test
    fun `completing a task`() {
        val response = MiraResponse.TaskCompleted("Ahmed কে call")

        assertThat(speak(response)).isEqualTo("হয়ে গেছে — Ahmed কে call।")
        assertThat(display(response)).isEqualTo("সম্পন্ন: Ahmed কে call")
    }

    @Test
    fun `no tasks found`() {
        val response = MiraResponse.TasksFound(0, emptyList())

        assertThat(speak(response)).isEqualTo("কোনো কাজ পাইনি।")
        assertThat(display(response)).isEqualTo("কোনো কাজ পাইনি।")
    }

    @Test
    fun `one task found`() {
        val response = MiraResponse.TasksFound(1, listOf("বাসা ভাড়া"))

        assertThat(speak(response)).isEqualTo("একটি কাজ — বাসা ভাড়া।")
        assertThat(display(response)).isEqualTo("১টি কাজ\n• বাসা ভাড়া")
    }

    @Test
    fun `three tasks are listed`() {
        val response = MiraResponse.TasksFound(3, listOf("বাসা ভাড়া", "অফিসে call", "টিকিট বুক"))

        assertThat(speak(response)).isEqualTo("৩টি কাজ — বাসা ভাড়া, অফিসে call ও টিকিট বুক।")
        assertThat(display(response)).isEqualTo("৩টি কাজ\n• বাসা ভাড়া\n• অফিসে call\n• টিকিট বুক")
    }

    @Test
    fun `more than three tasks speaks the count and lists three`() {
        val response = MiraResponse.TasksFound(
            count = 5,
            titles = listOf("বাসা ভাড়া", "অফিসে call", "টিকিট বুক", "ওষুধ কেনা", "রিপোর্ট জমা"),
        )

        assertThat(speak(response)).isEqualTo("৫টি কাজ — বাসা ভাড়া, অফিসে call ও টিকিট বুক।")
        assertThat(display(response)).endsWith("\nআরও ২টি")
    }

    @Test
    fun `reminder promises to remind rather than announcing a feature`() {
        val response = MiraResponse.ReminderCreated("Ahmed কে call", testInstant(TEST_TOMORROW, 10, 0))

        assertThat(speak(response)).isEqualTo("আগামীকাল সকাল ১০টায় মনে করিয়ে দেব — Ahmed কে call।")
        assertThat(display(response))
            .isEqualTo("রিমাইন্ডার: Ahmed কে call\nসময়: আগামীকাল সকাল ১০টায়")
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
                nextReminderTitle = "Ahmed কে call",
                nextReminderAt = testInstant(TEST_TODAY, 16, 30),
            )
        )

        assertThat(speak(response)).isEqualTo(
            "শুভ সকাল। ২টির সময় পেরিয়ে গেছে, আজ ৩টি আছে। এরপর আজ বিকেল ৪টা ৩০ মিনিটে — Ahmed কে call।"
        )
        assertThat(display(response)).isEqualTo(
            "শুভ সকাল।\n" +
                "সময় পেরিয়েছে: ২টি · আজকের কাজ: ৩টি · বাকি আছে: ৫টি · আজ শেষ: ১টি\n" +
                "পরের রিমাইন্ডার: Ahmed কে call, আজ বিকেল ৪টা ৩০ মিনিটে"
        )
    }

    @Test
    fun `brief with only work due today`() {
        val response = MiraResponse.BriefReady(
            testBrief(dayPart = DayPart.AFTERNOON, openTaskCount = 4, dueTodayCount = 2)
        )

        assertThat(speak(response)).isEqualTo("শুভ দুপুর। আজ ২টি কাজ আছে।")
    }

    @Test
    fun `brief with open work but nothing due says so`() {
        val response = MiraResponse.BriefReady(testBrief(dayPart = DayPart.EVENING, openTaskCount = 4))

        assertThat(speak(response)).isEqualTo("শুভ সন্ধ্যা। আজ কিছু নেই, ৪টি কাজ বাকি।")
    }

    @Test
    fun `an empty brief is short and plain`() {
        val response = MiraResponse.BriefReady(testBrief(dayPart = DayPart.EVENING))

        assertThat(speak(response)).isEqualTo("শুভ সন্ধ্যা। আজ নির্দিষ্ট কোনো কাজ নেই।")
        assertThat(display(response)).isEqualTo("শুভ সন্ধ্যা।\nআজ নির্দিষ্ট কোনো কাজ নেই।")
    }

    @Test
    fun `night does not greet with a farewell`() {
        val response = MiraResponse.BriefReady(testBrief(dayPart = DayPart.NIGHT))

        assertThat(speak(response)).startsWith("শুভ সন্ধ্যা।")
        assertThat(speak(response)).doesNotContain("শুভ রাত্রি")
    }

    @Test
    fun `calculation is spoken in Bengali numerals but shown as arithmetic`() {
        val response = MiraResponse.CalculationResult("1200 + 50", "1,250")

        assertThat(speak(response)).isEqualTo("১,২৫০ হয়।")
        assertThat(display(response)).isEqualTo("1200 + 50 = 1,250")
    }

    @Test
    fun `ambiguity names the candidates and asks`() {
        val response = MiraResponse.AmbiguousTask(
            title = "ahmed",
            candidates = listOf("Ahmed কে call", "অফিসে call"),
        )

        assertThat(speak(response)).isEqualTo("কোনটি — Ahmed কে call নাকি অফিসে call?")
        assertThat(display(response)).isEqualTo(
            "\"ahmed\" নামে একাধিক কাজ আছে:\n" +
                "• Ahmed কে call\n" +
                "• অফিসে call\n" +
                "কোনটি?"
        )
    }

    @Test
    fun `ambiguity without candidates still asks rather than guessing`() {
        val response = MiraResponse.AmbiguousTask(title = "call", candidates = emptyList())

        assertThat(speak(response)).isEqualTo("কোন কাজের কথা বলছেন?")
    }

    @Test
    fun `not understood answers in Bengali when he spoke Bengali`() {
        val response = MiraResponse.NotUnderstood("হুম কিছু একটা")

        assertThat(speak(response)).isEqualTo("ঠিক বুঝতে পারিনি — আবার বলবেন?")
        assertThat(display(response)).isEqualTo("ঠিক বুঝতে পারিনি।\nযা শুনেছি: \"হুম কিছু একটা\"")
    }

    @Test
    fun `not understood with no heard text omits the echo`() {
        assertThat(display(MiraResponse.NotUnderstood(""))).isEqualTo("ঠিক বুঝতে পারিনি।")
    }

    @Test
    fun `every error code has Bengali wording`() {
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

        assertThat(speak(permission)).contains("সেটিংস")
        assertThat(display(permission)).contains("সেটিংস")
        assertThat(speak(engine)).contains("অফলাইন")
        assertThat(display(engine)).contains("অফলাইন")
    }

    @Test
    fun `spoken Bengali never falls back to ASCII digits`() {
        val digitFree = listOf(
            MiraResponse.TaskCreated("কাজ", testInstant(TEST_LATER, 20, 15)),
            MiraResponse.TaskCompleted("কাজ"),
            MiraResponse.TasksFound(12, listOf("কাজ", "আরেক কাজ")),
            MiraResponse.ReminderCreated("কাজ", testInstant(TEST_NEXT_YEAR, 7, 5)),
            MiraResponse.BriefReady(
                testBrief(
                    openTaskCount = 10,
                    dueTodayCount = 4,
                    overdueCount = 2,
                    completedTodayCount = 3,
                    nextReminderTitle = "কাজ",
                    nextReminderAt = testInstant(TEST_TOMORROW, 11, 45),
                )
            ),
            MiraResponse.CalculationResult("১২ + ৮", "20"),
            MiraResponse.TaskUpdated("কাজ", testInstant(TEST_TOMORROW, 10, 0)),
            MiraResponse.AlarmSet("ওষুধ", testInstant(TEST_TOMORROW, 6, 30)),
            MiraResponse.TimerStarted("চা", Duration.ofMinutes(90), testInstant(TEST_TODAY, 10, 30)),
            MiraResponse.NotesFound(12, listOf("নোট", "আরেক নোট")),
            MiraResponse.MemoriesFound(12, listOf("একটা কথা", "আরেকটা কথা")),
            MiraResponse.ClientsFound(12, listOf("রহিম ট্রেডার্স", "করিম লিমিটেড")),
            MiraResponse.ExpenseRecorded(taka("150000"), "অফিস ভাড়া"),
            MiraResponse.IncomeRecorded(taka("1234.56"), "কনসাল্টিং"),
            MiraResponse.CalendarEventCreated("সাইট ভিজিট", testInstant(TEST_TOMORROW, 15, 0)),
        )

        digitFree.forEach { response ->
            assertThat(speak(response).none { it in '0'..'9' }).isTrue()
        }
    }

    @Test
    fun `updating a task says so rather than adding it again`() {
        val response = MiraResponse.TaskUpdated("Ahmed কে call", testInstant(TEST_TOMORROW, 10, 0))

        assertThat(speak(response)).isEqualTo("বদলে দিয়েছি। আগামীকাল সকাল ১০টায় Ahmed কে call।")
        assertThat(display(response))
            .isEqualTo("বদলে দিয়েছি: Ahmed কে call\nসময়: আগামীকাল সকাল ১০টায়")
    }

    @Test
    fun `updating a task without a time says nothing about time`() {
        val response = MiraResponse.TaskUpdated("Ahmed কে call", null)

        assertThat(speak(response)).isEqualTo("বদলে দিয়েছি — Ahmed কে call।")
        assertThat(display(response)).isEqualTo("বদলে দিয়েছি: Ahmed কে call")
    }

    @Test
    fun `an alarm names its label and when it rings`() {
        val response = MiraResponse.AlarmSet("ওষুধ", testInstant(TEST_TOMORROW, 6, 30))

        assertThat(speak(response))
            .isEqualTo("আগামীকাল সকাল ৬টা ৩০ মিনিটে অ্যালার্ম দিয়েছি — ওষুধ।")
        assertThat(display(response))
            .isEqualTo("অ্যালার্ম: ওষুধ\nসময়: আগামীকাল সকাল ৬টা ৩০ মিনিটে")
    }

    @Test
    fun `an alarm with no label still says when it rings`() {
        val response = MiraResponse.AlarmSet("  ", testInstant(TEST_TOMORROW, 6, 30))

        assertThat(speak(response)).isEqualTo("আগামীকাল সকাল ৬টা ৩০ মিনিটে অ্যালার্ম দিয়েছি।")
        assertThat(display(response))
            .isEqualTo("অ্যালার্ম দিয়েছি\nসময়: আগামীকাল সকাল ৬টা ৩০ মিনিটে")
    }

    @Test
    fun `a timer carries its length and its end`() {
        val response = MiraResponse.TimerStarted(
            label = "চা",
            duration = Duration.ofMinutes(25),
            endsAt = testInstant(TEST_TODAY, 9, 25),
        )

        assertThat(speak(response))
            .isEqualTo("চা — ২৫ মিনিট টাইমার চালু করেছি, শেষ হবে আজ সকাল ৯টা ২৫ মিনিটে।")
        assertThat(display(response))
            .isEqualTo("টাইমার: চা\nসময়কাল: ২৫ মিনিট\nশেষ: আজ সকাল ৯টা ২৫ মিনিটে")
    }

    @Test
    fun `an hour and a half is said in hours and minutes`() {
        val response = MiraResponse.TimerStarted(
            label = "",
            duration = Duration.ofMinutes(90),
            endsAt = testInstant(TEST_TODAY, 10, 30),
        )

        assertThat(speak(response))
            .isEqualTo("১ ঘণ্টা ৩০ মিনিট টাইমার চালু করেছি, শেষ হবে আজ সকাল ১০টা ৩০ মিনিটে।")
        assertThat(display(response))
            .isEqualTo("টাইমার: ১ ঘণ্টা ৩০ মিনিট\nশেষ: আজ সকাল ১০টা ৩০ মিনিটে")
    }

    @Test
    fun `a timer under a minute is counted in seconds`() {
        val response = MiraResponse.TimerStarted(
            label = "",
            duration = Duration.ofSeconds(45),
            endsAt = testInstant(TEST_TODAY, 9, 1),
        )

        assertThat(speak(response))
            .isEqualTo("৪৫ সেকেন্ড টাইমার চালু করেছি, শেষ হবে আজ সকাল ৯টা ১ মিনিটে।")
    }

    @Test
    fun `a note with a title and a note without one`() {
        assertThat(speak(MiraResponse.NoteCreated("বাসা ভাড়ার কাগজ")))
            .isEqualTo("নোট রেখেছি — বাসা ভাড়ার কাগজ।")
        assertThat(display(MiraResponse.NoteCreated("বাসা ভাড়ার কাগজ")))
            .isEqualTo("নোট: বাসা ভাড়ার কাগজ")
        assertThat(speak(MiraResponse.NoteCreated(null))).isEqualTo("নোট রেখেছি।")
        assertThat(display(MiraResponse.NoteCreated(null))).isEqualTo("নোট রেখেছি।")
    }

    @Test
    fun `notes found are counted and listed`() {
        assertThat(speak(MiraResponse.NotesFound(0, emptyList()))).isEqualTo("কোনো নোট পাইনি।")
        assertThat(speak(MiraResponse.NotesFound(1, listOf("বাসা ভাড়ার কাগজ"))))
            .isEqualTo("একটি নোট — বাসা ভাড়ার কাগজ।")

        val many = MiraResponse.NotesFound(
            count = 5,
            titles = listOf("বাসা ভাড়া", "গুদামের কাগজ", "ব্যাংক", "বীমা", "কর"),
        )

        assertThat(speak(many)).isEqualTo("৫টি নোট — বাসা ভাড়া, গুদামের কাগজ ও ব্যাংক।")
        assertThat(display(many))
            .isEqualTo("৫টি নোট\n• বাসা ভাড়া\n• গুদামের কাগজ\n• ব্যাংক\nআরও ২টি")
    }

    @Test
    fun `a stored memory repeats the fact back`() {
        val response = MiraResponse.MemoryStored("Ahmed", "সকালে call পছন্দ করে")

        assertThat(speak(response)).isEqualTo("মনে রাখলাম — Ahmed: সকালে call পছন্দ করে।")
        assertThat(display(response)).isEqualTo("মনে রাখলাম: Ahmed\nসকালে call পছন্দ করে")
    }

    @Test
    fun `memories found are listed, and none is said plainly`() {
        assertThat(speak(MiraResponse.MemoriesFound(0, emptyList())))
            .isEqualTo("এ বিষয়ে কিছু মনে নেই।")
        assertThat(display(MiraResponse.MemoriesFound(0, emptyList())))
            .isEqualTo("এ বিষয়ে কিছু মনে নেই।")

        val one = MiraResponse.MemoriesFound(1, listOf("সকালে call পছন্দ করে"))
        assertThat(speak(one)).isEqualTo("মনে আছে — সকালে call পছন্দ করে।")
        assertThat(display(one)).isEqualTo("মনে আছে:\n• সকালে call পছন্দ করে")

        val many = MiraResponse.MemoriesFound(
            count = 4,
            facts = listOf("ব্যাংক সিটি", "অফিস বনানীতে", "ছেলে বুয়েটে", "গাড়ি ধূসর"),
        )
        assertThat(speak(many)).isEqualTo("৪টি মনে আছে — ব্যাংক সিটি, অফিস বনানীতে ও ছেলে বুয়েটে।")
        assertThat(display(many)).endsWith("\nআরও ১টি")
    }

    @Test
    fun `a new client is one line`() {
        val response = MiraResponse.ClientCreated("রহিম ট্রেডার্স")

        assertThat(speak(response)).isEqualTo("ক্লায়েন্ট যোগ করেছি — রহিম ট্রেডার্স।")
        assertThat(display(response)).isEqualTo("ক্লায়েন্ট: রহিম ট্রেডার্স")
    }

    @Test
    fun `clients are counted with the classifier people take`() {
        assertThat(speak(MiraResponse.ClientsFound(0, emptyList())))
            .isEqualTo("কোনো ক্লায়েন্ট পাইনি।")
        assertThat(speak(MiraResponse.ClientsFound(1, listOf("রহিম ট্রেডার্স"))))
            .isEqualTo("একজন ক্লায়েন্ট — রহিম ট্রেডার্স।")

        val many = MiraResponse.ClientsFound(
            count = 5,
            names = listOf("রহিম ট্রেডার্স", "করিম লিমিটেড", "সুলতান কোং", "ডেল্টা", "এভারেস্ট"),
        )

        assertThat(speak(many))
            .isEqualTo("৫জন ক্লায়েন্ট — রহিম ট্রেডার্স, করিম লিমিটেড ও সুলতান কোং।")
        assertThat(display(many)).endsWith("\nআরও ২জন")
    }

    @Test
    fun `an invoice draft names the client and the total`() {
        val response = MiraResponse.InvoiceDraftCreated(
            number = "INV-2026-0042",
            clientName = "রহিম ট্রেডার্স",
            total = taka("150000"),
        )

        assertThat(speak(response))
            .isEqualTo("ইনভয়েস ড্রাফট INV-2026-0042 — রহিম ট্রেডার্স, ১,৫০,০০০ টাকা।")
        assertThat(display(response)).isEqualTo(
            "ইনভয়েস ড্রাফট INV-2026-0042\nক্লায়েন্ট: রহিম ট্রেডার্স\nমোট: ১,৫০,০০০ টাকা"
        )
    }

    @Test
    fun `a payment names whoever it can be attributed to`() {
        val fromClient =
            MiraResponse.PaymentRecorded(taka("50000"), "রহিম ট্রেডার্স", "INV-2026-0042")
        assertThat(speak(fromClient))
            .isEqualTo("পেমেন্ট লিখে রেখেছি — রহিম ট্রেডার্স থেকে ৫০,০০০ টাকা।")
        assertThat(display(fromClient)).isEqualTo(
            "পেমেন্ট: ৫০,০০০ টাকা\nক্লায়েন্ট: রহিম ট্রেডার্স\nইনভয়েস: INV-2026-0042"
        )

        val againstInvoice = MiraResponse.PaymentRecorded(taka("1234.56"), null, "INV-2026-0042")
        assertThat(speak(againstInvoice))
            .isEqualTo("পেমেন্ট লিখে রেখেছি — INV-2026-0042-এর ১,২৩৪.৫৬ টাকা।")

        val bare = MiraResponse.PaymentRecorded(taka("500"), null, null)
        assertThat(speak(bare)).isEqualTo("পেমেন্ট লিখে রেখেছি — ৫০০ টাকা।")
        assertThat(display(bare)).isEqualTo("পেমেন্ট: ৫০০ টাকা")
    }

    @Test
    fun `an invoice-draft confirmation reads back the client and the amount in Bengali`() {
        val withAmount =
            MiraResponse.InvoiceDraftRequested("রহিম ট্রেডার্স", taka("150000"), "লোগো ডিজাইন")
        assertThat(speak(withAmount)).isEqualTo(
            "রহিম ট্রেডার্স-এর জন্য একটি ইনভয়েস ড্রাফট করব — ১,৫০,০০০ টাকা, লোগো ডিজাইন-এর জন্য।"
        )
        assertThat(display(withAmount)).isEqualTo(
            "ইনভয়েস ড্রাফট\nক্লায়েন্ট: রহিম ট্রেডার্স\nমোট: ১,৫০,০০০ টাকা\nবিবরণ: লোগো ডিজাইন"
        )
    }

    @Test
    fun `a percentage payment confirmation reads back the share, never a zero amount`() {
        val resp = MiraResponse.PaymentByPercentageRequested(
            java.math.BigDecimal("50"), "রহিম ট্রেডার্স", "INV-2026-0042",
        )
        assertThat(speak(resp)).isEqualTo("INV-2026-0042-এর ৫০% রহিম ট্রেডার্স থেকে লিখব।")
        assertThat(display(resp)).isEqualTo(
            "পেমেন্ট: ৫০%\nইনভয়েস: INV-2026-0042\nক্লায়েন্ট: রহিম ট্রেডার্স"
        )
    }

    @Test
    fun `an expense and an income name the amount and the other side`() {
        val expense = MiraResponse.ExpenseRecorded(taka("2500.50"), "অফিস ভাড়া")
        assertThat(speak(expense)).isEqualTo("খরচ লিখে রেখেছি — ২,৫০০.৫০ টাকা, অফিস ভাড়া।")
        assertThat(display(expense)).isEqualTo("খরচ: ২,৫০০.৫০ টাকা\nবিবরণ: অফিস ভাড়া")

        val income = MiraResponse.IncomeRecorded(taka("225000"), "কনসাল্টিং")
        assertThat(speak(income)).isEqualTo("আয় লিখে রেখেছি — কনসাল্টিং থেকে ২,২৫,০০০ টাকা।")
        assertThat(display(income)).isEqualTo("আয়: ২,২৫,০০০ টাকা\nউৎস: কনসাল্টিং")
    }

    @Test
    fun `a calendar event says where it went and when`() {
        val response =
            MiraResponse.CalendarEventCreated("সাইট ভিজিট", testInstant(TEST_TOMORROW, 15, 0))

        assertThat(speak(response))
            .isEqualTo("ক্যালেন্ডারে রেখেছি। আগামীকাল বিকেল ৩টায় সাইট ভিজিট।")
        assertThat(display(response))
            .isEqualTo("ক্যালেন্ডার: সাইট ভিজিট\nসময়: আগামীকাল বিকেল ৩টায়")
    }

    @Test
    fun `contacts speak names and show the number as it can be dialled`() {
        val response = MiraResponse.ContactsFound(
            count = 2,
            contacts = listOf(
                testContact("Ahmed Khan"),
                testContact("Karim Uddin", phoneNumber = null),
            ),
        )

        assertThat(speak(response)).isEqualTo("২জন কন্টাক্ট — Ahmed Khan ও Karim Uddin।")
        assertThat(display(response))
            .isEqualTo("২জন কন্টাক্ট\n• Ahmed Khan — 01711-223344\n• Karim Uddin")
        assertThat(speak(MiraResponse.ContactsFound(0, emptyList())))
            .isEqualTo("কোনো কন্টাক্ট পাইনি।")
    }

    @Test
    fun `a prepared call is offered, not placed`() {
        val response = MiraResponse.CallPrepared(testContact("Ahmed Khan"))

        assertThat(speak(response)).isEqualTo("Ahmed Khan-কে কল করতে প্রস্তুত।")
        assertThat(display(response)).isEqualTo("কল: Ahmed Khan\n01711-223344")
    }

    @Test
    fun `a contact with no number is admitted rather than dialled`() {
        val response = MiraResponse.CallPrepared(testContact("Ahmed Khan", phoneNumber = null))

        assertThat(speak(response)).isEqualTo("Ahmed Khan আছে, কিন্তু নম্বর নেই।")
        assertThat(display(response)).isEqualTo("কল: Ahmed Khan\nনম্বর নেই।")
    }

    @Test
    fun `a prepared message is ready, not sent`() {
        val written = MiraResponse.MessagePrepared(testContact("Ahmed Khan"), "আসছি")
        assertThat(speak(written)).isEqualTo("Ahmed Khan-এর মেসেজ তৈরি।")
        assertThat(display(written)).isEqualTo("মেসেজ: Ahmed Khan\n\"আসছি\"")

        val empty = MiraResponse.MessagePrepared(testContact("Ahmed Khan"), null)
        assertThat(speak(empty)).isEqualTo("Ahmed Khan-কে মেসেজ পাঠাতে প্রস্তুত।")
        assertThat(display(empty)).isEqualTo("মেসেজ: Ahmed Khan")
    }

    @Test
    fun `two clients of the same name are put back to him`() {
        val response = MiraResponse.AmbiguousClient(
            query = "রহিম",
            candidates = listOf("রহিম ট্রেডার্স", "রহিম এন্টারপ্রাইজ"),
        )

        assertThat(speak(response)).isEqualTo("কোনজন — রহিম ট্রেডার্স নাকি রহিম এন্টারপ্রাইজ?")
        assertThat(display(response)).isEqualTo(
            "\"রহিম\" নামে একাধিক ক্লায়েন্ট আছে:\n" +
                "• রহিম ট্রেডার্স\n" +
                "• রহিম এন্টারপ্রাইজ\n" +
                "কোনজন?"
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

        assertThat(speak(response)).isEqualTo("কোনজন — Ahmed Khan নাকি Ahmed Rahman?")
        assertThat(display(response)).isEqualTo(
            "\"ahmed\" নামে একাধিক কন্টাক্ট আছে:\n" +
                "• Ahmed Khan — 01711-223344\n" +
                "• Ahmed Rahman — 01911-556677\n" +
                "কোনজন?"
        )
    }

    @Test
    fun `ambiguity with no candidates still asks rather than guessing`() {
        assertThat(speak(MiraResponse.AmbiguousClient("রহিম", emptyList())))
            .isEqualTo("কোন ক্লায়েন্টের কথা বলছেন?")
        assertThat(speak(MiraResponse.AmbiguousContact("ahmed", emptyList())))
            .isEqualTo("কোন কন্টাক্টের কথা বলছেন?")
    }

    @Test
    fun `a withheld permission says what granting it would let her do`() {
        val response = MiraResponse.PermissionNeeded(MiraCapability.CONTACTS)

        assertThat(speak(response)).isEqualTo("কন্টাক্টসের অনুমতি পেলে নাম আর নম্বর খুঁজে দিতে পারব।")
        assertThat(display(response)).isEqualTo(
            "কন্টাক্টসের অনুমতি পেলে নাম আর নম্বর খুঁজে দিতে পারব।\n" +
                "সেটিংসে গিয়ে অনুমতি দিন, তারপর আবার বলুন।"
        )
    }

    @Test
    fun `every capability has Bengali wording and none of it nags`() {
        MiraCapability.entries.forEach { capability ->
            val response = MiraResponse.PermissionNeeded(capability)

            assertThat(speak(response)).isNotEmpty()
            assertThat(speak(response)).doesNotContain("\n")
            assertThat(display(response)).contains("সেটিংস")
        }
    }

    /**
     * The same rule as the English phrasebook: an alarm is kept and arms itself, so "আবার বলুন"
     * would give Abu a second alarm rather than the one he asked for.
     */
    @Test
    fun `a refused alarm is not answered by telling Abu to ask again`() {
        val alarm = MiraResponse.PermissionNeeded(MiraCapability.EXACT_ALARMS)

        assertThat(display(alarm)).doesNotContain("আবার বলুন")
        assertThat(display(MiraResponse.PermissionNeeded(MiraCapability.CONTACTS)))
            .contains("আবার বলুন")
    }
}
