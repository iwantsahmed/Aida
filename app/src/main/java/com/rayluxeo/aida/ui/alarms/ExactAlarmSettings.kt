package com.rayluxeo.aida.ui.alarms

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the screen where Abu can hand exact alarms back.
 *
 * Wrapped because the OEM that ships without this activity is not a crash: from Android 13 the
 * per-app screen is standard, but a device without it should leave Mira saying her alarms may be
 * late rather than taking the app down. Falling back to Mira's own app settings still puts him
 * one screen away from the toggle.
 */
internal fun Context.openExactAlarmSettings() {
    val target = Uri.fromParts("package", packageName, null)
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, target)
    try {
        startActivity(intent)
    } catch (missing: ActivityNotFoundException) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, target))
        } catch (alsoMissing: ActivityNotFoundException) {
            // Nothing further to try: the notice on screen already says the alarms may be late.
        }
    }
}
