package com.rayluxeo.aida.data.calendar

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.MeetingRepositoryImpl
import com.rayluxeo.aida.domain.model.Meeting
import com.rayluxeo.aida.domain.model.MeetingNote
import com.rayluxeo.aida.domain.repository.MeetingRepository
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
class MeetingRepositoryImplTest {

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
    fun `a meeting and the notes it arrived with are written together`() = runTest {
        val repository = repository()

        val created = repository.create(
            meeting(title = "Kickoff"),
            listOf(note(body = "Scope agreed"), note(body = "Send the quote", isActionItem = true)),
        ).value()

        assertThat(created.id).isGreaterThan(0L)
        assertThat(repository.notesFor(created.id).value().map { it.body })
            .containsExactly("Scope agreed", "Send the quote")
            .inOrder()
        assertThat(repository.observeNotes(created.id).first().map { it.meetingId })
            .containsExactly(created.id, created.id)
    }

    @Test
    fun `a rejected note leaves no meeting behind`() = runTest {
        val repository = repository()

        val result = repository.create(
            meeting(title = "Never happened"),
            listOf(note(body = "Points at no task", taskId = 9_999L)),
        )

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.STORAGE)
        assertThat(repository.observeUpcoming().first()).isEmpty()
    }

    @Test
    fun `start and end times survive the round trip to the millisecond`() = runTest {
        val repository = repository()
        val startsAt = at(2026, 3, 6, 11, 30).plusMillis(123L)
        val endsAt = at(2026, 3, 6, 12, 15).plusMillis(456L)

        val created = repository.create(meeting(startsAt = startsAt, endsAt = endsAt)).value()

        val loaded = repository.byId(created.id).value()
        assertThat(loaded.startsAt).isEqualTo(startsAt)
        assertThat(loaded.endsAt).isEqualTo(endsAt)
        assertThat(repository.observeUpcoming().first().single().startsAt).isEqualTo(startsAt)
    }

    @Test
    fun `binning hides the meeting everywhere and restoring brings back the same row`() = runTest {
        val repository = repository()
        val created = repository.create(meeting(), listOf(note(body = "Kept"))).value()

        assertThat(repository.moveToBin(created.id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.observeUpcoming().first()).isEmpty()
        assertThat(repository.observeNotes(created.id).first()).isEmpty()
        assertThat(repository.byId(created.id).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)

        val restored = repository.restore(created.id).value()
        assertThat(restored.id).isEqualTo(created.id)
        assertThat(restored.deletedAt).isNull()
        assertThat(repository.notesFor(created.id).value().map { it.body }).containsExactly("Kept")
    }

    @Test
    fun `binning a meeting that is already in the bin is reported as missing`() = runTest {
        val repository = repository()
        val created = repository.create(meeting()).value()
        repository.moveToBin(created.id)

        assertThat(repository.moveToBin(created.id).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.restore(created.id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.restore(created.id).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a note cannot be added to a meeting that is in the bin`() = runTest {
        val repository = repository()
        val created = repository.create(meeting()).value()
        repository.moveToBin(created.id)

        val added = repository.addNote(note(meetingId = created.id, body = "Too late"))

        assertThat(added.failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `an action item records the task it became and can be unlinked again`() = runTest {
        val repository = repository()
        val created = repository.create(meeting()).value()
        val added = repository.addNote(
            note(meetingId = created.id, body = "Send the quote", isActionItem = true),
        ).value()
        val taskId = database.insertTask()

        assertThat(repository.observeOpenActionItems().first().map { it.id }).containsExactly(added.id)

        val linked = repository.linkNoteToTask(added.id, taskId).value()
        assertThat(linked.taskId).isEqualTo(taskId)
        assertThat(linked.isOpenActionItem).isFalse()
        assertThat(repository.observeOpenActionItems().first()).isEmpty()

        val unlinked = repository.linkNoteToTask(added.id, null).value()
        assertThat(unlinked.taskId).isNull()
        assertThat(repository.observeOpenActionItems().first().map { it.id }).containsExactly(added.id)
    }

    @Test
    fun `replacing the notes swaps the whole set and reports the new ids`() = runTest {
        val repository = repository()
        val created = repository.create(
            meeting(),
            listOf(note(body = "Old one"), note(body = "Old two")),
        ).value()

        val replaced = repository.replaceNotes(
            created.id,
            listOf(note(body = "New one"), note(body = "New two")),
        ).value()

        assertThat(replaced.map { it.body }).containsExactly("New one", "New two").inOrder()
        assertThat(replaced.map { it.id }).doesNotContain(0L)
        assertThat(repository.notesFor(created.id).value().map { it.body })
            .containsExactly("New one", "New two")
            .inOrder()
    }

    @Test
    fun `replacing the notes of a binned meeting is refused and changes nothing`() = runTest {
        val repository = repository()
        val created = repository.create(meeting(), listOf(note(body = "Kept"))).value()
        repository.moveToBin(created.id)

        val replaced = repository.replaceNotes(created.id, listOf(note(body = "New")))

        assertThat(replaced.failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        repository.restore(created.id)
        assertThat(repository.notesFor(created.id).value().map { it.body }).containsExactly("Kept")
    }

    @Test
    fun `purging a meeting takes its notes with it`() = runTest {
        val repository = repository()
        val created = repository.create(meeting(), listOf(note(body = "Gone too"))).value()
        val kept = repository.create(meeting(title = "Kept"), listOf(note(body = "Still here"))).value()

        assertThat(repository.purge(created.id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.notesFor(created.id).value()).isEmpty()
        assertThat(repository.notesFor(kept.id).value().map { it.body }).containsExactly("Still here")
        assertThat(repository.purge(created.id).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `updating a meeting stamps the clock rather than trusting the caller`() = runTest {
        val repository = repository()
        val created = repository.create(meeting(title = "Kickoff")).value()
        clock.moveTo(LocalDateTime.of(2026, 3, 5, 18, 0))

        val updated = repository.update(
            created.copy(title = "Kickoff and scope", updatedAt = Instant.EPOCH),
        ).value()

        assertThat(updated.title).isEqualTo("Kickoff and scope")
        assertThat(updated.updatedAt).isEqualTo(clock.now())
    }

    @Test
    fun `a meeting that is not there is reported as missing, not as a storage fault`() = runTest {
        val repository = repository()

        assertThat(repository.byId(404L).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.update(meeting().copy(id = 404L)).failureCode())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.removeNote(404L).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    private fun TestScope.repository(): MeetingRepository = MeetingRepositoryImpl(
        dao = database.meetingDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
    )

    private fun meeting(
        title: String = "Kickoff with Rayluxeo",
        startsAt: Instant = at(2026, 3, 6, 11),
        endsAt: Instant? = null,
    ) = Meeting(
        title = title,
        startsAt = startsAt,
        endsAt = endsAt,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun note(
        meetingId: Long = 0L,
        body: String = "Agreed the scope",
        isActionItem: Boolean = false,
        taskId: Long? = null,
    ) = MeetingNote(
        meetingId = meetingId,
        body = body,
        isActionItem = isActionItem,
        taskId = taskId,
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
