package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.scheduler.ReminderNotificationAction
import com.rayluxeo.aida.domain.usecase.SnoozeReminderUseCase
import org.junit.Test

/**
 * A notification is the one place Mira speaks to Abu when he is not looking at her, and a button
 * he cannot read is a button he will not press. So every label is checked in both languages, and
 * the Bengali ones are checked for Latin digits — "Snooze 10 min" with the number left in ASCII is
 * the failure that slips through a reading of the file.
 */
class ReminderWordingTest {

    private val wording = ReminderNotificationWordingImpl()

    private val languages = SpokenLanguage.entries

    /** Anything that would read as a foreign digit in the middle of a Bengali line. */
    private val latinDigit = Regex("[0-9]")

    // ---- The buttons ----

    @Test
    fun `every action is labelled in both languages`() {
        for (language in languages) {
            for (action in ReminderNotificationAction.entries) {
                assertThat(wording.actionLabel(action, language).trim()).isNotEmpty()
            }
        }
    }

    @Test
    fun `no two actions share a label`() {
        for (language in languages) {
            val labels = ReminderNotificationAction.entries.map { wording.actionLabel(it, language) }
            assertThat(labels).containsNoDuplicates()
        }
    }

    @Test
    fun `English and Bengali never give an action the same words`() {
        for (action in ReminderNotificationAction.entries) {
            assertThat(wording.actionLabel(action, SpokenLanguage.BENGALI))
                .isNotEqualTo(wording.actionLabel(action, SpokenLanguage.ENGLISH))
        }
    }

    @Test
    fun `Bengali labels carry no Latin digits`() {
        for (action in ReminderNotificationAction.entries) {
            val label = wording.actionLabel(action, SpokenLanguage.BENGALI)
            assertThat(latinDigit.containsMatchIn(label)).isFalse()
        }
        assertThat(latinDigit.containsMatchIn(wording.dismiss(SpokenLanguage.BENGALI))).isFalse()
    }

    /**
     * The spans are read from the use case that performs them, so a change to either constant
     * fails here rather than shipping a button that says ten minutes and means an hour.
     */
    @Test
    fun `a snooze button names the span it actually pushes the reminder by`() {
        assertThat(SnoozeReminderUseCase.SHORT.toMinutes()).isEqualTo(10L)
        assertThat(SnoozeReminderUseCase.LONG.toHours()).isEqualTo(1L)

        assertThat(wording.actionLabel(ReminderNotificationAction.SNOOZE_TEN_MINUTES, SpokenLanguage.ENGLISH))
            .contains("10")
        assertThat(wording.actionLabel(ReminderNotificationAction.SNOOZE_ONE_HOUR, SpokenLanguage.ENGLISH))
            .contains("1")

        assertThat(wording.actionLabel(ReminderNotificationAction.SNOOZE_TEN_MINUTES, SpokenLanguage.BENGALI))
            .contains("১০")
        assertThat(wording.actionLabel(ReminderNotificationAction.SNOOZE_ONE_HOUR, SpokenLanguage.BENGALI))
            .contains("১")
    }

    @Test
    fun `the two snoozes are told apart in both languages`() {
        for (language in languages) {
            assertThat(wording.actionLabel(ReminderNotificationAction.SNOOZE_TEN_MINUTES, language))
                .isNotEqualTo(wording.actionLabel(ReminderNotificationAction.SNOOZE_ONE_HOUR, language))
        }
    }

    // ---- The line under the title ----

    @Test
    fun `a plain reminder gets no second line`() {
        for (language in languages) {
            assertThat(wording.kindLine(ReminderKind.ONE_TIME, language)).isNull()
        }
    }

    @Test
    fun `every other kind says what it is, in both languages`() {
        val named = ReminderKind.entries - ReminderKind.ONE_TIME
        for (language in languages) {
            for (kind in named) {
                assertThat(wording.kindLine(kind, language)?.trim()).isNotEmpty()
            }
        }
        for (kind in named) {
            assertThat(wording.kindLine(kind, SpokenLanguage.BENGALI))
                .isNotEqualTo(wording.kindLine(kind, SpokenLanguage.ENGLISH))
        }
    }

    // ---- A reminder that arrived with no title ----

    /**
     * Singular on purpose. The fallback before this was the reminders channel's own name, which
     * is the plural "Reminders" — a list where Abu has exactly one thing.
     */
    @Test
    fun `an untitled reminder is named in the singular, in both languages`() {
        assertThat(wording.untitledReminder(SpokenLanguage.ENGLISH)).isEqualTo("Reminder")
        assertThat(wording.untitledReminder(SpokenLanguage.BENGALI)).isEqualTo("রিমাইন্ডার")

        for (language in languages) {
            assertThat(wording.untitledReminder(language).trim())
                .isEqualTo(wording.untitledReminder(language))
        }
    }

    // ---- Alarms ----

    @Test
    fun `an alarm Abu named leads with the name and says what it is underneath`() {
        val text = wording.alarm("Fajr", SpokenLanguage.ENGLISH)
        assertThat(text.title).isEqualTo("Fajr")
        assertThat(text.body).isNotEmpty()
    }

    /**
     * "Wake me up at seven" names no subject, so this branch is reached in earnest rather than
     * defensively. The words are pinned because the fallback before it was the app's own name,
     * which titled a nameless alarm "Mira".
     */
    @Test
    fun `an unnamed alarm is called an alarm and has nothing more to add`() {
        for (language in languages) {
            val text = wording.alarm("   ", language)
            assertThat(text.title.trim()).isNotEmpty()
            assertThat(text.body).isEmpty()
        }

        assertThat(wording.alarm("   ", SpokenLanguage.ENGLISH).title).isEqualTo("Alarm")
        assertThat(wording.alarm("", SpokenLanguage.BENGALI).title).isEqualTo("অ্যালার্ম")
    }

    // ---- Timers ----

    @Test
    fun `a finished timer Abu named leads with the name`() {
        val text = wording.timerFinished("Tea", ranOutWhileOff = false, language = SpokenLanguage.ENGLISH)
        assertThat(text.title).isEqualTo("Tea")
        assertThat(text.body).isNotEmpty()
    }

    @Test
    fun `an unnamed timer that just ended says so once`() {
        for (language in languages) {
            val text = wording.timerFinished("", ranOutWhileOff = false, language = language)
            assertThat(text.title.trim()).isNotEmpty()
            assertThat(text.body).isEmpty()
        }
    }

    /**
     * Ending while the phone was off is a different fact from ending a moment ago, and Abu acts
     * differently on each — so it must never be reported with the same line.
     */
    @Test
    fun `a timer that ran out while the phone was off says that instead`() {
        for (language in languages) {
            val late = wording.timerFinished("Tea", ranOutWhileOff = true, language = language)
            val fresh = wording.timerFinished("Tea", ranOutWhileOff = false, language = language)
            assertThat(late.body).isNotEmpty()
            assertThat(late.body).isNotEqualTo(fresh.body)
        }
    }

    // ---- The channels Android draws in its own settings ----

    @Test
    fun `both channels are named and described in both languages`() {
        for (language in languages) {
            for (channel in listOf(wording.alarmChannel(language), wording.timerChannel(language))) {
                assertThat(channel.title.trim()).isNotEmpty()
                assertThat(channel.body.trim()).isNotEmpty()
            }
        }
    }

    @Test
    fun `alarms and timers are not the same channel to Abu`() {
        for (language in languages) {
            assertThat(wording.alarmChannel(language).title)
                .isNotEqualTo(wording.timerChannel(language).title)
        }
    }

    /** Android puts these beside the briefs channel, so one of them in English would be a seam. */
    @Test
    fun `a Bengali channel is named in Bengali`() {
        assertThat(wording.alarmChannel(SpokenLanguage.BENGALI).title)
            .isNotEqualTo(wording.alarmChannel(SpokenLanguage.ENGLISH).title)
        assertThat(wording.timerChannel(SpokenLanguage.BENGALI).title)
            .isNotEqualTo(wording.timerChannel(SpokenLanguage.ENGLISH).title)
    }
}
