package com.rayluxeo.aida.voice

import com.rayluxeo.aida.domain.command.CommandOutcome
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How long [MiraVoiceState.SUCCESS] and [MiraVoiceState.ERROR] are held before the orb settles.
 *
 * Long enough that the end of a turn is seen rather than guessed at, short enough that it is over
 * before Abu could reach for the orb again. Nothing waits on it: a press during the hold cancels
 * it and starts listening immediately.
 */
internal const val TURN_END_HOLD_MS = 900L

/**
 * The spoken turn — press, listen, interpret, execute, speak — as one state machine.
 *
 * It lives here rather than in a view model because the interruption rules are the hard part and
 * they are not a screen's business: what a press means depends entirely on what Mira is already
 * doing, and that decision has to be in one place or "tap to interrupt her" behaves differently
 * on every screen that offers an orb.
 *
 * Two invariants hold the whole thing together:
 *
 * - **One turn at a time.** [turnJob] is the turn; starting another cancels the previous one.
 *   Listening is a *child* of that job ([listenJob]) so that ending the listening early submits
 *   what was heard instead of throwing the turn away.
 * - **Single-threaded by construction.** The screen calls this from the main thread and [scope]
 *   is main-dispatched, so the mutable fields below need no synchronisation. A scope on another
 *   dispatcher would break that and is why the scope is injected rather than invented here.
 *
 * Nothing collected here outlives [scope]: cancelling it releases the microphone, the audio focus
 * and the engine connection along with the collections that hold them.
 */
class VoiceConversationControllerImpl(
    private val recognizer: SpeechRecognitionManager,
    private val voice: MiraTextToSpeech,
    private val earcons: MiraEarconPlayer,
    private val audioFocus: MiraAudioFocusManager,
    private val execute: ExecuteCommandUseCase,
    private val renderer: ResponseRenderer,
    settings: SettingsRepository,
    voicePreferences: VoicePreferenceRepository,
    private val scope: CoroutineScope,
) : VoiceConversationController {

    private val _state = MutableStateFlow(VoiceSessionState())
    override val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    private var turnJob: Job? = null
    private var listenJob: Job? = null

    /**
     * The last thing Mira actually heard, kept so [retryLast] has something to re-run. It survives
     * the turn that produced it precisely because the turn may have ended badly.
     */
    private var lastUtterance: String? = null

    /**
     * Read rather than observed at the moment they are needed, so that pressing the orb never waits
     * on a preferences read — the activation tone has to land on the press, not after it.
     */
    private var liveSettings = MiraSettings()
    private var livePreferences = VoicePreferences.DEFAULT

    init {
        _state.update { it.copy(canListen = microphoneUsable()) }
        scope.launch { settings.settings.collect { liveSettings = it } }
        scope.launch { voicePreferences.preferences.collect { livePreferences = it } }
        scope.launch { audioFocus.focusLost.collect { onFocusLost() } }
    }

    override fun onOrbPressed() {
        when (_state.value.state) {
            MiraVoiceState.IDLE,
            MiraVoiceState.SUCCESS,
            MiraVoiceState.ERROR,
            -> beginTurn()

            // Ending the listening, not the turn: the collection is a child, so the rest of the
            // turn carries on with whatever was heard up to this point.
            MiraVoiceState.LISTENING -> listenJob?.cancel()

            MiraVoiceState.SPEAKING -> silence()

            // In flight. Stopping now would leave Abu unsure whether the record was written.
            MiraVoiceState.PROCESSING, MiraVoiceState.EXECUTING -> Unit
        }
    }

    override fun cancel() {
        turnJob?.cancel()
        turnJob = null
        listenJob = null
        voice.stop()
        _state.update {
            it.copy(
                state = MiraVoiceState.IDLE,
                amplitude = 0f,
                partialTranscript = null,
                canListen = microphoneUsable(),
            )
        }
    }

    override fun retryLast() {
        val utterance = lastUtterance ?: return
        if (_state.value.lastFailure?.isRetryable != true) return
        when (_state.value.state) {
            MiraVoiceState.IDLE, MiraVoiceState.SUCCESS, MiraVoiceState.ERROR -> Unit
            MiraVoiceState.LISTENING,
            MiraVoiceState.PROCESSING,
            MiraVoiceState.EXECUTING,
            MiraVoiceState.SPEAKING,
            -> return
        }
        turnJob?.cancel()
        turnJob = scope.launch { deliver(utterance) }
    }

    private fun beginTurn() {
        if (!microphoneUsable()) {
            val reason = if (recognizer.hasPermission()) {
                SpeechFailure.UNAVAILABLE
            } else {
                SpeechFailure.PERMISSION
            }
            turnJob?.cancel()
            turnJob = scope.launch { fail(reason) }
            return
        }
        turnJob?.cancel()
        turnJob = scope.launch { runTurn() }
    }

    private suspend fun runTurn() {
        if (liveSettings.activationSound) earcons.activation()
        _state.update {
            it.copy(
                state = MiraVoiceState.LISTENING,
                amplitude = 0f,
                partialTranscript = null,
                lastFailure = null,
                canListen = true,
            )
        }

        val heard = listen()
        val complete = heard.final?.trim().orEmpty()
        val caught = heard.partial?.trim().orEmpty()
        val failure = heard.failure
        lastUtterance = complete.ifEmpty { caught }.takeIf { it.isNotEmpty() }

        when {
            // A finished utterance is what Abu said, whatever the recognizer did afterwards.
            complete.isNotEmpty() -> deliver(complete)

            // If we caught a partial utterance and the session timed out or had no match, deliver what was heard!
            caught.isNotEmpty() && (failure == SpeechFailure.TIMEOUT || failure == SpeechFailure.NO_MATCH || failure == null) ->
                deliver(caught)

            // A session that broke off part way through has heard half a sentence at best. Saying
            // so and keeping it for [retryLast] is honest; running half a command is not.
            failure != null -> fail(failure)

            // No failure and no final: the session ended because Abu pressed the orb to stop it,
            // and what she caught is exactly what he meant her to work with.
            caught.isNotEmpty() -> deliver(caught)

            // He pressed, said nothing, and pressed again. That is not a failure.
            else -> _state.update {
                it.copy(state = MiraVoiceState.IDLE, amplitude = 0f, partialTranscript = null)
            }
        }
    }

    /**
     * Runs one recognition session to its end and reports what came out of it.
     *
     * The session is a child coroutine so that [onOrbPressed] can end it without ending the turn.
     * Cancelling a child never cancels its parent, which is exactly the behaviour "stop listening
     * and submit what was heard" needs.
     */
    private suspend fun listen(): Heard = coroutineScope {
        var finalText: String? = null
        var partial: String? = null
        var failure: SpeechFailure? = null

        val session = launch {
            val microphone = this
            audioFocus.withFocus(AudioFocusPurpose.LISTENING) {
                recognizer.listen(RecognitionRequest(mode = liveSettings.languageMode))
                    .collect { event ->
                        when (event) {
                            SpeechEvent.Ready, SpeechEvent.BeginningOfSpeech -> Unit

                            is SpeechEvent.Level ->
                                _state.update { it.copy(amplitude = event.rms) }

                            is SpeechEvent.Partial -> {
                                partial = event.text
                                _state.update { it.copy(partialTranscript = event.text) }
                            }

                            is SpeechEvent.Final -> {
                                finalText = event.text
                                // The utterance is complete, so the microphone is released now
                                // rather than at whatever point the engine decides to close.
                                microphone.cancel()
                            }

                            SpeechEvent.EndOfSpeech -> _state.update { it.copy(amplitude = 0f) }

                            is SpeechEvent.Failed -> failure = event.reason
                        }
                    }
            }
        }

        listenJob = session
        session.join()
        listenJob = null
        _state.update { it.copy(amplitude = 0f, partialTranscript = null) }

        // Both are reported. What a half-heard sentence is worth depends on why the session ended,
        // and that is the caller's judgement to make rather than this one's.
        Heard(final = finalText, partial = partial, failure = failure)
    }

    /** Puts one utterance through the pipeline and says the answer. */
    private suspend fun deliver(utterance: String) {
        _state.update {
            it.copy(
                state = MiraVoiceState.PROCESSING,
                amplitude = 0f,
                partialTranscript = null,
                lastFailure = null,
            )
        }

        val outcome = try {
            // The orb moves to EXECUTING the moment interpreting is over and the command is about
            // to be written. A command held for confirmation never reaches that point, so the
            // state Abu sees is the truth about whether his phone is changing.
            execute(utterance, InputSource.VOICE) { onDispatchBegan() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unexpected: Exception) {
            // The pipeline returns its failures as values, so anything thrown here was unforeseen.
            // The turn still has to end: an orb that spins for ever has no way back for Abu.
            fail(SpeechFailure.UNKNOWN)
            return
        }

        settle(outcome)
    }

    /** Marks the crossing from working out what Abu meant to actually writing it down. */
    private fun onDispatchBegan() {
        _state.update { it.copy(state = MiraVoiceState.EXECUTING) }
    }

    /**
     * Ends a turn with what the pipeline actually decided.
     *
     * A confirmation is NOT an answer: Mira has read the command back and is waiting. It gets no
     * completion tone and no SUCCESS flare, because both of those mean the thing is done — and
     * telling Abu a mutating command landed when it has not yet run is the one mistake here that
     * he would have no way to notice.
     */
    private suspend fun settle(outcome: CommandOutcome) {
        execute.logMiraReply(
            text = renderer.display(outcome.response, outcome.language),
            language = outcome.language,
            commandName = outcome.commandName(),
        )

        val awaitingAbu = outcome as? CommandOutcome.NeedsConfirmation
        _state.update {
            it.copy(
                pending = awaitingAbu?.let { needed ->
                    PendingVoiceConfirmation(needed.pending, needed.language)
                },
            )
        }

        if (livePreferences.spokenResponsesEnabled) {
            _state.update { it.copy(state = MiraVoiceState.SPEAKING) }
            // A voice that cannot speak this language does not fail the turn. The command ran and
            // the answer is already in the transcript; going silent is the graceful degradation.
            audioFocus.withFocus(AudioFocusPurpose.SPEAKING) {
                voice.speak(renderer.speak(outcome.response, outcome.language), outcome.language)
            }
        }

        if (awaitingAbu != null) {
            // The question has been asked. The orb goes quiet and the confirmation card is the
            // thing waiting on Abu now, so a press is free to start a new turn.
            _state.update {
                it.copy(state = MiraVoiceState.IDLE, amplitude = 0f, partialTranscript = null)
            }
            return
        }

        // Interrupted while she was speaking — by the orb or by something taking the audio. The
        // turn is over and neither the tone nor the flare belongs to it any more.
        currentCoroutineContext().ensureActive()

        val answered = outcome.isAnswer()
        if (liveSettings.activationSound) {
            if (answered) earcons.completion() else earcons.error()
        }
        hold(if (answered) MiraVoiceState.SUCCESS else MiraVoiceState.ERROR)
    }

    override fun confirmPending() {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        turnJob?.cancel()
        turnJob = scope.launch {
            _state.update { it.copy(state = MiraVoiceState.EXECUTING, lastFailure = null) }
            val outcome = try {
                execute.confirm(pending.request, pending.language)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unexpected: Exception) {
                fail(SpeechFailure.UNKNOWN)
                return@launch
            }
            settle(outcome)
        }
    }

    override fun dismissPending() {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        turnJob?.cancel()
        turnJob = scope.launch {
            val reply = renderer.display(MiraResponse.Abandoned, pending.language)
            execute.logMiraReply(reply, pending.language, null)
            if (livePreferences.spokenResponsesEnabled) {
                _state.update { it.copy(state = MiraVoiceState.SPEAKING) }
                audioFocus.withFocus(AudioFocusPurpose.SPEAKING) {
                    voice.speak(renderer.speak(MiraResponse.Abandoned, pending.language), pending.language)
                }
            }
            _state.update {
                it.copy(state = MiraVoiceState.IDLE, amplitude = 0f, partialTranscript = null)
            }
        }
    }

    private suspend fun fail(failure: SpeechFailure) {
        if (liveSettings.activationSound) earcons.error()
        _state.update { it.copy(lastFailure = failure, canListen = microphoneUsable()) }
        hold(MiraVoiceState.ERROR)
    }

    /** Shows how the turn ended for a moment, then settles. */
    private suspend fun hold(terminal: MiraVoiceState) {
        _state.update {
            it.copy(state = terminal, amplitude = 0f, partialTranscript = null)
        }
        delay(TURN_END_HOLD_MS)
        _state.update { it.copy(state = MiraVoiceState.IDLE) }
    }

    /**
     * Interrupting Mira stops her and nothing else. A press that shuts her up is not also a press
     * that asks her a question, and treating it as both is how an assistant ends up listening to
     * a room that was only trying to make it stop.
     */
    private fun silence() {
        // The turn is ended before the engine is: stopping first would let the reply's own
        // continuation run on and finish the turn with the tone that says it went well.
        turnJob?.cancel()
        turnJob = null
        listenJob = null
        voice.stop()
        _state.update {
            it.copy(state = MiraVoiceState.IDLE, amplitude = 0f, partialTranscript = null)
        }
    }

    /**
     * Something else took the audio — a call, an alarm. Mira gives way rather than competing.
     *
     * The turn ends here, quietly. Stopping her mid-sentence and then sounding the tone that means
     * "that went well" would put a chime into the first second of Abu's phone call, which is the
     * opposite of giving way. The command itself has already run and its answer is already in the
     * transcript, so nothing is lost by ending without ceremony.
     */
    private fun onFocusLost() {
        if (_state.value.state == MiraVoiceState.SPEAKING) silence()
    }

    private fun microphoneUsable(): Boolean = recognizer.isAvailable() && recognizer.hasPermission()

    /** What one recognition session produced, whichever way it ended. */
    private data class Heard(
        /** The utterance the recognizer committed to, or null if it never got that far. */
        val final: String?,
        /** The last thing it was part way through hearing. */
        val partial: String?,
        val failure: SpeechFailure?,
    )
}

/**
 * Whether the turn ended with Mira having answered rather than having given up.
 *
 * A rejected command and a [MiraResponse.Problem] both mean Abu did not get what he asked for, so
 * neither one gets the tone that says something landed. A [CommandOutcome.NeedsConfirmation] is
 * handled before this is ever reached and must never be counted here: it is a question, not an
 * answer, and nothing has run.
 */
private fun CommandOutcome.isAnswer(): Boolean = when (this) {
    is CommandOutcome.Rejected -> false
    is CommandOutcome.NeedsConfirmation -> false
    is CommandOutcome.Completed -> response !is MiraResponse.Problem
}

/**
 * Only a confirmation carries the command that produced it. A one-shot outcome does not, so that
 * turn is logged with no command name rather than with a guessed one.
 */
private fun CommandOutcome.commandName(): String? =
    (this as? CommandOutcome.NeedsConfirmation)?.pending?.command?.let { it::class.simpleName }
