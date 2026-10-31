package com.rayluxeo.aida.ui.mira

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraSpacing
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.VoiceSessionState
import org.junit.Test

/**
 * The two rules Mira's screen works out for itself, held where an eye cannot hold them.
 *
 * Both are branching over state and arithmetic over spacing tokens — no Compose runtime, no
 * Android, no Hilt (§7.4) — and both can be wrong in a way nobody would catch by looking. A
 * transcript whose rhythm has stopped grouping a question with its answer still looks like a
 * transcript, and a mark announcing "Speak to Mira" while it refuses every press still looks like
 * a mark.
 *
 * Gaps are compared as ratios rather than as dp values, so the rhythm survives the scale being
 * retuned and fails only when the intervals stop being one progression.
 */
class MiraScreenRulesTest {

    private val spacing = MiraSpacing()
    private val english = miraWording(SpokenLanguage.ENGLISH)

    // ------------------------------------------------------------------ the transcript's rhythm

    private val pair get() = spacing.gapBefore(TurnRole.MIRA, previous = TurnRole.ABU).value
    private val continuation get() = spacing.gapBefore(TurnRole.MIRA, previous = TurnRole.MIRA).value
    private val newExchange get() = spacing.gapBefore(TurnRole.ABU, previous = TurnRole.MIRA).value

    /**
     * The whole point of the rhythm: an answer belongs to the question above it, and that has to
     * be legible before a word is read. Without it the two roles have nothing keeping them apart
     * but their colour.
     */
    @Test
    fun `an answer sits closer to its question than a speaker carrying on does`() {
        assertThat(pair).isLessThan(continuation)
    }

    @Test
    fun `a new question opens wider than anything inside an exchange`() {
        assertThat(newExchange).isGreaterThan(pair)
        assertThat(newExchange).isGreaterThan(continuation)
        assertThat(newExchange)
            .isGreaterThan(spacing.gapBefore(TurnRole.ABU, previous = TurnRole.ABU).value)
    }

    /** Three intervals, each double the last, which is what makes them read as intervals. */
    @Test
    fun `the three intervals are a doubling`() {
        assertThat(continuation).isWithin(TOLERANCE).of(pair * 2f)
        assertThat(newExchange).isWithin(TOLERANCE).of(continuation * 2f)
    }

    /** The first turn already has the list's own top padding above it; it adds nothing to it. */
    @Test
    fun `the first turn of all gets the smallest gap there is`() {
        assertThat(spacing.gapBefore(TurnRole.ABU, previous = null).value).isLessThan(pair)
    }

    // ------------------------------------------------------------------ what the mark announces

    @Test
    fun `a press that would start listening says so`() {
        assertThat(resting().voiceDescription(english)).isEqualTo(english.speakToMira)
    }

    @Test
    fun `while Mira is listening the mark offers to stop her`() {
        val listening = resting().copy(
            voice = VoiceSessionState(state = MiraVoiceState.LISTENING, canListen = true),
        )

        assertThat(listening.voiceDescription(english)).isEqualTo(english.stopListening)
    }

    /**
     * A reply to something Abu typed is read out by the view model, outside the controller's turn,
     * so the controller still reports IDLE while Mira is plainly talking. A press stops her either
     * way, and what TalkBack says has to follow the press rather than the controller.
     */
    @Test
    fun `a reply to a typed line is announced as something a press would stop`() {
        val readingOut = resting().copy(speaking = true)

        assertThat(readingOut.voiceDescription(english)).isEqualTo(english.stopMira)
    }

    /**
     * The mark takes no press at all while a typed command is in flight, so it is not a button —
     * and the one thing TalkBack must never do is offer Abu a press nothing will answer.
     */
    @Test
    fun `a mark that will not answer a press never offers one`() {
        val working = resting().copy(working = true)

        assertThat(working.canListen).isFalse()
        assertThat(working.voiceDescription(english)).isNotEqualTo(english.speakToMira)
        assertThat(working.voiceDescription(english)).isEqualTo(english.thinking)
    }

    /** Why it is dead, and what is left to do about it, in one sentence: the mark has no glyph. */
    @Test
    fun `a microphone Android will not ask about again says what is left to do`() {
        val settingsOnly = resting().copy(
            voice = VoiceSessionState(canListen = false),
            microphone = MicrophoneAccess.SETTINGS_ONLY,
        )

        assertThat(settingsOnly.voiceDescription(english)).isEqualTo(english.speechOff)
    }

    private fun resting() = MiraUiState(
        transcript = TranscriptState.Empty,
        voice = VoiceSessionState(canListen = true),
        microphone = MicrophoneAccess.GRANTED,
    )

    private companion object {

        /** These are whole dp values doubled; the tolerance is here because `Dp` holds a float. */
        const val TOLERANCE = 0.001f
    }
}
