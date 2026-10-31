package com.rayluxeo.aida.ai.gemini

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * One call to Gemini. Behind an interface so the engine's rules can be exercised without a
 * network, and so nothing above this line knows how the request is shaped.
 */
interface GeminiClient {
    /**
     * Returns the model's raw JSON command object. Never throws: transport trouble, an HTTP
     * error, a timeout or an unreadable reply all come back as a failure.
     */
    suspend fun generate(apiKey: String, systemInstruction: String, utterance: String): AppResult<String>
}

/**
 * The free-tier Gemini endpoint over plain [HttpURLConnection] — no SDK, no billing account.
 *
 * The key travels in the `x-goog-api-key` header rather than the query string so it cannot end
 * up in a proxy log or a crash report, and nothing in this file logs the key, the prompt or the
 * response body.
 *
 * It is also where Mira learns the free tier's real quota state: a 429 updates
 * [GeminiAvailabilityManager] with the genuine [GeminiQuotaState] the API reported, and every
 * success clears it back to [GeminiQuotaState.Available]. No usage is counted or guessed.
 */
@Singleton
class HttpGeminiClient @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val availabilityManager: GeminiAvailabilityManager,
    private val clock: MiraClock,
) : GeminiClient {

    override suspend fun generate(
        apiKey: String,
        systemInstruction: String,
        utterance: String,
    ): AppResult<String> = withContext(dispatchers.io) {
        var lastTransientStatus = 0
        repeat(MAX_ATTEMPTS) { attemptIndex ->
            if (attemptIndex > 0) {
                // A 503 is a capacity signal, not a usage one: the free tier is momentarily
                // overloaded, and Google's own guidance is to retry with backoff. This path is only
                // reached after a LOCAL miss, so the alternative to a short wait is telling Abu
                // nothing at all — the backoff is spent only on the cloud fallback, never in front of
                // an answer Mira already has. It stays small and bounded so she never looks hung.
                delay(BACKOFF_MS shl (attemptIndex - 1))
            }
            when (val outcome = attempt(apiKey, systemInstruction, utterance)) {
                is Attempt.Settled -> return@withContext outcome.result
                is Attempt.Transient -> lastTransientStatus = outcome.status
            }
        }
        failure(
            MiraErrorCode.ENGINE_UNAVAILABLE,
            "Gemini stayed overloaded after $MAX_ATTEMPTS attempts (HTTP $lastTransientStatus)",
        )
    }

    /**
     * One request/response cycle. A transient server error (5xx) comes back as [Attempt.Transient]
     * so [generate] can retry it; a success, a 4xx, a 429 or a transport failure is [Attempt.Settled]
     * and ends the call. The quota side effects of a 429 and a 200 happen here, once per real attempt.
     */
    private suspend fun attempt(
        apiKey: String,
        systemInstruction: String,
        utterance: String,
    ): Attempt {
        var connection: HttpURLConnection? = null
        try {
            val body = GeminiJson.forRequests
                .encodeToString(GenerateContentRequest.serializer(), requestFor(systemInstruction, utterance))
                .toByteArray(Charsets.UTF_8)

            val http = (URI(ENDPOINT).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
                setFixedLengthStreamingMode(body.size)
            }
            connection = http
            http.outputStream.use { it.write(body) }

            val status = http.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                if (status == HTTP_TOO_MANY_REQUESTS) {
                    // A 429 carries a quota envelope, not an echo of the prompt, so it is read
                    // (bounded) to learn the real reset time — parsed in memory, never logged.
                    val retryAfter = http.getHeaderField("Retry-After")
                    val errorBody = http.errorStream?.readBounded(MAX_ERROR_BODY_CHARS)
                    availabilityManager.updateQuotaState(
                        GeminiRateLimitParser.parse(clock.now().toEpochMilli(), retryAfter, errorBody),
                    )
                } else {
                    // Any other error body can quote the prompt back, so it is closed unread.
                    http.errorStream?.close()
                }
                return if (status in TRANSIENT_STATUSES) {
                    Attempt.Transient(status)
                } else {
                    Attempt.Settled(
                        failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Gemini answered with HTTP $status"),
                    )
                }
            }

            // A 200 is proof the free tier is serving Mira right now; clear any stale 429 state.
            availabilityManager.updateQuotaState(GeminiQuotaState.Available)

            val payload = http.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = GeminiJson.forResponses.decodeFromString(GenerateContentResponse.serializer(), payload)
            val text = response.candidates.firstOrNull()
                ?.content
                ?.parts
                ?.joinToString(separator = "") { it.text }
                ?.trim()

            return if (text.isNullOrEmpty()) {
                Attempt.Settled(failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Gemini returned no usable candidate"))
            } else {
                Attempt.Settled(text.asSuccess())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            // A transport failure already cost a connect/read timeout; retrying would only make Mira
            // look hung, so the local verdict takes over now.
            return Attempt.Settled(failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Gemini could not be reached", error))
        } catch (error: SerializationException) {
            return Attempt.Settled(
                failure(MiraErrorCode.ENGINE_UNAVAILABLE, "Gemini sent a reply Aida could not read", error),
            )
        } catch (error: Exception) {
            // attempt() promises never to throw, and HttpURLConnection has unchecked failure modes of its own.
            return Attempt.Settled(failure(MiraErrorCode.ENGINE_UNAVAILABLE, "The Gemini call failed unexpectedly", error))
        } finally {
            connection?.disconnect()
        }
    }

    /** The result of a single [attempt]: either the call is settled, or a 5xx asked to retry. */
    private sealed interface Attempt {
        data class Settled(val result: AppResult<String>) : Attempt
        data class Transient(val status: Int) : Attempt
    }

    /** Reads at most [limit] characters from an error stream and closes it. Null when it is empty. */
    private fun InputStream.readBounded(limit: Int): String? = use { stream ->
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(limit)
            val read = reader.read(buffer, 0, buffer.size)
            if (read > 0) String(buffer, 0, read) else null
        }
    }

    private fun requestFor(systemInstruction: String, utterance: String) = GenerateContentRequest(
        systemInstruction = Content(parts = listOf(Part(systemInstruction))),
        contents = listOf(Content(parts = listOf(Part(utterance)), role = "user")),
        generationConfig = GenerationConfig(
            responseMimeType = "application/json",
            responseSchema = MiraCommandSchema.json,
            temperature = 0.0,
            candidateCount = 1,
            maxOutputTokens = MAX_OUTPUT_TOKENS,
        ),
    )

    private companion object {
        const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent"

        /** [HttpURLConnection] has no constant for 429, the free tier's quota/rate-limit status. */
        const val HTTP_TOO_MANY_REQUESTS = 429

        /**
         * A 5xx from Gemini is a momentary capacity problem on Google's side, not a problem with the
         * key, the model or Abu's quota. The free-tier Flash models return 503 ("model overloaded")
         * in bursts, and Google's own guidance is to retry these with backoff rather than give up.
         */
        val TRANSIENT_STATUSES = setOf(500, 502, 503, 504)

        /** A handful of tries turns a passing overload into an answer; beyond that, the local verdict wins. */
        const val MAX_ATTEMPTS = 3

        /** Backoff before retry n is BACKOFF_MS shl (n-1): 450ms then 900ms — bounded, so Mira never hangs. */
        const val BACKOFF_MS = 450L

        // Connect stays short — an unreachable network should fail fast to the local answer. The read
        // budget is deliberately longer: the cloud engine is only ever reached AFTER the local engine
        // already missed, so there is no good answer waiting to be delayed, and giving up early just
        // turns a slow-but-real reply into "I didn't catch that". The free-tier Flash-Lite model does
        // a minimum of thinking that cannot be switched off on the 3.x line (thinkingBudget 0 is a 400
        // there), and its replies land around 7-9s, so an 8s read budget timed out as often as it
        // succeeded. 15s clears the real spread with margin while still bounding the wait.
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 15_000

        /** One small command object. Anything longer is the model ignoring the schema. */
        const val MAX_OUTPUT_TOKENS = 512

        /** A quota envelope is small; cap the read so a hostile body cannot balloon memory. */
        const val MAX_ERROR_BODY_CHARS = 8_192
    }
}
