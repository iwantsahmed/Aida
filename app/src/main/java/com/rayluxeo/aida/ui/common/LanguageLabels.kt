package com.rayluxeo.aida.ui.common

import com.rayluxeo.aida.core.common.LanguageMode

/**
 * How the three language modes are written for Abu, in one place.
 *
 * Settings and onboarding each held their own copy of this list, which meant the two pickers
 * could drift apart and show him different words for the same choice. Each label is deliberately
 * written in its own language — বাংলা is not "Bengali" — so the control reads correctly whichever
 * mode is active.
 */
fun LanguageMode.label(): String = when (this) {
    LanguageMode.AUTO -> "Auto"
    LanguageMode.BANGLA -> "বাংলা"
    LanguageMode.ENGLISH -> "English"
}

/** The modes in the order both pickers present them. */
val languageModesInOrder: List<LanguageMode> = listOf(
    LanguageMode.AUTO,
    LanguageMode.BANGLA,
    LanguageMode.ENGLISH,
)

/** The labels for [languageModesInOrder], for controls that take a list of strings. */
val languageLabelsInOrder: List<String> = languageModesInOrder.map { it.label() }
