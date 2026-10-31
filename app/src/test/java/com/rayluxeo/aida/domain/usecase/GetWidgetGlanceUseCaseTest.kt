package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.brief.AttentionItem
import com.rayluxeo.aida.domain.brief.BriefEvent
import com.rayluxeo.aida.domain.brief.BriefMoney
import com.rayluxeo.aida.domain.brief.BriefReminder
import com.rayluxeo.aida.domain.brief.DailyBriefEngine
import com.rayluxeo.aida.domain.brief.DailyBriefReport
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Money
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The hide-zero gates the home-screen glance depends on, proven without a launcher. A glance that
 * printed "৳0 outstanding" because there are no invoices, or showed overdue money while nothing is
 * overdue, would read as a second untrusted app — these are the lines that stop it.
 */
class GetWidgetGlanceUseCaseTest {

    private class FakeBriefEngine(private val result: AppResult<DailyBriefReport>) : DailyBriefEngine {
        override suspend fun today(): AppResult<DailyBriefReport> = result
        override suspend fun attention(): AppResult<List<AttentionItem>> = AppResult.Success(emptyList())
    }

    private suspend fun glanceOf(report: DailyBriefReport): WidgetGlance {
        val result = GetWidgetGlanceUseCase(FakeBriefEngine(AppResult.Success(report)))()
        return (result as AppResult.Success).value
    }

    @Test
    fun `no unpaid invoices hides the outstanding figure entirely`() = runTest {
        val glance = glanceOf(report(money = money(outstanding = null, invoiceCount = 0)))
        assertThat(glance.outstanding).isNull()
        assertThat(glance.overdue).isNull()
    }

    @Test
    fun `an outstanding figure is kept when invoices exist, with no overdue`() = runTest {
        val glance = glanceOf(
            report(money = money(outstanding = Money.of("45000"), invoiceCount = 2)),
        )
        assertThat(glance.outstanding).isEqualTo(Money.of("45000"))
        assertThat(glance.outstandingInvoiceCount).isEqualTo(2)
        assertThat(glance.overdue).isNull()
    }

    @Test
    fun `overdue money shows only when something is actually overdue`() = runTest {
        val glance = glanceOf(
            report(
                money = money(
                    outstanding = Money.of("45000"),
                    invoiceCount = 2,
                    overdue = Money.of("10000"),
                    overdueCount = 1,
                ),
            ),
        )
        assertThat(glance.overdue).isEqualTo(Money.of("10000"))
        assertThat(glance.overdueInvoiceCount).isEqualTo(1)
    }

    @Test
    fun `an overdue total with a zero count is not shown`() = runTest {
        // A stray overdueTotal without a count must not light the overdue line.
        val glance = glanceOf(
            report(money = money(outstanding = Money.of("45000"), invoiceCount = 2, overdue = Money.of("10000"), overdueCount = 0)),
        )
        assertThat(glance.overdue).isNull()
    }

    @Test
    fun `counts, next event and next reminder pass through`() = runTest {
        val event = BriefEvent(title = "Design Sync", startsAt = Instant.ofEpochSecond(1_800_000_000), endsAt = null, location = null)
        val first = BriefReminder(id = 1, title = "Call Ahmed", triggerAt = Instant.ofEpochSecond(1_800_000_500))
        val second = BriefReminder(id = 2, title = "Later", triggerAt = Instant.ofEpochSecond(1_800_100_000))
        val glance = glanceOf(
            report(
                openTaskCount = 5,
                dueTodayCount = 3,
                overdueCount = 1,
                nextEvent = event,
                reminders = listOf(first, second),
            ),
        )
        assertThat(glance.openTaskCount).isEqualTo(5)
        assertThat(glance.dueTodayCount).isEqualTo(3)
        assertThat(glance.overdueCount).isEqualTo(1)
        assertThat(glance.nextEvent).isEqualTo(event)
        // The first upcoming reminder is the one the glance shows.
        assertThat(glance.nextReminder).isEqualTo(first)
        assertThat(glance.isQuiet).isFalse()
    }

    @Test
    fun `an empty day is quiet`() = runTest {
        val glance = glanceOf(report())
        assertThat(glance.isQuiet).isTrue()
    }

    @Test
    fun `a failing brief propagates as a failure, not a blank glance`() = runTest {
        val result = GetWidgetGlanceUseCase(
            FakeBriefEngine(failure(MiraErrorCode.STORAGE, "database busy")),
        )()
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
    }

    private fun money(
        outstanding: Money? = null,
        invoiceCount: Int = 0,
        overdue: Money? = null,
        overdueCount: Int = 0,
    ) = BriefMoney(
        outstandingTotal = outstanding,
        overdueTotal = overdue,
        invoiceCount = invoiceCount,
        overdueCount = overdueCount,
        dueSoon = emptyList(),
        recentPayments = emptyList(),
    )

    private fun report(
        openTaskCount: Int = 0,
        dueTodayCount: Int = 0,
        overdueCount: Int = 0,
        nextEvent: BriefEvent? = null,
        reminders: List<BriefReminder> = emptyList(),
        money: BriefMoney = money(),
    ) = DailyBriefReport(
        date = LocalDate.of(2026, 10, 16),
        dayPart = DayPart.AFTERNOON,
        ownerName = "Abu",
        openTaskCount = openTaskCount,
        dueTodayCount = dueTodayCount,
        overdueCount = overdueCount,
        completedTodayCount = 0,
        dueToday = emptyList(),
        overdue = emptyList(),
        nextEvent = nextEvent,
        todaysEvents = emptyList(),
        calendarReadable = true,
        upcomingReminders = reminders,
        money = money,
        deadlines = emptyList(),
        followUps = emptyList(),
        attention = emptyList(),
    )
}
