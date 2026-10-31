package com.rayluxeo.aida.assistant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.rayluxeo.aida.MainActivity
import com.rayluxeo.aida.R

/**
 * Every way into Mira that starts from outside her own window, and the intents behind them.
 *
 * The launcher shortcuts, the Quick Settings tile and the home-screen widget are three controls
 * that look like they should do the same thing, so they are built from one set of actions here
 * rather than each assembling its own intent. When they drifted apart the two that were wrong were
 * indistinguishable from the one that was right.
 *
 * **The shortcuts are dynamic rather than declared in `res/xml`.** A static shortcut has to name
 * its target package as a literal string, and Mira's debug build installs as
 * `com.rayluxeo.aida.debug` — the literal would be wrong on exactly the build Abu runs day to day,
 * and a shortcut pointing at a package that is not installed is silently dropped by the launcher.
 * Built here, the component is resolved from the running context and is correct on both builds.
 *
 * An action is only half of an entry point. The other half is the shell arm that reads it back off
 * the intent and puts Abu where the label promised; [Entry] is the whole published vocabulary, and
 * every action in it has to be answered there.
 */
object MiraShortcuts {

    /** Open Mira and start listening. The tile, the widget and the first shortcut all use it. */
    const val ACTION_TALK = "com.rayluxeo.aida.action.TALK"

    /** Open Tasks with the composer already up, ready for one line. */
    const val ACTION_NEW_TASK = "com.rayluxeo.aida.action.NEW_TASK"

    /** Open the note editor on a note that does not exist yet, so the first thing Abu does is type. */
    const val ACTION_NEW_NOTE = "com.rayluxeo.aida.action.NEW_NOTE"

    /** Open Invoices screen to create or view invoices. */
    const val ACTION_NEW_INVOICE = "com.rayluxeo.aida.action.NEW_INVOICE"

    /** Open Document/Receipt Scanner flow. */
    const val ACTION_SCAN_RECEIPT = "com.rayluxeo.aida.action.SCAN_RECEIPT"

    /**
     * Open the handwriting canvas on a blank page.
     *
     * Separate from [ACTION_NEW_NOTE] rather than a flag on it, because the two are different
     * gestures: this one is what Abu wants the moment the S Pen leaves the phone, and it must not
     * cost him a tap on a keyboard note first. A page belongs to a note, so whatever answers this
     * action has to create the note on the way in — the canvas will not do it.
     */
    const val ACTION_QUICK_INK = "com.rayluxeo.aida.action.QUICK_INK"

    /** Open Home, which is the day: what is due, what is overdue and the next reminder. */
    const val ACTION_TODAY = "com.rayluxeo.aida.action.TODAY"

    /** A reminder linked to a client opens that client. Notifications only — not a launcher shortcut. */
    const val ACTION_CLIENT = "com.rayluxeo.aida.action.CLIENT"

    /**
     * The record id a deep-link opens, read by MainActivity and handed to the shell. Which record
     * it names is settled by the action beside it — a task, an invoice or a client — so one key
     * carries all three, and a reminder opens the thing it is about rather than a blank form.
     */
    const val EXTRA_TARGET_ID = "com.rayluxeo.aida.extra.TARGET_ID"

    /** The intent every "talk to Mira" control sends, so all of them behave identically. */
    fun talkIntent(context: Context): Intent = entryIntent(context, ACTION_TALK)

    /**
     * The same intent, wrapped for callers that cannot start an activity themselves — the tile
     * above all, because `startActivityAndCollapse` on Android 14+ takes a `PendingIntent` rather
     * than an `Intent`.
     */
    fun talkPendingIntent(context: Context): PendingIntent = entryPendingIntent(context, Entry.TALK)

    /** The `PendingIntent` behind one button of the home-screen widget. */
    internal fun widgetPendingIntent(context: Context, control: WidgetControl): PendingIntent =
        entryPendingIntent(context, control.entry)

    /**
     * Publishes the launcher shortcut set, replacing whatever was there.
     *
     * Called from a foreground entry point so it is never rate-limited, and called again on every
     * launch so the labels follow the device's language without anything having to watch for a
     * locale change. A refusal from the platform is swallowed: a missing shortcut is a small loss
     * and is not worth failing a launch over, and the set Abu already had is left exactly as it
     * was when the publish is refused.
     *
     * The cap is read here rather than in a helper of its own so that the one `try` covers both
     * calls. `getMaxShortcutCountPerActivity` has no documented failure of its own, and inventing a
     * catch for it left a live failure — a publish attempted before the user has unlocked — sharing
     * a block with a dead one.
     */
    fun refresh(context: Context) {
        try {
            val capacity = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                published(capacity).map { entry -> shortcut(context, entry) },
            )
        } catch (refused: IllegalArgumentException) {
            // Over the platform's cap, or rate-limited. Mira's other entry points are unaffected.
        } catch (locked: IllegalStateException) {
            // Called while the user is still locked, which is the one state the platform refuses
            // a publish outright in. There is no launcher menu to populate yet either.
        }
    }

    /**
     * The entries that fit a platform cap of [capacity], in rank order.
     *
     * [capacity] is what `getMaxShortcutCountPerActivity` reports: how many manifest and dynamic
     * shortcuts the **system** allows one activity to publish. That is fifteen on every release
     * Mira runs on, so at five entries this trims nothing today — it is a guard against a release
     * that lowers the number, because a list over the cap makes the whole publish throw. How many
     * of them a launcher then draws in its long-press menu is a separate and usually smaller
     * number, decided by the launcher; `setRank` is what decides which ones those are.
     *
     * Pure, so the decision about what yields first is checkable without a device. A cap below the
     * size of [Entry] is not an error — the excess is dropped rather than the publish abandoned.
     */
    internal fun published(capacity: Int): List<Entry> = Entry.entries.take(capacity.coerceAtLeast(0))

    private fun shortcut(context: Context, entry: Entry): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, entry.id)
            .setShortLabel(context.getString(entry.shortLabel))
            .setLongLabel(context.getString(entry.longLabel))
            .setIcon(IconCompat.createWithResource(context, entry.icon))
            .setIntent(entryIntent(context, entry.action))
            .setRank(entry.ordinal)
            .build()

    /**
     * `MainActivity` is `singleTask`, so a second tap on any of these reaches the instance that is
     * already open rather than stacking a new one. `FLAG_ACTIVITY_NEW_TASK` is required because
     * the tile, the widget and the launcher all start it from outside an activity.
     */
    private fun entryIntent(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * A `PendingIntent` onto one entry point, immutable because nothing that is handed one has any
     * business rewriting where Mira opens.
     *
     * Two PendingIntents are the same when the request code matches and `Intent.filterEquals`
     * agrees, and `filterEquals` does compare the action, so the entry points are already distinct
     * as they stand. The request code is what keeps them distinct on the day two of them differ by
     * an extra rather than by an action — which is exactly the case `FLAG_UPDATE_CURRENT` collapses
     * into one. Derived from the entry's rank rather than written out beside each call, so a copied
     * line cannot hand two entry points the same number; offset by one because zero is also what a
     * caller that forgot to pass a request code ends up with.
     */
    internal fun entryPendingIntent(context: Context, entry: Entry): PendingIntent =
        PendingIntent.getActivity(
            context,
            entry.ordinal + 1,
            entryIntent(context, entry.action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * The launcher shortcut set, in the order Mira would give them up.
     *
     * Declaration order is the rank handed to `setRank`, which is what a launcher with fewer rows
     * than Mira offers uses to choose the ones it draws. The order is a judgement rather than
     * history: the four above [TODAY] each start something that would otherwise be lost — a spoken
     * request, a task, a note, a page of handwriting — and losing one of those costs Abu the
     * thought he was holding. `Today` is the only entry that opens a view he already gets by
     * launching Mira normally, so it ranks last, and it is also the first [published] would give
     * up if a release ever lowered the platform cap below five.
     */
    internal enum class Entry(
        val id: String,
        val action: String,
        @StringRes val shortLabel: Int,
        @StringRes val longLabel: Int,
        @DrawableRes val icon: Int,
    ) {
        TALK(
            id = "mira.talk",
            action = ACTION_TALK,
            shortLabel = R.string.mira_shortcut_talk_short,
            longLabel = R.string.mira_shortcut_talk_long,
            icon = R.drawable.ic_shortcut_talk,
        ),
        NEW_TASK(
            id = "mira.new_task",
            action = ACTION_NEW_TASK,
            shortLabel = R.string.mira_shortcut_task_short,
            longLabel = R.string.mira_shortcut_task_long,
            icon = R.drawable.ic_shortcut_task,
        ),
        NEW_NOTE(
            id = "mira.new_note",
            action = ACTION_NEW_NOTE,
            shortLabel = R.string.mira_shortcut_note_short,
            longLabel = R.string.mira_shortcut_note_long,
            icon = R.drawable.ic_shortcut_note,
        ),
        NEW_INVOICE(
            id = "mira.new_invoice",
            action = ACTION_NEW_INVOICE,
            shortLabel = R.string.mira_shortcut_invoice_short,
            longLabel = R.string.mira_shortcut_invoice_long,
            icon = R.drawable.ic_mira_tile,
        ),
        QUICK_INK(
            id = "mira.quick_ink",
            action = ACTION_QUICK_INK,
            shortLabel = R.string.mira_shortcut_ink_short,
            longLabel = R.string.mira_shortcut_ink_long,
            icon = R.drawable.ic_shortcut_ink,
        ),
        SCAN_RECEIPT(
            id = "mira.scan_receipt",
            action = ACTION_SCAN_RECEIPT,
            shortLabel = R.string.mira_shortcut_scan_short,
            longLabel = R.string.mira_shortcut_scan_long,
            icon = R.drawable.ic_mira_tile,
        ),
        TODAY(
            id = "mira.today",
            action = ACTION_TODAY,
            shortLabel = R.string.mira_shortcut_today_short,
            longLabel = R.string.mira_shortcut_today_long,
            icon = R.drawable.ic_shortcut_today,
        ),
    }

    internal enum class WidgetControl(@IdRes val viewId: Int, val entry: Entry) {
        TALK(viewId = R.id.mira_widget_talk, entry = Entry.TALK),
        TASK(viewId = R.id.mira_widget_task, entry = Entry.NEW_TASK),
        NOTE(viewId = R.id.mira_widget_note, entry = Entry.NEW_NOTE),
        INK(viewId = R.id.mira_widget_ink, entry = Entry.QUICK_INK),
    }
}
