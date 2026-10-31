package com.rayluxeo.aida.data.calendar

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.CalendarLinkDao
import com.rayluxeo.aida.data.db.dao.MeetingDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Foreign keys are on here (see [openTestDatabase]), which is what makes the `SET NULL`
 * assertions mean anything: the whole point of this table is that unlinking one side empties the
 * row instead of deleting the record of the pairing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CalendarLinkDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: CalendarLinkDao
    private lateinit var meetings: MeetingDao

    @Before
    fun openDatabase() {
        database = openTestDatabase()
        dao = database.calendarLinkDao()
        meetings = database.meetingDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `linking an event records the pairing and the cached event details`() = runTest {
        val meetingId = meetings.insert(meetingRow())
        val startsAt = at(2026, 3, 6, 11)

        val id = dao.linkIfAbsent(
            linkRow(systemEventId = 42L, title = "Kickoff", startsAt = startsAt, meetingId = meetingId),
        )

        assertThat(id).isNotNull()
        val stored = dao.findBySystemEventId(42L)
        assertThat(stored?.id).isEqualTo(id)
        assertThat(stored?.title).isEqualTo("Kickoff")
        assertThat(stored?.startsAtEpochMillis).isEqualTo(startsAt.toEpochMilli())
        assertThat(stored?.meetingId).isEqualTo(meetingId)
    }

    @Test
    fun `the same system event cannot be linked twice`() = runTest {
        val first = meetings.insert(meetingRow(title = "First"))
        val second = meetings.insert(meetingRow(title = "Second"))
        dao.linkIfAbsent(linkRow(systemEventId = 42L, meetingId = first))

        val again = dao.linkIfAbsent(linkRow(systemEventId = 42L, meetingId = second))

        assertThat(again).isNull()
        assertThat(dao.findBySystemEventId(42L)?.meetingId).isEqualTo(first)
        assertThat(dao.linksForMeeting(second)).isEmpty()
    }

    @Test
    fun `deleting every linked record empties the link rather than removing it`() = runTest {
        val meetingId = meetings.insert(meetingRow())
        val taskId = database.insertTask()
        val projectId = database.insertProject()
        val id = dao.linkIfAbsent(
            linkRow(
                systemEventId = 42L,
                taskId = taskId,
                projectId = projectId,
                meetingId = meetingId,
            ),
        )!!

        meetings.deleteById(meetingId)
        database.taskDao().deleteById(taskId)
        database.deleteProject(projectId)

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored?.systemEventId).isEqualTo(42L)
        assertThat(stored?.taskId).isNull()
        assertThat(stored?.projectId).isNull()
        assertThat(stored?.meetingId).isNull()
    }

    @Test
    fun `binning a meeting leaves its link alone`() = runTest {
        val meetingId = meetings.insert(meetingRow())
        dao.linkIfAbsent(linkRow(systemEventId = 42L, meetingId = meetingId))

        meetings.softDelete(meetingId, NOW.toEpochMilli())

        assertThat(dao.linksForMeeting(meetingId)).hasSize(1)
        assertThat(dao.observeStale().first()).isEmpty()
    }

    @Test
    fun `a link with nothing left on Mira's side is the one a cleanup finds`() = runTest {
        val meetingId = meetings.insert(meetingRow())
        dao.linkIfAbsent(linkRow(systemEventId = 1L, meetingId = meetingId))
        val orphanId = dao.linkIfAbsent(linkRow(systemEventId = 2L))!!

        val stale = dao.observeStale().first()

        assertThat(stale.map { it.id }).containsExactly(orphanId)
        assertThat(dao.deleteStale()).isEqualTo(1)
        assertThat(dao.findById(orphanId)).isNull()
        assertThat(dao.linksForMeeting(meetingId)).hasSize(1)
    }

    @Test
    fun `upcoming links are ordered by when the event starts`() = runTest {
        dao.linkIfAbsent(linkRow(systemEventId = 1L, title = "Later", startsAt = at(2026, 3, 8, 9)))
        dao.linkIfAbsent(linkRow(systemEventId = 2L, title = "Sooner", startsAt = at(2026, 3, 6, 9)))
        dao.linkIfAbsent(linkRow(systemEventId = 3L, title = "Gone", startsAt = at(2026, 3, 1, 9)))

        val upcoming = dao.observeUpcoming(NOW.toEpochMilli()).first()

        assertThat(upcoming.map { it.title }).containsExactly("Sooner", "Later").inOrder()
    }

    @Test
    fun `a link starting at the first instant of the next window is not in this one`() = runTest {
        val dayStart = at(2026, 3, 6, 0)
        val dayEnd = at(2026, 3, 7, 0)
        dao.linkIfAbsent(linkRow(systemEventId = 1L, title = "Today", startsAt = dayStart))
        dao.linkIfAbsent(linkRow(systemEventId = 2L, title = "Tomorrow", startsAt = dayEnd))

        val inDay = dao.observeBetween(dayStart.toEpochMilli(), dayEnd.toEpochMilli()).first()

        assertThat(inDay.map { it.title }).containsExactly("Today")
    }

    @Test
    fun `links are found by the task and the project they point at`() = runTest {
        val taskId = database.insertTask()
        val projectId = database.insertProject()
        dao.linkIfAbsent(linkRow(systemEventId = 1L, title = "Task event", taskId = taskId))
        dao.linkIfAbsent(linkRow(systemEventId = 2L, title = "Project event", projectId = projectId))

        assertThat(dao.linksForTask(taskId).map { it.title }).containsExactly("Task event")
        assertThat(dao.linksForProject(projectId).map { it.title }).containsExactly("Project event")
    }

    @Test
    fun `an updated link keeps its row and takes the new event details`() = runTest {
        val meetingId = meetings.insert(meetingRow())
        val id = dao.linkIfAbsent(
            linkRow(systemEventId = 42L, title = "Kickoff", meetingId = meetingId),
        )!!
        val moved = at(2026, 3, 9, 16)

        val changed = dao.update(
            dao.findById(id)!!.copy(title = "Kickoff (moved)", startsAtEpochMillis = moved.toEpochMilli()),
        )

        assertThat(changed).isEqualTo(1)
        val stored = dao.findBySystemEventId(42L)
        assertThat(stored?.id).isEqualTo(id)
        assertThat(stored?.title).isEqualTo("Kickoff (moved)")
        assertThat(stored?.startsAtEpochMillis).isEqualTo(moved.toEpochMilli())
    }
}
