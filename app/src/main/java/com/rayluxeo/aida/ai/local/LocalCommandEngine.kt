package com.rayluxeo.aida.ai.local

import com.rayluxeo.aida.ai.nlu.Classification
import com.rayluxeo.aida.ai.nlu.CommandClassifier
import com.rayluxeo.aida.ai.nlu.CommandSlotParser
import com.rayluxeo.aida.ai.nlu.SlottedCommand
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandEngine
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mira's floor: the interpreter that runs on the phone, with no network and no account.
 *
 * It is the only engine that is always there, so [isAvailable] is unconditionally true — an
 * on-device engine reporting itself unavailable would leave Abu with nothing at all. Anything
 * it cannot place is returned as [EngineResult.NotUnderstood] rather than guessed at; the
 * router decides on its own whether to ask Gemini about it.
 *
 * The work itself belongs to two collaborators, in the order the pipeline states: the scan and
 * the classifier decide what Abu asked for, the slot parser builds it, and a missing required
 * slot is a miss rather than a blank command.
 */
@Singleton
class LocalCommandEngine @Inject constructor(
    private val classifier: CommandClassifier,
    private val slots: CommandSlotParser,
) : CommandEngine {

    override val id: EngineId = EngineId.LOCAL

    override suspend fun isAvailable(): Boolean = true

    override suspend fun interpret(request: CommandRequest): EngineResult {
        val normalized = request.normalized.trim()
        if (normalized.isEmpty()) return EngineResult.NotUnderstood

        val scan = slots.scan(normalized)
        val classification = classifier.classify(normalized, scan.signals) ?: return EngineResult.NotUnderstood
        val slotted = slots.fill(classification.intent, request, scan) ?: return EngineResult.NotUnderstood

        // A bare pleasantry is the one thing Mira can answer on her own yet would rather hand to
        // the cloud: offline she greets, online Gemini replies in Abu's own words. Everything else
        // the local engine produces is a firm verdict the router acts on without asking Gemini.
        return EngineResult.Interpreted(
            command = slotted.command,
            confidence = calibrate(classification, slotted),
            deferToCloud = slotted.command is AppCommand.Greet,
        )
    }

    /**
     * Blends the two independent judgements a scheduled command rests on. Being sure Abu asked
     * for a reminder means little if the time it would fire at was a guess, so both have to hold
     * before Mira acts without checking — the confirmation gate reads this number and nothing
     * else about how the command was arrived at.
     */
    private fun calibrate(classification: Classification, slotted: SlottedCommand): Float {
        val temporal = slotted.temporalConfidence ?: return classification.confidence
        return (classification.confidence * INTENT_WEIGHT + temporal * TEMPORAL_WEIGHT).coerceIn(0f, 1f)
    }

    private companion object {
        const val INTENT_WEIGHT = 0.7f
        const val TEMPORAL_WEIGHT = 0.3f
    }
}
