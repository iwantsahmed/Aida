package com.rayluxeo.aida.domain.automation

/**
 * Whether the two opt-in system grants behind the cross-app tier are currently enabled.
 *
 * A port returning plain booleans, so `domain` stays free of Android: the implementation reads
 * `Settings.Secure` and the enabled notification listeners. Read fresh every time — a grant Abu
 * toggles in system settings must be reflected without a cached value going stale (the same
 * discipline `MiraSystemAccess.hasGranted` keeps).
 *
 * The UI uses these to tell Abu, honestly, that a rule it just saved will not fire until he turns the
 * grant on — never claiming a rule is armed when it is not (§5). Performing a global action itself
 * goes through [AutomationDeviceGateway], which fails as a value when the service is off.
 */
interface CrossAppAccess {

    /** True when Mira's accessibility service is switched on in system settings. */
    fun isAccessibilityServiceEnabled(): Boolean

    /** True when Mira has been granted notification access (the notification-listener grant). */
    fun isNotificationAccessEnabled(): Boolean
}
