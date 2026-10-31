package com.rayluxeo.aida.ui.navigation

/** The shell in English. Short labels, plain sentences, nothing promised and nothing dated. */
internal object EnglishShellWording : ShellWording {

    override fun tabLabel(tab: MiraTab): String = when (tab) {
        MiraTab.HOME -> "Home"
        MiraTab.MIRA -> "Aida"
        MiraTab.TASKS -> "Tasks"
        MiraTab.VAULT -> "Vault"
    }

    override val back: String = "Back"

    override val notBuiltYet: String = "This part of Aida is not built yet."

    override val whatItWillDo: String = "What it will do"

    override val askMira: String = "Ask Aida instead"

    override val askMiraHint: String =
        "Ask her in your own words. She will say plainly if she cannot do it yet."

    override val appLock: AppLockWording = AppLockWording(
        title = "Aida is locked",
        subtitle = "Unlock with your fingerprint or screen lock to continue.",
        unlock = "Unlock",
        promptTitle = "Unlock Aida",
        promptSubtitle = "Confirm it is you to open Aida.",
        lockedOut = "Too many attempts. Try again in a little while, or use your screen lock.",
        cannotVerify = "Aida couldn't verify you. Try again.",
        notEnrolled = "No fingerprint or screen lock is set up on this phone yet.",
        enroll = "Set up screen lock",
    )

    override fun areaLockedTitle(areaName: String) = "$areaName is locked"

    override fun areaLockedSubtitle(areaName: String) =
        "Unlock with your fingerprint or screen lock to open $areaName."

    override val backupAreaName = "Backups"

    override fun areaTitle(area: MiraArea): String = when (area) {
        MiraArea.ALARMS -> "Alarms"
        MiraArea.TIMERS -> "Timers"
        MiraArea.CALENDAR -> "Calendar"
        MiraArea.SALAH -> "Salah"
        MiraArea.NOTES -> "Notes"
        MiraArea.PROJECTS -> "Projects"
        MiraArea.CLIENTS -> "Clients"
        MiraArea.INVOICES -> "Invoices"
        MiraArea.MONEY -> "Money"
        MiraArea.REPORTS -> "Reports"
        MiraArea.FILES -> "Files"
        MiraArea.AUTOMATIONS -> "Automations"
        MiraArea.MEMORY -> "Memory"
        MiraArea.SETTINGS -> "Settings"
    }

    override fun areaSummary(area: MiraArea): String = when (area) {
        MiraArea.ALARMS -> "The times Abu wants waking for, and the days they repeat on."
        MiraArea.TIMERS -> "Countdowns, several at once, read from the clock rather than a counter."
        MiraArea.CALENDAR -> "Abu's agenda, read from the phone's own calendars."
        MiraArea.SALAH -> "The five daily prayers, tracked — with times computed on this phone."
        MiraArea.NOTES -> "Anything worth writing down, searchable the moment it is saved."
        MiraArea.PROJECTS -> "The work in flight, and what each piece is waiting on."
        MiraArea.CLIENTS -> "Who Abu works with, and the history behind each name."
        MiraArea.INVOICES -> "Quotes and invoices, with the arithmetic done exactly."
        MiraArea.MONEY -> "Income, expenses and what they add up to — computed, never estimated."
        MiraArea.REPORTS -> "Income, expenses and the business over any period, with charts and PDF export."
        MiraArea.FILES -> "Documents Aida can find again, including ones scanned with the camera."
        MiraArea.AUTOMATIONS -> "Things Aida does on her own, on a schedule or on a trigger."
        MiraArea.MEMORY -> "What Aida has been told to remember, and nothing she was not."
        MiraArea.SETTINGS -> "Language, voice, reminders, Gemini and privacy."
    }

    override fun areaCapabilities(area: MiraArea): List<String> = when (area) {
        MiraArea.PROJECTS, MiraArea.ALARMS, MiraArea.TIMERS, MiraArea.NOTES, MiraArea.SETTINGS, MiraArea.INVOICES, MiraArea.MONEY, MiraArea.REPORTS, MiraArea.FILES, MiraArea.MEMORY, MiraArea.AUTOMATIONS, MiraArea.CALENDAR, MiraArea.SALAH, MiraArea.CLIENTS -> emptyList()
    }
}
