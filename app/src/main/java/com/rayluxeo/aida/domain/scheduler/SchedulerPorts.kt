package com.rayluxeo.aida.domain.scheduler

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Alarm
import com.rayluxeo.aida.domain.model.ReminderPlace
import com.rayluxeo.aida.domain.model.TimerRecord
import kotlinx.coroutines.flow.Flow

/**
 * Whether the OS will currently let Mira name a minute.
 *
 * Exact alarms are denied by default from Android 13 and can be taken away at any time. When they
 * are withdrawn the platform **stops the app and cancels every future exact alarm it holds** — so
 * this is not a static fact to read once at startup. [changes] is what lets Mira re-arm everything
 * the moment Abu grants it back, and degrade honestly when he does not.
 */
interface ExactAlarmAccess {

    fun canScheduleExact(): Boolean

    /** Emits whenever the platform's answer changes. Never completes. */
    val changes: Flow<Boolean>
}

/**
 * The alarm clock.
 *
 * Separate from [ReminderScheduler] because an alarm is not a reminder: it uses
 * `AlarmManager.setAlarmClock`, which is the one scheduling API the OS treats as
 * user-visible and does not defer — and which shows in the status bar, as an alarm should.
 */
interface AlarmClockScheduler {

    suspend fun schedule(alarm: Alarm): AppResult<Unit>

    suspend fun cancel(alarmId: Long): AppResult<Unit>

    /** Re-arms every enabled alarm. Called after a reboot and after exact alarms come back. */
    suspend fun rescheduleAll(): AppResult<Unit>
}

/**
 * A countdown's ending.
 *
 * Exact, because a 25-minute timer that fires at 27 minutes is wrong in a way Abu will notice
 * immediately. Several may be armed at once; each is keyed by its own timer id.
 */
interface TimerScheduler {

    suspend fun schedule(timer: TimerRecord): AppResult<Unit>

    suspend fun cancel(timerId: Long): AppResult<Unit>

    suspend fun rescheduleAll(): AppResult<Unit>
}

/**
 * Why Mira cannot watch for a place right now.
 *
 * Every one of these is an ordinary state rather than a fault, and each leads to different words:
 * a permission Abu has not granted is a thing he can fix, a device with no location provider is
 * not, and a full register is Mira's problem rather than his.
 */
enum class PlaceWatchRefusal {
    PERMISSION_MISSING,
    /**
     * The platform was asked and simply did not produce a fix in time.
     *
     * Its own member because every other value here names something Abu can go and change, and
     * saying "this phone has no location provider Mira can use" when the truth is "we were
     * indoors and it took too long" is a statement he would act on and could not fix. This one
     * asks him to try again.
     */
    NO_FIX_YET,
    BACKGROUND_PERMISSION_MISSING,
    LOCATION_DISABLED,
    NO_PROVIDER,
    LIMIT_REACHED,
    BATTERY_RESTRICTED,
}

/** What the platform said when asked to watch a place. */
sealed interface PlaceWatchResult {
    data object Watching : PlaceWatchResult
    data class Refused(val reason: PlaceWatchRefusal) : PlaceWatchResult
}

/**
 * Watches for Abu arriving somewhere.
 *
 * Built on the platform's own proximity alerts rather than Play Services, so Mira adds no Google
 * dependency to an app whose claim is that his records stay on his phone. That choice costs
 * something — the platform API is older and its battery behaviour varies by OEM — which is exactly
 * why every refusal above is a state Abu is told about rather than a silent failure.
 *
 * **Location is never required.** Every other kind of reminder works with location switched off,
 * the permission refused, and the provider absent.
 */
interface PlaceReminderScheduler {

    /** How many places the OS will hold at once, so Mira can say when she has run out. */
    val capacity: Int

    suspend fun watch(place: ReminderPlace): AppResult<PlaceWatchResult>

    suspend fun stopWatching(placeId: Long): AppResult<Unit>

    /** Re-registers every place after a reboot, which drops them all. */
    suspend fun rewatchAll(): AppResult<Unit>
}
