package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.data.db.dao.ReminderDao
import com.rayluxeo.aida.data.db.dao.TaskDao
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.repository.BriefRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the brief in one pass so the counts and the greeting describe the same moment.
 * Everything is resolved in the device's zone through [MiraClock]; nothing asks SQLite what
 * time it is.
 */
@Singleton
class BriefRepositoryImpl @Inject constructor(
    private val tasks: TaskDao,
    private val reminders: ReminderDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : BriefRepository {

    override suspend fun todayBrief(): AppResult<DailyBrief> =
        storageCall(dispatchers, "build daily brief") {
            val now = clock.now()
            val window = clock.dayWindow()
            val next = reminders.nextInState(ReminderState.SCHEDULED, now.toEpochMilli())
            DailyBrief(
                date = clock.today(),
                dayPart = dayPartFor(clock.localNow().hour),
                openTaskCount = tasks.countOpen(),
                dueTodayCount = tasks.countDueBetween(window.startMillis, window.endMillis),
                overdueCount = tasks.countOverdue(window.startMillis),
                completedTodayCount = tasks.countCompletedBetween(window.startMillis, window.endMillis),
                nextReminderTitle = next?.title,
                nextReminderAt = next?.let { Instant.ofEpochMilli(it.triggerAtEpochMillis) },
            )
        }

    private fun dayPartFor(hour: Int): DayPart = when (hour) {
        in 5..11 -> DayPart.MORNING
        in 12..16 -> DayPart.AFTERNOON
        in 17..20 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }
}
