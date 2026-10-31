package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Automation

/**
 * Arms the alarms behind time-based automations — the seam that gives [AutomationNextRun] a way onto
 * the clock, implemented over AlarmManager in the data layer.
 *
 * A time-based automation that fires on its own is only as reliable as what re-arms it, so this port
 * is deliberately idempotent: [sync] can be called after every save, toggle and fire and always ends
 * with exactly one alarm (or none) matching the automation's current state.
 */
interface AutomationAlarmScheduler {

    /**
     * Brings the OS alarm into line with [automation]: a future time-based trigger on an enabled rule
     * is armed (replacing any earlier arming), and anything else — disabled, a non-time trigger, a
     * date already past — is cancelled. Safe to call on create, update, enable/disable, and after a
     * fire to arm the next occurrence.
     */
    suspend fun sync(automation: Automation): AppResult<Unit>

    /** Drops any alarm held for this automation — for a delete. */
    suspend fun cancel(automationId: Long): AppResult<Unit>

    /** Re-arms every enabled time-based automation. For boot, where the OS has dropped them all. */
    suspend fun rescheduleAll(): AppResult<Unit>
}
