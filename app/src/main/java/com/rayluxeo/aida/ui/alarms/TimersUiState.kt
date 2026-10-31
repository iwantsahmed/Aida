package com.rayluxeo.aida.ui.alarms

import com.rayluxeo.aida.core.common.SpokenLanguage
import java.time.Duration

/**
 * One countdown as the list shows it.
 *
 * [countdown] is recomputed from the timer's end instant on every tick rather than decremented,
 * so it is still right after the process has been killed and rebuilt. Nothing here is stored.
 */
data class TimerRow(
    val id: Long,
    val label: String,
    val countdown: String,
    val durationLabel: String,
    val isRunning: Boolean,
)

/**
 * The start-a-timer sheet.
 *
 * [minutes] is held as the text Abu typed rather than as a number, because a half-typed field is
 * a normal state and coercing it to zero would fight him while he is still typing.
 */
data class TimerComposerUiState(
    val label: String = "",
    val minutes: String = "",
    val isSaving: Boolean = false,
) {
    /** Null until the field holds a countdown Mira can actually run. */
    val duration: Duration?
        get() = minutes.toLongOrNull()
            ?.takeIf { it in 1..MAX_MINUTES }
            ?.let { Duration.ofMinutes(it) }

    val canStart: Boolean get() = duration != null && !isSaving

    private companion object {
        /** Twenty-four hours. Past that it is an alarm, and Mira has a better tool for one. */
        const val MAX_MINUTES = 24L * 60L
    }
}

data class TimersUiState(
    val isLoading: Boolean = true,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val timers: List<TimerRow> = emptyList(),
    val composer: TimerComposerUiState? = null,
    /** False when Android has withheld exact alarms, which a countdown's ending depends on. */
    val canScheduleExact: Boolean = true,
    val notice: AlarmsNotice? = null,
) {
    val isEmpty: Boolean get() = timers.isEmpty()
}

/** The shortcuts above the minutes field, in the order the composer offers them. */
internal val TIMER_PRESET_MINUTES: List<Long> = listOf(1, 5, 10, 15, 25, 30, 45, 60)
