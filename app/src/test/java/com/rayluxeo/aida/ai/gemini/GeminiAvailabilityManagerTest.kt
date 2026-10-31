package com.rayluxeo.aida.ai.gemini

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.repository.MiraSettings
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Hilt's local-test transform does not run under AGP 9, so the manager is built by hand with the
 * same fakes the engine test uses. These prove the quota state is genuine and observable, not the
 * inert constant it replaced.
 */
class GeminiAvailabilityManagerTest {

    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val nowMillis = now.toEpochMilli()
    private val clock = FixedClock(at = now, timeZone = ZoneId.of("Asia/Dhaka"))

    @Test
    fun `quota state starts available`() {
        assertThat(manager().getQuotaState()).isEqualTo(GeminiQuotaState.Available)
        assertThat(manager().quotaState.value).isEqualTo(GeminiQuotaState.Available)
    }

    @Test
    fun `updateQuotaState publishes to the observable flow`() {
        val manager = manager()
        val limited = GeminiQuotaState.RateLimited(resetTimeMs = nowMillis + 30_000)

        manager.updateQuotaState(limited)

        assertThat(manager.quotaState.value).isEqualTo(limited)
        assertThat(manager.getQuotaState()).isEqualTo(limited)
    }

    @Test
    fun `everything configured and available means available`() = runTest {
        assertThat(manager().isAvailable()).isTrue()
    }

    @Test
    fun `a future rate limit makes Gemini unavailable`() = runTest {
        val manager = manager()
        manager.updateQuotaState(GeminiQuotaState.RateLimited(resetTimeMs = nowMillis + 60_000))

        assertThat(manager.isAvailable()).isFalse()
    }

    @Test
    fun `a rate limit with no known reset stays blocking`() = runTest {
        val manager = manager()
        manager.updateQuotaState(GeminiQuotaState.RateLimited(resetTimeMs = null))

        assertThat(manager.isAvailable()).isFalse()
    }

    @Test
    fun `an exceeded quota makes Gemini unavailable`() = runTest {
        val manager = manager()
        manager.updateQuotaState(GeminiQuotaState.QuotaExceeded("daily limit reached"))

        assertThat(manager.isAvailable()).isFalse()
    }

    @Test
    fun `a disabled quota state makes Gemini unavailable`() = runTest {
        val manager = manager()
        manager.updateQuotaState(GeminiQuotaState.Disabled("off"))

        assertThat(manager.isAvailable()).isFalse()
    }

    @Test
    fun `an elapsed rate limit recovers on its own and the flow flips back to available`() = runTest {
        val manager = manager()
        manager.updateQuotaState(GeminiQuotaState.RateLimited(resetTimeMs = nowMillis - 1_000))

        assertThat(manager.isAvailable()).isTrue()
        assertThat(manager.quotaState.value).isEqualTo(GeminiQuotaState.Available)
    }

    @Test
    fun `quota is checked before the cheaper settings and connectivity reads`() = runTest {
        // Even with everything else switched off, an exceeded quota is reason enough to say no,
        // and the quota branch must not throw regardless of configuration.
        val manager = manager(enabled = false, apiKey = null, online = false)
        manager.updateQuotaState(GeminiQuotaState.QuotaExceeded("daily limit reached"))

        assertThat(manager.isAvailable()).isFalse()
    }

    private fun manager(
        enabled: Boolean = true,
        apiKey: String? = "test-key",
        online: Boolean = true,
    ): GeminiAvailabilityManager = GeminiAvailabilityManager(
        credentials = FakeGeminiCredentialStore(apiKey),
        settings = FakeSettingsRepository(MiraSettings(geminiEnabled = enabled)),
        connectivity = FakeConnectivityProbe(online),
        clock = clock,
    )
}
