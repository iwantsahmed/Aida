package com.rayluxeo.aida.core.common

import java.util.Locale

/** What Abu picked in Settings. AUTO resolves per utterance. */
enum class LanguageMode { AUTO, BANGLA, ENGLISH }

/** The language a single utterance or reply is actually in. */
enum class SpokenLanguage {
    BENGALI,
    ENGLISH;

    val locale: Locale
        get() = when (this) {
            BENGALI -> Locale.forLanguageTag("bn-BD")
            ENGLISH -> Locale.forLanguageTag("en-US")
        }

    val tag: String get() = locale.toLanguageTag()

    companion object {
        fun fromTag(tag: String?): SpokenLanguage =
            if (tag?.startsWith("bn", ignoreCase = true) == true) BENGALI else ENGLISH
    }
}

/**
 * How the raw text was written, independent of which language it is in.
 * "Kal shokal 10 tay" is BENGALI in [SpokenLanguage] terms but ROMANIZED_BENGALI in script terms.
 */
enum class TextScript { BENGALI, LATIN, MIXED }
