package com.rayluxeo.aida.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A recurring charge Abu is signed up to.
 *
 * [nextChargeOn] is a [LocalDate] rather than an instant: a subscription charges on a day, not
 * at a moment, and storing a moment would move the day whenever the phone crossed a timezone.
 */
data class Subscription(
    val id: Long = 0L,
    val name: String,
    val vendor: String? = null,
    val amount: Money,
    val cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
    /** Read only when [cycle] is [SubscriptionCycle.CUSTOM_DAYS]. */
    val cycleDays: Int? = null,
    val nextChargeOn: LocalDate,
    val isActive: Boolean = true,
    val reminderId: Long? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {

    /**
     * The charge after [date], worked out with calendar arithmetic rather than a day count.
     *
     * A month is not thirty days. Adding 30 to the 31st of January lands on the 2nd of March and
     * silently moves the charge off its day for good; [LocalDate.plusMonths] clamps to the last
     * day of a short month instead, which is what a card issuer actually does. The same reasoning
     * covers quarters and a yearly charge falling on the 29th of February.
     *
     * A [SubscriptionCycle.CUSTOM_DAYS] row with a missing or non-positive `cycleDays` advances
     * by a single day rather than throwing or standing still: a bad row must not take down the
     * whole sweep, and it must not spin forever either.
     */
    fun chargeAfter(date: LocalDate): LocalDate = when (cycle) {
        SubscriptionCycle.WEEKLY -> date.plusWeeks(1)
        SubscriptionCycle.MONTHLY -> date.plusMonths(1)
        SubscriptionCycle.QUARTERLY -> date.plusMonths(3)
        SubscriptionCycle.YEARLY -> date.plusYears(1)
        SubscriptionCycle.CUSTOM_DAYS ->
            date.plusDays((cycleDays ?: 1).coerceAtLeast(1).toLong())
    }

    /**
     * Where the charge date lands once every date that has already gone by has been stepped over.
     *
     * A charge dated [today] is due today and stays put — advancing it the moment the day opened
     * would mean Abu was never told about it. Only a date strictly in the past moves on.
     *
     * One honest limitation: because the row keeps no anchor day, a monthly subscription that has
     * been clamped to the 28th by a February stays on the 28th afterwards rather than climbing
     * back to the 31st. Restoring it would need an anchor column the schema does not have, and
     * charging Abu's card a few days early is the worse of the two errors.
     */
    fun nextChargeOnOrAfter(today: LocalDate): LocalDate {
        var date = nextChargeOn
        while (date.isBefore(today)) {
            date = chargeAfter(date)
        }
        return date
    }
}
