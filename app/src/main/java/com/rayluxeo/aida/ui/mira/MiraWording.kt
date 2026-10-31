package com.rayluxeo.aida.ui.mira

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraCapability

/**
 * Every word Mira's own screen puts in front of Abu, in the language he is speaking.
 *
 * This screen was the last one still written in English only, which is the defect `AGENTS.md` §12
 * names outright: a Bengali exchange read "Listening" above it and offered "Yes, do it" below it.
 * The transcript itself was always bilingual — [com.rayluxeo.aida.ui.response.ResponseRenderer]
 * words every reply — so the chrome around it was the one place his own sentence changed language
 * halfway down the screen.
 *
 * Nothing here is a MiraResponse and nothing here belongs in `ui/response`: a status line, a
 * placeholder and the label on a control exist whether or not a command has been interpreted, so
 * there is no outcome for a phrasebook entry to hang on. They follow the same shape all the same —
 * one interface, one object per language, one selector.
 */
/** One starter chip: what it reads, and what it puts in the field when tapped. */
data class MiraSuggestionText(val label: String, val draft: String)

internal interface MiraWording {

    val title: String

    /** Accessibility labels for the wordmark-bar controls. */
    val conversationHistory: String
    val settings: String

    // --- the reference hero, the suggestions and the ask bar ---
    /** The stacked caps beside the headline: four short words. */
    val alsoTry: String
    val askAnything: String
    /** Starter prompts. Each fills the field with [MiraSuggestionText.draft] rather than firing blind. */
    val suggestions: List<MiraSuggestionText>
    /** A short 'what she did' label for a turn that produced a command, or null. */
    fun commandResult(commandName: String): String?

    // ---------------------------------------------------------------- the status line

    val listening: String

    val thinking: String

    val carryingOut: String

    val speaking: String

    /** Said once, quietly, and never as a failure: typing does everything speech does. */
    val microphoneOff: String

    val answeredOnDevice: String

    val answeredByGemini: String

    val listeningStopped: String

    val tapToSpeak: String

    // ---------------------------------------------------------------- the composer

    val send: String

    // ---------------------------------------------------------------- notices

    val dismiss: String

    val tryAgain: String

    val allowMicrophone: String

    val openMicrophoneSettings: String

    // ---------------------------------------------------------------- the confirmation gate

    val confirmYes: String

    val confirmNo: String

    // ---------------------------------------------------------------- the mark, for TalkBack

    /**
     * The avatar carries no glyph, so everything a press means — including why it would do
     * nothing — has to be said here instead.
     */
    val speakToMira: String

    val stopListening: String

    val stopMira: String

    val speechOff: String

    // ---------------------------------------------------------------- what a reply prepared

    val openDialler: String

    val openMessage: String

    val openWhatsApp: String

    val openAppSettings: String

    /** What Mira is asking Android for, named the way Abu would name it rather than by permission. */
    fun grant(capability: MiraCapability): String
}

internal fun miraWording(language: SpokenLanguage): MiraWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishMiraWording
    SpokenLanguage.BENGALI -> BengaliMiraWording
}
