package com.rayluxeo.aida.ui.alarms

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.response.WEEKDAY_SET
import java.time.DayOfWeek
import java.time.Duration
import org.junit.Test

/**
 * The phrasebook is where a bilingual screen slips silently: a weekday added with no Bengali name
 * still compiles, and Abu finds out by reading "Mon" in the middle of a Bengali row.
 *
 * These walk every value rather than sampling one.
 */
class AlarmsWordingTest {

    private val english = alarmsWording(SpokenLanguage.ENGLISH)
    private val bengali = alarmsWording(SpokenLanguage.BENGALI)

    @Test
    fun `every weekday has a short name in both languages and none repeats`() {
        val englishNames = DayOfWeek.entries.map { english.weekdayShort(it) }
        val bengaliNames = DayOfWeek.entries.map { bengali.weekdayShort(it) }

        assertThat(englishNames).containsNoDuplicates()
        assertThat(bengaliNames).containsNoDuplicates()
        assertThat(englishNames.none { it.isEmpty() }).isTrue()
        assertThat(bengaliNames.none { it.isEmpty() }).isTrue()
    }

    @Test
    fun `the three repeat shapes read as themselves rather than as a list of days`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.repeatSummary(emptySet())).isEqualTo(words.repeatOnce)
            assertThat(words.repeatSummary(EVERY_DAY)).isEqualTo(words.repeatEveryDay)
            assertThat(words.repeatSummary(WEEKDAY_SET)).isEqualTo(words.repeatWeekdays)
        }
    }

    /** A partial repeat is the days themselves, in week order whatever order the set arrived in. */
    @Test
    fun `a partial repeat lists its days in week order`() {
        val days = setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY)

        assertThat(english.repeatSummary(days)).isEqualTo("Mon · Thu")
        assertThat(bengali.repeatSummary(days)).isEqualTo("সোম · বৃহঃ")
    }

    /**
     * Every one of the 128 subsets, because the fault this guards against is a set that is named
     * as one idea in one place and spelled out day by day in the other. The row and Mira's spoken
     * reply share the decision; only the words differ, so a set that grows a name of its own must
     * grow it on both sides at once.
     */
    @Test
    fun `every set of repeat days reads as one idea or as exactly its days`() {
        val week = DayOfWeek.entries
        for (mask in 0 until (1 shl week.size)) {
            val days = week.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }.toSet()
            listOf(english, bengali).forEach { words ->
                val summary = words.repeatSummary(days)
                val asOneIdea = setOf(words.repeatOnce, words.repeatEveryDay, words.repeatWeekdays)

                assertThat(summary).isNotEmpty()
                if (summary !in asOneIdea) {
                    assertThat(summary.split(DAY_SEPARATOR)).hasSize(days.size)
                }
            }
        }
    }

    /**
     * The fault this guards against shipped once: a row read "Mon · Wed · Fri · Rings tomorrow,
     * 5 am", where the ring time parsed as a fourth day. The mark that joins the two halves of a
     * row cannot be the mark that joins days inside the left half.
     */
    @Test
    fun `a row's two halves are not joined by the mark that joins its days`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        listOf(english, bengali).forEach { words ->
            val repeat = words.repeatSummary(days)
            val line = words.rowSupporting(repeat, words.alarmOff)

            assertThat(line).contains(repeat)
            assertThat(line).contains(words.alarmOff)
            assertThat(line.split(DAY_SEPARATOR)).hasSize(days.size)
        }
    }

    /** A title that drops either half is a row Abu cannot scan or cannot name. */
    @Test
    fun `a row's title carries both the time and the label`() {
        listOf(english, bengali).forEach { words ->
            val title = words.rowTitle("5 am", words.untitledAlarm)

            assertThat(title).contains("5 am")
            assertThat(title).contains(words.untitledAlarm)
        }
    }

    /**
     * One sentence, not a paragraph.
     *
     * This line is danger-coloured, sits above everything else on two screens, and shares its row
     * with an action button that is measured first — so the column left for the words is narrow
     * and every character is paid for in height. It was a title and a body concatenated, about
     * 160 characters, which came to roughly nine wrapped lines on a phone. The cap is deliberately
     * generous; what it forbids is the paragraph coming back.
     */
    @Test
    fun `the exact-alarm notice stays one line long in both languages`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.exactAlarmsDenied).isNotEmpty()
            assertThat(words.exactAlarmsDenied.length).isAtMost(NOTICE_CHARACTER_BUDGET)
            assertThat(words.openExactAlarmSettings).isNotEmpty()
            assertThat(words.openExactAlarmSettings.length).isAtMost(ACTION_CHARACTER_BUDGET)
        }
    }

    /** Three presets a row cannot tell apart are three chips Abu cannot tell apart either. */
    @Test
    fun `the repeat presets are distinct words in both languages`() {
        listOf(english, bengali).forEach { words ->
            val presets = listOf(words.repeatOnce, words.repeatEveryDay, words.repeatWeekdays)

            assertThat(presets).containsNoDuplicates()
            assertThat(presets.none { it.isEmpty() }).isTrue()
        }
    }

    /**
     * English takes its day names from `Locale.US`, so a Bengali phrasebook that ever fell through
     * to the same call would put "Mon" in the middle of a Bengali row — the slip that reads as
     * broken even to someone who cannot read the words around it.
     */
    @Test
    fun `no Bengali weekday name is written in Latin letters`() {
        DayOfWeek.entries.forEach { day ->
            val name = bengali.weekdayShort(day)

            assertThat(name.none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
            assertThat(name).isNotEmpty()
        }
    }

    @Test
    fun `a countdown drops the hours until there are some`() {
        assertThat(english.countdown(Duration.ofMinutes(25))).isEqualTo("25:00")
        assertThat(english.countdown(Duration.ofSeconds(59))).isEqualTo("00:59")
        assertThat(english.countdown(Duration.ofMinutes(90))).isEqualTo("1:30:00")
    }

    /** A timer whose moment has gone reads as zero, never as a countdown running backwards. */
    @Test
    fun `a countdown past its moment reads as zero`() {
        assertThat(english.countdown(Duration.ofSeconds(-5))).isEqualTo("00:00")
        assertThat(bengali.countdown(Duration.ofSeconds(-5))).isEqualTo("০০:০০")
    }

    /**
     * Latin digits inside a Bengali sentence are the one slip that reads as broken even to someone
     * who cannot read the words around them.
     */
    @Test
    fun `nothing Bengali is written with Latin digits`() {
        val counted = listOf(
            bengali.countdown(Duration.ofMinutes(90)),
            bengali.durationSummary(Duration.ofMinutes(25)),
            bengali.durationSummary(Duration.ofMinutes(90)),
            bengali.durationPreset(Duration.ofMinutes(45)),
            bengali.durationPreset(Duration.ofHours(1)),
        )

        counted.forEach { line ->
            assertThat(line.none { it in '0'..'9' }).isTrue()
            assertThat(line).isNotEmpty()
        }
    }

    @Test
    fun `a duration of less than an hour still names its minutes`() {
        assertThat(english.durationSummary(Duration.ofMinutes(1))).isEqualTo("1 minute")
        assertThat(english.durationSummary(Duration.ofMinutes(25))).isEqualTo("25 minutes")
        assertThat(english.durationSummary(Duration.ofHours(2))).isEqualTo("2 hours")
        assertThat(english.durationSummary(Duration.ofMinutes(90))).isEqualTo("1 hour 30 minutes")
    }

    /** Every preset the composer offers has to read as a length, not as a bare number. */
    @Test
    fun `every timer preset is worded in both languages`() {
        TIMER_PRESET_MINUTES.forEach { minutes ->
            val duration = Duration.ofMinutes(minutes)
            assertThat(english.durationPreset(duration)).isNotEmpty()
            assertThat(bengali.durationPreset(duration)).isNotEmpty()
            assertThat(english.durationSummary(duration)).isNotEmpty()
            assertThat(bengali.durationSummary(duration)).isNotEmpty()
        }
    }

    /** The presets are what Abu reaches for, so they must be in order and never zero. */
    @Test
    fun `the timer presets are positive and get longer`() {
        assertThat(TIMER_PRESET_MINUTES.all { it > 0 }).isTrue()
        assertThat(TIMER_PRESET_MINUTES).isInStrictOrder()
    }

    private companion object {
        /** Roughly three wrapped lines beside the action button on a phone. */
        const val NOTICE_CHARACTER_BUDGET = 120

        /** The button shares the notice's row and takes its width out of the words beside it. */
        const val ACTION_CHARACTER_BUDGET = 16
    }
}
