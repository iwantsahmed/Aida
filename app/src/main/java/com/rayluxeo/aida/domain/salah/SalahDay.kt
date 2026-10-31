package com.rayluxeo.aida.domain.salah

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * One day resolved for Abu's place and settings: the five prayer times and sunrise, the following
 * dawn (so the next-prayer and the night calculation stay correct after Isha), the Tahajjud window,
 * and the Hijri date. The live countdown is derived from this and the clock on each tick, so nothing
 * here is time-sensitive and the whole value is cacheable.
 */
data class SalahDay(
    val date: LocalDate,
    val location: PrayerLocation,
    val times: PrayerTimes,
    val tomorrowFajr: ZonedDateTime,
    val tahajjud: TahajjudWindow,
    val hijri: HijriDate,
)
