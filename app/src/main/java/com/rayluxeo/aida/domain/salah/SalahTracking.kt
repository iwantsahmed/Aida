package com.rayluxeo.aida.domain.salah

import java.time.Instant
import java.time.LocalDate

/**
 * One obligatory prayer's record for a day — how it stands, and when it was logged. The domain view
 * of a row in the tracking table; the five obligatory prayers only ([Prayer.TAHAJJUD] has its own
 * record type, because it is never counted among them).
 */
data class ObligatoryTracking(
    val localDate: LocalDate,
    val prayer: Prayer,
    val status: PrayerStatus,
    val scheduledAt: Instant? = null,
    val prayedAt: Instant? = null,
    val source: TrackingSource = TrackingSource.MANUAL,
    val notes: String? = null,
)

/** A night's Tahajjud record. Voluntary, so it is never "missed" — only prayed or not. */
data class TahajjudTracking(
    val localDate: LocalDate,
    val status: TahajjudStatus,
    val prayedAt: Instant? = null,
    val source: TrackingSource = TrackingSource.MANUAL,
    val notes: String? = null,
)

/** A whole day's tracking, the shape the screens and the statistics read. */
data class SalahDayTracking(
    val date: LocalDate,
    val obligatory: List<ObligatoryTracking>,
    val tahajjud: TahajjudTracking?,
) {
    /** The status of one obligatory prayer, defaulting to not-recorded when nothing is logged. */
    fun statusOf(prayer: Prayer): PrayerStatus =
        obligatory.firstOrNull { it.prayer == prayer }?.status ?: PrayerStatus.NOT_RECORDED

    /** How many of the five are completed. */
    val completedCount: Int get() = Prayer.OBLIGATORY.count { statusOf(it).isCompleted }

    /** True only when all five obligatory prayers are completed. */
    val isAllFive: Boolean get() = Prayer.OBLIGATORY.all { statusOf(it).isCompleted }
}
