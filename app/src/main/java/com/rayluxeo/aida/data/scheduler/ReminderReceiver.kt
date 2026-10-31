package com.rayluxeo.aida.data.scheduler

import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.usecase.ReminderFiredUseCase
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.brief.BriefNotificationWording
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The alarm's landing point. Everything happens under [goAsync], which keeps the process alive
 * long enough for the suspend calls to complete.
 *
 * **The invariant this file exists to protect: nothing the database does can stop a reminder
 * firing.** It is no longer true that the notification is posted before Room is touched — the row
 * is read first, because its kind and its task decide which buttons the notification carries, and
 * without that every reminder got the `ONE_TIME` default and Complete was never offered on a task
 * reminder. What holds instead, and what any change here must keep holding, is weaker but is the
 * part that actually matters:
 *
 * - the title travels in the intent extras and is never read back, so the words Abu sees do not
 *   depend on storage at all;
 * - the row read is folded to null on anything but success, and every value taken from it has a
 *   working default, so a database that will not open costs the notification its buttons and
 *   nothing else;
 * - the re-arm runs in a `finally`, so a notification that goes badly cannot stop a repeating
 *   reminder coming back.
 *
 * A reminder that arrives with no title is named by [MiraNotifications], which is the layer that
 * has already read Abu's language — a word chosen here could only be a word in one language.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var reminders: ReminderRepository

    @Inject lateinit var notifications: MiraNotifications

    @Inject lateinit var dispatchers: DispatcherProvider

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var wording: BriefNotificationWording

    @Inject lateinit var reminderFired: ReminderFiredUseCase

    // No super.onReceive() call: Hilt's Gradle plugin rewrites this class to extend its
    // generated base and injects that call into the bytecode itself. Writing it here would
    // resolve against BroadcastReceiver's abstract member and fail to compile.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
        if (reminderId <= 0L) return

        val title = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty()

        val power = context.getSystemService(PowerManager::class.java)
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aida:reminder_receiver")?.apply {
            acquire(30_000L)
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                // DataStore, not Room: the channel name Android shows in its own settings
                // screen has to be in Abu's language, and reading it here cannot be affected by
                // the database being unavailable.
                val language = unpromptedLanguage(settings.current().languageMode)
                // The row is read for its kind and its task, because those decide which buttons
                // the notification carries. Without them every reminder got the ONE_TIME default
                // and Complete was never offered on anything — the one action Abu most wants on a
                // task reminder. A row that cannot be read still notifies, with the plain actions.
                val row = (reminders.byId(reminderId) as? AppResult.Success)?.value
                notifications.showReminder(
                    reminderId = reminderId,
                    title = title,
                    labels = wording.reminderChannel(language),
                    kind = row?.kind ?: ReminderKind.ONE_TIME,
                    linkedTaskId = row?.linkedTaskId,
                    language = language,
                )
            } finally {
                // In the finally, not after the post: re-arming must survive the notification
                // going badly, or a recurring reminder that errors once stops for ever. This also
                // replaces the old markFired call — that closed the occurrence without arming the
                // next one, so every repeating reminder fired exactly once.
                try {
                    reminderFired(reminderId)
                } finally {
                    pending.finish()
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.rayluxeo.aida.action.REMINDER_FIRE"
        const val EXTRA_REMINDER_ID = "com.rayluxeo.aida.extra.REMINDER_ID"
        const val EXTRA_TITLE = "com.rayluxeo.aida.extra.REMINDER_TITLE"
    }
}
