package com.rayluxeo.aida.ai.nlu

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Folds the many Latin spellings of a Bengali word onto one canonical token.
 *
 * Romanized Bengali has no orthography. Abu may write "shokal", "sokal" and "skal" in three
 * consecutive sentences and mean the same thing, so the pipeline cannot match on spellings —
 * it matches on families, and this is where a family is decided. What a family contains lives
 * in [Vocabulary]; how a spelling is matched to one lives in [VocabularyIndex].
 *
 * Nothing here looks at the language of the sentence. A fold is a fact about a word, and the
 * same word means the same thing whether Abu wrote it inside Bengali or inside English.
 */
@Singleton
class BanglishNormalizer @Inject constructor() {

    /**
     * One token to its canonical form: "shokal", "sokal" and "skal" all to
     * [CanonicalToken.MORNING]. A token that belongs to no family comes back unchanged.
     *
     * Forms that need a neighbour to mean anything are left alone here — "ta" is the hour
     * marker in "10 ta" and the definite article in "kaj ta", and with no sentence around it
     * there is nothing to tell them apart. Use [foldAll] or [foldTokens] where context exists.
     */
    fun fold(token: String): String = VocabularyIndex.foldOne(token.lowercase())

    /** Folds a whitespace-separated stretch of text, phrases and context-bound forms included. */
    fun foldAll(text: String): String =
        foldTokens(text.lowercase().split(' ', '\t', '\n').filter { it.isNotEmpty() })
            .joinToString(separator = " ")

    /**
     * Folds a token stream. [protectedTokens] are exempt from the fuzzy consonant-skeleton
     * match, which is how "Ahmed" survives a sentence full of Bengali; they are still matched
     * against the table itself, because a name that is also a word is still that word.
     */
    fun foldTokens(tokens: List<String>, protectedTokens: Set<String> = emptySet()): List<String> =
        VocabularyIndex.fold(tokens, protectedTokens).map { it.token }

    /**
     * The consonant frame a fuzzy match is made on. Exposed because it is the whole reason
     * unlisted spellings work, and a spelling that will not fold is only explainable by
     * looking at its skeleton.
     */
    fun skeleton(token: String): String = VocabularyIndex.skeletonOf(token)
}
