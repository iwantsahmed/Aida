package com.rayluxeo.aida.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The live state of the optional cloud tier, in terms the app can show without knowing it is Gemini.
 *
 * It exists so the presentation layer can surface whether the free tier is clear or throttled
 * without importing the `ai` layer (§4): an adapter there maps the engine's real quota signals onto
 * this port. Every value reflects a signal the API actually returned — nothing counts requests or
 * guesses (§12).
 */
interface CloudAiStatusProvider {
    val quota: Flow<CloudQuotaStatus>
}

/** What the cloud free tier last told Mira. */
enum class CloudQuotaStatus {
    /** Callable — the resting state and the one a success restores. */
    AVAILABLE,

    /** A short, self-clearing per-minute throttle. */
    RATE_LIMITED,

    /** A hard daily free-tier ceiling was hit. */
    QUOTA_EXCEEDED,

    /** No clear signal either way. */
    UNKNOWN,
}
