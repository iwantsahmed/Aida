package com.rayluxeo.aida.ui.privacy

/** The dashboard in English. Plain sentences, nothing promised and nothing dated. */
internal object EnglishPrivacyWording : PrivacyWording {

    override val dashboardTitle: String = "Privacy & Security Dashboard"

    override val offlineArchitectureTitle: String = "Offline-First Privacy Architecture"
    override val offlineArchitectureBody: String =
        "Aida operates 100% offline-first. All database records, notes, vault entries, and " +
            "audio recordings remain strictly local on device."
    override val cloudDisabledChip: String = "Cloud: Disabled"
    override val onDeviceNluChip: String = "On-Device NLU: Active"

    override val cloudAiTitle: String = "Cloud AI (Gemini)"
    override val geminiEngineTitle: String = "Gemini AI Engine"
    override val geminiActiveSubtitle: String = "Active via free key"
    override val geminiOffSubtitle: String = "Switched off"

    override val appLockTitle: String = "App Lock"
    override val appLockRowTitle: String = "Require unlock to open Aida"
    override val appLockOnSubtitle: String = "Fingerprint or screen lock, on every launch"
    override val appLockOffSubtitle: String = "Anyone with the phone can open Aida"

    override val destructiveActionsTitle: String = "Destructive Data Actions"
    override val eraseAllButton: String = "Erase All Local Data"
    override val eraseDialogTitle: String = "Erase all local data?"
    override val eraseDialogMessage: String =
        "This permanently removes every task, note, vault item, invoice, voice note, scan, " +
            "file and preference from this device. There is no backup, and it cannot be undone."
    override val eraseDialogConfirm: String = "Erase everything"
    override val eraseDialogCancel: String = "Cancel"

    override val geminiDisabledNotice: String = "Gemini AI disabled"
    override val geminiKeyRequiredNotice: String = "Gemini key required in Settings"
    override val appLockOnNotice: String = "App lock on. Aida asks on next launch."
    override val appLockOffNotice: String = "App lock off"
    override val dataErasedNotice: String = "All local data erased"
    override val eraseFailedNotice: String = "Couldn't finish erasing local data."
}
