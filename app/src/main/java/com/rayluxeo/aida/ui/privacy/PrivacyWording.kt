package com.rayluxeo.aida.ui.privacy

import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * Every word the Privacy & Security dashboard puts in front of Abu, in one language: the card
 * titles and bodies, the two state chips, the Gemini and app-lock rows, the erase button and its
 * confirmation dialog, and the notices the view model raises after a toggle or an erase.
 *
 * One phrasebook so the screen and the view model speak with the same voice — a notice saying the
 * erase is done must use the same word for "erased" that the button offered, in whichever language
 * the screen is drawn in. The view model words a notice the moment it sets it (§5), and the screen
 * resolves the rest from the language it is currently showing.
 */
internal interface PrivacyWording {

    val dashboardTitle: String

    // --- Offline-first architecture card -------------------------------------------------------

    val offlineArchitectureTitle: String
    val offlineArchitectureBody: String
    val cloudDisabledChip: String
    val onDeviceNluChip: String

    // --- Cloud AI (Gemini) card ----------------------------------------------------------------

    val cloudAiTitle: String
    val geminiEngineTitle: String
    val geminiActiveSubtitle: String
    val geminiOffSubtitle: String

    // --- App-lock card -------------------------------------------------------------------------

    val appLockTitle: String
    val appLockRowTitle: String
    val appLockOnSubtitle: String
    val appLockOffSubtitle: String

    // --- Destructive data actions --------------------------------------------------------------

    val destructiveActionsTitle: String
    val eraseAllButton: String
    val eraseDialogTitle: String
    val eraseDialogMessage: String
    val eraseDialogConfirm: String
    val eraseDialogCancel: String

    // --- Notices the view model raises ---------------------------------------------------------

    val geminiDisabledNotice: String
    val geminiKeyRequiredNotice: String
    val appLockOnNotice: String
    val appLockOffNotice: String
    val dataErasedNotice: String
    val eraseFailedNotice: String
}

internal fun privacyWording(language: SpokenLanguage): PrivacyWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliPrivacyWording
    SpokenLanguage.ENGLISH -> EnglishPrivacyWording
}
