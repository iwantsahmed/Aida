package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.AttentionItem
import com.rayluxeo.aida.domain.brief.AttentionKind
import com.rayluxeo.aida.domain.brief.AttentionSeverity
import com.rayluxeo.aida.domain.brief.BriefDeadline
import com.rayluxeo.aida.domain.brief.BriefEvent
import com.rayluxeo.aida.domain.brief.BriefFollowUp
import com.rayluxeo.aida.domain.brief.BriefInvoice
import com.rayluxeo.aida.domain.brief.BriefMoney
import com.rayluxeo.aida.domain.brief.BriefPayment
import com.rayluxeo.aida.domain.brief.BriefReminder
import com.rayluxeo.aida.domain.brief.BriefTask
import com.rayluxeo.aida.domain.brief.DailyBriefReport
import com.rayluxeo.aida.domain.brief.EveningReviewReport
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import org.junit.Test

/**
 * The brief is the one thing Mira says unasked, so what she says has to be right in both
 * languages before it is convenient in either.
 *
 * The clock is fixed to Tuesday 10 March 2026, 10:00 in Dhaka, which is what makes "tomorrow"
 * and "in 5 days" assertable rather than a guess about when the build ran.
 */
class BriefNarratorTest {

    private val clock = FixedMiraClock()
    private val narrator = BriefNarrator(clock, TaskTimeFormatter(clock))

    private fun spoken(report: DailyBriefReport, language: SpokenLanguage) =
        narrator.speakDailyBrief(report, language)

    private fun spoken(report: EveningReviewReport, language: SpokenLanguage) =
        narrator.speakEveningReview(report, language)

    // ---- The spoken brief ----

    @Test
    fun `a full day is one paragraph that names the work, the money and the worries`() {
        assertThat(spoken(busyDay(), SpokenLanguage.ENGLISH)).isEqualTo(
            "Good morning, Abu. " +
                "2 overdue and 3 due today. " +
                "Next, Rayluxeo call — 3 pm. " +
                "2 reminders, the next — Electricity bill, 5 pm. " +
                "BDT 1,20,000 outstanding, BDT 45,000 of it overdue. " +
                "Ahmed's invoice is overdue. " +
                "Rayluxeo website is due tomorrow. " +
                "1 more needs attention.",
        )
    }

    @Test
    fun `the same day in Bengali, with every figure in Bengali numerals`() {
        val bengali = spoken(busyDay(), SpokenLanguage.BENGALI)

        assertThat(bengali).isEqualTo(
            "শুভ সকাল, Abu। " +
                "২টির সময় পেরিয়ে গেছে, আজ ৩টি আছে। " +
                "এরপর Rayluxeo call — বিকেল ৩টা। " +
                "২টি রিমাইন্ডার, পরেরটি — Electricity bill, বিকেল ৫টা। " +
                "মোট বকেয়া ১,২০,০০০ টাকা, তার মধ্যে ৪৫,০০০ টাকা ওভারডিউ। " +
                "Ahmed — ইনভয়েস ওভারডিউ। " +
                "Rayluxeo website — আগামীকাল শেষ করতে হবে। " +
                "আরও ১টি বিষয় দেখা দরকার।",
        )
        assertThat(bengali).doesNotContainMatch("[0-9]")
    }

    @Test
    fun `a quiet day is two sentences and no invented business`() {
        assertThat(spoken(quietDay(), SpokenLanguage.ENGLISH))
            .isEqualTo("Good morning, Abu. Nothing on today.")
        assertThat(spoken(quietDay(), SpokenLanguage.BENGALI))
            .isEqualTo("শুভ সকাল, Abu। আজ বিশেষ কিছু নেই।")
    }

    @Test
    fun `an outstanding balance keeps a day from being called quiet`() {
        val report = quietDay().copy(money = money(outstanding = "80000"))

        assertThat(spoken(report, SpokenLanguage.ENGLISH))
            .isEqualTo("Good morning, Abu. Nothing due today. BDT 80,000 outstanding.")
    }

    @Test
    fun `a zero balance is not announced`() {
        val report = quietDay().copy(money = money(outstanding = "0"))

        assertThat(spoken(report, SpokenLanguage.ENGLISH)).isEqualTo("Good morning, Abu. Nothing on today.")
    }

    @Test
    fun `a calendar Mira cannot read is said out loud, not passed off as an empty day`() {
        val report = busyDay().copy(nextEvent = null, calendarReadable = false)

        val english = spoken(report, SpokenLanguage.ENGLISH)
        assertThat(english).contains("I can't see your calendar.")
        assertThat(english).doesNotContain("Nothing in the calendar")
        assertThat(spoken(report, SpokenLanguage.BENGALI)).contains("ক্যালেন্ডার দেখতে পাচ্ছি না।")
    }

    /** The trap: with no events and no tasks the day looks quiet, and a withheld calendar is not. */
    @Test
    fun `an empty day with an unreadable calendar still admits the calendar was not read`() {
        val report = quietDay().copy(calendarReadable = false)

        val english = spoken(report, SpokenLanguage.ENGLISH)
        assertThat(english).isEqualTo("Good morning, Abu. Nothing due today. I can't see your calendar.")
        assertThat(english).doesNotContain("Nothing on today.")
    }

    @Test
    fun `one reminder and several read differently`() {
        val one = busyDay().copy(upcomingReminders = listOf(reminder("Electricity bill", hour = 17)))

        assertThat(spoken(one, SpokenLanguage.ENGLISH)).contains("One reminder — Electricity bill, 5 pm.")
        assertThat(spoken(one, SpokenLanguage.BENGALI)).contains("একটি রিমাইন্ডার — Electricity bill, বিকেল ৫টা।")
        assertThat(spoken(busyDay(), SpokenLanguage.ENGLISH))
            .contains("2 reminders, the next — Electricity bill, 5 pm.")
    }

    @Test
    fun `an all-day event is spoken as all day rather than as a time`() {
        val report = busyDay().copy(nextEvent = event("Eid holiday", hour = 0, allDay = true))

        assertThat(spoken(report, SpokenLanguage.ENGLISH)).contains("Next, Eid holiday — All day.")
        assertThat(spoken(report, SpokenLanguage.BENGALI)).contains("এরপর Eid holiday — সারাদিন।")
    }

    @Test
    fun `an event tomorrow keeps its day, one today does not repeat it`() {
        val tomorrow = busyDay().copy(nextEvent = event("Kickoff", hour = 9, day = 11))

        assertThat(spoken(tomorrow, SpokenLanguage.ENGLISH)).contains("Next, Kickoff — Tomorrow, 9 am.")
        assertThat(spoken(busyDay(), SpokenLanguage.ENGLISH)).contains("Next, Rayluxeo call — 3 pm.")
    }

    @Test
    fun `only the first two worries are spoken and the rest are counted`() {
        val english = spoken(busyDay(), SpokenLanguage.ENGLISH)

        assertThat(english).contains("1 more needs attention.")
        assertThat(english).doesNotContain("Adobe")
    }

    // ---- Needs Attention ----

    @Test
    fun `the three cards Abu asked for read as sentences in English`() {
        assertThat(line(overdueInvoice()).title).isEqualTo("Ahmed's invoice is overdue.")
        assertThat(line(websiteDeadline()).title).isEqualTo("Rayluxeo website is due tomorrow.")
        assertThat(line(adobeRenewal()).title).isEqualTo("Adobe subscription renews soon.")
    }

    @Test
    fun `and as Bengali sentences that never inflect the name`() {
        val language = SpokenLanguage.BENGALI

        assertThat(line(overdueInvoice(), language).title).isEqualTo("Ahmed — ইনভয়েস ওভারডিউ।")
        assertThat(line(websiteDeadline(), language).title)
            .isEqualTo("Rayluxeo website — আগামীকাল শেষ করতে হবে।")
        assertThat(line(adobeRenewal(), language).title)
            .isEqualTo("Adobe — সাবস্ক্রিপশন শিগগিরই রিনিউ হবে।")
    }

    @Test
    fun `a subject with no date falls back on how urgent it is`() {
        val now = attention(AttentionKind.PROJECT_DEADLINE, "Rayluxeo website", AttentionSeverity.NOW)
        val watch = attention(AttentionKind.INVOICE_DUE_SOON, "Ahmed", AttentionSeverity.WATCH)

        assertThat(line(now).title).isEqualTo("Rayluxeo website is due now.")
        assertThat(line(watch).title).isEqualTo("Ahmed's invoice is due soon.")
        assertThat(line(now, SpokenLanguage.BENGALI).title)
            .isEqualTo("Rayluxeo website — এখনই শেষ করতে হবে।")
    }

    @Test
    fun `a subject with no amount and no date has no second line at all`() {
        assertThat(line(adobeRenewal()).detail).isNull()
        assertThat(line(adobeRenewal(), SpokenLanguage.BENGALI).detail).isNull()
    }

    @Test
    fun `an amount and a date are both carried into the second line`() {
        val item = overdueInvoice().copy(
            amount = taka("45000"),
            at = FixedMiraClock.dhaka(2026, 3, 6, 12),
        )

        assertThat(line(item).detail).isEqualTo("BDT 45,000 · 6 March")
        assertThat(line(item, SpokenLanguage.BENGALI).detail).isEqualTo("৪৫,০০০ টাকা · ৬ মার্চ")
    }

    @Test
    fun `a name that already ends in s takes a bare possessive`() {
        assertThat(line(overdueInvoice().copy(subject = "Jonas")).title)
            .isEqualTo("Jonas' invoice is overdue.")
    }

    @Test
    fun `a day count reads as one day rather than 1 days`() {
        val followUp = attention(
            kind = AttentionKind.CLIENT_FOLLOW_UP,
            subject = "Ahmed",
            severity = AttentionSeverity.WATCH,
            daysAway = -1,
        )
        val inDays = adobeRenewal().copy(daysAway = 5)

        assertThat(line(followUp).title).isEqualTo("No word from Ahmed in 1 day.")
        assertThat(line(inDays).title).isEqualTo("Adobe subscription renews in 5 days.")
        assertThat(line(inDays, SpokenLanguage.BENGALI).title)
            .isEqualTo("Adobe — সাবস্ক্রিপশন ৫ দিন পর রিনিউ হবে।")
    }

    // ---- The evening review ----

    @Test
    fun `the evening review reports the day without grading it`() {
        assertThat(spoken(fullEvening(), SpokenLanguage.ENGLISH)).isEqualTo(
            "4 done, 3 still open. " +
                "One payment in, BDT 30,000. " +
                "3 expenses recorded, BDT 4,500. " +
                "One invoice raised, BDT 12,000. " +
                "One event tomorrow — Kickoff, 9 am. " +
                "First tomorrow — Finish the homepage.",
        )
    }

    @Test
    fun `the evening review in Bengali`() {
        val bengali = spoken(fullEvening(), SpokenLanguage.BENGALI)

        assertThat(bengali).isEqualTo(
            "৪টি কাজ শেষ হয়েছে, ৩টি বাকি। " +
                "একটি পেমেন্ট এসেছে, ৩০,০০০ টাকা। " +
                "৩টি খরচ লেখা হয়েছে, ৪,৫০০ টাকা। " +
                "একটি ইনভয়েস হয়েছে, ১২,০০০ টাকা। " +
                "আগামীকাল একটি ইভেন্ট — Kickoff, সকাল ৯টা। " +
                "আগামীকাল প্রথমে — Finish the homepage।",
        )
        assertThat(bengali).doesNotContainMatch("[0-9]")
    }

    @Test
    fun `a day with nothing recorded says so, and still looks ahead`() {
        assertThat(spoken(emptyEvening(), SpokenLanguage.ENGLISH))
            .isEqualTo("Nothing recorded today. Nothing in the calendar tomorrow.")
        assertThat(spoken(emptyEvening(), SpokenLanguage.BENGALI))
            .isEqualTo("আজ নতুন কিছু লেখা হয়নি। আগামীকাল ক্যালেন্ডারে কিছু নেই।")
    }

    @Test
    fun `unfinished work is reported even when nothing was recorded`() {
        val report = emptyEvening().copy(stillOpen = listOf(task("Invoice Rayluxeo")))

        assertThat(spoken(report, SpokenLanguage.ENGLISH))
            .isEqualTo("Nothing marked done, 1 still open. Nothing in the calendar tomorrow.")
    }

    @Test
    fun `an unreadable calendar is not reported as an empty tomorrow`() {
        val report = fullEvening().copy(tomorrowsEvents = emptyList(), calendarReadable = false)

        val english = spoken(report, SpokenLanguage.ENGLISH)
        assertThat(english).contains("I can't see your calendar.")
        assertThat(english).doesNotContain("Nothing in the calendar tomorrow.")
    }

    @Test
    fun `several payments and several tasks tomorrow take their plurals`() {
        val report = fullEvening().copy(
            paymentsReceived = listOf(payment("Ahmed", "30000"), payment("Rayluxeo", "20000")),
            paymentsTotal = taka("50000"),
            tomorrowsPriorities = listOf(task("Finish the homepage"), task("Send the quote")),
            tomorrowsEvents = listOf(event("Kickoff", hour = 9, day = 11), event("Review", hour = 16, day = 11)),
        )

        val english = spoken(report, SpokenLanguage.ENGLISH)
        assertThat(english).contains("2 payments in, BDT 50,000.")
        assertThat(english).contains("2 events tomorrow, the first — Kickoff, 9 am.")
        assertThat(english).contains("2 tasks waiting tomorrow, first — Finish the homepage.")
    }

    /**
     * Two currencies have no sum Mira is allowed to invent, and the count is still true, so she
     * says the count and drops the figure rather than converting anything.
     */
    @Test
    fun `invoices in two currencies lose the total and keep the count`() {
        val report = fullEvening().copy(
            invoicesCreated = listOf(
                invoice(number = "INV-014", client = "Rayluxeo", amount = taka("12000")),
                invoice(number = "INV-015", client = "Northwind", amount = dollars("100")),
            ),
        )

        assertThat(spoken(report, SpokenLanguage.ENGLISH)).contains("2 invoices raised.")
    }

    // ---- Notifications ----

    @Test
    fun `the morning notification is a title and one line`() {
        val english = narrator.morningNotification(busyDay(), SpokenLanguage.ENGLISH)
        val bengali = narrator.morningNotification(busyDay(), SpokenLanguage.BENGALI)

        assertThat(english.title).isEqualTo("Morning brief")
        assertThat(english.line).isEqualTo("2 overdue and 3 due today. 3 need attention.")
        assertThat(bengali.title).isEqualTo("সকালের ব্রিফ")
        assertThat(bengali.line).isEqualTo("২টির সময় পেরিয়ে গেছে, আজ ৩টি আছে। ৩টি বিষয় দেখা দরকার।")
    }

    @Test
    fun `with nothing needing attention the notification names the next thing instead`() {
        val report = busyDay().copy(attention = emptyList())

        assertThat(narrator.morningNotification(report, SpokenLanguage.ENGLISH).line)
            .isEqualTo("2 overdue and 3 due today. Next, Rayluxeo call — 3 pm.")
    }

    @Test
    fun `the evening notification counts tomorrow rather than naming it`() {
        val english = narrator.eveningNotification(fullEvening(), SpokenLanguage.ENGLISH)
        val bengali = narrator.eveningNotification(fullEvening(), SpokenLanguage.BENGALI)

        assertThat(english.title).isEqualTo("Evening review")
        assertThat(english.line).isEqualTo("4 done, 3 still open. One event tomorrow.")
        assertThat(bengali.title).isEqualTo("সন্ধ্যার হিসাব")
        assertThat(bengali.line).isEqualTo("৪টি কাজ শেষ হয়েছে, ৩টি বাকি। আগামীকাল একটি ইভেন্ট আছে।")
    }

    // ---- The rows Home draws ----

    @Test
    fun `an event row carries its time and its place`() {
        val row = narrator.eventLine(event("Rayluxeo call", hour = 15, location = "Gulshan"), SpokenLanguage.ENGLISH)

        assertThat(row.title).isEqualTo("Rayluxeo call")
        assertThat(row.detail).isEqualTo("Today, 3 pm · Gulshan")
    }

    @Test
    fun `an all-day event row says all day in both languages`() {
        val allDay = event("Eid holiday", hour = 0, day = 11, allDay = true)

        assertThat(narrator.eventLine(allDay, SpokenLanguage.ENGLISH).detail).isEqualTo("All day")
        assertThat(narrator.eventLine(allDay, SpokenLanguage.BENGALI).detail).isEqualTo("সারাদিন")
    }

    @Test
    fun `a reminder row is the title and the moment`() {
        val row = narrator.reminderLine(reminder("Electricity bill", hour = 17), SpokenLanguage.BENGALI)

        assertThat(row.title).isEqualTo("Electricity bill")
        assertThat(row.detail).isEqualTo("আজ, বিকেল ৫টা")
    }

    /** An invoice number is a name Abu reads back to a client, so it keeps its Latin digits. */
    @Test
    fun `an invoice row keeps its number in Latin digits even in Bengali`() {
        val overdue = invoice(
            number = "INV-014",
            client = "Ahmed",
            amount = taka("45000"),
            dueOn = LocalDate.of(2026, 3, 6),
            isOverdue = true,
        )

        assertThat(narrator.invoiceLine(overdue, SpokenLanguage.ENGLISH))
            .isEqualTo(BriefLine("Ahmed", "BDT 45,000 · INV-014 · Overdue · 6 March"))
        assertThat(narrator.invoiceLine(overdue, SpokenLanguage.BENGALI))
            .isEqualTo(BriefLine("Ahmed", "৪৫,০০০ টাকা · INV-014 · ওভারডিউ · ৬ মার্চ"))
    }

    @Test
    fun `an invoice with no client is titled by its number`() {
        val row = narrator.invoiceLine(
            invoice(number = "INV-020", client = null, amount = taka("12000"), dueOn = LocalDate.of(2026, 3, 12)),
            SpokenLanguage.ENGLISH,
        )

        assertThat(row).isEqualTo(BriefLine("INV-020", "BDT 12,000 · 12 March"))
    }

    @Test
    fun `a payment with no client is still named`() {
        assertThat(narrator.paymentLine(payment(client = null, amount = "5000"), SpokenLanguage.ENGLISH))
            .isEqualTo(BriefLine("Payment", "BDT 5,000 · Today, 12 pm"))
        assertThat(narrator.paymentLine(payment(client = null, amount = "5000"), SpokenLanguage.BENGALI))
            .isEqualTo(BriefLine("পেমেন্ট", "৫,০০০ টাকা · আজ, দুপুর ১২টা"))
    }

    @Test
    fun `a deadline row names its milestone and marks a date that has gone by`() {
        val upcoming = deadline(due = LocalDate.of(2026, 3, 11), milestone = "Handover")
        val passed = deadline(due = LocalDate.of(2026, 3, 8))

        assertThat(narrator.deadlineLine(upcoming, SpokenLanguage.ENGLISH))
            .isEqualTo(BriefLine("Rayluxeo website — Handover", "11 March · Ahmed"))
        assertThat(narrator.deadlineLine(passed, SpokenLanguage.ENGLISH))
            .isEqualTo(BriefLine("Rayluxeo website", "Overdue · 8 March · Ahmed"))
    }

    @Test
    fun `a client never spoken to is a different row from one gone quiet`() {
        val stale = BriefFollowUp(
            clientId = 1,
            clientName = "Ahmed",
            lastContactAt = FixedMiraClock.dhaka(2026, 2, 26, 11),
            daysSince = 12,
        )
        val never = BriefFollowUp(clientId = 2, clientName = "Northwind", lastContactAt = null, daysSince = null)

        assertThat(narrator.followUpLine(stale, SpokenLanguage.ENGLISH).detail)
            .isEqualTo("Last contact 12 days ago")
        assertThat(narrator.followUpLine(stale, SpokenLanguage.BENGALI).detail)
            .isEqualTo("শেষ যোগাযোগ ১২ দিন আগে")
        assertThat(narrator.followUpLine(never, SpokenLanguage.ENGLISH).detail)
            .isEqualTo("No contact recorded.")
        assertThat(narrator.followUpLine(never, SpokenLanguage.BENGALI).detail)
            .isEqualTo("যোগাযোগের কোনো রেকর্ড নেই।")
    }

    @Test
    fun `an outstanding total reads as taka in Bengali and by code in English`() {
        assertThat(narrator.moneyPhrase(taka("150000"), SpokenLanguage.ENGLISH)).isEqualTo("BDT 1,50,000")
        assertThat(narrator.moneyPhrase(taka("150000"), SpokenLanguage.BENGALI)).isEqualTo("১,৫০,০০০ টাকা")
        assertThat(narrator.moneyPhrase(dollars("100"), SpokenLanguage.BENGALI)).isEqualTo("১০০ USD")
    }

    @Test
    fun `an afternoon brief greets the afternoon and an unnamed owner gets a bare greeting`() {
        val report = quietDay().copy(dayPart = DayPart.AFTERNOON, ownerName = null)

        assertThat(spoken(report, SpokenLanguage.ENGLISH)).isEqualTo("Good afternoon. Nothing on today.")
        assertThat(spoken(report, SpokenLanguage.BENGALI)).isEqualTo("শুভ দুপুর। আজ বিশেষ কিছু নেই।")
    }

    // ---- Fixtures ----

    private fun line(item: AttentionItem, language: SpokenLanguage = SpokenLanguage.ENGLISH) =
        narrator.attentionLine(item, language)

    private fun busyDay() = DailyBriefReport(
        date = TODAY,
        dayPart = DayPart.MORNING,
        ownerName = "Abu Sayed Ahmed",
        openTaskCount = 7,
        dueTodayCount = 3,
        overdueCount = 2,
        completedTodayCount = 1,
        dueToday = emptyList(),
        overdue = emptyList(),
        nextEvent = event("Rayluxeo call", hour = 15),
        todaysEvents = emptyList(),
        calendarReadable = true,
        upcomingReminders = listOf(reminder("Electricity bill", hour = 17), reminder("Call the bank", hour = 19)),
        money = money(outstanding = "120000", overdue = "45000"),
        deadlines = emptyList(),
        followUps = emptyList(),
        attention = listOf(overdueInvoice(), websiteDeadline(), adobeRenewal()),
    )

    private fun quietDay() = DailyBriefReport(
        date = TODAY,
        dayPart = DayPart.MORNING,
        ownerName = "Abu",
        openTaskCount = 0,
        dueTodayCount = 0,
        overdueCount = 0,
        completedTodayCount = 0,
        dueToday = emptyList(),
        overdue = emptyList(),
        nextEvent = null,
        todaysEvents = emptyList(),
        calendarReadable = true,
        upcomingReminders = emptyList(),
        money = money(),
        deadlines = emptyList(),
        followUps = emptyList(),
        attention = emptyList(),
    )

    private fun fullEvening() = EveningReviewReport(
        date = TODAY,
        completedToday = listOf(task("Homepage"), task("Logo"), task("Emails"), task("Quote")),
        stillOpen = listOf(task("Invoice Rayluxeo"), task("Brief the writer"), task("Book the flight")),
        paymentsReceived = listOf(payment("Ahmed", "30000")),
        paymentsTotal = taka("30000"),
        expensesRecorded = 3,
        expensesTotal = taka("4500"),
        invoicesCreated = listOf(invoice(number = "INV-014", client = "Rayluxeo", amount = taka("12000"))),
        tomorrowsEvents = listOf(event("Kickoff", hour = 9, day = 11)),
        tomorrowsPriorities = listOf(task("Finish the homepage")),
        calendarReadable = true,
    )

    private fun emptyEvening() = EveningReviewReport(
        date = TODAY,
        completedToday = emptyList(),
        stillOpen = emptyList(),
        paymentsReceived = emptyList(),
        paymentsTotal = null,
        expensesRecorded = 0,
        expensesTotal = null,
        invoicesCreated = emptyList(),
        tomorrowsEvents = emptyList(),
        tomorrowsPriorities = emptyList(),
        calendarReadable = true,
    )

    private fun overdueInvoice() = attention(
        kind = AttentionKind.INVOICE_OVERDUE,
        subject = "Ahmed",
        severity = AttentionSeverity.NOW,
        daysAway = -4,
    )

    private fun websiteDeadline() = attention(
        kind = AttentionKind.PROJECT_DEADLINE,
        subject = "Rayluxeo website",
        severity = AttentionSeverity.SOON,
        daysAway = 1,
    )

    private fun adobeRenewal() = attention(
        kind = AttentionKind.SUBSCRIPTION_RENEWING,
        subject = "Adobe",
        severity = AttentionSeverity.WATCH,
    )

    private fun attention(
        kind: AttentionKind,
        subject: String,
        severity: AttentionSeverity,
        amount: Money? = null,
        at: Instant? = null,
        daysAway: Long? = null,
    ) = AttentionItem(
        kind = kind,
        subject = subject,
        severity = severity,
        amount = amount,
        at = at,
        daysAway = daysAway,
    )

    private fun event(
        title: String,
        hour: Int,
        day: Int = 10,
        allDay: Boolean = false,
        location: String? = null,
    ) = BriefEvent(
        title = title,
        startsAt = FixedMiraClock.dhaka(2026, 3, day, hour),
        endsAt = null,
        location = location,
        allDay = allDay,
    )

    private fun reminder(title: String, hour: Int) =
        BriefReminder(id = 1, title = title, triggerAt = FixedMiraClock.dhaka(2026, 3, 10, hour))

    private fun task(title: String) = BriefTask(id = 1, title = title, dueAt = null, isOverdue = false)

    private fun invoice(
        number: String,
        client: String?,
        amount: Money,
        dueOn: LocalDate? = null,
        isOverdue: Boolean = false,
    ) = BriefInvoice(
        id = 1,
        number = number,
        clientName = client,
        amount = amount,
        dueOn = dueOn,
        isOverdue = isOverdue,
    )

    private fun payment(client: String?, amount: String) = BriefPayment(
        id = 1,
        clientName = client,
        amount = taka(amount),
        receivedAt = FixedMiraClock.dhaka(2026, 3, 10, 12),
    )

    private fun deadline(due: LocalDate, milestone: String? = null) = BriefDeadline(
        projectId = 1,
        projectName = "Rayluxeo website",
        clientName = "Ahmed",
        dueOn = due,
        milestone = milestone,
    )

    private fun money(outstanding: String? = null, overdue: String? = null) = BriefMoney(
        outstandingTotal = outstanding?.let { taka(it) },
        overdueTotal = overdue?.let { taka(it) },
        invoiceCount = 0,
        overdueCount = 0,
        dueSoon = emptyList(),
        recentPayments = emptyList(),
    )

    private fun taka(value: String) = Money.of(value)

    private fun dollars(value: String) = Money.of(value, Currency.getInstance("USD"))

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 3, 10)
    }
}
