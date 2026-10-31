package com.rayluxeo.aida.ui.alarms

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Alarm
import com.rayluxeo.aida.domain.model.TimerRecord
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.repository.AlarmRepository
import com.rayluxeo.aida.domain.repository.TimerRepository
import com.rayluxeo.aida.domain.scheduler.AlarmClockScheduler
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import com.rayluxeo.aida.domain.scheduler.TimerScheduler
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written collaborators for the Alarms and Timers view models.
 *
 * Hilt's local-test transform does not run under AGP 9, so nothing here is injected: each test
 * assembles the view model itself. They behave like the real repositories in the ways the screens
 * depend on — the flows are live, the ordering matches, and failures are values.
 */
class FakeAlarmRepository : AlarmRepository {

    private val rows = MutableStateFlow<List<Alarm>>(emptyList())
    private var nextId = 1L

    /** Set to make the next mutating call fail, so the refusal paths can be exercised. */
    var failWith: MiraErrorCode? = null

    override fun observeAll(): Flow<List<Alarm>> = rows.map { list -> list.sortedBy { it.at } }

    override suspend fun byId(id: Long): AppResult<Alarm> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No alarm with id $id")

    override suspend fun enabledOrSnoozed(): AppResult<List<Alarm>> =
        AppResult.Success(rows.value.filter { it.isEnabled || it.snoozedUntil != null })

    override suspend fun create(alarm: Alarm): AppResult<Alarm> {
        failWith?.let { return failure(it, "asked to fail") }
        val saved = alarm.copy(id = nextId++)
        rows.value = rows.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun update(alarm: Alarm): AppResult<Alarm> {
        failWith?.let { return failure(it, "asked to fail") }
        if (rows.value.none { it.id == alarm.id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No alarm with id ${alarm.id}")
        }
        rows.value = rows.value.map { if (it.id == alarm.id) alarm else it }
        return AppResult.Success(alarm)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        failWith?.let { return failure(it, "asked to fail") }
        rows.value = rows.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    fun seed(vararg alarms: Alarm) {
        rows.value = alarms.map { alarm ->
            if (alarm.id == 0L) alarm.copy(id = nextId++) else alarm.also { nextId = maxOf(nextId, it.id + 1) }
        }
    }

    fun snapshot(): List<Alarm> = rows.value
}

/** Records what reached the OS, so a switch can be proved to disarm as well as to store. */
class FakeAlarmClockScheduler : AlarmClockScheduler {

    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()

    /** Set to the refusal Android gives when exact alarms have been withdrawn. */
    var refuseWith: MiraErrorCode? = null

    override suspend fun schedule(alarm: Alarm): AppResult<Unit> {
        refuseWith?.let { return failure(it, "asked to refuse") }
        scheduled += alarm.id
        return AppResult.Success(Unit)
    }

    override suspend fun cancel(alarmId: Long): AppResult<Unit> {
        cancelled += alarmId
        return AppResult.Success(Unit)
    }

    override suspend fun rescheduleAll(): AppResult<Unit> = AppResult.Success(Unit)
}

/**
 * Countdowns.
 *
 * The end instant is resolved here, against the test's own clock, exactly as the real repository
 * resolves it — which is what makes a countdown read from the clock rather than from a counter
 * something a test can actually prove.
 */
class FakeTimerRepository(private val clock: MiraClock) : TimerRepository {

    private val rows = MutableStateFlow<List<TimerRecord>>(emptyList())
    private var nextId = 1L

    var failWith: MiraErrorCode? = null

    override fun observeActive(): Flow<List<TimerRecord>> = rows.map { list ->
        list.filter { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }
            .sortedBy { it.endsAt }
    }

    override suspend fun byId(id: Long): AppResult<TimerRecord> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No timer with id $id")

    override suspend fun start(label: String, duration: Duration): AppResult<TimerRecord> {
        failWith?.let { return failure(it, "asked to fail") }
        val now = clock.now()
        val started = TimerRecord(
            id = nextId++,
            label = label,
            duration = duration,
            startedAt = now,
            endsAt = now.plus(duration),
            state = TimerState.RUNNING,
            createdAt = now,
            updatedAt = now,
        )
        rows.value = rows.value + started
        return AppResult.Success(started)
    }

    override suspend fun pause(id: Long): AppResult<TimerRecord> = change(id) { timer ->
        if (timer.state != TimerState.RUNNING) {
            failure(MiraErrorCode.VALIDATION, "Timer $id is not running")
        } else {
            AppResult.Success(
                timer.copy(
                    state = TimerState.PAUSED,
                    remainingWhenPaused = timer.remainingAt(clock.now()),
                ),
            )
        }
    }

    override suspend fun resume(id: Long): AppResult<TimerRecord> = change(id) { timer ->
        if (timer.state != TimerState.PAUSED) {
            failure(MiraErrorCode.VALIDATION, "Timer $id is not paused")
        } else {
            val left = timer.remainingWhenPaused ?: Duration.ZERO
            AppResult.Success(
                timer.copy(
                    state = TimerState.RUNNING,
                    endsAt = clock.now().plus(left),
                    remainingWhenPaused = null,
                ),
            )
        }
    }

    override suspend fun finish(id: Long): AppResult<TimerRecord> = change(id) { timer ->
        AppResult.Success(timer.copy(state = TimerState.FINISHED, remainingWhenPaused = null))
    }

    override suspend fun cancel(id: Long): AppResult<TimerRecord> = change(id) { timer ->
        AppResult.Success(timer.copy(state = TimerState.CANCELLED, remainingWhenPaused = null))
    }

    fun snapshot(): List<TimerRecord> = rows.value

    private fun change(
        id: Long,
        transform: (TimerRecord) -> AppResult<TimerRecord>,
    ): AppResult<TimerRecord> {
        failWith?.let { return failure(it, "asked to fail") }
        val current = rows.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No timer with id $id")
        val changed = transform(current)
        if (changed is AppResult.Success) {
            rows.value = rows.value.map { if (it.id == id) changed.value else it }
        }
        return changed
    }
    override suspend fun active(): AppResult<List<TimerRecord>> =
        AppResult.Success(
            rows.value.filter { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED },
        )

}

class FakeTimerScheduler : TimerScheduler {

    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()

    override suspend fun schedule(timer: TimerRecord): AppResult<Unit> {
        scheduled += timer.id
        return AppResult.Success(Unit)
    }

    override suspend fun cancel(timerId: Long): AppResult<Unit> {
        cancelled += timerId
        return AppResult.Success(Unit)
    }

    override suspend fun rescheduleAll(): AppResult<Unit> = AppResult.Success(Unit)
}

/**
 * Whether the OS will let Mira name a minute.
 *
 * [set] pushes a new answer down [changes], which is how a test reproduces the thing that actually
 * happens on the phone: the permission is taken away while Abu is looking at the screen.
 */
class FakeExactAlarmAccess(initial: Boolean = true) : ExactAlarmAccess {

    private val state = MutableStateFlow(initial)

    override fun canScheduleExact(): Boolean = state.value

    override val changes: Flow<Boolean> = state

    fun set(allowed: Boolean) {
        state.value = allowed
    }
}
