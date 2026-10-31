package com.rayluxeo.aida.domain.model

import com.rayluxeo.aida.core.common.SpokenLanguage
import java.time.Instant

/**
 * A sum Mira has already worked out, kept so Abu can ask "what was that again".
 *
 * [resultText] is the answer exactly as it was given to him, not a number to be formatted a
 * second time. Re-deriving it later — from a different locale, a different rounding, a corrected
 * expression — would risk reading back an answer he was never told, which is worse than having
 * no history at all. The record is append-only for the same reason.
 */
data class CalculationRecord(
    val id: Long = 0L,
    val expression: String,
    val resultText: String,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val createdAt: Instant,
)
