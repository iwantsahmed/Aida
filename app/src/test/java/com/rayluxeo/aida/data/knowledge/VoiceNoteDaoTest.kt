package com.rayluxeo.aida.data.knowledge

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.NoteDao
import com.rayluxeo.aida.data.db.dao.VoiceNoteDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VoiceNoteDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: VoiceNoteDao
    private lateinit var notes: NoteDao

    @Before
    fun openDatabase() {
        database = openKnowledgeDatabase()
        dao = database.voiceNoteDao()
        notes = database.noteDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `insert returns the new row id and the recording is observable`() = runTest {
        val id = dao.insert(voiceNoteEntity(durationMillis = 9_500L))

        assertThat(id).isGreaterThan(0L)
        assertThat(dao.observeAll().first().single().durationMillis).isEqualTo(9_500L)
    }

    @Test
    fun `the backlog holds only recordings with no words against them, oldest first`() = runTest {
        val older = dao.insert(voiceNoteEntity(recordedAtEpochMillis = SEED_MILLIS - 60_000))
        val newer = dao.insert(voiceNoteEntity(recordedAtEpochMillis = SEED_MILLIS))
        dao.insert(voiceNoteEntity(transcript = "already written down"))

        assertThat(dao.observeAwaitingTranscript().first().map { it.id })
            .containsExactly(older, newer).inOrder()
    }

    @Test
    fun `a transcript takes the recording out of the backlog`() = runTest {
        val id = dao.insert(voiceNoteEntity())

        assertThat(dao.setTranscript(id, "call Rahman about the gold logo", SEED_MILLIS + 1_000)).isEqualTo(1)

        assertThat(dao.observeAwaitingTranscript().first()).isEmpty()
        assertThat(dao.findById(id)?.transcript).isEqualTo("call Rahman about the gold logo")
    }

    @Test
    fun `clearing the audio keeps the transcript rather than deleting the recording`() = runTest {
        val fileId = database.seedFile("recording.m4a")
        val id = dao.insert(voiceNoteEntity(audioFileId = fileId, transcript = "worth keeping"))

        database.hardDeleteFile(fileId)

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored?.audioFileId).isNull()
        assertThat(stored?.transcript).isEqualTo("worth keeping")
    }

    @Test
    fun `deleting the written note unlinks the recording rather than deleting it`() = runTest {
        val noteId = notes.insert(noteEntity(body = "written up"))
        val id = dao.insert(voiceNoteEntity(noteId = noteId, transcript = "written up"))

        database.hardDeleteNote(noteId)

        val stored = dao.findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored?.noteId).isNull()
    }

    @Test
    fun `soft delete hides the recording but leaves it readable by id`() = runTest {
        val id = dao.insert(voiceNoteEntity(transcript = "said out loud"))

        assertThat(dao.softDelete(id, SEED_MILLIS + 1_000)).isEqualTo(1)

        assertThat(dao.observeAll().first()).isEmpty()
        assertThat(dao.findById(id)?.deletedAtEpochMillis).isEqualTo(SEED_MILLIS + 1_000)
        assertThat(dao.restore(id, SEED_MILLIS + 2_000)).isEqualTo(1)
        assertThat(dao.observeAll().first().map { it.id }).containsExactly(id)
    }

    @Test
    fun `writing a recording up as a note links the two together`() = runTest {
        val id = dao.insert(voiceNoteEntity(transcript = "call Rahman about the gold logo"))

        val noteId = dao.saveAsNote(id, noteEntity(title = "Rahman", body = "call Rahman about the gold logo"))

        assertThat(noteId).isNotNull()
        assertThat(dao.findById(id)?.noteId).isEqualTo(noteId)
        assertThat(notes.findById(noteId!!)?.body).isEqualTo("call Rahman about the gold logo")
        assertThat(notes.search("rahman*").map { it.id }).containsExactly(noteId)
    }

    @Test
    fun `writing the same recording up twice returns the first note and writes no second copy`() = runTest {
        val id = dao.insert(voiceNoteEntity(transcript = "call Rahman"))
        val first = dao.saveAsNote(id, noteEntity(body = "call Rahman"))

        val second = dao.saveAsNote(id, noteEntity(body = "call Rahman"))

        assertThat(second).isEqualTo(first)
        assertThat(database.countNotes()).isEqualTo(1)
    }

    @Test
    fun `writing up a recording that does not exist leaves no half-written note`() = runTest {
        val noteId = dao.saveAsNote(voiceNoteId = 404L, note = noteEntity(body = "orphan"))

        assertThat(noteId).isNull()
        assertThat(database.countNotes()).isEqualTo(0)
    }
}
