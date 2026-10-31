package com.rayluxeo.aida.voice

import android.speech.SpeechRecognizer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The waveform, the error surface and the confidence score are the three places where a careless
 * mapping quietly lies to Abu: a level that never moves, a real failure reported as "I didn't
 * catch that", or a recognizer that does not score being read as one that is unsure.
 */
class SpeechSignalsTest {

    @Test
    fun `mic level spans the recognizer's working range`() {
        assertThat(normaliseMicLevel(-2f)).isEqualTo(0f)
        assertThat(normaliseMicLevel(4f)).isWithin(0.001f).of(0.5f)
        assertThat(normaliseMicLevel(10f)).isEqualTo(1f)
    }

    @Test
    fun `mic level clamps instead of overshooting the waveform`() {
        assertThat(normaliseMicLevel(-120f)).isEqualTo(0f)
        assertThat(normaliseMicLevel(96f)).isEqualTo(1f)
    }

    @Test
    fun `silence and speech produce different levels`() {
        assertThat(normaliseMicLevel(0f)).isLessThan(normaliseMicLevel(6f))
    }

    @Test
    fun `no match and speech timeout stay distinguishable`() {
        assertThat(speechFailureOf(SpeechRecognizer.ERROR_NO_MATCH)).isEqualTo(SpeechFailure.NO_MATCH)
        assertThat(speechFailureOf(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)).isEqualTo(SpeechFailure.TIMEOUT)
    }

    @Test
    fun `a missing permission is never reported as something Abu could fix by speaking again`() {
        assertThat(speechFailureOf(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
            .isEqualTo(SpeechFailure.PERMISSION)
    }

    @Test
    fun `every network failure maps to NETWORK`() {
        val network = listOf(
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        )

        assertThat(network.map(::speechFailureOf).toSet()).containsExactly(SpeechFailure.NETWORK)
    }

    @Test
    fun `a busy or throttled recognizer is reported as BUSY`() {
        val busy = listOf(
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
        )

        assertThat(busy.map(::speechFailureOf).toSet()).containsExactly(SpeechFailure.BUSY)
    }

    @Test
    fun `a language the device cannot serve is its own answer`() {
        val language = listOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        )

        assertThat(language.map(::speechFailureOf).toSet())
            .containsExactly(SpeechFailure.LANGUAGE_UNAVAILABLE)
    }

    @Test
    fun `a broken audio path or an unanswerable service is reported as UNAVAILABLE`() {
        val unavailable = listOf(
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
            SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS,
        )

        assertThat(unavailable.map(::speechFailureOf).toSet()).containsExactly(SpeechFailure.UNAVAILABLE)
    }

    @Test
    fun `only the failures Abu can fix by speaking again are retryable`() {
        val retryable = SpeechFailure.entries.filter { it.isRetryable }

        assertThat(retryable).containsExactly(
            SpeechFailure.NO_MATCH,
            SpeechFailure.TIMEOUT,
            SpeechFailure.NETWORK,
            SpeechFailure.BUSY,
        )
    }

    @Test
    fun `an error code this Android version never documented still becomes a value`() {
        assertThat(speechFailureOf(Int.MAX_VALUE)).isEqualTo(SpeechFailure.UNKNOWN)
    }

    @Test
    fun `no documented error code is left unmapped`() {
        // ERROR_NETWORK_TIMEOUT (1) through ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS (15) is the
        // whole documented range up to API 37. Only ERROR_CLIENT is genuinely unclassifiable.
        val unclassified = (1..15).filter { speechFailureOf(it) == SpeechFailure.UNKNOWN }

        assertThat(unclassified).containsExactly(SpeechRecognizer.ERROR_CLIENT)
    }

    @Test
    fun `the first usable hypothesis is the one Mira takes`() {
        assertThat(topHypothesisIndex(listOf("kal shokal dosh tay", "kal shokal dosh ta"))).isEqualTo(0)
    }

    @Test
    fun `a blank leading hypothesis is skipped rather than answered with`() {
        assertThat(topHypothesisIndex(listOf("", "   ", "remind me at ten"))).isEqualTo(2)
    }

    @Test
    fun `nothing usable is reported as nothing rather than as an empty answer`() {
        assertThat(topHypothesisIndex(null)).isEqualTo(-1)
        assertThat(topHypothesisIndex(emptyList())).isEqualTo(-1)
        assertThat(topHypothesisIndex(listOf(null, "", "  "))).isEqualTo(-1)
    }

    @Test
    fun `a score belongs to the hypothesis it was read beside`() {
        val scores = floatArrayOf(0.2f, 0.9f)

        assertThat(confidenceAt(scores, 0)).isEqualTo(0.2f)
        assertThat(confidenceAt(scores, 1)).isEqualTo(0.9f)
    }

    @Test
    fun `a recognizer that does not score is no opinion, never low confidence`() {
        assertThat(confidenceAt(null, 0)).isEqualTo(NO_CONFIDENCE)
        assertThat(confidenceAt(floatArrayOf(), 0)).isEqualTo(NO_CONFIDENCE)
        assertThat(confidenceAt(floatArrayOf(0.8f), 3)).isEqualTo(NO_CONFIDENCE)
        assertThat(confidenceAt(floatArrayOf(0.8f), -1)).isEqualTo(NO_CONFIDENCE)
        assertThat(NO_CONFIDENCE).isLessThan(0f)
    }

    @Test
    fun `a score outside 0 to 1 is not passed on as if it meant something`() {
        assertThat(confidenceAt(floatArrayOf(-1f), 0)).isEqualTo(NO_CONFIDENCE)
        assertThat(confidenceAt(floatArrayOf(Float.NaN), 0)).isEqualTo(NO_CONFIDENCE)
        assertThat(confidenceAt(floatArrayOf(4f), 0)).isEqualTo(1f)
    }
}
