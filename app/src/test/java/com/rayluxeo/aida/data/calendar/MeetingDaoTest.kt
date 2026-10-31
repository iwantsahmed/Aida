package com.rayluxeo.aida.data.calendar

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
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
 * Hilt is deliberately absent: its local-test bytecode transform does not run under AGP 9, so the
 * database is built by hand and the application is pinned to the plain [Application].
 *
 * Foreign keys are turned on in [openTestDatabase], so the cascade and `SET NULL` assertions here
 * are testing SQLite rather than testing nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MeetingDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: MeetingDao

    @Before
    fun openDatabase() {
        database = openTestDatabase()
        dao = database.meetingDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an inserted meeting comes back with the times it went in with`() = runTest {
        val startsAt = at(2026, 3, 6, 11, 30)
        val endsAt = at(2026, 3, 6, 12, 15)

        val id = dao.insert(meetingRow(title = "Kickoff", startsAt = startsAt, endsAt = endsAt))

        val stored = dao.findLiveById(id)
        assertThat(id).isGreaterThan(0L)
        assertThat(stored?.title).isEqualTo("Kickoff")
        assertThat(stored?.startsAtEpochMillis).isEqualTo(startsAt.toEpochMilli())
        assertThat(stored?.endsAtEpochMillis).isEqualTo(endsAt.toEpochMilli())
    }

    @Test
    fun `upcoming excludes a meeting that has already started`() = runTest {
        dao.insert(meetingRow(title = "Tomorrow", startsAt = at(2026, 3, 6, 11)))
        dao.insert(meetingRow(title = "Yesterday", startsAt = at(2026, 3, 4, 11)))

        val upcoming = dao.observeUpcoming(NOW.toEpochMilli()).first()

        assertThat(upcoming.map { it.title }).containsExactly("Tomorrow")
    }

    @Test
    fun `a binned meeting disappears from every read`() = runTest {
        val id = dao.insert(meetingRow(title = "Cancelled review"))
        val clientId = database.insertClient()
        val projectId = database.insertProject(clientId)
        val linked = dao.insert(
            meetingRow(title = "Kept", clientId = clientId, projectId = projectId),
        )
        dao.insertNote(noteRow(id, body = "Nothing came of it"))

        assertThat(dao.softDelete(id, NOW.toEpochMilli())).isEqualTo(1)

        assertThat(dao.observeUpcoming(NOW.toEpochMilli()).first().map { it.id }).containsExactly(linked)
        assertThat(dao.observeBetween(NOW.toEpochMilli(), at(2026, 3, 7, 0).toEpochMilli()).first())
            .hasSize(1)
        assertThat(dao.findLiveById(id)).isNull()
        assertThat(dao.notesFor(id)).isEmpty()
        assertThat(dao.observeNotes(id).first()).isEmpty()
        assertThat(dao.findById(id)).isNotNull()
    }

    @Test
    fun `binning the same meeting twice reports nothing left to bin`() = runTest {
        val id = dao.insert(meetingRow())

        assertThat(dao.softDelete(id, NOW.toEpochMilli())).isEqualTo(1)
        assertThat(dao.softDelete(id, NOW.toEpochMilli())).isEqualTo(0)
    }

    @Test
    fun `restore brings back the same row and its notes`() = runTest {
        val id = dao.insert(meetingRow())
        dao.insertNote(noteRow(id, body = "Agreed the scope"))
        dao.softDelete(id, NOW.toEpochMilli())

        assertThat(dao.restore(id, NOW.toEpochMilli())).isEqualTo(1)

        assertThat(dao.findLiveById(id)?.id).isEqualTo(id)
        assertThat(dao.notesFor(id).map { it.body }).containsExactly("Agreed the scope")
        assertThat(dao.restore(id, NOW.toEpochMilli())).isEqualTo(0)
    }

    @Test
    fun `deleting a meeting for real takes its notes with it`() = runTest {
        val id = dao.insert(meetingRow())
        dao.insertNote(noteRow(id, body = "First"))
        dao.insertNote(noteRow(id, body = "Second"))
        val other = dao.insert(meetingRow(title = "Untouched"))
        dao.insertNote(noteRow(other, body = "Kept"))

        assertThat(dao.deleteById(id)).isEqualTo(1)

        assertThat(dao.notesFor(id)).isEmpty()
        assertThat(dao.notesFor(other).map { it.body }).containsExactly("Kept")
    }

    @Test
    fun `deleting the client leaves the meeting without one`() = runTest {
        val clientId = database.insertClient()
        val projectId = database.insertProject(clientId)
        val id = dao.insert(meetingRow(clientId = clientId, projectId = projectId))

        database.deleteProject(projectId)
        database.deleteClient(clientId)

        val stored = dao.findLiveById(id)
        assertThat(stored).isNotNull()
        assertThat(stored?.clientId).isNull()
        assertThat(stored?.projectId).isNull()
    }

    @Test
    fun `deleting the task an action item became keeps the note`() = runTest {
        val meetingId = dao.insert(meetingRow())
        val taskId = database.insertTask()
        dao.insertNote(noteRow(meetingId, body = "Send the quote", isActionItem = true, taskId = taskId))

        database.taskDao().deleteById(taskId)

        val notes = dao.notesFor(meetingId)
        assertThat(notes.map { it.body }).containsExactly("Send the quote")
        assertThat(notes.single().taskId).isNull()
    }

    @Test
    fun `open action items ignore the ones that already became tasks`() = runTest {
        val meetingId = dao.insert(meetingRow())
        val taskId = database.insertTask()
        dao.insertNote(noteRow(meetingId, body = "Still open", isActionItem = true))
        dao.insertNote(noteRow(meetingId, body = "Already a task", isActionItem = true, taskId = taskId))
        dao.insertNote(noteRow(meetingId, body = "Just a note", isActionItem = false))

        assertThat(dao.observeOpenActionItems().first().map { it.body }).containsExactly("Still open")
    }

    @Test
    fun `open action items ignore a binned meeting`() = runTest {
        val meetingId = dao.insert(meetingRow())
        dao.insertNote(noteRow(meetingId, body = "Still open", isActionItem = true))

        dao.softDelete(meetingId, NOW.toEpochMilli())

        assertThat(dao.observeOpenActionItems().first()).isEmpty()
    }

    @Test
    fun `a note cannot be attached to a binned meeting`() = runTest {
        val meetingId = dao.insert(meetingRow())
        dao.softDelete(meetingId, NOW.toEpochMilli())

        assertThat(dao.insertNote(noteRow(meetingId, body = "Too late"))).isNull()
    }

    @Test
    fun `insertWithNotes writes the meeting and its notes together`() = runTest {
        val id = dao.insertWithNotes(meetingRow(title = "Kickoff")) { meetingId ->
            listOf(
                noteRow(meetingId, body = "Scope agreed", createdAt = at(2026, 3, 6, 11, 5)),
                noteRow(meetingId, body = "Send the quote", isActionItem = true, createdAt = at(2026, 3, 6, 11, 10)),
            )
        }

        assertThat(dao.findLiveById(id)?.title).isEqualTo("Kickoff")
        assertThat(dao.notesFor(id).map { it.body })
            .containsExactly("Scope agreed", "Send the quote")
            .inOrder()
    }

    @Test
    fun `insertWithNotes leaves nothing behind when a note is rejected`() = runTest {
        val before = dao.observeUpcoming(0L).first().size

        val rejected = runCatching {
            dao.insertWithNotes(meetingRow(title = "Never happened")) { meetingId ->
                listOf(
                    noteRow(meetingId, body = "Fine"),
                    noteRow(meetingId, body = "Points at no task", taskId = 9_999L),
                )
            }
        }

        assertThat(rejected.isFailure).isTrue()

        assertThat(dao.observeUpcoming(0L).first()).hasSize(before)
        assertThat(dao.observeOpenActionItems().first()).isEmpty()
    }

    @Test
    fun `replaceNotes swaps the whole set in one go`() = runTest {
        val id = dao.insert(meetingRow())
        dao.insertNote(noteRow(id, body = "Old one"))
        dao.insertNote(noteRow(id, body = "Old two"))

        val ids = dao.replaceNotes(
            id,
            listOf(
                noteRow(id, body = "New one", createdAt = at(2026, 3, 6, 12)),
                noteRow(id, body = "New two", createdAt = at(2026, 3, 6, 13)),
            ),
        )

        assertThat(ids).hasSize(2)
        assertThat(dao.notesFor(id).map { it.body }).containsExactly("New one", "New two").inOrder()
    }

    @Test
    fun `replaceNotes refuses a meeting that is not live`() = runTest {
        val id = dao.insert(meetingRow())
        dao.insertNote(noteRow(id, body = "Kept"))
        dao.softDelete(id, NOW.toEpochMilli())

        assertThat(dao.replaceNotes(id, listOf(noteRow(id, body = "New")))).isNull()

        dao.restore(id, NOW.toEpochMilli())
        assertThat(dao.notesFor(id).map { it.body }).containsExactly("Kept")
    }

    @Test
    fun `setNoteTask records the task and clears it again`() = runTest {
        val meetingId = dao.insert(meetingRow())
        val taskId = database.insertTask()
        val noteId = dao.insertNote(noteRow(meetingId, body = "Send the quote", isActionItem = true))!!

        assertThat(dao.setNoteTask(noteId, taskId, NOW.toEpochMilli())).isEqualTo(1)
        assertThat(dao.findNoteById(noteId)?.taskId).isEqualTo(taskId)

        assertThat(dao.setNoteTask(noteId, null, NOW.toEpochMilli())).isEqualTo(1)
        assertThat(dao.findNoteById(noteId)?.taskId).isNull()
    }

    @Test
    fun `meetings are listed for the client and the project they belong to`() = runTest {
        val clientId = database.insertClient()
        val projectId = database.insertProject(clientId)
        dao.insert(meetingRow(title = "With the client", clientId = clientId))
        dao.insert(meetingRow(title = "About the project", projectId = projectId))
        dao.insert(meetingRow(title = "Unattached"))

        assertThat(dao.observeForClient(clientId).first().map { it.title })
            .containsExactly("With the client")
        assertThat(dao.observeForProject(projectId).first().map { it.title })
            .containsExactly("About the project")
    }

    @Test
    fun `a meeting starting at the first instant of the next window is not in this one`() = runTest {
        val dayStart = at(2026, 3, 6, 0)
        val dayEnd = at(2026, 3, 7, 0)
        dao.insert(meetingRow(title = "First instant of the day", startsAt = dayStart))
        dao.insert(meetingRow(title = "First instant of tomorrow", startsAt = dayEnd))

        val inDay = dao.observeBetween(dayStart.toEpochMilli(), dayEnd.toEpochMilli()).first()

        assertThat(inDay.map { it.title }).containsExactly("First instant of the day")
    }
}
