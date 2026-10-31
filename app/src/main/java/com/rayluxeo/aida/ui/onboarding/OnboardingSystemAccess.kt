package com.rayluxeo.aida.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rayluxeo.aida.ui.vault.biometricEnrollIntent

/**
 * What one runtime permission is doing, as far as onboarding can honestly tell.
 *
 * Android gives no way to distinguish "never asked" from "asked twice and blocked" — both report
 * no rationale — so [BLOCKED] is only ever concluded from a refusal Mira actually received in
 * this run, never guessed from a cold check.
 */
enum class PermissionState {
    /** Not granted, and Mira has not asked during this run. Asking is still worth offering. */
    NOT_GRANTED,

    GRANTED,

    /** Refused, but Android will still show the dialog if Abu changes his mind. */
    DENIED,

    /** Refused for good. Only the system settings screen can turn it on now. */
    BLOCKED,
}

/** The platform's verdict on whether the vault can ever be unlocked on this phone. */
enum class BiometricReadiness {
    /** Not asked yet. The screen reports "checking" rather than inventing an answer. */
    UNKNOWN,
    READY,
    NONE_ENROLLED,

    /** No usable sensor and no device credential — covers missing, broken and unsupported. */
    UNAVAILABLE,
}

/**
 * Mirrors the vault's own requirement in `ui/vault/VaultUnlock.kt`: Class 3 biometrics with the
 * device PIN as the fallback. Onboarding must report readiness for exactly the authenticators
 * the vault will later demand, or the step would promise an unlock that does not happen.
 */
private const val VAULT_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * POST_NOTIFICATIONS only became a runtime permission in API 33. Below that the request dialog
 * does not exist and notifications are governed solely by the system settings screen, so Mira
 * must not launch a request that would come back denied without ever being shown.
 */
internal val notificationsRequestable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Reading what is already scheduled and writing an event Abu dictates are two permissions, and
 * Mira does both, so the calendar step asks for both at once rather than coming back for the
 * second one the first time he dictates something.
 */
internal val calendarPermissions: List<String> = listOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

/** Read only. Mira never writes a contact, so the step never asks to. */
internal val contactsPermissions: List<String> = listOf(Manifest.permission.READ_CONTACTS)

internal fun Context.hasMicrophonePermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/** True only when every one of [names] is granted: a half-granted pair is not a capability. */
internal fun Context.hasPermissions(names: List<String>): Boolean = names.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * Asked of the notification manager rather than of the permission, because a notification can
 * also be switched off from the system settings screen on any Android version.
 */
internal fun Context.notificationsEnabled(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled()

/** True only straight after a refusal, where it means Android will still show the dialog again. */
internal fun Activity.canRequestAgain(permission: String): Boolean =
    shouldShowRequestPermissionRationale(permission)

internal fun Context.biometricReadiness(): BiometricReadiness {
    val manager = getSystemService(BiometricManager::class.java)
        ?: return BiometricReadiness.UNAVAILABLE
    return when (manager.canAuthenticate(VAULT_AUTHENTICATORS)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricReadiness.READY
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricReadiness.NONE_ENROLLED
        else -> BiometricReadiness.UNAVAILABLE
    }
}

/** Mira's own page in system settings — the only route left once a permission is blocked. */
internal fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ),
    )
}

internal fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    try {
        startActivity(intent)
    } catch (missing: ActivityNotFoundException) {
        openAppSettings()
    }
}

/** Enrolment, falling back to the general security screen on a device without the direct action. */
internal fun Context.openBiometricEnrollment() {
    try {
        startActivity(biometricEnrollIntent())
    } catch (missing: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }
}

/**
 * @return false when this OEM ships no assistant screen, so the step can say so plainly instead
 * of appearing to have done something.
 */
internal fun Context.openAssistantSettings(): Boolean = try {
    startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
    true
} catch (missing: ActivityNotFoundException) {
    false
}

/**
 * Compose only offers a [Context], and on a themed or configuration-wrapped host that Context is
 * a chain of wrappers rather than the Activity itself, so it has to be unwrapped rather than cast.
 * The Activity is needed for one thing only: whether a refused permission can be asked for again.
 */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
