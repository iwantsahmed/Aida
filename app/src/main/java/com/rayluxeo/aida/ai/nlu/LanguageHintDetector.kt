package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which language Abu is speaking and which script he wrote it in, and says how sure
 * it is about the first of those.
 *
 * The two are not the same question, which is why this is its own class. "Tomorrow 10 tay
 * Ahmed ke call korar reminder dio" is written almost entirely in Latin letters and is
 * Bengali in every other respect; answering it in English because it looks Latin would be
 * wrong in exactly the way Mira exists not to be. So script is counted in letters, and
 * language is decided from the words — a sentence that leans on Bengali grammar is Bengali
 * however it was typed.
 *
 * Confidence is reported rather than assumed. A long sentence built out of Bengali function
 * words is a strong verdict; two words that could go either way is a guess, and saying so
 * lets the rest of the pipeline treat it as one.
 */
@Singleton
class LanguageHintDetector @Inject constructor() {

    fun detect(raw: String, mode: LanguageMode): LanguageHint {
        val scan = UtteranceScanner.scan(raw)
        val script = scriptOf(scan)
        return when (mode) {
            // Abu pinned a language in Settings. There is nothing to be unsure about.
            LanguageMode.BANGLA -> LanguageHint(SpokenLanguage.BENGALI, script, PINNED)
            LanguageMode.ENGLISH -> LanguageHint(SpokenLanguage.ENGLISH, script, PINNED)
            LanguageMode.AUTO -> resolve(scan, script)
        }
    }

    private fun resolve(scan: UtteranceScan, script: TextScript): LanguageHint {
        val folded = VocabularyIndex.fold(scan.tokens, scan.protectedTokens)
        val words = folded.count { it.token.any(Char::isLetter) }
        val bengaliWords = folded.count { isBengaliWord(it) }
        val bengali = when {
            script == TextScript.BENGALI -> true
            bengaliWords >= BENGALI_WORD_THRESHOLD -> true
            // Everything he wrote was Bengali; there was just not much of it.
            bengaliWords > 0 && bengaliWords == words -> true
            script == TextScript.MIXED && scan.bengaliLetters * 2 >= scan.latinLetters -> true
            else -> false
        }
        return LanguageHint(
            language = if (bengali) SpokenLanguage.BENGALI else SpokenLanguage.ENGLISH,
            script = script,
            confidence = confidence(bengali, bengaliWords, words, scan),
        )
    }

    /**
     * A word argues for Bengali when a Bengali or Banglish spelling is what produced it, or
     * when it is still in Bengali script because the table had no entry for it. An English
     * synonym that folded to the same canonical token argues for nothing: "note", "find" and
     * "task" are ordinary English, and two of them in a sentence are not a Bengali sentence.
     */
    private fun isBengaliWord(folded: FoldedToken): Boolean =
        folded.token.any(Char::isLetter) &&
            (folded.bengaliEvidence || folded.token.any(UtteranceScanner::isBengaliScript))

    /**
     * How much of what Abu wrote actually argued for the verdict, discounted when there was
     * barely anything to read. A single word is one coincidence away from being wrong however
     * clearly it points, and an utterance of nothing but digits points nowhere at all.
     */
    private fun confidence(bengali: Boolean, bengaliWords: Int, words: Int, scan: UtteranceScan): Float {
        if (words == 0) return NO_EVIDENCE
        val share = bengaliWords.toFloat() / words
        val support = if (bengali) share else 1f - share
        return ((BASE + SUPPORT_WEIGHT * support) * brevity(scan)).coerceIn(0f, 1f)
    }

    private fun brevity(scan: UtteranceScan): Float =
        when (scan.tokens.count { token -> token.any(Char::isLetter) }) {
            0, 1 -> LONE_WORD
            2 -> TWO_WORDS
            else -> 1f
        }

    private fun scriptOf(scan: UtteranceScan): TextScript = when {
        scan.bengaliLetters > 0 && scan.latinLetters > 0 -> TextScript.MIXED
        scan.bengaliLetters > 0 -> TextScript.BENGALI
        else -> TextScript.LATIN
    }

    private companion object {
        /** Two Bengali function words are enough; one could be a coincidence of spelling. */
        const val BENGALI_WORD_THRESHOLD = 2

        const val PINNED = 1f
        const val BASE = 0.55f
        const val SUPPORT_WEIGHT = 0.4f
        const val LONE_WORD = 0.6f
        const val TWO_WORDS = 0.8f

        /** "12 * 4" is neither language. Reporting either one confidently would be a lie. */
        const val NO_EVIDENCE = 0.3f
    }
}
