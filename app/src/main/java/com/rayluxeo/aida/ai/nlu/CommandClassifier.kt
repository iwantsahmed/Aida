package com.rayluxeo.aida.ai.nlu

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Structural facts about an utterance that argue for an intent without being words.
 *
 * They are found by the parsers before the intent is known, because "kal shokal 10 tay" and
 * "500 taka" tell Mira what kind of request she is looking at as loudly as any verb does.
 */
data class IntentSignals(
    val hasTemporal: Boolean = false,
    val hasDuration: Boolean = false,
    val hasMoney: Boolean = false,
    val hasArithmetic: Boolean = false,
)

/**
 * The winning intent, with the reasoning kept. [evidence] is what makes a wrong answer
 * debuggable — it says which token scored what, rather than leaving a number to be stared at.
 */
data class Classification(
    val intent: MiraIntent,
    val confidence: Float,
    val score: Float,
    val evidence: List<String>,
)

/**
 * Scores every rule in [Lexicon] against an utterance and returns the best, or null when
 * nothing argued convincingly enough for Mira to act.
 *
 * The score is a plain sum of stated contributions: matched trigger weights, one bonus for a
 * trigger sitting at an edge of the sentence, structural bonuses for a time, a duration, an
 * amount or a sum, and penalties for negative evidence and for deliberately standing down in
 * favour of a stronger intent. Nothing here is tuned against a corpus; every number is a
 * statement about how much a word is worth, and a mistake is fixed by arguing with that
 * statement rather than by nudging a constant.
 */
@Singleton
class CommandClassifier @Inject constructor() {

    fun classify(normalized: String, signals: IntentSignals = IntentSignals()): Classification? {
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val lexical = Lexicon.rules.associate { it.intent to lexicalScoreOf(it, tokens) }
        return Lexicon.rules
            .map { score(it, tokens, signals, lexical) }
            .filter { it.score >= MIN_SCORE }
            .maxByOrNull { it.score }
    }

    private fun score(
        rule: IntentRule,
        tokens: List<String>,
        signals: IntentSignals,
        lexical: Map<MiraIntent, LexicalScore>,
    ): Classification {
        val own = lexical.getValue(rule.intent)
        var total = own.weight
        val evidence = own.evidence.toMutableList()

        if (own.bestIndex != null && atEdge(own.bestIndex, tokens.size)) {
            total += EDGE_BONUS
            // English leads with the verb, Bengali ends with it. A trigger in the middle of a
            // sentence is more often part of the subject than the request itself.
            evidence += describe("trigger at sentence edge", EDGE_BONUS)
        }

        total += bonus(signals.hasTemporal, rule.temporalBonus, "time expression present", evidence)
        total += bonus(signals.hasDuration, rule.durationBonus, "duration present", evidence)
        total += bonus(signals.hasMoney, rule.moneyBonus, "amount of money present", evidence)
        total += bonus(signals.hasArithmetic, rule.arithmeticBonus, "arithmetic expression present", evidence)

        for (negative in rule.negatives) {
            if (tokens.none { Lexicon.triggerMatches(negative, it) }) continue
            total -= NEGATIVE_PENALTY
            evidence += describe("contradicted by '$negative'", -NEGATIVE_PENALTY)
        }

        for (stronger in rule.yieldsTo) {
            if (!standsDownTo(rule, lexical.getValue(stronger))) continue
            total -= YIELD_PENALTY
            evidence += describe("yields to $stronger", -YIELD_PENALTY)
        }

        return Classification(
            intent = rule.intent,
            confidence = (total / SATURATION).coerceIn(0f, 1f),
            score = total,
            evidence = evidence,
        )
    }

    /**
     * True when the stronger intent matched a trigger this one cannot account for.
     *
     * Asking for evidence the yielding rule does not itself own is what keeps a shared word
     * from being read as a reason to stand down: "invoice khujo" gives every search intent the
     * word "khujo", and if that counted, the tasks search would yield to the notes search, the
     * notes search would yield to the memory search, and Abu would be told she did not follow.
     */
    private fun standsDownTo(rule: IntentRule, stronger: LexicalScore): Boolean {
        val own = rule.triggers.mapTo(mutableSetOf()) { it.token }
        return stronger.matched.any { it !in own }
    }

    private fun bonus(present: Boolean, amount: Float, reason: String, evidence: MutableList<String>): Float {
        if (!present || amount == 0f) return 0f
        evidence += describe(reason, amount)
        return amount
    }

    private fun lexicalScoreOf(rule: IntentRule, tokens: List<String>): LexicalScore {
        var weight = 0f
        var bestWeight = 0f
        var bestIndex: Int? = null
        val matched = mutableListOf<String>()
        val evidence = mutableListOf<String>()

        for (trigger in rule.triggers) {
            val index = indexOf(trigger.token, tokens)
            if (index < 0) continue
            weight += trigger.weight
            matched += trigger.token
            evidence += describe("token '${trigger.token}'", trigger.weight)
            if (trigger.weight > bestWeight) {
                bestWeight = trigger.weight
                bestIndex = index
            }
        }
        return LexicalScore(weight, bestIndex, matched, evidence)
    }

    /**
     * Where a trigger starts in the token stream, or -1. A trigger written with a space is a
     * phrase and only counts as a run: "mone rakho" means remember, while "mone" on its own
     * means "in mind" and belongs to whatever sentence it was borrowed from.
     */
    private fun indexOf(trigger: String, tokens: List<String>): Int {
        val words = trigger.split(' ')
        if (words.size == 1) return tokens.indexOfFirst { Lexicon.triggerMatches(trigger, it) }
        for (start in 0..tokens.size - words.size) {
            if (words.indices.all { Lexicon.triggerMatches(words[it], tokens[start + it]) }) return start
        }
        return -1
    }

    /** True in the opening or closing quarter of the utterance, where request verbs live. */
    private fun atEdge(index: Int, size: Int): Boolean {
        if (size <= EDGE_EXEMPT_SIZE) return true
        val quarter = size / 4
        return index < quarter || index >= size - quarter
    }

    private fun describe(reason: String, amount: Float): String =
        "$reason ${if (amount >= 0f) "+" else "-"}${formatAmount(amount)}"

    private fun formatAmount(amount: Float): String {
        val hundredths = (abs(amount) * 100).roundToInt()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }

    private data class LexicalScore(
        val weight: Float,
        val bestIndex: Int?,
        /** The trigger tokens that matched, so a yield can ask what the evidence actually was. */
        val matched: List<String>,
        val evidence: List<String>,
    )

    private companion object {
        /** Below this nothing has argued for an intent strongly enough to act on. */
        const val MIN_SCORE = 0.5f

        /** The score at which confidence is considered total; scores above it simply clamp. */
        const val SATURATION = 1.6f

        const val EDGE_BONUS = 0.15f
        const val NEGATIVE_PENALTY = 0.6f
        const val YIELD_PENALTY = 1.0f

        /** In a very short utterance every position is an edge. */
        const val EDGE_EXEMPT_SIZE = 3
    }
}
