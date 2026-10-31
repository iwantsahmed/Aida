package com.rayluxeo.aida.assistant

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.assistant.ui.AssistantOverlayState
import com.rayluxeo.aida.assistant.ui.AssistantOverlayWording
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.SpeechFailure
import com.rayluxeo.aida.voice.VoiceSessionState
import java.time.Instant
import org.junit.Test

/**
 * The parts of the assistant session that are not the framework.
 *
 * `VoiceInteractionSession`, `VoiceInteractionService` and the `ComposeView` host cannot be built
 * in a local unit test — they are platform classes, and Hilt cannot be used here either under
 * AGP 9 (google/dagger#5261). What *is* testable was deliberately kept out of those classes: which
 * turn the overlay is looking at, which language it speaks, and what it says. The rest — that the
 * panel appears on the assist gesture, that Compose draws under the hand-built view-tree owners,
 * and that Mira can be heard from a session — needs the device and is listed as such in the report.
 */
class AssistantSessionLogicTest {

    private val opened = Instant.parse("2026-09-25T09:00:00Z")

    @Test
    fun `the overlay shows only the turn this session opened`() {
        val turns = listOf(
            turn(TurnRole.ABU, "yesterday's question", opened.minusSeconds(3_600)),
            turn(TurnRole.MIRA, "yesterday's answer", opened.minusSeconds(3_599)),
            turn(TurnRole.ABU, "kalker kaj gulo bolo", opened.plusSeconds(2)),
            turn(TurnRole.MIRA, "Three things tomorrow.", opened.plusSeconds(4)),
        )

        val exchange = latestExchange(turns, opened)

        assertThat(exchange.heard).isEqualTo("kalker kaj gulo bolo")
        assertThat(exchange.answer).isEqualTo("Three things tomorrow.")
    }

    @Test
    fun `nothing from before the session leaks into the panel`() {
        val turns = listOf(
            turn(TurnRole.ABU, "yesterday's question", opened.minusSeconds(10)),
            turn(TurnRole.MIRA, "yesterday's answer", opened.minusSeconds(9)),
        )

        val exchange = latestExchange(turns, opened)

        assertThat(exchange.heard).isNull()
        assertThat(exchange.answer).isNull()
    }

    @Test
    fun `the latest exchange wins when the same session holds several`() {
        val turns = listOf(
            turn(TurnRole.ABU, "first", opened.plusSeconds(1)),
            turn(TurnRole.MIRA, "first answer", opened.plusSeconds(2)),
            turn(TurnRole.ABU, "second", opened.plusSeconds(3)),
            turn(TurnRole.MIRA, "second answer", opened.plusSeconds(4)),
        )

        val exchange = latestExchange(turns, opened)

        assertThat(exchange.heard).isEqualTo("second")
        assertThat(exchange.answer).isEqualTo("second answer")
    }

    @Test
    fun `a chosen language is used whatever the transcript was in`() {
        assertThat(assistantLanguage(LanguageMode.BANGLA, SpokenLanguage.ENGLISH))
            .isEqualTo(SpokenLanguage.BENGALI)
        assertThat(assistantLanguage(LanguageMode.ENGLISH, SpokenLanguage.BENGALI))
            .isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `auto follows the last exchange and falls back to English`() {
        assertThat(assistantLanguage(LanguageMode.AUTO, SpokenLanguage.BENGALI))
            .isEqualTo(SpokenLanguage.BENGALI)
        assertThat(assistantLanguage(LanguageMode.AUTO, null)).isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `a failure from the last turn is hidden while the next one is moving`() {
        val listening = AssistantOverlayState(
            voice = VoiceSessionState(
                state = MiraVoiceState.LISTENING,
                lastFailure = SpeechFailure.NO_MATCH,
            ),
        )
        val settled = AssistantOverlayState(
            voice = VoiceSessionState(
                state = MiraVoiceState.ERROR,
                lastFailure = SpeechFailure.NO_MATCH,
            ),
        )

        assertThat(listening.failure).isNull()
        assertThat(settled.failure).isEqualTo(SpeechFailure.NO_MATCH)
    }

    @Test
    fun `the live partial outranks what was heard, and only while listening`() {
        val speaking = AssistantOverlayState(
            voice = VoiceSessionState(
                state = MiraVoiceState.LISTENING,
                partialTranscript = "kal shokal",
            ),
            heard = "an older sentence",
        )
        val stopped = speaking.copy(
            voice = speaking.voice.copy(state = MiraVoiceState.PROCESSING),
        )

        assertThat(speaking.spokenLine).isEqualTo("kal shokal")
        assertThat(stopped.spokenLine).isEqualTo("an older sentence")
    }

    @Test
    fun `every voice state has words in both languages`() {
        MiraVoiceState.entries.forEach { state ->
            val english = AssistantOverlayWording.status(state, SpokenLanguage.ENGLISH)
            val bengali = AssistantOverlayWording.status(state, SpokenLanguage.BENGALI)
            assertThat(english).isNotEmpty()
            assertThat(bengali).isNotEmpty()
            assertThat(bengali).isNotEqualTo(english)
        }
    }

    @Test
    fun `the context line names the app and says when a screenshot came with it`() {
        val withShot = AssistantOverlayState.Screen(appLabel = "Chrome", includesScreenshot = true)
        val textOnly = AssistantOverlayState.Screen(appLabel = "Chrome", includesScreenshot = false)
        val anonymous = AssistantOverlayState.Screen(appLabel = null, includesScreenshot = false)

        assertThat(AssistantOverlayWording.screenNote(withShot, SpokenLanguage.ENGLISH))
            .isEqualTo("Android shared Chrome's screen and a screenshot")
        assertThat(AssistantOverlayWording.screenNote(textOnly, SpokenLanguage.ENGLISH))
            .isEqualTo("Android shared Chrome's screen")
        assertThat(AssistantOverlayWording.screenNote(anonymous, SpokenLanguage.ENGLISH))
            .doesNotContain("null")
        assertThat(AssistantOverlayWording.screenNote(withShot, SpokenLanguage.BENGALI))
            .contains("Chrome")
    }

    @Test
    fun `only a failure that could succeed again offers to try again`() {
        val retry = AssistantOverlayWording.failureAction(
            SpeechFailure.NO_MATCH,
            SpokenLanguage.ENGLISH,
        )
        val open = AssistantOverlayWording.failureAction(
            SpeechFailure.PERMISSION,
            SpokenLanguage.ENGLISH,
        )

        assertThat(retry).isEqualTo("Try again")
        assertThat(open).isEqualTo("Open Aida")
    }

    private fun turn(role: TurnRole, text: String, at: Instant) = ConversationTurn(
        id = at.epochSecond,
        role = role,
        text = text,
        language = SpokenLanguage.ENGLISH,
        source = InputSource.VOICE,
        createdAt = at,
    )
}
