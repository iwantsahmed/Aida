package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal

/**
 * Where an automation's place watch stands, told plainly so the view model never claims a rule is
 * armed when nothing is listening (§5). Reuses [PlaceWatchRefusal] rather than a second reason list
 * (§7.12) — a place automation and a place reminder are refused for the same reasons.
 */
sealed interface AutomationPlaceWatch {
    /** Not a place trigger, or disabled — any stale alert has been removed. */
    data object NotWatched : AutomationPlaceWatch
    data object Watching : AutomationPlaceWatch
    data class Refused(val reason: PlaceWatchRefusal) : AutomationPlaceWatch
}

/**
 * Arms and disarms the geofence behind a place-triggered automation, on the same platform
 * proximity-alert primitive the place reminders use — no Play Services, no new permission, nothing
 * sent anywhere. **Location is never required**: a phone with it switched off runs every other
 * trigger unchanged, and this simply reports a refusal.
 *
 * Unlike a place reminder, a place automation is **recurring**: the alert is armed with no expiry
 * and fires on every arrival, so the receiver does not release it after firing. [sync] is
 * idempotent — it arms an enabled place rule, re-arms an edited one, and removes the watch for a
 * rule that is disabled or no longer a place trigger.
 */
interface AutomationPlaceScheduler {

    val capacity: Int

    suspend fun sync(automation: Automation): AppResult<AutomationPlaceWatch>

    suspend fun cancel(automationId: Long): AppResult<Unit>

    /** Re-arms every enabled place automation after a reboot or an app update dropped the alerts. */
    suspend fun rewatchAll(): AppResult<Unit>
}
