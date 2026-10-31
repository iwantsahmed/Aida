package com.rayluxeo.aida.domain.salah

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Where Abu is in the day's prayers right now: the window he is in, the one coming up, when it
 * falls and how long is left. [current] is which prayer's window is open — before Fajr that is
 * still Isha, because the night belongs to it.
 */
data class NextPrayerInfo(
    val current: Prayer,
    val next: Prayer,
    val nextTime: ZonedDateTime,
    val remaining: Duration,
)

/**
 * The pure timing logic the home card, the hero and the voice answers all read from. It takes the
 * moment to reason about rather than reading a clock, so a countdown and a midnight roll-over are
 * both testable (§4.4).
 */
object SalahClock {

    /**
     * The next prayer at [now], given today's times and tomorrow's Fajr (needed because after Isha
     * the next prayer is the following dawn). [tomorrowFajr] keeps the answer correct across
     * midnight without the caller needing a second full day of times.
     */
    fun nextPrayer(now: Instant, today: PrayerTimes, tomorrowFajr: ZonedDateTime): NextPrayerInfo {
        val events = today.obligatory + (Prayer.FAJR to tomorrowFajr)
        for (i in events.indices) {
            val (prayer, time) = events[i]
            if (time.toInstant().isAfter(now)) {
                val current = if (i == 0) Prayer.ISHA else events[i - 1].first
                return NextPrayerInfo(current, prayer, time, remainingUntil(now, time))
            }
        }
        // now is past tomorrow's Fajr — the caller gave stale data; fall back to that last event.
        val (prayer, time) = events.last()
        return NextPrayerInfo(Prayer.ISHA, prayer, time, remainingUntil(now, time))
    }

    /** Time from [now] until [time]; never negative, so a just-passed prayer reads as zero, not as "overdue". */
    fun remainingUntil(now: Instant, time: ZonedDateTime): Duration {
        val d = Duration.between(now, time.toInstant())
        return if (d.isNegative) Duration.ZERO else d
    }
}
