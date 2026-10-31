package com.rayluxeo.aida.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.repository.VoicePreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * How Mira sounds, kept in the same preferences file Abu's choices were already written to.
 *
 * The keys in [MiraPreferenceKeys] predate this class — the voice configuration used to be a
 * field of `MiraSettings` — and they were kept exactly as they were so that the phone in Abu's
 * hand carries its voice, rate and pitch across the change. Nothing here migrates anything,
 * because there is nothing to migrate: the same seven keys, read by a different port.
 *
 * [preferences] is de-duplicated because it drives the TTS engine: the engine is rebound when the
 * chosen engine changes, and a DataStore emission caused by an unrelated key must not cost a
 * rebind.
 */
@Singleton
class VoicePreferenceRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : VoicePreferenceRepository {

    override val preferences: Flow<VoicePreferences> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toPreferences)
        .distinctUntilChanged()

    override suspend fun current(): VoicePreferences = withContext(dispatchers.io) { preferences.first() }

    override suspend fun setEngine(packageName: String?) = write {
        it.putOrRemove(MiraPreferenceKeys.VOICE_ENGINE, packageName?.takeIf(String::isNotBlank))
    }

    override suspend fun setVoiceId(language: SpokenLanguage, voiceId: String?) = write { stored ->
        val key = when (language) {
            SpokenLanguage.ENGLISH -> MiraPreferenceKeys.VOICE_ENGLISH_NAME
            SpokenLanguage.BENGALI -> MiraPreferenceKeys.VOICE_BENGALI_NAME
        }
        stored.putOrRemove(key, voiceId?.takeIf(String::isNotBlank))
    }

    /** Clamped on the way in, so a value outside the slider's range can never reach the engine. */
    override suspend fun setSpeechRate(rate: Float) = write {
        it[MiraPreferenceKeys.SPEECH_RATE] = rate.coerceIn(VoicePreferences.RATE_RANGE)
    }

    override suspend fun setPitch(pitch: Float) = write {
        it[MiraPreferenceKeys.VOICE_PITCH] = pitch.coerceIn(VoicePreferences.PITCH_RANGE)
    }

    override suspend fun setSpokenResponsesEnabled(enabled: Boolean) = write {
        it[MiraPreferenceKeys.SPOKEN_RESPONSES] = enabled
    }

    override suspend fun setSetupComplete(complete: Boolean) = write {
        it[MiraPreferenceKeys.VOICE_SETUP_COMPLETE] = complete
    }

    private suspend fun write(block: (MutablePreferences) -> Unit) {
        withContext(dispatchers.io) { store.edit { stored -> block(stored) } }
    }

    /**
     * Rate and pitch are clamped on the way out as well as in. A file written by an older build,
     * or hand-edited on a rooted phone, is still read into a value the engine can accept.
     */
    private fun toPreferences(stored: Preferences): VoicePreferences {
        val defaults = VoicePreferences.DEFAULT
        return VoicePreferences(
            engine = stored[MiraPreferenceKeys.VOICE_ENGINE],
            englishVoiceId = stored[MiraPreferenceKeys.VOICE_ENGLISH_NAME],
            bengaliVoiceId = stored[MiraPreferenceKeys.VOICE_BENGALI_NAME],
            speechRate = (stored[MiraPreferenceKeys.SPEECH_RATE] ?: defaults.speechRate)
                .coerceIn(VoicePreferences.RATE_RANGE),
            pitch = (stored[MiraPreferenceKeys.VOICE_PITCH] ?: defaults.pitch)
                .coerceIn(VoicePreferences.PITCH_RANGE),
            spokenResponsesEnabled = stored[MiraPreferenceKeys.SPOKEN_RESPONSES]
                ?: defaults.spokenResponsesEnabled,
            setupComplete = stored[MiraPreferenceKeys.VOICE_SETUP_COMPLETE] ?: defaults.setupComplete,
        )
    }

    /** A cleared voice leaves no key behind, so "never chosen" and "chosen then cleared" agree. */
    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else set(key, value)
    }
}
