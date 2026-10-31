package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import javax.inject.Inject

/**
 * The only place Mira's replies turn into words.
 *
 * The clock is injected because half of what makes a reply sound human is saying "tomorrow"
 * instead of a date, and that judgement depends on when the reply is being made.
 *
 * [style] decides how much Mira says around an answer, never what the answer is: the figure, the
 * fact and the outcome are identical across styles, and only the framing changes. It defaults to
 * [ConversationStyleProvider.Balanced] so a direct `MiraResponseRenderer(clock)` — the entry point
 * tests and previews use — keeps today's wording; Hilt supplies the settings-backed provider.
 */
class MiraResponseRenderer @Inject constructor(
    clock: MiraClock,
    private val style: ConversationStyleProvider = ConversationStyleProvider.Balanced,
) : ResponseRenderer {

    private val times = MiraTimes(clock)
    private val english = EnglishPhrasebook(times)
    private val bengali = BengaliPhrasebook(times)

    override fun speak(response: MiraResponse, language: SpokenLanguage): String =
        phrasebook(language).speak(response, style.current())

    override fun display(response: MiraResponse, language: SpokenLanguage): String =
        phrasebook(language).display(response, style.current())

    private fun phrasebook(language: SpokenLanguage): Phrasebook = when (language) {
        SpokenLanguage.BENGALI -> bengali
        SpokenLanguage.ENGLISH -> english
    }
}
