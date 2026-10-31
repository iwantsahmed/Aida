package com.rayluxeo.aida.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * An alarm clock Abu set, which is not the same thing as [AlarmRecord].
 *
 * [AlarmRecord] is the ledger of what Mira asked the operating system to do. This is the thing Abu
 * thinks of as "my 7 o'clock": a time of day, the days it repeats on, a label, and a switch.
 *
 * [at] is a time of day rather than an instant on purpose. A repeating alarm means seven o'clock
 * wherever Abu happens to be, and storing an instant would move it every time he crossed a zone.
 */
data class Alarm(
    val id: Long = 0L,
    val label: String,
    val at: LocalTime,
    /** Empty for a one-time alarm: it fires at the next [at] and then switches itself off. */
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val isEnabled: Boolean = true,
    /** The moment it is armed for, or null while it is switched off. Derived, then stored. */
    val nextTriggerAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val repeats: Boolean get() = repeatDays.isNotEmpty()
}

/**
 * A place a reminder waits at.
 *
 * [label] is what Abu called it — "the office" — not an address Mira looked up. She has no
 * geocoder and adding one would mean sending his places to somebody's server.
 */
data class ReminderPlace(
    val id: Long = 0L,
    val reminderId: Long,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val trigger: PlaceTrigger,
    /** Whether the OS is holding this alert right now. False is ordinary, not an error. */
    val isRegistered: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)
