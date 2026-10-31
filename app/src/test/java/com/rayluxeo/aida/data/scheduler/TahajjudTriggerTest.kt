package com.rayluxeo.aida.data.scheduler

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.salah.TahajjudReminderMode
import com.rayluxeo.aida.domain.salah.TahajjudWindow
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * Which minute of the night a Tahajjud reminder falls on. Pure, so the choice is proved here with
 * no clock and no AlarmManager — the two invariants that matter are that it never falls before the
 * last third begins and never at or after Fajr.
 */
class TahajjudTriggerTest {

    @Test
    fun `last third mode fires at the start of the last third`() {
        assertThat(TahajjudTrigger.at(WINDOW, TahajjudReminderMode.LAST_THIRD, 0))
            .isEqualTo(lastThird)
    }

    @Test
    fun `fifteen after adds a quarter hour`() {
        assertThat(TahajjudTrigger.at(WINDOW, TahajjudReminderMode.FIFTEEN_AFTER_LAST_THIRD, 0))
            .isEqualTo(lastThird.plusMinutes(15))
    }

    @Test
    fun `thirty after adds a half hour`() {
        assertThat(TahajjudTrigger.at(WINDOW, TahajjudReminderMode.THIRTY_AFTER_LAST_THIRD, 0))
            .isEqualTo(lastThird.plusMinutes(30))
    }

    @Test
    fun `custom uses the chosen offset into the last third`() {
        assertThat(TahajjudTrigger.at(WINDOW, TahajjudReminderMode.CUSTOM, 45))
            .isEqualTo(lastThird.plusMinutes(45))
    }

    @Test
    fun `disabled has no moment at all`() {
        assertThat(TahajjudTrigger.at(WINDOW, TahajjudReminderMode.DISABLED, 0)).isNull()
    }

    @Test
    fun `a custom offset past Fajr is pulled back to a minute before it`() {
        // The night's last third runs 01:20–05:00; an offset of 300 minutes would land past Fajr.
        val result = TahajjudTrigger.at(WINDOW, TahajjudReminderMode.CUSTOM, 300)

        assertThat(result).isEqualTo(nextFajr.minusMinutes(1))
        assertThat(result!!.isBefore(nextFajr)).isTrue()
    }

    @Test
    fun `a negative custom offset is pulled up to the last third`() {
        assertThat(TahajjudTrigger.at(WINDOW, TahajjudReminderMode.CUSTOM, -30))
            .isEqualTo(lastThird)
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

        // A short, deliberately tight night so the Fajr clamp is exercised: the last third runs
        // 04:15 to 05:00.
        val maghrib: ZonedDateTime = ZonedDateTime.of(2026, 10, 10, 18, 0, 0, 0, ZONE)
        val nextFajr: ZonedDateTime = ZonedDateTime.of(2026, 10, 11, 5, 0, 0, 0, ZONE)
        val lastThird: ZonedDateTime = ZonedDateTime.of(2026, 10, 11, 1, 20, 0, 0, ZONE)

        val WINDOW = TahajjudWindow(
            maghrib = maghrib,
            middleOfNight = ZonedDateTime.of(2026, 10, 10, 23, 30, 0, 0, ZONE),
            lastThirdStart = lastThird,
            nextFajr = nextFajr,
        )
    }
}
