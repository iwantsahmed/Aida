package com.rayluxeo.aida.domain.model

import java.time.Duration
import java.time.Instant

/**
 * A countdown.
 *
 * [endsAt] is the stored truth and what is left is derived from it, so a running timer is still
 * correct after the process is killed: the answer comes from the wall clock rather than from
 * whatever the app last managed to write down. Only a paused timer carries [remainingWhenPaused],
 * because a paused countdown has no end instant left to measure against.
 *
 * [startedAt] is when Abu started the timer, not when it last resumed, so elapsed time is
 * deliberately not derivable from it across a pause.
 */
data class TimerRecord(
    val id: Long = 0L,
    val label: String,
    val duration: Duration,
    val startedAt: Instant,
    val endsAt: Instant,
    val state: TimerState,
    val remainingWhenPaused: Duration? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {

    /**
     * What is left at [now]. Never negative: a running timer whose moment has passed but which
     * nothing has marked finished yet reads as zero, rather than counting backwards.
     */
    fun remainingAt(now: Instant): Duration = when (state) {
        TimerState.RUNNING -> Duration.between(now, endsAt).let {
            if (it.isNegative) Duration.ZERO else it
        }
        TimerState.PAUSED -> remainingWhenPaused ?: Duration.ZERO
        TimerState.FINISHED, TimerState.CANCELLED -> Duration.ZERO
    }
}
