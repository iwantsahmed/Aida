package com.rayluxeo.aida.ai.gemini

/**
 * What the Gemini free tier is telling Mira right now. Every value here reflects a real signal:
 * [Available] is the resting state and the one a successful call restores, while [RateLimited] and
 * [QuotaExceeded] are only ever set from an actual HTTP 429 the API returned. Nothing in Mira
 * counts requests or guesses — there is no usage meter behind this type.
 */
sealed interface GeminiQuotaState {
    /** Gemini may be called. The state a 200 response always returns Mira to. */
    data object Available : GeminiQuotaState

    /**
     * A short, self-clearing throttle (a per-minute 429). [resetTimeMs] is the wall-clock instant,
     * in epoch milliseconds, at or after which a call may be retried — derived from the API's
     * `RetryInfo` or the `Retry-After` header, never invented. Null means the window is unknown and
     * the state stays blocking until a later success clears it.
     */
    data class RateLimited(val resetTimeMs: Long? = null) : GeminiQuotaState

    /** A hard ceiling was hit (a daily free-tier quota). [message] is the API's own wording. */
    data class QuotaExceeded(val message: String) : GeminiQuotaState

    /** Gemini is off for a reason unrelated to quota. Reserved for callers that disable it explicitly. */
    data class Disabled(val reason: String) : GeminiQuotaState

    /**
     * Whether this is the resting [Available] state outright. This is the "is Gemini clear right
     * now, with no caveat" question — a rate-limit window that has simply elapsed is not yet
     * [Available] by this measure (it becomes so on the next availability check). For the
     * time-aware question the router asks, use [isUsableAt].
     */
    val isQuotaAvailable: Boolean
        get() = this is Available

    /**
     * Whether a call may be attempted at [nowMillis]. A [RateLimited] window whose reset instant has
     * passed clears itself here, so a per-minute throttle recovers on its own without needing a
     * further signal from the API.
     */
    fun isUsableAt(nowMillis: Long): Boolean = when (this) {
        is Available -> true
        is RateLimited -> resetTimeMs != null && nowMillis >= resetTimeMs
        is QuotaExceeded -> false
        is Disabled -> false
    }
}
