package com.rayluxeo.aida.domain.brief

import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.salah.Prayer
import java.time.Instant
import java.time.LocalDate

/**
 * One thing Mira thinks Abu should look at, and why.
 *
 * Language-free by construction: [subject] is a name out of his own records, never a sentence.
 * `ui/response` turns a kind and a subject into words in his language — an [AttentionItem] that
 * carried English prose could not be read out in Bengali.
 *
 * Every item is DERIVED from a record that exists. Mira does not invent something to show when the
 * card would otherwise be empty; an empty card is a good day, and saying so is honest.
 */
data class AttentionItem(
    val kind: AttentionKind,
    /** The client, invoice number, project or subscription this is about, as Abu named it. */
    val subject: String,
    val severity: AttentionSeverity,
    val amount: Money? = null,
    /** The moment that makes this urgent — a due date, a renewal, a deadline. */
    val at: Instant? = null,
    /** Days until [at], negative when it has passed. Null when the item has no moment. */
    val daysAway: Long? = null,
    /** The record behind it, so a tap can open the thing itself rather than a search. */
    val reference: AttentionReference? = null,
)

enum class AttentionKind {
    INVOICE_OVERDUE,
    INVOICE_DUE_SOON,
    PROJECT_DEADLINE,
    SUBSCRIPTION_RENEWING,
    TASK_OVERDUE,
    CLIENT_FOLLOW_UP,
}

/**
 * How loudly the card should say it. [NOW] is for something already past its moment; [SOON] is a
 * warning; [WATCH] is a note. The card never uses colour alone to carry this — the words say it.
 */
enum class AttentionSeverity { NOW, SOON, WATCH }

/** What an item points at, so Home can open the record rather than guess at a search term. */
data class AttentionReference(val type: AttentionTarget, val id: Long)

enum class AttentionTarget { INVOICE, PROJECT, TASK, CLIENT, SUBSCRIPTION }

/** A calendar entry as the brief needs it: enough to say it, not enough to edit it. */
data class BriefEvent(
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val location: String?,
    val allDay: Boolean = false,
)

/** A reminder the brief mentions. */
data class BriefReminder(val id: Long, val title: String, val triggerAt: Instant)

/** A task the brief mentions, with only what is needed to name it and rank it. */
data class BriefTask(val id: Long, val title: String, val dueAt: Instant?, val isOverdue: Boolean)

/** A project deadline, which is either the project's own or its next unmet milestone. */
data class BriefDeadline(
    val projectId: Long,
    val projectName: String,
    val clientName: String?,
    val dueOn: LocalDate,
    val milestone: String? = null,
)

/**
 * A client Abu has not spoken to in a while.
 *
 * [lastContactAt] is null when there is no recorded contact at all, which is a different and
 * usually more interesting case than a contact that has gone stale.
 */
data class BriefFollowUp(
    val clientId: Long,
    val clientName: String,
    val lastContactAt: Instant?,
    val daysSince: Long?,
)

/**
 * What Mira knows about the money, as of now.
 *
 * Every figure is [Money] — `BigDecimal` underneath, never a `Double`, at any point including the
 * sums that produced it (`AGENTS.md` §8). A currency that cannot express an amount refuses it
 * rather than rounding.
 */
data class BriefMoney(
    val outstandingTotal: Money?,
    val overdueTotal: Money?,
    val invoiceCount: Int,
    val overdueCount: Int,
    val dueSoon: List<BriefInvoice>,
    val recentPayments: List<BriefPayment>,
)

data class BriefInvoice(
    val id: Long,
    val number: String,
    val clientName: String?,
    val amount: Money,
    val dueOn: LocalDate?,
    val isOverdue: Boolean,
)

data class BriefPayment(val id: Long, val clientName: String?, val amount: Money, val receivedAt: Instant)

/**
 * The next prayer, as the morning brief needs it: which one, when it falls, and whether it falls
 * tomorrow — so the line can say "Fajr is at 4:38 am tomorrow" after Isha without the renderer
 * re-deriving the day.
 *
 * Language-free by construction, like everything else the engines hand back: the prayer's name and
 * the moment are worded per language in `ui/response` (§4 rule 6). Present only when Abu has set a
 * prayer location; absent otherwise, which is how the brief stays silent about Salah until he has
 * turned it on.
 */
data class BriefSalah(
    val nextPrayer: Prayer,
    val nextTime: Instant,
    val isTomorrow: Boolean,
)

/**
 * Where today's obligatory prayers stand, for the evening review: how many of the five Abu has
 * recorded as prayed, out of [total]. The wording is never judgemental (his instruction) — a
 * prayer not logged is simply not counted here, never called missed or failed.
 */
data class ReviewSalah(
    val completedCount: Int,
    val total: Int,
)

/**
 * Everything Mira can say about today, built on the device from Abu's own records.
 *
 * Gemini is never involved: this is a query over Room and the calendar provider, so it works with
 * the radio off, which is the whole point (`AGENTS.md` §3).
 *
 * A section is EMPTY rather than absent when Mira looked and found nothing, and absent — null —
 * when she could not look at all. The calendar is the case that matters: [nextEvent] being null
 * because permission was withheld is a different fact from there being no meeting today, and
 * [calendarReadable] is what tells them apart.
 */
data class DailyBriefReport(
    val date: LocalDate,
    val dayPart: DayPart,
    val ownerName: String?,
    val openTaskCount: Int,
    val dueTodayCount: Int,
    val overdueCount: Int,
    val completedTodayCount: Int,
    val dueToday: List<BriefTask>,
    val overdue: List<BriefTask>,
    val nextEvent: BriefEvent?,
    val todaysEvents: List<BriefEvent>,
    val calendarReadable: Boolean,
    val upcomingReminders: List<BriefReminder>,
    val money: BriefMoney,
    val deadlines: List<BriefDeadline>,
    val followUps: List<BriefFollowUp>,
    val attention: List<AttentionItem>,
    /** The next prayer, when Abu has set a prayer location; null when Salah is not configured. */
    val salah: BriefSalah? = null,
) {
    /**
     * True when there is genuinely nothing to report, which is a normal day and not a fault.
     *
     * [calendarReadable] is part of the test on purpose. Without it a withheld calendar permission
     * makes [nextEvent] null, and a day with no tasks would then be declared quiet — Mira telling
     * Abu his day is clear when she was never allowed to look at it.
     */
    val isQuiet: Boolean
        get() = calendarReadable && dueTodayCount == 0 && overdueCount == 0 && nextEvent == null &&
            upcomingReminders.isEmpty() && attention.isEmpty()
}

/**
 * What the day came to.
 *
 * Deliberately not a scorecard. Mira reports what happened; she does not grade it, and nothing
 * here is phrased as a target Abu missed.
 */
data class EveningReviewReport(
    val date: LocalDate,
    val completedToday: List<BriefTask>,
    val stillOpen: List<BriefTask>,
    val paymentsReceived: List<BriefPayment>,
    val paymentsTotal: Money?,
    val expensesRecorded: Int,
    val expensesTotal: Money?,
    val invoicesCreated: List<BriefInvoice>,
    val tomorrowsEvents: List<BriefEvent>,
    val tomorrowsPriorities: List<BriefTask>,
    val calendarReadable: Boolean,
    /** Today's obligatory-prayer tally, when Salah is configured; null otherwise. */
    val salah: ReviewSalah? = null,
) {
    val isQuiet: Boolean
        get() = completedToday.isEmpty() && paymentsReceived.isEmpty() &&
            expensesRecorded == 0 && invoicesCreated.isEmpty()
}
