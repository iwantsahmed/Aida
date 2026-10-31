package com.rayluxeo.aida.data.automation

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.rayluxeo.aida.domain.automation.CrossAppAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the two system grants behind the cross-app tier, fresh on every call (never cached), so the
 * UI reflects a grant Abu toggles in system settings the next time it looks — matching
 * [com.rayluxeo.aida.ui.mira.MiraSystemAccess].
 */
@Singleton
class AndroidCrossAppAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CrossAppAccess {

    override fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = ComponentName(context, MiraAccessibilityService::class.java)
        val flat = component.flattenToString()
        val flatShort = component.flattenToShortString()
        // The setting is a ':'-separated list of enabled service components.
        return enabled.split(':').any { it.equals(flat, ignoreCase = true) || it.equals(flatShort, ignoreCase = true) }
    }

    override fun isNotificationAccessEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
