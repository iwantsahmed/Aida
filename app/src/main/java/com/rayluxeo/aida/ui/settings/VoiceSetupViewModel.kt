package com.rayluxeo.aida.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.ui.tasks.displayLanguage
import com.rayluxeo.aida.voice.VoicePersonaManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How Mira gets her voice.
 *
 * Choosing a voice and hearing it are one gesture here, not two. The only way to judge a voice is
 * to listen to it, so tapping a row both plays a sample in that voice and makes it the one Mira
 * uses — a decision that is free to change by tapping the next row.
 *
 * The preferences are read once and then held locally for as long as the screen is open, rather
 * than collected from the repository. A flow would fight the sliders: the stored value arrives
 * back mid-drag and snaps the thumb out from under Abu's finger. Every change made here is
 * written through immediately, so the two never actually disagree.
 *
 * Nothing in this class ranks, filters or labels a voice by gender. The Android TTS API does not
 * expose it and a voice name is an opaque engine identifier, so a guess would be wrong on a real
 * device. Do not add one.
 */
@HiltViewModel
class VoiceSetupViewModel @Inject constructor(
    private val personas: VoicePersonaManager,
    private val repository: VoicePreferenceRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceSetupUiState())
    val state: StateFlow<VoiceSetupUiState> = _state.asStateFlow()

    private var voicesJob: Job? = null
    private var previewJob: Job? = null

    init {
        // A read path, so it is observed rather than fetched once: Abu can change the language on
        // the settings screen this one is opened from and come straight back.
        viewModelScope.launch {
            settings.settings.collect { preferences ->
                _state.update { it.copy(language = preferences.languageMode.displayLanguage()) }
            }
        }
        viewModelScope.launch {
            val stored = repository.current()
            val engines = personas.engines()
            _state.update { it.copy(loading = false, engines = engines, preferences = stored) }
            loadVoices(stored.engine)
        }
    }

    /**
     * Pinning an engine drops both chosen voices and re-enumerates.
     *
     * A voice identifier only means something to the engine that reported it, so carrying one
     * across would leave Mira pointing at a voice that does not exist on the new engine — which
     * shows up not as an error but as silence.
     *
     * [enginePackage] is null for "whichever engine Android is set to use", which is a real
     * choice and not an absence of one.
     */
    fun selectEngine(enginePackage: String?) {
        val current = _state.value.preferences
        if (current.engine == enginePackage) return
        commit(
            updated = current.copy(
                engine = enginePackage,
                englishVoiceId = null,
                bengaliVoiceId = null,
            ),
        ) {
            it.setEngine(enginePackage)
            it.setVoiceId(SpokenLanguage.ENGLISH, null)
            it.setVoiceId(SpokenLanguage.BENGALI, null)
        }
        loadVoices(enginePackage)
    }

    /** Makes [voiceId] Mira's voice for [language] and plays it, because that is the same act. */
    fun chooseVoice(voiceId: String, language: SpokenLanguage) {
        val current = _state.value.preferences
        val updated = when (language) {
            SpokenLanguage.ENGLISH -> current.copy(englishVoiceId = voiceId)
            SpokenLanguage.BENGALI -> current.copy(bengaliVoiceId = voiceId)
        }
        commit(updated) { it.setVoiceId(language, voiceId) }
        preview(voiceId, language)
    }

    /**
     * Plays a sample without changing anything.
     *
     * A second tap interrupts the first rather than queueing behind it. Waiting through a voice
     * Abu has already rejected is the fastest way to make a listening flow feel slow.
     */
    fun preview(voiceId: String, language: SpokenLanguage) {
        previewJob?.cancel()
        val current = _state.value.preferences
        _state.update { it.copy(previewing = voiceId, notice = null) }
        previewJob = viewModelScope.launch {
            val result = personas.preview(
                enginePackage = current.engine,
                voiceName = voiceId,
                language = language,
                rate = current.speechRate,
                pitch = current.pitch,
            )
            _state.update {
                it.copy(
                    previewing = null,
                    // `it.language` is what Abu reads, not [language], which is the language the
                    // sample was spoken in: a Bengali voice that will not play still has to say
                    // so in English if that is what he chose.
                    notice = if (result is AppResult.Failure) {
                        voiceSetupWording(it.language).previewFailed
                    } else {
                        it.notice
                    },
                )
            }
        }
    }

    /** Plays the voice already chosen for [language] at the rate and pitch now on the sliders. */
    fun playSample(language: SpokenLanguage) {
        val voiceId = _state.value.preferences.voiceIdFor(language) ?: return
        preview(voiceId, language)
    }

    fun onSpeechRateChange(rate: Float) {
        _state.update {
            it.copy(
                preferences = it.preferences.copy(
                    speechRate = rate.coerceIn(VoicePreferences.RATE_RANGE),
                ),
            )
        }
    }

    fun onPitchChange(pitch: Float) {
        _state.update {
            it.copy(preferences = it.preferences.copy(pitch = pitch.coerceIn(VoicePreferences.PITCH_RANGE)))
        }
    }

    /** Sliders write once the thumb is let go, rather than on every pixel of the drag. */
    fun commitSpeechRate() {
        val current = _state.value.preferences
        commit(current) { it.setSpeechRate(current.speechRate) }
    }

    fun commitPitch() {
        val current = _state.value.preferences
        commit(current) { it.setPitch(current.pitch) }
    }

    /**
     * Whether Mira answers out loud at all. It lives here because this is the screen where Abu is
     * already deciding how she sounds, and an off switch buried elsewhere is how people end up
     * believing the voice is broken.
     */
    fun setSpokenResponsesEnabled(enabled: Boolean) {
        val current = _state.value.preferences
        if (current.spokenResponsesEnabled == enabled) return
        commit(current.copy(spokenResponsesEnabled = enabled)) {
            it.setSpokenResponsesEnabled(enabled)
        }
    }

    /** Android may have no screen to send Abu to. A silent no-op would hide that from him. */
    fun reportProblem(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /**
     * The audition engine holds a bound service for as long as it lives, so it goes when the
     * screen does. Nothing else in the app enumerates voices.
     */
    override fun onCleared() {
        personas.release()
    }

    private fun loadVoices(enginePackage: String?) {
        voicesJob?.cancel()
        _state.update { it.copy(voicesLoading = true, voicesLoaded = false) }
        voicesJob = viewModelScope.launch {
            val english = personas.voices(enginePackage, SpokenLanguage.ENGLISH)
            val bengali = personas.voices(enginePackage, SpokenLanguage.BENGALI)
            _state.update {
                it.copy(
                    voicesLoading = false,
                    voicesLoaded = true,
                    englishVoices = english,
                    bengaliVoices = bengali,
                )
            }
        }
    }

    /**
     * Writes a change through, and keeps `setupComplete` true exactly when Mira can speak.
     *
     * One language is enough: Mira can talk to Abu in English while a Bengali voice downloads,
     * and that is better than a setup flow that refuses to close. The flag is derived here rather
     * than set at each call site so it cannot be left true by an engine switch that emptied it.
     */
    private fun commit(
        updated: VoicePreferences,
        write: suspend (VoicePreferenceRepository) -> Unit,
    ) {
        val complete = updated.englishVoiceId != null || updated.bengaliVoiceId != null
        val settled = updated.copy(setupComplete = complete)
        val completionChanged = complete != _state.value.preferences.setupComplete
        _state.update { it.copy(preferences = settled) }
        viewModelScope.launch {
            write(repository)
            if (completionChanged) repository.setSetupComplete(complete)
        }
    }
}
