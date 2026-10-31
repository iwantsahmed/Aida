package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse

/**
 * Turns Mira's structured [MiraResponse] into words. Spoken and written wording differ:
 * speech stays short because a long spoken reply is tiresome, while the transcript can
 * carry a little more detail.
 */
interface ResponseRenderer {
    fun speak(response: MiraResponse, language: SpokenLanguage): String
    fun display(response: MiraResponse, language: SpokenLanguage): String
}
