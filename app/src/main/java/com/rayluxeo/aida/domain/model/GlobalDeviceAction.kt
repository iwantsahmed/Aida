package com.rayluxeo.aida.domain.model

/**
 * A system-wide gesture an automation can perform through the opt-in accessibility service.
 *
 * Deliberately a small, fixed set — exactly the global actions an `AccessibilityService` may perform
 * **without reading the screen**, which is the whole point of how Mira uses that service: as a pure
 * actuator that can press these buttons but can never see what is on the display (the service is
 * declared `canRetrieveWindowContent="false"`). Nothing here opens an app, types, taps a coordinate,
 * or reads content.
 */
enum class GlobalDeviceAction {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
}
