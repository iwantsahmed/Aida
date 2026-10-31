package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * When a time-based automation trigger should next fire.
 *
 * Pure so the arithmetic — "the next weekday at 08:00", "every 90 minutes from now" — is testable
 * against an injected clock rather than the wall. It is the single source of the next moment, used
 * both to arm an alarm and, after one fires, to arm the one after it. A non-time trigger (Manual, a
 * device event, a record deadline) returns null here: those are not on the clock and are never armed
 * by [AutomationAlarmScheduler].
 */
object AutomationNextRun {

    /**
     * The next instant [config] should fire, strictly after [after], or null when it never will — a
     * date already past, or a trigger that is not time-based at all.
     */
    fun next(config: AutomationTriggerConfig, after: Instant, zone: ZoneId): Instant? = when (config) {
        is AutomationTriggerConfig.TimeOfDay -> nextAt(config.minuteOfDay, config.daysOfWeek, after, zone)
        is AutomationTriggerConfig.DayOfWeekTrigger ->
            nextAt(config.minuteOfDay, setOf(config.dayOfWeek), after, zone)
        is AutomationTriggerConfig.Interval -> after.plusSeconds(config.everyMinutes.toLong() * 60L)
        is AutomationTriggerConfig.DateTrigger -> {
            val moment = LocalDateTime.of(config.date, minuteToTime(config.minuteOfDay)).atZone(zone).toInstant()
            moment.takeIf { it.isAfter(after) }
        }
        else -> null
    }

    /** True for the triggers [next] actually schedules — the ones an alarm can arm. */
    fun isTimeBased(config: AutomationTriggerConfig): Boolean = when (config) {
        is AutomationTriggerConfig.TimeOfDay,
        is AutomationTriggerConfig.DayOfWeekTrigger,
        is AutomationTriggerConfig.Interval,
        is AutomationTriggerConfig.DateTrigger,
        -> true
        else -> false
    }

    /**
     * The next occurrence of [minuteOfDay] on an allowed day. An empty [days] means every day, so the
     * walk finds today (if the time is still ahead) or tomorrow; a set of days skips forward to the
     * first one that matches with the time still ahead. Eight steps cover a full week with a day to
     * spare, so the loop always resolves.
     */
    private fun nextAt(
        minuteOfDay: Int,
        days: Set<java.time.DayOfWeek>,
        after: Instant,
        zone: ZoneId,
    ): Instant {
        val time = minuteToTime(minuteOfDay)
        val localAfter = LocalDateTime.ofInstant(after, zone)
        var date = localAfter.toLocalDate()
        repeat(8) {
            val candidate = LocalDateTime.of(date, time)
            val dayAllowed = days.isEmpty() || date.dayOfWeek in days
            if (dayAllowed && candidate.isAfter(localAfter)) {
                return candidate.atZone(zone).toInstant()
            }
            date = date.plusDays(1)
        }
        return LocalDateTime.of(date, time).atZone(zone).toInstant()
    }

    private fun minuteToTime(minuteOfDay: Int): LocalTime =
        LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59))
}
