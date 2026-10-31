package com.rayluxeo.aida.domain.salah

/**
 * A point on the earth, in degrees. Latitude is positive north, longitude positive east.
 */
data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Per-prayer minute corrections Abu can set to line Mira up with his own mosque's timetable, which
 * can differ from any calculation by a minute or two. Positive moves the time later.
 */
data class PrayerAdjustments(
    val fajr: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
)

/**
 * Everything that shapes a day's prayer times apart from the date and place: the twilight method,
 * the Asr school, the high-latitude fallback, any custom angles, and Abu's own minute corrections.
 */
data class PrayerParameters(
    val method: CalculationMethod,
    val madhab: Madhab,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
    val customFajrAngle: Double? = null,
    val customIshaAngle: Double? = null,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
) {
    /** The Fajr depression angle actually in force — the custom one when set, else the method's. */
    val effectiveFajrAngle: Double get() = customFajrAngle ?: method.fajrAngle

    /** The Isha depression angle actually in force (ignored when the method uses a fixed interval). */
    val effectiveIshaAngle: Double get() = customIshaAngle ?: method.ishaAngle
}
