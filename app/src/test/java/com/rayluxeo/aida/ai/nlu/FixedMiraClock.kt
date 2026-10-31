package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.core.common.MiraClock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A clock that never moves, so "tomorrow at 10" is the same instant on every run.
 *
 * Abu's zone is fixed to Dhaka rather than the machine's default: the tests assert exact UTC
 * instants, and a build agent in another zone would otherwise disagree with his phone.
 */
class FixedMiraClock(
    private val instant: Instant = DEFAULT_NOW,
    private val zone: ZoneId = DHAKA,
) : MiraClock {

    override fun now(): Instant = instant

    override fun zone(): ZoneId = zone

    companion object {
        val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")

        /** Tuesday 10 March 2026, 10:00 in Dhaka. */
        val DEFAULT_NOW: Instant = Instant.parse("2026-03-10T04:00:00Z")

        /** Builds an instant from a Dhaka wall-clock reading, which is how the tests think. */
        fun dhaka(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
            LocalDateTime.of(year, month, day, hour, minute).atZone(DHAKA).toInstant()

        fun at(hour: Int, minute: Int = 0): FixedMiraClock =
            FixedMiraClock(dhaka(2026, 3, 10, hour, minute))
    }
}
