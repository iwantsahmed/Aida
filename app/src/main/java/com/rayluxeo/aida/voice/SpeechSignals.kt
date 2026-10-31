package com.rayluxeo.aida.voice

import android.speech.SpeechRecognizer

/**
 * `onRmsChanged` is documented only as "more positive is louder", and in practice the recognizer
 * reports roughly -2 dB of room tone up to about 10 dB of clear speech. The waveform needs a
 * bounded 0f..1f, and an honest one: this maps that working range linearly and clamps, rather
 * than inventing motion that the microphone is not actually seeing.
 */
private const val RMS_FLOOR_DB = -2f
private const val RMS_CEILING_DB = 10f

/** The recognizer declined to score this result. Never a low score. */
internal const val NO_CONFIDENCE = -1f

internal fun normaliseMicLevel(rmsDb: Float): Float =
    ((rmsDb - RMS_FLOOR_DB) / (RMS_CEILING_DB - RMS_FLOOR_DB)).coerceIn(0f, 1f)

/**
 * The position of the hypothesis Mira will actually use, or -1 when there is nothing usable.
 *
 * The hypotheses and the confidence scores are two parallel arrays, and a blank leading hypothesis
 * — which some services emit — puts them out of step. Picking by index rather than by value is
 * what keeps a result's score its own rather than the one above it.
 */
internal fun topHypothesisIndex(hypotheses: List<String?>?): Int =
    hypotheses?.indexOfFirst { !it.isNullOrBlank() } ?: -1

/**
 * The recognizer's own confidence in the hypothesis at [index], or [NO_CONFIDENCE].
 *
 * `CONFIDENCE_SCORES` is optional and most recognizers never fill it. An absent score is no
 * opinion: reading it as low confidence would make Mira ask Abu to confirm every single thing he
 * says on a phone whose recognizer simply does not score.
 */
internal fun confidenceAt(scores: FloatArray?, index: Int): Float {
    if (scores == null || index !in scores.indices) return NO_CONFIDENCE
    val score = scores[index]
    return if (score.isNaN() || score < 0f) NO_CONFIDENCE else score.coerceAtMost(1f)
}

/**
 * Every `SpeechRecognizer.ERROR_*` constant through API 37, mapped onto [SpeechFailure].
 *
 * The distinction that matters to Mira is what Abu can do next. Retrying helps with a misheard or
 * busy recognizer; a language the device cannot serve is its own answer, because the way out of it
 * is to speak the other language or to type rather than to say the same thing again.
 */
internal fun speechFailureOf(error: Int): SpeechFailure = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH -> SpeechFailure.NO_MATCH
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.TIMEOUT
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.PERMISSION
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    -> SpeechFailure.NETWORK
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
    -> SpeechFailure.BUSY
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    -> SpeechFailure.LANGUAGE_UNAVAILABLE
    SpeechRecognizer.ERROR_AUDIO,
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
    SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS,
    -> SpeechFailure.UNAVAILABLE
    SpeechRecognizer.ERROR_CLIENT -> SpeechFailure.UNKNOWN
    else -> SpeechFailure.UNKNOWN
}
