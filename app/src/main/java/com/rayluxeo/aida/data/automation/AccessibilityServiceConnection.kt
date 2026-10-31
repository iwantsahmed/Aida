package com.rayluxeo.aida.data.automation

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live link to the running accessibility service, held for the one process.
 *
 * A global action can only be performed by the bound `AccessibilityService` instance, but
 * [com.rayluxeo.aida.data.automation.AndroidAutomationDeviceGateway] has no way to reach that
 * instance directly. The service registers itself here on connect and clears itself on unbind, so the
 * gateway can ask this singleton to perform an action — and get an honest "nothing is connected" when
 * the service is off (the default), which it turns into a visible failed run (§12).
 *
 * The reference is `@Volatile`: it is written on the service's main thread and read from whatever
 * coroutine dispatcher an automation runs on.
 */
@Singleton
class AccessibilityServiceConnection @Inject constructor() {

    @Volatile
    private var service: MiraAccessibilityService? = null

    fun attach(service: MiraAccessibilityService) {
        this.service = service
    }

    /** Clears only if [service] is still the one attached, so a late unbind cannot drop a newer bind. */
    fun detach(service: MiraAccessibilityService) {
        if (this.service === service) this.service = null
    }

    /**
     * Performs the OS global action on the connected service, or returns false when none is connected
     * — the signal the gateway turns into a failed run rather than a silent no-op.
     */
    fun performGlobalAction(osGlobalAction: Int): Boolean =
        service?.performGlobalAction(osGlobalAction) ?: false
}
