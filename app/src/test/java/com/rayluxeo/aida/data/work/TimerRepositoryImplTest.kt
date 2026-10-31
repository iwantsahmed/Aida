package com.rayluxeo.aida.data.work

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.TimerRepositoryImpl
import com.rayluxeo.aida.domain.model.TimerState
import com.rayluxeo.aida.domain.repository.TimerRepository
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The clock is pinned and moved by hand, because every assertion here is about what a timer says
 * at a particular moment — a test that read the wall clock would be measuring the test runner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TimerRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(START)

    @Before
    fun openDatabase() {
        database = openTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `starting a timer stores when it ends rather than what is left`() = runTest {
        val repository = repository()

        val timer = repository.start("Steep the tea", Duration.ofMinutes(25)).successValue()

        assertThat(timer.state).isEqualTo(TimerState.RUNNING)
        assertThat(timer.duration).isEqualTo(Duration.ofMinutes(25))
        assertThat(timer.startedAt).isEqualTo(clock.now())
        assertThat(timer.endsAt).isEqualTo(clock.now().plus(Duration.ofMinutes(25)))
        assertThat(timer.remainingWhenPaused).isNull()
    }

    @Test
    fun `a running timer counts down from the clock, not from the row`() = runTest {
        val repository = repository()
        val timer = repository.start("Steep the tea", Duration.ofMinutes(25)).successValue()

        clock.moveTo(START.plusMinutes(10))
        val reread = repository.byId(timer.id).successValue()

        assertThat(reread.remainingAt(clock.now())).isEqualTo(Duration.ofMinutes(15))
        assertThat(reread.endsAt).isEqualTo(timer.endsAt)
    }

    @Test
    fun `a running timer whose moment has passed reads as zero, never as negative`() = runTest {
        val repository = repository()
        val timer = repository.start("Steep the tea", Duration.ofMinutes(25)).successValue()

        clock.moveTo(START.plusMinutes(40))

        assertThat(repository.byId(timer.id).successValue().remainingAt(clock.now()))
            .isEqualTo(Duration.ZERO)
    }

    @Test
    fun `what is left of a paused timer survives the database exactly`() = runTest {
        val repository = repository()
        val timer = repository.start("Steep the tea", Duration.ofMinutes(25)).successValue()

        clock.moveTo(START.plusMinutes(10))
        val paused = repository.pause(timer.id).successValue()

        assertThat(paused.state).isEqualTo(TimerState.PAUSED)
        assertThat(paused.remainingWhenPaused).isEqualTo(Duration.ofMinutes(15))

        clock.moveTo(START.plusMinutes(90))
        val reread = repository.byId(timer.id).successValue()

        assertThat(reread.remainingWhenPaused).isEqualTo(Duration.ofMinutes(15))
        assertThat(reread.remainingAt(clock.now())).isEqualTo(Duration.ofMinutes(15))
    }

    @Test
    fun `resuming moves the end forward by however long the pause lasted`() = runTest {
        val repository = repository()
        val timer = repository.start("Steep the tea", Duration.ofMinutes(25)).successValue()
        clock.moveTo(START.plusMinutes(10))
        repository.pause(timer.id).successValue()

        clock.moveTo(START.plusMinutes(40))
        val resumed = repository.resume(timer.id).successValue()

        assertThat(resumed.state).isEqualTo(TimerState.RUNNING)
        assertThat(resumed.remainingWhenPaused).isNull()
        assertThat(resumed.endsAt).isEqualTo(clock.now().plus(Duration.ofMinutes(15)))
        assertThat(resumed.remainingAt(clock.now())).isEqualTo(Duration.ofMinutes(15))
        assertThat(resumed.startedAt).isEqualTo(timer.startedAt)
    }

    @Test
    fun `pausing a timer that is not running is refused`() = runTest {
        val repository = repository()
        val timer = repository.start("Steep the tea", Duration.ofMinutes(25)).successValue()
        repository.pause(timer.id).successValue()

        assertThat(repository.pause(timer.id).failureCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.resume(404L).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a cancelled timer is over and stays over`() = runTest {
        val repository = repository()
        val timer = repository.start("Steep the tea", Duration.ofMinutes(25)).successValue()

        val cancelled = repository.cancel(timer.id).successValue()

        assertThat(cancelled.state).isEqualTo(TimerState.CANCELLED)
        assertThat(cancelled.remainingAt(clock.now())).isEqualTo(Duration.ZERO)
        assertThat(repository.finish(timer.id).failureCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `only running and paused timers are active`() = runTest {
        val repository = repository()
        val running = repository.start("Steep the tea", Duration.ofMinutes(25)).successValue()
        val paused = repository.start("Boil the eggs", Duration.ofMinutes(8)).successValue()
        val finished = repository.start("Warm the oven", Duration.ofMinutes(12)).successValue()
        repository.pause(paused.id).successValue()
        repository.finish(finished.id).successValue()

        assertThat(repository.observeActive().first().map { it.id })
            .containsExactly(paused.id, running.id)
            .inOrder()
    }

    @Test
    fun `a timer with no duration is refused`() = runTest {
        val repository = repository()

        assertThat(repository.start("Steep the tea", Duration.ZERO).failureCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.start("Steep the tea", Duration.ofMinutes(-5)).failureCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeActive().first()).isEmpty()
    }

    private fun repository(): TimerRepository = TimerRepositoryImpl(
        dao = database.timerDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private companion object {
        val START: LocalDateTime = LocalDateTime.of(2026, 3, 5, 14, 0)
    }
}
