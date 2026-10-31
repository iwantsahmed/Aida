package com.rayluxeo.aida.voice

import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import java.util.Locale
import java.util.MissingResourceException

/**
 * The languages Mira listens for, and their BCP 47 tags in the form the recognition extras expect.
 *
 * Derived from [SpokenLanguage] rather than typed out, so the allowed-language lists handed to the
 * recognizer can never drift from the languages Mira can actually interpret.
 */
internal val RECOGNITION_LANGUAGES: Set<SpokenLanguage> = SpokenLanguage.entries.toSet()

internal val RECOGNITION_LANGUAGE_TAGS: List<String> = SpokenLanguage.entries.map { it.tag }

/**
 * Locale matching for voices and recognition models.
 *
 * Engines are inconsistent about how they name a locale: Google's TTS reports voices as
 * `ben_BGD` while its recognition service advertises `bn-BD`, Samsung reports `bn_BD`, and some
 * engines report a bare language with no country at all. Comparing tags as strings therefore
 * loses real matches, so everything here compares the language subtag only, tolerating both the
 * two-letter (ISO 639-1) and three-letter (ISO 639-2) forms and ignoring the country.
 *
 * Ignoring the country is deliberate. An `en-GB` voice speaking English to Abu is far better than
 * no voice at all, and Bengali TTS voices are commonly published as `bn-IN` rather than `bn-BD`.
 */
internal fun matchesSpokenLanguage(locale: Locale, language: SpokenLanguage): Boolean {
    val wanted = languageCodes(language.locale)
    if (wanted.isEmpty()) return false
    return languageCodes(locale).any { it in wanted }
}

/**
 * Same match, for the BCP 47 tags that `RecognitionSupport` returns. Underscores are accepted
 * because engines hand back Java-style `bn_BD` at least as often as the documented `bn-BD`.
 */
internal fun matchesSpokenLanguage(languageTag: String, language: SpokenLanguage): Boolean =
    matchesSpokenLanguage(Locale.forLanguageTag(languageTag.trim().replace('_', '-')), language)

private fun languageCodes(locale: Locale): Set<String> {
    val two = locale.language.lowercase(Locale.ROOT)
    val three = try {
        locale.isO3Language.lowercase(Locale.ROOT)
    } catch (missing: MissingResourceException) {
        // A locale the JDK has no three-letter code for. The two-letter form is all there is.
        ""
    }
    return setOf(two, three).filter { it.isNotEmpty() }.toSet()
}

/**
 * Builds the label Abu reads in the voice list.
 *
 * Voice names are opaque engine identifiers — `bn-bd-x-ban-local`, `en-us-x-tpf-network` — with
 * no published format, so the only honest way to tell two voices of the same locale apart is to
 * strip the prefix they literally share and show what is left. Nothing in the name is
 * interpreted, and in particular nothing in it is read as a gender.
 */
internal fun voiceLabels(
    voiceNames: List<String>,
    language: SpokenLanguage,
    localeOf: (String) -> Locale? = { null },
): Map<String, String> {
    // Each voice is named from its OWN locale, not from the section it is filed under. An engine
    // lists en-GB and en-IN voices among the English ones, and calling all of them "English
    // (United States)" tells Abu the opposite of what he is about to hear.
    val fallback = language.locale.getDisplayName(Locale.ENGLISH)
    fun readable(name: String): String = localeOf(name)?.getDisplayName(Locale.ENGLISH) ?: fallback

    // With nothing to tell apart, the raw identifier is noise rather than information.
    if (voiceNames.size <= 1) return voiceNames.associateWith { readable(it) }

    val shared = voiceNames.reduce(String::commonPrefixWith)
    return voiceNames.associateWith { name ->
        val distinct = name.removePrefix(shared).trim('-', '_', ' ')
        if (distinct.isEmpty()) readable(name) else "${readable(name)} · $distinct"
    }
}

/**
 * Which language the recognizer should be told to expect, or null to leave it to the device.
 *
 * AUTO returns null deliberately. Naming a language under AUTO would force Abu's mixed sentences
 * into whichever one was guessed first, so the device's own default stands and the language is
 * read back off the result instead.
 */
internal fun resolveRecognitionLanguage(mode: LanguageMode): SpokenLanguage? = when (mode) {
    LanguageMode.BANGLA -> SpokenLanguage.BENGALI
    LanguageMode.ENGLISH -> SpokenLanguage.ENGLISH
    LanguageMode.AUTO -> null
}

/**
 * Folds the language tags a recognition service reports onto the two languages Mira speaks.
 *
 * A service lists dozens of them and spells each one its own way, so this answers only the
 * question Mira has: which of her own two the device can actually hear. A tag that matches neither
 * is not her concern, and an unparseable one is dropped rather than guessed at.
 */
internal fun supportedLanguagesOf(languageTags: Collection<String>): Set<SpokenLanguage> =
    RECOGNITION_LANGUAGES
        .filterTo(mutableSetOf()) { language ->
            languageTags.any { matchesSpokenLanguage(it, language) }
        }
