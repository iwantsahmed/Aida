package com.rayluxeo.aida.data.scheduler

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The arithmetic that keeps several timers apart.
 *
 * Android matches a PendingIntent on its request code and never on its extras, and `notify`
 * replaces by id across the whole app, so a collision in either space is silent: Abu sets two
 * timers, sees two, and is told about one. None of that shows up on a device until the second
 * timer is the one that matters, which is why it is proved here instead.
 *
 * The other two bands are the ones already in use — reminders key both spaces on the row id, and
 * Room only ever hands out positive ids, while the morning and evening briefs took -101 and -102.
 */
class TimerCodesTest {

    @Test
    fun `no two timers share an alarm`() {
        val codes = IDS.map { TimerCodes.requestCode(it) }

        assertThat(codes).containsNoDuplicates()
    }

    @Test
    fun `no two timers share a notification`() {
        val ids = IDS.map { TimerCodes.notificationId(it) }

        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `a timer never takes a reminder's alarm or a brief's`() {
        for (id in IDS) {
            val code = TimerCodes.requestCode(id)

            assertThat(code).isLessThan(0)
            assertThat(code).isNotEqualTo(MORNING_BRIEF_REQUEST_CODE)
            assertThat(code).isNotEqualTo(EVENING_BRIEF_REQUEST_CODE)
        }
    }

    @Test
    fun `a timer never replaces a reminder's notification or a brief's`() {
        for (id in IDS) {
            val notificationId = TimerCodes.notificationId(id)

            assertThat(notificationId).isLessThan(0)
            assertThat(notificationId).isNotEqualTo(MORNING_BRIEF_NOTIFICATION_ID)
            assertThat(notificationId).isNotEqualTo(EVENING_BRIEF_NOTIFICATION_ID)
        }
    }

    private companion object {
        /** More countdowns than Abu will ever have open, and the first ids Room hands out. */
        val IDS: List<Long> = (1L..5_000L).toList()

        const val MORNING_BRIEF_REQUEST_CODE = -101
        const val EVENING_BRIEF_REQUEST_CODE = -102
        const val MORNING_BRIEF_NOTIFICATION_ID = -101
        const val EVENING_BRIEF_NOTIFICATION_ID = -102
    }
}
