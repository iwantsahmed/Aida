package com.rayluxeo.aida.domain.salah

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One day's computed prayer moments, each a wall-clock instant in the location's own zone.
 *
 * Sunrise is carried because it bounds Fajr (Fajr must have been prayed before it) and because the
 * night calculation for Tahajjud needs the following day's Fajr — but it is not a prayer. The five
 * obligatory times are reachable by [Prayer] through [time].
 */
data class PrayerTimes(
    val date: LocalDate,
    val zone: ZoneId,
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime,
) {
    /** The scheduled moment of an obligatory prayer; null for [Prayer.TAHAJJUD], which has no fixed time. */
    fun time(prayer: Prayer): ZonedDateTime? = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
        Prayer.TAHAJJUD -> null
    }

    /** The five obligatory moments in order, for iterating a day. */
    val obligatory: List<Pair<Prayer, ZonedDateTime>>
        get() = listOf(
            Prayer.FAJR to fajr,
            Prayer.DHUHR to dhuhr,
            Prayer.ASR to asr,
            Prayer.MAGHRIB to maghrib,
            Prayer.ISHA to isha,
        )
}

/**
 * The night window in which Tahajjud may be prayed, from the night's Maghrib to the following
 * Fajr. [lastThirdStart] is the moment the last third of the night begins — the most virtuous
 * window and the one Mira offers as the reminder default. None of these is presented as a required
 * minute (Abu's instruction): the last third is a recommended window, not an obligation.
 */
data class TahajjudWindow(
    val maghrib: ZonedDateTime,
    val middleOfNight: ZonedDateTime,
    val lastThirdStart: ZonedDateTime,
    val nextFajr: ZonedDateTime,
)
