package com.rayluxeo.aida.ui.navigation

import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * Every word the shell puts in front of Abu, in one language: the four tab labels, the areas hub
 * and the screen an unbuilt area renders.
 *
 * One phrasebook for the three of them because they are one frame with one voice — the word for
 * an area on its hub row, on its own screen's title bar and in the tab strip must be the same
 * word, and three files would start identical and drift.
 *
 * It also holds the areas' own copy, which used to sit on the [MiraArea] enum as English string
 * literals. That made the enum both a routing table and a phrasebook, and it made the hub the one
 * screen in Mira that could not speak Bengali. The enum now carries only what does not change
 * with language — route, icon, whether it is built — and every word comes from here.
 */
internal interface ShellWording {

    /** The four labels in the bottom bar. Also read aloud by TalkBack as each tab's name. */
    fun tabLabel(tab: MiraTab): String

    /** What the back arrow announces. It is never drawn, only spoken. */
    val back: String

    // --- An area that is not built yet ---------------------------------------------------------

    val notBuiltYet: String

    val whatItWillDo: String

    val askMira: String

    val askMiraHint: String

    // --- The app-lock screen -------------------------------------------------------------------

    /** The lock screen's own copy, gathered so the gate in `ui/vault` stays language-free. */
    val appLock: AppLockWording

    /** The cover title over one sensitive area Abu asked to gate, e.g. "Money is locked". */
    fun areaLockedTitle(areaName: String): String

    /** The cover's line under that title, telling him how to open the area. */
    fun areaLockedSubtitle(areaName: String): String

    /** What the Backups gate calls itself; Backups is not a [MiraArea], so it needs its own name. */
    val backupAreaName: String

    // --- The areas themselves ------------------------------------------------------------------

    fun areaTitle(area: MiraArea): String

    /** One line, present tense, saying what the area is for. */
    fun areaSummary(area: MiraArea): String

    /**
     * The concrete things an area will be able to do, for the screen it renders before it can.
     *
     * Empty for an area that is already built: its own screen is the answer, and copy describing
     * a feature that shipped is copy nobody will ever reread.
     */
    fun areaCapabilities(area: MiraArea): List<String>
}

/**
 * The lock screen's words, in one language. A small bundle on [ShellWording] rather than loose
 * members so the shell can hand the gate a single already-worded value.
 */
internal data class AppLockWording(
    val title: String,
    val subtitle: String,
    val unlock: String,
    val promptTitle: String,
    val promptSubtitle: String,
    val lockedOut: String,
    val cannotVerify: String,
    val notEnrolled: String,
    val enroll: String,
)

internal fun shellWording(language: SpokenLanguage): ShellWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliShellWording
    SpokenLanguage.ENGLISH -> EnglishShellWording
}
