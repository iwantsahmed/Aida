package com.rayluxeo.aida.assistant.share

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.assistant.SharedPayload
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandOutcome
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the sheet another app's share sheet opens.
 *
 * It is a second door into the one pipeline, not a second pipeline. Whatever is in the request
 * field goes through [ExecuteCommandUseCase] exactly as a line typed on the Mira screen does:
 * normalised, logged as Abu's own words, routed local-first, validated, confirmed if it is
 * mutating and uncertain, and answered with a structured response this view model renders.
 *
 * Two things it deliberately does not do. It never speaks: Abu opened this from inside another
 * app, often from a text selection, and a reply read aloud over what he was doing is an
 * interruption he did not ask for. And it never writes anything of its own — the shared content
 * is shown and offered, and nothing about it is stored unless a command Abu sent stores it.
 */
@HiltViewModel
class ShareToMiraViewModel @Inject constructor(
    private val reader: SharedPayloadReader,
    private val execute: ExecuteCommandUseCase,
    private val renderer: ResponseRenderer,
    private val settings: SettingsRepository,
    private val conversation: ConversationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareToMiraUiState())
    val state: StateFlow<ShareToMiraUiState> = _state.asStateFlow()

    /**
     * The share is read once. A configuration change recomposes the sheet with the same intent,
     * and reading it again would ask the resolver for a name Mira already has and reset a request
     * Abu had started writing.
     */
    private var taken = false

    init {
        viewModelScope.launch {
            val language = startingLanguage()
            // An outcome carries the language of the exchange that produced it and outranks this
            // opening guess, so a reply that landed while the settings read was still in flight
            // is not reworded underneath Abu.
            _state.update { if (it.reply == null && it.pending == null) it.copy(language = language) else it }
        }
    }

    /** Called once with the intent that opened the sheet. */
    fun onShared(intent: Intent) {
        if (taken) return
        taken = true
        viewModelScope.launch {
            when (val intake = reader.read(intent)) {
                is SharedIntake.Unsupported -> _state.update {
                    it.copy(stage = ShareStage.Unsupported(intake.reason, intake.declaredType))
                }

                is SharedIntake.Ready -> _state.update {
                    it.copy(
                        stage = ShareStage.Ready(intake.payload, intake.names),
                        request = openingRequest(intake.payload),
                        suggestionSeed = seedFor(intake.payload, intake.names),
                    )
                }
            }
        }
    }

    fun onRequestChange(value: String) {
        _state.update { it.copy(request = value) }
    }

    /** Sends whatever is in the field. The only difference from the Mira screen is where it came from. */
    fun send() {
        val text = _state.value.request.trim()
        if (text.isEmpty() || _state.value.working) return
        // Marked busy before the coroutine starts, so a second tap that arrives in the same frame
        // as the first cannot run the same command twice.
        _state.update { it.copy(working = true, reply = null, replyIsProblem = false, pending = null) }
        viewModelScope.launch {
            // SHARED, not TEXT: this text came from another app, and the router refuses the cloud
            // engine for it. Marking it TEXT would make it indistinguishable from Abu's own words.
            val outcome = execute(text, InputSource.SHARED)
            handle(outcome, outcome.pendingCommandName())
            _state.update { it.copy(working = false) }
        }
    }

    fun confirmPending() {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(working = true, pending = null) }
        viewModelScope.launch {
            handle(
                outcome = execute.confirm(pending.request, pending.language),
                commandName = pending.request.command.commandName(),
            )
            _state.update { it.copy(working = false) }
        }
    }

    /** Drops the command and says so, so the transcript records that nothing happened. */
    fun cancelPending() {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        viewModelScope.launch {
            val written = renderer.display(MiraResponse.Abandoned, pending.language)
            execute.logMiraReply(written, pending.language, pending.request.command.commandName())
            _state.update { it.copy(reply = written, replyIsProblem = false) }
        }
    }

    private suspend fun handle(outcome: CommandOutcome, commandName: String?) {
        val written = renderer.display(outcome.response, outcome.language)
        execute.logMiraReply(written, outcome.language, commandName)

        _state.update { current ->
            when (outcome) {
                is CommandOutcome.Completed -> current.copy(
                    language = outcome.language,
                    // The field is cleared so a second ask starts from nothing rather than from a
                    // sentence Mira has already carried out.
                    request = "",
                    reply = written,
                    replyIsProblem = outcome.response is MiraResponse.Problem,
                    pending = null,
                )

                is CommandOutcome.NeedsConfirmation -> current.copy(
                    language = outcome.language,
                    reply = null,
                    replyIsProblem = false,
                    pending = ShareConfirmation(written, outcome.pending, outcome.language),
                )

                is CommandOutcome.Rejected -> current.copy(
                    language = outcome.language,
                    reply = written,
                    replyIsProblem = true,
                    pending = null,
                )
            }
        }
    }

    /**
     * What the field holds before Abu touches it.
     *
     * A run of text he selected is as often a request as it is content — "kal shokal 10 tay Ahmed
     * ke call" arrives here from a chat — so it starts in the field where one tap sends it or he
     * writes in front of it. A link or a file is neither, so the field starts empty and the
     * suggestions offer the sentence instead.
     */
    private fun openingRequest(payload: SharedPayload): String = when (payload) {
        is SharedPayload.Text -> payload.text
        is SharedPayload.Link, is SharedPayload.Documents -> ""
    }

    /**
     * The words of the payload, for a suggestion to build a sentence round.
     *
     * A link with no title falls back to its host rather than to the whole address, because the
     * address does not survive the normalizer and a note reading "https developer android com
     * guide" is worse than one reading "developer android com".
     */
    private fun seedFor(payload: SharedPayload, names: List<String>): String = when (payload) {
        is SharedPayload.Text -> payload.text.trim()
        is SharedPayload.Link -> payload.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: SharedText.hostOf(payload.url)

        is SharedPayload.Documents -> names
            .mapNotNull { it.substringBeforeLast('.').trim().takeIf(String::isNotEmpty) }
            .joinToString(separator = ", ")
    }

    /**
     * The language the sheet opens in.
     *
     * AUTO follows the same rule the Mira screen's notices do — whichever language the last
     * exchange happened in, and English on a transcript that is still empty — so a share sheet
     * and the app itself never disagree about which language Abu is in. From the first reply
     * onwards the outcome carries its own language and that one wins.
     */
    private suspend fun startingLanguage(): SpokenLanguage = when (settings.current().languageMode) {
        LanguageMode.BANGLA -> SpokenLanguage.BENGALI
        LanguageMode.ENGLISH -> SpokenLanguage.ENGLISH
        LanguageMode.AUTO -> conversation.observeRecent(limit = 1).first().lastOrNull()?.language
            ?: SpokenLanguage.ENGLISH
    }
}

/**
 * Only a confirmation carries the command that produced it. A one-shot outcome does not, so that
 * turn is logged with no command name rather than with a guessed one.
 */
private fun CommandOutcome.pendingCommandName(): String? =
    (this as? CommandOutcome.NeedsConfirmation)?.pending?.command?.commandName()

private fun AppCommand.commandName(): String? = this::class.simpleName
