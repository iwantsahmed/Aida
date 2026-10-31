package com.rayluxeo.aida.ui.mira

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rayluxeo.aida.domain.command.MiraCapability

/**
 * The runtime permissions behind a capability, or none where Android grants it another way.
 *
 * This file is where a [MiraAction] meets Android: Mira states a capability in her own terms and
 * everything platform-shaped about it is answered here, so nothing else on the screen has to know
 * that a calendar is two runtime permissions, an exact alarm is none, and notifications are a
 * permission on one Android version and a settings toggle on another.
 *
 * An empty list is the instruction to ask on a settings screen instead: exact alarms have no
 * dialog at all, and POST_NOTIFICATIONS only became a runtime permission in API 33, so below
 * that a request would come back denied without ever being shown.
 */
internal fun MiraCapability.permissions(): List<String> = when (this) {
    MiraCapability.CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
    MiraCapability.CALENDAR -> listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )
    MiraCapability.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
    MiraCapability.NOTIFICATIONS ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    MiraCapability.EXACT_ALARMS -> emptyList()
}

/** The platform's own answer, asked fresh: a grant can be taken back while Mira is running. */
internal fun Context.hasGranted(capability: MiraCapability): Boolean = when (capability) {
    MiraCapability.CONTACTS, MiraCapability.CALENDAR, MiraCapability.MICROPHONE ->
        capability.permissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    // Asked of the notification manager rather than of the permission, because a notification can
    // also be switched off from the system settings screen on any Android version.
    MiraCapability.NOTIFICATIONS -> NotificationManagerCompat.from(this).areNotificationsEnabled()

    MiraCapability.EXACT_ALARMS ->
        getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
}

/**
 * The system screens that can still grant a capability, best first.
 *
 * Every phone has Mira's own app details page, so it closes each list: an OEM that has dropped
 * the specific screen costs Abu one extra tap rather than leaving the control with nowhere to go.
 */
internal fun MiraCapability.settingsIntents(context: Context): List<Intent> = when (this) {
    MiraCapability.NOTIFICATIONS -> listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetails(context),
    )

    MiraCapability.EXACT_ALARMS -> listOf(
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", context.packageName, null),
        ),
        appDetails(context),
    )

    MiraCapability.CONTACTS, MiraCapability.CALENDAR, MiraCapability.MICROPHONE ->
        listOf(appDetails(context))
}

/**
 * ACTION_DIAL, never ACTION_CALL. The dialler opens with the number in it and Abu presses the
 * green button himself, which is why Mira needs no CALL_PHONE permission and cannot place a call
 * to someone she misheard.
 */
internal fun dialIntent(number: String): Intent =
    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))

/** The messaging app opens on the draft Mira wrote. The send is his. */
internal fun messageIntent(number: String, body: String?): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).apply {
        if (!body.isNullOrBlank()) putExtra(SMS_BODY_EXTRA, body)
    }

/** Opens WhatsApp composed with recipient phone and optional message body. */
internal fun whatsAppIntent(number: String, body: String?): Intent {
    val clean = number.filter { it.isDigit() }.let {
        if (it.startsWith("0") && it.length == 11) "880" + it.drop(1) else it
    }
    val url = buildString {
        append("https://api.whatsapp.com/send?phone=").append(clean)
        if (!body.isNullOrBlank()) {
            append("&text=").append(Uri.encode(body))
        }
    }
    return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setPackage("com.whatsapp")
    }
}

/** @return false when nothing installed can take the intent, so Mira can say so plainly. */
internal fun Context.tryStartActivity(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (missing: ActivityNotFoundException) {
    false
}

private fun appDetails(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

/** The documented extra for an `smsto:` intent. The SDK carries no constant for it. */
private const val SMS_BODY_EXTRA = "sms_body"
