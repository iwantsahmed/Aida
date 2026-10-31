package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.ConversationStyle
import com.rayluxeo.aida.domain.model.DayPart
import java.time.Instant
import java.time.LocalDateTime

/**
 * One language's complete wording. English and Bengali live in separate implementations so a
 * future reader can reword one of them without having to read the other.
 *
 * [style] shapes how much is said around an answer, never the answer itself (§8): under
 * [ConversationStyle.BALANCED] every reply is byte-for-byte what Mira has always said.
 */
internal interface Phrasebook {
    fun speak(response: MiraResponse, style: ConversationStyle): String
    fun display(response: MiraResponse, style: ConversationStyle): String
}

/**
 * Where a moment falls relative to Abu's current day. Mira says "tomorrow" rather than a date
 * whenever she can, and this comparison is made once here instead of inside each phrasebook.
 */
internal enum class DayOffset { TODAY, TOMORROW, YESTERDAY, OTHER }

/** A moment already resolved into Abu's own wall clock, ready to be put into words. */
internal data class WallTime(
    val local: LocalDateTime,
    val offset: DayOffset,
    /** True when the moment is in the current calendar year, so the year can be left unsaid. */
    val sameYear: Boolean,
) {
    val hour12: Int get() = (local.hour % 12).let { if (it == 0) 12 else it }
    val minute: Int get() = local.minute
}

/**
 * The single point where an [Instant] meets the clock. Phrasebooks never call the clock
 * themselves, which keeps "is this today or tomorrow" answerable from one place in tests.
 */
internal class MiraTimes(private val clock: MiraClock) {

    fun at(instant: Instant): WallTime {
        val local = LocalDateTime.ofInstant(instant, clock.zone())
        val today = clock.today()
        val offset = when (local.toLocalDate()) {
            today -> DayOffset.TODAY
            today.plusDays(1) -> DayOffset.TOMORROW
            today.minusDays(1) -> DayOffset.YESTERDAY
            else -> DayOffset.OTHER
        }
        return WallTime(local = local, offset = offset, sameYear = local.year == today.year)
    }

    /** Abu's own current date, so a report keyed on a [java.time.LocalDate] can say "today". */
    fun today(): java.time.LocalDate = clock.today()

    /** Where a whole day falls relative to Abu's today, for reports that carry a date, not a moment. */
    fun offsetOf(date: java.time.LocalDate): DayOffset = when (date) {
        clock.today() -> DayOffset.TODAY
        clock.today().plusDays(1) -> DayOffset.TOMORROW
        clock.today().minusDays(1) -> DayOffset.YESTERDAY
        else -> DayOffset.OTHER
    }

    /**
     * Which part of the day it is right now, so a greeting can say "good evening" rather than a
     * flat "hello". The same four-way split the daily brief uses, decided here against the clock
     * so a greeting with no record behind it still knows the hour.
     */
    fun currentDayPart(): DayPart {
        val hour = LocalDateTime.ofInstant(clock.now(), clock.zone()).hour
        return when (hour) {
            in 5..11 -> DayPart.MORNING
            in 12..16 -> DayPart.AFTERNOON
            in 17..20 -> DayPart.EVENING
            else -> DayPart.NIGHT
        }
    }
}

/**
 * Abu's own titles may run to two hundred characters, and reading one of those aloud — three of
 * them, in a search result — is exactly the tiresome reply the product forbids. Speech is clipped;
 * the transcript still shows what he wrote in full.
 */
internal fun String.clipped(limit: Int): String {
    val clean = trim()
    if (clean.length <= limit) return clean
    val cut = clean.take(limit)
    val lastSpace = cut.lastIndexOf(' ')
    val head = if (lastSpace >= limit / 2) cut.take(lastSpace) else cut.trimEnd()
    return "$head…"
}

internal fun String.forSpeech(): String = clipped(SPOKEN_TITLE_LIMIT)

/** How many titles Mira is willing to read out; beyond three a spoken list stops being useful. */
internal const val SPOKEN_TITLE_COUNT = 3

/**
 * How many saved places Mira names out loud. Fewer than [SPOKEN_TITLE_COUNT], because the same
 * sentence already carries the place name she did not recognise, and a third one pushes it past
 * one breath. The transcript still lists every place she holds.
 */
internal const val SPOKEN_PLACE_COUNT = 2

private const val SPOKEN_TITLE_LIMIT = 40
