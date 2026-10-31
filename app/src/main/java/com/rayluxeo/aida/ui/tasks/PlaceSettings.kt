package com.rayluxeo.aida.ui.tasks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal

/**
 * Whether a refusal is something an Android settings screen can actually fix.
 *
 * Kept beside [openPlaceSettings] because the two answer the same question and would otherwise
 * disagree: a caller offering a settings button for a refusal this file has no intent for would
 * land Abu on the app details page wondering what he was meant to change.
 *
 * `NO_FIX_YET` is the interesting one. It DOES deserve a button — "try again" — but not this
 * button, because nothing in settings is wrong. A device with no provider and a register Mira has
 * filled cannot be answered at all.
 */
internal val PlaceWatchRefusal.opensSettings: Boolean
    get() = when (this) {
        PlaceWatchRefusal.PERMISSION_MISSING,
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING,
        PlaceWatchRefusal.LOCATION_DISABLED,
        PlaceWatchRefusal.BATTERY_RESTRICTED,
        -> true

        PlaceWatchRefusal.NO_PROVIDER,
        PlaceWatchRefusal.LIMIT_REACHED,
        PlaceWatchRefusal.NO_FIX_YET,
        -> false
    }

/**
 * Opens the Android screen that answers one [PlaceWatchRefusal].
 *
 * Settings rather than a runtime permission dialog, deliberately. Location while in use and
 * location all the time are granted on different screens, Android only shows the second as a
 * settings trip anyway, and a dialog Abu has already refused twice never appears again — which
 * would leave Mira offering a button that silently does nothing. A settings screen always opens.
 *
 * The refusals [opensSettings] rules out never reach this function, because no button is offered
 * for them: a device with no location provider and a register Mira has filled are not things a
 * settings screen can change, and a fix that has not arrived yet wants a retry rather than a trip
 * to settings. They are still listed in the `when` below, falling through to the app details
 * page, so that the exhaustive branch keeps compiling and a future caller that reaches this
 * anyway lands somewhere sane instead of crashing.
 */
internal fun Context.openPlaceSettings(refusal: PlaceWatchRefusal) {
    val appDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    val intent = when (refusal) {
        PlaceWatchRefusal.LOCATION_DISABLED -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        PlaceWatchRefusal.BATTERY_RESTRICTED -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        // Both location permissions live on Mira's own app screen.
        PlaceWatchRefusal.PERMISSION_MISSING,
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING,
        PlaceWatchRefusal.NO_PROVIDER,
        PlaceWatchRefusal.LIMIT_REACHED,
        PlaceWatchRefusal.NO_FIX_YET,
        -> appDetails
    }
    try {
        startActivity(intent)
    } catch (missing: ActivityNotFoundException) {
        try {
            startActivity(appDetails)
        } catch (alsoMissing: ActivityNotFoundException) {
            // Nothing further to try; the notice on screen already says what is wrong.
        }
    }
}
