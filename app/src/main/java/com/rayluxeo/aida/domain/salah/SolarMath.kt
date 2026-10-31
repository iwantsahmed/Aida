package com.rayluxeo.aida.domain.salah

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * The solar astronomy prayer times are built from — degree-based trigonometry, the Julian day, and
 * the sun's apparent position. These are the high-precision equations from Jean Meeus's
 * *Astronomical Algorithms* (the same source the well-known open-source prayer-time implementations
 * use); they run entirely on the device with no library and no network, which is what lets Mira
 * show today's times on a plane (§2, §3).
 *
 * Everything here is pure and deterministic — the same date and place always give the same answer —
 * so the whole engine is testable without a clock or a device.
 */
internal object SolarMath {

    private fun toRadians(degrees: Double): Double = degrees * PI / 180.0
    private fun toDegrees(radians: Double): Double = radians * 180.0 / PI

    fun sinDeg(d: Double): Double = sin(toRadians(d))
    fun cosDeg(d: Double): Double = cos(toRadians(d))
    fun tanDeg(d: Double): Double = tan(toRadians(d))
    fun arcsinDeg(x: Double): Double = toDegrees(asin(x))
    fun arccosDeg(x: Double): Double = toDegrees(acos(x))
    fun arccotDeg(x: Double): Double = toDegrees(atan(1.0 / x))
    fun arctan2Deg(y: Double, x: Double): Double = toDegrees(atan2(y, x))

    /** Wrap an angle into [0, 360). */
    fun fixAngle(angle: Double): Double = wrap(angle, 360.0)

    /** Wrap an hour value into [0, 24). */
    fun fixHour(hour: Double): Double = wrap(hour, 24.0)

    private fun wrap(value: Double, modulus: Double): Double {
        val r = value - modulus * floor(value / modulus)
        return if (r < 0) r + modulus else r
    }

    /**
     * The Julian day at midnight UT for a civil date. Prayer maths adds the day fraction of the
     * moment of interest to this before reading the sun's position.
     */
    fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** The sun's declination in degrees at Julian day [jd]. */
    fun declination(jd: Double): Double {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
        val e = 23.439 - 0.00000036 * d
        return arcsinDeg(sinDeg(e) * sinDeg(l))
    }

    /** The equation of time in hours at Julian day [jd] — the gap between apparent and mean solar time. */
    fun equationOfTime(jd: Double): Double {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(arctan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0)
        return q / 15.0 - ra
    }
}
