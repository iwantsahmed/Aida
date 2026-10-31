package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.AttentionKind
import com.rayluxeo.aida.domain.brief.AttentionSeverity
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Money

/**
 * Every word the daily brief, the evening review and the Needs Attention card say to Abu.
 *
 * The engines under `domain/brief` return counts, amounts and names and nothing else, which is
 * what lets the same report be spoken in Bengali and read in English. The sentences themselves
 * cannot be assembled from translated fragments — "৩টির সময় পেরিয়ে গেছে" is not "3" plus
 * "overdue" — so a whole sentence is what each language owns here, not a word list.
 *
 * Same shape as the phrasebooks beside it and as `ui/home/HomeWording`: one interface, one
 * object per language, one selector.
 */
internal interface BriefWording {

    // ---- What Home puts on screen: fragments and labels, never sentences. ----

    val needsAttention: String
    val nothingNeedsAttention: String
    val readMyBrief: String
    val quickCapture: String
    val askMira: String
    val nextUp: String
    val upcomingReminders: String
    val outstanding: String
    val dueSoon: String
    val recentPayments: String
    val projectDeadlines: String
    val followUps: String
    val noEventsToday: String

    /** The screen's way of saying the calendar could not be read, as a label above an empty list. */
    val calendarUnreadable: String

    // ---- The notification: two lines, and the channel Android shows in system settings. ----

    val morningTitle: String
    val eveningTitle: String

    /**
     * What Mira says when the report could not be built at all. She says something rather than
     * nothing: silence on a morning she could not read the database looks exactly like a quiet
     * day, and those are different facts.
     */
    val briefUnavailable: String

    val briefChannelName: String
    val briefChannelDescription: String
    val reminderChannelName: String
    val reminderChannelDescription: String

    val allDay: String

    /** A payment whose client Mira does not know still has to be called something. */
    val paymentReceived: String

    val noContactRecorded: String

    /** Marks an invoice or a deadline whose date has gone by, in a row that has no room for more. */
    val overdueMark: String

    // ---- What a notification puts in its title. ----

    val morningBriefTitle: String
    val eveningReviewTitle: String

    // ---- What Mira says out loud. ----

    /**
     * Mira says the calendar is unreadable rather than saying there is nothing in it. Permission
     * withheld and a genuinely empty day are different facts and Abu acts differently on each.
     */
    val calendarUnreadableSentence: String

    /** A day with nothing on it is a normal day, and one short sentence is the honest report. */
    val quietDay: String

    val quietEvening: String

    fun greeting(part: DayPart, name: String?): String

    fun taskSentence(dueToday: Int, overdue: Int, open: Int): String

    fun nextEventSentence(title: String, moment: String): String

    fun reminderSentence(count: Int, title: String, moment: String): String

    /**
     * Called only when at least one of the two is a non-zero amount, so an empty brief never
     * announces a balance of nothing.
     */
    fun moneySentence(outstanding: Money?, overdue: Money?): String

    fun needAttention(count: Int): String

    fun moreNeedAttention(count: Int): String

    fun completedSentence(done: Int, open: Int): String

    /** [total] is null when the day's payments were in currencies that cannot be added together. */
    fun paymentsSentence(count: Int, total: Money?): String

    fun expensesSentence(count: Int, total: Money?): String

    fun invoicesRaisedSentence(count: Int, total: Money?): String

    /** [title] and [moment] are null when the count is all Mira has, as in a notification line. */
    fun tomorrowEventsSentence(count: Int, title: String?, moment: String?): String

    fun tomorrowPrioritySentence(count: Int, title: String): String

    /**
     * The morning brief's one Salah line — the next prayer and when it falls. [moment] is a bare
     * time already worded by the formatter; [tomorrow] is true after Isha, when the next prayer is
     * the following dawn.
     */
    fun salahNextSentence(prayerName: String, moment: String, tomorrow: Boolean): String

    /**
     * The evening review's Salah line: how many of the five obligatory prayers Abu has recorded as
     * prayed. Never judgemental (his instruction) — it reports a tally, never a prayer "missed".
     */
    fun salahReviewSentence(completed: Int, total: Int): String

    // ---- Shared by the card, the notification and the spoken brief. ----

    /**
     * The Needs Attention line. [subject] is a name out of Abu's records and is never inflected,
     * because a possessive built onto an arbitrary name is how a card starts mangling them.
     */
    fun attentionTitle(kind: AttentionKind, subject: String, timing: AttentionTiming): String

    fun money(value: Money): String

    fun daysSinceContact(days: Long): String
}

/**
 * When the thing behind an attention item happens, reduced to the cases a sentence actually
 * distinguishes.
 *
 * [Unspecified] carries the severity because a record with no date still has to say how loud it
 * is — the card never leaves that to colour alone.
 */
internal sealed interface AttentionTiming {
    data object Today : AttentionTiming
    data object Tomorrow : AttentionTiming
    data class InDays(val days: Long) : AttentionTiming
    data class DaysAgo(val days: Long) : AttentionTiming
    data class Unspecified(val severity: AttentionSeverity) : AttentionTiming
}

internal object EnglishBriefWording : BriefWording {

    override val morningTitle = "Your morning brief"
    override val eveningTitle = "Today in review"
    override val briefUnavailable = "Aida could not put the brief together this time."
    override val briefChannelName = "Briefs"
    override val briefChannelDescription = "The morning brief and the evening review, when they are switched on."
    override val reminderChannelName = "Reminders"
    override val reminderChannelDescription = "Reminders Aida is holding for you."

    override val needsAttention = "Needs attention"
    override val nothingNeedsAttention = "Nothing needs attention."
    override val readMyBrief = "Read my brief"
    override val quickCapture = "Quick capture"
    override val askMira = "Ask Aida"
    override val nextUp = "Next"
    override val upcomingReminders = "Reminders"
    override val outstanding = "Outstanding"
    override val dueSoon = "Due soon"
    override val recentPayments = "Recent payments"
    override val projectDeadlines = "Deadlines"
    override val followUps = "Follow-ups"
    override val noEventsToday = "Nothing in the calendar today."
    override val calendarUnreadable = "Calendar not readable."
    override val allDay = "All day"
    override val paymentReceived = "Payment"
    override val noContactRecorded = "No contact recorded."
    override val overdueMark = "Overdue"
    override val morningBriefTitle = "Morning brief"
    override val eveningReviewTitle = "Evening review"
    override val calendarUnreadableSentence = "I can't see your calendar."
    override val quietDay = "Nothing on today."
    override val quietEvening = "Nothing recorded today."

    /**
     * NIGHT shares EVENING's greeting for the reason the phrasebooks give: "good night" is a
     * farewell in English, and a brief that opens with a goodbye reads as a mistake.
     */
    override fun greeting(part: DayPart, name: String?): String {
        val hello = when (part) {
            DayPart.MORNING -> "Good morning"
            DayPart.AFTERNOON -> "Good afternoon"
            DayPart.EVENING, DayPart.NIGHT -> "Good evening"
        }
        return if (name == null) "$hello." else "$hello, $name."
    }

    /** Overdue work is named first, because that is the part Abu can still be surprised by. */
    override fun taskSentence(dueToday: Int, overdue: Int, open: Int): String = when {
        overdue > 0 && dueToday > 0 -> "$overdue overdue and $dueToday due today."
        overdue > 0 -> "$overdue overdue."
        dueToday > 0 -> "$dueToday due today."
        open > 0 -> "Nothing due today, $open open."
        else -> "Nothing due today."
    }

    override fun nextEventSentence(title: String, moment: String): String = "Next, $title — $moment."

    override fun reminderSentence(count: Int, title: String, moment: String): String =
        if (count <= 1) "One reminder — $title, $moment."
        else "$count reminders, the next — $title, $moment."

    override fun moneySentence(outstanding: Money?, overdue: Money?): String = when {
        outstanding != null && overdue != null ->
            "${money(outstanding)} outstanding, ${money(overdue)} of it overdue."
        outstanding != null -> "${money(outstanding)} outstanding."
        overdue != null -> "${money(overdue)} overdue."
        else -> ""
    }

    override fun needAttention(count: Int): String =
        if (count == 1) "1 needs attention." else "$count need attention."

    override fun moreNeedAttention(count: Int): String =
        if (count == 1) "1 more needs attention." else "$count more need attention."

    override fun completedSentence(done: Int, open: Int): String = when {
        done > 0 && open > 0 -> "$done done, $open still open."
        done > 0 -> "$done done."
        open > 0 -> "Nothing marked done, $open still open."
        else -> "Nothing marked done."
    }

    override fun paymentsSentence(count: Int, total: Money?): String {
        val head = if (count == 1) "One payment in" else "$count payments in"
        return if (total == null) "$head." else "$head, ${money(total)}."
    }

    override fun expensesSentence(count: Int, total: Money?): String {
        val head = if (count == 1) "One expense recorded" else "$count expenses recorded"
        return if (total == null) "$head." else "$head, ${money(total)}."
    }

    override fun invoicesRaisedSentence(count: Int, total: Money?): String {
        val head = if (count == 1) "One invoice raised" else "$count invoices raised"
        return if (total == null) "$head." else "$head, ${money(total)}."
    }

    override fun tomorrowEventsSentence(count: Int, title: String?, moment: String?): String = when {
        count == 0 -> "Nothing in the calendar tomorrow."
        title == null -> if (count == 1) "One event tomorrow." else "$count events tomorrow."
        count == 1 -> "One event tomorrow — $title, $moment."
        else -> "$count events tomorrow, the first — $title, $moment."
    }

    override fun tomorrowPrioritySentence(count: Int, title: String): String =
        if (count <= 1) "First tomorrow — $title."
        else "$count tasks waiting tomorrow, first — $title."

    override fun salahNextSentence(prayerName: String, moment: String, tomorrow: Boolean): String =
        if (tomorrow) "$prayerName is at $moment tomorrow." else "$prayerName is at $moment."

    override fun salahReviewSentence(completed: Int, total: Int): String =
        if (completed >= total) "Salah: all $total recorded as prayed."
        else "Salah: $completed of $total recorded as prayed."

    override fun attentionTitle(
        kind: AttentionKind,
        subject: String,
        timing: AttentionTiming,
    ): String = when (kind) {
        AttentionKind.INVOICE_OVERDUE -> "${possessive(subject)} invoice is overdue."

        AttentionKind.INVOICE_DUE_SOON -> when (timing) {
            is AttentionTiming.DaysAgo -> "${possessive(subject)} invoice was due ${dayCount(timing.days)} ago."
            else -> "${possessive(subject)} invoice is due ${ahead(timing)}."
        }

        AttentionKind.PROJECT_DEADLINE -> when (timing) {
            is AttentionTiming.DaysAgo -> "$subject is past its deadline."
            else -> "$subject is due ${ahead(timing)}."
        }

        AttentionKind.SUBSCRIPTION_RENEWING -> when (timing) {
            is AttentionTiming.DaysAgo -> "$subject subscription renewed ${dayCount(timing.days)} ago."
            else -> "$subject subscription renews ${ahead(timing)}."
        }

        AttentionKind.TASK_OVERDUE -> "$subject is overdue."

        AttentionKind.CLIENT_FOLLOW_UP -> when (timing) {
            is AttentionTiming.DaysAgo -> "No word from $subject in ${dayCount(timing.days)}."
            else -> "$subject is due a follow-up."
        }
    }

    /** The currency is named: a bare figure for money Abu is owed is not enough to act on. */
    override fun money(value: Money): String = "${value.currency.currencyCode} ${moneyFigure(value)}"

    override fun daysSinceContact(days: Long): String = "Last contact ${dayCount(days)} ago"

    /**
     * A date that has gone by on a forward-looking kind reads as "now": the thing is due and the
     * day for it has passed, which is the same instruction to Abu either way.
     */
    private fun ahead(timing: AttentionTiming): String = when (timing) {
        AttentionTiming.Today -> "today"
        AttentionTiming.Tomorrow -> "tomorrow"
        is AttentionTiming.InDays -> "in ${dayCount(timing.days)}"
        is AttentionTiming.DaysAgo -> "now"
        is AttentionTiming.Unspecified -> when (timing.severity) {
            AttentionSeverity.NOW -> "now"
            AttentionSeverity.SOON, AttentionSeverity.WATCH -> "soon"
        }
    }

    private fun dayCount(value: Long): String = if (value == 1L) "1 day" else "$value days"

    /** Abu's client list holds names like "Jonas", and "Jonas's invoice" is not how it is written. */
    private fun possessive(name: String): String =
        if (name.endsWith("s", ignoreCase = true)) "$name'" else "$name's"
}

internal object BengaliBriefWording : BriefWording {

    override val morningTitle = "আজ সকালের ব্রিফ"
    override val eveningTitle = "আজকের হিসাব"
    override val briefUnavailable = "এবার আইদা ব্রিফটি তৈরি করতে পারেননি।"
    override val briefChannelName = "ব্রিফ"
    override val briefChannelDescription = "সকালের ব্রিফ আর সন্ধ্যার হিসাব, চালু থাকলে।"
    override val reminderChannelName = "রিমাইন্ডার"
    override val reminderChannelDescription = "আইদা আপনার জন্য যে রিমাইন্ডারগুলো রেখেছে।"

    override val needsAttention = "যা দেখা দরকার"
    override val nothingNeedsAttention = "এখন দেখার মতো কিছু নেই।"
    override val readMyBrief = "ব্রিফ শোনান"
    override val quickCapture = "দ্রুত লিখুন"
    override val askMira = "আইদাকে বলুন"
    override val nextUp = "পরবর্তী"
    override val upcomingReminders = "রিমাইন্ডার"
    override val outstanding = "বকেয়া"
    override val dueSoon = "শিগগির দিতে হবে"
    override val recentPayments = "সাম্প্রতিক পেমেন্ট"
    override val projectDeadlines = "প্রজেক্টের সময়সীমা"
    override val followUps = "যোগাযোগ করা দরকার"
    override val noEventsToday = "আজ ক্যালেন্ডারে কিছু নেই।"
    override val calendarUnreadable = "ক্যালেন্ডার পড়া যাচ্ছে না।"
    override val allDay = "সারাদিন"
    override val paymentReceived = "পেমেন্ট"
    override val noContactRecorded = "যোগাযোগের কোনো রেকর্ড নেই।"
    override val overdueMark = "ওভারডিউ"
    override val morningBriefTitle = "সকালের ব্রিফ"
    override val eveningReviewTitle = "সন্ধ্যার হিসাব"
    override val calendarUnreadableSentence = "ক্যালেন্ডার দেখতে পাচ্ছি না।"
    override val quietDay = "আজ বিশেষ কিছু নেই।"
    override val quietEvening = "আজ নতুন কিছু লেখা হয়নি।"

    /** NIGHT shares EVENING's greeting: "শুভ রাত্রি" is what you say on the way out. */
    override fun greeting(part: DayPart, name: String?): String {
        val hello = when (part) {
            DayPart.MORNING -> "শুভ সকাল"
            DayPart.AFTERNOON -> "শুভ দুপুর"
            DayPart.EVENING, DayPart.NIGHT -> "শুভ সন্ধ্যা"
        }
        return if (name == null) "$hello।" else "$hello, $name।"
    }

    override fun taskSentence(dueToday: Int, overdue: Int, open: Int): String = when {
        overdue > 0 && dueToday > 0 ->
            "${digits(overdue)}টির সময় পেরিয়ে গেছে, আজ ${digits(dueToday)}টি আছে।"
        overdue > 0 -> "${digits(overdue)}টির সময় পেরিয়ে গেছে।"
        dueToday > 0 -> "আজ ${digits(dueToday)}টি কাজ আছে।"
        open > 0 -> "আজ নির্দিষ্ট কিছু নেই, ${digits(open)}টি বাকি আছে।"
        else -> "আজ নির্দিষ্ট কোনো কাজ নেই।"
    }

    override fun nextEventSentence(title: String, moment: String): String = "এরপর $title — $moment।"

    override fun reminderSentence(count: Int, title: String, moment: String): String =
        if (count <= 1) "একটি রিমাইন্ডার — $title, $moment।"
        else "${digits(count)}টি রিমাইন্ডার, পরেরটি — $title, $moment।"

    /**
     * "ওভারডিউ" sits after the amount as a predicate, so the figure never has to take a case
     * ending — a genitive glued onto "৪৫,০০০ USD" is how a foreign currency starts reading wrong.
     */
    override fun moneySentence(outstanding: Money?, overdue: Money?): String = when {
        outstanding != null && overdue != null ->
            "মোট বকেয়া ${money(outstanding)}, তার মধ্যে ${money(overdue)} ওভারডিউ।"
        outstanding != null -> "মোট বকেয়া ${money(outstanding)}।"
        overdue != null -> "ওভারডিউ ${money(overdue)}।"
        else -> ""
    }

    override fun needAttention(count: Int): String = "${digits(count)}টি বিষয় দেখা দরকার।"

    override fun moreNeedAttention(count: Int): String = "আরও ${digits(count)}টি বিষয় দেখা দরকার।"

    override fun completedSentence(done: Int, open: Int): String = when {
        done > 0 && open > 0 -> "${digits(done)}টি কাজ শেষ হয়েছে, ${digits(open)}টি বাকি।"
        done > 0 -> "${digits(done)}টি কাজ শেষ হয়েছে।"
        open > 0 -> "কোনো কাজ শেষ হয়নি, ${digits(open)}টি বাকি।"
        else -> "আজ কোনো কাজ শেষ হয়নি।"
    }

    override fun paymentsSentence(count: Int, total: Money?): String {
        val head = if (count == 1) "একটি পেমেন্ট এসেছে" else "${digits(count)}টি পেমেন্ট এসেছে"
        return if (total == null) "$head।" else "$head, ${money(total)}।"
    }

    override fun expensesSentence(count: Int, total: Money?): String {
        val head = if (count == 1) "একটি খরচ লেখা হয়েছে" else "${digits(count)}টি খরচ লেখা হয়েছে"
        return if (total == null) "$head।" else "$head, ${money(total)}।"
    }

    override fun invoicesRaisedSentence(count: Int, total: Money?): String {
        val head = if (count == 1) "একটি ইনভয়েস হয়েছে" else "${digits(count)}টি ইনভয়েস হয়েছে"
        return if (total == null) "$head।" else "$head, ${money(total)}।"
    }

    override fun tomorrowEventsSentence(count: Int, title: String?, moment: String?): String = when {
        count == 0 -> "আগামীকাল ক্যালেন্ডারে কিছু নেই।"
        title == null ->
            if (count == 1) "আগামীকাল একটি ইভেন্ট আছে।" else "আগামীকাল ${digits(count)}টি ইভেন্ট আছে।"
        count == 1 -> "আগামীকাল একটি ইভেন্ট — $title, $moment।"
        else -> "আগামীকাল ${digits(count)}টি ইভেন্ট, প্রথমটি — $title, $moment।"
    }

    override fun tomorrowPrioritySentence(count: Int, title: String): String =
        if (count <= 1) "আগামীকাল প্রথমে — $title।"
        else "আগামীকাল ${digits(count)}টি কাজ, প্রথমে — $title।"

    override fun salahNextSentence(prayerName: String, moment: String, tomorrow: Boolean): String =
        if (tomorrow) "আগামীকাল $prayerName — $moment।" else "পরবর্তী নামাজ $prayerName — $moment।"

    override fun salahReviewSentence(completed: Int, total: Int): String =
        if (completed >= total) "নামাজ: ${digits(total)} ওয়াক্তই পড়া হয়েছে বলে লেখা আছে।"
        else "নামাজ: ${digits(completed)}/${digits(total)} পড়া হয়েছে বলে লেখা আছে।"

    /**
     * The subject leads and the statement follows the dash. Bengali would normally inflect the
     * name — "আহমেদের ইনভয়েস" — but Abu's records hold names in both scripts, and there is no
     * ending that is right for "Ahmed" and "আহমেদ" and "Rayluxeo" at once.
     */
    override fun attentionTitle(
        kind: AttentionKind,
        subject: String,
        timing: AttentionTiming,
    ): String = when (kind) {
        AttentionKind.INVOICE_OVERDUE -> "$subject — ইনভয়েস ওভারডিউ।"

        AttentionKind.INVOICE_DUE_SOON -> when (timing) {
            is AttentionTiming.DaysAgo ->
                "$subject — ইনভয়েস ${dayCount(timing.days)} আগেই দেওয়ার কথা ছিল।"
            else -> "$subject — ইনভয়েস ${ahead(timing)} দিতে হবে।"
        }

        AttentionKind.PROJECT_DEADLINE -> when (timing) {
            is AttentionTiming.DaysAgo -> "$subject — সময়সীমা ${dayCount(timing.days)} আগেই পেরিয়েছে।"
            else -> "$subject — ${ahead(timing)} শেষ করতে হবে।"
        }

        AttentionKind.SUBSCRIPTION_RENEWING -> when (timing) {
            is AttentionTiming.DaysAgo ->
                "$subject — সাবস্ক্রিপশন ${dayCount(timing.days)} আগে রিনিউ হয়েছে।"
            else -> "$subject — সাবস্ক্রিপশন ${ahead(timing)} রিনিউ হবে।"
        }

        AttentionKind.TASK_OVERDUE -> "$subject — সময় পেরিয়ে গেছে।"

        AttentionKind.CLIENT_FOLLOW_UP -> when (timing) {
            is AttentionTiming.DaysAgo -> "$subject — ${dayCount(timing.days)} যোগাযোগ হয়নি।"
            else -> "$subject — যোগাযোগ করা দরকার।"
        }
    }

    /** Taka in Bengali numerals. A foreign currency keeps its code — Mira does not translate it. */
    override fun money(value: Money): String {
        val word = if (value.currency == Money.BDT) "টাকা" else value.currency.currencyCode
        return "${bengaliNumerals(moneyFigure(value))} $word"
    }

    override fun daysSinceContact(days: Long): String = "শেষ যোগাযোগ ${dayCount(days)} আগে"

    private fun ahead(timing: AttentionTiming): String = when (timing) {
        AttentionTiming.Today -> "আজ"
        AttentionTiming.Tomorrow -> "আগামীকাল"
        is AttentionTiming.InDays -> "${dayCount(timing.days)} পর"
        is AttentionTiming.DaysAgo -> "এখনই"
        is AttentionTiming.Unspecified -> when (timing.severity) {
            AttentionSeverity.NOW -> "এখনই"
            AttentionSeverity.SOON, AttentionSeverity.WATCH -> "শিগগিরই"
        }
    }

    private fun dayCount(value: Long): String = "${digits(value)} দিন"

    private fun digits(value: Int): String = bengaliNumerals(value.toString())

    private fun digits(value: Long): String = bengaliNumerals(value.toString())
}

internal fun briefWording(language: SpokenLanguage): BriefWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishBriefWording
    SpokenLanguage.BENGALI -> BengaliBriefWording
}
