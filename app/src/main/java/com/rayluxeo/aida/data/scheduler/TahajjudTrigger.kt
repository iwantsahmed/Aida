package com.rayluxeo.aida.data.scheduler

import com.rayluxeo.aida.domain.salah.TahajjudReminderMode
import com.rayluxeo.aida.domain.salah.TahajjudWindow
import java.time.ZonedDateTime

/**
 * Which minute of the night a Tahajjud reminder falls on, chosen from the computed [TahajjudWindow]
 * by Abu's [TahajjudReminderMode]. Pure and deterministic, so the choice can be proved without an
 * `AlarmManager` or a clock.
 *
 * Two invariants hold whatever the mode and whatever the custom offset:
 *
 * - the moment is never before [TahajjudWindow.lastThirdStart] — the last third is the window Abu
 *   asked to be woken for, and a reminder earlier than it is for a different night than the one he
 *   meant;
 * - the moment is always strictly before [TahajjudWindow.nextFajr] — a Tahajjud reminder at or
 *   after Fajr has missed the very window it exists for, so it is pulled back to a minute before.
 *
 * None of this is presented to Abu as a requirement (his instruction): the window is offered, the
 * minute is a convenience, and [TahajjudReminderMode.DISABLED] simply has no moment at all.
 */
internal object TahajjudTrigger {

    fun at(
        window: TahajjudWindow,
        mode: TahajjudReminderMode,
        customMinutesIntoLastThird: Int,
    ): ZonedDateTime? {
        val start = window.lastThirdStart
        val raw = when (mode) {
            TahajjudReminderMode.LAST_THIRD -> start
            TahajjudReminderMode.FIFTEEN_AFTER_LAST_THIRD -> start.plusMinutes(15)
            TahajjudReminderMode.THIRTY_AFTER_LAST_THIRD -> start.plusMinutes(30)
            TahajjudReminderMode.CUSTOM -> start.plusMinutes(customMinutesIntoLastThird.toLong())
            TahajjudReminderMode.DISABLED -> return null
        }
        // The latest honest moment is a minute before Fajr; anything later has missed the window.
        val latest = window.nextFajr.minusMinutes(1)
        val notTooLate = if (raw.isAfter(latest)) latest else raw
        // And never earlier than the last third begins — a negative custom offset is pulled up.
        return if (notTooLate.isBefore(start)) start else notTooLate
    }
}
