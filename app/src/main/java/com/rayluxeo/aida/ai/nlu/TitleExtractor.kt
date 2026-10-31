package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.core.common.SpokenLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns what is left of an utterance, once the time, the money and the sum have been taken out,
 * into a title Abu would recognise as his own words.
 *
 * Three things make that harder than dropping stop words. Bengali puts the object before the
 * verb — "Ahmed ke call korar" is literally "Ahmed to call doing" — so a title lifted straight
 * out reads backwards in a list; the canonical form is lowercase, so the name he typed has to
 * be put back the way he typed it; and which words are request rather than content depends on
 * what he asked for. "call" is noise in "Ahmed ke call koro" and the whole point of "Ahmed ke
 * call korar reminder dao", which is why the winning intent is an argument here.
 */
@Singleton
class TitleExtractor @Inject constructor() {

    /**
     * [remainder] is the normalized text minus whatever the parsers already claimed, [raw] the
     * original input used to restore capitalisation, and [intent] what Abu turned out to be
     * asking for. Returns null when nothing recognisable is left.
     */
    fun extract(
        remainder: String,
        raw: String,
        language: SpokenLanguage,
        intent: MiraIntent,
    ): String? {
        val bengali = language == SpokenLanguage.BENGALI
        val dropped = requestWordsFor(intent)
        val tokens = remainder.split(' ').filter { it.isNotEmpty() }
        // Before anything reorders or thins the list, because both compound rules below read
        // adjacency and the filter and toVerbFirst each destroy it.
        val framing = framingCompoundIndices(tokens) + framingParticleIndices(tokens, dropped)
        val kept = tokens
            .filterIndexed { index, _ -> index !in framing }
            .filter { !isDropped(it, dropped) && it !in PARTICLES }
            .filter { !bengali || (it !in AMBIGUOUS_PARTICLES && !isStrayLetter(it)) }
        val ordered = if (bengali) toVerbFirst(kept) else kept
        val trimmed = trimConnectors(ordered)
        if (trimmed.isEmpty()) return null
        val surfaces = surfaceForms(raw)
        return trimmed.joinToString(separator = " ") { surfaces[it] ?: it }
    }

    /** The words that framed this particular request, plus the ones that frame every request. */
    private fun requestWordsFor(intent: MiraIntent): Set<String> =
        Lexicon.byIntent.getValue(intent).triggers
            .filter { it.namesTheRequest }
            .flatMapTo(REQUEST_WORDS.toMutableSet()) { it.token.split(' ') }

    private fun isDropped(token: String, dropped: Set<String>): Boolean =
        dropped.any { trigger ->
            if (trigger in EXACT_REQUEST_WORDS) token == trigger
            else Lexicon.triggerMatches(trigger, token)
        }

    /**
     * In a Bengali sentence a lone Latin letter is a case ending that was typed with a space in
     * front of it, as in "dupur e". English keeps its single letters — "a" and "I" mean something.
     */
    private fun isStrayLetter(token: String): Boolean =
        token.length == 1 && token[0] in 'a'..'z'

    /**
     * "likhe rakho" is one verb meaning "write it down", and it asks for the record rather than
     * describing it.
     *
     * The half carrying the meaning is the participle, so it can never go on its own: in "ei blog
     * post ta ami likhe felechi" Abu is reporting something he did, and dropping "likhe" there
     * leaves "ami felechi" — "I threw it away", the opposite of what he wrote. It goes only when
     * a light verb of keeping or giving follows it, which is what makes the run a request rather
     * than a statement.
     */
    private fun framingCompoundIndices(tokens: List<String>): Set<Int> {
        val framing = mutableSetOf<Int>()
        for (index in tokens.indices) {
            if (tokens[index] !in WRITING_PARTICIPLES) continue
            var end = index + 1
            while (end < tokens.size && tokens[end] in KEEPING_VERBS) end++
            if (end > index + 1) framing += index until end
        }
        return framing
    }

    /**
     * "Wake me up" is one verb and the particle carries none of its meaning. The verb is already
     * a word of asking, so dropping it alone leaves the particle standing on its own and Abu's
     * alarm list reads "up" — which is exactly what it did.
     *
     * The same shape as [framingCompoundIndices] and the same caution: the particle goes only
     * when the run back to the verb is entirely framing — the object of "wake me up" and nothing
     * else — and only when that verb is one this request is already dropping. "Follow up with
     * Ahmed" and "back up the site" keep their particle, because "follow" and "back" are what
     * Abu is asking for rather than how he asked.
     */
    private fun framingParticleIndices(tokens: List<String>, dropped: Set<String>): Set<Int> {
        val particles = mutableSetOf<Int>()
        for (index in tokens.indices) {
            if (tokens[index] !in VERB_PARTICLES) continue
            var cursor = index - 1
            while (cursor >= 0 && tokens[cursor] in PHRASAL_OBJECTS) cursor--
            if (cursor >= 0 && isDropped(tokens[cursor], dropped)) particles += index
        }
        return particles
    }

    /**
     * Rewrites "Ahmed to call" as "call Ahmed". The object marker survives normalization as
     * [CanonicalToken.TO], which is exactly the hinge the two halves pivot around.
     */
    private fun toVerbFirst(tokens: List<String>): List<String> {
        val hinge = tokens.indexOf(CanonicalToken.TO)
        if (hinge <= 0 || hinge == tokens.lastIndex) return tokens
        val before = tokens.subList(0, hinge)
        val after = tokens.subList(hinge + 1, tokens.size)
        return after + before
    }

    private fun trimConnectors(tokens: List<String>): List<String> {
        var start = 0
        var end = tokens.size
        while (start < end && tokens[start] in CONNECTORS) start++
        while (end > start && tokens[end - 1] in CONNECTORS) end--
        return tokens.subList(start, end)
    }

    /** Maps each lowercase word back to the spelling Abu actually used, so "ahmed" is "Ahmed" again. */
    private fun surfaceForms(raw: String): Map<String, String> {
        val surfaces = mutableMapOf<String, String>()
        for (word in raw.split(' ', '\t', '\n', '\r')) {
            val cleaned = word.trim { !it.isLetter() }
            if (cleaned.isEmpty()) continue
            surfaces.putIfAbsent(cleaned.lowercase(), cleaned)
            val withoutPossessive = cleaned.removeSuffix("'s").removeSuffix("’s").removeSuffix("'S")
            if (withoutPossessive != cleaned && withoutPossessive.isNotEmpty()) {
                surfaces.putIfAbsent(withoutPossessive.lowercase(), withoutPossessive)
            }
        }
        return surfaces
    }

    private companion object {

        /**
         * Politeness, Bengali light verbs and the bare verbs of asking. They frame a request in
         * every language Abu writes and are never what the request is about, so they go whatever
         * the intent turned out to be. [CanonicalToken.CALCULATE] is among them because it also
         * carries "koto" — the Bengali "how much", which ends a question about a phone number
         * just as readily as one about a sum.
         */
        val REQUEST_WORDS = setOf(
            CanonicalToken.ME,
            CanonicalToken.MY,
            CanonicalToken.DO,
            CanonicalToken.GIVE,
            CanonicalToken.CALCULATE,
            CanonicalToken.CREATE,
            CanonicalToken.UPDATE,
            CanonicalToken.SEARCH,
            "please",
            "plz",
            "kindly",
            "set",
            "record",
            "log",
            "what",
            "which",
            "how",
            "ki",
            // Exact-matched — see EXACT_REQUEST_WORDS. Two letters is short enough that the
            // prefix rule turns them into a wildcard over a large part of the language.
            "কি",
            "কী",
            // Imperatives of "kora" and "rakha". Their consonant frames are two letters long,
            // below what the fuzzy fold will touch, so they arrive spelled as Abu typed them.
            "koro",
            "korun",
            "koren",
            "rakho",
            "rekho",
            "করো",
            "করুন",
            "রাখো",
            "রেখো",
            // The imperatives of "lekha", and the polite form of "rakha" the block above missed.
            // Abu addressing Mira is the asking, never what he is asking about.
            //
            // A Bengali entry here matches by PREFIX, which is why "রাখেন" and "করেন" are absent:
            // they would also swallow "রাখেনি" and "করেনি" and record the opposite of what he
            // wrote. "rakhen" is left out for its own reason — it is both the polite imperative
            // and the honorific present, so in "Rahim shaheb office bondho rakhen" it is the
            // predicate. It sits in [KEEPING_VERBS] instead, where "likhe rakhen" still works.
            "likho",
            "lekho",
            "likhun",
            "rakhun",
            "লিখো",
            "লেখো",
            "লিখুন",
            "রাখুন",
        )

        /**
         * The request words that must match a whole token and never a prefix.
         *
         * `Lexicon.triggerMatches` matches a Bengali trigger by prefix, which is right for a
         * language that glues its endings on: "আজ" should reach "আজকে" and "আজকের". It stops
         * being right when the trigger is short, because a two-letter prefix is a wildcard over a
         * large part of the vocabulary. "কি" is the one that bit: it swallowed "কিনতে" — "to buy"
         * — so "প্রিন্টার কালি কিনতে হবে" recorded a reminder about printer ink with the buying
         * taken out. It also reaches "কিছু", "কিন্তু" and "কিনে".
         *
         * The same reasoning already keeps "রাখেন" and "করেন" out of [REQUEST_WORDS] entirely.
         * These two cannot simply be removed — "ki" and "কি" really do frame a question Abu is
         * asking — so they are kept and matched exactly instead. As a question particle "কি"
         * stands alone, so exact matching is also what the grammar wants.
         */
        val EXACT_REQUEST_WORDS = setOf("কি", "কী")

        /**
         * Bengali case endings, postpositions and plural markers that Abu types as separate
         * words. They are grammar, never content, and none of them is an English word, so they
         * go whatever language the sentence was judged to be — "Ahmed er number koto" leans on
         * only one Bengali word and still reads as English to the detector.
         */
        val PARTICLES = setOf(
            "er", "ti", "tar", "der", "gulo", "guli", "gula", "jonno", "theke", "kache",
            "niye", "diye", "byapare", "somporke", "sathe", "shathe",
            "এর", "টি", "টার", "দের", "গুলো", "গুলি", "জন্য", "থেকে", "কাছে", "নিয়ে",
            "ব্যাপারে", "সম্পর্কে", "সাথে", "দিয়ে",
        )

        /**
         * The half of a Bengali compound verb that says "write". It frames a request only when one
         * of [KEEPING_VERBS] follows it, so membership here is EXACT — "likhechi" and "লিখেছি" are
         * Abu saying he has already written the thing, and they keep their place in the title.
         */
        val WRITING_PARTICIPLES = setOf("likhe", "লিখে")

        /**
         * The light verbs that finish a "write it down" compound. Several of them are content on
         * their own — "Ahmed er jonno ekta copy rekhe dio" is a real task — so they never remove
         * anything by themselves and go only as part of a run that opened with a participle.
         *
         * Exact membership, deliberately: these must not inherit the prefix matching that
         * [REQUEST_WORDS] gets from `Lexicon.triggerMatches`, or "রেখে" would swallow "রেখেছি" and
         * turn a statement into a request.
         */
        val KEEPING_VERBS = setOf(
            CanonicalToken.DO,
            CanonicalToken.GIVE,
            "rakho", "rekho", "rakhun", "rakhen", "rekhe", "felo", "nao",
            "রাখো", "রেখো", "রাখুন", "রেখে", "ফেলো", "নাও",
        )

        /**
         * The particles of an English phrasal verb. One word, because every entry has to be a
         * particle Abu actually says after a word of asking: "set up a meeting" and "wake me up"
         * both earn it, while a list filled in by imagination would quietly take the meaning out
         * of "turn off the alarm" or "note down the address".
         */
        val VERB_PARTICLES = setOf("up")

        /** What can sit between a phrasal verb and its particle: the person being woken. */
        val PHRASAL_OBJECTS = setOf(CanonicalToken.ME, "it", "us")

        /**
         * The same grammar, in spellings that are also ordinary English words. "ta" is Bengali
         * for "the" and English for "thanks", so these are dropped only from a sentence already
         * judged to be Bengali.
         */
        val AMBIGUOUS_PARTICLES = setOf("ta", "te", "ra", "ar")

        /** Dropped only at the ends, where they are leftovers rather than part of the phrase. */
        val CONNECTORS = setOf(
            CanonicalToken.TO,
            CanonicalToken.AT,
            "for",
            "from",
            "with",
            "as",
            "about",
            "of",
            "on",
            "in",
            "is",
            "the",
            "a",
            "an",
            "that",
        )
    }
}
