package com.rayluxeo.aida.ui.home

import com.rayluxeo.aida.domain.brief.AttentionTarget
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.AttentionKind
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.ui.response.bengaliNumerals
import com.rayluxeo.aida.ui.response.moneyFigure

/**
 * Every word Home says to Abu, in the language he chose.
 *
 * Home reads its dates and its task lines through [com.rayluxeo.aida.ui.tasks.TaskTimeFormatter],
 * so those were already bilingual — but the chrome around them was not, and the screen ended up
 * reading "Overdue" beside "আগামীকাল, সকাল ১০টা". This follows the same phrasebook shape as
 * ui/response and ui/tasks: one interface, one object per language, one selector.
 *
 * The brief's own models carry no prose — an [AttentionKind] and a subject, never a sentence —
 * so the line Abu reads on the Needs Attention card is composed here. That is the only reason a
 * phrasebook belongs to a screen at all: the engine could not have written the sentence without
 * picking a language, which `domain` is not allowed to do.
 */
internal interface HomeWording {

    /**
     * A bare count as Abu reads it. Bengali uses its own digits, so a number dropped straight
     * into the UI with `toString()` is the one place on Home where the language leaks.
     */
    fun number(value: Int): String

    /** The one line of brand voice on the hero. Quiet, and true to the product. */
    val tagline: String
    val invoices: String
    val agenda: String
    val askAnything: String
    val reviewNow: String
    fun statDueToday(count: Int): String
    fun statOverdueInvoices(count: Int): String
    fun statUpcomingReminders(count: Int): String
    val today: String

    /** The one glance line under the greeting: what needs him and what is due today. */
    fun homeGlance(needs: Int, dueToday: Int): String

    /** What the greeting says instead while she is hearing him out. */
    val listening: String

    /** ...and while she is working out what he meant. */
    val working: String
    val open: String
    val nothingOpen: String
    val reminders: String
    val money: String

    /** The Salah card's section header. */
    val salah: String

    /** The way through from the card to the Salah screen. */
    val viewSalah: String
    val deadlines: String
    val followUps: String
    val quickCapture: String
    val capturePlaceholder: String
    val send: String
    val askMira: String
    val readBrief: String
    val stopReading: String
    val tasks: String
    val vault: String
    val settings: String

    /** The content description for the search control on Home's top bar. */
    val search: String

    /** The label for the Areas control on Home's top bar — the door to every feature off the bar. */
    val areas: String
    val overdueCount: String
    val dueTodayCount: String
    val openCount: String
    val doneTodayCount: String
    val highPriority: String
    val dismiss: String
    val confirmYes: String
    val confirmNo: String
    val outstanding: String
    val overdueMoney: String
    val dueSoon: String
    val recentPayments: String
    val calendarUnreadable: String
    val spokenRepliesOff: String
    val openSettings: String
    /** A payment whose client is not recorded still has to be called something. */
    val payment: String

    val needsAttention: String
    val openAction: String
    val remindMeAction: String
    val markDoneAction: String
    val dismissAction: String
    val reminderCreated: String

    /** The persisted title of a reminder made from a focus card: a localized prefix on the subject. */
    fun reviewTitle(subject: String): String

    /** The localized name of the kind of record a focus row points at. */
    fun recordTypeLabel(target: AttentionTarget): String

    fun greeting(part: DayPart, ownerName: String?): String

    fun moreOpen(count: Int): String

    fun amount(money: Money): String

    fun acrossInvoices(count: Int): String

    fun overdueInvoices(count: Int): String

    fun attentionLine(kind: AttentionKind, subject: String, daysAway: Long?): String

    fun invoiceTitle(number: String, clientName: String?): String

    fun invoiceDetail(value: Money, dueLabel: String?, isOverdue: Boolean): String

    fun paymentDetail(value: Money, whenLabel: String): String

    fun deadlineDetail(clientName: String?, milestone: String?, dueLabel: String): String

    fun followUpDetail(daysSince: Long?): String

    fun eventDetail(whenLabel: String, location: String?): String

    // ------------------------------------------------------------------ read aloud

    fun spokenTasks(dueToday: Int, overdue: Int, stillOpen: Int): String

    fun spokenEvent(title: String, whenLabel: String): String

    fun spokenReminder(title: String, whenLabel: String): String

    fun spokenMoney(outstanding: Money?, overdueInvoiceCount: Int): String

    /** The same line the card shows, closed off so it reads as a sentence when it is spoken. */
    fun spokenAttention(kind: AttentionKind, subject: String, daysAway: Long?): String

    /** What Mira says when the day genuinely holds nothing. Reported, never congratulated. */
    val spokenQuiet: String

    /** The spoken Salah line: the next prayer and when it falls. Said even on a quiet day. */
    fun spokenSalah(prayerName: String, moment: String, tomorrow: Boolean): String
}

internal object EnglishHomeWording : HomeWording {
    override val tagline = "Progress lives in the details."
    override val invoices = "Invoices"
    override val agenda = "Reminders"
    override val askAnything = "Ask Aida anything…"
    override val reviewNow = "Review now"
    override fun statDueToday(count: Int) = if (count == 1) "1 due today" else "$count due today"
    override fun statOverdueInvoices(count: Int) =
        if (count == 0) "On track" else if (count == 1) "1 overdue" else "$count overdue"
    override fun statUpcomingReminders(count: Int) =
        if (count == 1) "1 upcoming" else "$count upcoming"
    override val today = "Today"
    override val listening = "Listening…"
    override val working = "One moment…"

    override fun homeGlance(needs: Int, dueToday: Int): String {
        val a = when {
            needs == 1 -> "One thing needs you"
            needs > 1 -> "$needs things need you"
            else -> null
        }
        val b = when {
            dueToday == 1 -> if (a == null) "One task is due today" else "one is due today"
            dueToday > 1 -> if (a == null) "$dueToday tasks are due today" else "$dueToday are due today"
            else -> null
        }
        return when {
            a != null && b != null -> "$a, and $b."
            a != null -> "$a."
            b != null -> b.replaceFirstChar { it.uppercase() } + "."
            else -> "You're all caught up."
        }
    }
    override val open = "Open"
    override val nothingOpen = "Nothing open right now."
    override val reminders = "Reminders"
    override val money = "Money"
    override val salah = "Salah"
    override val viewSalah = "View Salah"
    override val deadlines = "Deadlines"
    override val followUps = "Follow-ups"
    override val quickCapture = "Quick capture"
    override val capturePlaceholder = "Task, reminder, payment…"
    override val send = "Send"
    override val askMira = "Ask Aida"
    override val readBrief = "Read my brief"
    override val stopReading = "Stop"
    override val tasks = "Tasks"
    override val vault = "Vault"
    override val settings = "Settings"
    override val search = "Search"
    override val areas = "Areas"
    override val overdueCount = "Overdue"
    override val dueTodayCount = "Due today"
    override val openCount = "Open"
    override val doneTodayCount = "Done today"
    override val highPriority = "High"
    override val dismiss = "Dismiss"
    override val confirmYes = "Yes, do it"
    override val confirmNo = "No"
    override val outstanding = "Outstanding"
    override val overdueMoney = "Overdue"
    override val dueSoon = "Due soon"
    override val recentPayments = "Recent payments"
    override fun number(value: Int) = value.toString()

    override val calendarUnreadable = "Aida cannot read the calendar."
    override val spokenRepliesOff = "Aida's voice is switched off."
    override val openSettings = "Settings"
    override val payment = "Payment"

    override val needsAttention = "Needs Attention"
    override val openAction = "Open"
    override val remindMeAction = "Remind Me"
    override val markDoneAction = "Mark Done"
    override val dismissAction = "Dismiss"
    override val reminderCreated = "Reminder set."
    override fun reviewTitle(subject: String) = "Review: $subject"
    override fun recordTypeLabel(target: AttentionTarget) = when (target) {
        AttentionTarget.INVOICE -> "Invoice"
        AttentionTarget.PROJECT -> "Project"
        AttentionTarget.TASK -> "Task"
        AttentionTarget.CLIENT -> "Client"
        AttentionTarget.SUBSCRIPTION -> "Subscription"
    }

    override fun greeting(part: DayPart, ownerName: String?): String {
        val name = ownerName.firstName()
        val opening = when (part) {
            DayPart.MORNING -> "Good morning"
            DayPart.AFTERNOON -> "Good afternoon"
            // "Good night" is a farewell in English, so NIGHT shares the evening greeting rather
            // than opening the day with a goodbye. The same rule as ui/response.
            DayPart.EVENING, DayPart.NIGHT -> "Good evening"
        }
        return if (name == null) "$opening." else "$opening, $name."
    }

    override fun moreOpen(count: Int) = if (count == 1) "1 more open" else "$count more open"

    override fun amount(money: Money) = "${money.currency.currencyCode} ${moneyFigure(money)}"

    override fun acrossInvoices(count: Int) =
        if (count == 1) "Across 1 invoice" else "Across $count invoices"

    override fun overdueInvoices(count: Int) =
        if (count == 1) "1 invoice overdue" else "$count invoices overdue"

    override fun attentionLine(kind: AttentionKind, subject: String, daysAway: Long?): String {
        val late = daysAway?.takeIf { it < 0 }?.let { -it }
        return when (kind) {
            AttentionKind.INVOICE_OVERDUE -> when (late) {
                null -> "$subject's invoice is overdue"
                else -> "$subject's invoice is ${days(late)} overdue"
            }
            AttentionKind.INVOICE_DUE_SOON -> "$subject's invoice is due ${whenPhrase(daysAway)}"
            AttentionKind.PROJECT_DEADLINE -> when (late) {
                null -> "$subject is due ${whenPhrase(daysAway)}"
                else -> "$subject was due ${days(late)} ago"
            }
            AttentionKind.SUBSCRIPTION_RENEWING -> "$subject renews ${whenPhrase(daysAway)}"
            AttentionKind.TASK_OVERDUE -> when (late) {
                null -> "$subject is overdue"
                else -> "$subject is ${days(late)} overdue"
            }
            AttentionKind.CLIENT_FOLLOW_UP -> when (late) {
                null -> "No contact with $subject yet"
                else -> "No contact with $subject for ${days(late)}"
            }
        }
    }

    override fun invoiceTitle(number: String, clientName: String?) =
        if (clientName == null) number else "$number · $clientName"

    override fun invoiceDetail(value: Money, dueLabel: String?, isOverdue: Boolean): String {
        val figure = amount(value)
        val timing = dueLabel ?: return figure
        return if (isOverdue) "$figure · Overdue $timing" else "$figure · Due $timing"
    }

    override fun paymentDetail(value: Money, whenLabel: String) = "${amount(value)} · $whenLabel"

    override fun deadlineDetail(clientName: String?, milestone: String?, dueLabel: String): String =
        listOfNotNull(milestone, clientName, "Due $dueLabel").joinToString(" · ")

    override fun followUpDetail(daysSince: Long?): String =
        if (daysSince == null) "No contact recorded" else "Last contact ${days(daysSince)} ago"

    override fun eventDetail(whenLabel: String, location: String?): String =
        if (location == null) whenLabel else "$whenLabel · $location"

    override fun spokenTasks(dueToday: Int, overdue: Int, stillOpen: Int): String = when {
        overdue > 0 && dueToday > 0 -> "$overdue overdue and $dueToday due today."
        overdue > 0 -> "$overdue overdue."
        dueToday > 0 -> "$dueToday due today."
        stillOpen > 0 -> "Nothing due today, $stillOpen open."
        else -> "Nothing due today."
    }

    override fun spokenEvent(title: String, whenLabel: String) = "Next: $title, $whenLabel."

    override fun spokenReminder(title: String, whenLabel: String) = "Reminder: $title, $whenLabel."

    override fun spokenMoney(outstanding: Money?, overdueInvoiceCount: Int): String {
        val total = outstanding?.let { "${amount(it)} outstanding" }
        val late = if (overdueInvoiceCount > 0) overdueInvoices(overdueInvoiceCount) else null
        val parts = listOfNotNull(total, late)
        return if (parts.isEmpty()) "" else "${parts.joinToString(", ")}."
    }

    override fun spokenAttention(kind: AttentionKind, subject: String, daysAway: Long?) =
        "${attentionLine(kind, subject, daysAway)}."

    override val spokenQuiet = "Nothing needs attention."

    override fun spokenSalah(prayerName: String, moment: String, tomorrow: Boolean): String =
        if (tomorrow) "$prayerName is at $moment tomorrow." else "$prayerName is at $moment."

    /** "1 day" reads wrong as "1 days", and Mira says numerals rather than spelling them out. */
    private fun days(count: Long) = if (count == 1L) "1 day" else "$count days"

    private fun whenPhrase(daysAway: Long?): String = when {
        daysAway == null -> "soon"
        daysAway == 0L -> "today"
        daysAway == 1L -> "tomorrow"
        daysAway > 1L -> "in ${days(daysAway)}"
        daysAway == -1L -> "yesterday"
        else -> "${days(-daysAway)} ago"
    }
}

internal object BengaliHomeWording : HomeWording {
    override val tagline = "উন্নতি লুকায় খুঁটিনাটিতে।"
    override val invoices = "ইনভয়স"
    override val agenda = "রিমাইন্ডার"
    override val askAnything = "আইদাকে যা খুশি জিজ্ঞাসা করুন…"
    override val reviewNow = "এখনই দেখুন"
    override fun statDueToday(count: Int) = "${number(count)} আজ"
    override fun statOverdueInvoices(count: Int) =
        if (count == 0) "ঠিক আছে" else "${number(count)} বকেয়া"
    override fun statUpcomingReminders(count: Int) = "${number(count)} আসছে"
    override val today = "আজ"
    override val listening = "শুনছি…"
    override val working = "এক মুহূর্ত…"

    override fun homeGlance(needs: Int, dueToday: Int): String {
        val a = when {
            needs == 1 -> "একটি বিষয়ে নজর দরকার"
            needs > 1 -> "${digits(needs)}টি বিষয়ে নজর দরকার"
            else -> null
        }
        val b = when {
            dueToday == 1 -> "আজ একটি কাজ আছে"
            dueToday > 1 -> if (a == null) "আজ ${digits(dueToday)}টি কাজ আছে" else "আজ ${digits(dueToday)}টি আছে"
            else -> null
        }
        return when {
            a != null && b != null -> "$a, আর $b।"
            a != null -> "$a।"
            b != null -> "$b।"
            else -> "সব গুছানো আছে।"
        }
    }
    override val open = "বাকি"
    override val nothingOpen = "এখন কিছু বাকি নেই।"
    override val reminders = "রিমাইন্ডার"
    override val money = "টাকা-পয়সা"
    override val salah = "নামাজ"
    override val viewSalah = "নামাজ দেখুন"
    override val deadlines = "সময়সীমা"
    override val followUps = "যোগাযোগ বাকি"
    override val quickCapture = "দ্রুত লিখুন"
    override val capturePlaceholder = "কাজ, রিমাইন্ডার, পেমেন্ট…"
    override val send = "পাঠান"
    override val askMira = "আইদাকে জিজ্ঞাসা করুন"
    override val readBrief = "আজকের কথা শোনান"
    override val stopReading = "থামুন"
    override val tasks = "কাজ"
    override val vault = "ভল্ট"
    override val settings = "সেটিংস"
    override val search = "খুঁজুন"
    override val areas = "সব বিভাগ"
    override val overdueCount = "সময় পেরিয়েছে"
    override val dueTodayCount = "আজ শেষ"
    override val openCount = "বাকি"
    override val doneTodayCount = "আজ হয়েছে"
    override val highPriority = "জরুরি"
    override val dismiss = "বন্ধ করুন"
    override val confirmYes = "হ্যাঁ, করুন"
    override val confirmNo = "না"
    override val outstanding = "বকেয়া"
    override val overdueMoney = "সময় পেরিয়েছে"
    override val dueSoon = "শীঘ্রই দিতে হবে"
    override val recentPayments = "সাম্প্রতিক পেমেন্ট"
    override fun number(value: Int) = bengaliNumerals(value.toString())

    override val calendarUnreadable = "আইদা ক্যালেন্ডার পড়তে পারছে না।"
    override val spokenRepliesOff = "আইদার কণ্ঠ বন্ধ আছে।"
    override val openSettings = "সেটিংস"
    override val payment = "পেমেন্ট"

    override val needsAttention = "মনোযোগ প্রয়োজন"
    override val openAction = "খুলুন"
    override val remindMeAction = "মনে করিয়ে দাও"
    override val markDoneAction = "সম্পন্ন করো"
    override val dismissAction = "বাতিল"
    override val reminderCreated = "রিমাইন্ডার সেট করা হয়েছে।"
    override fun reviewTitle(subject: String) = "পর্যালোচনা: $subject"
    override fun recordTypeLabel(target: AttentionTarget) = when (target) {
        AttentionTarget.INVOICE -> "ইনভয়স"
        AttentionTarget.PROJECT -> "প্রজেক্ট"
        AttentionTarget.TASK -> "কাজ"
        AttentionTarget.CLIENT -> "ক্লায়েন্ট"
        AttentionTarget.SUBSCRIPTION -> "সাবস্ক্রিপশন"
    }

    override fun greeting(part: DayPart, ownerName: String?): String {
        val name = ownerName.firstName()
        val opening = when (part) {
            DayPart.MORNING -> "শুভ সকাল"
            DayPart.AFTERNOON -> "শুভ দুপুর"
            // "শুভ রাত্রি" is what you say on the way out, so NIGHT shares the evening greeting.
            DayPart.EVENING, DayPart.NIGHT -> "শুভ সন্ধ্যা"
        }
        return if (name == null) "$opening।" else "$opening, $name।"
    }

    override fun moreOpen(count: Int) = "আরও ${digits(count)}টি বাকি"

    override fun amount(money: Money) =
        "${bengaliNumerals(moneyFigure(money))} ${currencyWord(money)}"

    override fun acrossInvoices(count: Int) = "${digits(count)}টি ইনভয়েসে"

    override fun overdueInvoices(count: Int) = "${digits(count)}টি ইনভয়েসের সময় পেরিয়েছে"

    override fun attentionLine(kind: AttentionKind, subject: String, daysAway: Long?): String {
        val late = daysAway?.takeIf { it < 0 }?.let { -it }
        return when (kind) {
            AttentionKind.INVOICE_OVERDUE -> when (late) {
                null -> "$subject-এর ইনভয়েসের সময় পেরিয়েছে"
                else -> "$subject-এর ইনভয়েস ${days(late)} ধরে বকেয়া"
            }
            AttentionKind.INVOICE_DUE_SOON -> "$subject-এর ইনভয়েস ${whenPhrase(daysAway)} দিতে হবে"
            AttentionKind.PROJECT_DEADLINE -> when (late) {
                null -> "$subject-এর সময়সীমা ${whenPhrase(daysAway)}"
                else -> "$subject-এর সময়সীমা ${days(late)} আগে পেরিয়েছে"
            }
            AttentionKind.SUBSCRIPTION_RENEWING -> "$subject-এর নবায়ন ${whenPhrase(daysAway)}"
            AttentionKind.TASK_OVERDUE -> when (late) {
                null -> "$subject-এর সময় পেরিয়েছে"
                else -> "$subject ${days(late)} ধরে বাকি"
            }
            AttentionKind.CLIENT_FOLLOW_UP -> when (late) {
                null -> "$subject-এর সাথে এখনও যোগাযোগ হয়নি"
                else -> "$subject-এর সাথে ${days(late)} যোগাযোগ হয়নি"
            }
        }
    }

    override fun invoiceTitle(number: String, clientName: String?) =
        if (clientName == null) number else "$number · $clientName"

    override fun invoiceDetail(value: Money, dueLabel: String?, isOverdue: Boolean): String {
        val figure = amount(value)
        val timing = dueLabel ?: return figure
        return if (isOverdue) "$figure · $timing, সময় পেরিয়েছে" else "$figure · $timing"
    }

    override fun paymentDetail(value: Money, whenLabel: String) = "${amount(value)} · $whenLabel"

    override fun deadlineDetail(clientName: String?, milestone: String?, dueLabel: String): String =
        listOfNotNull(milestone, clientName, dueLabel).joinToString(" · ")

    override fun followUpDetail(daysSince: Long?): String =
        if (daysSince == null) "কোনো যোগাযোগের হিসাব নেই" else "শেষ যোগাযোগ ${days(daysSince)} আগে"

    override fun eventDetail(whenLabel: String, location: String?): String =
        if (location == null) whenLabel else "$whenLabel · $location"

    override fun spokenTasks(dueToday: Int, overdue: Int, stillOpen: Int): String = when {
        overdue > 0 && dueToday > 0 ->
            "${digits(overdue)}টির সময় পেরিয়ে গেছে, আজ ${digits(dueToday)}টি আছে।"
        overdue > 0 -> "${digits(overdue)}টির সময় পেরিয়ে গেছে।"
        dueToday > 0 -> "আজ ${digits(dueToday)}টি কাজ আছে।"
        stillOpen > 0 -> "আজ কিছু নেই, ${digits(stillOpen)}টি কাজ বাকি।"
        else -> "আজ নির্দিষ্ট কোনো কাজ নেই।"
    }

    override fun spokenEvent(title: String, whenLabel: String) = "এরপর $whenLabel — $title।"

    override fun spokenReminder(title: String, whenLabel: String) = "রিমাইন্ডার $whenLabel — $title।"

    override fun spokenMoney(outstanding: Money?, overdueInvoiceCount: Int): String {
        val total = outstanding?.let { "${amount(it)} বকেয়া" }
        val late = if (overdueInvoiceCount > 0) overdueInvoices(overdueInvoiceCount) else null
        val parts = listOfNotNull(total, late)
        return if (parts.isEmpty()) "" else "${parts.joinToString(", ")}।"
    }

    override fun spokenAttention(kind: AttentionKind, subject: String, daysAway: Long?) =
        "${attentionLine(kind, subject, daysAway)}।"

    override val spokenQuiet = "নজর দেওয়ার মতো কিছু নেই।"

    override fun spokenSalah(prayerName: String, moment: String, tomorrow: Boolean): String =
        if (tomorrow) "আগামীকাল $prayerName — $moment।" else "পরবর্তী নামাজ $prayerName — $moment।"

    private fun days(count: Long) = "${digits(count)} দিন"

    private fun whenPhrase(daysAway: Long?): String = when {
        daysAway == null -> "শীঘ্রই"
        daysAway == 0L -> "আজ"
        daysAway == 1L -> "আগামীকাল"
        daysAway > 1L -> "${days(daysAway)} পরে"
        daysAway == -1L -> "গতকাল"
        else -> "${days(-daysAway)} আগে"
    }

    private fun digits(value: Int) = bengaliNumerals(value.toString())

    private fun digits(value: Long) = bengaliNumerals(value.toString())

    /** Taka has a word; anything else keeps its code, exactly as the response phrasebook does. */
    private fun currencyWord(money: Money) =
        if (money.currency == Money.BDT) "টাকা" else money.currency.currencyCode
}

internal fun homeWording(language: SpokenLanguage): HomeWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishHomeWording
    SpokenLanguage.BENGALI -> BengaliHomeWording
}

/**
 * Abu is greeted by name, not by record.
 *
 * The profile holds whatever he typed — often his full name — and "Good morning, Abu Sayed Ahmed"
 * is a form letter rather than a greeting.
 */
private fun String?.firstName(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.substringBefore(' ')
