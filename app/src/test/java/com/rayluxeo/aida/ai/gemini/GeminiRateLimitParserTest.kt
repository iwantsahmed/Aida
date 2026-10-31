package com.rayluxeo.aida.ai.gemini

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The 429 mapping is a pure function of the header, the body and the clock, so it is tested
 * directly — no HTTP server needed. Every input here is a shape the real Gemini free tier returns.
 */
class GeminiRateLimitParserTest {

    private val now = 1_000_000L

    @Test
    fun `a per-minute RetryInfo delay becomes a rate limit with an absolute reset time`() {
        val body = """
            {
              "error": {
                "code": 429,
                "message": "Resource has been exhausted (e.g. check quota).",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.QuotaFailure",
                    "violations": [
                      { "quotaId": "GenerateRequestsPerMinutePerProjectPerModel-FreeTier" }
                    ]
                  },
                  { "@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "30s" }
                ]
              }
            }
        """.trimIndent()

        val state = GeminiRateLimitParser.parse(now, retryAfterHeader = null, body = body)

        assertThat(state).isEqualTo(GeminiQuotaState.RateLimited(resetTimeMs = now + 30_000))
    }

    @Test
    fun `a fractional RetryInfo delay is honoured to the millisecond`() {
        val body = """
            {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[
              {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"1.5s"}]}}
        """.trimIndent()

        val state = GeminiRateLimitParser.parse(now, retryAfterHeader = null, body = body)

        assertThat(state).isEqualTo(GeminiQuotaState.RateLimited(resetTimeMs = now + 1_500))
    }

    @Test
    fun `RetryInfo wins over the Retry-After header when both are present`() {
        val body = """
            {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[
              {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"30s"}]}}
        """.trimIndent()

        val state = GeminiRateLimitParser.parse(now, retryAfterHeader = "99", body = body)

        assertThat(state).isEqualTo(GeminiQuotaState.RateLimited(resetTimeMs = now + 30_000))
    }

    @Test
    fun `the Retry-After header is used when the body carries no delay`() {
        val state = GeminiRateLimitParser.parse(now, retryAfterHeader = "15", body = null)

        assertThat(state).isEqualTo(GeminiQuotaState.RateLimited(resetTimeMs = now + 15_000))
    }

    @Test
    fun `a per-day quota failure becomes an exceeded quota with the API's own message`() {
        val body = """
            {
              "error": {
                "code": 429,
                "message": "You exceeded your current quota. Daily limit reached.",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.QuotaFailure",
                    "violations": [
                      { "quotaId": "GenerateRequestsPerDayPerProjectPerModel-FreeTier" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val state = GeminiRateLimitParser.parse(now, retryAfterHeader = null, body = body)

        assertThat(state)
            .isEqualTo(GeminiQuotaState.QuotaExceeded("You exceeded your current quota. Daily limit reached."))
    }

    @Test
    fun `a per-day quota failure is a ceiling even when a retry delay is offered`() {
        val body = """
            {"error":{"code":429,"message":"Daily limit reached.","status":"RESOURCE_EXHAUSTED","details":[
              {"@type":"type.googleapis.com/google.rpc.QuotaFailure",
               "violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]},
              {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"42s"}]}}
        """.trimIndent()

        val state = GeminiRateLimitParser.parse(now, retryAfterHeader = null, body = body)

        assertThat(state).isEqualTo(GeminiQuotaState.QuotaExceeded("Daily limit reached."))
    }

    @Test
    fun `a quota failure with no retry hint falls back to an exceeded quota`() {
        val body = """
            {"error":{"code":429,"message":"Resource exhausted.","status":"RESOURCE_EXHAUSTED","details":[
              {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"OtherLimit"}]}]}}
        """.trimIndent()

        val state = GeminiRateLimitParser.parse(now, retryAfterHeader = null, body = body)

        assertThat(state).isEqualTo(GeminiQuotaState.QuotaExceeded("Resource exhausted."))
    }

    @Test
    fun `a bare or unreadable 429 falls back to a bounded default cooldown`() {
        val state = GeminiRateLimitParser.parse(now, retryAfterHeader = null, body = "not json at all")

        assertThat(state).isEqualTo(GeminiQuotaState.RateLimited(resetTimeMs = now + 60_000))
    }

    @Test
    fun `an exceeded quota with no message uses a calm default`() {
        val body = """
            {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[
              {"@type":"type.googleapis.com/google.rpc.QuotaFailure",
               "violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}
        """.trimIndent()

        val state = GeminiRateLimitParser.parse(now, retryAfterHeader = null, body = body)

        assertThat(state).isEqualTo(GeminiQuotaState.QuotaExceeded("Gemini free-tier quota reached."))
    }
}
