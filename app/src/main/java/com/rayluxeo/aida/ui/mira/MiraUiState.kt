package com.rayluxeo.aida.ui.mira

import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.ConfirmationRequest
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.VoiceSessionState

/**
 * The transcript's three honest shapes. A vault of turns that has not loaded yet is not the same
 * thing as one that is genuinely empty, and Mira must not flash an empty state on the way in.
 */
sealed interface TranscriptState {
    data object Loading : TranscriptState
    data object Empty : TranscriptState
    data class Content(val turns: List<ConversationTurn>) : TranscriptState
}

/**
 * A command Mira understood but will not carry out until Abu says yes.
 *
 * [summary] is already rendered in [language], so the screen never has to reach back into the
 * response layer to find out what it is asking about.
 *
 * [spoken] says which path is holding it. One card asks the question either way, but a spoken
 * command belongs to the voice controller's turn and must be resolved there, so that confirming
 * it speaks the result and settles the orb instead of leaving it waiting on a turn nobody owns.
 */
data class PendingConfirmation(
    val summary: String,
    val request: ConfirmationRequest,
    val language: SpokenLanguage,
    val spoken: Boolean = false,
)

/**
 * What Android currently says about the microphone, which is not the same question as whether
 * Mira can hear.
 *
 * The three cases lead to three different controls, and collapsing them is how an app ends up
 * showing a button that opens a dialog Android has already decided never to show again.
 */
enum class MicrophoneAccess {
    /** The permission is held. Whether speech works now depends on the recognizer, not on Abu. */
    GRANTED,

    /** Not held, and the system dialog will still be shown. Pressing the orb asks for it. */
    ASKABLE,

    /**
     * Not held, and Android will not raise the dialog again. The system settings screen is the
     * only route left, so that is what gets offered.
     */
    SETTINGS_ONLY,
}

/** The one thing a notice may offer beyond being read and waved away. */
enum class NoticeOffer {
    /** Say the last utterance again. Offered only where the failure is a retryable one. */
    RETRY,

    /** Ask Android for the microphone. */
    ALLOW_MICROPHONE,

    /** Open the system screen that can still grant the microphone. */
    MICROPHONE_SETTINGS,
}

/**
 * Something Mira could not do, in Abu's own language, with at most one way forward.
 *
 * [offer] is null whenever there is nothing that would genuinely help: a control that says
 * "Retry" over a failure that cannot succeed is a fake control.
 */
data class MiraNotice(
    val text: String,
    val offer: NoticeOffer? = null,
)

/**
 * Everything the Mira screen draws.
 *
 * [voice] is the spoken turn exactly as `VoiceConversationController` reports it, copied and not
 * interpreted — listening, the live partial, the microphone level and the last speech failure all
 * come from there, so the screen and the controller cannot drift apart about what Mira is doing.
 *
 * [working] and [speaking] belong to the other half of this screen: a line Abu typed travels the
 * same pipeline without ever touching the controller, and the composer has to say so.
 *
 * [action] is what her latest reply prepared but will not do by itself — a number to dial, a
 * message to send, a permission to grant. It is dropped the moment Abu says anything else.
 *
 * [languageMode] is Abu's setting, kept here rather than only in the view model so that the one
 * rule deciding which language the screen speaks lives beside the transcript it reads that rule
 * from. See [language].
 */
data class MiraUiState(
    val transcript: TranscriptState = TranscriptState.Loading,
    val draft: String = "",
    val voice: VoiceSessionState = VoiceSessionState(),
    val working: Boolean = false,
    val speaking: Boolean = false,
    val pending: PendingConfirmation? = null,
    val action: MiraAction? = null,
    val lastEngine: EngineId? = null,
    val notice: MiraNotice? = null,
    val microphone: MicrophoneAccess = MicrophoneAccess.ASKABLE,
    val languageMode: LanguageMode = LanguageMode.AUTO,
) {
    /**
     * The language the screen's own words are in — the status line, the placeholder, the label on
     * a control — as distinct from the language of any one turn, which each turn carries itself.
     *
     * Chrome belongs to no particular utterance, so AUTO follows whichever language the last
     * exchange happened in, and falls back to English on a transcript that is still empty.
     */
    val language: SpokenLanguage
        get() = when (languageMode) {
            LanguageMode.BANGLA -> SpokenLanguage.BENGALI
            LanguageMode.ENGLISH -> SpokenLanguage.ENGLISH
            LanguageMode.AUTO -> (transcript as? TranscriptState.Content)
                ?.turns
                ?.lastOrNull()
                ?.language
                ?: SpokenLanguage.ENGLISH
        }

    val listening: Boolean get() = voice.state == MiraVoiceState.LISTENING

    /**
     * Whether pressing the orb right now would only stop Mira talking.
     *
     * Interrupting her never needs the microphone, and asking Android for permission at the exact
     * moment Abu is trying to shut her up is the wrong answer to the wrong question.
     */
    val pressWouldOnlyInterrupt: Boolean
        get() = speaking || voice.state == MiraVoiceState.SPEAKING

    /**
     * What the orb should be showing.
     *
     * A reply to a typed line is spoken outside the controller's turn, so the controller reads
     * IDLE while Mira is plainly talking. Drawing a resting orb at that moment contradicts both
     * the status line above it and the tap that would stop her.
     */
    val orbState: MiraVoiceState
        get() = if (voice.state == MiraVoiceState.IDLE && speaking) {
            MiraVoiceState.SPEAKING
        } else {
            voice.state
        }

    /** What Abu is part way through saying. Never shown as though it were a finished turn. */
    val partial: String? get() = voice.partialTranscript?.takeIf { listening && it.isNotBlank() }

    val canSend: Boolean get() = draft.isNotBlank() && !working

    /**
     * The orb is pressable while a press could do something.
     *
     * It goes dead in exactly two cases: a refusal Android will not raise again, where the
     * settings screen offered beside it is the only route left, and a typed command already in
     * flight. Everywhere else it stays live, deliberately — [VoiceSessionState.canListen] is the
     * controller's own cached answer and can be a moment behind a permission Abu has just granted,
     * and a stale cache must not be able to leave him with a dead orb and nothing to press.
     * A press that then cannot listen is answered with a notice saying so, which is more use than
     * a control that has quietly stopped responding.
     */
    val canListen: Boolean
        get() = (voice.canListen || microphone != MicrophoneAccess.SETTINGS_ONLY) && !working

    /**
     * The turn [action] is drawn against: Mira's latest, because that is the reply that prepared
     * it. Null while the transcript has yet to catch up with her, so a control is never shown
     * detached from the sentence that says what it will do.
     */
    val actionTurnId: Long?
        get() = action?.let {
            (transcript as? TranscriptState.Content)
                ?.turns
                ?.lastOrNull()
                ?.takeIf { turn -> turn.role == TurnRole.MIRA }
                ?.id
        }
}
