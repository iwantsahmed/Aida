package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.GlobalDeviceAction

/**
 * The outside world an automation action needs but the engine must not touch directly.
 *
 * [AutomationEngine] is pure Kotlin — no Context, no Intent — so the two actions that reach past
 * Mira's own data (posting a system notification, opening a link in whatever app owns it) go through
 * this port, implemented in the Android layer. It is the one seam through which an automation acts on
 * the device, which is also where the single rule lives: nothing here may send a message, move money
 * or delete a record on Abu's behalf — [AutomationValidator] already forbids those verbs, and this
 * surface offers no way to do them either.
 */
interface AutomationDeviceGateway {

    /** Posts a notification Abu can see and tap away. Fails only if the system refuses to post. */
    suspend fun notify(title: String, body: String?): AppResult<Unit>

    /**
     * Opens [url] in whatever app registers its scheme — a browser for http(s), another app for its
     * own `scheme://`. The link is shown, never acted on silently; a URL nothing can open is a value
     * failure, not a crash.
     */
    suspend fun openLink(url: String): AppResult<Unit>

    /**
     * Performs a system-wide gesture (back, home, recents, …) through the opt-in accessibility
     * service. When that service is not enabled this fails as a value, so a rule that needs it shows
     * a failed run in the history rather than doing nothing (§12). The service is a pure actuator:
     * performing a gesture reads nothing from the screen.
     */
    suspend fun performGlobalAction(action: GlobalDeviceAction): AppResult<Unit>

    /**
     * Brings another app forward by package. A package nothing on the device can launch is a value
     * failure, not a crash — the same treatment [openLink] gives a URL no app can open.
     */
    suspend fun launchApp(packageName: String): AppResult<Unit>
}
