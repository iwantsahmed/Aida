package com.rayluxeo.aida.assistant.share

import com.rayluxeo.aida.assistant.SharedPayload
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.ConfirmationRequest

/**
 * The three honest shapes of an incoming share.
 *
 * [Reading] is not the same state as an empty share: the resolver is asked for a file's name and
 * its type on the way in, and flashing "nothing came through" during that lookup would say
 * something untrue about a share that is perfectly good.
 */
sealed interface ShareStage {
    data object Reading : ShareStage

    /** [names] are the file names, in [SharedPayload.Documents] order. Empty for text and links. */
    data class Ready(val payload: SharedPayload, val names: List<String>) : ShareStage

    data class Unsupported(val reason: UnsupportedShare, val declaredType: String?) : ShareStage
}

/**
 * A command Mira understood from the sheet but will not carry out until Abu says yes.
 *
 * [summary] is already rendered in [language], so the sheet never reaches back into the response
 * layer to find out what it is asking about.
 */
data class ShareConfirmation(
    val summary: String,
    val request: ConfirmationRequest,
    val language: SpokenLanguage,
)

/**
 * Everything the Ask Mira sheet draws.
 *
 * [request] is the whole of what travels the pipeline. A suggestion writes into it rather than
 * sending on Abu's behalf, so what the transcript records as his words is always what was on
 * screen in front of him — there is no hidden sentence composed behind the field.
 *
 * [suggestionSeed] is the part of the payload worth putting into such a sentence: the words of a
 * run of text, a page's title, a file's name. It is empty when the share has no words at all, and
 * the suggestions are then not offered rather than offered with nothing in them.
 */
data class ShareToMiraUiState(
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val stage: ShareStage = ShareStage.Reading,
    val request: String = "",
    val suggestionSeed: String = "",
    val working: Boolean = false,
    val pending: ShareConfirmation? = null,
    val reply: String? = null,
    /** True when [reply] is Mira saying she could not, so the sheet colours it as a failure. */
    val replyIsProblem: Boolean = false,
) {
    val canAsk: Boolean get() = stage is ShareStage.Ready

    val canSend: Boolean get() = canAsk && request.isNotBlank() && !working && pending == null

    val canSuggest: Boolean get() = canAsk && suggestionSeed.isNotBlank() && !working

    /**
     * Whether the sheet may be closed right now.
     *
     * It may not while a command is in flight. This activity owns the scope that command runs in,
     * so dismissing the sheet mid-write cancels a record Abu asked for half way through — the one
     * case where letting a swipe through would lose something real.
     */
    val dismissible: Boolean get() = !working
}
