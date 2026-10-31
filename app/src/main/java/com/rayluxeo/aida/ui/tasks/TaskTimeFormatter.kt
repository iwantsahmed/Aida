package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.response.bengaliNumerals
import com.rayluxeo.aida.ui.response.bengaliWeekdayName
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/**
 * Every date and time the Home and Tasks screens put in front of Abu is worded here.
 *
 * ResponseRenderer already words anything Mira *says*; a due time on a list row, the date under
 * the greeting and the labels on the add-task sheet have no MiraResponse of their own, so without
 * one place to word them each composable would invent its own — and the Bengali ones would drift
 * apart first.
 *
 * The clock is injected for the same reason the phrasebooks take one: "today" and "tomorrow" are
 * the whole difference between a label that reads and a label that has to be decoded.
 */
class TaskTimeFormatter @Inject constructor(private val clock: MiraClock) {

    /** A due or trigger moment as a list row shows it: the day, then the time. */
    fun momentLabel(instant: Instant, language: SpokenLanguage): String {
        val local = LocalDateTime.ofInstant(instant, clock.zone())
        val day = dayLabel(local.toLocalDate(), language)
        val time = timeLabel(local.toLocalTime(), language)
        return "$day, $time"
    }

    /**
     * A due moment as a task row shows it, with the overdue word in the same language as the
     * moment beside it.
     *
     * The prefix used to be glued on by the composable as the English "Overdue — ", so a Bengali
     * row read "Overdue — গতকাল, সকাল ৯টা". It belongs here, with the rest of the wording both
     * Home and Tasks share, rather than in either screen.
     */
    fun dueLabel(instant: Instant, language: SpokenLanguage, isOverdue: Boolean): String {
        val moment = momentLabel(instant, language)
        if (!isOverdue) return moment
        return when (language) {
            SpokenLanguage.ENGLISH -> "Overdue · $moment"
            SpokenLanguage.BENGALI -> "সময় পেরিয়েছে · $moment"
        }
    }

    /** The line under Mira's greeting on Home: weekday and date, never a year. */
    fun dateHeadline(date: LocalDate, language: SpokenLanguage): String = when (language) {
        SpokenLanguage.ENGLISH ->
            "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)}, ${dateLabel(date, language)}"
        SpokenLanguage.BENGALI -> "${bengaliWeekdayName(date.dayOfWeek)}, ${dateLabel(date, language)}"
    }

    /** A bare date, with the year only when it is not the current one. */
    fun dateLabel(date: LocalDate, language: SpokenLanguage): String {
        val sameYear = date.year == clock.today().year
        return when (language) {
            SpokenLanguage.ENGLISH -> {
                val month = date.month.getDisplayName(TextStyle.FULL, Locale.US)
                if (sameYear) "${date.dayOfMonth} $month" else "${date.dayOfMonth} $month ${date.year}"
            }
            SpokenLanguage.BENGALI -> {
                val month = BENGALI_MONTHS[date.monthValue - 1]
                val day = digits(date.dayOfMonth, language)
                if (sameYear) "$day $month" else "$day $month ${digits(date.year, language)}"
            }
        }
    }

    /**
     * A wall-clock time. Bengali carries the part of the day instead of am/pm, which is how the
     * phrasebook speaks it and how Abu would write it.
     *
     * The wording itself lives in the clock-free [wallClockLabel] so the Automations sheet, which
     * has a chosen [LocalTime] but no formatter injected, speaks a trigger time exactly as a task's
     * due time reads — one Bengali rule, not two drifting copies.
     */
    fun timeLabel(time: LocalTime, language: SpokenLanguage): String = wallClockLabel(time, language)

    /**
     * Overdue means an earlier day, never earlier today — the same line the task repository
     * draws, so a row cannot read as overdue while the brief still counts it as due today.
     */
    fun isOverdue(dueAt: Instant?): Boolean {
        val due = dueAt ?: return false
        return due.isBefore(startOfToday())
    }

    fun isDueToday(dueAt: Instant?): Boolean {
        val due = dueAt ?: return false
        return !due.isBefore(startOfToday()) && due.isBefore(startOfTomorrow())
    }

    /** Turns the two halves the add-task sheet collects into the single moment a task stores. */
    fun toInstant(date: LocalDate, time: LocalTime): Instant =
        LocalDateTime.of(date, time).atZone(clock.zone()).toInstant()

    private fun dayLabel(date: LocalDate, language: SpokenLanguage): String {
        val today = clock.today()
        return when (date) {
            today -> if (language == SpokenLanguage.BENGALI) "আজ" else "Today"
            today.plusDays(1) -> if (language == SpokenLanguage.BENGALI) "আগামীকাল" else "Tomorrow"
            today.minusDays(1) -> if (language == SpokenLanguage.BENGALI) "গতকাল" else "Yesterday"
            else -> dateLabel(date, language)
        }
    }

    private fun startOfToday(): Instant = clock.today().atStartOfDay(clock.zone()).toInstant()

    private fun startOfTomorrow(): Instant = clock.today().plusDays(1).atStartOfDay(clock.zone()).toInstant()

    private fun digits(value: Int, language: SpokenLanguage): String =
        if (language == SpokenLanguage.BENGALI) bengaliNumerals(value.toString()) else value.toString()

    private companion object {
        val BENGALI_MONTHS = listOf(
            "জানুয়ারি",
            "ফেব্রুয়ারি",
            "মার্চ",
            "এপ্রিল",
            "মে",
            "জুন",
            "জুলাই",
            "আগস্ট",
            "সেপ্টেম্বর",
            "অক্টোবর",
            "নভেম্বর",
            "ডিসেম্বর",
        )
    }
}

/**
 * A wall-clock time, worded without needing a clock or a formatter instance.
 *
 * Pulled out of [TaskTimeFormatter.timeLabel] so a screen that collects a [LocalTime] directly —
 * the Automations sheet choosing when a rule fires — speaks it in the same Bengali part-of-day
 * form as a task's due time, instead of growing a second copy of that wording that drifts.
 */
internal fun wallClockLabel(time: LocalTime, language: SpokenLanguage): String {
    val hour12 = (time.hour % 12).let { if (it == 0) 12 else it }
    // Padded by hand rather than by String.format, which would follow the default locale.
    val padded = if (time.minute < 10) "0${time.minute}" else "${time.minute}"
    return when (language) {
        SpokenLanguage.ENGLISH -> {
            val meridiem = if (time.hour < 12) "am" else "pm"
            if (time.minute == 0) "$hour12 $meridiem" else "$hour12:$padded $meridiem"
        }
        SpokenLanguage.BENGALI -> {
            val part = bengaliPartOfDay(time.hour)
            val hour = bengaliNumerals(hour12.toString())
            if (time.minute == 0) "$part ${hour}টা" else "$part $hour:${bengaliNumerals(padded)}"
        }
    }
}

private fun bengaliPartOfDay(hour: Int): String = when (hour) {
    in 4..11 -> "সকাল"
    in 12..14 -> "দুপুর"
    in 15..17 -> "বিকেল"
    in 18..19 -> "সন্ধ্যা"
    else -> "রাত"
}

/**
 * Which language the screens write in.
 *
 * AUTO has nothing to sniff here — a list of Abu's own task titles says nothing about which
 * language he wants Mira's labels in — so it settles on English, exactly as the text normalizer
 * does for an utterance with no Bengali signal in it.
 */
internal fun LanguageMode.displayLanguage(): SpokenLanguage = when (this) {
    LanguageMode.BANGLA -> SpokenLanguage.BENGALI
    LanguageMode.ENGLISH -> SpokenLanguage.ENGLISH
    LanguageMode.AUTO -> SpokenLanguage.ENGLISH
}
