package com.rayluxeo.aida.data.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Mira's accessibility service — a pure **actuator**, never a screen reader.
 *
 * Its only job is to be bound so that [AccessibilityServiceConnection] can perform a global gesture
 * (back, home, recents) on Abu's behalf when an automation asks. It is declared
 * `canRetrieveWindowContent="false"` (see `res/xml/accessibility_service_config.xml`), so it has no
 * way to read what is on the screen, and [onAccessibilityEvent] deliberately does nothing — Mira
 * presses the button and sees nothing, which is the whole reason this is safe to offer (docs/PRIVACY.md
 * §6.5). It is OFF by default and only runs once Abu enables it in system settings.
 *
 * Field injection is correct here: an Android entry point cannot be constructor-injected (§13).
 */
@AndroidEntryPoint
class MiraAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var connection: AccessibilityServiceConnection

    override fun onServiceConnected() {
        super.onServiceConnected()
        connection.attach(this)
    }

    /** Intentionally empty: this service acts, it does not observe. No screen content is read. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connection.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connection.detach(this)
        super.onDestroy()
    }
}
