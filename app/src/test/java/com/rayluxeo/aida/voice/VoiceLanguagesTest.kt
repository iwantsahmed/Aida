package com.rayluxeo.aida.voice

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import java.util.Locale
import org.junit.Test

/**
 * Engines disagree about how to spell a locale, and comparing tags as strings silently loses
 * real voices — which on a device looks exactly like "Mira has no Bengali voice".
 */
class VoiceLanguagesTest {

    @Test
    fun `the documented Bengali tag matches`() {
        assertThat(matchesSpokenLanguage(Locale.forLanguageTag("bn-BD"), SpokenLanguage.BENGALI)).isTrue()
    }

    @Test
    fun `a Bengali voice from another country still speaks Bengali`() {
        assertThat(matchesSpokenLanguage(Locale.forLanguageTag("bn-IN"), SpokenLanguage.BENGALI)).isTrue()
    }

    @Test
    fun `a language-only tag matches`() {
        assertThat(matchesSpokenLanguage(Locale.forLanguageTag("bn"), SpokenLanguage.BENGALI)).isTrue()
        assertThat(matchesSpokenLanguage(Locale.forLanguageTag("en"), SpokenLanguage.ENGLISH)).isTrue()
    }

    @Test
    fun `the three-letter form TTS engines report matches too`() {
        assertThat(matchesSpokenLanguage(Locale.forLanguageTag("ben"), SpokenLanguage.BENGALI)).isTrue()
        assertThat(matchesSpokenLanguage(Locale.forLanguageTag("eng"), SpokenLanguage.ENGLISH)).isTrue()
    }

    @Test
    fun `underscore separated tags from engines are accepted`() {
        assertThat(matchesSpokenLanguage("bn_BD", SpokenLanguage.BENGALI)).isTrue()
        assertThat(matchesSpokenLanguage("en_US", SpokenLanguage.ENGLISH)).isTrue()
    }

    @Test
    fun `a different language never matches`() {
        assertThat(matchesSpokenLanguage("en-GB", SpokenLanguage.BENGALI)).isFalse()
        assertThat(matchesSpokenLanguage("bn-BD", SpokenLanguage.ENGLISH)).isFalse()
        assertThat(matchesSpokenLanguage("fr-FR", SpokenLanguage.ENGLISH)).isFalse()
        assertThat(matchesSpokenLanguage("hi-IN", SpokenLanguage.BENGALI)).isFalse()
    }

    @Test
    fun `an empty or unparseable tag matches nothing`() {
        assertThat(matchesSpokenLanguage("", SpokenLanguage.ENGLISH)).isFalse()
        assertThat(matchesSpokenLanguage("   ", SpokenLanguage.BENGALI)).isFalse()
    }

    @Test
    fun `voices of one locale get labels that actually tell them apart`() {
        val names = listOf("en-us-x-tpf-local", "en-us-x-sfg-local", "en-us-x-iom-network")

        val labels = voiceLabels(names, SpokenLanguage.ENGLISH)

        assertThat(labels.keys).containsExactlyElementsIn(names)
        assertThat(labels.values.toSet()).hasSize(names.size)
        assertThat(labels.values).containsNoDuplicates()
    }

    @Test
    fun `a label keeps the readable locale and drops the prefix every voice repeats`() {
        val labels = voiceLabels(listOf("en-us-x-tpf-local", "en-us-x-sfg-local"), SpokenLanguage.ENGLISH)

        val label = labels.getValue("en-us-x-tpf-local")
        assertThat(label).contains("English")
        assertThat(label).contains("tpf")
        assertThat(label).doesNotContain("en-us-x-")
    }

    @Test
    fun `a lone voice is labelled by its locale alone`() {
        val labels = voiceLabels(listOf("bn-bd-x-ban-local"), SpokenLanguage.BENGALI)

        assertThat(labels.getValue("bn-bd-x-ban-local"))
            .isEqualTo(SpokenLanguage.BENGALI.locale.getDisplayName(Locale.ENGLISH))
    }

    @Test
    fun `a chosen language is the one the recognizer is told to expect`() {
        assertThat(resolveRecognitionLanguage(LanguageMode.BANGLA)).isEqualTo(SpokenLanguage.BENGALI)
        assertThat(resolveRecognitionLanguage(LanguageMode.ENGLISH)).isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `AUTO names no language, so the device is free to hear either`() {
        assertThat(resolveRecognitionLanguage(LanguageMode.AUTO)).isNull()
    }

    @Test
    fun `the tags handed to the recognizer are exactly the languages Mira understands`() {
        assertThat(RECOGNITION_LANGUAGE_TAGS).containsExactly("bn-BD", "en-US")
        assertThat(RECOGNITION_LANGUAGES)
            .containsExactly(SpokenLanguage.BENGALI, SpokenLanguage.ENGLISH)
    }

    @Test
    fun `a service's own spelling of a language still counts as support for it`() {
        val reported = listOf("bn_BD", "en-GB", "fr-FR", "de-DE")

        assertThat(supportedLanguagesOf(reported))
            .containsExactly(SpokenLanguage.BENGALI, SpokenLanguage.ENGLISH)
    }

    @Test
    fun `a device that serves only one of Mira's languages is reported as serving only that one`() {
        assertThat(supportedLanguagesOf(listOf("en-US", "es-ES", "hi-IN")))
            .containsExactly(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `an empty or unreadable list claims support for nothing`() {
        assertThat(supportedLanguagesOf(emptyList())).isEmpty()
        assertThat(supportedLanguagesOf(listOf("", "  "))).isEmpty()
    }

    @Test
    fun `a voice is named from its own locale, not from the section it sits in`() {
        val locales = mapOf(
            "en-us-x-iob-local" to Locale.forLanguageTag("en-US"),
            "en-gb-x-gba-local" to Locale.forLanguageTag("en-GB"),
            "en-in-x-ena-local" to Locale.forLanguageTag("en-IN"),
        )
        val labels = voiceLabels(locales.keys.toList(), SpokenLanguage.ENGLISH) { locales[it] }

        // All three are filed under English. Only the first is American, and the row Abu reads
        // before tapping has to say so.
        assertThat(labels.getValue("en-us-x-iob-local")).contains("United States")
        assertThat(labels.getValue("en-gb-x-gba-local")).contains("United Kingdom")
        assertThat(labels.getValue("en-in-x-ena-local")).contains("India")
    }

    @Test
    fun `a voice whose locale is unknown falls back to the section language`() {
        val labels = voiceLabels(listOf("mystery-a", "mystery-b"), SpokenLanguage.BENGALI)

        assertThat(labels.getValue("mystery-a")).contains("Bangla")
    }
}
