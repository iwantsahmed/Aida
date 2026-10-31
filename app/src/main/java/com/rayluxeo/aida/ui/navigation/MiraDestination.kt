package com.rayluxeo.aida.ui.navigation

import androidx.annotation.DrawableRes
import com.rayluxeo.aida.R


/** Every route Mira can show. Nothing navigates to a string literal. */
object MiraRoutes {

    /** The nav argument the task editor reads. Kept here so the route and the screen agree. */
    const val TASK_ID_ARG = "taskId"

    /** Zero is the task that has not been created yet. */
    const val NEW_TASK_ID = 0L

    /** The nav argument the note editor reads. Kept here so the route and the screen agree. */
    const val NOTE_ID_ARG = "noteId"

    /** Zero is the note that has not been created yet, exactly as [NEW_TASK_ID] is for tasks. */
    const val NEW_NOTE_ID = 0L

    /** The nav argument the handwriting canvas reads. */
    const val INK_PAGE_ID_ARG = "pageId"

    /**
     * Zero is a page that does not exist yet.
     *
     * A sentinel rather than an optional argument because the canvas always belongs to a note and
     * only ever asks one question — reopen this page, or start a blank one. An optional argument
     * would have meant a second route shape for the sake of one value.
     */
    const val NEW_INK_PAGE_ID = 0L

    const val ONBOARDING = "onboarding"

    const val HOME = "home"
    const val MIRA = "mira"
    const val TASKS = "tasks"
    const val VAULT = "vault"

    /** The full editor for one task. Built with [taskDetail]; nothing formats the id by hand. */
    const val TASK_DETAIL = "tasks/detail/{$TASK_ID_ARG}"

    /**
     * The task the editor opens on.
     *
     * [NEW_TASK_ID] is the task that does not exist yet: the same screen, saving through
     * `CreateTaskUseCase` instead of `EditTaskUseCase`. A separate route would have meant a second
     * copy of a form with eighteen fields on it.
     */
    fun taskDetail(taskId: Long = NEW_TASK_ID): String = "tasks/detail/$taskId"

    /** The full editor for one note. Built with [noteDetail]; nothing formats the id by hand. */
    const val NOTE_DETAIL = "notes/detail/{$NOTE_ID_ARG}"

    fun noteDetail(noteId: Long = NEW_NOTE_ID): String = "notes/detail/$noteId"

    /** The full-screen handwriting canvas for one page of one note. Built with [inkPage]. */
    const val INK_PAGE = "notes/ink/{$NOTE_ID_ARG}/{$INK_PAGE_ID_ARG}"

    fun inkPage(noteId: Long, pageId: Long = NEW_INK_PAGE_ID): String = "notes/ink/$noteId/$pageId"

    /** The hub that lists every area outside the bottom bar. */

    const val ALARMS = "alarms"
    const val TIMERS = "timers"
    const val CALENDAR = "calendar"
    const val VOICE_NOTES = "voice_notes"
    const val BACKUP = "backup"
    const val PRIVACY_DASHBOARD = "privacy"
    const val NOTES = "notes"
    const val PROJECTS = "projects"
    const val CLIENTS = "clients"
    const val INVOICES = "invoices"
    const val MONEY = "money"
    const val REPORTS = "reports"
    const val FILES = "files"
    const val SCANNER = "files/scanner"
    const val AUTOMATIONS = "automations"
    const val MEMORY = "memory"

    const val SETTINGS = "settings"
    const val VOICE_SETUP = "settings/voice"
    const val ASSISTANT_SETUP = "settings/assistant"

    /**
     * Abu's named places. A sibling of the two above rather than an area of its own: it is a
     * handful of rows he maintains so that "when I get to the office" resolves to somewhere, not
     * a place he goes to get work done.
     */
    const val PLACES = "settings/places"

    /** The remaining settings sub-screens, all siblings of the ones above. */
    const val PERMISSIONS = "settings/permissions"
    const val ACTIVITY_LOG = "settings/activity"
    const val LICENSES = "settings/licenses"

    /** Salah — the prayer-times area, its monthly tracker and its own settings. */
    const val SALAH = "salah"
    const val SALAH_MONTH = "salah/month"
    const val SALAH_SETTINGS = "salah/settings"

    /** Quran — recitation, daily 2-page target tracking, Arabic with Bengali transliteration and translation. */
    const val QURAN = "quran"
}

/**
 * The four destinations that earn a place in the bottom bar.
 *
 * It is also the shell's definition of a tab, and therefore of a push: a forward navigation to
 * anything that is not one of these routes arrives over the tabs rather than beside them, which
 * is what [MiraApp] reads to decide how a screen moves in. Nothing else maintains that list.
 *
 * The label is not here. It changes with Abu's language, and a route does not — see [ShellWording].
 */
enum class MiraTab(
    val route: String,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val activeIconRes: Int,
) {
    HOME(MiraRoutes.HOME, R.drawable.ic_home, R.drawable.ic_home_bold),
    MIRA(MiraRoutes.MIRA, R.drawable.ic_graphic_eq, R.drawable.ic_graphic_eq_bold),
    TASKS(MiraRoutes.TASKS, R.drawable.ic_check_circle, R.drawable.ic_check_circle_bold),
    VAULT(MiraRoutes.VAULT, R.drawable.ic_lock, R.drawable.ic_lock_bold),
}

/**
 * The areas reachable from the hub, and the honest state of each one.
 *
 * Only what does not change with language lives here: the route, the glyph, and whether the area
 * is built. Its title, its one-line summary and the list of what it will do are Abu's language's
 * business and belong to [ShellWording] — an enum that carried English sentences made the hub the
 * one screen in Mira that could not speak Bengali.
 *
 * [isBuilt] is the single source of truth for both the hub listing and what a route renders.
 * An area with `isBuilt = false` still has a real route and a real screen — one that says
 * plainly what the area will do and offers to hand the request to Mira instead. That is a
 * deliberate, governed exception to the "no empty screens" rule in AGENTS.md §12.1: the screen
 * tells the truth and does something, rather than pretending to be a feature.
 *
 * When an area ships, add its real `composable` to the graph FIRST and flip [isBuilt] in the
 * same change. The two are not independent: planned areas are registered by filtering on
 * `isBuilt`, so flipping it alone removes the route and the hub's own row then navigates
 * to a destination that no longer exists. Clear its capabilities in both phrasebooks in the same
 * change, too: copy describing a feature that has shipped is copy nobody will reread.
 */
enum class MiraArea(
    val route: String,
    @param:DrawableRes val iconRes: Int,
    val isBuilt: Boolean,
) {
    ALARMS(MiraRoutes.ALARMS, R.drawable.ic_alarm, isBuilt = true),
    TIMERS(MiraRoutes.TIMERS, R.drawable.ic_hourglass_empty, isBuilt = true),
    CALENDAR(MiraRoutes.CALENDAR, R.drawable.ic_calendar_month, isBuilt = true),
    SALAH(MiraRoutes.SALAH, R.drawable.ic_salah, isBuilt = true),
    NOTES(MiraRoutes.NOTES, R.drawable.ic_description, isBuilt = true),
    PROJECTS(MiraRoutes.PROJECTS, R.drawable.ic_work_outline, isBuilt = true),
    CLIENTS(MiraRoutes.CLIENTS, R.drawable.ic_groups, isBuilt = true),
    INVOICES(MiraRoutes.INVOICES, R.drawable.ic_receipt_long, isBuilt = true),
    MONEY(MiraRoutes.MONEY, R.drawable.ic_account_balance_wallet, isBuilt = true),
    REPORTS(MiraRoutes.REPORTS, R.drawable.ic_chart, isBuilt = true),
    FILES(MiraRoutes.FILES, R.drawable.ic_insert_drive_file, isBuilt = true),
    AUTOMATIONS(MiraRoutes.AUTOMATIONS, R.drawable.ic_auto_awesome_motion, isBuilt = true),
    MEMORY(MiraRoutes.MEMORY, R.drawable.ic_psychology, isBuilt = true),
    SETTINGS(MiraRoutes.SETTINGS, R.drawable.ic_settings, isBuilt = true);

    companion object {
        fun fromRoute(route: String?): MiraArea? = entries.firstOrNull { it.route == route }

        /** Everything the hub lists, in the order it lists them. */
        val hubOrder: List<MiraArea> = entries.toList()
    }
}

