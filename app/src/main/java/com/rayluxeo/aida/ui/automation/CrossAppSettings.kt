package com.rayluxeo.aida.ui.automation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Opens the system screens where Abu turns the cross-app grants on, mirroring
 * `ExactAlarmSettings` — the specific screen first, the general Settings app as a never-dead-ends
 * fallback. These grants are only ever granted from system settings, never a dialog.
 */
fun Context.openAccessibilitySettings() =
    openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

fun Context.openNotificationListenerSettings() =
    openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
