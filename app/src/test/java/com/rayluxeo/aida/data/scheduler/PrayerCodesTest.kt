package com.rayluxeo.aida.data.scheduler

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.salah.Prayer
import java.time.LocalDate
import org.junit.Test

/**
 * The arithmetic that keeps the Salah alarms apart — from each other, and from every other
 * scheduler's notifications.
 *
 * Android matches a PendingIntent on its request code and never on its extras, so two prayer alarms
 * sharing a code would be one alarm and the daily rollover would quietly replace what it meant to
 * keep. `notify` replaces by id across the whole app, so a prayer notification id that lands on a
 * reminder's, a brief's, a timer's or an alarm clock's would take the other's card down. None of
 * that shows on a device until the day it matters, which is why it is proved here.
 */
class PrayerCodesTest {

    @Test
    fun `no two prayer alarms share a request code`() {
        val codes = requestCodes()

        assertThat(codes).containsNoDuplicates()
    }

    @Test
    fun `prayer request codes sit in their own fixed band`() {
        for (code in requestCodes()) {
            assertThat(code).isAtLeast(3_000_000)
        }
    }

    @Test
    fun `no two prayer notifications share an id`() {
        assertThat(notificationIds()).containsNoDuplicates()
    }

    @Test
    fun `every prayer notification id clears the reminder, brief, timer and alarm bands`() {
        // Reminders key on a positive row id; the briefs took -101 and -102; timers a band from
        // -1_000; the alarm clock a band from -2_000_000. The -3_000_000 band is below them all.
        for (id in notificationIds()) {
            assertThat(id).isAtMost(-3_000_000)
        }
    }

    @Test
    fun `a check-in keeps its own id from one day to the next`() {
        val asrToday = PrayerCodes.checkInNotificationId(Prayer.ASR, LocalDate.of(2026, 10, 10))
        val asrTomorrow = PrayerCodes.checkInNotificationId(Prayer.ASR, LocalDate.of(2026, 10, 11))

        assertThat(asrToday).isNotEqualTo(asrTomorrow)
    }

    private fun requestCodes(): List<Int> =
        Prayer.OBLIGATORY.map(PrayerCodes::prayerTimeRequestCode) +
            Prayer.OBLIGATORY.map(PrayerCodes::checkInRequestCode) +
            PrayerCodes.tahajjudRequestCode() +
            PrayerCodes.rolloverRequestCode()

    /** The fixed ids plus a decade of daily check-in ids — far more days than any collision needs. */
    private fun notificationIds(): List<Int> {
        val fixed = Prayer.OBLIGATORY.map(PrayerCodes::prayerTimeNotificationId) +
            PrayerCodes.tahajjudNotificationId()
        val start = LocalDate.of(2025, 1, 1)
        val checkIns = buildList {
            for (offset in 0 until DAYS) {
                val date = start.plusDays(offset.toLong())
                for (prayer in Prayer.OBLIGATORY) {
                    add(PrayerCodes.checkInNotificationId(prayer, date))
                }
            }
        }
        return fixed + checkIns
    }

    private companion object {
        const val DAYS = 4_000
    }
}
