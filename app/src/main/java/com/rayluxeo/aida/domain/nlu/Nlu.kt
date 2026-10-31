package com.rayluxeo.aida.domain.nlu

import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import java.time.Instant

/**
 * The canonical form of an utterance. [normalized] is lowercase, Latin-script, ASCII-digit
 * text with Banglish spelling variants folded to one spelling, so the intent matcher sees
 * the same string whether Abu wrote "কল", "call", "kol" or "kall".
 */
data class NormalizedText(
    val raw: String,
    val normalized: String,
    val language: SpokenLanguage,
    val script: TextScript,
)

interface TextNormalizer {
    fun normalize(raw: String, mode: LanguageMode): NormalizedText
}

/**
 * A recognised date/time expression, plus the input with that expression stripped out so
 * the remainder can be used as a task or reminder title.
 */
data class TemporalMatch(
    val instant: Instant,
    val hasExplicitTime: Boolean,
    val remainder: String,
    val confidence: Float,
)

/** Parses relative and absolute times in English, Bengali and Banglish against an injected clock. */
interface DateTimeParser {
    fun parse(normalized: String): TemporalMatch?
}
