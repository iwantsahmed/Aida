package com.rayluxeo.aida.data.scheduler

import com.rayluxeo.aida.domain.salah.Prayer
import java.time.LocalDate

/**
 * Where the Salah alarms and their notifications sit in the two id spaces Mira shares across the
 * app, kept apart from the reminder, brief, timer and alarm-clock schedulers.
 *
 * Two different kinds of collision are at stake, and they are guarded differently.
 *
 * **Alarm request codes.** Android identifies a `PendingIntent` by its request code *and the
 * intent's component* — never by its extras. Every Salah alarm targets [PrayerReceiver], a
 * component no other scheduler uses, so a Salah request code cannot collide with a reminder's or a
 * timer's whatever its numeric value (the same reasoning the alarm clock relies on). The only job
 * left here is to keep the four Salah alarm kinds apart *from each other*, so re-arming one on the
 * daily rollover replaces only itself and never doubles up. They are given a fixed positive band
 * well above any row id a single-person app will ever reach; the bands also keep the kinds disjoint.
 *
 * **Notification ids.** `notify` replaces by id across the whole app, whatever channel a
 * notification is on, so this space must be genuinely disjoint by value. Reminders key it on a
 * positive row id; the two briefs took -101 and -102; timers a band from -1_000; the alarm clock a
 * band from -2_000_000. Salah takes the -3_000_000 band, which none of those reach. The check-in id
 * folds in the day so a check-in Abu left unanswered yesterday is not silently replaced by today's.
 *
 * It is pulled out of the scheduler because this arithmetic is the one part of arming a prayer that
 * can be wrong in a way no device test would catch, and a plain JUnit test reaches it here with no
 * `AlarmManager` in sight.
 */
internal object PrayerCodes {

    // ---- Alarm request codes (all target PrayerReceiver). ----

    private const val PRAYER_TIME_REQUEST_BASE = 3_000_000
    private const val CHECK_IN_REQUEST_BASE = 3_100_000
    private const val TAHAJJUD_REQUEST_CODE = 3_200_000
    private const val ROLLOVER_REQUEST_CODE = 3_300_000

    fun prayerTimeRequestCode(prayer: Prayer): Int = PRAYER_TIME_REQUEST_BASE + prayer.ordinal

    fun checkInRequestCode(prayer: Prayer): Int = CHECK_IN_REQUEST_BASE + prayer.ordinal

    fun tahajjudRequestCode(): Int = TAHAJJUD_REQUEST_CODE

    fun rolloverRequestCode(): Int = ROLLOVER_REQUEST_CODE

    // ---- Notification ids (the one app-wide namespace). ----

    private const val PRAYER_TIME_NOTIFICATION_BASE = -3_000_000
    private const val TAHAJJUD_NOTIFICATION_ID = -3_000_100
    private const val CHECK_IN_NOTIFICATION_BASE = -3_100_000

    /** Wider than the five obligatory prayers, so a day's check-in ids never run into the next day's. */
    private const val PRAYERS_PER_DAY = 8

    fun prayerTimeNotificationId(prayer: Prayer): Int = PRAYER_TIME_NOTIFICATION_BASE - prayer.ordinal

    fun tahajjudNotificationId(): Int = TAHAJJUD_NOTIFICATION_ID

    /**
     * Distinct per prayer *and per day*, so an unanswered check-in from yesterday is not quietly
     * replaced when today's for the same prayer arrives. The epoch day only grows going forward, so
     * this stays well below the prayer-time and Tahajjud ids above it and can never climb into them.
     */
    fun checkInNotificationId(prayer: Prayer, date: LocalDate): Int =
        CHECK_IN_NOTIFICATION_BASE - (date.toEpochDay().toInt() * PRAYERS_PER_DAY + prayer.ordinal)
}
