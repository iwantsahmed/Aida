package com.rayluxeo.aida.ui.mira

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandOutcome
import com.rayluxeo.aida.domain.command.ConfirmationRequest
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Contact
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.voice.MiraEarconPlayer
import com.rayluxeo.aida.voice.MiraTextToSpeech
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.SpeakingState
import com.rayluxeo.aida.voice.SpeechFailure
import com.rayluxeo.aida.voice.VoiceConversationController
import com.rayluxeo.aida.voice.VoiceSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the one screen where Mira actually answers.
 *
 * The spoken turn is no longer here. [controller] owns listening, interpreting what it heard,
 * carrying it out and reading the answer back, because that sequence has real interruption rules
 * and they have to live in one place. This view model observes that state and forwards the press;
 * it never decides what a press means, and it never touches a recognizer.
 *
 * What is left is the other half of the screen, and it is the half that must never depend on
 * speech: a line Abu types travels the same pipeline, is confirmed the same way, and is answered
 * out loud under the same setting. With the microphone denied or missing, nothing here changes.
 *
 * A third rule governs what she prepares: a call, a message and a permission are offered as a
 * [MiraAction] and go no further. This view model never dials, never sends and never grants —
 * the screen owns the launchers and reports back what Android said.
 */
@HiltViewModel
class MiraViewModel @Inject constructor(
    private val execute: ExecuteCommandUseCase,
    private val controller: VoiceConversationController,
    private val tts: MiraTextToSpeech,
    private val earcons: MiraEarconPlayer,
    private val renderer: ResponseRenderer,
    private val entry: MiraEntryCoordinator,
    conversation: ConversationRepository,
    settings: SettingsRepository,
    voicePreferences: VoicePreferenceRepository,
    private val conversationThreads: com.rayluxeo.aida.domain.repository.ConversationThreadRepository? = null,
    private val contextManager: com.rayluxeo.aida.domain.context.ConversationContextManager? = null,
    private val shareLauncher: com.rayluxeo.aida.ui.invoices.delivery.AndroidInvoiceShareLauncher? = null,
    private val recordDelivery: com.rayluxeo.aida.domain.usecase.RecordInvoiceDeliveryUseCase? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(MiraUiState())
    val state: StateFlow<MiraUiState> = _state.asStateFlow()

    /**
     * What the screen was opened to do — a question to drop into the composer, or a request to
     * start listening. Held by the application-scoped coordinator because it is usually set before
     * this view model exists; the screen reads it on arrival and calls [onEntryHandled].
     */
    val entryIntent: StateFlow<MiraEntryIntent?> = entry.pending

    /** The screen has acted on the entry intent; clear it so returning to the tab does not replay it. */
    fun onEntryHandled() {
        entry.consume()
    }

    /** The last settings Mira read. Written only from [viewModelScope], which is single-threaded. */
    private val liveSettings = MutableStateFlow(MiraSettings())
    private val voicePrefs = MutableStateFlow(VoicePreferences.DEFAULT)

    /**
     * What Abu last asked for, kept so that a permission he grants can finish the thing he
     * actually wanted rather than leaving him to say it a second time.
     */
    private var lastAttempt: Attempt? = null

    /**
     * A failure he has already read and waved away. The controller goes on reporting it — it is
     * the last thing that happened — so without this the notice would spring straight back.
     */
    private var dismissedFailure: SpeechFailure? = null

    init {
        viewModelScope.launch {
            conversation.observeRecent().collect { turns ->
                _state.update {
                    it.copy(
                        transcript = if (turns.isEmpty()) {
                            TranscriptState.Empty
                        } else {
                            TranscriptState.Content(turns)
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { current ->
                liveSettings.value = current
                _state.update { it.copy(languageMode = current.languageMode) }
            }
        }
        viewModelScope.launch {
            voicePreferences.preferences.collect { voicePrefs.value = it }
        }
        viewModelScope.launch {
            tts.state.collect { speaking ->
                _state.update { it.copy(speaking = speaking == SpeakingState.SPEAKING) }
            }
        }
        viewModelScope.launch {
            controller.state.collect(::onVoiceSession)
        }
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
    }

    /** Sends whatever Abu typed. The only difference from speaking is the [InputSource]. */
    fun sendDraft() {
        val text = _state.value.draft.trim()
        if (text.isEmpty() || _state.value.working) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch { submit(text, InputSource.TEXT) }
    }

    /**
     * The orb, forwarded and nothing more.
     *
     * What a press means depends on what Mira is doing, and that decision belongs to the
     * controller: a tap stops her while she speaks or listens, and starts listening when she is
     * idle. Making that call a second time here is how the two would start disagreeing. Nothing
     * spoken originates in this view model — a typed reply is read on the screen, never aloud — so
     * there is nothing of its own to release first.
     */
    fun onOrbPressed() {
        controller.onOrbPressed()
    }

    /**
     * The screen is going away.
     *
     * An open microphone is closed, because the controller's scope is the application's and
     * nothing else would ever end that session — Abu would walk away with the microphone live.
     * A command already in flight is deliberately left alone: it is writing something he asked
     * for, and abandoning it half way is worse than finishing it unobserved.
     */
    fun onScreenLeft() {
        if (_state.value.voice.state == MiraVoiceState.LISTENING) controller.cancel()
    }

    /** Says the last utterance again. Only ever reachable from a retryable failure. */
    fun retryVoice() {
        controller.retryLast()
    }

    /**
     * The platform's own answer about the microphone, asked fresh on every resume: Abu may have
     * granted it on a settings screen and walked straight back in.
     */
    fun onMicrophoneChecked(granted: Boolean) {
        _state.update { current ->
            if (granted) {
                current.copy(
                    microphone = MicrophoneAccess.GRANTED,
                    notice = current.notice.takeUnless { it.isAboutTheMicrophone() },
                )
            } else if (current.microphone == MicrophoneAccess.GRANTED) {
                // Taken back while Mira was running. Android will raise the dialog again.
                current.copy(microphone = MicrophoneAccess.ASKABLE)
            } else {
                current
            }
        }
    }

    /**
     * Called once the screen knows whether it may use the microphone.
     *
     * A grant finishes the press that asked for it, because starting to listen is what Abu was
     * doing when Android interrupted him. A refusal is a state Mira shows rather than a dead end —
     * the text field is still right there — and one Android will not raise again leaves the
     * settings screen as the only route, which is what gets offered.
     */
    fun onMicrophonePermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (granted) {
            dismissedFailure = null
            _state.update { it.copy(microphone = MicrophoneAccess.GRANTED, notice = null) }
            controller.onOrbPressed()
            return
        }
        val access = if (canAskAgain) MicrophoneAccess.ASKABLE else MicrophoneAccess.SETTINGS_ONLY
        // Worded before the update rather than inside it: the language is read back off the state
        // this call is about to replace, and `update` is free to run its lambda more than once.
        val refusal = MiraNotice(
            text = speechFailureWording(SpeechFailure.PERMISSION, noticeLanguage()),
            offer = access.microphoneOffer(),
        )
        _state.update { it.copy(microphone = access, notice = refusal) }
    }

    fun confirmPending() {
        val pending = _state.value.pending ?: return
        if (pending.spoken) {
            controller.confirmPending()
            return
        }
        val attempt = Attempt.Confirmed(pending.request, pending.language)
        lastAttempt = attempt
        _state.update { it.copy(pending = null) }
        viewModelScope.launch { carryOut(attempt) }
    }

    /** Drops the command and says so, so the transcript records that nothing happened. */
    fun cancelPending() {
        val pending = _state.value.pending ?: return
        if (pending.spoken) {
            controller.dismissPending()
            return
        }
        _state.update { it.copy(pending = null) }
        viewModelScope.launch {
            execute.logMiraReply(
                text = renderer.display(MiraResponse.Abandoned, pending.language),
                language = pending.language,
                commandName = pending.request.command.commandName(),
            )
        }
    }

    fun dismissNotice() {
        dismissedFailure = _state.value.voice.lastFailure
        _state.update { it.copy(notice = null) }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            contextManager?.clear()
            conversationThreads?.create(null)
            _state.update { it.copy(pending = null, action = null) }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            conversationThreads?.delete(id)
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            contextManager?.clear()
            conversationThreads?.deleteAll()
            _state.update { it.copy(pending = null, action = null) }
        }
    }

    fun observeThreads(): kotlinx.coroutines.flow.Flow<List<com.rayluxeo.aida.domain.model.Conversation>> =
        conversationThreads?.observeThreads() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    /** One past thread's messages, for reading it back in the history sheet. A read path only. */
    fun observeThreadMessages(id: Long): kotlinx.coroutines.flow.Flow<List<com.rayluxeo.aida.domain.model.ConversationTurn>> =
        conversationThreads?.observeMessages(id) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun searchConversations(query: String): List<com.rayluxeo.aida.domain.model.ConversationSearchHit> =
        conversationThreads?.search(query)?.let {
            if (it is com.rayluxeo.aida.core.common.AppResult.Success) it.value else emptyList()
        }.orEmpty()

    fun onInvoiceDeliveryConfirmed(context: android.content.Context, draft: com.rayluxeo.aida.domain.model.InvoiceDeliveryDraft) {
        shareLauncher?.launchDelivery(context, draft)
        viewModelScope.launch {
            recordDelivery?.invoke(draft, com.rayluxeo.aida.domain.model.InvoiceDeliveryStatus.SHARED)
            _state.update { it.copy(action = null) }
        }
    }

    fun onInvoiceDeliveryDismissed() {
        _state.update { it.copy(action = null) }
    }

    /**
     * Called once Android has answered the grant Mira asked for.
     *
     * A grant is answered by finishing what Abu asked for in the first place — he wanted the
     * call, not the permission — and a refusal Android will not raise again moves the control to
     * the settings screen, which by then is the only route there is.
     */
    fun onCapabilityResult(capability: MiraCapability, granted: Boolean, canAskAgain: Boolean) {
        if ((_state.value.action as? MiraAction.Grant)?.capability != capability) return
        if (!granted) {
            _state.update {
                it.copy(action = MiraAction.Grant(capability, viaSettings = !canAskAgain))
            }
            return
        }
        _state.update { it.copy(action = null, notice = null) }
        retry()
    }

    /**
     * Nothing installed on this phone could take what the control was holding out. It stops being
     * offered rather than staying on screen doing nothing.
     */
    fun onActionUnavailable() {
        val notice = MiraNotice(render(MiraResponse.Problem(MiraErrorCode.UNKNOWN)))
        _state.update { it.copy(action = null, notice = notice) }
    }


    /**
     * The spoken turn, copied rather than interpreted.
     *
     * The one judgement made here is about the failure: it is turned into words in Abu's language
     * and given at most one way forward, and a failure he has already dismissed is left alone.
     */
    private fun onVoiceSession(voice: VoiceSessionState) {
        val underWay = voice.state.isATurnInProgress()
        if (underWay) dismissedFailure = null
        val failure = voice.lastFailure?.takeUnless { underWay || it == dismissedFailure }
        val notice = failure?.let {
            MiraNotice(text = speechFailureWording(it, noticeLanguage()), offer = it.offer())
        }
        _state.update { current ->
            current.copy(
                voice = voice,
                // A turn getting under way clears what was said about the last one, and drops what
                // the reply before it had prepared — the same rule a typed line follows.
                notice = when {
                    underWay -> null
                    notice != null -> notice
                    else -> current.notice
                },
                // The controller's confirmation is mirrored into the one card this screen draws.
                // A spoken pending that has gone is gone: the controller resolved it, and keeping
                // this copy would leave a dead card offering to run a command twice.
                pending = when {
                    voice.pending != null -> PendingConfirmation(
                        summary = renderer.display(voice.pending.request.summary, voice.pending.language),
                        request = voice.pending.request,
                        language = voice.pending.language,
                        spoken = true,
                    )
                    current.pending?.spoken == true -> null
                    underWay -> null
                    else -> current.pending
                },
                action = if (underWay) null else current.action,
                // The recognizer refusing for want of permission is Android's answer too, and it
                // outranks whatever this screen last read off the platform.
                microphone = if (failure == SpeechFailure.PERMISSION) {
                    current.microphone.withoutTheMicrophone()
                } else {
                    current.microphone
                },
            )
        }
    }


    private suspend fun submit(text: String, source: InputSource) {
        lastAttempt = Attempt.Spoken(text, source)
        _state.update { it.copy(working = true, notice = null, pending = null, action = null) }
        val outcome = execute(text, source)
        handle(outcome, outcome.commandName())
        _state.update { it.copy(working = false) }
    }

    /** Runs a command Abu has already agreed to, whether he has just said yes or just granted. */
    private suspend fun carryOut(attempt: Attempt.Confirmed) {
        _state.update { it.copy(working = true, notice = null, action = null) }
        handle(
            outcome = execute.confirm(attempt.request, attempt.language),
            commandName = attempt.request.command.commandName(),
        )
        _state.update { it.copy(working = false) }
    }

    /**
     * Repeats the last thing Abu asked for, now that Mira is allowed to do it. A command he had
     * already agreed to is re-run as the command, so a grant never makes him say yes twice.
     */
    private fun retry() {
        val attempt = lastAttempt ?: return
        viewModelScope.launch {
            when (attempt) {
                is Attempt.Spoken -> submit(attempt.text, attempt.source)
                is Attempt.Confirmed -> carryOut(attempt)
            }
        }
    }

    private suspend fun handle(outcome: CommandOutcome, commandName: String?) {
        val written = renderer.display(outcome.response, outcome.language)
        execute.logMiraReply(written, outcome.language, commandName)

        when (outcome) {
            is CommandOutcome.Completed -> {
                _state.update {
                    it.copy(
                        lastEngine = outcome.engine,
                        pending = null,
                        action = outcome.response.offered(),
                    )
                }
                if (outcome.response !is MiraResponse.Problem && liveSettings.value.activationSound) {
                    earcons.completion()
                }
            }

            is CommandOutcome.NeedsConfirmation -> _state.update {
                it.copy(
                    pending = PendingConfirmation(written, outcome.pending, outcome.language),
                    lastEngine = outcome.pending.engine,
                    action = null,
                )
            }

            is CommandOutcome.Rejected -> _state.update { it.copy(pending = null, action = null) }
        }
        // A typed reply is read on the screen, not out loud. Speaking belongs to a spoken turn, which
        // the controller owns; a line Abu typed he is already reading, so reading it back to him is
        // noise. Only a question he asked by voice gets a spoken answer.
    }

    private fun render(response: MiraResponse): String = renderer.display(response, noticeLanguage())

    /**
     * A notice belongs to no particular utterance, so it is worded in whatever language the
     * screen itself is speaking. That rule lives on [MiraUiState.language], beside the transcript
     * it reads from, so a notice and the status line above it can never disagree.
     */
    private fun noticeLanguage(): SpokenLanguage = _state.value.language

    /**
     * The one way forward a speech failure has, or none.
     *
     * Retry is offered only where saying the same thing again could genuinely work. A microphone
     * that is switched off is not one of those: nothing is retried until Android has been asked.
     */
    private fun SpeechFailure.offer(): NoticeOffer? = when {
        this == SpeechFailure.PERMISSION -> _state.value.microphone.microphoneOffer()
        isRetryable -> NoticeOffer.RETRY
        else -> null
    }

    /**
     * The one thing Mira would do again if Android stopped standing in the way. The two shapes
     * differ in where the command comes from: a spoken line has to travel the whole pipeline
     * again, while a command Abu already agreed to is re-run as itself.
     */
    private sealed interface Attempt {
        data class Spoken(val text: String, val source: InputSource) : Attempt

        data class Confirmed(
            val request: ConfirmationRequest,
            val language: SpokenLanguage,
        ) : Attempt
    }
}

/**
 * True while Mira is part way through a spoken turn.
 *
 * What she last failed at stops being worth reading the moment she is doing something about it,
 * and a retry goes straight from ERROR to PROCESSING without passing through LISTENING, so the
 * test cannot simply be "is she listening".
 */
private fun MiraVoiceState.isATurnInProgress(): Boolean = when (this) {
    MiraVoiceState.LISTENING,
    MiraVoiceState.PROCESSING,
    MiraVoiceState.EXECUTING,
    MiraVoiceState.SPEAKING,
    -> true

    MiraVoiceState.IDLE, MiraVoiceState.SUCCESS, MiraVoiceState.ERROR -> false
}

/**
 * A grant this screen believed in, contradicted by the recognizer. Android will still raise the
 * dialog — nothing has said otherwise — so the orb stays live and a press asks again.
 */
private fun MicrophoneAccess.withoutTheMicrophone(): MicrophoneAccess =
    if (this == MicrophoneAccess.GRANTED) MicrophoneAccess.ASKABLE else this

/** Where the microphone can still be granted from, given what Android has already decided. */
private fun MicrophoneAccess.microphoneOffer(): NoticeOffer? = when (this) {
    MicrophoneAccess.GRANTED -> null
    MicrophoneAccess.ASKABLE -> NoticeOffer.ALLOW_MICROPHONE
    MicrophoneAccess.SETTINGS_ONLY -> NoticeOffer.MICROPHONE_SETTINGS
}

/** A granted microphone answers the notice that asked for it, so that notice goes. */
private fun MiraNotice?.isAboutTheMicrophone(): Boolean =
    this?.offer == NoticeOffer.ALLOW_MICROPHONE || this?.offer == NoticeOffer.MICROPHONE_SETTINGS

/**
 * The control, if any, that a reply hands to the screen.
 *
 * Only three of Mira's replies prepare something she will not finish herself, and each of them
 * is a deliberate stop: she has the number, the draft or the refusal, and the next movement is
 * Abu's.
 */
private fun MiraResponse.offered(): MiraAction? = when (this) {
    is MiraResponse.CallPrepared -> contact.dialable()?.let { MiraAction.Call(it) }
    is MiraResponse.MessagePrepared -> contact.dialable()?.let { MiraAction.Message(it, body, app) }
    is MiraResponse.InvoiceDeliveryPrepared -> MiraAction.DeliverInvoice(draft)
    is MiraResponse.PermissionNeeded -> MiraAction.Grant(
        capability = capability,
        // Android has no dialog for exact alarms; its settings toggle is the only way in.
        viaSettings = capability == MiraCapability.EXACT_ALARMS,
    )

    else -> null
}

/**
 * A contact with no number is not a control. Mira says she has the person and not the number,
 * which is more use than a button that opens an empty dialler.
 */
private fun Contact.dialable(): String? = phoneNumber?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Only a confirmation carries the command that produced it. A one-shot outcome does not, so that
 * turn is logged with no command name rather than with a guessed one.
 */
private fun CommandOutcome.commandName(): String? =
    (this as? CommandOutcome.NeedsConfirmation)?.pending?.command?.commandName()

private fun AppCommand.commandName(): String? = this::class.simpleName
