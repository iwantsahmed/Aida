package com.rayluxeo.aida.ui.mira

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.TurnRole
import java.time.Instant
import org.junit.Test

/**
 * [MiraUiState.language] decides which language the screen's own chrome speaks — the status line,
 * the placeholder, the label on a prepared control — as distinct from the language of any one
 * turn, which each turn carries itself.
 *
 * It is worth its own tests because the AUTO case reads from the transcript, so the rule has two
 * inputs and either can move on its own.
 */
class MiraUiStateTest {

    @Test
    fun `the setting wins over whatever the last turn was in`() {
        val state = MiraUiState(
            languageMode = LanguageMode.ENGLISH,
            transcript = TranscriptState.Content(listOf(turn(SpokenLanguage.BENGALI))),
        )

        assertThat(state.language).isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `Bangla is spoken whatever the transcript holds`() {
        val state = MiraUiState(
            languageMode = LanguageMode.BANGLA,
            transcript = TranscriptState.Content(listOf(turn(SpokenLanguage.ENGLISH))),
        )

        assertThat(state.language).isEqualTo(SpokenLanguage.BENGALI)
    }

    /** Abu switches language mid-conversation, and the chrome has to follow him without a setting. */
    @Test
    fun `on AUTO the screen follows the language of the last thing said`() {
        val state = MiraUiState(
            languageMode = LanguageMode.AUTO,
            transcript = TranscriptState.Content(
                listOf(turn(SpokenLanguage.ENGLISH, id = 1L), turn(SpokenLanguage.BENGALI, id = 2L)),
            ),
        )

        assertThat(state.language).isEqualTo(SpokenLanguage.BENGALI)
    }

    /** Nothing has been said, so there is nothing to follow. English rather than a guess. */
    @Test
    fun `on AUTO an empty transcript falls back to English`() {
        assertThat(MiraUiState(transcript = TranscriptState.Empty).language)
            .isEqualTo(SpokenLanguage.ENGLISH)
        assertThat(MiraUiState(transcript = TranscriptState.Loading).language)
            .isEqualTo(SpokenLanguage.ENGLISH)
    }

    private fun turn(language: SpokenLanguage, id: Long = 1L) = ConversationTurn(
        id = id,
        role = TurnRole.ABU,
        text = "kal shokal dosh tay",
        language = language,
        source = InputSource.TEXT,
        createdAt = Instant.EPOCH,
    )
}
