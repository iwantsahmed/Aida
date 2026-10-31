package com.rayluxeo.aida.voice

import com.rayluxeo.aida.core.common.SpokenLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How much of the other script a stretch has to carry before it earns its own voice.
 *
 * Twelve characters is about three words in either script — the shortest thing that reads as a
 * clause rather than a borrowed word. Everything Mira actually borrows sits under it: a name
 * ("Ahmed"), a loanword ("reminder", "homepage"), a verb ("call"). Those stay in the sentence's
 * own voice, because a Bengali voice saying "Ahmed" is far better than two voices trading places
 * in the middle of a reply.
 */
private const val MIN_SWITCH_CHARS = 12

/** Bengali letters, marks and digits. The danda that ends a Bengali sentence is U+0964, outside it. */
private val BENGALI_BLOCK = '\u0980'..'\u09FF'

/** Full stop, question, exclamation, the danda and the double danda. A line break ends one too. */
private const val SENTENCE_ENDS = ".!?\u0964\u0965\n\r"

/**
 * Decides which voice says what, one sentence at a time.
 *
 * Mira's Bengali replies are full of English: client names, "invoice", "reminder", "homepage".
 * Breaking each of those out into its own utterance makes her sound like two people arguing, so
 * the rule here is that **a segment has to be worth the switch**, and the unit that can be worth
 * it is a sentence. A sentence is never cut in half: a voice change inside one is audible as a
 * stumble however well the two voices are matched, while a change at a full stop is just the next
 * sentence starting. Sentences are therefore the atoms, and a run of them changes voice only when
 * it is written *wholly* in the other script and is long enough to be worth hearing in it — see
 * [MIN_SWITCH_CHARS].
 *
 * "Wholly" is absolute rather than a majority, and that is deliberate. A Bengali sentence carrying
 * an English title — "যোগ করেছি — Homepage redesign।" — is still a Bengali sentence, and a majority
 * rule hands it to the English voice purely because Latin spelling runs longer per word. The price
 * is the mirror case: an English reply quoting a Bengali clause keeps the English voice, because
 * the clause shares its sentence with English words. Paying it would mean cutting a sentence in
 * two, which is the one thing this class will not do.
 *
 * Script is what is measured, not language, because a voice reads glyphs rather than meaning. An
 * English voice handed Bengali letters produces silence or nonsense, whichever the engine prefers,
 * and that is the failure this class exists to avoid.
 */
@Singleton
class VoiceResponseFormatterImpl @Inject constructor() : VoiceResponseFormatter {

    override fun segment(text: String, primary: SpokenLanguage): List<SpokenSegment> {
        if (text.isBlank()) return emptyList()
        val sentences = sentences(text, primary)
        return spoken(text, runs(sentences, resolve(sentences)), primary)
    }

    /**
     * Ranges cover the whole string end to end, and the whitespace after a full stop belongs to
     * the sentence it followed. That is what makes merging lossless: two sentences that end up in
     * the same voice are re-joined as the substring the reply already was, spacing and all.
     */
    private fun sentences(text: String, primary: SpokenLanguage): List<Sentence> {
        val found = mutableListOf<Sentence>()
        var start = 0
        var index = 0
        while (index < text.length) {
            if (text[index] !in SENTENCE_ENDS) {
                index++
                continue
            }
            var end = index + 1
            while (end < text.length && (text[end] in SENTENCE_ENDS || text[end].isWhitespace())) end++
            found += scan(text, start, end, primary)
            start = end
            index = end
        }
        if (start < text.length) found += scan(text, start, text.length, primary)
        return found
    }

    private fun scan(text: String, start: Int, end: Int, primary: SpokenLanguage): Sentence {
        var bengali = 0
        var latin = 0
        for (index in start until end) {
            val character = text[index]
            when {
                character in BENGALI_BLOCK -> bengali++
                character.isLetter() -> latin++
            }
        }
        val bengaliIsPrimary = primary == SpokenLanguage.BENGALI
        return Sentence(
            start = start,
            end = end,
            primaryChars = if (bengaliIsPrimary) bengali else latin,
            otherChars = if (bengaliIsPrimary) latin else bengali,
        )
    }

    /**
     * A sentence with nothing to read in it — a bare figure, a line of punctuation — takes the
     * verdict of the sentence before it, or of the first one with an opinion when it comes first.
     * Left to decide alone it would split a run of English in two over a date.
     */
    private fun resolve(sentences: List<Sentence>): List<Boolean> {
        val leanings = sentences.map { it.leansOther }
        var carried = leanings.firstNotNullOfOrNull { it } ?: false
        return leanings.map { lean ->
            carried = lean ?: carried
            carried
        }
    }

    private fun runs(sentences: List<Sentence>, leanings: List<Boolean>): List<Run> {
        val grouped = mutableListOf<Run>()
        sentences.forEachIndexed { index, sentence ->
            val open = grouped.lastOrNull()
            if (open != null && open.other == leanings[index]) {
                open.end = sentence.end
                open.otherChars += sentence.otherChars
            } else {
                grouped += Run(sentence.start, sentence.end, leanings[index], sentence.otherChars)
            }
        }
        // A run in the other script that is still shorter than a clause is not worth a voice
        // change, so it is spoken in the reply's own voice and rejoins its neighbours below.
        grouped.forEach { if (it.other && it.otherChars < MIN_SWITCH_CHARS) it.other = false }
        return grouped
    }

    /** Neighbouring runs that ended up in the same voice are one segment, not two utterances. */
    private fun spoken(text: String, runs: List<Run>, primary: SpokenLanguage): List<SpokenSegment> {
        val segments = mutableListOf<SpokenSegment>()
        var open: Run? = null
        for (run in runs) {
            val current = open
            if (current == null) {
                open = run
            } else if (current.other == run.other) {
                current.end = run.end
            } else {
                add(segments, text, current, primary)
                open = run
            }
        }
        open?.let { add(segments, text, it, primary) }
        return segments
    }

    private fun add(
        segments: MutableList<SpokenSegment>,
        text: String,
        run: Run,
        primary: SpokenLanguage,
    ) {
        val words = text.substring(run.start, run.end).trim()
        if (words.isEmpty()) return
        segments += SpokenSegment(words, if (run.other) opposite(primary) else primary)
    }

    private fun opposite(primary: SpokenLanguage): SpokenLanguage = when (primary) {
        SpokenLanguage.BENGALI -> SpokenLanguage.ENGLISH
        SpokenLanguage.ENGLISH -> SpokenLanguage.BENGALI
    }

    private class Sentence(
        val start: Int,
        val end: Int,
        val primaryChars: Int,
        val otherChars: Int,
    ) {
        /**
         * True only when the sentence holds none of the reply's own script at all, and null when
         * it holds no letters either way and so argues for neither voice.
         */
        val leansOther: Boolean?
            get() = if (primaryChars == 0 && otherChars == 0) null else primaryChars == 0
    }

    private class Run(
        val start: Int,
        var end: Int,
        var other: Boolean,
        var otherChars: Int,
    )
}
