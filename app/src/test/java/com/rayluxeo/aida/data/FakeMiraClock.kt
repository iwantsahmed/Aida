package com.rayluxeo.aida.data

import com.rayluxeo.aida.core.common.MiraClock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A clock the tests own outright. Day windows and day parts are the whole point of several of
 * these tests, so every one of them pins both the instant and the zone — a test that passed only
 * between 05:00 and 11:00 local time would be worse than no test.
 */
class FakeMiraClock(
    localNow: LocalDateTime,
    private val zoneId: ZoneId = DHAKA,
) : MiraClock {

    private var instant: Instant = localNow.atZone(zoneId).toInstant()

    override fun now(): Instant = instant

    override fun zone(): ZoneId = zoneId

    fun moveTo(localNow: LocalDateTime) {
        instant = localNow.atZone(zoneId).toInstant()
    }

    companion object {
        /** Abu's zone. Offset +06:00 with no daylight saving, which keeps the arithmetic readable. */
        val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")
    }
}
