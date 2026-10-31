package com.rayluxeo.aida.domain.salah

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/**
 * The prayer-time engine, checked for astronomical correctness. The times are a religious matter,
 * so these pin ordering, the equinox day (when any place's sunrise ≈ 6 and sunset ≈ 18 in solar
 * time), the madhab difference, minute corrections, and that moving the place or the zone moves the
 * times the way the sky actually does.
 */
class PrayerTimeCalculatorTest {

    private val calculator = PrayerTimeCalculator()
    private val dhaka = GeoCoordinates(latitude = 23.8103, longitude = 90.4125)
    private val dhakaZone: ZoneId = ZoneId.of("Asia/Dhaka")
    private val karachiShafi = PrayerParameters(method = CalculationMethod.KARACHI, madhab = Madhab.SHAFI)

    private fun PrayerTimes.local(get: (PrayerTimes) -> java.time.ZonedDateTime): LocalTime =
        get(this).toLocalTime()

    @Test
    fun `the five prayers come in order with sunrise between Fajr and Dhuhr`() {
        val t = calculator.calculate(LocalDate.of(2025, 3, 21), dhakaZone, dhaka, karachiShafi)

        assertThat(t.fajr.toInstant().toEpochMilli()).isLessThan(t.sunrise.toInstant().toEpochMilli())
        assertThat(t.sunrise.toInstant().toEpochMilli()).isLessThan(t.dhuhr.toInstant().toEpochMilli())
        assertThat(t.dhuhr.toInstant().toEpochMilli()).isLessThan(t.asr.toInstant().toEpochMilli())
        assertThat(t.asr.toInstant().toEpochMilli()).isLessThan(t.maghrib.toInstant().toEpochMilli())
        assertThat(t.maghrib.toInstant().toEpochMilli()).isLessThan(t.isha.toInstant().toEpochMilli())
    }

    @Test
    fun `equinox in Dhaka gives a roughly twelve-hour day near the zone meridian`() {
        val t = calculator.calculate(LocalDate.of(2025, 3, 21), dhakaZone, dhaka, karachiShafi)

        // Dhaka sits almost on its zone meridian (90 degE, UTC+6), so at the equinox clock time ≈
        // solar time: sunrise near 6, Dhuhr near noon, sunset near 18.
        assertThat(t.sunrise.toLocalTime() in LocalTime.of(5, 45)..LocalTime.of(6, 15)).isTrue()
        assertThat(t.dhuhr.toLocalTime() in LocalTime.of(11, 50)..LocalTime.of(12, 20)).isTrue()
        assertThat(t.maghrib.toLocalTime() in LocalTime.of(17, 55)..LocalTime.of(18, 25)).isTrue()
    }

    @Test
    fun `Fajr precedes sunrise and Isha follows Maghrib by a civil-twilight gap`() {
        val t = calculator.calculate(LocalDate.of(2025, 6, 21), dhakaZone, dhaka, karachiShafi)

        val fajrToSunrise = java.time.Duration.between(t.fajr, t.sunrise).toMinutes()
        val maghribToIsha = java.time.Duration.between(t.maghrib, t.isha).toMinutes()
        assertThat(fajrToSunrise).isIn(50L..110L)
        assertThat(maghribToIsha).isIn(50L..110L)
    }

    @Test
    fun `Hanafi Asr falls later than standard Asr`() {
        val date = LocalDate.of(2025, 3, 21)
        val shafi = calculator.calculate(date, dhakaZone, dhaka, karachiShafi)
        val hanafi = calculator.calculate(date, dhakaZone, dhaka, karachiShafi.copy(madhab = Madhab.HANAFI))

        assertThat(hanafi.asr.toInstant().toEpochMilli()).isGreaterThan(shafi.asr.toInstant().toEpochMilli())
    }

    @Test
    fun `Umm al-Qura sets Isha ninety minutes after Maghrib`() {
        val t = calculator.calculate(
            LocalDate.of(2025, 3, 21),
            dhakaZone,
            dhaka,
            PrayerParameters(method = CalculationMethod.UMM_AL_QURA, madhab = Madhab.SHAFI),
        )

        val gap = java.time.Duration.between(t.maghrib, t.isha).toMinutes()
        assertThat(gap).isEqualTo(90L)
    }

    @Test
    fun `a manual Fajr adjustment moves Fajr by exactly that many minutes`() {
        val date = LocalDate.of(2025, 3, 21)
        val base = calculator.calculate(date, dhakaZone, dhaka, karachiShafi)
        val adjusted = calculator.calculate(
            date,
            dhakaZone,
            dhaka,
            karachiShafi.copy(adjustments = PrayerAdjustments(fajr = 10)),
        )

        assertThat(java.time.Duration.between(base.fajr, adjusted.fajr).toMinutes()).isEqualTo(10L)
        // Other prayers are untouched.
        assertThat(adjusted.dhuhr).isEqualTo(base.dhuhr)
    }

    @Test
    fun `changing the zone shifts every prayer by the offset difference`() {
        val date = LocalDate.of(2025, 3, 21)
        val dhakaTimes = calculator.calculate(date, dhakaZone, dhaka, karachiShafi)
        // Same place, but pretend the clock is UTC+5 instead of UTC+6 — everything moves an hour.
        val shifted = calculator.calculate(date, ZoneOffset.ofHours(5), dhaka, karachiShafi)

        val diff = java.time.Duration.between(shifted.dhuhr.toLocalTime().atDate(date), dhakaTimes.dhuhr.toLocalTime().atDate(date)).toMinutes()
        assertThat(diff).isEqualTo(60L)
    }

    @Test
    fun `moving west makes solar noon later`() {
        val date = LocalDate.of(2025, 3, 21)
        val east = calculator.calculate(date, ZoneOffset.ofHours(6), GeoCoordinates(23.8, 95.0), karachiShafi)
        val west = calculator.calculate(date, ZoneOffset.ofHours(6), GeoCoordinates(23.8, 85.0), karachiShafi)

        assertThat(west.dhuhr.toInstant().toEpochMilli()).isGreaterThan(east.dhuhr.toInstant().toEpochMilli())
    }

    @Test
    fun `tahajjud last third and middle fall where the night divides`() {
        val date = LocalDate.of(2025, 3, 21)
        val today = calculator.calculate(date, dhakaZone, dhaka, karachiShafi)
        val tomorrow = calculator.calculate(date.plusDays(1), dhakaZone, dhaka, karachiShafi)

        val window = calculator.tahajjudWindow(today.maghrib, tomorrow.fajr)
        val nightSeconds = java.time.Duration.between(today.maghrib, tomorrow.fajr).seconds

        assertThat(window.nextFajr).isEqualTo(tomorrow.fajr)
        assertThat(java.time.Duration.between(today.maghrib, window.middleOfNight).seconds)
            .isEqualTo(nightSeconds / 2)
        assertThat(java.time.Duration.between(today.maghrib, window.lastThirdStart).seconds)
            .isEqualTo(nightSeconds * 2 / 3)
        // The last third begins after the middle, and before the next Fajr.
        assertThat(window.lastThirdStart.toInstant().toEpochMilli()).isGreaterThan(window.middleOfNight.toInstant().toEpochMilli())
        assertThat(window.lastThirdStart.toInstant().toEpochMilli()).isLessThan(window.nextFajr.toInstant().toEpochMilli())
    }
}
