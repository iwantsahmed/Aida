package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.domain.model.ConversationStyle

/**
 * How much Mira says around an answer, read at the moment a reply is worded.
 *
 * The renderer's [ResponseRenderer.speak] and [ResponseRenderer.display] are synchronous, so they
 * cannot suspend on [com.rayluxeo.aida.domain.repository.SettingsRepository]. This port lets them
 * read the current [ConversationStyle] without blocking; the production implementation caches the
 * latest setting (see [SettingsConversationStyleProvider]).
 *
 * Public only so it can sit in [MiraResponseRenderer]'s public constructor signature; it is wired
 * and read entirely inside `ui/response`.
 */
interface ConversationStyleProvider {
    fun current(): ConversationStyle

    companion object {
        /**
         * Mira's balanced wording, with no settings wired. This is the default the renderer falls
         * back to in tests and previews, so the shape of a reply never depends on a DataStore read
         * that a unit test did not set up — [ConversationStyle.BALANCED] is today's behaviour.
         */
        val Balanced: ConversationStyleProvider = object : ConversationStyleProvider {
            override fun current(): ConversationStyle = ConversationStyle.BALANCED
        }
    }
}
