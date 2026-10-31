package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.nlu.DateTimeParser
import com.rayluxeo.aida.domain.nlu.TemporalMatch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts a day and a time of day together into the one moment Abu meant, against his own clock.
 *
 * [RelativeDateParser] and [TimeExpressionParser] each answer half the question and neither can
 * answer it alone: "kal 10 tay" is tomorrow at ten only because the ten is read against the
 * tomorrow, and a bare "friday" moves to next week only once the hour it carries turns out to
 * have gone. Those two decisions live here and nowhere else.
 *
 * It reads canonical tokens, so "kal shokal 10 tay", "আগামীকাল সকাল ১০টায়" and "tomorrow
 * morning at 10" are literally the same input by the time they arrive. The expression is
 * reported back with the tokens it consumed removed, because whatever is left over is almost
 * always the title of the thing Abu wants remembered.
 */
@Singleton
class MiraDateTimeParser @Inject constructor(
    private val dates: RelativeDateParser,
    private val times: TimeExpressionParser,
    private val clock: MiraClock,
) : DateTimeParser {

    /**
     * For a caller that holds a clock and nothing else. Neither parser has a collaborator of
     * its own, so there is nothing here for a caller to configure or substitute.
     */
    constructor(clock: MiraClock) : this(RelativeDateParser(clock), TimeExpressionParser(clock), clock)

    override fun parse(normalized: String): TemporalMatch? {
        val duration = times.parseDuration(normalized)
        if (duration != null) {
            return TemporalMatch(
                instant = clock.now().plus(duration.duration),
                hasExplicitTime = true,
                remainder = duration.remainder,
                confidence = DURATION_CONFIDENCE,
            )
        }

        val date = dates.read(normalized)
        // A day already established that a time is being discussed, which is what lets the bare
        // "10" in "kal 10 tay" be an hour while the "3" in "call 3 people" stays a count.
        val time = times.read(date?.date?.remainder ?: normalized, anchored = date != null)
        if (date == null && time == null) return null

        val local = compose(date, time)
        return TemporalMatch(
            instant = local.atZone(clock.zone()).toInstant(),
            hasExplicitTime = time?.namesAnHour == true,
            remainder = time?.time?.remainder ?: date?.date?.remainder ?: normalized,
            confidence = confidenceOf(date != null, time?.namesAnHour == true),
        )
    }

    private fun compose(date: RelativeDateParser.Reading?, time: TimeExpressionParser.Reading?): LocalDateTime {
        val day = date?.date?.date ?: clock.today()
        if (time == null) return settle(LocalDateTime.of(day, LocalTime.of(DEFAULT_HOUR, 0)), date)
        val stated = time.time.time
        val unqualified = time.unqualifiedHour ?: return settle(LocalDateTime.of(day, stated), date)
        return nextOccurrenceOf(day, unqualified, stated.minute, date)
    }

    /**
     * Moves a candidate forward when it has already passed. A named day rolls by a week — Abu
     * saying "friday" on a Friday evening means the next one — while a bare time rolls by a day.
     * A day he named outright stays where he put it, even if it is behind us.
     */
    private fun settle(candidate: LocalDateTime, date: RelativeDateParser.Reading?): LocalDateTime = when {
        candidate.isAfter(clock.localNow()) -> candidate
        date == null -> candidate.plusDays(1)
        date.rollsByWeek -> candidate.plusDays(DAYS_IN_WEEK.toLong())
        else -> candidate
    }

    /**
     * An hour with nothing to say which half of the day it belongs to means the next time that
     * hour comes round — which inside a "tomorrow" phrase is tomorrow morning, not tonight.
     */
    private fun nextOccurrenceOf(
        day: LocalDate,
        hour: Int,
        minute: Int,
        date: RelativeDateParser.Reading?,
    ): LocalDateTime {
        val now = clock.localNow()
        val morning = LocalDateTime.of(day, LocalTime.of(hour % HOURS_ON_A_DIAL, minute))
        val evening = LocalDateTime.of(day, LocalTime.of(hour % HOURS_ON_A_DIAL + HOURS_ON_A_DIAL, minute))
        return listOf(morning, evening).firstOrNull { it.isAfter(now) } ?: settle(morning, date)
    }

    private fun confidenceOf(hasDate: Boolean, hasHour: Boolean): Float = when {
        hasDate && hasHour -> DAY_AND_TIME_CONFIDENCE
        hasHour -> TIME_ONLY_CONFIDENCE
        hasDate -> DAY_ONLY_CONFIDENCE
        else -> DAY_PART_ONLY_CONFIDENCE
    }

    private companion object {
        const val DEFAULT_HOUR = 9
        const val DAYS_IN_WEEK = 7
        const val HOURS_ON_A_DIAL = 12

        const val DURATION_CONFIDENCE = 0.9f
        const val DAY_AND_TIME_CONFIDENCE = 0.95f
        const val TIME_ONLY_CONFIDENCE = 0.85f
        const val DAY_ONLY_CONFIDENCE = 0.8f
        const val DAY_PART_ONLY_CONFIDENCE = 0.7f
    }
}
