package com.rayluxeo.aida.data.moneyflow

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Subscription
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/**
 * The calendar arithmetic behind the subscription sweep, on its own and with no database.
 *
 * Month-end is the whole point. Thirty-day arithmetic moves a charge that falls on the 31st onto
 * the 2nd of March and it never comes back; calendar arithmetic clamps it to the last day of
 * February, which is what a card issuer does.
 */
class SubscriptionCycleTest {

    @Test
    fun `a monthly charge on the 31st of January lands on the last day of February`() {
        val subscription = subscription(
            cycle = SubscriptionCycle.MONTHLY,
            nextChargeOn = LocalDate.of(2026, 1, 31),
        )

        val next = subscription.nextChargeOnOrAfter(LocalDate.of(2026, 2, 5))

        assertThat(next).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun `a monthly charge on the 31st of January lands on the 29th in a leap year`() {
        val subscription = subscription(
            cycle = SubscriptionCycle.MONTHLY,
            nextChargeOn = LocalDate.of(2028, 1, 31),
        )

        assertThat(subscription.nextChargeOnOrAfter(LocalDate.of(2028, 2, 5)))
            .isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `a charge falling today is left where Abu can still see it`() {
        val today = LocalDate.of(2026, 3, 5)
        val subscription = subscription(nextChargeOn = today)

        assertThat(subscription.nextChargeOnOrAfter(today)).isEqualTo(today)
    }

    @Test
    fun `a charge months in the past is stepped forward until it is no longer overdue`() {
        val subscription = subscription(
            cycle = SubscriptionCycle.MONTHLY,
            nextChargeOn = LocalDate.of(2025, 11, 15),
        )

        assertThat(subscription.nextChargeOnOrAfter(LocalDate.of(2026, 3, 5)))
            .isEqualTo(LocalDate.of(2026, 3, 15))
    }

    @Test
    fun `a weekly charge moves by seven days`() {
        val subscription = subscription(
            cycle = SubscriptionCycle.WEEKLY,
            nextChargeOn = LocalDate.of(2026, 2, 26),
        )

        assertThat(subscription.nextChargeOnOrAfter(LocalDate.of(2026, 3, 5)))
            .isEqualTo(LocalDate.of(2026, 3, 5))
    }

    @Test
    fun `a quarterly charge moves by three calendar months`() {
        val subscription = subscription(
            cycle = SubscriptionCycle.QUARTERLY,
            nextChargeOn = LocalDate.of(2026, 1, 31),
        )

        assertThat(subscription.nextChargeOnOrAfter(LocalDate.of(2026, 3, 5)))
            .isEqualTo(LocalDate.of(2026, 4, 30))
    }

    @Test
    fun `a yearly charge on the 29th of February lands on the 28th in a common year`() {
        val subscription = subscription(
            cycle = SubscriptionCycle.YEARLY,
            nextChargeOn = LocalDate.of(2028, 2, 29),
        )

        assertThat(subscription.nextChargeOnOrAfter(LocalDate.of(2029, 1, 1)))
            .isEqualTo(LocalDate.of(2029, 2, 28))
    }

    @Test
    fun `a custom cycle moves by its own number of days`() {
        val subscription = subscription(
            cycle = SubscriptionCycle.CUSTOM_DAYS,
            cycleDays = 45,
            nextChargeOn = LocalDate.of(2026, 2, 10),
        )

        assertThat(subscription.nextChargeOnOrAfter(LocalDate.of(2026, 3, 5)))
            .isEqualTo(LocalDate.of(2026, 3, 27))
    }

    @Test
    fun `a custom cycle with no day count still moves forward rather than spinning`() {
        val subscription = subscription(
            cycle = SubscriptionCycle.CUSTOM_DAYS,
            cycleDays = null,
            nextChargeOn = LocalDate.of(2026, 3, 1),
        )

        assertThat(subscription.nextChargeOnOrAfter(LocalDate.of(2026, 3, 5)))
            .isEqualTo(LocalDate.of(2026, 3, 5))
    }

    private fun subscription(
        cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
        cycleDays: Int? = null,
        nextChargeOn: LocalDate,
    ): Subscription = Subscription(
        name = "Hosting",
        amount = Money.of("1200.00"),
        cycle = cycle,
        cycleDays = cycleDays,
        nextChargeOn = nextChargeOn,
        createdAt = EPOCH,
        updatedAt = EPOCH,
    )

    private companion object {
        val EPOCH: Instant = Instant.ofEpochMilli(0L)
    }
}
