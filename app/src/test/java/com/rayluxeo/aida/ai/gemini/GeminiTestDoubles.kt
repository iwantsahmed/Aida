package com.rayluxeo.aida.ai.gemini

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written collaborators. Hilt's test bytecode transform does not run under AGP 9, so
 * nothing here is injected — every class under test is constructed directly.
 */

class FixedClock(
    private val at: Instant,
    private val timeZone: ZoneId,
) : MiraClock {
    override fun now(): Instant = at
    override fun zone(): ZoneId = timeZone
}

class FakeSettingsRepository(initial: MiraSettings) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<MiraSettings> = state

    override suspend fun current(): MiraSettings = state.value

    override suspend fun setLanguageMode(mode: LanguageMode) {
        state.value = state.value.copy(languageMode = mode)
    }

    override suspend fun setActivationSound(enabled: Boolean) {
        state.value = state.value.copy(activationSound = enabled)
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        state.value = state.value.copy(hapticFeedback = enabled)
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        state.value = state.value.copy(onboardingComplete = complete)
    }

}

class FakeGeminiCredentialStore(key: String?) : GeminiCredentialStore {

    private val state = MutableStateFlow(key)

    override val hasKey: Flow<Boolean> = state.map { !it.isNullOrBlank() }

    override suspend fun apiKey(): String? = state.value

    override suspend fun setApiKey(key: String?) {
        state.value = key
    }
}

class FakeConnectivityProbe(private val online: Boolean) : ConnectivityProbe {
    override fun isOnline(): Boolean = online
}

/** Records what it was asked so a test can prove the network is never touched by a cheap check. */
class FakeGeminiClient(private val reply: AppResult<String>) : GeminiClient {

    var calls: Int = 0
        private set

    var lastSystemInstruction: String? = null
        private set

    var lastUtterance: String? = null
        private set

    override suspend fun generate(
        apiKey: String,
        systemInstruction: String,
        utterance: String,
    ): AppResult<String> {
        calls++
        lastSystemInstruction = systemInstruction
        lastUtterance = utterance
        return reply
    }
}
