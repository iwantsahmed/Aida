package com.rayluxeo.aida.domain.salah

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** A date on the Islamic calendar, as numbers only — the month's name is rendered per language in the UI. */
data class HijriDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

/**
 * Converts a civil date to the Islamic (Umm al-Qura) calendar using the JDK's own [HijrahDate],
 * which is bundled, offline and free (§2, §3) — no library and no network. It is a calculated
 * calendar, so a local moon sighting can differ by a day; Mira shows it as the computed Hijri date,
 * not as a ruling.
 */
object HijriCalendar {
    fun from(date: LocalDate): HijriDate {
        val h = HijrahDate.from(date)
        return HijriDate(
            year = h.get(ChronoField.YEAR_OF_ERA),
            month = h.get(ChronoField.MONTH_OF_YEAR),
            day = h.get(ChronoField.DAY_OF_MONTH),
        )
    }
}
