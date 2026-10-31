package com.rayluxeo.aida.domain.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When an alarm set for a time of day next goes off.
 *
 * It is a pure function in its own file because it is the one piece of the alarm clock that can be
 * wrong without anything crashing: an alarm armed a day late looks exactly like an alarm that
 * worked, right up until the morning Abu misses. Everything else in the alarm area is plumbing
 * around this answer.
 *
 * The arithmetic is done in the zone rather than on instants, which is what keeps 07:00 at 07:00
 * across a daylight-saving change — the reason the stored column is a time of day and not an
 * instant. A local time that the transition skips is moved forward by the length of the gap, and
 * one that happens twice resolves to the first of the two; both are `ZonedDateTime`'s own rules,
 * and both are deterministic, which matters more here than which side of the seam is chosen.
 */
object NextAlarmTime {

    /**
     * The first occurrence of [at] strictly after [after].
     *
     * Strictly: an alarm set for the minute that is currently ticking belongs to the next time
     * round, not to now. [repeatDays] empty means every day, so the answer is today if the time
     * is still ahead and tomorrow otherwise.
     */
    fun after(
        at: LocalTime,
        repeatDays: Set<DayOfWeek>,
        zone: ZoneId,
        after: Instant,
    ): Instant {
        val from = ZonedDateTime.ofInstant(after, zone).toLocalDate()
        return generateSequence(from) { it.plusDays(1) }
            .filter { repeatDays.isEmpty() || it.dayOfWeek in repeatDays }
            .map { ZonedDateTime.of(it, at, zone).toInstant() }
            .first { it.isAfter(after) }
    }
}
