package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.model.Contact
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Abu's zone. Fixed here so "today" never depends on where the test machine sits. */
internal val TEST_ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

internal val TEST_TODAY: LocalDate = LocalDate.of(2026, 9, 24)
internal val TEST_TOMORROW: LocalDate = TEST_TODAY.plusDays(1)
internal val TEST_YESTERDAY: LocalDate = TEST_TODAY.minusDays(1)

/** Far enough out that Mira has to name the date instead of saying "tomorrow". */
internal val TEST_LATER: LocalDate = LocalDate.of(2026, 10, 3)

/** In another calendar year, where the year has to be said out loud. */
internal val TEST_NEXT_YEAR: LocalDate = LocalDate.of(2027, 1, 5)

internal class FixedClock(
    private val instant: Instant,
    private val timeZone: ZoneId,
) : MiraClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = timeZone
}

/** A renderer whose "now" is 2026-09-24, 09:00 in Dhaka. */
internal fun testRenderer(): MiraResponseRenderer =
    MiraResponseRenderer(FixedClock(testInstant(TEST_TODAY, 9, 0), TEST_ZONE))

internal fun testInstant(date: LocalDate, hour: Int, minute: Int = 0): Instant =
    date.atTime(hour, minute).atZone(TEST_ZONE).toInstant()

internal fun testBrief(
    dayPart: DayPart = DayPart.MORNING,
    openTaskCount: Int = 0,
    dueTodayCount: Int = 0,
    overdueCount: Int = 0,
    completedTodayCount: Int = 0,
    nextReminderTitle: String? = null,
    nextReminderAt: Instant? = null,
): DailyBrief = DailyBrief(
    date = TEST_TODAY,
    dayPart = dayPart,
    openTaskCount = openTaskCount,
    dueTodayCount = dueTodayCount,
    overdueCount = overdueCount,
    completedTodayCount = completedTodayCount,
    nextReminderTitle = nextReminderTitle,
    nextReminderAt = nextReminderAt,
)

/** A contact as the phone hands one over: a name, a number to dial, and nothing Mira keeps. */
internal fun testContact(
    name: String,
    phoneNumber: String? = "01711-223344",
    systemId: Long = 1L,
): Contact = Contact(
    systemId = systemId,
    displayName = name,
    phoneNumber = phoneNumber,
    email = null,
)

/** Built from a string so that no amount in a test has ever been through a Double. */
internal fun taka(value: String): Money = Money.of(value)
