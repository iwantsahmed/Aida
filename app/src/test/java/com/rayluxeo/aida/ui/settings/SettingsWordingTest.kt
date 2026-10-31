package com.rayluxeo.aida.ui.settings

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.voice.TtsVoiceInfo
import org.junit.Test

/**
 * The Settings and Voice Setup phrasebooks, held to the rule they exist to keep.
 *
 * Abu reads one of these two languages, so a line written only in the other is not a cosmetic
 * gap — it is Mira telling him something about his own phone in a language he did not choose
 * (AGENTS.md §12). Settings is the worst place of all for that, because the control that picks the
 * language sits at the top of it.
 *
 * Both phrasebooks are interfaces, so a missing line cannot compile; what these tests catch is a
 * line that was added and left empty, or one copied across from the other language.
 */
class SettingsWordingTest {

    @Test
    fun `every settings line is written in both languages`() {
        SpokenLanguage.entries.forEach { language ->
            val words = settingsWording(language)
            assertThat(
                listOf(
                    words.title,
                    words.back,
                    words.dismiss,
                    words.language,
                    words.languageFootnote,
                    words.voice,
                    words.spokenResponses,
                    words.spokenResponsesDetail,
                    words.activationSound,
                    words.activationSoundDetail,
                    words.hapticFeedback,
                    words.hapticFeedbackDetail,
                    words.howMiraSounds,
                    words.assistant,
                    words.setMiraAsAssistant,
                    words.setMiraAsAssistantDetail,
                    words.reminders,
                    words.remindersMayBeLate,
                    words.remindersMayBeLateDetail,
                    words.allowExactAlarms,
                    words.gemini,
                    words.geminiOn,
                    words.geminiOff,
                    words.geminiDetail,
                    words.addKey,
                    words.replaceKey,
                    words.removeKey,
                    words.geminiKeyTitle,
                    words.geminiKeyDetail,
                    words.keyLabel,
                    words.saveKey,
                    words.savingKey,
                    words.conversation,
                    words.clearConversation,
                    words.clearConversationDetail,
                    words.clearConversationTitle,
                    words.clearConversationMessage,
                    words.clearIt,
                    words.keepIt,
                    words.about,
                    words.conversationNotCleared,
                    words.geminiKeyRefused,
                ),
            ).doesNotContain("")
        }
    }

    @Test
    fun `the voice summary says which languages have a voice, in both languages`() {
        SpokenLanguage.entries.forEach { language ->
            val words = settingsWording(language)

            assertThat(words.voiceSummary(VoicePreferences.DEFAULT)).isNotEmpty()

            val onlyEnglish = VoicePreferences.DEFAULT.copy(
                englishVoiceId = "en-us-x-a",
                setupComplete = true,
            )
            assertThat(words.voiceSummary(onlyEnglish)).contains("English")
            assertThat(words.voiceSummary(onlyEnglish)).doesNotContain("বাংলা")

            val both = onlyEnglish.copy(bengaliVoiceId = "bn-bd-x-a")
            assertThat(words.voiceSummary(both)).contains("বাংলা")
        }
    }

    /** A Latin digit in a Bengali line reads as a different voice, so the build number converts. */
    @Test
    fun `the about lines carry the version, in the numerals of the chosen language`() {
        assertThat(EnglishSettingsWording.versionLine("1.4.0")).contains("1.4.0")
        assertThat(EnglishSettingsWording.buildLine(7)).contains("7")
        assertThat(BengaliSettingsWording.versionLine("1.4.0")).contains("১.৪.০")
        assertThat(BengaliSettingsWording.buildLine(7)).contains("৭")
    }

    @Test
    fun `every voice setup line is written in both languages`() {
        SpokenLanguage.entries.forEach { language ->
            val words = voiceSetupWording(language)
            assertThat(
                listOf(
                    words.title,
                    words.subtitle,
                    words.back,
                    words.dismiss,
                    words.noEngines,
                    words.openTextToSpeechSettings,
                    words.intro,
                    words.engineSection,
                    words.systemDefault,
                    words.systemDefaultDetail,
                    words.englishVoices,
                    words.bengaliVoices,
                    words.englishMissing,
                    words.bengaliMissing,
                    words.speedAndPitch,
                    words.speechRate,
                    words.pitch,
                    words.hearEnglish,
                    words.hearBengali,
                    words.spokenReplies,
                    words.speakHerAnswers,
                    words.speakHerAnswersDetail,
                    words.previewFailed,
                    words.noTextToSpeechSettingsScreen,
                ),
            ).doesNotContain("")
        }
    }

    /**
     * A row may only repeat what the engine reported. Nothing here — in either language — may
     * imply a gender, which `android.speech.tts.Voice` does not expose (AGENTS.md §4.3).
     */
    @Test
    fun `a voice row reports only what the engine said`() {
        val voice = testVoice("bn-bd-x-abc", "bn-BD").copy(
            requiresNetwork = true,
            isInstalled = false,
        )

        val english = EnglishVoiceSetupWording.attributes(voice)
        assertThat(english).contains("bn-BD")
        assertThat(english).contains("needs the network")
        assertThat(english).contains("not downloaded yet")

        val bengali = BengaliVoiceSetupWording.attributes(voice)
        assertThat(bengali).contains("নেটওয়ার্ক লাগে")
        assertThat(bengali).contains("এখনও নামানো হয়নি")
    }

    @Test
    fun `a rate reads as a ratio, in the numerals of the chosen language`() {
        assertThat(EnglishVoiceSetupWording.multiplier(1.25f)).isEqualTo("1.3×")
        assertThat(BengaliVoiceSetupWording.multiplier(1.25f)).isEqualTo("১.৩×")
    }

    /** The locale tag is Android's own identifier and must survive numeral conversion unchanged. */
    @Test
    fun `an installed voice's locale tag is not rewritten past recognition`() {
        val voice: TtsVoiceInfo = testVoice("en-us-x-abc", "en-US")
        assertThat(EnglishVoiceSetupWording.attributes(voice)).startsWith("en-US")
        assertThat(BengaliVoiceSetupWording.attributes(voice)).startsWith("en-US")
    }
}
