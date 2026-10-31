package com.rayluxeo.aida.data.automation

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.rayluxeo.aida.R
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.automation.AutomationDeviceGateway
import com.rayluxeo.aida.domain.model.GlobalDeviceAction
import com.rayluxeo.aida.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android side of [AutomationDeviceGateway]: the one place an automation touches the device.
 *
 * Both effects fail as values, never as crashes — a revoked POST_NOTIFICATIONS, or a link no app can
 * open, is a failed run Abu can see in the history, not an exception that takes the receiver down.
 * The automations notification channel is its own, at default importance (Mira reporting, not Mira
 * interrupting), so Abu can silence a chatty rule without losing the reminders he asked for.
 */
@Singleton
class AndroidAutomationDeviceGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val accessibility: AccessibilityServiceConnection,
) : AutomationDeviceGateway {

    private val manager: NotificationManager? by lazy { context.getSystemService() }
    private val nextNotificationId = AtomicInteger(NOTIFICATION_BASE_ID)

    override suspend fun notify(title: String, body: String?): AppResult<Unit> {
        val service = manager ?: return failure(MiraErrorCode.STORAGE, "No notification service")
        ensureChannel(service, bengali = settings.current().languageMode == LanguageMode.BANGLA)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mira_notification)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        body?.takeIf { it.isNotBlank() }?.let { builder.setContentText(it) }
        return try {
            service.notify(nextNotificationId.incrementAndGet(), builder.build())
            Unit.asSuccess()
        } catch (_: SecurityException) {
            failure(MiraErrorCode.PERMISSION_REQUIRED, "Notifications are turned off")
        } catch (e: Exception) {
            failure(MiraErrorCode.STORAGE, "Could not post notification: ${e.message}")
        }
    }

    override suspend fun openLink(url: String): AppResult<Unit> {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: return failure(MiraErrorCode.VALIDATION, "Not a valid link: $url")
        // NEW_TASK is required to launch an activity from a non-activity context (a receiver, the
        // engine running in the background).
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Unit.asSuccess()
        } catch (_: ActivityNotFoundException) {
            failure(MiraErrorCode.NOT_FOUND, "No app can open $url")
        } catch (e: Exception) {
            failure(MiraErrorCode.UNKNOWN, "Could not open $url: ${e.message}")
        }
    }

    override suspend fun performGlobalAction(action: GlobalDeviceAction): AppResult<Unit> {
        // The master opt-in gates the gesture too, so the toggle being off truly stops it rather than
        // only the create-sheet note saying so — the notification listener checks the same flag.
        if (!settings.current().crossAppAutomationsEnabled) {
            return failure(MiraErrorCode.PERMISSION_REQUIRED, "Cross-app automations are off")
        }
        val osAction = when (action) {
            GlobalDeviceAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalDeviceAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalDeviceAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalDeviceAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            GlobalDeviceAction.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        }
        // A false here means no service is connected — the accessibility grant is off. That is a
        // failed run Abu can see, not a silent nothing (§12).
        return if (accessibility.performGlobalAction(osAction)) {
            Unit.asSuccess()
        } else {
            failure(MiraErrorCode.PERMISSION_REQUIRED, "The accessibility service is not enabled")
        }
    }

    override suspend fun launchApp(packageName: String): AppResult<Unit> {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return failure(MiraErrorCode.NOT_FOUND, "No launchable app for $packageName")
        return try {
            context.startActivity(intent)
            Unit.asSuccess()
        } catch (_: ActivityNotFoundException) {
            failure(MiraErrorCode.NOT_FOUND, "Could not launch $packageName")
        } catch (e: Exception) {
            failure(MiraErrorCode.UNKNOWN, "Could not launch $packageName: ${e.message}")
        }
    }

    private fun ensureChannel(service: NotificationManager, bengali: Boolean) {
        // createNotificationChannel is idempotent, so creating it on every post is the cheap, safe
        // way to be sure it exists. The name is what Android shows in system settings, so it follows
        // Abu's chosen language like any other line he reads.
        val channel = NotificationChannel(
            CHANNEL_ID,
            if (bengali) "অটোমেশন" else "Automations",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        service.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "mira_automations"
        const val NOTIFICATION_BASE_ID = 70_000
    }
}
