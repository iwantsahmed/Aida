package com.rayluxeo.aida.domain.salah

/**
 * A named set of twilight angles used to place Fajr and Isha, as published by the major prayer-time
 * authorities. Dhuhr, Asr, Sunrise and Maghrib do not depend on the method — they are fixed by the
 * sun's position — so only the Fajr and Isha twilight depths vary here.
 *
 * [fajrAngle] / [ishaAngle] are the sun's depression below the horizon, in degrees. When
 * [ishaIntervalMinutes] is non-zero the method fixes Isha as a set number of minutes after Maghrib
 * instead of by an angle (Umm al-Qura does this), and [ishaAngle] is then unused.
 *
 * [CUSTOM] carries placeholder angles; the real values travel on [PrayerParameters.customFajrAngle]
 * and [PrayerParameters.customIshaAngle] so Abu can match a local mosque's timetable exactly.
 */
enum class CalculationMethod(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int,
) {
    /** University of Islamic Sciences, Karachi — the method most Bangladesh timetables follow. */
    KARACHI(fajrAngle = 18.0, ishaAngle = 18.0, ishaIntervalMinutes = 0),

    MUSLIM_WORLD_LEAGUE(fajrAngle = 18.0, ishaAngle = 17.0, ishaIntervalMinutes = 0),

    EGYPTIAN(fajrAngle = 19.5, ishaAngle = 17.5, ishaIntervalMinutes = 0),

    /** Umm al-Qura, Makkah — Isha is a fixed 90 minutes after Maghrib rather than an angle. */
    UMM_AL_QURA(fajrAngle = 18.5, ishaAngle = 0.0, ishaIntervalMinutes = 90),

    /** Islamic Society of North America. */
    NORTH_AMERICA_ISNA(fajrAngle = 15.0, ishaAngle = 15.0, ishaIntervalMinutes = 0),

    /** Abu's own angles, read from [PrayerParameters]. */
    CUSTOM(fajrAngle = 18.0, ishaAngle = 17.0, ishaIntervalMinutes = 0),
}
