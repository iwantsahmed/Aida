package com.rayluxeo.aida.domain.brief

import com.rayluxeo.aida.core.common.AppResult
import java.time.LocalDate
import java.time.LocalTime

/**
 * Builds Mira's picture of today from Abu's own records.
 *
 * A port rather than a concrete class because two very different things ask for a brief — the Home
 * screen, continuously, and a scheduled notification, once — and both must get the same answer.
 *
 * It runs entirely on the device. Nothing here consults Gemini, and nothing here needs the radio.
 */
interface DailyBriefEngine {

    /** The full picture, for Home and for "Read my brief". */
    suspend fun today(): AppResult<DailyBriefReport>

    /**
     * Just the things that want looking at.
     *
     * Separate from [today] because the Needs Attention card is the one part of Home that must
     * stay cheap: it is rebuilt on every repository emission, and a full brief per keystroke
     * elsewhere in the app would be wasteful.
     */
    suspend fun attention(): AppResult<List<AttentionItem>>
}

/** Builds the account of a day that has happened, rather than one that is about to. */
interface EveningReviewEngine {
    suspend fun review(date: LocalDate): AppResult<EveningReviewReport>
}

/**
 * When Mira speaks up unasked.
 *
 * Both are off by default. An assistant that starts making noise on a schedule Abu never chose is
 * a notification he will turn off for good, so the first one he gets is one he asked for.
 */
data class BriefSchedule(
    val morningEnabled: Boolean = false,
    val morningAt: LocalTime = DEFAULT_MORNING,
    val eveningEnabled: Boolean = false,
    val eveningAt: LocalTime = DEFAULT_EVENING,
) {
    companion object {
        val DEFAULT_MORNING: LocalTime = LocalTime.of(8, 0)
        val DEFAULT_EVENING: LocalTime = LocalTime.of(21, 0)
        val DEFAULT = BriefSchedule()
    }
}

/** Which of the two a scheduled wake-up is for. */
enum class BriefKind { MORNING, EVENING }

interface BriefScheduleRepository {
    val schedule: kotlinx.coroutines.flow.Flow<BriefSchedule>
    suspend fun current(): BriefSchedule
    suspend fun setMorningEnabled(enabled: Boolean)
    suspend fun setMorningAt(time: LocalTime)
    suspend fun setEveningEnabled(enabled: Boolean)
    suspend fun setEveningAt(time: LocalTime)
}

/**
 * Arms and disarms the two daily wake-ups.
 *
 * Deliberately **inexact**. A brief is not a reminder: nothing turns on it landing at 08:00:00
 * rather than 08:04, and asking for an exact alarm would make the feature depend on a permission
 * Android can revoke — so a morning brief keeps working on a phone where exact alarms are denied,
 * which is the opposite of how `ReminderScheduler` must behave.
 *
 * It is also why this is its own port rather than another method on `ReminderScheduler`: the two
 * make opposite trades, and sharing an interface would hide that.
 */
interface BriefScheduler {

    /** Arms whatever [schedule] says is on, and cancels whatever it says is off. Idempotent. */
    suspend fun apply(schedule: BriefSchedule): AppResult<Unit>

    suspend fun cancel(kind: BriefKind): AppResult<Unit>

    /**
     * Re-arms after a reboot, which drops every alarm the OS was holding. Without this a brief Abu
     * switched on months ago silently stops the first time he restarts his phone.
     */
    suspend fun rescheduleAll(): AppResult<Unit>
}
