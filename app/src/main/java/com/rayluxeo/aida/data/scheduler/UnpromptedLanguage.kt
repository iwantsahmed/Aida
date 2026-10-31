package com.rayluxeo.aida.data.scheduler

import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * Which language Mira uses when she speaks first.
 *
 * An alarm carries no utterance, so `AUTO` has no script to read and falls back to English the way
 * Mira's other unprompted surfaces do. It lives here rather than inside one receiver because both
 * of them wake on a timer with nothing of Abu's to go on, and two copies of this rule would drift.
 */
internal fun unpromptedLanguage(mode: LanguageMode): SpokenLanguage = when (mode) {
    LanguageMode.BANGLA -> SpokenLanguage.BENGALI
    LanguageMode.ENGLISH -> SpokenLanguage.ENGLISH
    LanguageMode.AUTO -> SpokenLanguage.ENGLISH
}
