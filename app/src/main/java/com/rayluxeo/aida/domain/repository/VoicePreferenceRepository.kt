package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.SpokenLanguage
import kotlinx.coroutines.flow.Flow

/**
 * How Mira sounds, as Abu set it.
 *
 * A port of its own rather than a corner of [SettingsRepository], because these seven values are
 * read together on every single spoken reply and are edited together on one screen. The voice
 * identifiers in particular are opaque strings from whichever engine produced them, so they are
 * only meaningful as a set — an English voice id from one engine paired with a Bengali id from
 * another is not a configuration that can speak.
 */
data class VoicePreferences(
    /** The TTS engine package Abu pinned, or null to follow the system default. */
    val engine: String? = null,
    /** Opaque `android.speech.tts.Voice.getName()` values. Never parsed, only matched. */
    val englishVoiceId: String? = null,
    val bengaliVoiceId: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val spokenResponsesEnabled: Boolean = true,
    val setupComplete: Boolean = false,
) {
    fun voiceIdFor(language: SpokenLanguage): String? = when (language) {
        SpokenLanguage.ENGLISH -> englishVoiceId
        SpokenLanguage.BENGALI -> bengaliVoiceId
    }

    companion object {
        val RATE_RANGE = 0.5f..2.0f
        val PITCH_RANGE = 0.5f..2.0f
        val DEFAULT = VoicePreferences()
    }
}

interface VoicePreferenceRepository {
    val preferences: Flow<VoicePreferences>

    suspend fun current(): VoicePreferences

    suspend fun setEngine(packageName: String?)

    suspend fun setVoiceId(language: SpokenLanguage, voiceId: String?)

    suspend fun setSpeechRate(rate: Float)

    suspend fun setPitch(pitch: Float)

    suspend fun setSpokenResponsesEnabled(enabled: Boolean)

    suspend fun setSetupComplete(complete: Boolean)
}
