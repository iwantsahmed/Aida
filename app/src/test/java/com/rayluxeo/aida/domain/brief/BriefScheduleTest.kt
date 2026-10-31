package com.rayluxeo.aida.domain.brief

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.data.scheduler.BriefOccurrence
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

/**
 * The one part of the brief scheduler that can be wrong in a way no device test would catch:
 * when the next wake-up is due.
 *
 * A brief is armed for a wall-clock time, one day at a time, and re-armed by the receiver at the
 * instant it fires. That makes two cases load-bearing and both are here — an occurrence that is
 * exactly now must roll to tomorrow rather than arm itself again, and a day that is 23 or 25 hours
 * long must still put the brief at the hour Abu chose.
 *
 * Dhaka has no daylight saving, which is why the boundary cases use New York: Abu's own zone would
 * never exercise them, and the arithmetic must not depend on that.
 */
class BriefScheduleTest {

    // ------------------------------------------------------------------ the ordinary day

    @Test
    fun `an hour still to come today is today`() {
        val now = FixedMiraClock.dhaka(2026, 3, 10, 6, 30)

        val next = BriefOccurrence.next(EIGHT_AM, FixedMiraClock.DHAKA, now)

        assertThat(next).isEqualTo(FixedMiraClock.dhaka(2026, 3, 10, 8, 0))
    }

    @Test
    fun `an hour that has passed today is tomorrow`() {
        val now = FixedMiraClock.dhaka(2026, 3, 10, 9, 15)

        val next = BriefOccurrence.next(EIGHT_AM, FixedMiraClock.DHAKA, now)

        assertThat(next).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 8, 0))
    }

    @Test
    fun `one minute short of the hour is still today`() {
        val now = FixedMiraClock.dhaka(2026, 3, 10, 7, 59)

        val next = BriefOccurrence.next(EIGHT_AM, FixedMiraClock.DHAKA, now)

        assertThat(next).isEqualTo(FixedMiraClock.dhaka(2026, 3, 10, 8, 0))
    }

    /**
     * The receiver re-arms at the moment the alarm fires, so "now" is exactly the scheduled time.
     * An occurrence that merely equalled now would arm the same instant again and fire in a loop.
     */
    @Test
    fun `exactly now rolls to tomorrow rather than arming itself again`() {
        val now = FixedMiraClock.dhaka(2026, 3, 10, 8, 0)

        val next = BriefOccurrence.next(EIGHT_AM, FixedMiraClock.DHAKA, now)

        assertThat(next).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 8, 0))
    }

    @Test
    fun `midnight is a time like any other`() {
        val now = FixedMiraClock.dhaka(2026, 3, 10, 23, 30)

        val next = BriefOccurrence.next(LocalTime.MIDNIGHT, FixedMiraClock.DHAKA, now)

        assertThat(next).isEqualTo(FixedMiraClock.dhaka(2026, 3, 11, 0, 0))
    }

    @Test
    fun `the evening default is armed for the same evening when the day is young`() {
        val now = FixedMiraClock.dhaka(2026, 3, 10, 10, 0)

        val next = BriefOccurrence.next(
            BriefSchedule.DEFAULT_EVENING,
            FixedMiraClock.DHAKA,
            now,
        )

        assertThat(next).isEqualTo(FixedMiraClock.dhaka(2026, 3, 10, 21, 0))
    }

    // ------------------------------------------------------------------ daylight saving

    /**
     * 02:30 does not exist on 8 March 2026 in New York — the clocks go straight from 02:00 to
     * 03:00. The occurrence moves to the far side of the gap instead of being invented.
     */
    @Test
    fun `a time inside the spring-forward gap moves to the far side of it`() {
        val now = Instant.parse("2026-03-08T06:00:00Z") // 01:00 EST, an hour before the gap

        val next = BriefOccurrence.next(LocalTime.of(2, 30), NEW_YORK, now)

        assertThat(next).isEqualTo(Instant.parse("2026-03-08T07:30:00Z")) // 03:30 EDT
    }

    @Test
    fun `a gap time already behind us goes to tomorrow, when it exists again`() {
        val now = Instant.parse("2026-03-08T08:00:00Z") // 04:00 EDT, the gap is past

        val next = BriefOccurrence.next(LocalTime.of(2, 30), NEW_YORK, now)

        assertThat(next).isEqualTo(Instant.parse("2026-03-09T06:30:00Z")) // 02:30 EDT
    }

    /** 01:30 happens twice on 1 November 2026. The earlier of the two is the one Mira takes. */
    @Test
    fun `a time inside the autumn overlap takes the first of its two readings`() {
        val now = Instant.parse("2026-11-01T04:30:00Z") // 00:30 EDT

        val next = BriefOccurrence.next(LocalTime.of(1, 30), NEW_YORK, now)

        assertThat(next).isEqualTo(Instant.parse("2026-11-01T05:30:00Z")) // 01:30 EDT, not EST
    }

    /**
     * The day the clocks go back is 25 hours long. Adding a fixed 24 hours to the alarm that just
     * fired would put the brief at seven in the morning; the date arithmetic keeps it at eight.
     */
    @Test
    fun `the hour Abu chose survives a twenty-five hour day`() {
        val firedAt = Instant.parse("2026-10-31T12:00:00Z") // 08:00 EDT

        val next = BriefOccurrence.next(EIGHT_AM, NEW_YORK, firedAt)

        assertThat(next).isEqualTo(Instant.parse("2026-11-01T13:00:00Z")) // 08:00 EST
        assertThat(next.toEpochMilli() - firedAt.toEpochMilli()).isEqualTo(TWENTY_FIVE_HOURS_MILLIS)
    }

    /** And the 23-hour day the other way, which would otherwise put the brief at nine. */
    @Test
    fun `the hour Abu chose survives a twenty-three hour day`() {
        val firedAt = Instant.parse("2026-03-07T13:00:00Z") // 08:00 EST

        val next = BriefOccurrence.next(EIGHT_AM, NEW_YORK, firedAt)

        assertThat(next).isEqualTo(Instant.parse("2026-03-08T12:00:00Z")) // 08:00 EDT
        assertThat(next.toEpochMilli() - firedAt.toEpochMilli()).isEqualTo(TWENTY_THREE_HOURS_MILLIS)
    }

    // ------------------------------------------------------------------ the schedule itself

    /**
     * Guards the promise in [BriefSchedule]'s KDoc: the first notification Mira sends unasked is
     * one Abu switched on himself. A default flipped to true here would be a silent regression.
     */
    @Test
    fun `both briefs are off until Abu turns them on`() {
        val schedule = BriefSchedule.DEFAULT

        assertThat(schedule.morningEnabled).isFalse()
        assertThat(schedule.eveningEnabled).isFalse()
        assertThat(schedule.morningAt).isEqualTo(LocalTime.of(8, 0))
        assertThat(schedule.eveningAt).isEqualTo(LocalTime.of(21, 0))
    }

    private companion object {
        val EIGHT_AM: LocalTime = LocalTime.of(8, 0)
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        const val TWENTY_FIVE_HOURS_MILLIS = 25L * 60 * 60 * 1000
        const val TWENTY_THREE_HOURS_MILLIS = 23L * 60 * 60 * 1000
    }
}
