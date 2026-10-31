package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.MiraClock

/**
 * Half-open [startMillis, endMillis) covering one local day. Both the task counts and the daily
 * brief resolve "today" through this single function, so a task can never be due today in one
 * place and overdue in another.
 */
internal data class DayWindow(val startMillis: Long, val endMillis: Long)

internal fun MiraClock.dayWindow(): DayWindow {
    val zone = zone()
    val today = today()
    return DayWindow(
        startMillis = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
