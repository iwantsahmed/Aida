package com.rayluxeo.aida.domain.model

import java.time.Instant
import java.time.LocalDate

enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

data class DailyBrief(
    val date: LocalDate,
    val dayPart: DayPart,
    val openTaskCount: Int,
    val dueTodayCount: Int,
    val overdueCount: Int,
    val completedTodayCount: Int,
    val nextReminderTitle: String?,
    val nextReminderAt: Instant?,
)
