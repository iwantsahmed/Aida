package com.rayluxeo.aida.domain.brief

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.CalendarEvent
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.repository.CalendarRepository
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The account of a day that has already happened, assembled on the device from Abu's own records.
 *
 * It reports; it does not grade. There is no streak here, no score and no target he missed, and
 * [EveningReviewReport.isQuiet] exists so that a thin day can be said briefly rather than
 * apologetically.
 *
 * Three things decide whether this is right:
 *
 * - **The day is a whole local day.** The review is normally asked for at nine in the evening but
 *   it is ABOUT midnight-to-midnight in [MiraClock.zone], so a task finished at nine in the
 *   morning is in it. "Tomorrow" is the next LOCAL day, which is 23 or 25 hours long across a
 *   daylight-saving change and never simply "now plus twenty-four hours".
 * - **Money is [com.rayluxeo.aida.domain.model.Money] throughout**, `BigDecimal` underneath and
 *   never a `Double`. Both totals come from the repositories' own SQL sums rather than being
 *   added up here, so a list trimmed for display can never quietly produce a short total.
 * - **A failure is a value.** Anything Mira could not read comes back as an
 *   [AppResult.Failure] rather than as an empty section, with the single deliberate exception of
 *   the calendar: it is the phone's, not Mira's, and [EveningReviewReport.calendarReadable] is
 *   what tells "nothing tomorrow" apart from "could not look".
 *
 * Nothing here consults Gemini and nothing here needs the radio.
 */
class DefaultEveningReviewEngine @Inject constructor(
    private val tasks: TaskRepository,
    private val payments: PaymentRepository,
    private val expenses: ExpenseRepository,
    private val invoices: InvoiceRepository,
    private val clients: ClientRepository,
    private val calendar: CalendarRepository,
    private val clock: MiraClock,
    /**
     * Where today's prayers stand, so the review can offer Abu the count he still has to finish
     * logging. Both optional so the engine builds in a plain unit test without the Salah graph;
     * absent — or with no prayer location set — the review simply says nothing about Salah.
     */
    private val salahSettings: SalahSettingsRepository? = null,
    private val salahTracking: SalahTrackingRepository? = null,
) : EveningReviewEngine {

    override suspend fun review(date: LocalDate): AppResult<EveningReviewReport> {
        val zone = clock.zone()
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val tomorrowEnd = date.plusDays(2).atStartOfDay(zone).toInstant()

        // Day-independent filters only: DUE_TODAY and OVERDUE are resolved against the clock's own
        // today inside the repository, which is the wrong day whenever a past date is reviewed.
        val finished = tasks.search(null, TaskFilter.DONE).valueOr { return it }
            .filter { task -> task.completedAt?.within(dayStart, dayEnd) == true }
            .sortedBy { it.completedAt }
        val open = tasks.search(null, TaskFilter.OPEN).valueOr { return it }

        val received = payments.observeRecent(DAY_PAYMENT_LIMIT).first()
            .filter { it.receivedAt.within(dayStart, dayEnd) }
            .sortedBy { it.receivedAt }
        val paymentsTotal = received.singleCurrency { it.amount }?.let { currency ->
            payments.totalReceivedBetween(dayStart, dayEnd, currency).valueOr { return it }
        }

        val spent = expenses.listBetween(dayStart, dayEnd).valueOr { return it }
        val expensesTotal = spent.singleCurrency { it.amount }?.let { currency ->
            expenses.totalSpentBetween(dayStart, dayEnd, currency).valueOr { return it }
        }

        val raised = invoices.search(null).valueOr { return it }
            .filter { it.createdAt.within(dayStart, dayEnd) }
            .sortedBy { it.createdAt }

        val names = clientNames(
            raised.map { it.clientId }.toSet() + received.mapNotNull { it.clientId },
        )

        val tomorrow = if (calendar.canRead()) calendar.eventsBetween(dayEnd, tomorrowEnd) else null

        return AppResult.Success(
            EveningReviewReport(
                date = date,
                completedToday = finished.map { it.asFinished() },
                stillOpen = open.filter { it.wasDueBy(dayEnd) }
                    .mostUrgentFirst()
                    .map { it.asOutstanding(dayStart) },
                paymentsReceived = received.map { it.asBriefPayment(names) },
                paymentsTotal = paymentsTotal,
                expensesRecorded = spent.size,
                expensesTotal = expensesTotal,
                invoicesCreated = raised.map { it.asBriefInvoice(names, date) },
                tomorrowsEvents = (tomorrow?.getOrNull() ?: emptyList())
                    .sortedBy { it.startsAt }
                    .map { it.asBriefEvent() },
                tomorrowsPriorities = open.filter { it.isDueWithin(dayEnd, tomorrowEnd) }
                    .mostUrgentFirst()
                    .map { it.asOutstanding(dayStart) },
                calendarReadable = tomorrow is AppResult.Success,
                salah = salahReview(date),
            ),
        )
    }

    /**
     * Today's obligatory-prayer tally, when Abu has a prayer location set. Reads the log and counts
     * only what is recorded as prayed; nothing here is phrased as a target he missed (his
     * instruction, and the same reporting-not-grading rule the whole review follows).
     */
    private suspend fun salahReview(date: LocalDate): ReviewSalah? {
        val tracking = salahTracking ?: return null
        if (salahSettings?.current()?.isConfigured != true) return null
        val day = tracking.observeDay(date).first()
        return ReviewSalah(completedCount = day.completedCount, total = Prayer.OBLIGATORY.size)
    }

    /**
     * Names what can be named and leaves the rest unnamed.
     *
     * A client in the bin reads as NOT_FOUND here, and losing a label is not a reason to refuse
     * Abu the whole account of his day — the payment and the invoice behind it are still facts.
     */
    private suspend fun clientNames(ids: Set<Long>): Map<Long, String> =
        ids.mapNotNull { id -> clients.byId(id).getOrNull()?.let { id to it.name } }.toMap()

    /** Half-open, exactly as the repositories' own windows are: [from] counts, [to] does not. */
    private fun Instant.within(from: Instant, to: Instant): Boolean = this >= from && this < to

    private fun Task.wasDueBy(moment: Instant): Boolean = dueAt?.let { it < moment } == true

    private fun Task.isDueWithin(from: Instant, to: Instant): Boolean =
        dueAt?.within(from, to) == true

    /** Soonest first; a shared moment is broken by priority, and then by id so the order is stable. */
    private fun List<Task>.mostUrgentFirst(): List<Task> =
        sortedWith(compareBy({ it.dueAt }, { it.priority.urgency() }, { it.id }))

    private fun TaskPriority.urgency(): Int = when (this) {
        TaskPriority.HIGH -> 0
        TaskPriority.NORMAL -> 1
        TaskPriority.LOW -> 2
    }

    /** A task that is finished is not overdue, whenever it happened to have been due. */
    private fun Task.asFinished(): BriefTask =
        BriefTask(id = id, title = title, dueAt = dueAt, isOverdue = false)

    private fun Task.asOutstanding(dayStart: Instant): BriefTask =
        BriefTask(id = id, title = title, dueAt = dueAt, isOverdue = wasDueBy(dayStart))

    private fun Payment.asBriefPayment(names: Map<Long, String>): BriefPayment = BriefPayment(
        id = id,
        clientName = clientId?.let(names::get),
        amount = amount,
        receivedAt = receivedAt,
    )

    /** The figure is the bill Abu raised, not what is left on it: nothing has been paid yet today. */
    private fun Invoice.asBriefInvoice(names: Map<Long, String>, date: LocalDate): BriefInvoice =
        BriefInvoice(
            id = id,
            number = number,
            clientName = names[clientId],
            amount = total,
            dueOn = dueOn,
            isOverdue = isStillOwed && dueOn < date,
        )

    /** A draft was never sent, so it cannot be late; a void one is not owed at all. */
    private val Invoice.isStillOwed: Boolean
        get() = when (status) {
            InvoiceStatus.DRAFT, InvoiceStatus.READY, InvoiceStatus.PAID, InvoiceStatus.VOID -> false
            InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE -> true
        }

    /** [CalendarEvent] carries no all-day flag, so [BriefEvent.allDay] keeps its default here. */
    private fun CalendarEvent.asBriefEvent(): BriefEvent =
        BriefEvent(title = title, startsAt = startsAt, endsAt = endsAt, location = location)

    /**
     * The one currency the day's money moved in, or null where there was none or more than one.
     *
     * Mira holds no exchange rates with the radio off, so a day that mixes currencies has no
     * single honest total and the report carries none. The list is still there, each amount in
     * the currency it arrived in, and the renderer says what it can.
     */
    private fun <T> List<T>.singleCurrency(amount: (T) -> Money): Currency? =
        mapTo(mutableSetOf()) { amount(it).currency }.singleOrNull()

    private inline fun <T> AppResult<T>.valueOr(onFailure: (AppResult.Failure) -> Nothing): T =
        when (this) {
            is AppResult.Success -> value
            is AppResult.Failure -> onFailure(this)
        }

    private companion object {
        /**
         * How far back the payments LIST is read before it is narrowed to the day. The totals come
         * from the repository's own sum over the same window, so this bounds what is shown and
         * never what is counted; it is far past a day's worth of payments for one freelancer.
         */
        const val DAY_PAYMENT_LIMIT = 200
    }
}
