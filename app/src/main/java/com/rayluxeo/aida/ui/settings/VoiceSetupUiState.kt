package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.voice.TtsEngineInfo
import com.rayluxeo.aida.voice.TtsVoiceInfo

/**
 * What Voice Setup knows.
 *
 * [voicesLoaded] exists so "this engine has no Bengali voice" is only ever said once the engine
 * has actually been asked. An empty list before the answer comes back is ignorance, not a fact,
 * and sending Abu off to install a voice he already has would be worse than saying nothing.
 *
 * Nothing here ranks, groups or labels a voice by gender. `android.speech.tts.Voice` does not
 * report one, so the rows carry only what the device actually said and Abu picks by ear.
 *
 * [language] is what every word on the screen is said in. It is held here rather than read in the
 * composable because the language toggle is one tap away on the settings screen this one is
 * reached from, and a screen that went on speaking English after Abu chose বাংলা would be the
 * defect the phrasebook exists to fix.
 */
data class VoiceSetupUiState(
    val loading: Boolean = true,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val engines: List<TtsEngineInfo> = emptyList(),
    val voicesLoading: Boolean = false,
    val voicesLoaded: Boolean = false,
    val englishVoices: List<TtsVoiceInfo> = emptyList(),
    val bengaliVoices: List<TtsVoiceInfo> = emptyList(),
    val preferences: VoicePreferences = VoicePreferences.DEFAULT,
    /** The voice speaking right now, so its own row can show that it is the one being heard. */
    val previewing: String? = null,
    /** One line above the content when something did not work. Never a raw exception. */
    val notice: String? = null,
) {
    val noEngines: Boolean get() = !loading && engines.isEmpty()

    val englishMissing: Boolean get() = voicesLoaded && !voicesLoading && englishVoices.isEmpty()

    val bengaliMissing: Boolean get() = voicesLoaded && !voicesLoading && bengaliVoices.isEmpty()

    fun voicesFor(language: SpokenLanguage): List<TtsVoiceInfo> = when (language) {
        SpokenLanguage.ENGLISH -> englishVoices
        SpokenLanguage.BENGALI -> bengaliVoices
    }

    /** Hearing the rate and pitch needs a voice to hear them in. */
    fun canPlaySample(language: SpokenLanguage): Boolean = preferences.voiceIdFor(language) != null
}
