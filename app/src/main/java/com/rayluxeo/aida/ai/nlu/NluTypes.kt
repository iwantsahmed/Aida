package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.domain.model.Money
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the pieces of the understanding pipeline hand to each other.
 *
 * Every parser here follows the same shape: it returns what it recognised, the span it consumed,
 * and the text that is left once that span is taken out. The remainder matters as much as the
 * value — "kal shokal 10 tay Ahmed ke call korar reminder dao" only yields the title "call Ahmed"
 * because each parser removes exactly what it claimed and passes the rest on.
 */

/** Which language an utterance is in, and how the letters were actually written. */
data class LanguageHint(
    val language: SpokenLanguage,
    val script: TextScript,
    /** 0f..1f. Low when the evidence was thin — a bare "ok" carries almost none. */
    val confidence: Float,
)

/** A number recognised in any of the four ways Abu writes them. */
data class ParsedNumber(
    val value: BigDecimal,
    /** The input with the number expression removed. */
    val remainder: String,
    val matched: String,
)

/** An amount of money, with the currency it was stated in. */
data class ParsedMoney(
    val money: Money,
    val remainder: String,
    val matched: String,
    /** False when no currency word was present and BDT was assumed from Abu's default. */
    val currencyWasExplicit: Boolean,
)

/** A day, with no time of day attached. */
data class ParsedDate(
    val date: LocalDate,
    val remainder: String,
    val matched: String,
    val confidence: Float,
)

/** A time of day, with no date attached. */
data class ParsedTime(
    val time: LocalTime,
    val remainder: String,
    val matched: String,
    /**
     * True when Abu said something that fixes morning or evening — "সকাল", "pm", "raat".
     * A bare "10" is ambiguous and the caller decides which 10 he meant.
     */
    val meridiemWasExplicit: Boolean,
)

/** A span of time, for timers: "20 minute", "in half an hour", "আধা ঘণ্টা". */
data class ParsedDuration(
    val duration: Duration,
    val remainder: String,
    val matched: String,
)
