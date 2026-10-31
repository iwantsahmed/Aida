package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.salah.Prayer
import java.time.Duration

/**
 * The few Salah words that both the brief (`BriefWording`) and Home (`HomeWording`) need, kept in
 * one place so the prayer names and the countdown cannot drift between the two phrasebooks the way
 * two hand-maintained copies would. It lives in `ui/response` — where the rest of Mira's rendering
 * is — because the domain carries facts only (§4 rule 6): a `Prayer` and a `Duration`, never a
 * spoken name.
 */

/** An obligatory prayer's name in Abu's language. Tahajjud is included for completeness. */
internal fun salahPrayerName(prayer: Prayer, language: SpokenLanguage): String = when (language) {
    SpokenLanguage.ENGLISH -> when (prayer) {
        Prayer.FAJR -> "Fajr"
        Prayer.DHUHR -> "Dhuhr"
        Prayer.ASR -> "Asr"
        Prayer.MAGHRIB -> "Maghrib"
        Prayer.ISHA -> "Isha"
        Prayer.TAHAJJUD -> "Tahajjud"
    }
    SpokenLanguage.BENGALI -> when (prayer) {
        Prayer.FAJR -> "ফজর"
        Prayer.DHUHR -> "যোহর"
        Prayer.ASR -> "আসর"
        Prayer.MAGHRIB -> "মাগরিব"
        Prayer.ISHA -> "এশা"
        Prayer.TAHAJJUD -> "তাহাজ্জুদ"
    }
}

/**
 * How long until the next prayer, worded for a live countdown: "1h 24m remaining" /
 * "১ ঘণ্টা ২৪ মিনিট বাকি". The duration never goes negative ([com.rayluxeo.aida.domain.salah.SalahClock]
 * clamps it at zero), and the last minute reads as "Now" rather than "0m".
 */
internal fun salahRemainingLabel(remaining: Duration, language: SpokenLanguage): String {
    val minutes = remaining.toMinutes()
    val hours = minutes / 60
    val mins = minutes % 60
    return when (language) {
        SpokenLanguage.ENGLISH -> when {
            minutes <= 0L -> "Now"
            hours > 0L && mins == 0L -> "${hours}h remaining"
            hours > 0L -> "${hours}h ${mins}m remaining"
            else -> "${mins}m remaining"
        }
        SpokenLanguage.BENGALI -> when {
            minutes <= 0L -> "এখন"
            hours > 0L && mins == 0L -> "${bengaliNumerals(hours.toString())} ঘণ্টা বাকি"
            hours > 0L -> "${bengaliNumerals(hours.toString())} ঘণ্টা ${bengaliNumerals(mins.toString())} মিনিট বাকি"
            else -> "${bengaliNumerals(mins.toString())} মিনিট বাকি"
        }
    }
}
