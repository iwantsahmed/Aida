package com.rayluxeo.aida.ui.meetings

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

/**
 * The Meetings phrasebook is the one place the screen gets its words, and a missed translation here
 * is not a red build — it is an English label sitting in a Bengali form, which nobody notices until
 * Abu does.
 *
 * Every member is walked in both languages: a blank one is a label the screen would draw empty, and
 * a Bengali value equal to its English twin is a line that was copied across and never translated.
 */
class MeetingsWordingTest {

    private val english = meetingsWording(SpokenLanguage.ENGLISH)
    private val bengali = meetingsWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishMeetingsWording)
        assertThat(bengali).isSameInstanceAs(BengaliMeetingsWording)
    }

    @Test
    fun `every fixed label is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.screenTitle).isNotEmpty()
            assertThat(words.newMeeting).isNotEmpty()
            assertThat(words.emptyMessage).isNotEmpty()
            assertThat(words.meetingTitleLabel).isNotEmpty()
            assertThat(words.meetingTitlePlaceholder).isNotEmpty()
            assertThat(words.locationLabel).isNotEmpty()
            assertThat(words.saveMeeting).isNotEmpty()
        }
    }

    @Test
    fun `every line carrying runtime data is worded in both languages`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.startsAt(MOMENT)).isNotEmpty()
            assertThat(words.location(PLACE)).isNotEmpty()
            assertThat(words.meetingScheduled(MEETING_TITLE)).isNotEmpty()
        }
    }

    @Test
    fun `no fixed label was left in English on the Bengali side`() {
        assertThat(bengali.screenTitle).isNotEqualTo(english.screenTitle)
        assertThat(bengali.newMeeting).isNotEqualTo(english.newMeeting)
        assertThat(bengali.emptyMessage).isNotEqualTo(english.emptyMessage)
        assertThat(bengali.meetingTitleLabel).isNotEqualTo(english.meetingTitleLabel)
        assertThat(bengali.meetingTitlePlaceholder).isNotEqualTo(english.meetingTitlePlaceholder)
        assertThat(bengali.locationLabel).isNotEqualTo(english.locationLabel)
        assertThat(bengali.saveMeeting).isNotEqualTo(english.saveMeeting)
    }

    @Test
    fun `no data-bearing line was left in English on the Bengali side`() {
        assertThat(bengali.startsAt(MOMENT)).isNotEqualTo(english.startsAt(MOMENT))
        assertThat(bengali.location(PLACE)).isNotEqualTo(english.location(PLACE))
        assertThat(bengali.meetingScheduled(MEETING_TITLE))
            .isNotEqualTo(english.meetingScheduled(MEETING_TITLE))
    }

    /**
     * A data-bearing line keeps the data it was handed: the start moment arrives already formatted,
     * the location is Abu's own text, and the notice names the meeting he just scheduled.
     */
    @Test
    fun `a data-bearing line carries the value it was given`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.startsAt(MOMENT)).contains(MOMENT)
            assertThat(words.location(PLACE)).contains(PLACE)
            assertThat(words.meetingScheduled(MEETING_TITLE)).contains(MEETING_TITLE)
        }
    }

    private companion object {
        const val MOMENT = "2026-10-09 14:30"
        const val PLACE = "Studio"
        const val MEETING_TITLE = "Client Review Sync"
    }
}
