package com.rayluxeo.aida.data.calendar

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.CalendarLinkRepositoryImpl
import com.rayluxeo.aida.domain.model.CalendarLink
import com.rayluxeo.aida.domain.repository.CalendarLinkRepository
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CalendarLinkRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    @Before
    fun openDatabase() {
        database = openTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `linking an event keeps its own copy of the title and times`() = runTest {
        val repository = repository()
        val meetingId = database.meetingDao().insert(meetingRow())
        val startsAt = at(2026, 3, 6, 11).plusMillis(321L)

        val linked = repository.link(
            link(systemEventId = 42L, title = "Kickoff", startsAt = startsAt, meetingId = meetingId),
        ).value()

        assertThat(linked.id).isGreaterThan(0L)
        val stored = repository.bySystemEventId(42L).value()
        assertThat(stored?.id).isEqualTo(linked.id)
        assertThat(stored?.title).isEqualTo("Kickoff")
        assertThat(stored?.startsAt).isEqualTo(startsAt)
        assertThat(stored?.meetingId).isEqualTo(meetingId)
    }

    @Test
    fun `linking the same system event twice is refused without replacing the first link`() = runTest {
        val repository = repository()
        val first = database.meetingDao().insert(meetingRow(title = "First"))
        val second = database.meetingDao().insert(meetingRow(title = "Second"))
        repository.link(link(systemEventId = 42L, meetingId = first))

        val again = repository.link(link(systemEventId = 42L, meetingId = second))

        assertThat(again.failureCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.bySystemEventId(42L).value()?.meetingId).isEqualTo(first)
        assertThat(repository.forMeeting(second).value()).isEmpty()
    }

    @Test
    fun `an event Mira has never linked answers null rather than failing`() = runTest {
        val repository = repository()

        val found = repository.bySystemEventId(999L)

        assertThat(found).isInstanceOf(AppResult.Success::class.java)
        assertThat((found as AppResult.Success).value).isNull()
    }

    @Test
    fun `a link survives the records it points at and is then stale`() = runTest {
        val repository = repository()
        val meetingId = database.meetingDao().insert(meetingRow())
        val taskId = database.insertTask()
        val linked = repository.link(
            link(systemEventId = 42L, taskId = taskId, meetingId = meetingId),
        ).value()

        assertThat(repository.observeStale().first()).isEmpty()

        database.meetingDao().deleteById(meetingId)
        database.taskDao().deleteById(taskId)

        val stale = repository.observeStale().first()
        assertThat(stale.map { it.id }).containsExactly(linked.id)
        assertThat(stale.single().isStale).isTrue()
        assertThat(stale.single().title).isEqualTo("Kickoff")
    }

    @Test
    fun `purging stale links leaves the ones that still point somewhere`() = runTest {
        val repository = repository()
        val meetingId = database.meetingDao().insert(meetingRow())
        val kept = repository.link(link(systemEventId = 1L, meetingId = meetingId)).value()
        repository.link(link(systemEventId = 2L)).value()
        repository.link(link(systemEventId = 3L)).value()

        assertThat(repository.purgeStale().value()).isEqualTo(2)

        assertThat(repository.forMeeting(meetingId).value().map { it.id }).containsExactly(kept.id)
        assertThat(repository.observeStale().first()).isEmpty()
    }

    @Test
    fun `updating a link refreshes the cached event details and stamps the clock`() = runTest {
        val repository = repository()
        val meetingId = database.meetingDao().insert(meetingRow())
        val linked = repository.link(
            link(systemEventId = 42L, title = "Kickoff", meetingId = meetingId),
        ).value()
        val moved = at(2026, 3, 9, 16)
        clock.moveTo(LocalDateTime.of(2026, 3, 5, 18, 0))

        val updated = repository.update(
            linked.copy(title = "Kickoff (moved)", startsAt = moved, updatedAt = Instant.EPOCH),
        ).value()

        assertThat(updated.updatedAt).isEqualTo(clock.now())
        val stored = repository.bySystemEventId(42L).value()
        assertThat(stored?.id).isEqualTo(linked.id)
        assertThat(stored?.title).isEqualTo("Kickoff (moved)")
        assertThat(stored?.startsAt).isEqualTo(moved)
        assertThat(stored?.updatedAt).isEqualTo(clock.now())
    }

    @Test
    fun `upcoming links are ordered by when the event starts`() = runTest {
        val repository = repository()
        repository.link(link(systemEventId = 1L, title = "Later", startsAt = at(2026, 3, 8, 9)))
        repository.link(link(systemEventId = 2L, title = "Sooner", startsAt = at(2026, 3, 6, 9)))
        repository.link(link(systemEventId = 3L, title = "Past", startsAt = at(2026, 3, 1, 9)))

        assertThat(repository.observeUpcoming().first().map { it.title })
            .containsExactly("Sooner", "Later")
            .inOrder()
        assertThat(
            repository.observeBetween(at(2026, 3, 6, 0), at(2026, 3, 7, 0)).first().map { it.title },
        ).containsExactly("Sooner")
    }

    @Test
    fun `unlinking removes the row and says so when there is nothing to remove`() = runTest {
        val repository = repository()
        val linked = repository.link(link(systemEventId = 42L)).value()

        assertThat(repository.unlink(linked.id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.bySystemEventId(42L).value()).isNull()
        assertThat(repository.unlink(linked.id).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.update(linked).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    private fun TestScope.repository(): CalendarLinkRepository = CalendarLinkRepositoryImpl(
        dao = database.calendarLinkDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
    )

    private fun link(
        systemEventId: Long,
        title: String = "Kickoff",
        startsAt: Instant = at(2026, 3, 6, 11),
        taskId: Long? = null,
        projectId: Long? = null,
        meetingId: Long? = null,
    ) = CalendarLink(
        systemEventId = systemEventId,
        systemCalendarId = 1L,
        title = title,
        startsAt = startsAt,
        taskId = taskId,
        projectId = projectId,
        meetingId = meetingId,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun <T> AppResult<T>.value(): T {
        assertThat(this).isInstanceOf(AppResult.Success::class.java)
        return (this as AppResult.Success).value
    }

    private fun AppResult<*>.failureCode(): MiraErrorCode {
        assertThat(this).isInstanceOf(AppResult.Failure::class.java)
        return (this as AppResult.Failure).error.code
    }
}
