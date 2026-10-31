package com.rayluxeo.aida.ai.gemini

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Turns a real HTTP 429 from the Gemini endpoint into a [GeminiQuotaState]. Kept separate from the
 * client so the mapping can be exercised without a network: everything it needs — the `Retry-After`
 * header, the response body and the current time — is passed in.
 *
 * It reads only Google's quota-error envelope (`RESOURCE_EXHAUSTED`, with `RetryInfo` and
 * `QuotaFailure` details); it never logs the body and never copies anything but Google's own quota
 * message into the state. Every access is tolerant, so a surprising shape degrades to a safe
 * default rather than throwing.
 */
internal object GeminiRateLimitParser {

    /**
     * @param nowMillis wall-clock now, so a relative retry delay becomes an absolute reset instant.
     * @param retryAfterHeader the HTTP `Retry-After` header, if any (integer seconds).
     * @param body the 429 response body, if it could be read; parsed leniently and never logged.
     */
    fun parse(nowMillis: Long, retryAfterHeader: String?, body: String?): GeminiQuotaState {
        val error = body
            ?.let { runCatching { lenient.parseToJsonElement(it) }.getOrNull() }
            ?.obj()?.get("error")?.obj()

        val message = error?.get("message")?.string()
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.length > MAX_MESSAGE_CHARS) it.take(MAX_MESSAGE_CHARS).trimEnd() + "…" else it }

        val details = (error?.get("details") as? JsonArray).orEmptyObjects()

        if (details.any { it.isQuotaFailure() && it.mentionsDailyQuota() }) {
            return GeminiQuotaState.QuotaExceeded(message ?: DEFAULT_QUOTA_MESSAGE)
        }

        val retryDelayMs = details.firstNotNullOfOrNull { it.retryDelayMillis() }
            ?: retryAfterHeader.parseRetryAfterSeconds()?.let { it * 1_000L }

        if (retryDelayMs != null) {
            return GeminiQuotaState.RateLimited(resetTimeMs = nowMillis + retryDelayMs.coerceAtLeast(0L))
        }

        // A quota failure with no retry hint is a ceiling we cannot time; anything else is a plain
        // throttle that clears after a conservative default.
        return if (details.any { it.isQuotaFailure() }) {
            GeminiQuotaState.QuotaExceeded(message ?: DEFAULT_QUOTA_MESSAGE)
        } else {
            GeminiQuotaState.RateLimited(resetTimeMs = nowMillis + DEFAULT_COOLDOWN_MS)
        }
    }

    private fun JsonObject.typeTag(): String = get("@type").string().orEmpty()

    private fun JsonObject.isQuotaFailure(): Boolean = typeTag().endsWith("QuotaFailure")

    /** True when a violation names a per-day limit, which does not self-clear within a session. */
    private fun JsonObject.mentionsDailyQuota(): Boolean =
        (get("violations") as? JsonArray).orEmptyObjects().any { violation ->
            val id = violation["quotaId"].string().orEmpty()
            val metric = violation["quotaMetric"].string().orEmpty()
            id.contains("PerDay", ignoreCase = true) || metric.contains("PerDay", ignoreCase = true)
        }

    /** `RetryInfo.retryDelay` arrives as a protobuf duration string such as "17s" or "1.5s". */
    private fun JsonObject.retryDelayMillis(): Long? {
        if (!typeTag().endsWith("RetryInfo")) return null
        val raw = get("retryDelay").string()?.trim()?.removeSuffix("s") ?: return null
        val seconds = raw.toDoubleOrNull() ?: return null
        return (seconds * 1_000.0).toLong()
    }

    private fun String?.parseRetryAfterSeconds(): Long? = this?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonArray?.orEmptyObjects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject }.orEmpty()

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A per-minute throttle with no stated delay: wait a short, bounded moment rather than hammer. */
    private const val DEFAULT_COOLDOWN_MS = 60_000L
    private const val MAX_MESSAGE_CHARS = 300
    private const val DEFAULT_QUOTA_MESSAGE = "Gemini free-tier quota reached."
}
