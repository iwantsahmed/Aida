package com.rayluxeo.aida.ui.voicenotes

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

/**
 * The Voice Notes phrasebook is the one place the screen and the view model's notices get their
 * words, so a missed translation here is not a red build — it is an English label sitting in a
 * Bengali screen, which nobody notices until Abu does.
 *
 * Every assertion below is about a gap a human eye skips: a label left untranslated, a recording
 * state or a notice added to the enum and forgotten in one language.
 */
class VoiceNotesWordingTest {

    private val english = voiceNotesWording(SpokenLanguage.ENGLISH)
    private val bengali = voiceNotesWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishVoiceNotesWording)
        assertThat(bengali).isSameInstanceAs(BengaliVoiceNotesWording)
    }

    @Test
    fun `every fixed label is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            assertThat(words.title).isNotEmpty()
            assertThat(words.emptyMessage).isNotEmpty()
            assertThat(words.record).isNotEmpty()
            assertThat(words.pause).isNotEmpty()
            assertThat(words.resume).isNotEmpty()
            assertThat(words.stopAndSave).isNotEmpty()
            assertThat(words.play).isNotEmpty()
            assertThat(words.deleteNote).isNotEmpty()
            assertThat(words.untitledNote).isNotEmpty()
        }
    }

    @Test
    fun `no fixed label was left in English on the Bengali side`() {
        assertThat(bengali.title).isNotEqualTo(english.title)
        assertThat(bengali.emptyMessage).isNotEqualTo(english.emptyMessage)
        assertThat(bengali.record).isNotEqualTo(english.record)
        assertThat(bengali.pause).isNotEqualTo(english.pause)
        assertThat(bengali.resume).isNotEqualTo(english.resume)
        assertThat(bengali.stopAndSave).isNotEqualTo(english.stopAndSave)
        assertThat(bengali.play).isNotEqualTo(english.play)
        assertThat(bengali.deleteNote).isNotEqualTo(english.deleteNote)
        assertThat(bengali.untitledNote).isNotEqualTo(english.untitledNote)
    }

    @Test
    fun `every recording state is worded in both languages, and differently`() {
        RecordingState.entries.forEach { state ->
            assertThat(english.recordingStatus(state)).isNotEmpty()
            assertThat(bengali.recordingStatus(state)).isNotEmpty()
            assertThat(bengali.recordingStatus(state)).isNotEqualTo(english.recordingStatus(state))
        }
    }

    @Test
    fun `every notice is worded in both languages, and differently`() {
        VoiceNotesNotice.entries.forEach { notice ->
            assertThat(english.notice(notice)).isNotEmpty()
            assertThat(bengali.notice(notice)).isNotEmpty()
            assertThat(bengali.notice(notice)).isNotEqualTo(english.notice(notice))
        }
    }
}
