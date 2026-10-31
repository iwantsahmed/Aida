package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * What Mira actually asked the operating system to do, as opposed to what Abu asked for.
 *
 * A [Reminder] is intent; this is the OS-side fact. Keeping the two apart is what makes
 * "the reminder is still there, so why did nothing happen" answerable after a reboot or after
 * the exact-alarm permission was taken away.
 *
 * [reminderId] goes null rather than the record vanishing when its reminder is deleted, because
 * an alarm that fired is history and history does not get tidied away.
 */
data class AlarmRecord(
    val id: Long = 0L,
    val reminderId: Long?,
    val label: String,
    val triggerAt: Instant,
    /** False when the OS refused an exact alarm and Mira settled for a window. */
    val isExact: Boolean,
    val state: AlarmState,
    val firedAt: Instant? = null,
    /** The PendingIntent request code, which is how a scheduled alarm is found and cancelled. */
    val requestCode: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
