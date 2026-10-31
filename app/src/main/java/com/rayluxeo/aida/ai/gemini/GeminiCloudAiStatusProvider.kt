package com.rayluxeo.aida.ai.gemini

import com.rayluxeo.aida.domain.repository.CloudAiStatusProvider
import com.rayluxeo.aida.domain.repository.CloudQuotaStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Maps Gemini's real quota signal onto the layer-neutral [CloudAiStatusProvider], so Settings can
 * show the free-tier state without the UI knowing the cloud engine is Gemini (§4).
 */
@Singleton
class GeminiCloudAiStatusProvider @Inject constructor(
    private val availability: GeminiAvailabilityManager,
) : CloudAiStatusProvider {

    override val quota: Flow<CloudQuotaStatus> = availability.quotaState.map { it.toStatus() }
}

private fun GeminiQuotaState.toStatus(): CloudQuotaStatus = when (this) {
    is GeminiQuotaState.Available -> CloudQuotaStatus.AVAILABLE
    is GeminiQuotaState.RateLimited -> CloudQuotaStatus.RATE_LIMITED
    is GeminiQuotaState.QuotaExceeded -> CloudQuotaStatus.QUOTA_EXCEEDED
    is GeminiQuotaState.Disabled -> CloudQuotaStatus.UNKNOWN
}
