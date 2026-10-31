package com.rayluxeo.aida.voice

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.ConfirmationRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything Mira can be doing with her voice, as one value.
 *
 * The orb renders this directly, which is why [PROCESSING] and [EXECUTING] are separate: working
 * out what Abu meant and actually writing the record are different waits, and the second one is
 * the one where something is changing on his phone. [SUCCESS] and [ERROR] are momentary — they
 * exist so the end of a turn is visible rather than a silent drop back to [IDLE].
 */
enum class MiraVoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    EXECUTING,
    SPEAKING,
    SUCCESS,
    ERROR,
}

/**
 * A spoken command Mira has read back but not carried out.
 *
 * It is held rather than dispatched because the pipeline's confirmation gate said so, and the
 * language is kept with it so the answer to "yes" is spoken the way the question was asked.
 */
data class PendingVoiceConfirmation(
    val request: ConfirmationRequest,
    val language: SpokenLanguage,
)

/**
 * One value the screen can render without knowing anything about recognizers or engines.
 *
 * [partialTranscript] is what Abu is part way through saying; it is deliberately separate from
 * the committed transcript, because it changes several times a second and is not yet a turn.
 *
 * [pending] is the confirmation gate reaching the spoken path. A turn that ends here has NOT run:
 * Mira has asked a question and is waiting, which is why it settles to [MiraVoiceState.IDLE]
 * rather than [MiraVoiceState.SUCCESS].
 */
data class VoiceSessionState(
    val state: MiraVoiceState = MiraVoiceState.IDLE,
    /** 0f..1f microphone level. Only meaningful while [MiraVoiceState.LISTENING]. */
    val amplitude: Float = 0f,
    val partialTranscript: String? = null,
    val lastFailure: SpeechFailure? = null,
    /** True when the microphone is usable at all: permission granted and a recognizer present. */
    val canListen: Boolean = false,
    val pending: PendingVoiceConfirmation? = null,
)

/**
 * The whole spoken turn, from pressing the orb to Mira finishing her reply.
 *
 * This exists because that turn is a state machine with real interruption rules, and those rules
 * were otherwise going to live in a view model beside the screen's own concerns. Listening,
 * interpreting, executing and speaking are one sequence, and any of them can be cut short.
 *
 * [onOrbPressed] is deliberately the only control. What a press means depends on what Mira is
 * doing — start listening, stop listening and submit, or shut up — and putting that decision in
 * one place is what makes "tap to interrupt her, tap again to talk" behave the same everywhere.
 */
interface VoiceConversationController {

    val state: StateFlow<VoiceSessionState>

    /**
     * The single gesture.
     *
     * IDLE / SUCCESS / ERROR -> start listening.
     * LISTENING              -> stop listening and submit what was heard.
     * SPEAKING               -> stop speaking at once, and return to IDLE without listening, so
     *                           interrupting Mira is not also a command to start talking.
     * PROCESSING / EXECUTING -> ignored. The command is already in flight and cancelling it
     *                           halfway would leave Abu unsure what was written.
     */
    fun onOrbPressed()

    /**
     * Carries out the command Mira read back, and speaks the result.
     *
     * No-op when nothing is pending, so a double tap on the confirm button cannot run a mutating
     * command twice.
     */
    fun confirmPending()

    /** Drops the pending command without running it, and says so. */
    fun dismissPending()

    /** Abandons whatever is happening and returns to [MiraVoiceState.IDLE]. */
    fun cancel()

    /** Re-runs the last utterance that failed for a retryable reason. No-op otherwise. */
    fun retryLast()
}
