package com.rayluxeo.aida.domain.scheduler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Reminder

/**
 * Arms and disarms the OS-level alarm behind a [Reminder]. Implemented over AlarmManager
 * because a reminder that fires at the wrong minute is worse than no reminder at all.
 */
interface ReminderScheduler {
    /** True when the OS currently allows exact alarms (API 31+ can revoke this). */
    fun canScheduleExact(): Boolean
    suspend fun schedule(reminder: Reminder): AppResult<Unit>
    suspend fun cancel(reminderId: Long): AppResult<Unit>
}
