package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.domain.model.PlaceTrigger

/**
 * The place Abu named, which way round the reminder fires, and his sentence with the place
 * phrase taken back out.
 *
 * [remainder] is what the reminder is actually about: "remind me to buy printer ink when I get
 * near the office" leaves "remind me to buy printer ink" behind, which is the same shape
 * [ParsedRecurrence] hands a task. [label] stays in Abu's own words rather than being resolved
 * to anything — Mira has no geocoder, and matching it against the places he has saved is the
 * handler's work.
 */
data class ParsedPlace(
    val label: String,
    val trigger: PlaceTrigger,
    val remainder: String,
    val matched: String,
)

/**
 * Reads "when I get near the office", "office e pouchhale" and "অফিসে পৌঁছালে" as a place a
 * reminder waits at instead of a moment.
 *
 * An object rather than an injected class, like [RecurrenceExpressionParser]: it holds no state,
 * has no collaborator and reads no clock, so there is nothing for a caller to configure or
 * substitute.
 *
 * **The two languages put the place on opposite sides of the marker, and that is the thing a
 * later reader will get wrong.** English marks the place with a phrase in FRONT of it — "when I
 * get near THE OFFICE" — so the words after the marker name the place and the words before it
 * are the reminder. Bengali and Banglish mark it with a verb ending BEHIND it — "OFFICE e
 * pouchhale", "অফিসে পৌঁছালে" — so the word before the marker names the place and the reminder
 * is what is left on either side of it. A parser written for one direction reads half of what
 * Abu says and quietly mis-titles the other half: "office e pouchhale printer ink kinte mone
 * koriye dao" would come back with the whole sentence as the place name.
 *
 * Bengali script glues the locative onto the place word — "office e" is written "অফিসে" — so
 * the particle is consumed only when Abu typed it as its own word, and a marker matches with or
 * without it either way.
 *
 * The bare "near" is the one marker that is a word before it is a marker, so it is held to two
 * extra conditions ([Marker.needsRemainder]). A place reminder Mira invents out of "the deadline
 * is near" never fires at all, and Abu has no way to notice that it did not — far worse than
 * failing to read a sentence he can simply say again.
 */
object PlaceExpressionParser {

    fun parse(text: String): ParsedPlace? {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.indices.firstNotNullOfOrNull { index -> read(tokens, index) }
    }

    /**
     * The markers are tried longest first, so that "near" cannot claim a sentence that "when i
     * get near" already accounts for. A marker whose place turns out to be unusable falls
     * through to the next one rather than ending the search, because the sentence may still
     * carry a marker Mira can read.
     */
    private fun read(tokens: List<String>, index: Int): ParsedPlace? =
        MARKERS.firstNotNullOfOrNull { marker ->
            if (matchesAt(tokens, index, marker)) build(tokens, index, marker) else null
        }

    private fun matchesAt(tokens: List<String>, index: Int, marker: Marker): Boolean {
        if (index + marker.tokens.size > tokens.size) return false
        return marker.tokens.withIndex().all { (offset, token) -> tokens[index + offset] == token }
    }

    private fun build(tokens: List<String>, index: Int, marker: Marker): ParsedPlace? {
        val claimed = (index until index + marker.tokens.size).toMutableSet()
        val place = placeIndices(tokens, index, marker, claimed) ?: return null
        claimed += place

        val label = place.joinToString(separator = " ") { tokens[it] }.trim { it in PUNCTUATION }
        if (label.none { it.isLetter() }) return null
        // A word that only points at somewhere already known names no place Mira can resolve.
        if (place.all { tokens[it] in DEICTICS }) return null

        val remainder = join(tokens) { it !in claimed }
        if (marker.needsRemainder && remainder.isEmpty()) return null

        return ParsedPlace(
            label = label,
            trigger = marker.trigger,
            remainder = remainder,
            matched = join(tokens) { it in claimed },
        )
    }

    /**
     * Where the place sits relative to the marker. English takes the rest of the sentence: the
     * place phrase is the tail of what Abu says, and cutting it short would need a guess about
     * where a place name stops. Bengali takes the one word in front, because the locative is
     * part of that word and a longer run would swallow the reminder itself.
     */
    private fun placeIndices(
        tokens: List<String>,
        index: Int,
        marker: Marker,
        claimed: MutableSet<Int>,
    ): List<Int>? {
        if (marker.placeFollows) {
            val start = index + marker.tokens.size
            return (start until tokens.size).toList().ifEmpty { null }
        }
        var cursor = index - 1
        if (cursor >= 0 && tokens[cursor] in LOCATIVES) {
            claimed += cursor
            cursor--
        }
        return if (cursor >= 0) listOf(cursor) else null
    }

    private fun join(tokens: List<String>, keep: (Int) -> Boolean): String =
        tokens.filterIndexed { index, _ -> keep(index) }.joinToString(separator = " ")

    /**
     * One way of saying that a request waits on a place. [placeFollows] is the whole reason this
     * type exists: it is what tells the two grammars apart.
     */
    private data class Marker(
        val tokens: List<String>,
        val trigger: PlaceTrigger,
        val placeFollows: Boolean,
        /**
         * True for a marker that is an ordinary word elsewhere. It counts only when the sentence
         * still says something once the place phrase is taken out, so "near the office" on its
         * own — which names no reminder at all — is left alone.
         */
        val needsRemainder: Boolean = false,
    )

    private fun following(vararg tokens: String, trigger: PlaceTrigger, needsRemainder: Boolean = false) =
        Marker(tokens.toList(), trigger, placeFollows = true, needsRemainder = needsRemainder)

    private fun preceding(vararg tokens: String, trigger: PlaceTrigger) =
        Marker(tokens.toList(), trigger, placeFollows = false)

    /**
     * Sorted longest first, which is what keeps "near" from claiming the tail of "when i get
     * near". Only spellings that are certainly what they look like are listed: a Bengali form
     * guessed at would fold real sentences onto a place reminder that never fires.
     *
     * "when i'm near" arrives here as three tokens — the tokenizer drops the apostrophe and
     * splits the letters either side of it — so that spelling is listed the way it actually
     * lands rather than the way it is typed.
     */
    private val MARKERS: List<Marker> = listOf(
        // ---------------------------------------------------------- English: the place follows
        following("when", "i", "get", "near", trigger = PlaceTrigger.ENTER),
        following("when", "i", "get", "to", trigger = PlaceTrigger.ENTER),
        following("when", "i", "get", "away", "from", trigger = PlaceTrigger.EXIT),
        following("when", "i", "arrive", "at", trigger = PlaceTrigger.ENTER),
        following("when", "i", "arrive", trigger = PlaceTrigger.ENTER),
        following("when", "i", "reach", trigger = PlaceTrigger.ENTER),
        following("when", "i", "am", "near", trigger = PlaceTrigger.ENTER),
        following("when", "i", "m", "near", trigger = PlaceTrigger.ENTER),
        following("when", "im", "near", trigger = PlaceTrigger.ENTER),
        following("when", "i", "leave", trigger = PlaceTrigger.EXIT),
        following("near", trigger = PlaceTrigger.ENTER, needsRemainder = true),

        // ------------------------------------------- Bengali and Banglish: the place precedes
        preceding("theke", "ber", "hole", trigger = PlaceTrigger.EXIT),
        preceding("theke", "berole", trigger = PlaceTrigger.EXIT),
        preceding("থেকে", "বেরোলে", trigger = PlaceTrigger.EXIT),
        preceding("kache", "gele", trigger = PlaceTrigger.ENTER),
        preceding("কাছে", "গেলে", trigger = PlaceTrigger.ENTER),
        preceding("pouchhale", trigger = PlaceTrigger.ENTER),
        preceding("pouchale", trigger = PlaceTrigger.ENTER),
        preceding("পৌঁছালে", trigger = PlaceTrigger.ENTER),
        preceding("gele", trigger = PlaceTrigger.ENTER),
        preceding("গেলে", trigger = PlaceTrigger.ENTER),
        preceding("giye", trigger = PlaceTrigger.ENTER),
        preceding("chere", trigger = PlaceTrigger.EXIT),
        preceding("ছাড়লে", trigger = PlaceTrigger.EXIT),
    ).sortedByDescending { it.tokens.size }

    /**
     * The case endings Abu types as their own word. They belong to the place rather than to the
     * reminder, so they are taken out with the marker — and "er" among them is what makes
     * "office er kache gele" read the same as "অফিসের কাছে গেলে", where the ending is glued on.
     */
    private val LOCATIVES = setOf("e", "te", "er", "এ", "তে", "এর")

    /** Words that point at a place already in mind. Mira has nothing to resolve them against. */
    private val DEICTICS = setOf(
        "there",
        "here",
        "ekhane",
        "okhane",
        "shekhane",
        "sekhane",
        "এখানে",
        "ওখানে",
        "সেখানে",
    )

    /**
     * Trimmed off a label. Deliberately a list of punctuation rather than "anything that is not
     * a letter", because a Bengali vowel sign is not a letter and trimming one off would leave
     * "অফিসে" as "অফিস" — a different word from the one Abu typed. The danda ends a Bengali
     * sentence the way a full stop ends an English one.
     */
    private const val PUNCTUATION = " .,;:!?।'\"()"
}
