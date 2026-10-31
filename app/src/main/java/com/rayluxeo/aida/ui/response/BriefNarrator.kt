package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.AttentionItem
import com.rayluxeo.aida.domain.brief.BriefDeadline
import com.rayluxeo.aida.domain.brief.BriefEvent
import com.rayluxeo.aida.domain.brief.BriefFollowUp
import com.rayluxeo.aida.domain.brief.BriefInvoice
import com.rayluxeo.aida.domain.brief.BriefPayment
import com.rayluxeo.aida.domain.brief.BriefReminder
import com.rayluxeo.aida.domain.brief.BriefSalah
import com.rayluxeo.aida.domain.brief.DailyBriefReport
import com.rayluxeo.aida.domain.brief.EveningReviewReport
import com.rayluxeo.aida.domain.brief.ReviewSalah
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Turns a brief into words, in the language Abu is working in.
 *
 * The engines hand back counts, amounts and names — nothing Abu could read. Three different
 * renderings come out of the same report and they are genuinely different jobs: a spoken
 * paragraph he should not want to interrupt, a notification title and one line, and the short
 * fragments Home lays out around its own headings.
 *
 * Dates and times are worded by [TaskTimeFormatter] rather than here, for the reason that file
 * exists: Home, Tasks and the brief must not each grow their own way of saying "tomorrow".
 */
class BriefNarrator @Inject constructor(
    private val clock: MiraClock,
    private val times: TaskTimeFormatter,
) {

    /** The whole brief as one spoken paragraph, for "Read my brief". */
    fun speakDailyBrief(report: DailyBriefReport, language: SpokenLanguage): String {
        val wording = briefWording(language)
        val greeting = wording.greeting(report.dayPart, shortName(report.ownerName))
        // The Salah line is appended after the body, so it is said even on an otherwise quiet day —
        // the next prayer is a fact worth one line regardless of the rest of the morning.
        val salah = salahSentence(report.salah, wording, language)
        return sentences(listOf(greeting) + dailyBriefBody(report, wording, language) + listOfNotNull(salah))
    }

    /**
     * The morning notification. Shorter than the spoken brief because a notification Abu has to
     * expand to understand is one he stops reading.
     */
    fun morningNotification(report: DailyBriefReport, language: SpokenLanguage): BriefNotification {
        val wording = briefWording(language)
        val next = report.nextEvent
        val line = buildList {
            add(wording.taskSentence(report.dueTodayCount, report.overdueCount, report.openTaskCount))
            when {
                report.attention.isNotEmpty() -> add(wording.needAttention(report.attention.size))
                !report.calendarReadable -> add(wording.calendarUnreadableSentence)
                next != null -> add(nextEventSentence(next, wording, language))
            }
            salahSentence(report.salah, wording, language)?.let { add(it) }
        }
        return BriefNotification(title = wording.morningBriefTitle, line = sentences(line))
    }

    /** The day's account, spoken. Facts only — Mira reports the day, she does not grade it. */
    fun speakEveningReview(report: EveningReviewReport, language: SpokenLanguage): String {
        val wording = briefWording(language)
        val parts = buildList {
            if (report.isQuiet && report.stillOpen.isEmpty()) {
                add(wording.quietEvening)
            } else {
                add(wording.completedSentence(report.completedToday.size, report.stillOpen.size))
                if (report.paymentsReceived.isNotEmpty()) {
                    add(wording.paymentsSentence(report.paymentsReceived.size, report.paymentsTotal.ifCounts()))
                }
                if (report.expensesRecorded > 0) {
                    add(wording.expensesSentence(report.expensesRecorded, report.expensesTotal.ifCounts()))
                }
                if (report.invoicesCreated.isNotEmpty()) {
                    add(wording.invoicesRaisedSentence(report.invoicesCreated.size, total(report.invoicesCreated)))
                }
            }
            salahReviewSentence(report.salah, wording)?.let { add(it) }
            add(tomorrowSentence(report, wording, language))
            report.tomorrowsPriorities.firstOrNull()?.let {
                add(wording.tomorrowPrioritySentence(report.tomorrowsPriorities.size, it.title.trim().forSpeech()))
            }
        }
        return sentences(parts)
    }

    fun eveningNotification(report: EveningReviewReport, language: SpokenLanguage): BriefNotification {
        val wording = briefWording(language)
        val line = buildList {
            add(wording.completedSentence(report.completedToday.size, report.stillOpen.size))
            salahReviewSentence(report.salah, wording)?.let { add(it) }
            if (report.calendarReadable) {
                add(wording.tomorrowEventsSentence(report.tomorrowsEvents.size, title = null, moment = null))
            } else {
                add(wording.calendarUnreadableSentence)
            }
        }
        return BriefNotification(title = wording.eveningReviewTitle, line = sentences(line))
    }

    /**
     * One Needs Attention row. The title states the fact — overdue, due tomorrow, renewing — so
     * the card never depends on a colour to say how urgent it is.
     */
    fun attentionLine(item: AttentionItem, language: SpokenLanguage): BriefLine {
        val wording = briefWording(language)
        return BriefLine(
            title = wording.attentionTitle(item.kind, item.subject.trim(), timingOf(item)),
            detail = detail(
                item.amount?.let(wording::money),
                item.at?.let { times.dateLabel(localDate(it), language) },
            ),
        )
    }

    fun eventLine(event: BriefEvent, language: SpokenLanguage): BriefLine {
        val wording = briefWording(language)
        val moment = if (event.allDay) wording.allDay else times.momentLabel(event.startsAt, language)
        return BriefLine(title = event.title.trim(), detail = detail(moment, event.location?.trim()))
    }

    fun reminderLine(reminder: BriefReminder, language: SpokenLanguage): BriefLine = BriefLine(
        title = reminder.title.trim(),
        detail = times.momentLabel(reminder.triggerAt, language),
    )

    /**
     * An invoice row. The number stays in Latin digits in both languages: it is a name Abu reads
     * back to a client, not a count, so it never goes through the Bengali numerals.
     */
    fun invoiceLine(invoice: BriefInvoice, language: SpokenLanguage): BriefLine {
        val wording = briefWording(language)
        val client = invoice.clientName?.trim()?.takeIf { it.isNotEmpty() }
        return BriefLine(
            title = client ?: invoice.number.trim(),
            detail = detail(
                wording.money(invoice.amount),
                if (client != null) invoice.number.trim() else null,
                if (invoice.isOverdue) wording.overdueMark else null,
                invoice.dueOn?.let { times.dateLabel(it, language) },
            ),
        )
    }

    fun paymentLine(payment: BriefPayment, language: SpokenLanguage): BriefLine {
        val wording = briefWording(language)
        return BriefLine(
            title = payment.clientName?.trim()?.takeIf { it.isNotEmpty() } ?: wording.paymentReceived,
            detail = detail(wording.money(payment.amount), times.momentLabel(payment.receivedAt, language)),
        )
    }

    fun deadlineLine(deadline: BriefDeadline, language: SpokenLanguage): BriefLine {
        val wording = briefWording(language)
        val milestone = deadline.milestone?.trim()?.takeIf { it.isNotEmpty() }
        val project = deadline.projectName.trim()
        return BriefLine(
            title = if (milestone == null) project else "$project — $milestone",
            detail = detail(
                if (deadline.dueOn.isBefore(clock.today())) wording.overdueMark else null,
                times.dateLabel(deadline.dueOn, language),
                deadline.clientName?.trim(),
            ),
        )
    }

    /**
     * A client with no recorded contact at all is a different row from one Abu spoke to in
     * March, and saying "0 days" for the first would be a lie.
     */
    fun followUpLine(followUp: BriefFollowUp, language: SpokenLanguage): BriefLine {
        val wording = briefWording(language)
        return BriefLine(
            title = followUp.clientName.trim(),
            detail = followUp.daysSince?.let(wording::daysSinceContact) ?: wording.noContactRecorded,
        )
    }

    /** The outstanding total as Home prints it beside its own heading. */
    fun moneyPhrase(value: Money, language: SpokenLanguage): String = briefWording(language).money(value)

    private fun dailyBriefBody(
        report: DailyBriefReport,
        wording: BriefWording,
        language: SpokenLanguage,
    ): List<String> {
        val outstanding = report.money.outstandingTotal.ifCounts()
        val overdue = report.money.overdueTotal.ifCounts()
        // A day looks quiet partly because `nextEvent` is null, which is also what a calendar
        // Mira could not read looks like. The short answer is only honest when she could read it.
        if (report.isQuiet && report.calendarReadable && outstanding == null && overdue == null) {
            return listOf(wording.quietDay)
        }
        return buildList {
            add(wording.taskSentence(report.dueTodayCount, report.overdueCount, report.openTaskCount))
            add(calendarSentence(report, wording, language))
            report.upcomingReminders.firstOrNull()?.let {
                add(
                    wording.reminderSentence(
                        count = report.upcomingReminders.size,
                        title = it.title.trim().forSpeech(),
                        moment = spokenMoment(it.triggerAt, language),
                    ),
                )
            }
            if (outstanding != null || overdue != null) add(wording.moneySentence(outstanding, overdue))
            addAll(attentionSentences(report.attention, wording))
        }
    }

    /**
     * Silence about the calendar is only honest when Mira could actually read it. When she could
     * not, she says so — Home shows the same distinction, and a brief that skipped it would let
     * a withheld permission pass for an empty day.
     */
    private fun calendarSentence(
        report: DailyBriefReport,
        wording: BriefWording,
        language: SpokenLanguage,
    ): String {
        if (!report.calendarReadable) return wording.calendarUnreadableSentence
        val next = report.nextEvent ?: return ""
        return nextEventSentence(next, wording, language)
    }

    private fun nextEventSentence(
        event: BriefEvent,
        wording: BriefWording,
        language: SpokenLanguage,
    ): String = wording.nextEventSentence(
        title = event.title.trim().forSpeech(),
        moment = if (event.allDay) wording.allDay else spokenMoment(event.startsAt, language),
    )

    private fun attentionSentences(items: List<AttentionItem>, wording: BriefWording): List<String> {
        val spoken = items.take(SPOKEN_ATTENTION_COUNT).map {
            wording.attentionTitle(it.kind, it.subject.trim().forSpeech(), timingOf(it))
        }
        val rest = items.size - spoken.size
        return if (rest > 0) spoken + wording.moreNeedAttention(rest) else spoken
    }

    private fun tomorrowSentence(
        report: EveningReviewReport,
        wording: BriefWording,
        language: SpokenLanguage,
    ): String {
        if (!report.calendarReadable) return wording.calendarUnreadableSentence
        val first = report.tomorrowsEvents.firstOrNull()
        return wording.tomorrowEventsSentence(
            count = report.tomorrowsEvents.size,
            title = first?.title?.trim()?.forSpeech(),
            moment = first?.let { event ->
                if (event.allDay) wording.allDay
                else times.timeLabel(localTime(event.startsAt), language)
            },
        )
    }

    /**
     * The sentence around it already says which day, so a moment today is spoken as a bare time.
     * "Next, the call — Today, 3 pm" says today twice and reads like a form.
     */
    private fun spokenMoment(instant: Instant, language: SpokenLanguage): String =
        if (localDate(instant) == clock.today()) times.timeLabel(localTime(instant), language)
        else times.momentLabel(instant, language)

    private fun timingOf(item: AttentionItem): AttentionTiming {
        val days = item.daysAway ?: return AttentionTiming.Unspecified(item.severity)
        return when {
            days == 0L -> AttentionTiming.Today
            days == 1L -> AttentionTiming.Tomorrow
            days > 1L -> AttentionTiming.InDays(days)
            else -> AttentionTiming.DaysAgo(-days)
        }
    }

    /**
     * Invoices raised today are summed here because the engine reports them one by one. Mixed
     * currencies have no total, and [Money] refuses to add them rather than inventing a rate —
     * so the sentence loses its figure and keeps its count, which is still true.
     */
    private fun total(invoices: List<BriefInvoice>): Money? {
        val currency = invoices.firstOrNull()?.amount?.currency ?: return null
        if (invoices.any { it.amount.currency != currency }) return null
        return invoices.map { it.amount }.reduce(Money::plus).ifCounts()
    }

    /**
     * The one Salah line, or nothing when Abu has not set a prayer location. The prayer's time is
     * spoken bare — the sentence itself says which prayer and whether it is tomorrow — so it reads
     * "Fajr is at 4:38 am tomorrow" rather than repeating the day twice.
     */
    private fun salahSentence(salah: BriefSalah?, wording: BriefWording, language: SpokenLanguage): String? {
        val next = salah ?: return null
        return wording.salahNextSentence(
            prayerName = salahPrayerName(next.nextPrayer, language),
            moment = times.timeLabel(localTime(next.nextTime), language),
            tomorrow = next.isTomorrow,
        )
    }

    private fun salahReviewSentence(salah: ReviewSalah?, wording: BriefWording): String? =
        salah?.let { wording.salahReviewSentence(it.completedCount, it.total) }

    /** Abu's profile may hold his full name; Mira greets him by the first part of it. */
    private fun shortName(ownerName: String?): String? =
        ownerName?.trim()?.takeIf { it.isNotEmpty() }?.substringBefore(' ')

    private fun localDate(instant: Instant) = LocalDateTime.ofInstant(instant, clock.zone()).toLocalDate()

    private fun localTime(instant: Instant) = LocalDateTime.ofInstant(instant, clock.zone()).toLocalTime()

    private fun sentences(parts: List<String>): String =
        parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    private fun detail(vararg parts: String?): String? = parts
        .filterNotNull()
        .filter { it.isNotBlank() }
        .joinToString(DETAIL_SEPARATOR)
        .takeIf { it.isNotEmpty() }

    /** A zero balance is not news, and announcing one makes a quiet day sound busy. */
    private fun Money?.ifCounts(): Money? = this?.takeIf { it.amount.signum() != 0 }

    private companion object {
        /** Beyond two, a spoken list of worries stops being a brief and becomes a recital. */
        const val SPOKEN_ATTENTION_COUNT = 2

        const val DETAIL_SEPARATOR = " · "
    }
}

/**
 * A notification has room for a title and one line, and Mira uses both rather than letting
 * Android truncate a paragraph mid-word.
 */
data class BriefNotification(val title: String, val line: String)

/**
 * One row as Home shows it: what the row is about, and the figure or moment that qualifies it.
 * The detail is null when there is nothing true to put there — an invoice with no due date, a
 * client Mira has never recorded a conversation with.
 */
data class BriefLine(val title: String, val detail: String?)
