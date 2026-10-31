package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.automation.AutomationAlarmScheduler
import com.rayluxeo.aida.domain.automation.AutomationPlaceScheduler
import com.rayluxeo.aida.domain.automation.AutomationPlaceWatch
import com.rayluxeo.aida.domain.model.Automation
import javax.inject.Inject

/**
 * Arms (or tears down) everything an automation needs to fire on its own, in one place.
 *
 * A rule can be time-based, place-based, or neither, and a single edit can turn one into another —
 * so the alarm and the geofence have to move together or they drift: a rule edited from "at 8am" to
 * "at the office" would otherwise keep ringing at 8 *and* watch the office. This keeps them in
 * lockstep, and takes the scheduling out of the view model, where arming a scheduler directly was a
 * §4 wrinkle (a view model deciding and performing a side effect rather than a use case).
 *
 * [sync] returns the [AutomationPlaceWatch] so the caller can tell Abu when a place could not be
 * watched (a withheld background-location grant) rather than claiming the rule is armed (§5).
 */
class SyncAutomationSchedulesUseCase @Inject constructor(
    private val alarm: AutomationAlarmScheduler,
    private val place: AutomationPlaceScheduler,
) {

    suspend fun sync(automation: Automation): AppResult<AutomationPlaceWatch> {
        alarm.sync(automation)
        return place.sync(automation)
    }

    suspend fun cancel(automationId: Long): AppResult<Unit> {
        alarm.cancel(automationId)
        return place.cancel(automationId)
    }
}
