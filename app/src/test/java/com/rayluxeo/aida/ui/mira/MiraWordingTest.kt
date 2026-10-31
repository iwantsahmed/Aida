package com.rayluxeo.aida.ui.mira

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraCapability
import org.junit.Test

/**
 * Mira's own screen was the last one written in English only, and a phrasebook slips silently:
 * a line added to [MiraWording] with the English text copied into the Bengali object compiles,
 * and Abu finds out by reading "Listening" over a Bengali exchange.
 *
 * So every line is walked rather than sampled, and the two phrasebooks are compared against each
 * other — a Bengali entry that still holds its English words is the failure this catches.
 */
class MiraWordingTest {

    private val english = miraWording(SpokenLanguage.ENGLISH)
    private val bengali = miraWording(SpokenLanguage.BENGALI)

    @Test
    fun `the selector hands back the phrasebook for the language asked for`() {
        assertThat(english).isSameInstanceAs(EnglishMiraWording)
        assertThat(bengali).isSameInstanceAs(BengaliMiraWording)
    }

    @Test
    fun `every line is written in both languages`() {
        english.everyLine().forEach { assertThat(it).isNotEmpty() }
        bengali.everyLine().forEach { assertThat(it).isNotEmpty() }
    }

    /** Same count, same order: the comparison below is only meaningful if they line up. */
    @Test
    fun `the two phrasebooks say the same number of things`() {
        assertThat(bengali.everyLine()).hasSize(english.everyLine().size)
    }

    @Test
    fun `no Bengali line is still holding its English words`() {
        english.everyLine().zip(bengali.everyLine()).forEach { (inEnglish, inBengali) ->
            assertThat(inBengali).isNotEqualTo(inEnglish)
        }
    }

    /**
     * `AGENTS.md` §10 forbids emoji in UI copy. Bengali and Latin both live in the basic plane, so
     * a surrogate pair anywhere in the phrasebook is a character from outside it — which in
     * practice means an emoji got pasted in.
     */
    @Test
    fun `nothing on this screen is written with an emoji`() {
        (english.everyLine() + bengali.everyLine()).forEach { line ->
            assertThat(line.any { it.isSurrogate() }).isFalse()
        }
    }

    /**
     * The status line is the only thing telling Abu whether Mira is still listening or has moved
     * on to working it out, so two of those states wearing one word would make it useless.
     */
    @Test
    fun `no two states of a turn are reported with the same word`() {
        listOf(english, bengali).forEach { words ->
            assertThat(
                listOf(
                    words.listening,
                    words.thinking,
                    words.carryingOut,
                    words.speaking,
                ),
            ).containsNoDuplicates()
        }
    }

    /**
     * A press on the mark means four different things, and it is the one control on the screen
     * with no glyph to explain itself — so TalkBack has to get four different sentences.
     */
    @Test
    fun `what a press on the mark would do is said four different ways`() {
        listOf(english, bengali).forEach { words ->
            assertThat(
                listOf(
                    words.speakToMira,
                    words.stopListening,
                    words.stopMira,
                    words.speechOff,
                ),
            ).containsNoDuplicates()
        }
    }

    @Test
    fun `every capability Mira can ask for is named in both languages, and none twice`() {
        MiraCapability.entries.forEach { capability ->
            assertThat(english.grant(capability)).isNotEmpty()
            assertThat(bengali.grant(capability)).isNotEmpty()
        }
        assertThat(MiraCapability.entries.map { english.grant(it) }).containsNoDuplicates()
        assertThat(MiraCapability.entries.map { bengali.grant(it) }).containsNoDuplicates()
    }

    /**
     * Confirming and cancelling sit side by side in the same card. They were never going to be
     * mistaken for each other in English; this is here so that stays true if either is reworded.
     */
    @Test
    fun `yes and no are not the same word`() {
        assertThat(english.confirmYes).isNotEqualTo(english.confirmNo)
        assertThat(bengali.confirmYes).isNotEqualTo(bengali.confirmNo)
    }

    /**
     * Every line, in a fixed order, so the two phrasebooks can be compared entry by entry. A line
     * added to [MiraWording] and not added here is caught by nothing, which is why this list and
     * the interface are deliberately kept in the same shape and order.
     */
    private fun MiraWording.everyLine(): List<String> = listOf(
        title,
        conversationHistory,
        settings,
        listening,
        thinking,
        carryingOut,
        speaking,
        microphoneOff,
        answeredOnDevice,
        answeredByGemini,
        listeningStopped,
        tapToSpeak,
        send,
        dismiss,
        tryAgain,
        allowMicrophone,
        openMicrophoneSettings,
        confirmYes,
        confirmNo,
        speakToMira,
        stopListening,
        stopMira,
        speechOff,
        openDialler,
        openMessage,
        openAppSettings,
    ) + MiraCapability.entries.map { grant(it) }
}
