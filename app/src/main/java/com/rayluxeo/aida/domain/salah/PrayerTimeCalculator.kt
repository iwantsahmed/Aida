package com.rayluxeo.aida.domain.salah

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Computes a day's prayer times for a place, entirely on the device (§2, §3). Pure and
 * deterministic: given the date, the zone, the coordinates and the parameters it always returns the
 * same times, so every case the tests care about — the five prayers, a Hanafi Asr, a travel day, a
 * daylight-saving change — is checkable without a clock or a network.
 *
 * The method is the standard astronomical one (see [SolarMath]): the sun's noon fixes Dhuhr; the
 * depression angles for the method fix Fajr and Isha; the shadow factor for the madhab fixes Asr;
 * and a pair of refinement passes removes the small error from using an approximate day fraction
 * when reading the sun's position.
 */
class PrayerTimeCalculator @Inject constructor() {

    fun calculate(
        date: LocalDate,
        zone: ZoneId,
        coordinates: GeoCoordinates,
        parameters: PrayerParameters,
    ): PrayerTimes {
        val latitude = coordinates.latitude
        val longitude = coordinates.longitude
        val offsetHours = zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 3600.0
        val jDate = SolarMath.julianDay(date.year, date.monthValue, date.dayOfMonth) - longitude / (15.0 * 24.0)

        val fajrAngle = parameters.effectiveFajrAngle
        val ishaAngle = parameters.effectiveIshaAngle
        val asrFactor = parameters.madhab.asrShadowFactor.toDouble()
        val intervalIsha = parameters.method.ishaIntervalMinutes > 0

        // Raw solar times in hours of the day, refined twice.
        var times = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            times = computePass(times, jDate, latitude, fajrAngle, ishaAngle, asrFactor, intervalIsha)
        }

        // Shift from solar time at Greenwich to the location's civil clock.
        for (i in times.indices) times[i] += offsetHours - longitude / 15.0

        if (parameters.highLatitudeRule != HighLatitudeRule.NONE) {
            applyHighLatitude(times, fajrAngle, ishaAngle, parameters.highLatitudeRule)
        }

        // Maghrib is sunset; Isha by interval replaces the angle result where the method says so.
        var isha = times[IDX_ISHA]
        if (intervalIsha) isha = times[IDX_SUNSET] + parameters.method.ishaIntervalMinutes / 60.0

        val adjustments = parameters.adjustments
        val fajr = times[IDX_FAJR] + adjustments.fajr / 60.0
        val sunrise = times[IDX_SUNRISE]
        val dhuhr = times[IDX_DHUHR] + adjustments.dhuhr / 60.0
        val asr = times[IDX_ASR] + adjustments.asr / 60.0
        val maghrib = times[IDX_SUNSET] + adjustments.maghrib / 60.0
        isha += adjustments.isha / 60.0

        return PrayerTimes(
            date = date,
            zone = zone,
            fajr = hoursToZdt(date, zone, fajr),
            sunrise = hoursToZdt(date, zone, sunrise),
            dhuhr = hoursToZdt(date, zone, dhuhr),
            asr = hoursToZdt(date, zone, asr),
            maghrib = hoursToZdt(date, zone, maghrib),
            isha = hoursToZdt(date, zone, isha),
        )
    }

    /** The night window for Tahajjud: Maghrib tonight to Fajr tomorrow, with its middle and last third. */
    fun tahajjudWindow(maghribTonight: ZonedDateTime, fajrTomorrow: ZonedDateTime): TahajjudWindow {
        val nightSeconds = Duration.between(maghribTonight, fajrTomorrow).seconds
        return TahajjudWindow(
            maghrib = maghribTonight,
            middleOfNight = maghribTonight.plusSeconds(nightSeconds / 2),
            lastThirdStart = maghribTonight.plusSeconds(nightSeconds * 2 / 3),
            nextFajr = fajrTomorrow,
        )
    }

    private fun computePass(
        previous: DoubleArray,
        jDate: Double,
        latitude: Double,
        fajrAngle: Double,
        ishaAngle: Double,
        asrFactor: Double,
        intervalIsha: Boolean,
    ): DoubleArray {
        val portions = DoubleArray(previous.size) { previous[it] / 24.0 }
        val result = DoubleArray(previous.size)
        result[IDX_FAJR] = sunAngleTime(fajrAngle, portions[IDX_FAJR], jDate, latitude, counterClockwise = true)
        result[IDX_SUNRISE] = sunAngleTime(RISE_SET_ANGLE, portions[IDX_SUNRISE], jDate, latitude, counterClockwise = true)
        result[IDX_DHUHR] = midDay(portions[IDX_DHUHR], jDate)
        result[IDX_ASR] = asrTime(asrFactor, portions[IDX_ASR], jDate, latitude)
        result[IDX_SUNSET] = sunAngleTime(RISE_SET_ANGLE, portions[IDX_SUNSET], jDate, latitude, counterClockwise = false)
        result[IDX_ISHA] = if (intervalIsha) {
            result[IDX_SUNSET]
        } else {
            sunAngleTime(ishaAngle, portions[IDX_ISHA], jDate, latitude, counterClockwise = false)
        }
        return result
    }

    private fun midDay(portion: Double, jDate: Double): Double =
        SolarMath.fixHour(12.0 - SolarMath.equationOfTime(jDate + portion))

    private fun sunAngleTime(
        angle: Double,
        portion: Double,
        jDate: Double,
        latitude: Double,
        counterClockwise: Boolean,
    ): Double {
        val decl = SolarMath.declination(jDate + portion)
        val noon = midDay(portion, jDate)
        val term = (-SolarMath.sinDeg(angle) - SolarMath.sinDeg(decl) * SolarMath.sinDeg(latitude)) /
            (SolarMath.cosDeg(decl) * SolarMath.cosDeg(latitude))
        val t = (1.0 / 15.0) * SolarMath.arccosDeg(term)
        return noon + if (counterClockwise) -t else t
    }

    private fun asrTime(factor: Double, portion: Double, jDate: Double, latitude: Double): Double {
        val decl = SolarMath.declination(jDate + portion)
        val angle = -SolarMath.arccotDeg(factor + SolarMath.tanDeg(abs(latitude - decl)))
        return sunAngleTime(angle, portion, jDate, latitude, counterClockwise = false)
    }

    private fun applyHighLatitude(times: DoubleArray, fajrAngle: Double, ishaAngle: Double, rule: HighLatitudeRule) {
        val night = SolarMath.fixHour(times[IDX_SUNRISE] - times[IDX_SUNSET])
        times[IDX_FAJR] = adjustForHighLatitude(times[IDX_FAJR], times[IDX_SUNRISE], fajrAngle, night, rule, counterClockwise = true)
        times[IDX_ISHA] = adjustForHighLatitude(times[IDX_ISHA], times[IDX_SUNSET], ishaAngle, night, rule, counterClockwise = false)
    }

    private fun adjustForHighLatitude(
        time: Double,
        base: Double,
        angle: Double,
        night: Double,
        rule: HighLatitudeRule,
        counterClockwise: Boolean,
    ): Double {
        val portion = nightPortion(angle, night, rule)
        val diff = if (counterClockwise) SolarMath.fixHour(base - time) else SolarMath.fixHour(time - base)
        return if (time.isNaN() || diff > portion) {
            base + if (counterClockwise) -portion else portion
        } else {
            time
        }
    }

    private fun nightPortion(angle: Double, night: Double, rule: HighLatitudeRule): Double = when (rule) {
        HighLatitudeRule.MIDDLE_OF_NIGHT -> night / 2.0
        HighLatitudeRule.SEVENTH_OF_NIGHT -> night / 7.0
        HighLatitudeRule.TWILIGHT_ANGLE -> angle / 60.0 * night
        HighLatitudeRule.NONE -> night // unreachable: NONE short-circuits before here
    }

    /** Place an hour-of-day value (possibly < 0 or ≥ 24 at extreme latitudes) on the civil date, to the minute. */
    private fun hoursToZdt(date: LocalDate, zone: ZoneId, hours: Double): ZonedDateTime {
        val safe = if (hours.isNaN()) 0.0 else hours
        val minutes = (safe * 60.0).roundToLong()
        return date.atStartOfDay(zone).plusMinutes(minutes)
    }

    private companion object {
        const val IDX_FAJR = 0
        const val IDX_SUNRISE = 1
        const val IDX_DHUHR = 2
        const val IDX_ASR = 3
        const val IDX_SUNSET = 4
        const val IDX_ISHA = 5

        /** Sun centre 50′ below the horizon — the standard refraction-plus-semidiameter sunrise/sunset depth. */
        const val RISE_SET_ANGLE = 0.833
    }
}
