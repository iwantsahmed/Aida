package com.rayluxeo.aida.data.automation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.automation.AutomationScheduler
import com.rayluxeo.aida.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Fires NOTIFICATION_POSTED automations when another app posts a notification.
 *
 * Two gates, both off by default, must be open for a rule to fire: the OS notification-access grant
 * (without it this service never runs) and Mira's own `crossAppAutomationsEnabled` toggle (so Abu can
 * pause the whole tier without revoking the grant). Mira's own notifications are ignored, so an
 * automation that posts a notification cannot set itself off in a loop.
 *
 * The notification's title is read only to decide which rules match (through the pure
 * [AutomationScheduler.checkAndTriggerNotification]) — it is never stored, and nothing about the
 * notification leaves the device (docs/PRIVACY.md §6.5). Field injection is correct for an Android
 * entry point (§13); kept in the main process (no `android:process`), per the manifest's house rule.
 */
@AndroidEntryPoint
class MiraNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var scheduler: AutomationScheduler

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        // Never react to Mira's own notifications — that is how a notify-action rule would loop.
        if (notification.packageName == packageName) return
        val title = notification.notification?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
        val fromPackage = notification.packageName
        scope.launch {
            // The app-level opt-in, checked fresh each time: the toggle being off pauses the tier even
            // while the system grant is still on.
            if (!settings.current().crossAppAutomationsEnabled) return@launch
            scheduler.checkAndTriggerNotification(fromPackage, title)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
