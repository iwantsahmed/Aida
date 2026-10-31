package com.rayluxeo.aida.ai.gemini

import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The single source of truth for whether the optional cloud engine may be called, and why not when
 * it may not.
 *
 * The quota side of that answer is genuine: [quotaState] only ever changes because the real Gemini
 * endpoint returned a 429 (set by [HttpGeminiClient]) or a later call succeeded. There is no
 * request counter and nothing is inferred — an absent signal means [GeminiQuotaState.Available].
 */
@Singleton
class GeminiAvailabilityManager @Inject constructor(
    private val credentials: GeminiCredentialStore,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityProbe,
    private val clock: MiraClock,
) {
    private val _quotaState = MutableStateFlow<GeminiQuotaState>(GeminiQuotaState.Available)

    /**
     * Observable quota state for the UI (Settings renders each case in Abu's language). It flips to
     * a blocking state the moment a 429 lands and back to [GeminiQuotaState.Available] on the next
     * success or when a rate-limit window elapses.
     */
    val quotaState: StateFlow<GeminiQuotaState> = _quotaState.asStateFlow()

    /** Records a real signal from the endpoint. The only writer of quota state. */
    fun updateQuotaState(state: GeminiQuotaState) {
        _quotaState.value = state
    }

    fun getQuotaState(): GeminiQuotaState {
        // Apply the same lazy expiry as isAvailable so this and the flow never disagree.
        quotaUsableNow()
        return _quotaState.value
    }

    suspend fun isAvailable(): Boolean = try {
        if (!quotaUsableNow()) false
        else settings.current().geminiEnabled && credentials.hasKey.first() && connectivity.isOnline()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        false
    }

    /**
     * Whether quota permits a call right now. An elapsed [GeminiQuotaState.RateLimited] window is
     * treated as recovered and the flow is flipped back to [GeminiQuotaState.Available] so observers
     * see the recovery rather than a stale throttle.
     */
    private fun quotaUsableNow(): Boolean {
        val state = _quotaState.value
        val usable = state.isUsableAt(clock.now().toEpochMilli())
        if (usable && state !is GeminiQuotaState.Available) {
            _quotaState.value = GeminiQuotaState.Available
        }
        return usable
    }

    fun fallbackMessage(): String = "Aida AI is unavailable. Local features still work."
}
