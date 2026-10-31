package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.TimerRecord
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.repository.TimerRepository
import com.rayluxeo.aida.domain.scheduler.TimerScheduler
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Several countdowns at once, and what happens to them when Abu is not looking.
 *
 * What is worth asserting here is what he could not notice going wrong: a timer still counting
 * down in the list with no alarm behind it, a second timer quietly taking the first one's alarm,
 * a paused countdown that resumes into nothing, and a reboot announcing a timer that ended hours
 * ago as though it had just run out.
 *
 * Nothing is injected — Hilt's local-test transform does not run under AGP 9 (google/dagger#5261)
 * — and the clock is moved by hand, because every assertion below is about a particular moment.
 */
class TimerUseCaseTest {

    private val clock = MovingClock(at(14, 0))
    private val timers = FakeTimerRepository(clock)
    private val scheduler = FakeTimerScheduler(timers, clock)

    private val startTimer = StartTimerUseCase(timers, scheduler)
    private val pauseTimer = PauseTimerUseCase(timers, scheduler)
    private val resumeTimer = ResumeTimerUseCase(timers, scheduler)
    private val cancelTimer = CancelTimerUseCase(timers, scheduler)
    private val finishTimer = FinishTimerUseCase(timers, scheduler)
    private val restoreTimers = RestoreTimersUseCase(timers, scheduler, finishTimer, clock)

    @Test
    fun `starting a timer arms an alarm for the moment it ends`() = runTest {
        val timer = startTimer("Steep the tea", Duration.ofMinutes(25)).value()

        assertThat(timer.state).isEqualTo(TimerState.RUNNING)
        assertThat(scheduler.armed[timer.id]).isEqualTo(at(14, 25))
    }

    @Test
    fun `a timer the OS will not arm is not left counting down`() = runTest {
        scheduler.refuse = true

        val refused = startTimer("Steep the tea", Duration.ofMinutes(25))

        assertThat(refused.failureCode()).isEqualTo(MiraErrorCode.PERMISSION_REQUIRED)
        assertThat(timers.active().value()).isEmpty()
    }

    @Test
    fun `two timers hold two alarms, and ending one leaves the other armed`() = runTest {
        val tea = startTimer("Steep the tea", Duration.ofMinutes(25)).value()
        val eggs = startTimer("Boil the eggs", Duration.ofMinutes(8)).value()

        assertThat(scheduler.armed).containsExactly(tea.id, at(14, 25), eggs.id, at(14, 8))

        cancelTimer(eggs.id).value()

        assertThat(scheduler.armed.keys).containsExactly(tea.id)
    }

    @Test
    fun `pausing takes the alarm down and resuming puts up a new one`() = runTest {
        val timer = startTimer("Steep the tea", Duration.ofMinutes(25)).value()

        clock.moveTo(at(14, 10))
        val paused = pauseTimer(timer.id).value()

        assertThat(paused.remainingWhenPaused).isEqualTo(Duration.ofMinutes(15))
        assertThat(scheduler.armed).isEmpty()

        clock.moveTo(at(15, 0))
        val resumed = resumeTimer(timer.id).value()

        assertThat(resumed.endsAt).isEqualTo(at(15, 15))
        assertThat(scheduler.armed[timer.id]).isEqualTo(at(15, 15))
    }

    @Test
    fun `a resume the OS refuses leaves the timer paused with what was left intact`() = runTest {
        val timer = startTimer("Steep the tea", Duration.ofMinutes(25)).value()
        clock.moveTo(at(14, 10))
        pauseTimer(timer.id).value()

        clock.moveTo(at(15, 0))
        scheduler.refuse = true
        val refused = resumeTimer(timer.id)

        assertThat(refused.failureCode()).isEqualTo(MiraErrorCode.PERMISSION_REQUIRED)
        val reread = timers.byId(timer.id).value()
        assertThat(reread.state).isEqualTo(TimerState.PAUSED)
        assertThat(reread.remainingWhenPaused).isEqualTo(Duration.ofMinutes(15))
        assertThat(scheduler.armed).isEmpty()
    }

    @Test
    fun `finishing a timer withdraws its alarm`() = runTest {
        val timer = startTimer("Steep the tea", Duration.ofMinutes(25)).value()

        clock.moveTo(at(14, 25))
        val finished = finishTimer(timer.id).value()

        assertThat(finished.state).isEqualTo(TimerState.FINISHED)
        assertThat(scheduler.armed).isEmpty()
        assertThat(timers.active().value()).isEmpty()
    }

    @Test
    fun `a reboot finishes what ran out, re-arms what is left, and leaves a paused timer alone`() = runTest {
        val overdue = startTimer("Steep the tea", Duration.ofMinutes(25)).value()
        val running = startTimer("Slow roast", Duration.ofHours(3)).value()
        val paused = startTimer("Boil the eggs", Duration.ofMinutes(8)).value()
        clock.moveTo(at(14, 2))
        pauseTimer(paused.id).value()

        // The phone was off from here, so nothing the OS was holding survives.
        scheduler.armed.clear()
        clock.moveTo(at(15, 0))

        val ranOut = restoreTimers().value()

        assertThat(ranOut.map { it.id }).containsExactly(overdue.id)
        assertThat(timers.byId(overdue.id).value().state).isEqualTo(TimerState.FINISHED)
        assertThat(scheduler.armed).containsExactly(running.id, at(17, 0))
        val stillPaused = timers.byId(paused.id).value()
        assertThat(stillPaused.state).isEqualTo(TimerState.PAUSED)
        assertThat(stillPaused.remainingWhenPaused).isEqualTo(Duration.ofMinutes(6))
    }

    @Test
    fun `timers that ran out while the phone was off come back in the order they ended`() = runTest {
        val first = startTimer("Steep the tea", Duration.ofMinutes(10)).value()
        val second = startTimer("Boil the eggs", Duration.ofMinutes(25)).value()
        scheduler.armed.clear()
        clock.moveTo(at(16, 0))

        val ranOut = restoreTimers().value()

        assertThat(ranOut.map { it.id }).containsExactly(first.id, second.id).inOrder()
        assertThat(scheduler.armed).isEmpty()
    }

    private fun <T> AppResult<T>.value(): T {
        assertThat(this).isInstanceOf(AppResult.Success::class.java)
        return (this as AppResult.Success).value
    }

    private fun AppResult<*>.failureCode(): MiraErrorCode {
        assertThat(this).isInstanceOf(AppResult.Failure::class.java)
        return (this as AppResult.Failure).error.code
    }

    private companion object {
        val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")

        /** A Dhaka wall-clock reading on one afternoon, which is how these tests think. */
        fun at(hour: Int, minute: Int): Instant =
            LocalDateTime.of(2026, 3, 5, hour, minute).atZone(DHAKA).toInstant()
    }
}

/** A clock the test moves by hand; every assertion here is about a particular moment. */
private class MovingClock(private var instant: Instant) : MiraClock {

    override fun now(): Instant = instant

    override fun zone(): ZoneId = ZoneId.of("Asia/Dhaka")

    fun moveTo(moment: Instant) {
        instant = moment
    }
}

/**
 * Mirrors the real countdown store, including its refusals: what is left of a paused timer is
 * measured when it is paused, and a timer that is already over cannot be moved again.
 */
private class FakeTimerRepository(private val clock: MiraClock) : TimerRepository {

    private val rows = mutableListOf<TimerRecord>()
    private var nextId = 1L

    override fun observeActive(): Flow<List<TimerRecord>> = flowOf(activeRows())

    override suspend fun active(): AppResult<List<TimerRecord>> = AppResult.Success(activeRows())

    override suspend fun byId(id: Long): AppResult<TimerRecord> =
        rows.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No timer with id $id")

    override suspend fun start(label: String, duration: Duration): AppResult<TimerRecord> {
        if (duration.isZero || duration.isNegative) {
            return failure(MiraErrorCode.VALIDATION, "Timer duration was $duration")
        }
        val startedAt = clock.now()
        val saved = TimerRecord(
            id = nextId++,
            label = label.trim(),
            duration = duration,
            startedAt = startedAt,
            endsAt = startedAt.plus(duration),
            state = TimerState.RUNNING,
            createdAt = startedAt,
            updatedAt = startedAt,
        )
        rows += saved
        return AppResult.Success(saved)
    }

    override suspend fun pause(id: Long): AppResult<TimerRecord> =
        move(id, TimerState.PAUSED, from = TimerState.RUNNING) {
            it.copy(remainingWhenPaused = it.remainingAt(clock.now()))
        }

    override suspend fun resume(id: Long): AppResult<TimerRecord> =
        move(id, TimerState.RUNNING, from = TimerState.PAUSED) {
            val left = it.remainingWhenPaused ?: Duration.ZERO
            it.copy(endsAt = clock.now().plus(left), remainingWhenPaused = null)
        }

    override suspend fun finish(id: Long): AppResult<TimerRecord> = settle(id, TimerState.FINISHED)

    override suspend fun cancel(id: Long): AppResult<TimerRecord> = settle(id, TimerState.CANCELLED)

    private fun activeRows(): List<TimerRecord> = rows
        .filter { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }
        .sortedBy { it.endsAt }

    private fun settle(id: Long, to: TimerState): AppResult<TimerRecord> {
        val current = rows.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No timer with id $id")
        if (current.state == TimerState.FINISHED || current.state == TimerState.CANCELLED) {
            return failure(MiraErrorCode.VALIDATION, "Timer $id is already over")
        }
        return write(current.copy(state = to, remainingWhenPaused = null))
    }

    private fun move(
        id: Long,
        to: TimerState,
        from: TimerState,
        change: (TimerRecord) -> TimerRecord,
    ): AppResult<TimerRecord> {
        val current = rows.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No timer with id $id")
        if (current.state != from) {
            return failure(MiraErrorCode.VALIDATION, "Timer $id is ${current.state}, not $from")
        }
        return write(change(current).copy(state = to, updatedAt = clock.now()))
    }

    private fun write(saved: TimerRecord): AppResult<TimerRecord> {
        rows[rows.indexOfFirst { it.id == saved.id }] = saved
        return AppResult.Success(saved)
    }
}

/**
 * Stands in for the OS's alarm register: one entry per timer, so a second timer overwriting the
 * first one's entry would show up here as the lost alarm it would be on the phone.
 */
private class FakeTimerScheduler(
    private val timers: FakeTimerRepository,
    private val clock: MiraClock,
) : TimerScheduler {

    val armed = mutableMapOf<Long, Instant>()
    var refuse = false

    override suspend fun schedule(timer: TimerRecord): AppResult<Unit> {
        if (refuse) return failure(MiraErrorCode.PERMISSION_REQUIRED, "Timer alarm refused")
        armed[timer.id] = timer.endsAt
        return AppResult.Success(Unit)
    }

    override suspend fun cancel(timerId: Long): AppResult<Unit> {
        armed.remove(timerId)
        return AppResult.Success(Unit)
    }

    override suspend fun rescheduleAll(): AppResult<Unit> {
        val now = clock.now()
        timers.active().let { active ->
            if (active is AppResult.Success) {
                active.value
                    .filter { it.state == TimerState.RUNNING && it.endsAt.isAfter(now) }
                    .forEach { schedule(it) }
            }
        }
        return AppResult.Success(Unit)
    }
}
